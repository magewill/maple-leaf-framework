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

@SuppressWarnings("all")
@Component
@Log4j2
@Profile("guava")
public class GuavaASyncEventBusBeanPostProcessor implements BeanPostProcessor {
    private final ConcurrentHashMap<String, Boolean> registeredBeans = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Class<?>, Set<Method>> subscribedMethodsCache = new ConcurrentHashMap<>();

    @Resource
    private AsyncEventBus asyncEventBus;

    @PreDestroy
    public void destroy() {
        registeredBeans.clear();
        subscribedMethodsCache.clear();
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        log.debug("处理Bean[{}]的Guava异步事件总线注册", beanName);
        if (bean == null) {
            return null;
        }
        if (registeredBeans.containsKey(beanName)) {
            log.debug("Bean[{}]已注册到Guava异步事件总线，跳过处理", beanName);
            return bean;
        }
        try {
            Set<Method> subscribeMethods = findSubscribeMethods(bean);
            if (CollUtil.isNotEmpty(subscribeMethods)) {
                asyncEventBus.register(bean);
                registeredBeans.put(beanName, Boolean.TRUE);
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

    private Set<Method> findSubscribeMethods(Object bean) {
        Class<?> beanClass = bean.getClass();
        return subscribedMethodsCache.computeIfAbsent(beanClass, clazz -> {
            Set<Method> result = CollUtil.newHashSet();
            Method[] methods = ReflectUtil.getMethods(clazz);
            if (methods == null || methods.length == 0) {
                return result;
            }
            for (Method method : methods) {
                Annotation[] annotations = method.getAnnotations();
                if (annotations == null || annotations.length == 0) {
                    continue;
                }
                for (Annotation annotation : annotations) {
                    if (annotation != null && annotation.annotationType().equals(Subscribe.class)) {
                        result.add(method);
                        break;
                    }
                }
            }
            return result;
        });
    }

    public int getRegisteredBeanCount() {
        return registeredBeans.size();
    }

    public boolean isBeanRegistered(String beanName) {
        return Optional.ofNullable(beanName)
                .map(registeredBeans::containsKey)
                .orElse(false);
    }
}
