package cn.maple.redisson.processor;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.redisson.annotation.GXConvertRedissonDelayQueueToTopic;
import cn.maple.redisson.listener.GXRedissonDelayQueueListener;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redisson延迟队列到可靠主题转换处理器
 * <p>
 * 该处理器用于将标注了GXConvertRedissonDelayQueueToTopic注解的bean中配置的延迟队列数据
 * 在到期后转发到Redisson的ReliableTopic中，实现可靠的消息队列。
 * </p>
 * <p>
 * 工作原理：
 * 1. 检测标注了GXConvertRedissonDelayQueueToTopic注解的bean
 * 2. 为每个延迟队列创建一个守护线程，监听队列中到期的消息
 * 3. 当消息到期时，通过GXRedissonDelayQueueListener接口将消息发布到指定的Topic
 * 4. 使用线程安全的方式管理所有监听线程，支持应用关闭时的优雅停止
 * </p>
 * <p>
 * 安全性说明：
 * 1. 使用ConcurrentHashMap安全地管理所有监听线程，避免并发修改问题
 * 2. 实现DisposableBean接口，确保在Spring容器关闭时能够优雅地停止所有线程
 * 3. 使用守护线程避免阻止JVM退出，同时在应用关闭时主动中断线程
 * 4. 对所有可能的异常进行捕获和处理，确保单个队列的异常不会影响其他队列
 * 5. 使用原子变量和线程工厂确保线程创建的线程安全性
 * </p>
 */
