package cn.maple.core.framework.config.aware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Objects;

/**
 * Spring应用上下文单例持有类
 * <p>
 * 该类使用枚举实现单例模式，用于在应用的任何位置获取Spring的ApplicationContext对象。
 * 枚举实现的单例模式具有以下优势：
 * 1. 线程安全 - Java保证枚举实例的创建是线程安全的
 * 2. 序列化安全 - 枚举的序列化机制能防止多实例问题
 * 3. 反射安全 - 枚举构造器受保护，防止通过反射创建新实例
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 获取ApplicationContext
 * ApplicationContext context = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
 * 
 * // 从Spring容器获取Bean
 * UserService userService = context.getBean(UserService.class);
 * </pre>
 * </p>
 * 
 * @author britton chen
 * @since 1.0.0
 */
@SuppressWarnings("all")
public enum GXApplicationContextSingleton {
    /**
     * 单例实例
     */
    INSTANCE;

    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXApplicationContextSingleton.class);

    /**
     * Spring应用上下文环境
     * 使用volatile关键字确保多线程环境下的可见性
     */
    private volatile ApplicationContext applicationContext;

    /**
     * 私有构造函数
     * 枚举类型的构造函数默认是私有的，这里显式声明以增强代码可读性
     */
    GXApplicationContextSingleton() {
    }

    /**
     * 获取ApplicationContext实例
     * <p>
     * 该方法是线程安全的，可以在多线程环境下安全调用
     * </p>
     *
     * @return Spring应用上下文，如果尚未设置则可能返回null
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 设置ApplicationContext实例
     * <p>
     * 该方法使用双重检查锁定模式确保在多线程环境下只设置一次ApplicationContext
     * 这是一种内存安全的实现，避免了不必要的同步开销
     * </p>
     *
     * @param applicationContext Spring应用上下文实例
     */
    public void setApplicationContext(ApplicationContext applicationContext) {
        LOG.info("GXApplicationContextSingleton类设置ApplicationContext对象被调用");
        if (Objects.isNull(this.applicationContext)) {
            synchronized (this) {
                if (Objects.isNull(this.applicationContext)) {
                    this.applicationContext = applicationContext;
                    LOG.info("ApplicationContext已成功设置到GXApplicationContextSingleton");
                }
            }
        }
    }
}