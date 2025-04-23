package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

/**
 * MyBatis模型软删除事件
 * <p>
 * 该事件在MyBatis模型被软删除时触发，用于实现业务逻辑的解耦和扩展。
 * 软删除通常指的是将数据标记为已删除状态，而非物理删除数据库记录。
 * 继承自GXBaseEvent基础事件类，支持泛型，可以携带任何继承自Dict的数据模型。
 * </p>
 * 
 * <p>
 * 事件特性：
 * - 支持泛型，可以携带任何继承自Dict的数据模型
 * - 支持事件类型区分，可以根据不同的业务场景触发不同的处理逻辑
 * - 支持额外参数传递，可以在事件中携带更多的上下文信息
 * - 支持自定义事件名称，便于事件的识别和追踪
 * </p>
 * 
 * <p>
 * 线程安全说明：
 * 该类本身是无状态的，仅作为事件载体，线程安全性取决于事件处理器的实现。
 * 事件对象一旦创建不应被修改，以确保在多线程环境下的数据一致性。
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建事件并发布
 * Dict userModel = Dict.create().set("id", 1).set("name", "张三").set("deleted", 1);
 * Dict params = Dict.create().set("operator", "admin").set("reason", "用户请求");
 * GXMyBatisModelDeleteSoftEvent<Dict> event = new GXMyBatisModelDeleteSoftEvent<>(userModel, "USER_SOFT_DELETE", params);
 * applicationEventPublisher.publishEvent(event);
 * 
 * // 2. 创建事件监听器
 * @Component
 * public class UserSoftDeleteListener {
 *     @EventListener
 *     public void onUserSoftDelete(GXMyBatisModelDeleteSoftEvent<Dict> event) {
 *         if ("USER_SOFT_DELETE".equals(event.getEventType())) {
 *             Dict user = event.getSource();
 *             Dict params = event.getParam();
 *             // 处理用户软删除后的业务逻辑
 *             System.out.println("用户ID:" + user.getInt("id") + "被" + 
 *                 params.getStr("operator") + "软删除，原因：" + params.getStr("reason"));
 *             // 可以在这里执行关联数据处理、发送通知等操作
 *         }
 *     }
 * }
 * </pre>
 * </p>
 * 
 * @param <M> 模型类型参数，必须继承自Dict类
 * @author britton
 * @since 1.0.0
 */
public class GXMyBatisModelDeleteSoftEvent<M extends Dict> extends GXBaseEvent<M> {
    public GXMyBatisModelDeleteSoftEvent(M source) {
        super(source);
    }

    public GXMyBatisModelDeleteSoftEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelDeleteSoftEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelDeleteSoftEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
