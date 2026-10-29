package cn.maple.core.datasource.it.mapper;

import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.it.entity.ListenerUserEntity;
import cn.maple.core.datasource.it.listener.ThrowingUserListener;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Declares a synchronous listener that always fails, so the transaction/exception semantics of the
 * listener chain can be verified against a real database.
 */
@Mapper
@GXMyBatisListener(listenerClazz = ThrowingUserListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
public interface ListenerUserThrowingSyncMapper extends GXBaseMapper<ListenerUserEntity> {
}
