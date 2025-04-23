package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.model.GXBaseModel;

/**
 * 创建数据之前的事件
 * <p>
 * 该事件在实体数据被持久化到数据库之前触发，允许在数据创建前进行额外的处理，
 * 例如数据校验、字段填充或业务规则验证等。
 * </p>
 * <p>
 * 线程安全说明：
 * 该事件处理应当是线程安全的，不应修改共享状态，避免并发问题。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建事件监听器
 * @Component
 * public class UserCreatingListener implements ApplicationListener<GXMyBatisModelCreatingEntityEvent<UserModel>> {
 *     @Override
 *     public void onApplicationEvent(GXMyBatisModelCreatingEntityEvent<UserModel> event) {
 *         UserModel user = event.getSource();
 *         // 在用户创建前进行额外处理
 *         if (user.getUsername() == null) {
 *             user.setUsername("默认用户名");
 *         }
 *         // 可以通过事件参数获取上下文信息
 *         Dict params = event.getParam();
 *         String operationType = params.getStr("operationType");
 *     }
 * }
 * 
 * // 2. 发布事件
 * UserModel user = new UserModel();
 * Dict params = Dict.create().set("operationType", "register");
 * applicationContext.publishEvent(new GXMyBatisModelCreatingEntityEvent<>(user, "CREATE", params));
 * </pre>
 * </p>
 * 
 * @param <M> 实体模型类型，必须继承自GXBaseModel
 * @author britton chen <britton@126.com>
 */
public class GXMyBatisModelCreatingEntityEvent<M extends GXBaseModel> extends GXBaseEvent<M> {
    /**
     * 创建基本事件对象
     * <p>
     * 使用最简单的构造方式创建事件，仅包含事件源对象。
     * 适用于简单场景，不需要额外上下文信息的情况。
     * </p>
     * 
     * @param source 事件源对象，通常是待创建的实体对象
     */
    public GXMyBatisModelCreatingEntityEvent(M source) {
        super(source);
    }

    /**
     * 创建带事件类型的事件对象
     * <p>
     * 包含事件源和事件类型信息，便于区分不同类型的创建事件。
     * </p>
     * 
     * @param source    事件源对象，通常是待创建的实体对象
     * @param eventType 事件类型，如"CREATE"、"REGISTER"等，用于标识事件的业务类型
     */
    public GXMyBatisModelCreatingEntityEvent(M source, String eventType) {
        super(source, eventType);
    }

    /**
     * 创建带事件类型和参数的事件对象
     * <p>
     * 除了基本信息外，还包含参数字典，可传递更多上下文信息。
     * 参数字典可用于传递创建操作的额外信息，如操作人、来源渠道等。
     * </p>
     * 
     * @param source    事件源对象，通常是待创建的实体对象
     * @param eventType 事件类型，如"CREATE"、"REGISTER"等，用于标识事件的业务类型
     * @param param     事件参数，可包含创建操作的上下文信息，如操作人ID、操作时间等
     */
    public GXMyBatisModelCreatingEntityEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    /**
     * 创建完整事件对象
     * <p>
     * 最完整的构造方式，包含事件源、类型、参数和名称。
     * 事件名称可用于进一步细分同一类型下的不同事件场景。
     * </p>
     * 
     * @param source    事件源对象，通常是待创建的实体对象
     * @param eventType 事件类型，如"CREATE"、"REGISTER"等，用于标识事件的业务类型
     * @param param     事件参数，可包含创建操作的上下文信息，如操作人ID、操作时间等
     * @param eventName 事件名称，用于区分同类型不同场景的事件，提供更精细的事件分类
     */
    public GXMyBatisModelCreatingEntityEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
