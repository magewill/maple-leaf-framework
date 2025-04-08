package cn.maple.core.framework.util;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * Spring上下文工具类
 * <p>
 * 提供对Spring ApplicationContext的访问能力，用于在非Spring管理的类中
 * 获取Spring Bean、判断Bean的特性、获取环境配置以及注册单例Bean等操作。
 * 该类通过GXApplicationContextSingleton枚举单例获取ApplicationContext实例。
 * </p>
 * 
 * @author maple
 */
public class GXSpringContextUtils {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXSpringContextUtils.class);

    /**
     * Spring应用上下文，通过GXApplicationContextSingleton单例获取
     */
    private static final ApplicationContext applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();

    /**
     * 私有构造函数，防止实例化
     */
    private GXSpringContextUtils() {
    }

    /**
     * 根据Bean名称获取Bean实例
     *
     * @param name Bean的名称
     * @return 返回Bean实例，如果获取失败则返回null
     */
    public static Object getBean(String name) {
        try {
            return applicationContext.getBean(name);
        } catch (RuntimeException e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 根据Bean类型获取Bean实例
     *
     * @param clazz Bean的类型
     * @param <T>   Bean的泛型类型
     * @return 返回指定类型的Bean实例，如果获取失败则返回null
     */
    public static <T> T getBean(Class<T> clazz) {
        try {
            return applicationContext.getBean(clazz);
        } catch (Exception e) {
            LOG.warn(String.format("记录获取Bean的信息, 不影响业务, Bean获取出错 : %s / %s", clazz.getSimpleName(), e.getMessage()));
        }
        return null;
    }

    /**
     * 根据Bean名称和类型获取Bean实例
     *
     * @param name         Bean的名称
     * @param requiredType Bean的类型
     * @param <T>          Bean的泛型类型
     * @return 返回指定名称和类型的Bean实例，如果获取失败则返回null
     */
    public static <T> T getBean(String name, Class<T> requiredType) {
        try {
            return applicationContext.getBean(name, requiredType);
        } catch (RuntimeException e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 判断是否包含指定名称的Bean
     *
     * @param name Bean的名称
     * @return 如果包含返回true，否则返回false
     */
    public static boolean containsBean(String name) {
        return applicationContext.containsBean(name);
    }

    /**
     * 判断指定名称的Bean是否为单例
     *
     * @param name Bean的名称
     * @return 如果是单例返回true，否则返回false
     */
    public static boolean isSingleton(String name) {
        return applicationContext.isSingleton(name);
    }

    /**
     * 获取指定名称Bean的类型
     *
     * @param name Bean的名称
     * @return 返回Bean的类型，如果Bean不存在则返回null
     */
    public static Class<?> getType(String name) {
        return applicationContext.getType(name);
    }

    /**
     * 获取指定类型的所有Bean
     *
     * @param clazz Bean的类型
     * @param <T>   Bean的泛型类型
     * @return 返回指定类型的所有Bean，key为Bean名称，value为Bean实例
     */
    public static <T> Map<String, T> getBeans(Class<T> clazz) {
        return applicationContext.getBeansOfType(clazz);
    }

    /**
     * 获取Spring环境配置
     *
     * @return 返回Environment对象，可用于获取配置属性
     */
    public static Environment getEnvironment() {
        return applicationContext.getEnvironment();
    }

    /**
     * 注册单例Bean到Spring容器
     * <p>
     * 只有当容器中不存在该类型的Bean时才会注册
     * </p>
     *
     * @param beanName        Bean的名称
     * @param singletonObject Bean的实例对象
     */
    public static void registerSingleton(String beanName, Object singletonObject) {
        if (null == getBean(singletonObject.getClass())) {
            ((AbstractApplicationContext) applicationContext).getBeanFactory().registerSingleton(beanName, singletonObject);
        }
    }

    /**
     * 获取Spring应用上下文
     *
     * @return 返回ApplicationContext对象
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
