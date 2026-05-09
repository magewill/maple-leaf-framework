package cn.maple.redisson.processor;

import cn.maple.redisson.listener.GXRedissonStreamMQListener;
import cn.maple.redisson.stream.GXRedissonStreamMQManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registers beans that implement {@link GXRedissonStreamMQListener}.
 */
@Component
@Log4j2
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false} && ${maple.framework.mq.stream.redisson.enable:false}")
@ConditionalOnBean(name = "redissonStreamMessageQueueManager")
public class GXRedissonStreamMQPostProcessor implements BeanPostProcessor, DisposableBean, PriorityOrdered, ApplicationContextAware {
    private static final Class<?> TARGET_INTERFACE = GXRedissonStreamMQListener.class;
    private static final String MANAGER_BEAN_NAME = "redissonStreamMessageQueueManager";

    private final Map<Class<?>, Boolean> interfaceImplementationCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> registeredBeans = new ConcurrentHashMap<>();
    private final AtomicInteger successCount = new AtomicInteger(0);
    private ApplicationContext applicationContext;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!isListenerBean(bean)) {
            return bean;
        }
        if (registeredBeans.putIfAbsent(beanName, Boolean.TRUE) != null) {
            log.warn("Redisson stream MQ listener bean [{}] has already been processed, skip duplicate registration", beanName);
            return bean;
        }
        registerListener((GXRedissonStreamMQListener) bean, beanName);
        return bean;
    }

    private boolean isListenerBean(Object bean) {
        if (bean == null) {
            return false;
        }
        if (bean instanceof GXRedissonStreamMQListener) {
            return true;
        }
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
        return implementsTargetInterface(targetClass);
    }

    private boolean implementsTargetInterface(Class<?> beanClass) {
        if (beanClass == null) {
            return false;
        }
        return interfaceImplementationCache.computeIfAbsent(beanClass, TARGET_INTERFACE::isAssignableFrom);
    }

    private void registerListener(GXRedissonStreamMQListener listener, String beanName) {
        try {
            getMessageQueueManager();
            listener.registerRedissonStreamListener();
        } catch (Exception e) {
            registeredBeans.remove(beanName);
            throw new IllegalStateException("Failed to register Redisson stream MQ listener bean [" + beanName + "]", e);
        }
        successCount.incrementAndGet();
        log.info("Registered Redisson stream MQ listener bean [{}]", beanName);
    }

    private GXRedissonStreamMQManager getMessageQueueManager() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext has not been injected");
        }
        return applicationContext.getBean(MANAGER_BEAN_NAME, GXRedissonStreamMQManager.class);
    }

    @Override
    public void destroy() {
        log.info("Redisson stream MQ listener post processor stopped, registeredListenerBeans={}", successCount.get());
        interfaceImplementationCache.clear();
        registeredBeans.clear();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
