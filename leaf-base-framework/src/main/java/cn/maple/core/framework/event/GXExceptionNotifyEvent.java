package cn.maple.core.framework.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.dto.GXExceptionNotifyEventDto;

/**
 * 异常通知事件
 * <p>
 * 该类用于封装异常通知事件，继承自GXBaseEvent，专门用于处理系统中发生的异常。
 * 当系统中发生异常时，可以通过该事件将异常信息传递给相应的监听器进行处理，
 * 如记录日志、发送告警通知等。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在全局异常处理器中创建并发布异常通知事件
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
 *         // 创建并发布异常通知事件
 *         // 参数1: 事件源数据
 *         // 参数2: 事件名称，如"EXCEPTION"
 *         // 参数3: 事件类型，如"SYSTEM_ERROR"、"BUSINESS_ERROR"等
 *         GXExceptionNotifyEvent event = new GXExceptionNotifyEvent(eventDto, "EXCEPTION", "SYSTEM_ERROR");
 *         eventPublisher.publishEvent(event);
 *
 *         // 返回错误响应
 *         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("系统异常，请稍后再试");
 *     }
 * }
 *
 * // 2. 创建异常事件监听器
 * @Component
 * public class ExceptionEventListener {
 *
 *     private static final Logger logger = LoggerFactory.getLogger(ExceptionEventListener.class);
 *
 *     @EventListener
 *     public void handleExceptionEvent(GXExceptionNotifyEvent event) {
 *         GXExceptionNotifyEventDto source = event.getSource();
 *         Throwable throwable = source.getThrowable();
 *         HttpServletRequest request = source.getHttpServletRequest();
 *
 *         // 获取事件名称和类型
 *         String eventName = event.getEventName();
 *         String eventType = event.getEventType();
 *
 *         // 记录异常日志
 *         logger.error("异常事件: {}, 类型: {}, 异常信息: {}, URL: {}",
 *             eventName,
 *             eventType,
 *             throwable.getMessage(),
 *             request != null ? request.getRequestURL() : "未知URL",
 *             throwable);
 *
 *         // 根据不同的事件类型进行不同的处理
 *         if ("SYSTEM_ERROR".equals(eventType)) {
 *             // 系统错误处理逻辑，如发送告警邮件给运维团队
 *         } else if ("BUSINESS_ERROR".equals(eventType)) {
 *             // 业务错误处理逻辑，如记录业务异常统计
 *         }
 *     }
 * }
 * </pre>
 *
 * @author britton
 * @see GXBaseEvent
 * @see cn.maple.core.framework.event.dto.GXExceptionNotifyEventDto
 */
public class GXExceptionNotifyEvent extends GXBaseEvent<GXExceptionNotifyEventDto> {
    /**
     * 创建异常通知事件
     *
     * @param source 异常通知事件数据传输对象，包含异常信息和HTTP请求信息
     */
    public GXExceptionNotifyEvent(GXExceptionNotifyEventDto source) {
        super(source);
    }

    /**
     * 创建异常通知事件
     *
     * @param source    异常通知事件数据传输对象，包含异常信息和HTTP请求信息
     * @param eventType 事件类型，用于区分不同类型的异常，如"SYSTEM_ERROR"、"BUSINESS_ERROR"等
     */
    public GXExceptionNotifyEvent(GXExceptionNotifyEventDto source, String eventType) {
        super(source, eventType);
    }

    /**
     * 创建异常通知事件
     *
     * @param source    异常通知事件数据传输对象，包含异常信息和HTTP请求信息
     * @param eventName 事件名称，用于标识事件的用途，如"EXCEPTION"、"ERROR"等
     * @param eventType 事件类型，用于区分不同类型的异常，如"SYSTEM_ERROR"、"BUSINESS_ERROR"等
     */
    public GXExceptionNotifyEvent(GXExceptionNotifyEventDto source, String eventType, String eventName) {
        super(source, eventType, eventName);
    }

    /**
     * 创建异常通知事件
     *
     * @param source    异常通知事件数据传输对象，包含异常信息和HTTP请求信息
     * @param param     额外的参数，可用于传递与异常相关的上下文信息，如用户ID、操作类型等
     * @param eventType 事件类型，用于区分不同类型的异常，如"SYSTEM_ERROR"、"BUSINESS_ERROR"等
     */
    public GXExceptionNotifyEvent(GXExceptionNotifyEventDto source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    /**
     * 创建异常通知事件
     *
     * @param source    异常通知事件数据传输对象，包含异常信息和HTTP请求信息
     * @param eventName 事件名称，用于标识事件的用途，如"EXCEPTION"、"ERROR"等
     * @param param     额外的参数，可用于传递与异常相关的上下文信息，如用户ID、操作类型等
     * @param eventType 事件类型，用于区分不同类型的异常，如"SYSTEM_ERROR"、"BUSINESS_ERROR"等
     */
    public GXExceptionNotifyEvent(GXExceptionNotifyEventDto source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
