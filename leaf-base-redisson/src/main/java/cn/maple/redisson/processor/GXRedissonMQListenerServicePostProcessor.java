package cn.maple.redisson.processor;

import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.redisson.listener.GXRedissonMQListener;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * Redisson消息队列监听器服务处理器
 * <p>
 * 该处理器用于自动检测并注册实现了GXRedissonMQListener接口的Bean。
 * 当Spring容器中的Bean实现了GXRedissonMQListener接口时，
 * 该处理器会自动调用Bean的registerRedissonListener方法，
 * 从而实现Redisson消息队列监听器的自动注册。
 * </p>
 * <p>
 * 工作原理：
 * 1. 在Bean初始化完成后检查其是否实现了GXRedissonMQListener接口
 * 2. 如果实现了该接口，则通过反射调用Bean的registerRedissonListener方法
 * 3. 该方法应当包含监听器的注册逻辑，如设置监听的主题和处理消息的回调
 * </p>
 * <p>
 * 使用该处理器可以简化Redisson消息队列监听器的注册过程，
 * 开发者只需实现GXRedissonMQListener接口并提供registerRedissonListener方法即可。
 * </p>
 * <p>
 * 安全性说明：
 * 1. 该处理器在多线程环境下是安全的，因为Spring容器确保BeanPostProcessor的调用是线程安全的
 * 2. 通过反射调用方法时进行了空值检查和异常捕获，防止因空指针或反射异常导致应用崩溃
 * 3. 使用日志记录关键操作和异常情况，便于问题排查和监控
 * </p>
 */
@Component
@Log4j2
@Lazy
public class GXRedissonMQListenerServicePostProcessor implements BeanPostProcessor {
    /**
     * Bean初始化前的处理
     * 本处理器不需要在Bean初始化前执行操作，直接返回Bean
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * Bean初始化后的处理
     * <p>
     * 检查Bean是否实现了GXRedissonMQListener接口，如果是，则调用其registerRedissonListener方法。
     * 该方法使用了线程安全的方式检查接口实现，并通过反射调用注册方法。
     * </p>
     * <p>
     * 安全处理：
     * 1. 对传入的bean进行空值检查，避免空指针异常
     * 2. 使用try-catch块捕获可能的异常，确保单个Bean的处理失败不会影响其他Bean
     * 3. 详细记录异常信息，便于问题排查
     * </p>
     *
     * @param bean     Bean实例
     * @param beanName Bean名称
     * @return 处理后的Bean实例
     * @throws BeansException 如果处理过程中发生异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (ObjectUtil.isNull(bean)) {
            return null;
        }

        try {
            // 获取目标接口名称
            String targetSimpleName = GXRedissonMQListener.class.getSimpleName();
            // 获取Bean实现的所有接口
            Class<?>[] allInterfacesForClass = ClassUtils.getAllInterfacesForClass(bean.getClass());

            // 检查Bean是否实现了GXRedissonMQListener接口
            for (Class<?> interfaceClass : allInterfacesForClass) {
                // 如果Bean实现了GXRedissonMQListener接口，则调用其registerRedissonListener方法
                if (interfaceClass.getSimpleName().equals(targetSimpleName)) {
                    try {
                        GXCommonUtils.reflectCallObjectMethod(bean, "registerRedissonListener");
                        log.info("成功注册Redisson的PUB/SUB监听器: {}", bean.getClass().getSimpleName());
                    } catch (Exception e) {
                        log.error("注册Redisson的PUB/SUB监听器失败: {} - {}", bean.getClass().getSimpleName(), e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理Bean [{}] 时发生异常: {}", beanName, e.getMessage(), e);
        }

        return bean;
    }
}