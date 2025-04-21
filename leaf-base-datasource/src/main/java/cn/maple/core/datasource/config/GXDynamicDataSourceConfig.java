package cn.maple.core.datasource.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.datasource.annotation.GXDataSource;
import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.datasource.properties.GXDynamicDataSourceProperties;
import cn.maple.core.framework.config.aware.GXApplicationContextAware;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.alibaba.druid.pool.DruidDataSource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 动态数据源配置类
 * <p>
 * 该类负责配置和管理多数据源，支持在运行时动态切换数据源。
 * 通过@GXDataSource注解可以指定默认数据源，如果未指定则使用名为"framework"的数据源作为默认数据源。
 * 支持与Seata分布式事务框架的集成，可以将普通DataSource包装成Seata的DataSourceProxy。
 * </p>
 * 
 * <p>
 * 使用示例1：在启动类上指定默认数据源
 * <pre>
 * @SpringBootApplication
 * @GXDataSource("master")
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 使用示例2：在业务方法中动态切换数据源
 * <pre>
 * @Service
 * public class UserServiceImpl implements UserService {
 *     @Override
 *     public User getUser(Long id) {
 *         // 切换到从库查询
 *         GXDynamicContextHolder.push("slave");
 *         try {
 *             return userMapper.selectById(id);
 *         } finally {
 *             // 操作完成后清理数据源标识
 *             GXDynamicContextHolder.poll();
 *         }
 *     }
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 使用示例3：使用注解方式切换数据源
 * <pre>
 * @Service
 * public class OrderServiceImpl implements OrderService {
 *     @GXDataSource("order")
 *     @Override
 *     public void createOrder(OrderDTO orderDTO) {
 *         // 此方法中使用的是order数据源
 *         orderMapper.insert(orderDTO);
 *     }
 * }
 * </pre>
 * </p>
 */
@Configuration
@Slf4j
public class GXDynamicDataSourceConfig extends GXApplicationContextAware {
    @Resource
    private GXDynamicDataSourceProperties dynamicDataSourceProperties;

    /**
     * 获取默认数据源属性
     *
     * @return 默认数据源属性
     */
    @Bean
    public GXDataSourceProperties dataSourceProperties() {
        GXDataSourceProperties dataSourceProperties = new GXDataSourceProperties();
        if (!CharSequenceUtil.isEmpty(dataSourceProperties.getUrl())) {
            return dataSourceProperties;
        }
        String defaultDataSourceName = getDefaultDataSourceName();
        return dynamicDataSourceProperties.getDatasource().get(defaultDataSourceName);
    }

    /**
     * 配置动态数据源
     * <p>
     * 根据配置创建多个数据源，并设置默认数据源
     * </p>
     *
     * @return 动态数据源
     */
    @Bean
    public GXDynamicDataSource dynamicDataSource() {
        GXDynamicDataSource dynamicDataSource = new GXDynamicDataSource();
        Map<Object, Object> dynamicDataSources = getDynamicDataSources();
        dynamicDataSource.setTargetDataSources(dynamicDataSources);
        
        String defaultDataSourceName = getDefaultDataSourceName();
        Optional<Object> firstDataSource = dynamicDataSources.keySet().stream()
                .filter(name -> name.equals(defaultDataSourceName))
                .findFirst();
        
        if (firstDataSource.isEmpty()) {
            throw new GXBusinessException("请配置正确的数据源");
        }
        
        // 设置默认数据源
        DataSource defaultDataSource = (DataSource) dynamicDataSources.get(firstDataSource.get());
        dynamicDataSource.setDefaultTargetDataSource(defaultDataSource);
        return dynamicDataSource;
    }

    /**
     * 获取默认数据源名称
     * <p>
     * 从SpringBootApplication注解的类上查找GXDataSource注解，获取指定的数据源名称
     * 如果未找到，则使用默认名称"framework"
     * </p>
     *
     * @return 默认数据源名称
     */
    private String getDefaultDataSourceName() {
        String[] defaultDataSourceName = new String[]{"framework"};
        GXSpringContextUtils.getApplicationContext().getBeansWithAnnotation(SpringBootApplication.class).forEach((name, obj) -> {
            GXDataSource annotation = obj.getClass().getAnnotation(GXDataSource.class);
            if (Objects.nonNull(annotation)) {
                defaultDataSourceName[0] = annotation.value();
            }
        });
        return defaultDataSourceName[0];
    }

    /**
     * 将普通的DataSource对象包装成Seata的DataSourceProxy
     * <p>
     * 如果项目中集成了Seata，则将数据源包装成Seata的代理数据源，以支持分布式事务
     * 如果未找到Seata相关类，则返回原始数据源
     * </p>
     *
     * @param dataSource 原始数据源
     * @return 包装后的数据源，可能是原始数据源或Seata代理数据源
     */
    private DataSource wrapSeataDataSource(DataSource dataSource) {
        try {
            Class<?> forName = Class.forName("io.seata.rm.datasource.DataSourceProxy");
            Constructor<?> constructor = ReflectUtil.getConstructor(forName, DataSource.class);
            if (constructor == null) {
                log.warn("找到Seata的DataSourceProxy类，但无法获取构造函数，将使用原始的DataSource对象");
                return dataSource;
            }
            Object o = ReflectUtil.newInstance(forName, dataSource);
            if (o == null) {
                log.warn("无法实例化Seata的DataSourceProxy对象，将使用原始的DataSource对象");
                return dataSource;
            }
            log.info("成功将数据源包装为Seata的DataSourceProxy");
            return (DataSource) o;
        } catch (ClassNotFoundException e) {
            log.debug("未找到Seata的DataSourceProxy类，将使用原始的DataSource对象");
        } catch (Exception e) {
            log.error("包装Seata数据源时发生异常: {}", e.getMessage(), e);
        }
        return dataSource;
    }

    /**
     * 获取所有配置的动态数据源
     * <p>
     * 根据配置创建多个数据源，并可选地包装成Seata代理数据源
     * </p>
     *
     * @return 数据源映射，key为数据源名称，value为数据源对象
     */
    protected Map<Object, Object> getDynamicDataSources() {
        Map<String, GXDataSourceProperties> dataSourcePropertiesMap = dynamicDataSourceProperties.getDatasource();
        Map<Object, Object> targetDataSources = new LinkedHashMap<>(dataSourcePropertiesMap.size());
        // TODO 此处可以通过在其他地方获取连接信息来新建连接池, 比如从另外的数据库读取信息
        dataSourcePropertiesMap.forEach((k, v) -> {
            try {
                DruidDataSource druidDataSource = GXDynamicDataSourceFactory.buildDruidDataSource(v);
                DataSource dataSource = wrapSeataDataSource(druidDataSource);
                targetDataSources.put(k, dataSource);
                log.debug("成功创建数据源: {}", k);
            } catch (Exception e) {
                log.error("创建数据源失败: {}, 错误: {}", k, e.getMessage(), e);
            }
        });
        return targetDataSources;
    }
}