@Component
@Log4j2
@Lazy
public class GXConvertRedissonDelayQueueToTopicPostProcessor implements BeanPostProcessor, DisposableBean {
    /**
     * 线程工厂，用于创建和命名监听线程
     */
    private final ThreadFactory threadFactory = new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final ThreadGroup group = Thread.currentThread().getThreadGroup();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, "redisson-delay-queue-thread-" + threadNumber.getAndIncrement(), 0);
            t.setDaemon(true); // 设置为守护线程，避免阻止JVM退出
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    };

    /**
     * 存储所有监听线程的映射，用于应用关闭时的优雅停止
     * 使用ConcurrentHashMap确保线程安全
     */
    private final Map<String, Thread> listenerThreads = new ConcurrentHashMap<>();

    @Resource
    private RedissonClient redissonMQClient;

    /**
     * 标记处理器是否正在运行
     */
    private volatile boolean running = true;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Object singletonTarget = AopProxyUtils.getSingletonTarget(bean);
        if (ObjectUtil.isNull(singletonTarget)) {
            return bean;
        }
        GXConvertRedissonDelayQueueToTopic convertRedissonQueueToTopic = singletonTarget.getClass().getAnnotation(GXConvertRedissonDelayQueueToTopic.class);
        if (ObjectUtil.isNotNull(convertRedissonQueueToTopic)) {
            Object listenerBean = getListenerBean(bean, convertRedissonQueueToTopic);
            if (ObjectUtil.isNull(listenerBean)) {
                log.warn("Bean [{}] 标注了GXConvertRedissonDelayQueueToTopic注解但未实现GXRedissonDelayQueueListener接口", beanName);
                return bean;
            }

            String delayQueueName = convertRedissonQueueToTopic.delayQueueName();
            String topicName = convertRedissonQueueToTopic.topicName();
            int timeout = convertRedissonQueueToTopic.timeout();

            // 检查队列名和主题名是否有效
            if (CharSequenceUtil.isBlank(delayQueueName) || CharSequenceUtil.isBlank(topicName)) {
                log.error("Bean [{}] 的延迟队列名或主题名为空，无法创建监听线程", beanName);
                return bean;
            }

            // 创建并启动监听线程
            startListenerThread(listenerBean, delayQueueName, topicName, timeout);
        }
        return bean;
    }

    /**
     * 创建并启动监听延迟队列的线程
     *
     * @param listenerBean   监听器Bean
     * @param delayQueueName 延迟队列名称
     * @param topicName      目标主题名称
     * @param timeout        轮询超时时间（秒）
     */
    private void startListenerThread(Object listenerBean, String delayQueueName, String topicName, int timeout) {
        // 检查是否已存在相同队列的监听线程
        if (listenerThreads.containsKey(delayQueueName)) {
            log.warn("延迟队列 [{}] 已有监听线程，跳过创建", delayQueueName);
            return;
        }

        RBlockingQueue<String> destinationQueue = redissonMQClient.getBlockingQueue(delayQueueName);
        String threadName = CharSequenceUtil.format("redisson-delay-queue-{}-thread", delayQueueName);

        Thread thread = threadFactory.newThread(() -> {
            log.info("启动延迟队列 [{}] 监听线程", delayQueueName);
            while (running) {
                try {
                    log.debug("【{}】线程在【{}】监听Redisson延迟队列【{}】开始", Thread.currentThread().getName(), DateUtil.now(), delayQueueName);
                    String message = destinationQueue.pollFromAny(timeout, TimeUnit.SECONDS);
                    // 可以将到期的数据存入持久化介质中 保证持久化介质的可靠性 可以提高该方案的可靠性
                    if (CharSequenceUtil.isNotEmpty(message)) {
                        log.info("【{}】线程在【{}】监听Redisson延迟队列【{}】收到消息【{}】", threadName, DateUtil.now(), delayQueueName, message);
                        try {
                            GXCommonUtils.reflectCallObjectMethod(listenerBean, "execute", topicName, message);
                        } catch (Exception e) {
                            log.error("处理延迟队列 [{}] 消息时发生异常: {}", delayQueueName, e.getMessage(), e);
                        }
                    }
                } catch (InterruptedException e) {
                    log.warn("获取Redisson的延迟队列【{}】数据发生中断，可能是应用正在关闭: {}", delayQueueName, e.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("监听Redisson延迟队列【{}】时发生异常: {}", delayQueueName, e.getMessage(), e);
                    // 避免因异常导致CPU占用过高
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("延迟队列 [{}] 监听线程已停止", delayQueueName);
        });

        thread.setName(threadName);
        listenerThreads.put(delayQueueName, thread);
        thread.start();
        log.info("已启动延迟队列 [{}] 监听线程", delayQueueName);
    }

    /**
     * 获取实现了GXRedissonDelayQueueListener接口并且标注了GXConvertRedissonDelayQueueToTopic注解的bean
     *
     * @param bean                        待检查的bean
     * @param convertRedissonQueueToTopic 注解实例
     * @return 如果bean实现了GXRedissonDelayQueueListener接口则返回bean，否则返回null
     */
    private Object getListenerBean(Object bean, GXConvertRedissonDelayQueueToTopic convertRedissonQueueToTopic) {
        if (ObjectUtil.isNotNull(convertRedissonQueueToTopic)) {
            String targetSimpleName = GXRedissonDelayQueueListener.class.getSimpleName();
            Class<?>[] allInterfacesForClass = ClassUtils.getAllInterfacesForClass(bean.getClass());
            for (Class<?> interfacesForClass : allInterfacesForClass) {
                if (interfacesForClass.getSimpleName().equals(targetSimpleName)) {
                    return bean;
                }
            }
        }
        return null;
    }

    /**
     * 应用关闭时优雅停止所有监听线程
     * <p>
     * 实现DisposableBean接口的destroy方法，在Spring容器关闭时被调用
     * 该方法会标记处理器为非运行状态，并中断所有监听线程
     * </p>
     *
     * @throws Exception 如果停止线程过程中发生异常
     */
    @Override
    public void destroy() throws Exception {
        log.info("正在停止所有Redisson延迟队列监听线程...");
        running = false;

        // 中断并等待所有监听线程结束
        for (Map.Entry<String, Thread> entry : listenerThreads.entrySet()) {
            String queueName = entry.getKey();
            Thread thread = entry.getValue();

            try {
                log.info("正在停止延迟队列 [{}] 的监听线程", queueName);
                thread.interrupt();
                // 等待线程结束，但最多等待5秒
                thread.join(5000);
                if (thread.isAlive()) {
                    log.warn("延迟队列 [{}] 的监听线程未能在5秒内停止", queueName);
                } else {
                    log.info("延迟队列 [{}] 的监听线程已成功停止", queueName);
                }
            } catch (InterruptedException e) {
                log.warn("等待延迟队列 [{}] 监听线程停止时被中断", queueName);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("停止延迟队列 [{}] 监听线程时发生异常: {}", queueName, e.getMessage(), e);
            }
        }

        listenerThreads.clear();
        log.info("所有Redisson延迟队列监听线程已停止");
    }
}
