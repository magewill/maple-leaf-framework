package cn.maple.core.datasource.config;

import org.apache.logging.log4j.Level;
import org.apache.seata.rm.datasource.SeataDataSourceProxy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class GXSeataProxyContractTest {
    @Test
    void proxyClassIsResolvedFromNewNamespace() {
        assertThat(GXDynamicDataSourceConfig.SEATA_DATASOURCE_PROXY_CLASS)
                .isEqualTo("org.apache.seata.rm.datasource.DataSourceProxy")
                .doesNotStartWith("io.seata");
    }

    @Test
    void proxyClassImplementsSeataDataSourceProxyAndKeepsDataSourceConstructor() throws Exception {
        Class<?> proxyClass = Class.forName(GXDynamicDataSourceConfig.SEATA_DATASOURCE_PROXY_CLASS, false,
                getClass().getClassLoader());

        assertThat(DataSource.class).isAssignableFrom(proxyClass);
        assertThat(SeataDataSourceProxy.class).isAssignableFrom(proxyClass);

        Constructor<?> constructor = proxyClass.getConstructor(DataSource.class);
        assertThat(constructor).isNotNull();
    }

    @Test
    void missingProxyClassFallsBackToOriginalDataSource() {
        DataSource original = new StubDataSource();

        DataSource wrapped = new GXDynamicDataSourceConfig().wrapSeataDataSource(original,
                new FilteredClassLoader("org.apache.seata.rm.datasource"));

        assertThat(wrapped).isSameAs(original);
    }

    @Test
    void fallbackPathLogsDebugWithoutWarnings() {
        GXLogCapture capture = GXLogCapture.attach(GXDynamicDataSourceConfig.class);
        try {
            DataSource original = new StubDataSource();

            DataSource wrapped = new GXDynamicDataSourceConfig().wrapSeataDataSource(original,
                    new FilteredClassLoader("org.apache.seata.rm.datasource"));

            assertThat(wrapped).isSameAs(original);
            assertThat(capture.hasEvent(Level.DEBUG, "Seata DataSourceProxy not found")).isTrue();
            assertThat(capture.count(Level.WARN)).isZero();
            assertThat(capture.count(Level.ERROR)).isZero();
        } finally {
            capture.detach();
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
