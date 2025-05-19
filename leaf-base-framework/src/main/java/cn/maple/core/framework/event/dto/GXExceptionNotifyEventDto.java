package cn.maple.core.framework.event.dto;

import cn.maple.core.framework.dto.GXBaseEventDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 异常通知事件数据传输对象
 * <p>
 * 该类用于封装异常信息和相关的HTTP请求信息，作为异常通知事件的数据载体。
 * 通常用于全局异常处理中，将捕获到的异常信息通过事件机制传递给异常处理监听器，
 * 以便进行日志记录、告警通知等操作。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在全局异常处理器中创建异常通知事件DTO
 * @ControllerAdvice
 * public class GlobalExceptionHandler {
 *
 *     @Autowired
 *     private ApplicationEventPublisher eventPublisher;
 *
 *     @ExceptionHandler(Exception.class)
 *     public ResponseEntity<String> handleException(Exception ex, HttpServletRequest request) {
 *         // 创建异常通知事件DTO
 *         GXExceptionNotifyEventDto eventDto = new GXExceptionNotifyEventDto();
 *         eventDto.setThrowable(ex);
 *         eventDto.setHttpServletRequest(request);
 *
 *         // 发布异常通知事件
 *         eventPublisher.publishEvent(new GXExceptionNotifyEvent(eventDto, "EXCEPTION", "SYSTEM_ERROR"));
 *
 *         // 返回错误响应
 *         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("系统异常，请稍后再试");
 *     }
 * }
 *
 * // 2. 在异常监听器中处理异常通知事件
 * @Component
 * public class ExceptionNotifyListener {
 *
 *     private static final Logger logger = LoggerFactory.getLogger(ExceptionNotifyListener.class);
 *
 *     @EventListener
 *     public void handleExceptionNotifyEvent(GXExceptionNotifyEvent event) {
 *         GXExceptionNotifyEventDto eventDto = event.getSource();
 *         Throwable throwable = eventDto.getThrowable();
 *         HttpServletRequest request = eventDto.getHttpServletRequest();
 *
 *         // 记录异常日志
 *         logger.error("异常发生: {}, URL: {}, 方法: {}",
 *             throwable.getMessage(),
 *             request.getRequestURL(),
 *             request.getMethod(),
 *             throwable);
 *
 *         // 可以进一步处理，如发送告警邮件、短信等
 *     }
 * }
 * </pre>
 *
 * @author britton
 * @see GXBaseEventDto
 * @see cn.maple.core.framework.event.GXExceptionNotifyEvent
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GXExceptionNotifyEventDto extends GXBaseEventDto {
    /**
     * 异常对象
     * <p>
     * 用于存储捕获到的异常信息，包括异常类型、异常消息和堆栈跟踪等。
     * 通过该字段，异常处理监听器可以获取完整的异常详情。
     * </p>
     */
    private Throwable throwable;

    /**
     * HTTP请求对象
     * <p>
     * 用于存储发生异常时的HTTP请求信息，包括请求URL、请求方法、请求参数、请求头等。
     * 使用transient关键字标记，确保在序列化时不会被包含，避免序列化问题。
     * </p>
     */
    private transient HttpServletRequest httpServletRequest;
}
