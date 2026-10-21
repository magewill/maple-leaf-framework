package cn.maple.core.datasource.it.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Async recorder for the order-item table.
 */
@Component
public class OrderItemEventRecorder implements GXMybatisListenerService<Dict> {
    public static final List<Dict> EVENTS = new CopyOnWriteArrayList<>();

    public static void reset() {
        EVENTS.clear();
    }

    @Override
    public void saveEntityListener(Dict data) {
        EVENTS.add(data);
    }
}
