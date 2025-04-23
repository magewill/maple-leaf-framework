package cn.maple.core.framework.event.processor;

import cn.hutool.core.util.ReflectUtil;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.Subscribe;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guava异步事件总线Bean后处理器
 * <p>
 * 自动检测并注册带有@Subscribe注解的方法到异步事件总线
 * 只有在激活"guava"profile时才会生效
 * </p>
 *
 * @author maple
 */

@SuppressWarnings("all")
@Component
@Log4j2
@Profile("guava")
public class GuavaASyncEventBusBeanPostProcessor implements BeanPostProcessor {
    /**
     * 事件总线bean由Spring IoC容器负责创建，这里通过@Resource注解注入该bean
     * 异步事件总线用于异步处理事件，提高系统吞吐量
     */
    @Resource
    private AsyncEventBus asyncEventBus;
    
    /**
     * 已注册的Bean缓存，避免重复注册
     * 使用ConcurrentHashMap确保线程安全
     */
    private final ConcurrentHashMap<String, Boolean> registeredBeans = new ConcurrentHashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * 对于每个容器执行了初始化的 bean，如果这个 bean 的某个方法注解了@Subscribe,则将该 bean 注册到事件总线
     * 该方法是线程安全的，可在多线程环境下安全调用
     *
     * @param bean     the new bean instance 新的Bean实例
     * @param beanName the name of the bean Bean的名称
     * @return Object 处理后的Bean对象
     * @throws BeansException 如果处理过程中发生异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        log.info("注册Guava EventBus的异步PostProcess");
        // 如果bean为null，直接返回，避免NPE
        if (bean == null) {
            return null;
        }
        
        // 检查是否已注册，避免重复注册
        if (registeredBeans.containsKey(beanName)) {
            return bean;
        }
        
        try {
            // 获取bean的所有方法
            Method[] methods = ReflectUtil.getMethods(bean.getClass());
            if (methods == null || methods.length == 0) {
                return bean;
            }
            
            // 检查是否有@Subscribe注解的方法
            boolean hasSubscribeMethod = false;
            for (Method method : methods) {
                // 检查方法上的注解
                Annotation[] annotations = method.getAnnotations();
                if (annotations == null || annotations.length == 0) {
                    continue;
                }
                
                for (Annotation annotation : annotations) {
                    // 如果包含@Subscribe注解
                    if (annotation != null && annotation.annotationType().equals(Subscribe.class)) {
                        hasSubscribeMethod = true;
                        log.info("在Bean[{}]中发现@Subscribe注解的方法[{}]，注册到Guava异步事件总线", beanName, method.getName());
                        break;
                    }
                }
                
                if (hasSubscribeMethod) {
                    break;
                }
            }
            
            // 如果有@Subscribe注解的方法，注册到事件总线
            if (hasSubscribeMethod) {
                asyncEventBus.register(bean);
                // 记录已注册的Bean
                registeredBeans.put(beanName, Boolean.TRUE);
                log.info("成功将Bean[{}]注册到Guava异步事件总线", beanName);
            }
        } catch (Exception e) {
            log.error("注册Bean[{}]到Guava异步事件总线时发生异常: {}", beanName, e.getMessage(), e);
        }
        return bean;
    }
}
