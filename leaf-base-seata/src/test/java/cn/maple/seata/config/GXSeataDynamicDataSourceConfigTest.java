package cn.maple.seata.config;

import cn.maple.core.datasource.config.GXDynamicDataSource;
import com.baomidou.mybatisplus.autoconfigure.SqlSessionFactoryBeanCustomizer;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GXSeataDynamicDataSourceConfigTest {
    @Test
    void customizerInjectsDynamicDataSourceIntoMybatisPlusFactoryBean() {
        GXDynamicDataSource dynamicDataSource = new GXDynamicDataSource();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("dynamicDataSource", dynamicDataSource);

        SqlSessionFactoryBeanCustomizer customizer = new GXSeataDynamicDataSourceConfig()
                .seataDynamicDataSourceCustomizer(beanFactory.getBeanProvider(GXDynamicDataSource.class));
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();

        customizer.customize(factoryBean);

        assertThat((DataSource) ReflectionTestUtils.getField(factoryBean, "dataSource"))
                .isSameAs(dynamicDataSource);
    }

    @Test
    void configDeclaresOnlyCustomizerAndVerifierBeans() {
        List<String> beanMethodTypes = Stream.of(GXSeataDynamicDataSourceConfig.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .map(method -> method.getReturnType().getName())
                .toList();

        assertThat(beanMethodTypes).containsExactlyInAnyOrder(
                SqlSessionFactoryBeanCustomizer.class.getName(),
                SmartInitializingSingleton.class.getName());
        assertThat(beanMethodTypes).noneMatch(type -> type.contains("DataSourceProxy"));
        assertThat(beanMethodTypes).noneMatch(type -> type.equals(DataSource.class.getName()));
    }

    @Test
    void logMessagesStayAsciiOnly() throws Exception {
        Path source = Path.of("src/main/java/cn/maple/seata/config/GXSeataDynamicDataSourceConfig.java");
        String content = Files.readString(source);

        List<String> nonAsciiLogLines = content.lines()
                .filter(line -> line.contains("log."))
                .filter(line -> line.chars().anyMatch(character -> character > 0x7F))
                .toList();

        assertThat(nonAsciiLogLines).isEmpty();
    }
}
