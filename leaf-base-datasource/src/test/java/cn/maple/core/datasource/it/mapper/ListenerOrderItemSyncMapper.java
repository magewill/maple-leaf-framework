package cn.maple.core.datasource.it.mapper;

import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.it.entity.ListenerOrderItemEntity;
import cn.maple.core.datasource.it.listener.SyncOrderItemEventRecorder;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@GXMyBatisListener(listenerClazz = SyncOrderItemEventRecorder.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
public interface ListenerOrderItemSyncMapper extends GXBaseMapper<ListenerOrderItemEntity> {
}
