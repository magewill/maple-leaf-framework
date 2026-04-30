package cn.maple.core.datasource.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.event.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@SuppressWarnings("all")
public class GXMyBatisSyncListener implements GXMyBatisBaseListener {
    @EventListener(condition = "T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_SAVE_ENTITY.getEventType().equals(#root.event.eventType)")
    public void listenerSaveEntity(GXMyBatisModelSaveEntityEvent<Dict> saveEntityEvent) {
        GXMyBatisBaseListener.super.listenerSaveEntity(saveEntityEvent);
    }

    @EventListener(condition = "T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_UPDATE_ENTITY.getEventType().equals(#root.event.eventType)")
    public void listenerUpdateEntity(GXMyBatisModelUpdateEntityEvent<Dict> updateEntityEvent) {
        GXMyBatisBaseListener.super.listenerUpdateEntity(updateEntityEvent);
    }

    @EventListener(condition = "T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_UPDATE_FIELD.getEventType().equals(#root.event.eventType)")
    public void listenerUpdateField(GXMyBatisModelUpdateFieldEvent<Dict> updateFieldEvent) {
        GXMyBatisBaseListener.super.listenerUpdateField(updateFieldEvent);
    }

    @EventListener(condition = "T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_DELETE_SOFT.getEventType().equals(#root.event.eventType)")
    public void listenerDeleteSoft(GXMyBatisModelDeleteSoftEvent<Dict> deleteSoftEvent) {
        GXMyBatisBaseListener.super.listenerDeleteSoft(deleteSoftEvent);
    }

    @EventListener(condition = "T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).SYNC_SAVE_BATCH_ENTITY.getEventType().equals(#root.event.eventType)")
    public void listenerSaveBatch(GXMyBatisModelSaveBatchEntityEvent<Dict> saveBatchEntityEvent) {
        GXMyBatisBaseListener.super.listenerSaveBatch(saveBatchEntityEvent);
    }
}
