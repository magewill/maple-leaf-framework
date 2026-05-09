package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class GXCacheKeysUtils {
    private static final String DEFAULT_PREFIX = "maple:default";

    private static final String CACHE_KEY_SEPARATOR = ":";

    private static final String SYS_CONFIG_PREFIX = "sys.config";

    private static final String SYS_CAPTCHA_PREFIX = "sys.captcha";

    private static final String SYS_NET_EASE_PREFIX = "sys.net-ease";

    private static final String SYS_ALI_YUN_PREFIX = "sys.ali-yun";

    private static final ConcurrentHashMap<String, String> PREFIX_CACHE = new ConcurrentHashMap<>();

    public String getSysConfigKey(String key) {
        return getCacheKey(SYS_CONFIG_PREFIX, key);
    }

    public String getCaptchaConfigKey(String key) {
        return getCacheKey(SYS_CAPTCHA_PREFIX, key);
    }

    public String getNetEaseSMSCodeConfigKey(String key) {
        return getCacheKey(SYS_NET_EASE_PREFIX, key);
    }

    public String getAliYunSMSCodeConfigKey(String key) {
        return getCacheKey(SYS_ALI_YUN_PREFIX, key);
    }

    public String getCacheKey(String configName, String key) {
        if (CharSequenceUtil.isEmpty(configName)) {
            log.warn("配置名称为空，使用默认前缀");
            return getDefaultCacheKey(key);
        }

        String cachedPrefix = PREFIX_CACHE.get(configName);
        if (cachedPrefix != null) {
            return joinCacheKey(cachedPrefix, key);
        }

        CacheKeysProperties cacheKeysProperties = GXSpringContextUtils.getBean(CacheKeysProperties.class);
        if (Objects.isNull(cacheKeysProperties)) {
            log.debug("未配置缓存键列表，使用默认前缀");
            return getDefaultCacheKey(key);
        }

        final List<Map<String, String>> list = cacheKeysProperties.getKeys();
        if (log.isDebugEnabled()) {
            log.debug("缓存键配置: {}", list);
        }

        for (Map<String, String> map : list) {
            final String prefix = map.get(configName);
            if (CharSequenceUtil.isNotEmpty(prefix)) {
                PREFIX_CACHE.put(configName, prefix);
                return joinCacheKey(prefix, key);
            }
        }

        return getDefaultCacheKey(key);
    }

    private String getDefaultCacheKey(String key) {
        if (CharSequenceUtil.isBlank(key)) {
            return DEFAULT_PREFIX + CACHE_KEY_SEPARATOR + "key";
        }
        return DEFAULT_PREFIX + CACHE_KEY_SEPARATOR + key;
    }

    private String joinCacheKey(String prefix, String key) {
        if (CharSequenceUtil.isBlank(key)) {
            return prefix + CACHE_KEY_SEPARATOR + "key";
        }
        return prefix + CACHE_KEY_SEPARATOR + key;
    }

    @Data
    @Component
    @Configuration
    @ConditionalOnExpression("${cache.enable:0}==1")
    @PropertySource(value = {"classpath:/${spring.profiles.active}/cache-key.yml"},
            factory = GXYamlPropertySourceFactory.class,
            encoding = "UTF-8",
            ignoreResourceNotFound = true)
    @ConfigurationProperties(prefix = "cache-key")
    static class CacheKeysProperties {
        private List<Map<String, String>> keys;
    }
}
