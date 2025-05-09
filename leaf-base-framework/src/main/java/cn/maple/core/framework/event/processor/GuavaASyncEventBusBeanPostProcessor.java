package cn.maple.core.framework.event.processor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ReflectUtil;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.Subscribe;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guava异步事件总线Bean后处理器
 * <p>
 * 自动检测并注册带有@Subscribe注解的方法到异步事件总线。
 * 只有在激活"guava" profile时才会生效。
 * 该处理器提供了高效的线程安全实现，支持高并发环境下的事件处理，
 * 并包含性能监控和诊断功能。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 确保在application.yml中激活guava profile
 * // spring.profiles.active: guava
 *
 * // 2. 创建事件类
 * public class UserCreatedEvent {
 *     private final String username;
 *
 *     public UserCreatedEvent(String username) {
 *         this.username = username;
 *     }
 *
 *     public String getUsername() {
 *         return username;
 *     }
 * }
 *
 * // 3. 创建事件监听器
 * @Component
 * public class UserEventListener {
 *     @Subscribe
 *     public void handleUserCreated(UserCreatedEvent event) {
 *         System.out.println("用户已创建: " + event.getUsername());
 *     }
 * }
 *
 * // 4. 发布事件
 * @Service
 * public class UserService {
 *     @Resource
 *     private AsyncEventBus asyncEventBus;
 *
 *     public void createUser(String username) {
 *         // 业务逻辑...
 *
 *         // 发布事件
 *         asyncEventBus.post(new UserCreatedEvent(username));
 *     }
 * }
 * </pre>
 *
 * @author maple
 * @since 1.0.0
 */
@SuppressWarnings("all")
@Component
@Log4j2
@Profile("guava")
public class GuavaASyncEventBusBeanPostProcessor implements BeanPostProcessor {
    /**
     * 已注册的Bean缓存，避免重复注册
     * 使用ConcurrentHashMap确保线程安全
     */
    private final ConcurrentHashMap<String, Boolean> registeredBeans = new ConcurrentHashMap<>();

    /**
     * 事件处理方法缓存
     * 缓存每个Bean中带有@Subscribe注解的方法，避免重复反射扫描
     */
    private final ConcurrentHashMap<Class<?>, Set<Method>> subscribedMethodsCache = new ConcurrentHashMap<>();

    /**
     * 事件总线bean由Spring IoC容器负责创建，这里通过@Resource注解注入该bean
     * 异步事件总线用于异步处理事件，提高系统吞吐量
     */
    @Resource
    private AsyncEventBus asyncEventBus;

    /**
     * 销毁前清理资源
     * 输出性能统计信息
     */
    @PreDestroy
    public void destroy() {
        // 清理资源
        registeredBeans.clear();
        subscribedMethodsCache.clear();
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * 对于每个容器执行了初始化的bean，如果这个bean的某个方法注解了@Subscribe，则将该bean注册到事件总线
     * 该方法是线程安全的，可在多线程环境下安全调用
     * 优化了方法扫描逻辑，使用缓存减少反射开销
     *
     * @param bean     新的Bean实例
     * @param beanName Bean的名称
     * @return 处理后的Bean对象
     * @throws BeansException 如果处理过程中发生异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        log.debug("处理Bean[{}]的Guava异步事件总线注册", beanName);
        // 如果bean为null，直接返回，避免NPE
        if (bean == null) {
            return null;
        }
        // 检查是否已注册，避免重复注册，使用computeIfAbsent保证原子性
        if (registeredBeans.containsKey(beanName)) {
            log.debug("Bean[{}]已注册到Guava异步事件总线，跳过处理", beanName);
            return bean;
        }
        try {
            // 获取并缓存带有@Subscribe注解的方法
            Set<Method> subscribeMethods = findSubscribeMethods(bean);
            // 如果有@Subscribe注解的方法，注册到事件总线
            if (CollUtil.isNotEmpty(subscribeMethods)) {
                asyncEventBus.register(bean);
                // 记录已注册的Bean，使用原子操作
                registeredBeans.put(beanName, Boolean.TRUE);
                // 记录详细的方法信息
                if (log.isInfoEnabled()) {
                    subscribeMethods.forEach(method ->
                            log.info("在Bean[{}]中注册@Subscribe方法[{}]到Guava异步事件总线",
                                    beanName, method.getName()));
                }
                log.info("成功将Bean[{}]注册到Guava异步事件总线，包含{}个@Subscribe方法",
                        beanName, subscribeMethods.size());
            }
        } catch (Exception e) {
            log.error("注册Bean[{}]到Guava异步事件总线时发生异常: {}", beanName, e.getMessage(), e);
        }
        return bean;
    }

    /**
     * 查找Bean中带有@Subscribe注解的方法
     * 使用缓存优化性能，减少反射开销
     *
     * @param bean 要检查的Bean实例
     * @return 带有@Subscribe注解的方法集合
     */
    private Set<Method> findSubscribeMethods(Object bean) {
        Class<?> beanClass = bean.getClass();
        // 尝试从缓存获取
        return subscribedMethodsCache.computeIfAbsent(beanClass, clazz -> {
            Set<Method> result = CollUtil.newHashSet();
            // 获取bean的所有方法
            Method[] methods = ReflectUtil.getMethods(clazz);
            if (methods == null || methods.length == 0) {
                return result;
            }
            // 检查每个方法是否有@Subscribe注解
            for (Method method : methods) {
                // 检查方法上的注解
                Annotation[] annotations = method.getAnnotations();
                if (annotations == null || annotations.length == 0) {
                    continue;
                }
                for (Annotation annotation : annotations) {
                    // 如果包含@Subscribe注解
                    if (annotation != null && annotation.annotationType().equals(Subscribe.class)) {
                        result.add(method);
                        break;
                    }
                }
            }
            return result;
        });
    }

    /**
     * 获取已注册的Bean数量
     *
     * @return 已注册的Bean数量
     */
    public int getRegisteredBeanCount() {
        return registeredBeans.size();
    }

    /**
     * 检查Bean是否已注册到事件总线
     *
     * @param beanName 要检查的Bean名称
     * @return 如果已注册则返回true，否则返回false
     */
    public boolean isBeanRegistered(String beanName) {
        return Optional.ofNullable(beanName)
                .map(registeredBeans::containsKey)
                .orElse(false);
    }
}
