package cn.maple.core.datasource.it.service;

import cn.maple.core.datasource.annotation.GXDataSource;
import cn.maple.core.datasource.config.GXDynamicContextHolder;
import cn.maple.core.datasource.it.entity.ListenerAccountEntity;
import cn.maple.core.datasource.it.entity.ListenerOrderEntity;
import cn.maple.core.datasource.it.mapper.ListenerAccountMapper;
import cn.maple.core.datasource.it.mapper.ListenerOrderMapper;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service used to verify dynamic datasource switching and transaction boundaries.
 */
@Component
public class MultiDataSourceTestService {
    /**
     * Account rows visible through the transaction-bound (framework) connection after the
     * slave-directed insert.
     */
    public static final AtomicInteger OBSERVED_FRAMEWORK_ACCOUNT_COUNT = new AtomicInteger(-1);

    /**
     * Rows visible on each datasource right before the global transaction is rolled back.
     */
    public static final java.util.concurrent.atomic.AtomicReference<int[]> OBSERVED_BEFORE_GLOBAL_ROLLBACK =
            new java.util.concurrent.atomic.AtomicReference<>(new int[]{-1, -1});

    private final ListenerOrderMapper orderMapper;
    private final ListenerAccountMapper accountMapper;

    public MultiDataSourceTestService(ListenerOrderMapper orderMapper, ListenerAccountMapper accountMapper) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
    }

    @GXDataSource("slave1")
    public int createAccountOnSlave(String owner, int balance) {
        return accountMapper.insert(account(owner, balance));
    }

    public int createOrderOnDefault(String orderNo, int amount) {
        return orderMapper.insert(order(orderNo, amount));
    }

    /**
     * Inserts an order on the default datasource, switches to the slave datasource in the middle,
     * then inserts another order on the default datasource.
     */
    @GXDataSource("framework")
    public void mixedWorkflow(String orderNo, String owner) {
        orderMapper.insert(order(orderNo + "-1", 10));
        GXDynamicContextHolder.executeOn("slave1", () -> accountMapper.insert(account(owner, 100)));
        orderMapper.insert(order(orderNo + "-2", 20));
    }

    public String currentContext() {
        return GXDynamicContextHolder.peek();
    }

    public void failInsideSlaveContext() {
        GXDynamicContextHolder.executeOnVoid("slave1", () -> {
            throw new IllegalStateException("boom inside slave context");
        });
    }

    /**
     * Writes to both schemas inside a single local transaction, then rolls back.
     * Used to demonstrate that a local transaction does not span datasources.
     */
    @Transactional(rollbackFor = Exception.class)
    public void crossDataSourceTransactionThenRollback(String orderNo, String owner) {
        orderMapper.insert(order(orderNo, 999));
        GXDynamicContextHolder.executeOnVoid("slave1", () -> accountMapper.insert(account(owner, 888)));
        Long visibleThroughTransaction = GXDynamicContextHolder.executeOn("framework", () -> accountMapper.selectCount(null));
        OBSERVED_FRAMEWORK_ACCOUNT_COUNT.set(visibleThroughTransaction == null ? 0 : visibleThroughTransaction.intValue());
        throw new IllegalStateException("rollback after writing to two datasources");
    }

    /**
     * Writes to both datasources inside one global transaction and commits.
     */
    @GlobalTransactional(name = "it-global-commit", rollbackFor = Exception.class)
    public void globalWriteThenCommit(String orderNo, String owner) {
        orderMapper.insert(order(orderNo, 333));
        GXDynamicContextHolder.executeOnVoid("slave1", () -> accountMapper.insert(account(owner, 444)));
    }

    /**
     * Writes to both datasources inside one global transaction, then triggers a rollback.
     */
    @GlobalTransactional(name = "it-global-rollback", rollbackFor = Exception.class)
    public void globalWriteThenRollback(String orderNo, String owner) {
        orderMapper.insert(order(orderNo, 555));
        GXDynamicContextHolder.executeOnVoid("slave1", () -> accountMapper.insert(account(owner, 666)));
        Long orderCount = GXDynamicContextHolder.executeOn("framework", () -> orderMapper.selectCount(null));
        Long accountCount = GXDynamicContextHolder.executeOn("slave1", () -> accountMapper.selectCount(null));
        OBSERVED_BEFORE_GLOBAL_ROLLBACK.set(new int[]{
                orderCount == null ? 0 : orderCount.intValue(),
                accountCount == null ? 0 : accountCount.intValue()
        });
        throw new IllegalStateException("rollback after global write");
    }

    private ListenerOrderEntity order(String orderNo, int amount) {
        ListenerOrderEntity entity = new ListenerOrderEntity();
        entity.setOrderNo(orderNo);
        entity.setAmount(amount);
        return entity;
    }

    private ListenerAccountEntity account(String owner, int balance) {
        ListenerAccountEntity entity = new ListenerAccountEntity();
        entity.setOwner(owner);
        entity.setBalance(balance);
        return entity;
    }
}
