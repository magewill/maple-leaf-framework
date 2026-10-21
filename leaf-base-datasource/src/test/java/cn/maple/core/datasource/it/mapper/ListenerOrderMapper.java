package cn.maple.core.datasource.it.mapper;

import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.it.entity.ListenerOrderEntity;
import cn.maple.core.datasource.it.listener.OrderEventRecorder;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@GXMyBatisListener(listenerClazz = OrderEventRecorder.class, runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)
public interface ListenerOrderMapper extends GXBaseMapper<ListenerOrderEntity> {
}
