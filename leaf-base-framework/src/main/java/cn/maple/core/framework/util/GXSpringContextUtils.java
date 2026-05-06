package cn.maple.core.framework.util;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.Objects;

public class GXSpringContextUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXSpringContextUtils.class);

    private GXSpringContextUtils() {
    }

    public static ApplicationContext getApplicationContext() {
        return GXApplicationContextSingleton.INSTANCE.getApplicationContext();
    }

    public static Object getBean(String name) {
        ApplicationContext applicationContext = getApplicationContext();
        if (Objects.isNull(name) || Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getBean(name);
        } catch (Exception e) {
            LOG.warn("Failed to get bean: name={}, error={}", name, e.getMessage());
        }
        return null;
    }

    public static <T> T getBean(Class<T> clazz) {
        ApplicationContext applicationContext = getApplicationContext();
        if (Objects.isNull(clazz) || Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getBean(clazz);
        } catch (Exception e) {
            LOG.debug("Failed to get bean: type={}, error={}", clazz.getSimpleName(), e.getMessage());
        }
        return null;
    }

    public static <T> T getBean(String name, Class<T> requiredType) {
        ApplicationContext applicationContext = getApplicationContext();
        if (Objects.isNull(name) || Objects.isNull(requiredType) || Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getBean(name, requiredType);
        } catch (Exception e) {
            LOG.debug("Failed to get bean: name={}, type={}, error={}", name, requiredType.getSimpleName(), e.getMessage());
        }
        return null;
    }

    public static boolean containsBean(String name) {
        ApplicationContext applicationContext = getApplicationContext();
        if (Objects.isNull(name) || Objects.isNull(applicationContext)) {
            return false;
        }
        try {
            return applicationContext.containsBean(name);
        } catch (Exception e) {
            LOG.debug("Failed to check bean existence: name={}, error={}", name, e.getMessage());
            return false;
        }
    }

    public static boolean isSingleton(String name) {
        ApplicationContext applicationContext = getApplicationContext();
        if (Objects.isNull(name) || Objects.isNull(applicationContext) || !containsBean(name)) {
            return false;
        }
        try {
            return applicationContext.isSingleton(name);
        } catch (Exception e) {
            LOG.debug("Failed to check singleton bean: name={}, error={}", name, e.getMessage());
            return false;
        }
    }

    public static Class<?> getType(String name) {
        ApplicationContext applicationContext = getApplicationContext();
        if (Objects.isNull(name) || Objects.isNull(applicationContext) || !containsBean(name)) {
            return null;
        }
        try {
            return applicationContext.getType(name);
        } catch (Exception e) {
            LOG.debug("Failed to get bean type: name={}, error={}", name, e.getMessage());
            return null;
        }
    }

    public static <T> Map<String, T> getBeans(Class<T> clazz) {
        ApplicationContext applicationContext = getApplicationContext();
        if (Objects.isNull(clazz) || Objects.isNull(applicationContext)) {
            return Map.of();
        }
        try {
            return applicationContext.getBeansOfType(clazz);
        } catch (Exception e) {
            LOG.debug("Failed to get beans: type={}, error={}", clazz.getSimpleName(), e.getMessage());
            return Map.of();
        }
    }

    public static Environment getEnvironment() {
        ApplicationContext applicationContext = getApplicationContext();
        if (Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getEnvironment();
        } catch (Exception e) {
            LOG.debug("Failed to get environment: error={}", e.getMessage());
            return null;
        }
    }

    public static void registerSingleton(String beanName, Object singletonObject) {
        ApplicationContext applicationContext = getApplicationContext();
        if (Objects.isNull(beanName) || beanName.trim().isEmpty()) {
            throw new IllegalArgumentException("Bean name must not be null or empty");
        }
        if (Objects.isNull(singletonObject)) {
            throw new IllegalArgumentException("Bean instance must not be null");
        }
        if (Objects.isNull(applicationContext)) {
            LOG.error("ApplicationContext is null, cannot register bean: {}", beanName);
            return;
        }

        try {
            if (null == getBean(singletonObject.getClass())) {
                if (applicationContext instanceof AbstractApplicationContext) {
                    ((AbstractApplicationContext) applicationContext).getBeanFactory().registerSingleton(beanName, singletonObject);
                    LOG.debug("Registered singleton bean: name={}, type={}", beanName, singletonObject.getClass().getName());
                } else {
                    LOG.debug("Cannot register bean: ApplicationContext is not AbstractApplicationContext");
                }
            } else {
                LOG.debug("Bean type already exists, skip register: type={}", singletonObject.getClass().getName());
            }
        } catch (Exception e) {
            LOG.error("Failed to register singleton bean: name={}, type={}, error={}", beanName, singletonObject.getClass().getName(), e.getMessage(), e);
        }
    }
}
