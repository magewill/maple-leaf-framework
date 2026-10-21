package cn.maple.seata.config;

import cn.maple.core.datasource.config.GXDynamicDataSource;
import cn.maple.seata.properties.GXLocalSeataProperties;
import cn.maple.seata.properties.GXNacosSeataProperties;
import org.apache.logging.log4j.Level;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.rm.datasource.AbstractDataSourceProxy;
import org.apache.seata.rm.datasource.SeataDataSourceProxy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GXSeataProxyAssemblyTest {
    private static final String MISSING_MESSAGE = "Seata datasource proxy is required but missing for [framework]";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GXSeataDynamicDataSourceConfig.class);

    @Test
    void reportsProxiedDataSourcesAtInfoLevel() {
        GXLogCapture capture = GXLogCapture.attach(GXSeataDynamicDataSourceConfig.class);
        try {
            runner.withUserConfiguration(ProxiedDataSourceConfig.class)
                    .run(context -> assertThat(context).hasNotFailed());

            assertThat(capture.hasEvent(Level.INFO, "[Seata] enabled=true")).isTrue();
            assertThat(capture.hasEvent(Level.INFO, "proxied=[framework]")).isTrue();
            assertThat(capture.count(Level.WARN)).isZero();
        } finally {
            capture.detach();
        }
    }

    @Test
    void reportsUnproxiedDataSourceAtWarnLevelWithoutFailingStartup() {
        GXLogCapture capture = GXLogCapture.attach(GXSeataDynamicDataSourceConfig.class);
        try {
            runner.withUserConfiguration(PlainDataSourceConfig.class)
                    .run(context -> assertThat(context).hasNotFailed());

            assertThat(capture.hasEvent(Level.WARN, "datasource 'framework' is NOT wrapped by Seata")).isTrue();
        } finally {
            capture.detach();
        }
    }

    @Test
    void reportsLocalConfigSourceWhenLocalPropertiesBeanIsPresent() {
        GXLogCapture capture = GXLogCapture.attach(GXSeataDynamicDataSourceConfig.class);
        try {
            runner.withUserConfiguration(ProxiedDataSourceConfig.class, LocalConfigSourceConfig.class)
                    .run(context -> assertThat(context).hasNotFailed());

            assertThat(capture.hasEvent(Level.INFO, "config-source=local")).isTrue();
        } finally {
            capture.detach();
        }
    }

    @Test
    void reportsNacosConfigSourceWhenNacosPropertiesBeanIsPresent() {
        GXLogCapture capture = GXLogCapture.attach(GXSeataDynamicDataSourceConfig.class);
        try {
            runner.withUserConfiguration(ProxiedDataSourceConfig.class, NacosConfigSourceConfig.class)
                    .run(context -> assertThat(context).hasNotFailed());

            assertThat(capture.hasEvent(Level.INFO, "config-source=nacos")).isTrue();
        } finally {
            capture.detach();
        }
    }

    @Test
    void failsWhenStrictModeIsEnabledAndTargetDataSourceIsNotProxied() {
        runner.withUserConfiguration(PlainDataSourceConfig.class)
                .withPropertyValues(GXSeataDynamicDataSourceConfig.STRICT_SWITCH_KEY + "=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(MISSING_MESSAGE);
                });
    }

    private static GXDynamicDataSource buildRoutingDataSource(DataSource target) {
        GXDynamicDataSource routingDataSource = new GXDynamicDataSource();
        routingDataSource.setTargetDataSources(Map.of("framework", target));
        routingDataSource.setDefaultTargetDataSource(target);
        routingDataSource.setDefaultDataSourceName("framework");
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    @Configuration(proxyBeanMethods = false)
    static class ProxiedDataSourceConfig {
        @Bean
        GXDynamicDataSource dynamicDataSource() {
            return buildRoutingDataSource(new StubSeataDataSource(new StubDataSource()));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PlainDataSourceConfig {
        @Bean
        GXDynamicDataSource dynamicDataSource() {
            return buildRoutingDataSource(new StubDataSource());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LocalConfigSourceConfig {
        @Bean
        GXLocalSeataProperties gxLocalSeataProperties() {
            return new GXLocalSeataProperties();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NacosConfigSourceConfig {
        @Bean
        GXNacosSeataProperties gxNacosSeataProperties() {
            return new GXNacosSeataProperties();
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
