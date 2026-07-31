package cn.maple.core.datasource.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.handler.GXDataFilterDataPermissionHandler;
import cn.maple.core.datasource.interceptor.GXDataFilterInterceptor;
import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.datasource.service.GXTenantIdService;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.exception.GXBusinessException;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@EnableTransactionManagement
@Configuration
public class GXMyBatisPlusConfig {
    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private GXDataSourceProperties dataSourceProperties;

    private static void customize(org.apache.ibatis.session.Configuration configuration) {
        configuration.setObjectWrapperFactory(new ObjectWrapperFactory() {
            @Override
            public boolean hasWrapperFor(Object object) {
                return object instanceof Map;
            }

            @Override
            @SuppressWarnings("unchecked")
            public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
                return new MybatisMapWrapper(metaObject, (Map<String, Object>) object);
            }
        });
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        if (GXApplicationContextSingleton.INSTANCE.getApplicationContext() == null) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
        }

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        addDataPermissionInterceptor(interceptor);
        addTenantInterceptor(interceptor);
        interceptor.addInnerInterceptor(new DynamicTableNameInnerInterceptor((sql, tableName) -> tableName));

        Boolean optimisticLocker = GXCommonUtils.getEnvironmentValue("maple.framework.enable.optimistic-locker", Boolean.class, Boolean.TRUE);
        if (Boolean.TRUE.equals(optimisticLocker)) {
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        }

        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(resolvePaginationDbType());
        paginationInnerInterceptor.setOptimizeJoin(false);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }

    @Bean
    @ConditionalOnProperty(name = "use-camel-case-mapping", havingValue = "true")
    public ConfigurationCustomizer configurationCustomizer() {
        return GXMyBatisPlusConfig::customize;
    }

    private void addDataPermissionInterceptor(MybatisPlusInterceptor interceptor) {
        Boolean enableDataPermission = GXCommonUtils.getEnvironmentValue("maple.framework.enable.data-permission", Boolean.class, Boolean.FALSE);
        if (Boolean.TRUE.equals(enableDataPermission)) {
            DataPermissionHandler dataPermissionHandler = GXSpringContextUtils.getBean(DataPermissionHandler.class);
            if (Objects.isNull(dataPermissionHandler)) {
                dataPermissionHandler = new GXDataFilterDataPermissionHandler();
            }
            interceptor.addInnerInterceptor(new DataPermissionInterceptor(dataPermissionHandler));
            return;
        }
        interceptor.addInnerInterceptor(new GXDataFilterInterceptor());
    }

    private void addTenantInterceptor(MybatisPlusInterceptor interceptor) {
        Boolean enableTenant = GXCommonUtils.getEnvironmentValue("maple.framework.enable.tenant", Boolean.class, Boolean.FALSE);
        if (Boolean.TRUE.equals(enableTenant)) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new GXTenantLineHandler()));
        }
    }

    private DbType resolvePaginationDbType() {
        String configuredDbType = GXDynamicDbTypeRegistry.resolveCurrent();
        if (CharSequenceUtil.isBlank(configuredDbType)) {
            configuredDbType = Optional.ofNullable(dataSourceProperties)
                    .map(GXDataSourceProperties::getDbType)
                    .filter(CharSequenceUtil::isNotBlank)
                    .orElse("mysql");
        }
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
                log.warn("Unknown dbType [{}], fallback to MYSQL for pagination interceptor", configuredDbType);
                yield DbType.MYSQL;
            }
        };
    }

    private static class GXTenantLineHandler implements TenantLineHandler {
        @Override
        public Expression getTenantId() {
            GXTenantIdService tenantIdService = GXSpringContextUtils.getBean(GXTenantIdService.class);
            if (Objects.isNull(tenantIdService)) {
                throw new GXBusinessException("GXTenantIdService bean is required when tenant plugin is enabled");
            }
            Expression tenantId = tenantIdService.getTenantId();
            if (Objects.isNull(tenantId)) {
                log.warn("GXTenantIdService returned null tenant id, fallback tenant id 0");
                return new LongValue(0);
            }
            return tenantId;
        }

        @Override
        public String getTenantIdColumn() {
            return "tenant_id";
        }

        @Override
        public boolean ignoreTable(String tableName) {
            if (CharSequenceUtil.isBlank(tableName)) {
                return true;
            }
            GXTenantIdService tenantIdService = GXSpringContextUtils.getBean(GXTenantIdService.class);
            if (Objects.isNull(tenantIdService)) {
                throw new GXBusinessException("GXTenantIdService bean is required when tenant plugin is enabled");
            }
            return tenantIdService.ignoreTable(tableName, getTenantIdColumn());
        }
    }
}
