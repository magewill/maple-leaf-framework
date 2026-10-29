package cn.maple.core.datasource.it.service;

import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.it.entity.ListenerUserEntity;
import cn.maple.core.datasource.it.listener.UserBatchChangeRecorder;
import cn.maple.core.datasource.it.mapper.ListenerBatchUserMapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * Service-layer batch write used to verify that {@code saveBatch} delivers exactly one batch event
 * against a real database.
 */
@Service
@GXMyBatisListener(listenerClazz = UserBatchChangeRecorder.class)
public class ListenerUserBatchService extends ServiceImpl<ListenerBatchUserMapper, ListenerUserEntity> {
}
