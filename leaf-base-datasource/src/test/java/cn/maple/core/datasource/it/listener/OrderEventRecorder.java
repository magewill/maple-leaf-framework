package cn.maple.core.datasource.it.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Async recorder for the order table; snapshots how many order items are visible at callback time.
 */
@Component
public class OrderEventRecorder implements GXMybatisListenerService<Dict> {
    public static final List<Dict> EVENTS = new CopyOnWriteArrayList<>();
    public static final List<Integer> OBSERVED_ITEM_COUNTS = new CopyOnWriteArrayList<>();
    private static JdbcTemplate jdbcTemplate;

    public OrderEventRecorder(JdbcTemplate jdbcTemplate) {
        OrderEventRecorder.jdbcTemplate = jdbcTemplate;
    }

    public static void reset() {
        EVENTS.clear();
        OBSERVED_ITEM_COUNTS.clear();
    }

    @Override
    public void saveEntityListener(Dict data) {
        EVENTS.add(data);
        Long orderId = data.getLong("id");
        Integer count = Objects.isNull(jdbcTemplate) || Objects.isNull(orderId) ? null
                : jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_listener_order_item WHERE order_id = ?", Integer.class, orderId);
        OBSERVED_ITEM_COUNTS.add(Objects.isNull(count) ? 0 : count);
    }
}
