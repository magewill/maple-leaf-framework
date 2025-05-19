package cn.maple.core.framework.ddd.publisher;

import cn.maple.core.framework.exception.GXBusinessException;

/**
 * 领域事件发布器接口
 * <p>
 * 在领域驱动设计(DDD)中，领域事件是领域模型中发生的、对其他部分有意义的事件。
 * 该接口定义了发布领域事件的标准方法，实现类负责将领域事件分发给对应的监听器或处理器。
 * </p>
 *
 * <p>使用示例:</p>
 * <pre>
 * // 1. 创建领域事件类
 * public class UserRegisteredEvent {
 *     private final String username;
 *     private final String email;
 *
 *     public UserRegisteredEvent(String username, String email) {
 *         this.username = username;
 *         this.email = email;
 *     }
 *
 *     // getter方法
 * }
 *
 * // 2. 实现领域事件发布器
 * @Component
 * public class SpringEventPublisher implements GXDomainEventPublisher {
 *     private final ApplicationEventPublisher eventPublisher;
 * <p>
 *     public SpringEventPublisher(ApplicationEventPublisher eventPublisher) {
 *         this.eventPublisher = eventPublisher;
 *     }
 *
 *     @Override
 *     public void publish(Object event) {
 *         eventPublisher.publishEvent(event);
 *     }
 * }
 * <p>
 * // 3. 在领域服务中使用
 * @Service
 * public class UserService {
 *     private final GXDomainEventPublisher eventPublisher;
 * <p>
 *     public UserService(GXDomainEventPublisher eventPublisher) {
 *         this.eventPublisher = eventPublisher;
 *     }
 * <p>
 *     public void registerUser(String username, String email) {
 *         // 业务逻辑...
 * <p>
 *         // 发布领域事件
 *         eventPublisher.publish(new UserRegisteredEvent(username, email));
 *     }
 * }
 * </pre>
 *
 * @author britton
 * @since 2021-11-07
 */
public interface GXDomainEventPublisher {
    /**
     * 发布领域事件
     * <p>
     * 该方法用于发布领域事件，将领域中发生的重要变化通知给系统的其他部分。
     * 默认实现会抛出业务异常，要求子类必须提供实现。
     * 实现类应当确保：
     * 1. 事件发布的可靠性，避免事件丢失
     * 2. 发布过程中的异常处理
     * 3. 在高并发环境下的性能考虑
     * </p>
     *
     * @param event 领域事件对象，不能为null
     * @throws GXBusinessException 当使用默认实现时抛出，提示需要实现该方法
     */
    default void publish(Object event) {
        throw new GXBusinessException("请实现publish方法");
    }
}
