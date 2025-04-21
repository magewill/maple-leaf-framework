package cn.maple.core.framework.config.aware;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;

/**
 * Spring应用上下文感知类
 * <p>
 * 该类实现了Spring的ApplicationContextAware接口，用于在Spring容器启动时
 * 自动获取ApplicationContext并设置到GXApplicationContextSingleton单例中。
 * 通过@Order注解设置为最高优先级，确保在其他组件使用前完成初始化。
 * </p>
 * <p>
 * 线程安全说明：
 * 该类在Spring容器初始化阶段被调用，此时通常是单线程环境。
 * 即使在多线程环境下，也通过GXApplicationContextSingleton的线程安全机制确保安全。
 * </p>
 * <p>
 * 使用场景：
 * 该类无需直接使用，Spring容器会自动调用其setApplicationContext方法。
 * 需要获取ApplicationContext时，应通过GXApplicationContextSingleton.INSTANCE获取。
 * </p>
 *
 * @author britton chen
 * @see GXApplicationContextSingleton
 * @since 1.0.0
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
@SuppressWarnings("all")
public class GXApplicationContextAware implements ApplicationContextAware {
    /**
     * Spring容器启动时自动调用此方法，设置ApplicationContext
     * <p>
     * 该方法将ApplicationContext设置到GXApplicationContextSingleton单例中，
     * 使其在应用的任何位置都可以访问Spring容器。
     * 方法仅在ApplicationContext尚未设置时执行设置操作，确保不会覆盖已有实例。
     * </p>
     *
     * @param applicationContext Spring应用上下文
     * @throws BeansException 如果设置过程中发生异常
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        if (GXApplicationContextSingleton.INSTANCE.getApplicationContext() == null) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
        }
    }
}