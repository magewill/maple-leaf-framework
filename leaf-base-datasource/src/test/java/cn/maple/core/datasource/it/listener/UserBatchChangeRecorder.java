package cn.maple.core.datasource.it.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records batch payloads and exposes the rows so a test can mutate them, verifying that a listener
 * cannot reach the business entities through the batch payload.
 */
@Component
public class UserBatchChangeRecorder implements GXMybatisListenerService<Dict> {
    public static final List<List<Dict>> BATCH_EVENTS = new CopyOnWriteArrayList<>();

    public static void reset() {
        BATCH_EVENTS.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void saveBatchListener(Dict data) {
        BATCH_EVENTS.add((List<Dict>) data.get("batchEntities"));
    }
}
