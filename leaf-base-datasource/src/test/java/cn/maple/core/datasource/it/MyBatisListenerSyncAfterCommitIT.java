package cn.maple.core.datasource.it;

import cn.maple.core.datasource.it.entity.ListenerOrderEntity;
import cn.maple.core.datasource.it.entity.ListenerOrderItemEntity;
import cn.maple.core.datasource.it.listener.SyncOrderEventRecorder;
import cn.maple.core.datasource.it.listener.SyncOrderItemEventRecorder;
import cn.maple.core.datasource.it.mapper.ListenerOrderItemSyncMapper;
import cn.maple.core.datasource.it.mapper.ListenerOrderSyncMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code maple.framework.mybatis.listener.sync-after-commit=true} for multi-table
 * transactions: callbacks must be deferred to commit and must observe the fully committed state.
 */
@SpringBootTest(classes = MyBatisListenerITApplication.class, properties = {
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "seata.enabled=false",
        "spring.autoconfigure.exclude=org.apache.seata.spring.boot.autoconfigure.SeataAutoConfiguration,org.apache.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration",
        "spring.main.banner-mode=off",
        "maple.framework.mybatis.listener.sync-after-commit=true"
})
@EnabledIfSystemProperty(named = "maple.it.enabled", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MyBatisListenerSyncAfterCommitIT {
    @Autowired
    private ListenerOrderSyncMapper orderMapper;

    @Autowired
    private ListenerOrderItemSyncMapper orderItemMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_listener_order_item");
        jdbcTemplate.update("DELETE FROM t_listener_order");
        SyncOrderEventRecorder.reset();
        SyncOrderItemEventRecorder.reset();
    }

    @Test
    void bothTableEventsAreDeliveredAfterCommitWithFullStateVisible() {
        new TransactionTemplate(transactionManager).execute(status -> {
            ListenerOrderEntity order = newOrder("ORD-2001", 500);
            orderMapper.insert(order);
            orderItemMapper.insert(newItem(order.getId(), "SKU-D", 4));
            return null;
        });

        assertEquals(1, SyncOrderEventRecorder.EVENTS.size());
        assertEquals(1, SyncOrderItemEventRecorder.EVENTS.size());
        assertEquals(List.of(1), SyncOrderEventRecorder.OBSERVED_ITEM_COUNTS);
        assertEquals(1, countRowsOf("t_listener_order"));
        assertEquals(1, countRowsOf("t_listener_order_item"));
    }

    @Test
    void noEventIsDeliveredWhenMultiTableTransactionRollsBack() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).execute(status -> {
            ListenerOrderEntity order = newOrder("ORD-2002", 600);
            orderMapper.insert(order);
            orderItemMapper.insert(newItem(order.getId(), "SKU-E", 1));
            throw new IllegalStateException("rollback after both tables");
        }));

        assertTrue(SyncOrderEventRecorder.EVENTS.isEmpty());
        assertTrue(SyncOrderItemEventRecorder.EVENTS.isEmpty());
        assertEquals(0, countRowsOf("t_listener_order"));
        assertEquals(0, countRowsOf("t_listener_order_item"));
    }

    private ListenerOrderEntity newOrder(String orderNo, int amount) {
        ListenerOrderEntity order = new ListenerOrderEntity();
        order.setOrderNo(orderNo);
        order.setAmount(amount);
        return order;
    }

    private ListenerOrderItemEntity newItem(Long orderId, String sku, int qty) {
        ListenerOrderItemEntity item = new ListenerOrderItemEntity();
        item.setOrderId(orderId);
        item.setSku(sku);
        item.setQty(qty);
        return item;
    }

    private Integer countRowsOf(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return Objects.isNull(count) ? 0 : count;
    }
}
