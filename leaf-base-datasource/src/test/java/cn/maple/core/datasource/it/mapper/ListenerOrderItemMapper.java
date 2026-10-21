package cn.maple.core.datasource.it.mapper;

import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.it.entity.ListenerOrderItemEntity;
import cn.maple.core.datasource.it.listener.OrderItemEventRecorder;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@GXMyBatisListener(listenerClazz = OrderItemEventRecorder.class, runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)
public interface ListenerOrderItemMapper extends GXBaseMapper<ListenerOrderItemEntity> {
}
