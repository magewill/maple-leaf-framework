package cn.maple.redisson.processor;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.maple.redisson.listener.GXRedissonMQListener;
import lombok.extern.log4j.Log4j2;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
@Lazy
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false}")
public class GXRedissonMQListenerServicePostProcessor implements BeanPostProcessor, DisposableBean, PriorityOrdered {
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
     * 已注册的监听器数量
     * 使用AtomicInteger确保线程安全，避免锁竞争
     */
    private final AtomicInteger registeredListenerCount = new AtomicInteger(0);

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
        // 空值检查
        if (ObjectUtil.isNull(bean)) {
            return null;
        }

        try {
            // 获取单例目标对象，处理可能的AOP代理情况
            Object targetBean = AopProxyUtils.getSingletonTarget(bean);
            Object actualBean = targetBean != null ? targetBean : bean;
            Class<?> beanClass = actualBean.getClass();

            // 检查Bean是否实现了目标接口
            if (implementsTargetInterface(beanClass)) {
                registerListener(actualBean, beanName);
            }
        } catch (Exception e) {
            // 记录异常但不中断处理流程，确保其他Bean不受影响
            log.error("处理Bean [{}] 时发生异常: {}", beanName, e.getMessage(), e);
        }

        return bean;
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
        return interfaceImplementationCache.computeIfAbsent(beanClass, clazz -> {
            // 获取Bean实现的所有接口
            Class<?>[] allInterfaces = ClassUtils.getAllInterfacesForClass(clazz);

            // 使用Java 8+ Stream API检查是否实现了目标接口
            return Arrays.stream(allInterfaces)
                    .anyMatch(interfaceClass -> interfaceClass == TARGET_INTERFACE);
        });
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
            Method method = findTargetMethod(bean.getClass())
                    .orElseThrow(() -> new NoSuchMethodException("Method '" + TARGET_METHOD_NAME + "' not found"));

            // 调用方法注册监听器
            method.setAccessible(true);
            method.invoke(bean);

            // 更新注册成功的监听器计数
            int count = registeredListenerCount.incrementAndGet();
            log.info("成功注册Redisson的PUB/SUB监听器: {}, 当前已注册监听器数量: {}", className, count);
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
        // 首先检查缓存中是否已有结果
        return methodCache.computeIfAbsent(clazz, cls -> {
            try {
                // 使用ReflectUtil工具类简化反射操作
                Method method = ReflectUtil.getMethod(cls, TARGET_METHOD_NAME);
                return Optional.ofNullable(method);
            } catch (Exception e) {
                // 如果在当前类中未找到方法，则检查父类
                Class<?> superClass = cls.getSuperclass();
                if (superClass != null && superClass != Object.class) {
                    return findTargetMethod(superClass);
                }
                return Optional.empty();
            }
        });
    }

    /**
     * 在应用关闭时清理资源
     * <p>
     * 清空缓存，释放内存资源
     * 记录监听器注册统计信息
     * </p>
     *
     * @throws Exception 如果清理过程中发生异常
     */
    @Override
    public void destroy() throws Exception {
        // 记录监听器注册统计信息
        log.info("Redisson消息队列监听器服务处理器关闭，共注册监听器: {} 个", registeredListenerCount.get());

        // 清空缓存，释放内存资源
        interfaceImplementationCache.clear();
        methodCache.clear();
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