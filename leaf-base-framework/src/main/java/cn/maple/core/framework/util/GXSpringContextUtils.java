package cn.maple.core.framework.util;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.Objects;

public class GXSpringContextUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXSpringContextUtils.class);

    @Getter
    private static final ApplicationContext applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();

    private GXSpringContextUtils() {
    }

    public static Object getBean(String name) {
        if (Objects.isNull(name) || Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getBean(name);
        } catch (Exception e) {
            LOG.warn("获取Bean失败: 名称={}, 错误={}", name, e.getMessage());
        }
        return null;
    }

    public static <T> T getBean(Class<T> clazz) {
        if (Objects.isNull(clazz) || Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getBean(clazz);
        } catch (Exception e) {
            LOG.debug("获取Bean失败: 类型={}, 错误={}", clazz.getSimpleName(), e.getMessage());
        }
        return null;
    }

    public static <T> T getBean(String name, Class<T> requiredType) {
        if (Objects.isNull(name) || Objects.isNull(requiredType) || Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getBean(name, requiredType);
        } catch (Exception e) {
            LOG.debug("获取Bean失败: 名称={}, 类型={}, 错误={}", name, requiredType.getSimpleName(), e.getMessage());
        }
        return null;
    }

    public static boolean containsBean(String name) {
        if (Objects.isNull(name) || Objects.isNull(applicationContext)) {
            return false;
        }
        try {
            return applicationContext.containsBean(name);
        } catch (Exception e) {
            LOG.debug("检查Bean是否存在时发生错误: 名称={}, 错误={}", name, e.getMessage());
            return false;
        }
    }

    public static boolean isSingleton(String name) {
        if (Objects.isNull(name) || Objects.isNull(applicationContext) || !containsBean(name)) {
            return false;
        }
        try {
            return applicationContext.isSingleton(name);
        } catch (Exception e) {
            LOG.debug("检查Bean是否为单例时发生错误: 名称={}, 错误={}", name, e.getMessage());
            return false;
        }
    }

    public static Class<?> getType(String name) {
        if (Objects.isNull(name) || Objects.isNull(applicationContext) || !containsBean(name)) {
            return null;
        }
        try {
            return applicationContext.getType(name);
        } catch (Exception e) {
            LOG.debug("获取Bean类型失败: 名称={}, 错误={}", name, e.getMessage());
            return null;
        }
    }

    public static <T> Map<String, T> getBeans(Class<T> clazz) {
        if (Objects.isNull(clazz) || Objects.isNull(applicationContext)) {
            return Map.of();
        }
        try {
            return applicationContext.getBeansOfType(clazz);
        } catch (Exception e) {
            LOG.debug("获取所有Bean失败: 类型={}, 错误={}", clazz.getSimpleName(), e.getMessage());
            return Map.of();
        }
    }

    public static Environment getEnvironment() {
        if (Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getEnvironment();
        } catch (Exception e) {
            LOG.debug("获取Environment失败: 错误={}", e.getMessage());
            return null;
        }
    }

    public static void registerSingleton(String beanName, Object singletonObject) {
        if (Objects.isNull(beanName) || beanName.trim().isEmpty()) {
            throw new IllegalArgumentException("Bean名称不能为null或空");
        }
        if (Objects.isNull(singletonObject)) {
            throw new IllegalArgumentException("Bean实例不能为null");
        }
        if (Objects.isNull(applicationContext)) {
            LOG.error("ApplicationContext为null，无法注册Bean: {}", beanName);
            return;
        }

        try {
            if (null == getBean(singletonObject.getClass())) {
                if (applicationContext instanceof AbstractApplicationContext) {
                    ((AbstractApplicationContext) applicationContext).getBeanFactory().registerSingleton(beanName, singletonObject);
                    LOG.debug("成功注册单例Bean: 名称={}, 类型={}", beanName, singletonObject.getClass().getName());
                } else {
                    LOG.debug("无法注册Bean: ApplicationContext不是AbstractApplicationContext类型");
                }
            } else {
                LOG.debug("已存在类型为{}的Bean，跳过注册", singletonObject.getClass().getName());
            }
        } catch (Exception e) {
            LOG.error("注册单例Bean失败: 名称={}, 类型={}, 错误={}", beanName, singletonObject.getClass().getName(), e.getMessage(), e);
        }
    }
}
