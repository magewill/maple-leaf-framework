package cn.maple.core.datasource.config;

import org.apache.seata.core.model.BranchType;
import org.apache.seata.rm.datasource.AbstractDataSourceProxy;
import org.apache.seata.rm.datasource.SeataDataSourceProxy;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GXSeataWrapperContractTest {
    @Test
    void wrappedTargetIsSeataProxyAndExposesOriginalInstance() {
        DataSource original = new StubDataSource();
        DataSource wrapped = new StubSeataDataSource(original);

        assertThat(wrapped).isInstanceOf(SeataDataSourceProxy.class);
        assertThat(((SeataDataSourceProxy) wrapped).getTargetDataSource()).isSameAs(original);
    }

    @Test
    void routingDataSourceIsNeverWrappedBySeataProxy() {
        DataSource target = new StubSeataDataSource(new StubDataSource());
        GXDynamicDataSource routingDataSource = new GXDynamicDataSource();
        routingDataSource.setTargetDataSources(Map.of("framework", target));
        routingDataSource.setDefaultTargetDataSource(target);
        routingDataSource.afterPropertiesSet();

        assertThat(routingDataSource).isNotInstanceOf(SeataDataSourceProxy.class);
        assertThat(routingDataSource.getResolvedDataSources().get("framework")).isInstanceOf(SeataDataSourceProxy.class);
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

    private static final class StubDataSource extends AbstractDataSource {
        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("stub datasource does not provide connections");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("stub datasource does not provide connections");
        }
    }
}
