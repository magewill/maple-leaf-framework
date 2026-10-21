package cn.maple.core.datasource.it;

import cn.maple.core.datasource.config.GXDynamicContextHolder;
import cn.maple.core.datasource.it.service.MultiDataSourceTestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end Seata AT verification across two schemas (test / test1).
 *
 * <p>Requires a running Seata TC on 127.0.0.1:8091 and the opt-in flags
 * {@code -Dmaple.it.enabled=true -Dmaple.it.seata.enabled=true} plus the datasource properties.</p>
 */
@SpringBootTest(classes = SeataMultiDataSourceITApplication.class, properties = {
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.main.banner-mode=off",
        "seata.enabled=true",
        "seata.application-id=maple-leaf-it",
        "seata.tx-service-group=maple_it_tx_group",
        "seata.enable-auto-data-source-proxy=false",
        "seata.config.type=file",
        "seata.registry.type=file",
        "seata.client.undo.log-serialization=fastjson2",
        "seata.service.vgroup-mapping.maple_it_tx_group=default",
        "seata.service.grouplist.default=127.0.0.1:8091"
})
@EnabledIfSystemProperty(named = "maple.it.enabled", matches = "true")
@EnabledIfSystemProperty(named = "maple.it.seata.enabled", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SeataGlobalTransactionIT {
    @Autowired
    private MultiDataSourceTestService testService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        clean("framework");
        clean("slave1");
        GXDynamicContextHolder.clear();
    }

    @Test
    void globalTransactionCommitsBothDatasources() {
        testService.globalWriteThenCommit("ORD-G1", "gina");

        assertEquals(1, countOn("framework", "t_listener_order"));
        assertEquals(1, countOn("slave1", "t_listener_account"));
        awaitUndoLogCleared("framework");
        awaitUndoLogCleared("slave1");
    }

    @Test
    void globalTransactionRollsBackBothDatasources() {
        MultiDataSourceTestService.OBSERVED_BEFORE_GLOBAL_ROLLBACK.set(new int[]{-1, -1});

        assertThrows(IllegalStateException.class,
                () -> testService.globalWriteThenRollback("ORD-G2", "henry"));

        int[] beforeRollback = MultiDataSourceTestService.OBSERVED_BEFORE_GLOBAL_ROLLBACK.get();
        assertEquals(1, beforeRollback[0], "Order should be written inside the global transaction");
        assertEquals(1, beforeRollback[1], "Account should be written inside the global transaction");
        assertEquals(0, countOn("framework", "t_listener_order"), "Global rollback must undo the order");
        assertEquals(0, countOn("slave1", "t_listener_account"), "Global rollback must undo the account");
        awaitUndoLogCleared("framework");
        awaitUndoLogCleared("slave1");
    }

    private void awaitUndoLogCleared(String dataSourceName) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (countOn(dataSourceName, "undo_log") == 0) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertEquals(0, countOn(dataSourceName, "undo_log"),
                "undo_log was not cleaned up for datasource " + dataSourceName);
    }

    private void clean(String dataSourceName) {
        GXDynamicContextHolder.executeOnVoid(dataSourceName, () -> {
            jdbcTemplate.update("DELETE FROM t_listener_order");
            jdbcTemplate.update("DELETE FROM t_listener_account");
            jdbcTemplate.update("DELETE FROM undo_log");
        });
    }

    private Integer countOn(String dataSourceName, String tableName) {
        return GXDynamicContextHolder.executeOn(dataSourceName, (Supplier<Integer>) () -> {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
            return Objects.isNull(count) ? 0 : count;
        });
    }
}
