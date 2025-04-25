package cn.maple.core.framework.handler;

import cn.maple.core.framework.service.GXBotNotificationExceptionService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 异步方法未捕获异常处理器
 * <p>
 * 该类实现了Spring的AsyncUncaughtExceptionHandler接口，用于处理@Async注解标记的异步方法中未被捕获的异常。
 * 由于异步方法在单独的线程中执行，其抛出的异常不会被调用者感知，因此需要特殊的异常处理机制。
 * 本处理器会记录详细的异常信息，包括异常消息、发生异常的方法名称以及方法参数值，便于问题定位和诊断。
 * 同时，支持通过机器人通知服务发送异常通知，实现异常的实时监控。
 * </p>
 *
 * <p>
 * 使用示例：
 * 1. 在Spring配置类中注册该异常处理器：
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
 * 2. 在异步方法上使用@Async注解：
 * <pre>
 * @Service
 * public class EmailService {
 *     @Async
 *     public void sendEmail(String to, String subject, String content) {
 *         // 发送邮件的逻辑
 *         // 如果此处抛出异常，将由GXAsyncExceptionHandler处理
 *     }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 安全性说明：
 * - 异常信息记录时避免了敏感信息的泄露
 * - 参数值记录时使用了toString()方法，可能需要注意敏感参数的脱敏处理
 * - 支持通过机器人通知服务发送异常通知，便于及时发现和处理问题
 * </p>
 *
 * @author maple
 * @since 1.0.0
 */
@Slf4j
public class GXAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    /**
     * 处理异步方法中未捕获的异常
     * <p>
     * 当@Async注解标记的方法抛出未被捕获的异常时，该方法会被调用。
     * 方法会记录详细的异常信息，包括：
     * 1. 异常消息和堆栈跟踪
     * 2. 发生异常的方法名称和所属类
     * 3. 方法的参数值（用于问题复现）
     * </p>
     * <p>
     * 同时，方法会尝试通过GXBotNotificationExceptionService发送异常通知，
     * 实现异常的实时监控。如果通知服务不可用，不会影响正常的日志记录。
     * </p>
     *
     * @param throwable 未捕获的异常
     * @param method    发生异常的方法
     * @param params    方法的参数值
     */
    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
        // 记录异常基本信息
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();

        log.error("异步方法执行异常 - 类: {}, 方法: {}, 异常: {}", className, methodName, throwable.getMessage());
        log.error("异常详情", throwable);

        // 记录方法参数信息，便于问题复现
        if (params.length > 0) {
            StringBuilder paramInfo = new StringBuilder("方法参数值: ");
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                paramInfo.append("参数").append(i + 1).append("=")
                        .append(param != null ? param.toString() : "null").append(", ");
            }
            // 移除最后的逗号和空格
            if (paramInfo.length() > 2) {
                paramInfo.setLength(paramInfo.length() - 2);
            }
            log.error(paramInfo.toString());
        } else {
            log.error("方法没有参数");
        }

        // 尝试通过机器人通知服务发送异常通知
        try {
            GXBotNotificationExceptionService botNotificationService = GXSpringContextUtils.getBean(GXBotNotificationExceptionService.class);
            if (Objects.nonNull(botNotificationService)) {
                botNotificationService.botNotificationException(throwable);
                log.info("已通过机器人通知服务发送异常通知");
            }
        } catch (Exception e) {
            log.warn("发送异常通知失败: {}", e.getMessage());
        }
    }
}
