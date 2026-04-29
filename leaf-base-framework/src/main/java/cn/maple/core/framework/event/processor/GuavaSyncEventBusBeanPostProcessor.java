package cn.maple.core.framework.event.processor;

import cn.hutool.core.util.ReflectUtil;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("all")
@Component
@Log4j2
@Profile("guava")
public class GuavaSyncEventBusBeanPostProcessor implements BeanPostProcessor {
    private final ConcurrentHashMap<String, Boolean> registeredBeans = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Object> registrationLocks = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Class<?>, Set<Method>> subscribedMethodsCache = new ConcurrentHashMap<>();

    @Resource
    private EventBus eventBus;

    @PreDestroy
    public void destroy() {
        registeredBeans.clear();
        registrationLocks.clear();
        subscribedMethodsCache.clear();
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        log.debug("处理Bean[{}]的Guava同步事件总线注册", beanName);
        if (bean == null) {
            return null;
        }
        if (beanName == null) {
            log.warn("Bean名称为空，跳过Guava同步事件总线注册: {}", bean.getClass().getName());
            return bean;
        }
        Set<Method> subscribeMethods = findSubscribeMethods(bean);
        if (subscribeMethods.isEmpty()) {
            return bean;
        }

        Object registrationLock = registrationLocks.computeIfAbsent(beanName, ignored -> new Object());
        try {
            synchronized (registrationLock) {
                if (registeredBeans.containsKey(beanName)) {
                    log.debug("Bean[{}]已注册到Guava同步事件总线，跳过处理", beanName);
                    return bean;
                }
                registerBean(bean, beanName, subscribeMethods);
            }
        } finally {
            registrationLocks.remove(beanName, registrationLock);
        }
        return bean;
    }

    private void registerBean(Object bean, String beanName, Set<Method> subscribeMethods) {
        try {
            eventBus.register(bean);
            registeredBeans.put(beanName, Boolean.TRUE);
        } catch (Exception e) {
            log.error("注册Bean[{}]到Guava同步事件总线时发生异常: {}", beanName, e.getMessage(), e);
            return;
        }
        if (log.isInfoEnabled()) {
            subscribeMethods.forEach(method ->
                    log.info("在Bean[{}]中注册@Subscribe方法[{}]到Guava同步事件总线",
                            beanName, method.getName()));
        }
        log.info("成功将Bean[{}]注册到Guava同步事件总线，包含{}个@Subscribe方法",
                beanName, subscribeMethods.size());
    }

    private Set<Method> findSubscribeMethods(Object bean) {
        Class<?> beanClass = bean.getClass();
        return subscribedMethodsCache.computeIfAbsent(beanClass, clazz -> {
            Set<Method> result = new LinkedHashSet<>();
            Method[] methods = ReflectUtil.getMethods(clazz);
            if (methods == null || methods.length == 0) {
                return Collections.emptySet();
            }
            for (Method method : methods) {
                if (method.isAnnotationPresent(Subscribe.class)) {
                    result.add(method);
                }
            }
            return Collections.unmodifiableSet(result);
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
