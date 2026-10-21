package cn.maple.core.datasource.it;

import cn.maple.core.datasource.config.GXDynamicContextHolder;
import cn.maple.core.datasource.config.GXDynamicDataSource;
import cn.maple.core.datasource.it.service.MultiDataSourceTestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies dynamic datasource switching against two real schemas (test / test1).
 *
 * <p>Enable with {@code -Dmaple.it.enabled=true -DIT_DB_URL=... -DIT_DB1_URL=... -DIT_DB_USER=... -DIT_DB_PASSWORD=...}.</p>
 */
@SpringBootTest(classes = MultiDataSourceITApplication.class, properties = {
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "seata.enabled=false",
        "spring.autoconfigure.exclude=org.apache.seata.spring.boot.autoconfigure.SeataAutoConfiguration,org.apache.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration",
        "spring.main.banner-mode=off"
})
@EnabledIfSystemProperty(named = "maple.it.enabled", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MultiDataSourceIT {
    @Autowired
    private MultiDataSourceTestService testService;

    @Autowired
    private GXDynamicDataSource dynamicDataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        GXDynamicContextHolder.executeOnVoid("framework", () -> {
            jdbcTemplate.update("DELETE FROM t_listener_order");
            jdbcTemplate.update("DELETE FROM t_listener_account");
        });
        GXDynamicContextHolder.executeOnVoid("slave1", () -> {
            jdbcTemplate.update("DELETE FROM t_listener_account");
            jdbcTemplate.update("DELETE FROM t_listener_order");
        });
        GXDynamicContextHolder.clear();
    }

    @Test
    void annotationRoutesWriteToSlaveDatasource() {
        int accountRows = testService.createAccountOnSlave("alice", 100);
        int orderRows = testService.createOrderOnDefault("ORD-M1", 10);

        assertEquals(1, accountRows);
        assertEquals(1, orderRows);
        assertEquals(1, countOn("slave1", "t_listener_account"));
        assertEquals(0, countOn("framework", "t_listener_account"));
        assertEquals(1, countOn("framework", "t_listener_order"));
        assertEquals(0, countOn("slave1", "t_listener_order"));
    }

    @Test
    void nestedSwitchRestoresOuterDatasource() {
        testService.mixedWorkflow("ORD-M2", "bob");

        assertEquals(2, countOn("framework", "t_listener_order"));
        assertEquals(1, countOn("slave1", "t_listener_account"));
        assertEquals(0, countOn("framework", "t_listener_account"));
        assertEquals(0, countOn("slave1", "t_listener_order"));
        assertNull(testService.currentContext(), "Context must be restored after nested switching");
    }

    @Test
    void contextHolderIsClearedWhenSwitchedBlockThrows() {
        assertThrows(IllegalStateException.class, () -> testService.failInsideSlaveContext());

        assertNull(testService.currentContext(), "Failed switching must not leak the datasource key");
        assertEquals(0, countOn("slave1", "t_listener_account"));
    }

    @Test
    void bothTargetDataSourcesAreWrappedBySeataProxy() {
        Map<?, ?> resolvedDataSources = (Map<?, ?>) ReflectionTestUtils.getField(dynamicDataSource, "resolvedDataSources");

        assertEquals(2, Objects.requireNonNull(resolvedDataSources).size());
        resolvedDataSources.forEach((name, dataSource) -> assertTrue(
                dataSource.getClass().getName().contains("DataSourceProxy"),
                () -> "Datasource " + name + " is not wrapped by Seata DataSourceProxy: " + dataSource.getClass().getName()));
    }

    @Test
    void localTransactionDoesNotSpanDataSources() {
        MultiDataSourceTestService.OBSERVED_FRAMEWORK_ACCOUNT_COUNT.set(-1);

        assertThrows(IllegalStateException.class,
                () -> testService.crossDataSourceTransactionThenRollback("ORD-M3", "carol"));

        assertEquals(0, countOn("framework", "t_listener_order"), "Default datasource must roll back");
        assertEquals(0, countOn("framework", "t_listener_account"));
        assertEquals(0, countOn("slave1", "t_listener_account"), "Slave writes must not leak outside the transaction");
        assertEquals(1, MultiDataSourceTestService.OBSERVED_FRAMEWORK_ACCOUNT_COUNT.get(),
                "The slave-directed insert is expected to reuse the transaction-bound connection");
    }

    private Integer countOn(String dataSourceName, String tableName) {
        return GXDynamicContextHolder.executeOn(dataSourceName, (Supplier<Integer>) () -> {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
            return Objects.isNull(count) ? 0 : count;
        });
    }
}
