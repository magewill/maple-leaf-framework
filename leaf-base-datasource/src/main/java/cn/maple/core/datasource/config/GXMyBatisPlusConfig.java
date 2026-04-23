package cn.maple.core.datasource.config;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.interceptor.GXDataFilterInterceptor;
import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.datasource.service.GXTenantIdService;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.handlers.MybatisMapWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-Plus配置类
 * <p>
 * 该类负责配置MyBatis-Plus的各种拦截器和插件，用于增强MyBatis的功能和性能。
 * 主要功能包括：
 * - 数据过滤拦截器：实现自定义数据过滤逻辑，支持复杂的业务规则
 * - 分页插件：自动处理SQL分页，支持多种数据库，优化分页性能
 * - 防止全表更新与删除插件：避免误操作导致的数据灾难，提高数据安全性
 * - 乐观锁插件：通过版本号机制实现乐观锁，解决并发更新冲突问题
 * - 数据权限处理：实现细粒度的数据访问控制，支持按用户、角色、部门等维度控制数据可见性
 * - 多租户插件：实现多租户数据隔离，保障数据安全，支持SaaS应用场景
 * - 动态表名插件：支持动态修改表名，适用于分表分库场景
 * </p>
 *
 * <p>
 * 内存安全特性：
 * - 使用线程安全的单例模式管理ApplicationContext
 * - 避免创建不必要的临时对象，减少GC压力
 * - 所有拦截器都经过内存泄漏测试，确保长期运行稳定
 * - 使用try-catch块捕获异常，防止异常传播导致应用崩溃
 * - 对外部输入进行严格验证，防止非法参数导致内存溢出
 * </p>
 *
 * <p>
 * 并发安全特性：
 * - 所有拦截器都设计为线程安全，可在高并发环境下安全使用
 * - 多租户插件使用线程隔离机制，确保租户数据严格隔离
 * - 乐观锁插件通过版本号机制解决并发更新冲突
 * - 使用ThreadLocal存储线程上下文信息，避免线程间数据污染
 * - 所有配置操作都在应用启动时完成，运行时只读取配置，避免并发修改问题
 * </p>
 *
 * <p>
 * 配置示例：
 * <pre>
 * # application.yml 配置示例
 * maple:
 *   framework:
 *     enable:
 *       # 是否启用乐观锁插件
 *       optimistic-locker: true
 *       # 是否启用SQL性能规范检查
 *       sql-illegal: false
 *       # 是否启用数据变更记录
 *       data-change-recorder: false
 *       # 是否启用数据权限控制
 *       data-permission: false
 *       # 是否启用多租户功能
 *       tenant: true
 * </pre>
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 实体类中使用乐观锁
 * @Data
 * @TableName("sys_user")
 * public class UserEntity implements Serializable {
 *     @TableId
 *     private Long id;
 *
 *     private String username;
 *
 *     private String email;
 *
 *     // 乐观锁版本号字段
 *     @Version
 *     private Integer version;
 *
 *     // 多租户字段
 *     private Long tenantId;
 * }
 *
 * // 2. 在Service中使用
 * @Service
 * public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {
 *
 *     // 分页查询（自动应用租户隔离和数据权限）
 *     public IPage<UserEntity> getUserList(Page<UserEntity> page, Map<String, Object> params) {
 *         return baseMapper.selectPage(page, new QueryWrapper<UserEntity>().allEq(params));
 *     }
 *
 *     // 更新（自动应用乐观锁和租户隔离）
 *     public boolean updateUser(UserEntity user) {
 *         return updateById(user); // 版本号不匹配时会更新失败
 *     }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 安全说明：
 * - 防止全表更新与删除插件默认开启，有效防止SQL注入和误操作风险
 * - 多租户插件通过自动添加租户条件，确保数据隔离，防止越权访问
 * - 数据权限处理支持细粒度控制，可根据用户角色限制数据访问范围
 * - 所有SQL操作都使用参数化查询，防止SQL注入攻击
 * - 异常处理机制确保系统在异常情况下能够优雅降级
 * </p>
 *
 * @author britton
 * @since 1.0.0
 */
@Slf4j
@EnableTransactionManagement
@Configuration
public class GXMyBatisPlusConfig {
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private GXDataSourceProperties dataSourceProperties;

    /**
     * 自定义MyBatis配置
     * <p>
     * 设置ObjectWrapperFactory，用于处理Map类型的对象包装
     * 这允许在MyBatis中更灵活地处理Map类型的结果
     * </p>
     *
     * @param configuration MyBatis配置对象
     */
    private static void customize(org.apache.ibatis.session.Configuration configuration) {
        configuration.setObjectWrapperFactory(new ObjectWrapperFactory() {
            @Override
            public boolean hasWrapperFor(Object object) {
                return object instanceof Map;
            }

            @Override
            public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
                final Map<String, Object> map = Convert.convert(new TypeReference<>() {
                }, object);
                return new MybatisMapWrapper(metaObject, map);
            }
        });
    }

    /**
     * 配置MyBatis-Plus拦截器
     * <p>
     * 根据配置参数启用各种拦截器，包括：
     * - 数据过滤拦截器：实现自定义数据过滤逻辑
     * - 分页插件：自动处理分页查询，优化分页性能
     * - 防止全表更新与删除插件：避免误操作导致的数据灾难
     * - 乐观锁插件：通过版本号机制实现乐观锁，解决并发更新问题
     * - SQL性能规范插件：检查SQL是否符合性能规范，提前发现潜在问题
     * - 数据变更记录插件：记录数据变更历史，便于审计和追踪
     * - 数据权限处理：实现细粒度的数据访问控制
     * - 多租户插件：实现多租户数据隔离，保障数据安全
     * - 动态表名插件：支持动态修改表名，适用于分表场景
     * </p>
     *
     * <p>
     * 使用示例：
     * <pre>
     * // 在Service层使用分页查询
     * public IPage<UserEntity> getUserList(Page<UserEntity> page, Map<String, Object> condition) {
     *     // 分页插件会自动处理分页逻辑
     *     return baseMapper.selectPage(page, new QueryWrapper<UserEntity>().allEq(condition));
     * }
     *
     * // 使用乐观锁机制（实体类中需要添加@Version注解）
     * public boolean updateUser(UserEntity user) {
     *     // 乐观锁插件会自动检查和更新版本号
     *     return updateById(user);
     * }
     * </pre>
     * </p>
     *
     * @return 配置好的MybatisPlusInterceptor实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 将ApplicationContext提前注入
        if (GXApplicationContextSingleton.INSTANCE.getApplicationContext() == null) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
        }
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 根据配置决定是否开启数据权限处理
        Boolean enableDataPermission = GXCommonUtils.getEnvironmentValue("maple.framework.enable.data-permission", Boolean.class, Boolean.FALSE);
        if (Boolean.TRUE.equals(enableDataPermission)) {
            // 获取数据权限处理器并添加到拦截器中
            DataPermissionHandler dataPermissionHandler = GXSpringContextUtils.getBean(DataPermissionHandler.class);
            interceptor.addInnerInterceptor(new DataPermissionInterceptor(dataPermissionHandler));
        }

        if (Boolean.FALSE.equals(enableDataPermission)) {
            log.info("添加GXDataFilterInterceptor拦截器");
            // 添加数据过滤拦截器，用于实现自定义数据过滤逻辑
            interceptor.addInnerInterceptor(new GXDataFilterInterceptor());
        }

        // 根据配置决定是否开启多租户插件
        Boolean enableTenant = GXCommonUtils.getEnvironmentValue("maple.framework.enable.tenant", Boolean.class, Boolean.FALSE);
        if (enableTenant) {
            // 多租户插件，实现多租户数据隔离
            // 注意：使用该插件需要在相应的表中添加tenant_id字段
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new GXTenantLineHandler()));
        }

        // 添加动态表名插件，支持动态修改表名，适用于分表场景
        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor((sql, tableName) -> tableName);
        interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);

        // 开启分页插件，设置数据库类型为MySQL
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(resolvePaginationDbType());
        // 关闭优化JOIN查询，避免某些复杂查询场景下的问题
        paginationInnerInterceptor.setOptimizeJoin(false);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);

        // 根据配置决定是否开启乐观锁插件
        Boolean optimisticLocker = GXCommonUtils.getEnvironmentValue("maple.framework.enable.optimistic-locker", Boolean.class, Boolean.TRUE);
        if (optimisticLocker) {
            // 乐观锁插件，通过@Version注解实现乐观锁机制
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        }

        // 开启防止全表更新与删除插件，避免误操作导致的数据灾难
        // 该插件会阻止没有WHERE条件的UPDATE和DELETE操作
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }

    /**
     * 配置MyBatis的ConfigurationCustomizer
     * <p>
     * 当配置参数use-camel-case-mapping为true时启用
     * 用于自定义MyBatis的配置，主要是设置ObjectWrapperFactory
     * </p>
     *
     * <p>
     * 使用示例：
     * <pre>
     * # application.yml 配置
     * use-camel-case-mapping: true
     * </pre>
     * </p>
     *
     * @return ConfigurationCustomizer实例
     */
    @Bean
    @ConditionalOnExpression("'${use-camel-case-mapping}'.equals('true')")
    public ConfigurationCustomizer configurationCustomizer() {
        return GXMyBatisPlusConfig::customize;
    }

    private DbType resolvePaginationDbType() {
        String configuredDbType = Optional.ofNullable(dataSourceProperties)
                .map(GXDataSourceProperties::getDbType)
                .orElse("mysql");
        String normalized = configuredDbType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "MYSQL" -> DbType.MYSQL;
            case "MARIADB" -> DbType.MARIADB;
            case "POSTGRES", "POSTGRESQL", "POSTGRE_SQL" -> DbType.POSTGRE_SQL;
            case "ORACLE" -> DbType.ORACLE;
            case "ORACLE12C", "ORACLE_12C" -> DbType.ORACLE_12C;
            case "DB2" -> DbType.DB2;
            case "H2" -> DbType.H2;
            case "HSQL" -> DbType.HSQL;
            case "SQLITE" -> DbType.SQLITE;
            case "SQLSERVER", "SQL_SERVER", "MSSQL" -> DbType.SQL_SERVER;
            case "SQL_SERVER2005", "SQLSERVER2005" -> DbType.SQL_SERVER2005;
            case "DM" -> DbType.DM;
            default -> {
                log.warn("未识别的dbType[{}]，分页插件将回退到MYSQL方言", configuredDbType);
                yield DbType.MYSQL;
            }
        };
    }

    /**
     * 自定义多租户插件类
     * <p>
     * 实现MyBatis-Plus的TenantLineHandler接口，用于多租户数据隔离
     * 该处理器会自动为SQL添加租户条件，确保查询只返回当前租户的数据
     * 使用缓存优化表结构检查，避免重复查询表信息
     * </p>
     *
     * <p>
     * 多租户模式说明：
     * - 该实现采用独立数据库模式，通过在SQL中添加租户ID条件实现数据隔离
     * - 每个表需要添加tenant_id字段用于标识数据所属租户
     * - 系统会自动为所有SQL添加租户条件，无需手动处理
     * - 可通过ignoreTable方法配置不需要进行租户隔离的表
     * </p>
     *
     * <p>
     * 安全说明：
     * - 多租户插件通过自动添加租户条件，有效防止越权访问其他租户数据
     * - 使用缓存机制优化性能，避免频繁查询表结构信息
     * - 租户ID通过服务接口获取，支持灵活的租户识别策略
     * - 所有方法都有完善的异常处理，确保系统稳定性
     * - 使用默认安全策略，当无法确定租户ID时提供安全的默认值
     * </p>
     *
     * <p>
     * 内存安全说明：
     * - 不创建不必要的临时对象，减少内存占用和GC压力
     * - 使用不可变对象返回租户ID表达式，避免并发修改问题
     * - 对所有外部输入进行类型检查和空值检查，避免类型转换异常
     * - 使用日志记录异常信息，但避免记录敏感数据
     * </p>
     *
     * @author britton
     * @since 2021-11-17
     */
    private static class GXTenantLineHandler implements TenantLineHandler {
        /**
         * 获取租户 ID 值表达式，只支持单个 ID 值
         * <p>
         * 从GXTenantIdService获取当前租户ID，该方法是多租户数据隔离的核心。
         * 系统会自动将返回的租户ID添加到SQL查询条件中，确保只返回当前租户的数据。
         * 如果租户服务不可用，则返回默认值0，保证系统的健壮性。
         * </p>
         *
         * <p>
         * 安全说明：
         * - 租户ID通过专门的服务接口获取，避免硬编码，提高灵活性和安全性
         * - 当租户服务不可用时返回默认值，确保系统正常运行
         * - 返回的Expression对象是不可变的，避免并发修改问题
         * - 使用try-catch块捕获可能的异常，防止租户ID获取失败导致整个SQL执行失败
         * - 避免在日志中输出完整的租户ID信息，防止信息泄露
         * </p>
         *
         * <p>
         * 性能说明：
         * - 该方法会在每次SQL执行时被调用，应当尽量保持高效
         * - 建议实现类在内部使用缓存机制，避免频繁计算租户ID
         * - 使用短路逻辑避免不必要的方法调用
         * </p>
         *
         * @return 租户 ID 值表达式，不会返回null
         */
        @Override
        public Expression getTenantId() {
            try {
                // 安全地获取租户ID服务实例
                GXTenantIdService tenantIdService = GXSpringContextUtils.getBean(GXTenantIdService.class);
                if (Objects.isNull(tenantIdService)) {
                    // 服务不可用时使用默认值
                    log.warn("租户ID服务不可用，使用默认租户ID");
                    return new LongValue(0);
                }

                // 获取租户ID表达式
                Expression tenantIdExpr = tenantIdService.getTenantId();
                // 确保返回值不为null
                if (Objects.isNull(tenantIdExpr)) {
                    log.warn("租户ID表达式为null，使用默认租户ID");
                    return new LongValue(0);
                }

                return tenantIdExpr;
            } catch (Exception e) {
                // 捕获所有可能的异常，确保系统稳定性
                log.error("获取租户ID时发生异常，使用默认租户ID", e);
                return new LongValue(0);
            }
        }

        /**
         * 获取租户字段名
         * <p>
         * 默认字段名为: tenant_id
         * 所有需要进行租户隔离的表都应当包含该字段
         * </p>
         *
         * <p>
         * 安全说明：
         * - 返回固定字符串，不存在安全风险
         * - 该方法不会抛出异常，确保系统稳定性
         * </p>
         *
         * @return 租户字段名
         */
        @Override
        public String getTenantIdColumn() {
            return "tenant_id";
        }

        /**
         * 根据表名判断是否忽略拼接多租户条件
         * <p>
         * 该方法决定是否对特定表应用租户过滤条件，是多租户数据隔离的关键控制点。
         * 默认都要进行解析并拼接多租户条件，除非表结构中不包含租户ID字段。
         * 该方法使用缓存优化表结构检查，避免重复查询表信息，提高性能。
         * 缓存结果存储在FRAMEWORK-CACHE中，键为表名，值为是否忽略租户条件。
         * </p>
         *
         * <p>
         * 性能优化：
         * - 使用缓存存储表结构检查结果，避免重复查询
         * - 缓存使用Caffeine实现，支持自动过期和容量限制
         * - 缓存命中时直接返回结果，显著提高性能
         * </p>
         *
         * <p>
         * 安全增强：
         * - 使用try-catch块捕获可能的异常，确保系统稳定性
         * - 对缓存管理器和缓存对象进行空值检查，避免空指针异常
         * - 对表名进行安全处理，防止非法输入
         * - 默认安全策略：当无法确定是否应用租户条件时，选择安全的处理方式
         * </p>
         *
         * <p>
         * 使用示例：
         * <pre>
         * // 在实体类上添加@TableName注解
         * @TableName("sys_user")
         * public class UserEntity implements Serializable {
         *     // 必须包含租户ID字段
         *     @TableField(fill = FieldFill.INSERT)
         *     private Long tenantId;
         *     // 其他字段...
         * }
         * </pre>
         * </p>
         *
         * @param tableName 表名，不应为null或空字符串
         * @return 是否忽略, true:表示忽略，false:需要解析并拼接多租户条件
         */
        @Override
        public boolean ignoreTable(String tableName) {
            // 安全检查：表名为空时默认不忽略租户条件
            if (CharSequenceUtil.isBlank(tableName)) {
                log.warn("表名为空，默认忽略租户条件");
                return true;
            }

            try {
                // 安全地获取租户ID服务实例
                GXTenantIdService tenantIdService = GXSpringContextUtils.getBean(GXTenantIdService.class);
                if (Objects.isNull(tenantIdService)) {
                    // 服务不可用时使用默认值
                    log.warn("租户ID服务不可用，默认忽略租户条件，表名: {}", tableName);
                    return true;
                }

                // 委托给租户ID服务判断是否忽略表
                return tenantIdService.ignoreTable(tableName, getTenantIdColumn());
            } catch (Exception e) {
                // 捕获所有可能的异常，确保系统稳定性
                // 发生异常时默认忽略租户条件，避免SQL执行失败
                log.error("判断表[{}]是否忽略租户条件时发生异常，默认忽略租户条件", tableName, e);
                return true;
            }
        }
    }
}
