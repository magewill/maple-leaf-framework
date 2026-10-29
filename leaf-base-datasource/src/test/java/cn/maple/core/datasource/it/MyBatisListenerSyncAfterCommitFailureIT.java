package cn.maple.core.datasource.it;

import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;
import cn.maple.core.datasource.it.entity.ListenerUserEntity;
import cn.maple.core.datasource.it.listener.ThrowingUserListener;
import cn.maple.core.datasource.it.mapper.ListenerUserThrowingSyncMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the {@code sync-after-commit} exception semantics against a real MySQL instance: only a
 * real transaction defers the callback, and only the inline path keeps the {@code propagate-exception}
 * behaviour (contracts/listener-callback.md).
 */
@SpringBootTest(classes = MyBatisListenerITApplication.class, properties = {
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "seata.enabled=false",
        "spring.autoconfigure.exclude=org.apache.seata.spring.boot.autoconfigure.SeataAutoConfiguration,org.apache.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration",
        "maple.framework.mybatis.listener.sync-after-commit=true",
        "maple.framework.mybatis.listener.propagate-exception=true",
        "spring.main.banner-mode=off"
})
@EnabledIfSystemProperty(named = "maple.it.enabled", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MyBatisListenerSyncAfterCommitFailureIT {
    private static final String TABLE_NAME = "t_listener_user";

    @Autowired
    private ListenerUserThrowingSyncMapper throwingUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM " + TABLE_NAME);
        ThrowingUserListener.reset();
    }

    @Test
    void inlineSyncAfterCommitKeepsThePropagationSemantics() {
        ListenerUserEntity entity = newEntity("inline-propagate");
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> throwingUserMapper.insert(entity));

        assertEquals("throwing listener boom", failure.getMessage(),
                "Without an active transaction sync-after-commit publishes inline, so propagate-exception applies");
        assertEquals(1, ThrowingUserListener.CALLS.get());
        assertEquals(1, countRows(entity.getId()), "The row was already committed when the callback ran");
        assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed(),
                "The propagating callback failure must be counted exactly once");
    }

    @Test
    void deferredSyncAfterCommitStaysIsolatedInsideARealTransaction() {
        ListenerUserEntity entity = newEntity("deferred-isolated");
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();

        Integer inserted = assertDoesNotThrow(() -> new TransactionTemplate(transactionManager)
                        .execute(status -> throwingUserMapper.insert(entity)),
                "A deferred callback runs after the commit point, so it must never fail the write");

        assertEquals(1, inserted);
        assertEquals(1, ThrowingUserListener.CALLS.get());
        assertEquals(1, countRows(entity.getId()));
        assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed(),
                "The isolated deferred callback failure must be counted exactly once");
    }

    private ListenerUserEntity newEntity(String username) {
        ListenerUserEntity entity = new ListenerUserEntity();
        entity.setUsername(username);
        entity.setScore(1);
        entity.setStatus("enabled");
        return entity;
    }

    private Integer countRows(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE id = ?", Integer.class, id);
        return Objects.isNull(count) ? 0 : count;
    }
}
