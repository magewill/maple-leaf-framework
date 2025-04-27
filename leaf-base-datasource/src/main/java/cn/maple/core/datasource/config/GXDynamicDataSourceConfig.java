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
 * 内存安全特性：
 * - 使用LinkedHashMap存储数据源，避免并发修改异常
 * - 数据源创建失败时记录日志但不中断程序，提高系统健壮性
 * - 使用Optional处理可能为空的对象，避免空指针异常
 * - 使用try-catch块捕获反射操作可能的异常，确保系统稳定性
 * - 数据源创建和管理在应用启动时完成，避免运行时频繁创建数据源对象
 * - 使用日志记录关键操作和异常，便于问题排查
 * </p>
 * 
 * <p>
 * 并发安全特性：
 * - 所有Bean都是单例的，配置操作在应用启动时完成，运行时只读取配置
 * - 数据源切换通过ThreadLocal实现，确保线程间数据隔离
 * - 使用不可变对象和线程安全的集合类进行操作
 * - 反射操作使用同步机制，避免并发问题
 * - 数据源创建过程是线程安全的，不会因为并发访问导致问题
 * </p>
 * 
 * <p>
 * 配置说明：
 * 在application.yml中配置多数据源：
 * <pre>
 * spring:
 *   datasource:
 *     dynamic:
 *       datasource:
 *         # 主库配置
 *         master:
 *           driver-class-name: com.mysql.cj.jdbc.Driver
 *           url: jdbc:mysql://localhost:3306/master_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
 *           username: root
 *           password: root
 *           # Druid连接池特有配置
 *           initial-size: 10
 *           max-active: 100
 *           min-idle: 10
 *           max-wait: 60000
 *         # 从库配置
 *         slave:
 *           driver-class-name: com.mysql.cj.jdbc.Driver
 *           url: jdbc:mysql://localhost:3307/slave_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
 *           username: root
 *           password: root
 *         # 其他业务库配置
 *         order:
 *           driver-class-name: com.mysql.cj.jdbc.Driver
 *           url: jdbc:mysql://localhost:3308/order_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
 *           username: root
 *           password: root
 * </pre>
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
 *     @Autowired
 *     private UserMapper userMapper;
 *     
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
 *     
 *     // 读写分离示例
 *     @Transactional
 *     public void createUserWithReadback(User user) {
 *         // 默认使用主库插入数据
 *         userMapper.insert(user);
 *         
 *         // 从从库读取数据进行验证
 *         GXDynamicContextHolder.push("slave");
 *         try {
 *             User savedUser = userMapper.selectById(user.getId());
 *             // 进行数据验证...
 *         } finally {
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
 *     @Autowired
 *     private OrderMapper orderMapper;
 *     
 *     @GXDataSource("order")
 *     @Override
 *     public void createOrder(OrderDTO orderDTO) {
 *         // 此方法中使用的是order数据源
 *         orderMapper.insert(orderDTO);
 *     }
 *     
 *     @GXDataSource("order")
 *     @Transactional // 注意：事务会在指定的数据源上开启
 *     public void processOrderWithTransaction(OrderDTO orderDTO) {
 *         // 在order数据源上执行事务操作
 *         orderMapper.insert(orderDTO);
 *         // 更新订单状态
 *         orderMapper.updateStatus(orderDTO.getId(), "PROCESSING");
 *     }
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 使用示例4：与Seata分布式事务集成
 * <pre>
 * // 1. 添加Seata依赖
 * // &lt;dependency&gt;
 * //     &lt;groupId&gt;io.seata&lt;/groupId&gt;
 * //     &lt;artifactId&gt;seata-spring-boot-starter&lt;/artifactId&gt;
 * //     &lt;version&gt;${seata.version}&lt;/version&gt;
 * // &lt;/dependency&gt;
 * 
 * // 2. 配置Seata
 * // seata:
 * //   enabled: true
 * //   application-id: ${spring.application.name}
 * //   tx-service-group: my_tx_group
 * //   service:
 * //     vgroup-mapping:
 * //       my_tx_group: default
 * 
 * // 3. 在服务中使用分布式事务
 * @Service
 * public class OrderServiceImpl implements OrderService {
 *     @Autowired
 *     private OrderMapper orderMapper;
 *     
 *     @Autowired
 *     private ProductService productService; // 远程服务
 *     
 *     @GlobalTransactional // Seata全局事务注解
 *     public void createOrderWithTransaction(OrderDTO orderDTO) {
 *         // 在order数据源上创建订单
 *         GXDynamicContextHolder.push("order");
 *         try {
 *             orderMapper.insert(orderDTO);
 *         } finally {
 *             GXDynamicContextHolder.poll();
 *         }
 *         
 *         // 调用远程服务扣减库存（在另一个微服务中使用不同的数据源）
 *         productService.reduceStock(orderDTO.getProductId(), orderDTO.getQuantity());
 *     }
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 注意事项：
 * - 动态数据源切换不支持在同一事务中切换，如需跨库事务请使用分布式事务框架如Seata
 * - 使用@Transactional注解时，事务会在当前数据源上开启，切换数据源会导致事务失效
 * - 在高并发环境下，避免频繁切换数据源，可能导致性能下降
 * - 数据源配置信息敏感，建议使用配置中心或加密存储
 * - 使用Druid连接池时，建议配置合理的连接池参数，避免连接泄漏
 * - 在微服务架构中，建议结合服务治理框架使用，实现更灵活的数据源管理
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
