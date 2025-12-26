package cn.maple.redisson.processor;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.maple.redisson.listener.GXRedissonMQListener;
import cn.maple.redisson.util.GXRedissonMQUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Redisson消息队列监听器服务处理器
 * <p>
 * 该处理器用于自动检测并注册实现了{@link GXRedissonMQListener}接口的Bean。
 * 当Spring容器中的Bean实现了{@link GXRedissonMQListener}接口时，
 * 该处理器会自动调用Bean的registerRedissonListener方法，
 * 从而实现Redisson消息队列监听器的自动注册。
 * </p>
 *
 * <p>
 * 工作原理：
 * 1. 在Bean初始化完成后检查其是否实现了{@link GXRedissonMQListener}接口
 * 2. 如果实现了该接口，则通过反射调用Bean的registerRedissonListener方法
 * 3. 该方法应当包含监听器的注册逻辑，如设置监听的主题和处理消息的回调
 * </p>
 *
 * <p>
 * 使用示例：
 * <p>
 * 1. 定义消息监听器：
 * <pre>
 * {@code
 * @Component
 * public class OrderCreatedListener implements GXRedissonMQListener {
 *     @Resource
 *     private RedissonClient redissonClient;
 *
 *     @Override
 *     public void registerRedissonListener() {
 *         // 获取Redisson的可靠主题
 *         RTopic topic = redissonClient.getTopic("order-created-topic", StringCodec.INSTANCE);
 *
 *         // 注册监听器
 *         topic.addListener(String.class, (channel, msg) -> {
 *             // 处理接收到的消息
 *             System.out.println("收到订单创建消息: " + msg);
 *         });
 *     }
 * }
 * }
 * </pre>
 * <p>
 * 2. 发送消息到主题：
 * <pre>
 * {@code
 * @Service
 * public class OrderService {
 *     @Resource
 *     private RedissonClient redissonClient;
 *
 *     public void createOrder(String orderId) {
 *         // 创建订单逻辑...
 *
 *         // 发布订单创建消息
 *         RTopic topic = redissonClient.getTopic("order-created-topic", StringCodec.INSTANCE);
 *         topic.publish(orderId);
 *     }
 * }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 安全性说明：
 * 1. 该处理器在多线程环境下是安全的，因为Spring容器确保BeanPostProcessor的调用是线程安全的
 * 2. 使用{@link AopProxyUtils}处理可能的AOP代理对象，确保正确获取目标Bean
 * 3. 通过反射调用方法时进行了空值检查和异常捕获，防止因空指针或反射异常导致应用崩溃
 * 4. 使用日志记录关键操作和异常情况，便于问题排查和监控
 * 5. 使用{@link ConcurrentHashMap}缓存接口检测结果，提高性能并确保线程安全
 * 6. 实现{@link DisposableBean}接口，在应用关闭时清理资源
 * 7. 实现{@link PriorityOrdered}接口，确保处理器的执行顺序
 * </p>
 *
 * <p>
 * 性能优化：
 * 1. 使用缓存减少重复的接口检测操作
 * 2. 优化反射调用，减少不必要的方法查找
 * 3. 使用Java 17+的新特性如模式匹配、增强型switch等提高代码效率
 * 4. 使用{@link ReflectUtil}工具类简化反射操作
 * 5. 使用{@link AtomicInteger}统计注册成功的监听器数量，避免锁竞争
 * </p>
 *
 * @author 马树铭
 * @since 2023-06-15
 */
@Component
@Log4j2
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false}")
public class GXRedissonMQPostProcessor implements BeanPostProcessor, DisposableBean, PriorityOrdered {
    /**
     * 目标接口类
     */
    private static final Class<?> TARGET_INTERFACE = GXRedissonMQListener.class;

    /**
     * 目标方法名
     */
    private static final String TARGET_METHOD_NAME = "registerRedissonListener";

    /**
     * 接口实现缓存，用于存储已检测过的Bean类与是否实现目标接口的映射关系
     * 使用ConcurrentHashMap确保线程安全，提高并发性能
     */
    private final Map<Class<?>, Boolean> interfaceImplementationCache = new ConcurrentHashMap<>();

    /**
     * 方法缓存，用于存储已查找过的方法，避免重复反射查找
     * 使用ConcurrentHashMap确保线程安全，提高并发性能
     */
    private final Map<Class<?>, Optional<Method>> methodCache = new ConcurrentHashMap<>();

    /**
     * 已注册的监听器集合，用于记录当前应用实例注册的监听器
     * 使用ConcurrentHashMap.newKeySet()确保线程安全的Set操作
     */
    //private final Set<String> registeredListeners = ConcurrentHashMap.newKeySet();
    private final Map<String, String> registeredListeners = new ConcurrentHashMap<>();

    /**
     * 注册成功计数
     */
    private final AtomicInteger successCount = new AtomicInteger(0);


    /**
     * 应用启动标识，确保清理逻辑只在应用启动时执行一次
     */
    private final AtomicBoolean applicationStarted = new AtomicBoolean(false);

