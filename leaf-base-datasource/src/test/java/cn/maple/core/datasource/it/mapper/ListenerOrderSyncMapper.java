package cn.maple.core.datasource.it.mapper;

import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.it.entity.ListenerOrderEntity;
import cn.maple.core.datasource.it.listener.SyncOrderEventRecorder;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@GXMyBatisListener(listenerClazz = SyncOrderEventRecorder.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
public interface ListenerOrderSyncMapper extends GXBaseMapper<ListenerOrderEntity> {
}
