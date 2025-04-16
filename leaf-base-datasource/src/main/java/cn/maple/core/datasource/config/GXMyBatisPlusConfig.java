package cn.maple.core.datasource.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.interceptor.GXDataFilterInterceptor;
import cn.maple.core.datasource.service.GXTenantIdService;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Map;
import java.util.Objects;

/**
 * MyBatis-Plus配置类
 * <p>
 * 该类负责配置MyBatis-Plus的各种拦截器和插件，包括：
 * - 数据过滤拦截器
 * - 分页插件
 * - 防止全表更新与删除插件
 * - 乐观锁插件
 * - SQL性能规范插件
 * - 数据变更记录插件
 * - 数据权限处理
 * - 多租户插件
 * - 动态表名插件
 * </p>
 * <p>
 * 各插件的启用由配置参数控制，可通过application配置文件中的maple.framework.enable.*属性进行设置
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
     * - 数据过滤拦截器
     * - 分页插件
     * - 防止全表更新与删除插件
     * - 乐观锁插件
     * - SQL性能规范插件
     * - 数据变更记录插件
     * - 数据权限处理
     * - 多租户插件
     * - 动态表名插件
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
        interceptor.addInnerInterceptor(new GXDataFilterInterceptor());
        // 开启分页插件
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setOptimizeJoin(false);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        // 开启防止全表更新与删除插件
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        // 开启乐观锁插件
        Boolean optimisticLocker = GXCommonUtils.getEnvironmentValue("maple.framework.enable.optimistic-locker", Boolean.class, Boolean.TRUE);
        if (optimisticLocker) {
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        }
        // 正式环境暂时不开启,开启sql性能规范插件,其他环境都开启sql性能规范插件
        Boolean enableSqlIllegal = GXCommonUtils.getEnvironmentValue("maple.framework.enable.sql-illegal", Boolean.class, Boolean.FALSE);
        if (Boolean.TRUE.equals(enableSqlIllegal)) {
            interceptor.addInnerInterceptor(new IllegalSQLInnerInterceptor());
        }
        // 数据变更记录插件
        Boolean enableDataChangeRecorder = GXCommonUtils.getEnvironmentValue("maple.framework.enable.data-change-recorder", Boolean.class, Boolean.FALSE);
        if (Boolean.TRUE.equals(enableDataChangeRecorder)) {
            interceptor.addInnerInterceptor(new DataChangeRecorderInnerInterceptor());
        }
        // 开启数据权限处理
        Boolean enableDataPermission = GXCommonUtils.getEnvironmentValue("maple.framework.enable.data-permission", Boolean.class, Boolean.FALSE);
        if (Boolean.TRUE.equals(enableDataPermission)) {
            DataPermissionHandler dataPermissionHandler = GXSpringContextUtils.getBean(DataPermissionHandler.class);
            interceptor.addInnerInterceptor(new DataPermissionInterceptor(dataPermissionHandler));
        }
        // 多租户插件(请在相应的表中新增tenant_id字段)
        Boolean enableTenant = GXCommonUtils.getEnvironmentValue("maple.framework.enable.tenant", Boolean.class, Boolean.FALSE);
        if (enableTenant) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new GXTenantLineHandler()));
        }
        // 动态表名插件
        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();
        dynamicTableNameInnerInterceptor.setTableNameHandler((sql, tableName) -> tableName);
        interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
        return interceptor;
    }

    /**
     * 配置MyBatis的ConfigurationCustomizer
     * <p>
     * 当配置参数use-camel-case-mapping为true时启用
     * 用于自定义MyBatis的配置，主要是设置ObjectWrapperFactory
     * </p>
     *
     * @return ConfigurationCustomizer实例
     */
    @Bean
    @ConditionalOnExpression("'${use-camel-case-mapping}'.equals('true')")
    public ConfigurationCustomizer configurationCustomizer() {
        return GXMyBatisPlusConfig::customize;
    }

    /**
     * 自定义多租户插件类
     * <p>
     * 实现MyBatis-Plus的TenantLineHandler接口，用于多租户数据隔离
     * 该处理器会自动为SQL添加租户条件，确保查询只返回当前租户的数据
     * 使用缓存优化表结构检查，避免重复查询表信息
     * </p>
     *
     * @author britton
     * @since 2021-11-17
     */
    private static class GXTenantLineHandler implements TenantLineHandler {
        /**
         * 数据缓存管理器
         * <p>
         * 用于缓存表结构信息，减少重复查询，提高性能
         * 使用Spring的CaffeineCacheManager，支持自动过期和大小限制
         * </p>
         */
        private static final CaffeineCacheManager caffeineCacheManager;

        static {
            caffeineCacheManager = GXSpringContextUtils.getBean(CaffeineCacheManager.class);
        }

        /**
         * 获取租户 ID 值表达式，只支持单个 ID 值
         * <p>
         * 从GXTenantIdService获取当前租户ID
         * 如果租户服务不可用，则返回默认值0
         * </p>
         *
         * @return 租户 ID 值表达式
         */
        @Override
        public Expression getTenantId() {
            GXTenantIdService tenantIdService = GXSpringContextUtils.getBean(GXTenantIdService.class);
            if (Objects.isNull(tenantIdService)) {
                return new LongValue(0);
            }
            return tenantIdService.getTenantId();
        }

        /**
         * 获取租户字段名
         * <p>
         * 默认字段名叫: tenant_id
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
         * 默认都要进行解析并拼接多租户条件
         * 该方法使用缓存优化表结构检查，避免重复查询表信息
         * 缓存结果存储在FRAMEWORK-CACHE中，键为表名，值为是否忽略租户条件
         * </p>
         *
         * @param tableName 表名
         * @return 是否忽略, true:表示忽略，false:需要解析并拼接多租户条件
         */
        @Override
        public boolean ignoreTable(String tableName) {
            assert caffeineCacheManager != null;
            Cache cache = caffeineCacheManager.getCache("FRAMEWORK-CACHE");
            assert cache != null;
            
            // 先从缓存中获取结果，避免重复查询
            Boolean hasTenantIdField = cache.get(tableName, Boolean.class);
            if (Objects.nonNull(hasTenantIdField)) {
                return Boolean.TRUE.equals(hasTenantIdField);
            }
            
            // 缓存未命中，查询表结构
            TableInfo tableInfo = TableInfoHelper.getTableInfo(tableName);
            if (Objects.isNull(tableInfo)) {
                // 表不存在，缓存结果并返回true
                cache.put(tableName, Boolean.TRUE);
                return true;
            }
            
            // 检查表是否包含租户ID字段
            boolean contains = !CollUtil.contains(tableInfo.getFieldList(), 
                field -> CharSequenceUtil.equalsIgnoreCase(field.getColumn(), getTenantIdColumn()));
            
            // 缓存结果
            cache.put(tableName, contains);
            return contains;
        }
    }
}