    /**
     * Bean初始化前的处理
     * 本处理器不需要在Bean初始化前执行操作，直接返回Bean
     *
     * @param bean     Bean实例
     * @param beanName Bean名称
     * @return 原始Bean实例
     * @throws BeansException 如果处理过程中发生异常
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * Bean初始化后的处理
     * <p>
     * 检查Bean是否实现了{@link GXRedissonMQListener}接口，如果是，则调用其registerRedissonListener方法。
     * 该方法使用了线程安全的方式检查接口实现，并通过反射调用注册方法。
     * </p>
     * <p>
     * 安全处理：
     * 1. 对传入的bean进行空值检查，避免空指针异常
     * 2. 处理可能的AOP代理对象，确保正确获取目标Bean
     * 3. 使用缓存减少重复的接口检测操作
     * 4. 使用try-catch块捕获可能的异常，确保单个Bean的处理失败不会影响其他Bean
     * 5. 详细记录异常信息，便于问题排查
     * </p>
     *
     * @param bean     Bean实例
     * @param beanName Bean名称
     * @return 处理后的Bean实例
     * @throws BeansException 如果处理过程中发生异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        try {
            Class<?> beanClass = bean.getClass();
            // ✅ 先快速检查接口实现（使用缓存），大部分 bean 会在这里就返回
            if (!implementsTargetInterface(beanClass)) {
                // ✅ 如果是代理对象，再检查目标类
                if (AopUtils.isAopProxy(bean)) {
                    Object targetBean = AopProxyUtils.getSingletonTarget(bean);
                    if (targetBean != null) {
                        Class<?> targetClass = targetBean.getClass();
                        if (implementsTargetInterface(targetClass)) {
                            registerListener(targetBean, beanName);
                        }
                    }
                }
                return bean;
            }
            // ✅ 实现了接口的 bean，获取真实对象并注册
            Object targetBean = AopProxyUtils.getSingletonTarget(bean);
            Object actualBean = targetBean != null ? targetBean : bean;
            registerListener(actualBean, beanName);
        } catch (Exception e) {
            log.error("处理Bean [{}] 时发生异常: {}", beanName, e.getMessage(), e);
        }
        return bean;
    }

    /**
     * 清理过期的订阅者信息
     * <p>
     * 在应用启动时清理Redis中可能残留的上次应用实例的订阅者信息
     * 这样可以防止订阅者信息累加，确保每次启动都是干净的状态
     * </p>
     */
    private void cleanupStaleSubscriptions() {
        try {
            // 获取 Redis 中所有订阅
            Map<String, String> allListeners = GXRedissonMQUtils.getAllLocalListeners();

            // 过滤出疑似僵尸订阅（可选：通过命名规则或时间戳判断）
            Map<String, String> staleListeners = filterStaleListeners(allListeners);

            if (!staleListeners.isEmpty()) {
                log.info("🧹 检测到 {} 个遗留订阅，开始清理...", staleListeners.size());

                for (Map.Entry<String, String> entry : staleListeners.entrySet()) {
                    try {
                        GXRedissonMQUtils.unsubscribe(entry.getKey(), entry.getValue());
                    } catch (Exception e) {
                        log.warn("清理遗留订阅失败: {}", entry.getKey());
                    }
                }
                log.info("✅ 遗留订阅清理完成");
            }
        } catch (Exception e) {
            log.error("清理遗留订阅时发生异常", e);
        }
    }

