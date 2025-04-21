package cn.maple.core.framework.constant;

/**
 * 事件名称常量类
 * <p>
 * 该类定义了系统中使用的各种事件名称常量，主要用于实体生命周期事件的标识。
 * 包含了创建、更新、保存、删除、恢复等操作的前置和后置事件名称。
 * 所有常量均为不可变的字符串，线程安全。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 在实体创建后触发事件
 * eventPublisher.publishEvent(new EntityEvent(entity, GXEventNameConstant.CREATED));
 * 
 * // 在实体更新前触发事件
 * eventPublisher.publishEvent(new EntityEvent(entity, GXEventNameConstant.UPDATING));
 * </pre>
 * </p>
 *
 * @author britton chen <britton@126.com>
 */
public class GXEventNameConstant {
    /**
     * 实体创建后事件
     * <p>
     * 当实体对象创建完成后触发该事件。
     * 可用于执行创建后的附加逻辑，如发送通知、更新缓存等。
     * </p>
     */
    public static final String CREATED = "created";

    /**
     * 实体创建前事件
     * <p>
     * 当实体对象即将被创建前触发该事件。
     * 可用于执行创建前的预处理逻辑，如数据校验、默认值设置等。
     * </p>
     */
    public static final String CREATING = "creating";

    /**
     * 实体更新后事件
     * <p>
     * 当实体对象更新完成后触发该事件。
     * 可用于执行更新后的附加逻辑，如清理缓存、记录变更历史等。
     * </p>
     */
    public static final String UPDATED = "updated";

    /**
     * 实体更新前事件
     * <p>
     * 当实体对象即将被更新前触发该事件。
     * 可用于执行更新前的预处理逻辑，如数据校验、权限检查等。
     * </p>
     */
    public static final String UPDATING = "updating";

    /**
     * 实体保存后事件
     * <p>
     * 当实体对象保存完成后触发该事件（保存可能是创建或更新）。
     * 可用于执行保存后的通用逻辑，不区分创建或更新操作。
     * </p>
     */
    public static final String SAVED = "saved";

    /**
     * 实体保存前事件
     * <p>
     * 当实体对象即将被保存前触发该事件（保存可能是创建或更新）。
     * 可用于执行保存前的通用预处理逻辑，不区分创建或更新操作。
     * </p>
     */
    public static final String SAVING = "saving";

    /**
     * 实体删除后事件
     * <p>
     * 当实体对象删除完成后触发该事件。
     * 可用于执行删除后的附加逻辑，如清理关联数据、记录删除日志等。
     * </p>
     */
    public static final String DELETED = "deleted";

    /**
     * 实体删除前事件
     * <p>
     * 当实体对象即将被删除前触发该事件。
     * 可用于执行删除前的预处理逻辑，如权限检查、关联数据处理等。
     * </p>
     */
    public static final String DELETING = "deleting";

    /**
     * 实体恢复后事件
     * <p>
     * 当已删除的实体对象恢复完成后触发该事件（适用于软删除）。
     * 可用于执行恢复后的附加逻辑，如恢复关联数据、发送通知等。
     * </p>
     */
    public static final String RESTORED = "restored";
    
    /**
     * 实体恢复前事件
     * <p>
     * 当已删除的实体对象即将被恢复前触发该事件（适用于软删除）。
     * 可用于执行恢复前的预处理逻辑，如权限检查、状态验证等。
     * </p>
     */
    public static final String RESTORING = "restoring";

    /**
     * 私有构造函数
     * <p>
     * 防止实例化，该类仅提供静态常量。
     * 符合常量类的设计模式。
     * </p>
     */
    private GXEventNameConstant() {
        // 私有构造函数，防止实例化
    }
}
