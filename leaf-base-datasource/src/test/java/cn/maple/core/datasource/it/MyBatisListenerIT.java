package cn.maple.core.datasource.it;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.it.entity.ListenerOrderEntity;
import cn.maple.core.datasource.it.entity.ListenerOrderItemEntity;
import cn.maple.core.datasource.it.entity.ListenerUserEntity;
import cn.maple.core.datasource.it.listener.AsyncUserChangeRecorder;
import cn.maple.core.datasource.it.listener.OrderEventRecorder;
import cn.maple.core.datasource.it.listener.OrderItemEventRecorder;
import cn.maple.core.datasource.it.listener.SyncOrderEventRecorder;
import cn.maple.core.datasource.it.listener.SyncOrderItemEventRecorder;
import cn.maple.core.datasource.it.listener.ThrowingUserListener;
import cn.maple.core.datasource.it.listener.UserBatchChangeRecorder;
import cn.maple.core.datasource.it.listener.UserChangeRecorder;
import cn.maple.core.datasource.it.mapper.ListenerOrderItemMapper;
import cn.maple.core.datasource.it.mapper.ListenerOrderItemSyncMapper;
import cn.maple.core.datasource.it.mapper.ListenerOrderMapper;
import cn.maple.core.datasource.it.mapper.ListenerOrderSyncMapper;
import cn.maple.core.datasource.it.mapper.ListenerUserAsyncMapper;
import cn.maple.core.datasource.it.mapper.ListenerUserMapper;
import cn.maple.core.datasource.it.service.ListenerUserBatchService;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.field.GXUpdateStrField;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.ibatis.executor.BatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test that runs the listener chain against a real MySQL instance.
 *
 * <p>Enable with {@code -Dmaple.it.enabled=true -DIT_DB_URL=... -DIT_DB_USER=... -DIT_DB_PASSWORD=...}.
 * The test owns the {@code t_listener_user} table and truncates it before every case.</p>
 */
