package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.lang.GXDict;

/**
 * MyBatis模型物理删除事件
 * <p>
 * 该事件在MyBatis模型被物理删除时触发，用于实现业务逻辑的解耦和扩展。
 * 物理删除指的是从数据库中永久移除记录，与软删除（标记删除）不同。
 * 继承自GXBaseEvent基础事件类，支持泛型，可以携带任何继承自GXDict的数据模型。
 * </p>
 * 
 * <p>
 * 事件特性：
 * - 支持泛型，可以携带任何继承自GXDict的数据模型
 * - 支持事件类型区分，可以根据不同的业务场景触发不同的处理逻辑
 * - 支持额外参数传递，可以在事件中携带更多的上下文信息
 * - 支持自定义事件名称，便于事件的识别和追踪
 * </p>
 * 
 * <p>
 * 安全性说明：
 * - 该事件涉及数据物理删除操作，应当谨慎处理，确保数据删除前已进行必要的备份
 * - 事件处理器应当进行适当的权限验证，防止未授权的删除操作
 * - 建议在处理敏感数据删除时，记录详细的操作日志，便于审计和追踪
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
 * GXDict userModel = new GXDict().set("id", 1).set("name", "张三");
 * Dict params = Dict.create().set("operator", "admin").set("reason", "数据清理");
 * GXMyBatisModelDeleteEvent<GXDict> event = new GXMyBatisModelDeleteEvent<>(userModel, "USER_DELETE", params);
 * applicationEventPublisher.publishEvent(event);
 * 
 * // 2. 创建事件监听器
 * @Component
 * public class UserDeleteListener {
 *     @EventListener
 *     public void onUserDelete(GXMyBatisModelDeleteEvent<GXDict> event) {
 *         if ("USER_DELETE".equals(event.getEventType())) {
 *             GXDict user = event.getSource();
 *             Dict params = event.getParam();
 *             // 处理用户物理删除后的业务逻辑
 *             System.out.println("用户ID:" + user.getInt("id") + "被" + 
 *                 params.getStr("operator") + "物理删除，原因：" + params.getStr("reason"));
 *             // 可以在这里执行关联数据清理、发送通知、记录审计日志等操作
 *         }
 *     }
 * }
 * </pre>
 * </p>
 * 
 * @param <M> 模型类型参数，必须继承自GXDict类
 * @author britton
 * @since 1.0.0
 */
public class GXMyBatisModelDeleteEvent<M extends GXDict> extends GXBaseEvent<M> {
    public GXMyBatisModelDeleteEvent(M source) {
        super(source);
    }

    public GXMyBatisModelDeleteEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelDeleteEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelDeleteEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
