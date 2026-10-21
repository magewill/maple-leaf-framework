package cn.maple.core.datasource.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import cn.maple.core.datasource.handler.GXAutoFillMetaObjectHandler;
import cn.maple.core.datasource.interceptor.GXDataFilterInterceptor;
import cn.maple.core.datasource.properties.GXDataSourceProperties;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.rm.datasource.AbstractDataSourceProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GXSeataDataSourceParityTest {
    @AfterEach
    void clearContext() {
        GXDynamicContextHolder.clear();
    }

    @Test
    void routingSequenceAndContextRestoreAreIdenticalWithAndWithoutWrapper() throws SQLException {
        assertThat(routeAndRecord(true)).isEqualTo(routeAndRecord(false));
    }

    @Test
    void autoFillHandlerStillFillsAuditFields() {
        AutoFillEntity entity = new AutoFillEntity();

        new GXAutoFillMetaObjectHandler().insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreatedBy()).isEqualTo("unknown");
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void mybatisPlusInterceptorChainStillContainsPaginationTenantAndBlockAttack() {
        GXMyBatisPlusConfig config = new GXMyBatisPlusConfig();
        GXDataSourceProperties dataSourceProperties = new GXDataSourceProperties();
        dataSourceProperties.setDbType("mysql");
        ReflectionTestUtils.setField(config, "applicationContext", new StaticApplicationContext());
        ReflectionTestUtils.setField(config, "dataSourceProperties", dataSourceProperties);

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .hasAtLeastOneElementOfType(GXDataFilterInterceptor.class)
                .hasAtLeastOneElementOfType(BlockAttackInnerInterceptor.class)
                .hasAtLeastOneElementOfType(PaginationInnerInterceptor.class);
    }

    private List<String> routeAndRecord(boolean wrapWithSeata) throws SQLException {
        DataSource ds1 = new RecordingDataSource("ds1");
        DataSource ds2 = new RecordingDataSource("ds2");
        GXDynamicDataSource routingDataSource = new GXDynamicDataSource();
        routingDataSource.setTargetDataSources(Map.of(
                "ds1", wrapWithSeata ? new StubSeataDataSource(ds1) : ds1,
                "ds2", wrapWithSeata ? new StubSeataDataSource(ds2) : ds2));
        routingDataSource.setDefaultTargetDataSource(wrapWithSeata ? new StubSeataDataSource(ds1) : ds1);
        routingDataSource.afterPropertiesSet();

        List<String> sequence = new ArrayList<>();
        GXDynamicContextHolder.push("ds1");
        sequence.add(servedBy(routingDataSource));
        GXDynamicContextHolder.push("ds2");
        sequence.add(servedBy(routingDataSource));
        GXDynamicContextHolder.poll();
        sequence.add(servedBy(routingDataSource));
        return sequence;
    }

    private String servedBy(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return connection.toString();
        }
    }

    private static final class StubSeataDataSource extends AbstractDataSourceProxy {
        private StubSeataDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public BranchType getBranchType() {
            return BranchType.AT;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return getTargetDataSource().getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getTargetDataSource().getConnection(username, password);
        }
    }

    private static final class RecordingDataSource extends AbstractDataSource {
        private final Connection connection;

        private RecordingDataSource(String name) {
            this.connection = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class[]{Connection.class},
                    (proxy, method, args) -> defaultValue(proxy, method, name));
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return connection;
        }

        private static Object defaultValue(Object proxy, Method method, String name) {
            return switch (method.getName()) {
                case "toString" -> name;
                case "getAutoCommit" -> true;
                case "isClosed", "isReadOnly", "isWrapperFor" -> false;
                case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                default -> {
                    Class<?> returnType = method.getReturnType();
                    if (!returnType.isPrimitive()) {
                        yield null;
                    }
                    if (returnType == boolean.class) {
                        yield false;
                    }
                    if (returnType == int.class) {
                        yield 0;
                    }
                    if (returnType == long.class) {
                        yield 0L;
                    }
                    if (returnType == short.class) {
                        yield (short) 0;
                    }
                    if (returnType == byte.class) {
                        yield (byte) 0;
                    }
                    if (returnType == char.class) {
                        yield '\0';
                    }
                    if (returnType == float.class) {
                        yield 0F;
                    }
                    if (returnType == double.class) {
                        yield 0D;
                    }
                    yield null;
                }
            };
        }
    }

    public static class AutoFillEntity {
        private String createdBy;
        private Integer createdAt;

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }

        public Integer getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Integer createdAt) {
            this.createdAt = createdAt;
        }
    }
}
