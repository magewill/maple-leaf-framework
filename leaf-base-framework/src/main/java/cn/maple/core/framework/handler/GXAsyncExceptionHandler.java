package cn.maple.core.framework.handler;

import cn.maple.core.framework.service.GXBotNotificationExceptionService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步方法未捕获异常处理器
 * <p>
 * 该类实现了Spring的AsyncUncaughtExceptionHandler接口,用于处理@Async注解标记的异步方法中未被捕获的异常。
 * 由于异步方法在单独的线程中执行,其抛出的异常不会被调用者感知,因此需要特殊的异常处理机制。
 * 本处理器会记录详细的异常信息,包括异常消息、发生异常的方法名称以及方法参数值,便于问题定位和诊断。
 * 同时,支持通过机器人通知服务发送异常通知,实现异常的实时监控。
 * </p>
 *
 * <p>
 * 使用示例:
 * 1. 在Spring配置类中注册该异常处理器:
 * <pre>
 * @Configuration
 * @EnableAsync
 * public class AsyncConfig implements AsyncConfigurer {
 *     @Override
 *     public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
 *         return new GXAsyncExceptionHandler();
 *     }
 * }
 * </pre>
 * <p>
 * 2. 在异步方法上使用@Async注解:
 * <pre>
 * @Service
 * public class EmailService {
 *     @Async
 *     public void sendEmail(String to, String subject, String content) {
 *         // 发送邮件的逻辑
 *         // 如果此处抛出异常,将由GXAsyncExceptionHandler处理
 *     }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 安全性说明:
 * - 异常信息记录时避免了敏感信息的泄露
 * - 参数值记录时使用了toString()方法,可能需要注意敏感参数的脱敏处理
 * - 支持通过机器人通知服务发送异常通知,便于及时发现和处理问题
 * </p>
 *
 * @author maple
 * @since 1.0.0
 */
@Slf4j
@Component
public class GXAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    @Resource
    private ThreadPoolTaskExecutor asyncExecutor;

    /**
     * 处理异步方法中未捕获的异常
     * <p>
     * 当@Async注解标记的方法抛出未被捕获的异常时,该方法会被调用。
     * 方法会记录详细的异常信息,包括:
     * 1. 异常消息和堆栈跟踪
     * 2. 发生异常的方法名称和所属类
     * 3. 方法的参数值(用于问题复现)
     * </p>
     * <p>
     * 同时,方法会尝试通过GXBotNotificationExceptionService发送异常通知,
     * 实现异常的实时监控。如果通知服务不可用,不会影响正常的日志记录。
     * </p>
     *
     * @param throwable 未捕获的异常
     * @param method    发生异常的方法
     * @param params    方法的参数值
     */
    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
        // 使用 StringBuilder 高效拼接日志信息
        StringBuilder errorMsg = new StringBuilder(512)
                .append("--------------Maple Leaf FrameWork异步调用,异常捕获--------------\n")
                .append("异常信息: ").append(throwable.getMessage()).append("\n")
                .append("方法名称: ").append(method.getName()).append("\n")
                .append("类名: ").append(method.getDeclaringClass().getName()).append("\n");

        // 安全地记录参数,避免 toString() 异常
        if (params.length > 0) {
            errorMsg.append("方法参数:\n");
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                String paramValue;
                try {
                    paramValue = param != null ? param.toString() : "null";
                } catch (Exception e) {
                    paramValue = "参数转换为字符串时出错: " + e.getMessage();
                }
                errorMsg.append("  参数[").append(i).append("]: ").append(paramValue).append("\n");
            }
        }

        // 添加线程池状态信息,帮助诊断是否与线程池相关
        if (asyncExecutor != null) {
            try {
                ThreadPoolExecutor executor = asyncExecutor.getThreadPoolExecutor();
                errorMsg.append("线程池状态: [活动线程数=").append(executor.getActiveCount())
                        .append(", 池大小=").append(executor.getPoolSize())
                        .append(", 核心池大小=").append(executor.getCorePoolSize())
                        .append(", 最大池大小=").append(executor.getMaximumPoolSize())
                        .append(", 队列大小=").append(executor.getQueue().size())
                        .append("]\n");

                // 添加系统负载信息
                double systemLoad = GXCommonUtils.getSystemLoadAverage();
                if (systemLoad >= 0) {
                    errorMsg.append("系统负载: ").append(String.format("%.2f", systemLoad)).append("\n");
                }
            } catch (Exception e) {
                log.warn("获取线程池状态信息失败: {}", e.getMessage());
            }
        }

        errorMsg.append("--------------Maple Leaf FrameWork异步调用,异常捕获--------------");

        // 先记录错误日志
        log.error(errorMsg.toString(), throwable);

        // 尝试通过机器人通知服务发送异常通知
        try {
            GXBotNotificationExceptionService botNotificationService =
                    GXSpringContextUtils.getBean(GXBotNotificationExceptionService.class);
            if (Objects.nonNull(botNotificationService)) {
                botNotificationService.botNotificationException(throwable);
                log.info("已通过机器人通知服务发送异常通知");
            }
        } catch (Exception e) {
            log.warn("发送异常通知失败: {}", e.getMessage());
        }
    }
}