package cn.maple.redisson.processor;

import cn.maple.redisson.listener.GXRedissonMQListener;
import cn.maple.redisson.util.GXRedissonMQUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registers beans that implement {@link GXRedissonMQListener}.
 */
@Component
@Log4j2
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false}")
public class GXRedissonMQPostProcessor implements BeanPostProcessor, DisposableBean, PriorityOrdered {
    private static final Class<?> TARGET_INTERFACE = GXRedissonMQListener.class;

    private final Map<Class<?>, Boolean> interfaceImplementationCache = new ConcurrentHashMap<>();
    private final Map<String, String> registeredListeners = new ConcurrentHashMap<>();
    private final Set<String> registeredBeans = ConcurrentHashMap.newKeySet();
    private final AtomicInteger successCount = new AtomicInteger(0);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        try {
            if (!isListenerBean(bean)) {
                return bean;
            }
            if (!registeredBeans.add(beanName)) {
                log.warn("Redisson MQ listener bean [{}] has already been processed, skip duplicate registration", beanName);
                return bean;
            }
            registerListener((GXRedissonMQListener) bean, beanName);
        } catch (Exception e) {
            log.error("Failed to process Redisson MQ listener bean [{}]", beanName, e);
        }
        return bean;
    }

    private boolean isListenerBean(Object bean) {
        if (bean == null) {
            return false;
        }
        if (bean instanceof GXRedissonMQListener) {
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

    private void registerListener(GXRedissonMQListener listener, String beanName) {
        Map<String, String> beforeListeners = GXRedissonMQUtils.getAllLocalListeners();
        try {
            listener.registerRedissonListener();
        } catch (Exception e) {
            registeredBeans.remove(beanName);
            log.error("Failed to register Redisson MQ listener bean [{}]", beanName, e);
            return;
        }

        Map<String, String> afterListeners = GXRedissonMQUtils.getAllLocalListeners();
        Set<String> beforeKeys = new HashSet<>(beforeListeners.keySet());
        int newListenerCount = 0;
        for (Map.Entry<String, String> entry : afterListeners.entrySet()) {
            if (!beforeKeys.contains(entry.getKey())) {
                registeredListeners.put(entry.getKey(), entry.getValue());
                newListenerCount++;
                log.debug("Recorded Redisson MQ listener, key={}, listenerId={}", entry.getKey(), entry.getValue());
            }
        }

        if (newListenerCount == 0) {
            log.warn("Redisson MQ listener bean [{}] did not register a new listener. It may already be registered.", beanName);
            return;
        }
        successCount.incrementAndGet();
        log.info("Registered Redisson MQ listener bean [{}], newListenerCount={}", beanName, newListenerCount);
    }

    @Override
    public void destroy() {
        if (registeredListeners.isEmpty()) {
            log.info("No Redisson MQ listeners need to be unsubscribed");
            clearLocalState();
            return;
        }

        int unsubscribeSuccessCount = 0;
        int unsubscribeFailCount = 0;
        for (String cacheKey : registeredListeners.keySet()) {
            try {
                GXRedissonMQUtils.unsubscribeByCacheKey(cacheKey);
                unsubscribeSuccessCount++;
            } catch (Exception e) {
                unsubscribeFailCount++;
                log.error("Failed to unsubscribe Redisson MQ listener, cacheKey={}", cacheKey, e);
            }
        }

        log.info("Redisson MQ listener unsubscribe finished, success={}, failed={}, registeredBeans={}, registeredListenerBeans={}",
                unsubscribeSuccessCount, unsubscribeFailCount, registeredBeans.size(), successCount.get());
        clearLocalState();
    }

    private void clearLocalState() {
        interfaceImplementationCache.clear();
        registeredListeners.clear();
        registeredBeans.clear();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
