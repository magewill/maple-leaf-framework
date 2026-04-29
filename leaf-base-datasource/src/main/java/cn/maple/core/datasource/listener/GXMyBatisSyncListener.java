package cn.maple.core.datasource.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.event.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * MyBatis同步事件监听器
 *
 * @author 塵渊 britton@126.com
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
    @EventListener(condition = "T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_SAVE_ENTITY.getEventType().equals(#root.event.eventType)")
    public void listenerSaveEntity(GXMyBatisModelSaveEntityEvent<Dict> saveEntityEvent) {
        GXMyBatisBaseListener.super.listenerSaveEntity(saveEntityEvent);
    }

    /**
     * 监听更新实体(Entity)事件
     *
     * @param updateEntityEvent 事件对象
     */
    @EventListener(condition = "T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_UPDATE_ENTITY.getEventType().equals(#root.event.eventType)")
    public void listenerUpdateEntity(GXMyBatisModelUpdateEntityEvent<Dict> updateEntityEvent) {
        GXMyBatisBaseListener.super.listenerUpdateEntity(updateEntityEvent);
    }

    /**
     * 监听更新指定字段事件
     *
     * @param updateFieldEvent 事件对象
     */
    @EventListener(condition = "T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_UPDATE_FIELD.getEventType().equals(#root.event.eventType)")
    public void listenerUpdateField(GXMyBatisModelUpdateFieldEvent<Dict> updateFieldEvent) {
        GXMyBatisBaseListener.super.listenerUpdateField(updateFieldEvent);
    }

    /**
     * 监听更新指定字段事件
     *
     * @param deleteSoftEvent 事件对象
     */
    @EventListener(condition = "T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_DELETE_SOFT.getEventType().equals(#root.event.eventType)")
    public void listenerDeleteSoft(GXMyBatisModelDeleteSoftEvent<Dict> deleteSoftEvent) {
        GXMyBatisBaseListener.super.listenerDeleteSoft(deleteSoftEvent);
    }

    /**
     * 监听批量新增与更新事件
     *
     * @param saveBatchEntityEvent 事件对象
     */
    @EventListener(condition = "T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_SAVE_BATCH_ENTITY.getEventType().equals(#root.event.eventType)")
    public void listenerSaveBatch(GXMyBatisModelSaveBatchEntityEvent<Dict> saveBatchEntityEvent) {
        GXMyBatisBaseListener.super.listenerSaveBatch(saveBatchEntityEvent);
    }
}
