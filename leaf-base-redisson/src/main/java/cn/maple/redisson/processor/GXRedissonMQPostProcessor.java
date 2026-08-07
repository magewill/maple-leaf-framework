package cn.maple.redisson.processor;

import cn.maple.redisson.listener.GXRedissonMQListener;
import cn.maple.redisson.util.GXRedissonMQUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@ConditionalOnBean(name = "redissonMQClient")
public class GXRedissonMQPostProcessor implements BeanPostProcessor, DisposableBean, PriorityOrdered {
    private static final Class<?> TARGET_INTERFACE = GXRedissonMQListener.class;

    private final Map<Class<?>, Boolean> interfaceImplementationCache = new ConcurrentHashMap<>();
    private final Map<String, String> registeredListeners = new ConcurrentHashMap<>();
    private final Set<String> registeredBeans = ConcurrentHashMap.newKeySet();
    private final AtomicInteger successCount = new AtomicInteger(0);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!isListenerBean(bean)) {
            return bean;
        }
        if (!registeredBeans.add(beanName)) {
            log.warn("Redisson MQ listener bean [{}] has already been processed, skip duplicate registration", beanName);
            return bean;
        }
        registerListener((GXRedissonMQListener) bean, beanName);
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
            Set<String> partiallyRegisteredKeys = recordNewListeners(beforeListeners);
            if (!partiallyRegisteredKeys.isEmpty()) {
                int cleanupFailures = unsubscribeListeners(partiallyRegisteredKeys);
                if (cleanupFailures > 0) {
                    log.error("Failed to compensate partial Redisson MQ listener registration, bean={}, failed={}",
                            beanName, cleanupFailures);
                }
            }
            registeredBeans.remove(beanName);
            throw new IllegalStateException("Failed to register Redisson MQ listener bean [" + beanName + "]", e);
        }

        Set<String> newListenerKeys = recordNewListeners(beforeListeners);
        if (newListenerKeys.isEmpty()) {
            log.warn("Redisson MQ listener bean [{}] did not register a new listener. It may already be registered.", beanName);
            return;
        }
        successCount.incrementAndGet();
        log.info("Registered Redisson MQ listener bean [{}], newListenerCount={}", beanName, newListenerKeys.size());
    }

    private Set<String> recordNewListeners(Map<String, String> beforeListeners) {
        Map<String, String> afterListeners = GXRedissonMQUtils.getAllLocalListeners();
        Set<String> beforeKeys = new HashSet<>(beforeListeners.keySet());
        Set<String> newListenerKeys = new HashSet<>();
        for (Map.Entry<String, String> entry : afterListeners.entrySet()) {
            if (!beforeKeys.contains(entry.getKey())) {
                registeredListeners.put(entry.getKey(), entry.getValue());
                newListenerKeys.add(entry.getKey());
                log.debug("Recorded Redisson MQ listener, key={}, listenerId={}", entry.getKey(), entry.getValue());
            }
        }
        return newListenerKeys;
    }

    @Override
    public void destroy() {
        int listenerCount = registeredListeners.size();
        if (listenerCount == 0) {
            log.info("No Redisson MQ listeners need to be unsubscribed");
        }

        int unsubscribeFailCount = unsubscribeListeners(new HashSet<>(registeredListeners.keySet()));
        int unsubscribeSuccessCount = listenerCount - unsubscribeFailCount;
        if (GXRedissonMQUtils.getAllLocalListeners().isEmpty()) {
            GXRedissonMQUtils.clearLocalCache();
        } else {
            log.warn("Retaining local Redisson MQ listener cache because subscriptions remain after shutdown, count={}",
                    GXRedissonMQUtils.getAllLocalListeners().size());
        }

        log.info("Redisson MQ listener unsubscribe finished, success={}, failed={}, registeredBeans={}, registeredListenerBeans={}",
                unsubscribeSuccessCount, unsubscribeFailCount, registeredBeans.size(), successCount.get());
        clearLocalState();
    }

    private int unsubscribeListeners(Set<String> cacheKeys) {
        int unsubscribeFailCount = 0;
        for (String cacheKey : cacheKeys) {
            try {
                GXRedissonMQUtils.unsubscribeByCacheKey(cacheKey);
                registeredListeners.remove(cacheKey);
            } catch (Exception e) {
                unsubscribeFailCount++;
                log.error("Failed to unsubscribe Redisson MQ listener, cacheKey={}", cacheKey, e);
            }
        }
        return unsubscribeFailCount;
    }

    private void clearLocalState() {
        interfaceImplementationCache.clear();
        registeredBeans.clear();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
