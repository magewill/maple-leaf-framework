package cn.maple.core.datasource.it.mapper;

import cn.maple.core.datasource.it.entity.ListenerUserEntity;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper without a listener declaration, so a service-level batch write resolves exactly one
 * listener and the payload stays writable (single-listener batch contract).
 */
@Mapper
public interface ListenerBatchUserMapper extends GXBaseMapper<ListenerUserEntity> {
}
