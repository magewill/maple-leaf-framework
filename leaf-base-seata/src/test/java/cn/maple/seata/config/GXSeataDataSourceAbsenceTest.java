package cn.maple.seata.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.SqlSessionFactoryBeanCustomizer;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class GXSeataDataSourceAbsenceTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GXSeataDynamicDataSourceConfig.class);

    @Test
    void startsWithoutDynamicDataSourceAndReportsSkipReason() {
        GXLogCapture capture = GXLogCapture.attach(GXSeataDynamicDataSourceConfig.class);
        try {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("seataDynamicDataSourceCustomizer");
                assertThat(context).hasBean("seataDataSourceProxyVerifier");

                MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
                SqlSessionFactoryBeanCustomizer customizer = context.getBean(SqlSessionFactoryBeanCustomizer.class);

                assertThatCode(() -> customizer.customize(factoryBean)).doesNotThrowAnyException();
                assertThat((DataSource) ReflectionTestUtils.getField(factoryBean, "dataSource")).isNull();
            });

            assertThat(capture.hasEvent(Level.WARN, "GXDynamicDataSource is absent")).isTrue();
        } finally {
            capture.detach();
        }
    }

    @Test
    void keepsUserDefinedSqlSessionFactory() {
        DataSource dataSource = Mockito.mock(DataSource.class);
        org.apache.ibatis.session.Configuration myBatisConfiguration = new org.apache.ibatis.session.Configuration();
        myBatisConfiguration.setEnvironment(new org.apache.ibatis.mapping.Environment(
                "test", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), dataSource));
        SqlSessionFactory customFactory = Mockito.mock(SqlSessionFactory.class);
        Mockito.when(customFactory.getConfiguration()).thenReturn(myBatisConfiguration);

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(GXSeataDynamicDataSourceConfig.class)
                .withBean(DataSource.class, () -> dataSource)
                .withBean(SqlSessionFactory.class, () -> customFactory)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SqlSessionFactory.class)).isSameAs(customFactory);
                });
    }
}
