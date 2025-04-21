package cn.maple.core.datasource.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.event.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * MyBatis同步事件监听器
 * <p>
 * 该监听器用于同步处理MyBatis操作产生的事件，包括实体保存、更新、字段更新、软删除和批量保存等操作。
 * 所有事件处理方法都在调用线程中同步执行，不会创建新的线程。
 * <p>
 * 线程安全说明：
 * 1. 该监听器在同步模式下工作，所有事件处理都在当前线程中执行
 * 2. 事件处理过程中的异常会直接影响调用方，需要确保异常被正确处理
 * 3. 由于是同步处理，长时间运行的操作可能会阻塞调用线程
 * <p>
 * 使用场景：
 * 1. 需要立即处理且与主业务流程强相关的操作
 * 2. 需要事务支持的操作
 * 3. 对数据一致性要求高的场景
 * <p>
 * 示例：当保存用户实体后，需要同步更新关联的权限信息
 */
@Component
@Log4j2
@SuppressWarnings("all")
public class GXMyBatisSyncListener implements GXMyBatisBaseListener {
    /**
     * 监听保存实体(Entity)事件
     *
     * @param saveEntityEvent 事件对象
     */
    @EventListener(condition = "#root.event.eventType.equals(T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_SAVE_ENTITY.eventType)")
    public void listenerSaveEntity(GXMyBatisModelSaveEntityEvent<Dict> saveEntityEvent) {
        GXMyBatisBaseListener.super.listenerSaveEntity(saveEntityEvent);
    }

    /**
     * 监听更新实体(Entity)事件
     *
     * @param updateEntityEvent 事件对象
     */
    @EventListener(condition = "#root.event.eventType.equals(T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_UPDATE_ENTITY.eventType)")
    public void listenerUpdateEntity(GXMyBatisModelUpdateEntityEvent<Dict> updateEntityEvent) {
        GXMyBatisBaseListener.super.listenerUpdateEntity(updateEntityEvent);
    }

    /**
     * 监听更新指定字段事件
     *
     * @param updateFieldEvent 事件对象
     */
    @EventListener(condition = "#root.event.eventType.equals(T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_UPDATE_FIELD.eventType)")
    public void listenerUpdateField(GXMyBatisModelUpdateFieldEvent<Dict> updateFieldEvent) {
        GXMyBatisBaseListener.super.listenerUpdateField(updateFieldEvent);
    }

    /**
     * 监听更新指定字段事件
     *
     * @param deleteSoftEvent 事件对象
     */
    @EventListener(condition = "#root.event.eventType.equals(T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_DELETE_SOFT.eventType)")
    public void listenerDeleteSoft(GXMyBatisModelDeleteSoftEvent<Dict> deleteSoftEvent) {
        GXMyBatisBaseListener.super.listenerDeleteSoft(deleteSoftEvent);
    }

    /**
     * 监听批量新增与更新事件
     *
     * @param saveBatchEntityEvent 事件对象
     */
    @EventListener(condition = "#root.event.eventType.equals(T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_SAVE_BATCH_ENTITY.eventType)")
    public void listenerSaveBatch(GXMyBatisModelSaveBatchEntityEvent<Dict> saveBatchEntityEvent) {
        GXMyBatisBaseListener.super.listenerSaveBatch(saveBatchEntityEvent);
    }
}