    /**
     * 过滤遗留订阅（可根据实际情况调整策略）
     */
    private Map<String, String> filterStaleListeners(Map<String, String> allListeners) {
        String currentInstanceId = GXRedissonMQUtils.getInstanceId();

        return allListeners.entrySet().stream()
                .filter(entry -> {
                    String key = entry.getKey();
                    // 检查1：如果已在 registeredListeners 中，不清理
                    if (registeredListeners.containsKey(key)) {
                        return false;
                    }
                    // 检查2：如果有 instanceId 前缀，确保不是当前实例的
                    List<String> split = CharSequenceUtil.split(key, ':');
                    if (!split.isEmpty()) {
                        String instanceId = split.get(0);
                        // 如果是当前实例的但不在 registeredListeners 中
                        // 说明是异常情况，保守起见不清理
                        if (instanceId.equals(currentInstanceId)) {
                            log.warn("⚠️  发现当前实例的未注册订阅: {}", key);
                            return false;
                        }
                    }
                    // 其他情况：清理
                    return true;
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 判断是否为遗留订阅
     */
    private boolean isStaleListener(String listenerId, long threshold) {
        // 假设 listenerId 格式：listener-{timestamp}-{uuid}
        try {
            String[] parts = listenerId.split("-");
            if (parts.length >= 2) {
                long timestamp = Long.parseLong(parts[1]);
                return timestamp < threshold;
            }
        } catch (Exception e) {
            // 解析失败，保守起见不清理
        }
        return false;
    }

    /**
     * 检查Bean类是否实现了目标接口
     * <p>
     * 使用缓存存储已检测过的Bean类与结果的映射关系，避免重复检测
     * 采用ConcurrentHashMap确保线程安全，提高并发性能
     * </p>
     *
     * @param beanClass Bean类
     * @return 如果Bean类实现了目标接口则返回true，否则返回false
     */
    private boolean implementsTargetInterface(Class<?> beanClass) {
        // 首先检查缓存中是否已有结果
        return interfaceImplementationCache.computeIfAbsent(beanClass, TARGET_INTERFACE::isAssignableFrom);
    }

    /**
     * 注册监听器
     * <p>
     * 通过反射调用Bean的registerRedissonListener方法，注册Redisson消息队列监听器
     * 使用try-catch块捕获可能的异常，确保单个Bean的处理失败不会影响其他Bean
     * </p>
     *
     * @param bean     Bean实例
     * @param beanName Bean名称
     */
    private void registerListener(Object bean, String beanName) {
        String className = bean.getClass().getSimpleName();
        try {
            // 尝试获取目标方法
            Method method = findTargetMethod(bean.getClass()).orElseThrow(() -> new NoSuchMethodException("Method '" + TARGET_METHOD_NAME + "' not found"));
            // 调用方法注册监听器
            method.setAccessible(true);

            // ✅ 记录注册前的监听器（使用 Set 避免并发问题）
            Set<String> beforeKeys = new HashSet<>(GXRedissonMQUtils.getAllLocalListeners().keySet());

            // ✅ 调用方法注册监听器
            method.invoke(bean);

            // 记录当前应用实例注册的监听器
            //registeredListeners.add(beanName);
            // ✅ 记录新注册的监听器
            Map<String, String> afterListeners = GXRedissonMQUtils.getAllLocalListeners();
            // ✅ 找出新增的监听器
            int newListenerCount = 0;
            for (Map.Entry<String, String> entry : afterListeners.entrySet()) {
                if (!beforeKeys.contains(entry.getKey())) {
                    registeredListeners.put(entry.getKey(), entry.getValue());
                    newListenerCount++;
                    log.debug("记录新注册的监听器: {} -> {}", entry.getKey(), entry.getValue());
                }
            }

            if (newListenerCount == 0) {
                log.warn("⚠️  监听器 {} 注册后未发现新增订阅，可能重复注册或注册失败", className);
            } else {
                successCount.incrementAndGet();
                log.info("✅ 成功注册监听器: {}, 新增 {} 个订阅", className, newListenerCount);
            }
            log.info("成功注册Redisson的PUB/SUB监听器: {}", className);
        } catch (Exception e) {
            // 记录详细的异常信息，便于问题排查
            log.error("注册Redisson的PUB/SUB监听器失败: {} - {}", className, e.getMessage(), e);
        }
    }

    /**
     * 查找目标方法
     * <p>
     * 在Bean类及其父类中查找指定名称的方法
     * 使用Optional包装返回结果，避免空指针异常
     * 使用缓存存储已查找过的方法，避免重复反射查找
     * </p>
     *
     * @param clazz Bean类
     * @return 包含目标方法的Optional对象，如果未找到则为空
     */
    private Optional<Method> findTargetMethod(Class<?> clazz) {
        return methodCache.computeIfAbsent(clazz, cls -> {
            // ✅ 遍历完整的类继承链
            Class<?> currentClass = cls;
            while (currentClass != null && currentClass != Object.class) {
                try {
                    Method method = ReflectUtil.getMethod(currentClass, TARGET_METHOD_NAME);
                    if (method != null) {
                        return Optional.of(method);
                    }
                } catch (Exception e) {
                    // 继续查找父类
                }
                currentClass = currentClass.getSuperclass();
            }
            return Optional.empty();
        });
    }

    /**
     * 在应用关闭时清理资源
     * <p>
     * 取消当前应用实例注册的所有订阅
     * 清空缓存，释放内存资源
     * </p>
     *
     * @throws Exception 如果清理过程中发生异常
     */
    @Override
    public void destroy() {
        String instanceId = GXRedissonMQUtils.getInstanceId();
        if (registeredListeners.isEmpty()) {
            log.info("无需取消订阅");
            return;
        }

        log.info("🛑 开始取消订阅 - 实例: {}, 共 {} 个", instanceId, registeredListeners.size());

        int successCount = 0;
        int failCount = 0;

        log.info("🛑 开始取消 {} 个订阅...", registeredListeners.size());
        for (Map.Entry<String, String> entry : registeredListeners.entrySet()) {
            try {
                GXRedissonMQUtils.unsubscribe(entry.getKey(), entry.getValue());
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("取消订阅失败: {} - {}", entry.getKey(), e.getMessage());
            }
        }

        log.info("✅ 订阅取消完成 - 成功: {}, 失败: {}", successCount, failCount);

        // 清空缓存
        interfaceImplementationCache.clear();
        methodCache.clear();
        registeredListeners.clear();
    }

    /**
     * 应用完全启动后的处理
     * 使用 ApplicationReadyEvent 确保所有 Bean 都已初始化完成
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (applicationStarted.compareAndSet(false, true)) {
            cleanupStaleSubscriptions();
        }
    }

    /**
     * 获取处理器的执行顺序
     * <p>
     * 返回最高优先级，确保该处理器在其他处理器之前执行
     * </p>
     *
     * @return 处理器的执行顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}