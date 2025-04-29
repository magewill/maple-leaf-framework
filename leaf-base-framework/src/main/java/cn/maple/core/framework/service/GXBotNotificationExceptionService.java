package cn.maple.core.framework.service;

import cn.hutool.core.collection.CollUtil;
import cn.maple.core.framework.event.GXExceptionNotifyEvent;
import cn.maple.core.framework.event.dto.GXExceptionNotifyEventDto;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 机器人通知应用异常信息服务接口
 * <p>
 * 该接口提供了一种机制，用于将应用程序中的特定异常通过机器人（如企业微信、钉钉等）发送通知给开发或运维人员。
 * 通过配置文件指定需要通知的异常类型，当这些异常发生时，会自动触发通知事件。
 * </p>
 *
 * <p>线程安全说明：</p>
 * <p>该接口的默认实现是线程安全的，因为它不维护任何状态，且使用的工具类都是线程安全的。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在配置文件中设置需要通知的异常类型
 * // application.yml
 * bot:
 *   notification:
 *     exception:
 *       - "java.lang.NullPointerException"
 *       - "cn.maple.core.framework.exception.GXBusinessException"
 *
 * // 2. 在异常处理器中使用该服务
 * @Component
 * public class GlobalExceptionHandler {
 *     @Autowired
 *     private GXBotNotificationExceptionService botNotificationService;
 *
 *     @ExceptionHandler(Exception.class)
 *     public ResponseEntity<String> handleException(Exception e) {
 *         // 发送异常通知
 *         botNotificationService.botNotificationException(e);
 *         // 处理异常...
 *     }
 * }
 * </pre>
 *
 * @author britton <britton@126.com>
 */
public interface GXBotNotificationExceptionService {
    /**
     * 处理异常并根据配置决定是否发送机器人通知
     * <p>
     * 该方法会检查传入的异常类型是否在配置的通知列表中，如果是，则创建并发布一个异常通知事件。
     * 通知事件包含异常信息和当前HTTP请求上下文，便于排查问题。
     * </p>
     * <p>
     * 实现说明：
     * 1. 从配置中获取需要通知的异常类型列表
     * 2. 检查当前异常是否在通知列表中
     * 3. 如果需要通知，则创建通知事件并发布
     * </p>
     * <p>
     * 性能考虑：该方法仅在异常类型匹配时才会创建事件对象，避免了不必要的对象创建
     * </p>
     *
     * @param throwable 需要处理的异常对象，不能为null
     */
    @SuppressWarnings("unchecked")
    default void botNotificationException(Throwable throwable) {
        List<String> botNotificationException = GXCommonUtils.getEnvironmentValue("bot.notification.exception", List.class, CollUtil.newArrayList());
        String throwableClassCanonicalName = throwable.getClass().getCanonicalName();
        if (CollUtil.contains(botNotificationException, throwableClassCanonicalName)) {
            GXExceptionNotifyEventDto exceptionNotifyEventDto = new GXExceptionNotifyEventDto();
            ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            RequestContextHolder.setRequestAttributes(servletRequestAttributes, true);
            exceptionNotifyEventDto.setThrowable(throwable);
            exceptionNotifyEventDto.setHttpServletRequest(GXCurrentRequestContextUtils.getHttpServletRequest());
            GXExceptionNotifyEvent exceptionNotifyEvent = new GXExceptionNotifyEvent(exceptionNotifyEventDto);
            GXEventPublisherUtils.publishEvent(exceptionNotifyEvent);
        }
    }
}