@SpringBootTest(classes = MyBatisListenerITApplication.class, properties = {
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "seata.enabled=false",
        "spring.autoconfigure.exclude=org.apache.seata.spring.boot.autoconfigure.SeataAutoConfiguration,org.apache.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration",
        "spring.main.banner-mode=off"
})
@EnabledIfSystemProperty(named = "maple.it.enabled", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MyBatisListenerIT {
    private static final String TABLE_NAME = "t_listener_user";

    @Autowired
    private ListenerUserMapper userMapper;

    @Autowired
    private ListenerUserAsyncMapper asyncUserMapper;

    @Autowired
    private ListenerOrderMapper orderMapper;

    @Autowired
    private ListenerOrderItemMapper orderItemMapper;

    @Autowired
    private ListenerOrderSyncMapper syncOrderMapper;

    @Autowired
    private ListenerOrderItemSyncMapper syncOrderItemMapper;

    @Autowired
    private ListenerUserBatchService userBatchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM " + TABLE_NAME);
        jdbcTemplate.update("DELETE FROM t_listener_order_item");
        jdbcTemplate.update("DELETE FROM t_listener_order");
        UserChangeRecorder.reset();
        AsyncUserChangeRecorder.reset();
        OrderEventRecorder.reset();
        OrderItemEventRecorder.reset();
        SyncOrderEventRecorder.reset();
        SyncOrderItemEventRecorder.reset();
        UserBatchChangeRecorder.reset();
        ThrowingUserListener.reset();
    }

    @Test
    void insertDeliversSaveEventWithEntityPayload() {
        ListenerUserEntity entity = newEntity("alice", 10);

        int rows = userMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
        assertEquals(1, UserChangeRecorder.SAVE_EVENTS.size());
        Dict payload = UserChangeRecorder.SAVE_EVENTS.getFirst();
        assertEquals("alice", payload.getStr("username"));
        assertEquals(10, payload.getInt("score"));
        assertEquals(entity.getId(), payload.getLong("id"));
        assertEquals("alice", jdbcTemplate.queryForObject(
                "SELECT username FROM " + TABLE_NAME + " WHERE id = ?", String.class, entity.getId()));
    }

    @Test
    void updateWithWrapperDeliversStructuredCondition() {
        ListenerUserEntity entity = insert("bob", 20);

        UpdateWrapper<ListenerUserEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", entity.getId()).set("status", "disabled");
        int rows = userMapper.update(null, wrapper);

        assertEquals(1, rows);
        assertEquals(1, UserChangeRecorder.UPDATE_EVENTS.size());
        UserChangeRecorder.UpdateEvent event = UserChangeRecorder.UPDATE_EVENTS.getFirst();
        assertEquals(entity.getId(), event.keyValuePairs().getLong("id"));
        assertEquals("=", event.keyOperatorPairs().getStr("id"));
        assertTrue(Objects.nonNull(event.rawWhereSql()) && !event.rawWhereSql().isBlank());
        assertEquals("disabled", jdbcTemplate.queryForObject(
                "SELECT status FROM " + TABLE_NAME + " WHERE id = ?", String.class, entity.getId()));
    }

    @Test
    void updateByIdDeliversEntityWithoutInventedCondition() {
        ListenerUserEntity entity = insert("carol", 30);
        ListenerUserEntity patch = new ListenerUserEntity();
        patch.setId(entity.getId());
        patch.setStatus("archived");

        int rows = userMapper.updateById(patch);

        assertEquals(1, rows);
        assertEquals(1, UserChangeRecorder.UPDATE_EVENTS.size());
        UserChangeRecorder.UpdateEvent event = UserChangeRecorder.UPDATE_EVENTS.getFirst();
        assertEquals(entity.getId(), event.data().getLong("id"));
        assertTrue(event.keyValuePairs().isEmpty());
        assertTrue(event.keyOperatorPairs().isEmpty());
        assertEquals("archived", jdbcTemplate.queryForObject(
                "SELECT status FROM " + TABLE_NAME + " WHERE id = ?", String.class, entity.getId()));
    }

    @Test
    void conditionalDeleteDeliversPhysicalDeleteEvent() {
        ListenerUserEntity entity = insert("dave", 40);

        int rows = userMapper.deleteCondition(conditionById(entity.getId()));

        assertEquals(1, rows);
        assertEquals(1, UserChangeRecorder.DELETE_EVENTS.size());
        Dict payload = UserChangeRecorder.DELETE_EVENTS.getFirst();
        assertEquals("deleteCondition", payload.getStr("operation"));
        assertEquals(entity.getId(), ((Dict) payload.get("conditionFieldData")).getLong("id"));
        assertEquals(0, countRows(entity.getId()));
    }

    @Test
    void conditionalSoftDeleteDeliversDeleteSoftEvent() {
        ListenerUserEntity entity = insert("erin", 50);

        int rows = userMapper.deleteSoftCondition(conditionById(entity.getId()),
                List.of(new GXUpdateStrField(null, "status", "deleted")));

        assertEquals(1, rows);
        assertEquals(1, UserChangeRecorder.DELETE_SOFT_EVENTS.size());
        Dict payload = UserChangeRecorder.DELETE_SOFT_EVENTS.getFirst();
        assertEquals(entity.getId(), ((Dict) payload.get("conditionFieldData")).getLong("id"));
        assertEquals("deleted", jdbcTemplate.queryForObject(
                "SELECT status FROM " + TABLE_NAME + " WHERE id = ?", String.class, entity.getId()));
        assertEquals(1, countRows(entity.getId()));
    }

    @Test
    void asyncListenerRunsAfterCommitOnExecutorThread() throws Exception {
        ListenerUserEntity entity = newEntity("frank", 60);

        new TransactionTemplate(transactionManager).execute(status -> {
            asyncUserMapper.insert(entity);
            return null;
        });

        await(Duration.ofSeconds(5), () -> !AsyncUserChangeRecorder.SAVE_EVENTS.isEmpty());

        assertEquals(1, AsyncUserChangeRecorder.SAVE_EVENTS.size());
        assertEquals("frank", AsyncUserChangeRecorder.SAVE_EVENTS.getFirst().getStr("username"));
        String threadName = AsyncUserChangeRecorder.EXECUTING_THREAD.get();
        assertNotNull(threadName);
        assertTrue(threadName.startsWith("maple-framework-mybatis-event-async-"),
                () -> "Unexpected executor thread: " + threadName);
        assertEquals(1, countRows(entity.getId()));
    }

    @Test
    void asyncListenerIsNotDeliveredWhenTransactionRollsBack() throws Exception {
        ListenerUserEntity entity = newEntity("grace", 70);

        new TransactionTemplate(transactionManager).execute(status -> {
            asyncUserMapper.insert(entity);
            status.setRollbackOnly();
            return null;
        });

        Thread.sleep(500L);

        assertTrue(AsyncUserChangeRecorder.SAVE_EVENTS.isEmpty());
        assertEquals(0, countRows(entity.getId()));
    }

    @Test
    void twoTableAsyncTransactionDeliversBothEventsAfterCommit() throws Exception {
        new TransactionTemplate(transactionManager).execute(status -> {
            ListenerOrderEntity order = newOrder("ORD-1001", 100);
            orderMapper.insert(order);
            orderItemMapper.insert(newItem(order.getId(), "SKU-A", 2));
            return null;
        });

        await(Duration.ofSeconds(5),
                () -> OrderEventRecorder.OBSERVED_ITEM_COUNTS.size() == 1 && OrderItemEventRecorder.EVENTS.size() == 1);

        assertEquals(1, OrderEventRecorder.EVENTS.size());
        assertEquals(1, OrderItemEventRecorder.EVENTS.size());
        assertEquals(List.of(1), OrderEventRecorder.OBSERVED_ITEM_COUNTS);
        assertEquals(1, countRowsOf("t_listener_order"));
        assertEquals(1, countRowsOf("t_listener_order_item"));
    }

    @Test
    void twoTableAsyncTransactionRollsBackWhenSecondTableWriteFails() throws Exception {
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactionManager).execute(status -> {
            ListenerOrderEntity order = newOrder("ORD-1002", 200);
            orderMapper.insert(order);
            orderItemMapper.insert(newItem(order.getId(), "SKU-B", 1));
            orderMapper.insert(newOrder("ORD-1002", 200));
            return null;
        }));

        Thread.sleep(400L);

        assertTrue(OrderEventRecorder.EVENTS.isEmpty());
        assertTrue(OrderItemEventRecorder.EVENTS.isEmpty());
        assertEquals(0, countRowsOf("t_listener_order"));
        assertEquals(0, countRowsOf("t_listener_order_item"));
    }

    @Test
    void syncEventsFireInsideTransactionAndSeePartialState() throws Exception {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).execute(status -> {
            ListenerOrderEntity order = newOrder("ORD-1003", 300);
            syncOrderMapper.insert(order);
            syncOrderItemMapper.insert(newItem(order.getId(), "SKU-C", 3));
            throw new IllegalStateException("rollback after both tables");
        }));

        Thread.sleep(200L);

        assertEquals(1, SyncOrderEventRecorder.EVENTS.size());
        assertEquals(1, SyncOrderItemEventRecorder.EVENTS.size());
        assertEquals(0, SyncOrderEventRecorder.OBSERVED_ITEM_COUNTS.getFirst());
        assertEquals(0, countRowsOf("t_listener_order"));
        assertEquals(0, countRowsOf("t_listener_order_item"));
    }

    @Test
    void conditionlessUpdateIsRejectedBeforeAnyPayloadIsBuilt() {
        ListenerUserEntity entity = insert("carol", 30);

        UpdateWrapper<ListenerUserEntity> wrapper = new UpdateWrapper<>();
        wrapper.set("status", "archived");

        // BlockAttackInnerInterceptor rejects a full-table update, so the "no condition" branch of the
        // payload parser is defensive: the database is never reached and nothing is published.
        assertThrows(RuntimeException.class, () -> userMapper.update(null, wrapper));

        assertTrue(UserChangeRecorder.UPDATE_EVENTS.isEmpty(), "A rejected write must not deliver any event");
        assertEquals("enabled", jdbcTemplate.queryForObject(
                        "SELECT status FROM " + TABLE_NAME + " WHERE id = ?", String.class, entity.getId()),
                "A full-table update must be blocked before touching data");
    }

    @Test
    void officialCollectionInsertBypassesTheListenerChain() {
        ListenerUserEntity first = newEntity("official-a", 1);
        ListenerUserEntity second = newEntity("official-b", 2);

        List<BatchResult> batchResults = userMapper.insert(List.of(first, second));

        assertFalse(batchResults.isEmpty(), "The official JDBC batch API must report its statement results");
        assertEquals(2, countRowsOf(TABLE_NAME), "Both rows must be persisted");
        assertTrue(UserChangeRecorder.SAVE_EVENTS.isEmpty(),
                "BaseMapper.insert(Collection) bypasses the intercepted per-row insert, so no event is delivered");
        assertTrue(UserBatchChangeRecorder.BATCH_EVENTS.isEmpty(),
                "The official batch API is not covered by the service-layer batch aspect");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveBatchDeliversOneEventWithDetachedEntityValues() {
        ListenerUserEntity first = newEntity("batch-a", 1);
        first.setExt(Dict.create().set("trace", "original"));
        ListenerUserEntity second = newEntity("batch-b", 2);
        second.setExt(Dict.create().set("trace", "original"));

        assertTrue(userBatchService.saveBatch(List.of(first, second)));

        assertEquals(2, countRowsOf(TABLE_NAME));
        assertEquals(1, UserBatchChangeRecorder.BATCH_EVENTS.size(),
                "A batch write must deliver exactly one event");
        List<Dict> rows = UserBatchChangeRecorder.BATCH_EVENTS.getFirst();
        assertEquals(2, rows.size());
        assertEquals("batch-a", rows.getFirst().getStr("username"));
        assertEquals("batch-b", rows.get(1).getStr("username"));

        Object payloadExt = rows.getFirst().get("ext");
        assertTrue(payloadExt instanceof Map<?, ?>, "The entity `ext` must stay a map in the payload");
        assertNotSame(first.getExt(), payloadExt,
                "Entity-owned values must be copied into the batch payload");

        ((Map<Object, Object>) payloadExt).put("trace", "listener-mutation");

        assertEquals("original", first.getExt().getStr("trace"),
                "A listener mutating the batch payload must not reach the business entity");
        assertEquals("original", jdbcTemplate.queryForObject(
                        "SELECT JSON_UNQUOTE(JSON_EXTRACT(ext, '$.trace')) FROM " + TABLE_NAME + " WHERE id = ?",
                        String.class, first.getId()),
                "The persisted JSON must stay unchanged");
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

    private ListenerUserEntity newEntity(String username, int score) {
        ListenerUserEntity entity = new ListenerUserEntity();
        entity.setUsername(username);
        entity.setScore(score);
        entity.setStatus("enabled");
        return entity;
    }

    private ListenerUserEntity insert(String username, int score) {
        ListenerUserEntity entity = newEntity(username, score);
        userMapper.insert(entity);
        UserChangeRecorder.reset();
        return entity;
    }

    private GXBaseQueryParamInnerDto conditionById(Long id) {
        return GXBaseQueryParamInnerDto.builder()
                .tableName(TABLE_NAME)
                .condition(List.of(new GXConditionEQ(null, "id", id)))
                .build();
    }

    private Integer countRows(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE id = ?", Integer.class, id);
        return Objects.isNull(count) ? 0 : count;
    }

    private void await(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && !condition.getAsBoolean()) {
            Thread.sleep(20L);
        }
        assertFalse(System.nanoTime() >= deadline && !condition.getAsBoolean(), "Condition not met within " + timeout);
    }
}
