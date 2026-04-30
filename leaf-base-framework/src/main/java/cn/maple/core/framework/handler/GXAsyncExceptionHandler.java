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

@Slf4j
@Component
public class GXAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    @Resource
    private ThreadPoolTaskExecutor asyncExecutor;

    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
        StringBuilder errorMsg = new StringBuilder(512)
                .append("--------------Maple Leaf FrameWork异步调用,异常捕获--------------\n")
                .append("异常信息: ").append(throwable.getMessage()).append("\n")
                .append("方法名称: ").append(method.getName()).append("\n")
                .append("类名: ").append(method.getDeclaringClass().getName()).append("\n");

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

        if (asyncExecutor != null) {
            try {
                ThreadPoolExecutor executor = asyncExecutor.getThreadPoolExecutor();
                errorMsg.append("线程池状态: [活动线程数=").append(executor.getActiveCount())
                        .append(", 池大小=").append(executor.getPoolSize())
                        .append(", 核心池大小=").append(executor.getCorePoolSize())
                        .append(", 最大池大小=").append(executor.getMaximumPoolSize())
                        .append(", 队列大小=").append(executor.getQueue().size())
                        .append("]\n");

                double systemLoad = GXCommonUtils.getSystemLoadAverage();
                if (systemLoad >= 0) {
                    errorMsg.append("系统负载: ").append(String.format("%.2f", systemLoad)).append("\n");
                }
            } catch (Exception e) {
                log.warn("获取线程池状态信息失败: {}", e.getMessage());
            }
        }

        errorMsg.append("--------------Maple Leaf FrameWork异步调用,异常捕获--------------");

        log.error(errorMsg.toString(), throwable);

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