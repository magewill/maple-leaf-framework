package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;

import java.lang.reflect.Method;
import java.util.Locale;

public final class GXSqlDbTypeResolver {
    public static final String DB_TYPE_PROPERTY_KEY = "spring.datasource.druid.db-type";

    private static final String DEFAULT_DB_TYPE = "mysql";
    private static final String DYNAMIC_DB_TYPE_REGISTRY_CLASS = "cn.maple.core.datasource.config.GXDynamicDbTypeRegistry";

    private GXSqlDbTypeResolver() {
    }

    public static String resolveDbType() {
        String dynamicDbType = resolveDbTypeFromDynamicRegistry();
        if (CharSequenceUtil.isNotBlank(dynamicDbType)) {
            return normalizeDbType(dynamicDbType);
        }

        String systemDbType = System.getProperty(DB_TYPE_PROPERTY_KEY);
        if (CharSequenceUtil.isNotBlank(systemDbType)) {
            return normalizeDbType(systemDbType);
        }

        String beanDbType = resolveDbTypeFromBean();
        if (CharSequenceUtil.isNotBlank(beanDbType)) {
            return normalizeDbType(beanDbType);
        }

        return normalizeDbType(GXCommonUtils.getEnvironmentValue(DB_TYPE_PROPERTY_KEY, String.class, DEFAULT_DB_TYPE));
    }

    public static String normalizeDbType(String dbType) {
        return CharSequenceUtil.isBlank(dbType) ? DEFAULT_DB_TYPE : dbType.toLowerCase(Locale.ROOT);
    }

    private static String resolveDbTypeFromDynamicRegistry() {
        try {
            Class<?> registryClass = Class.forName(DYNAMIC_DB_TYPE_REGISTRY_CLASS);
            Method method = registryClass.getMethod("resolveCurrent");
            Object dbType = method.invoke(null);
            return dbType == null ? null : dbType.toString();
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }

    private static String resolveDbTypeFromBean() {
        try {
            Object properties = GXSpringContextUtils.getBean("dataSourceProperties");
            if (properties == null) {
                return null;
            }
            Method method = properties.getClass().getMethod("getDbType");
            Object dbType = method.invoke(properties);
            return dbType == null ? null : dbType.toString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
