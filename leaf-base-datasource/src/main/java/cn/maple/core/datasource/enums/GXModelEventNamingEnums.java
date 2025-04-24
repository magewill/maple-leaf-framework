package cn.maple.core.datasource.enums;

import lombok.Getter;

/**
 * MyBatis操作事件命名枚举类
 * <p>
 * 该枚举类定义了系统中所有MyBatis操作相关的事件类型和名称，包括同步和异步两种模式。
 * 每个枚举值包含事件类型标识、事件名称和描述信息，用于在事件发布和监听过程中进行标识和区分。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 1. 获取事件类型
 * String eventType = GXModelEventNamingEnums.SYNC_SAVE_ENTITY.getEventType();
 * 
 * // 2. 获取事件名称
 * String eventName = GXModelEventNamingEnums.SYNC_SAVE_ENTITY.getEventName();
 * 
 * // 3. 在事件监听器中使用
 * @EventListener(condition = "#event.eventName == 'syncSaveEntity'")
 * public void handleSaveEvent(GXMyBatisModelSaveEntityEvent<Dict> event) {
 *     // 处理保存事件...
 * }
 * </pre>
 * 
 * <p>事件类型说明：</p>
 * <ol>
 *   <li>同步事件(SYNC_*)：在数据库操作完成后立即触发，在当前线程中执行</li>
 *   <li>异步事件(ASYNC_*)：在数据库操作完成后异步触发，在单独的线程中执行</li>
 * </ol>
 * 
 * @author 塵渊 britton@126.com
 */
@Getter
public enum GXModelEventNamingEnums {
    /**
     * 同步保存单个实体对象事件
     * 在调用GXBaseMapper.insert方法后同步触发
     */
    SYNC_SAVE_ENTITY("sync_save_entity", "syncSaveEntity", "同步保存实体对象"),
    
    /**
     * 同步批量保存实体对象事件
     * 在调用ServiceImpl.saveBatch或saveOrUpdateBatch方法后同步触发
     */
    SYNC_SAVE_BATCH_ENTITY("sync_save_batch_entity", "syncSaveBatchEntity", "同步批量保存实体对象"),
    
    /**
     * 同步更新实体对象事件
     * 在调用GXBaseMapper.update方法后同步触发
     */
    SYNC_UPDATE_ENTITY("sync_update_entity", "syncUpdateEntity", "同步更新实体对象"),
    
    /**
     * 同步更新指定字段事件
     * 在调用GXBaseMapper.updateFieldByCondition方法后同步触发
     */
    SYNC_UPDATE_FIELD("sync_update_field", "syncUpdateField", "同步更新指定字段"),
    
    /**
     * 同步软删除事件
     * 在调用GXBaseMapper.deleteSoftCondition方法后同步触发
     * 软删除是通过更新标记字段而非物理删除数据
     */
    SYNC_DELETE_SOFT("sync_delete_soft", "syncDeleteSoft", "同步通过条件软删除"),
    
    /**
     * 同步物理删除事件
     * 在调用物理删除方法后同步触发
     * 物理删除会从数据库中永久移除数据
     */
    SYNC_DELETE("sync_delete", "syncDelete", "同步通过条件物理删除"),

    /**
     * 异步保存单个实体对象事件
     * 在调用GXBaseMapper.insert方法后异步触发
     * 异步事件在单独的线程中执行，不会阻塞主流程
     */
    ASYNC_SAVE_ENTITY("async_save_entity", "asyncSaveEntity", "异步保存实体对象"),
    
    /**
     * 异步批量保存实体对象事件
     * 在调用ServiceImpl.saveBatch或saveOrUpdateBatch方法后异步触发
     * 适用于大批量数据处理场景
     */
    ASYNC_SAVE_BATCH_ENTITY("async_save_batch_entity", "asyncSaveBatchEntity", "异步批量保存实体对象"),
    
    /**
     * 异步更新实体对象事件
     * 在调用GXBaseMapper.update方法后异步触发
     * 适用于更新后需要执行耗时操作的场景
     */
    ASYNC_UPDATE_ENTITY("async_update_entity", "asyncUpdateEntity", "异步更新实体对象"),
    
    /**
     * 异步更新指定字段事件
     * 在调用GXBaseMapper.updateFieldByCondition方法后异步触发
     * 适用于字段更新后需要异步处理的场景
     */
    ASYNC_UPDATE_FIELD("async_update_field", "asyncUpdateField", "异步更新指定字段"),
    
    /**
     * 异步软删除事件
     * 在调用GXBaseMapper.deleteSoftCondition方法后异步触发
     * 适用于软删除后需要执行耗时清理操作的场景
     */
    ASYNC_DELETE_SOFT("async_delete_soft", "asyncDeleteSoft", "异步通过条件软删除"),
    
    /**
     * 异步物理删除事件
     * 在调用物理删除方法后异步触发
     * 适用于删除后需要异步清理关联资源的场景
     */
    ASYNC_DELETE("async_delete", "asyncDelete", "异步通过条件物理删除");

    /**
     * 事件类型
     * <p>
     * 用于标识事件的类型，如sync_save_entity、async_update_field等
     * 主要用于事件发布和路由过程中的类型识别
     * </p>
     */
    private final String eventType;

    /**
     * 事件名称
     * <p>
     * 用于在事件监听器中通过condition表达式进行事件过滤
     * 例如：@EventListener(condition = "#event.eventName == 'syncSaveEntity'")
     * </p>
     */
    private final String eventName;

    /**
     * 事件描述
     * <p>
     * 对事件用途的简要描述，便于开发人员理解事件的作用
     * 在日志记录和调试过程中也会使用此描述
     * </p>
     */
    private final String desc;

    GXModelEventNamingEnums(String eventType, String eventName, String desc) {
        this.eventType = eventType;
        this.eventName = eventName;
        this.desc = desc;
    }
}
