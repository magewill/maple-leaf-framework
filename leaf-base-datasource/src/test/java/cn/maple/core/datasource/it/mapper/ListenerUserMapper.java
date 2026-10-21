package cn.maple.core.datasource.it.mapper;

import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.it.entity.ListenerUserEntity;
import cn.maple.core.datasource.it.listener.UserChangeRecorder;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@GXMyBatisListener(listenerClazz = UserChangeRecorder.class)
public interface ListenerUserMapper extends GXBaseMapper<ListenerUserEntity> {
}
