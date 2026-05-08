package cn.maple.seata.config;

import cn.maple.core.datasource.config.GXDynamicDataSource;
import com.baomidou.mybatisplus.autoconfigure.SqlSessionFactoryBeanCustomizer;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.Test;
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
        SqlSessionFactoryBeanCustomizer customizer =
                new GXSeataDynamicDataSourceConfig().seataDynamicDataSourceCustomizer(dynamicDataSource);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();

        customizer.customize(factoryBean);

        assertThat((DataSource) ReflectionTestUtils.getField(factoryBean, "dataSource"))
                .isSameAs(dynamicDataSource);
    }

    @Test
    void configDoesNotDeclareDataSourceProxyWrapperBean() {
        List<String> beanMethodNames = Stream.of(GXSeataDynamicDataSourceConfig.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getReturnType().getName() + "#" + method.getName())
                .toList();

        assertThat(beanMethodNames)
                .containsExactly("com.baomidou.mybatisplus.autoconfigure.SqlSessionFactoryBeanCustomizer#seataDynamicDataSourceCustomizer");
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
