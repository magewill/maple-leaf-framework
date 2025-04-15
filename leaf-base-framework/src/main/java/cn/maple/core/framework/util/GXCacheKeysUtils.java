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

/**
 * 缓存键工具类
 * 用于统一管理系统中使用的缓存键，避免缓存键冲突和重复定义
 * 通过配置文件管理缓存键前缀，实现缓存键的集中管理和动态配置
 *
 * @author zj chen <britton@126.com>
 */
@Component
@Slf4j
public class GXCacheKeysUtils {
    /**
     * 默认缓存前缀
     */
    private static final String DEFAULT_PREFIX = "geoxus:default";
    
    /**
     * 缓存键分隔符
     */
    private static final String CACHE_KEY_SEPARATOR = ":";
    
    /**
     * 系统配置前缀
     */
    private static final String SYS_CONFIG_PREFIX = "sys.config";
    
    /**
     * 图形验证码前缀
     */
    private static final String SYS_CAPTCHA_PREFIX = "sys.captcha";
    
    /**
     * 网易云短信前缀
     */
    private static final String SYS_NET_EASE_PREFIX = "sys.net-ease";
    
    /**
     * 阿里云短信前缀
     */
    private static final String SYS_ALI_YUN_PREFIX = "sys.ali-yun";
    
    /**
     * 缓存前缀映射缓存，用于提高性能
     */
    private static final ConcurrentHashMap<String, String> PREFIX_CACHE = new ConcurrentHashMap<>();
    /**
     * 获取系统配置的缓存KEY
     * 使用sys.config前缀构建缓存键
     *
     * @param key 缓存KEY的后缀部分
     * @return 完整的缓存KEY字符串
     */
    public String getSysConfigKey(String key) {
        return getCacheKey(SYS_CONFIG_PREFIX, key);
    }

    /**
     * 获取图形验证码的缓存KEY
     * 使用sys.captcha前缀构建缓存键
     *
     * @param key 缓存KEY的后缀部分
     * @return 完整的缓存KEY字符串
     */
    public String getCaptchaConfigKey(String key) {
        return getCacheKey(SYS_CAPTCHA_PREFIX, key);
    }

    /**
     * 获取网易云短信验证码的缓存KEY
     * 使用sys.net-ease前缀构建缓存键
     *
     * @param key 缓存KEY的后缀部分
     * @return 完整的缓存KEY字符串
     */
    public String getNetEaseSMSCodeConfigKey(String key) {
        return getCacheKey(SYS_NET_EASE_PREFIX, key);
    }

    /**
     * 获取阿里云短信验证码的缓存KEY
     * 使用sys.ali-yun前缀构建缓存键
     *
     * @param key 缓存KEY的后缀部分
     * @return 完整的缓存KEY字符串
     */
    public String getAliYunSMSCodeConfigKey(String key) {
        return getCacheKey(SYS_ALI_YUN_PREFIX, key);
    }

    /**
     * 根据配置文件的name和key获取缓存的key
     * 该方法从配置文件中查找指定配置名对应的前缀，并与提供的key组合形成完整的缓存键
     * 如果未找到配置或配置为空，则使用默认前缀
     *
     * @param configName 配置名称，用于在配置文件中查找对应的前缀
     * @param key        缓存KEY的后缀部分
     * @return 完整的缓存KEY字符串
     */
    public String getCacheKey(String configName, String key) {
        // 参数校验
        if (CharSequenceUtil.isEmpty(configName)) {
            log.warn("配置名称为空，使用默认前缀");
            return getDefaultCacheKey(key);
        }
        
        // 先从缓存中查找前缀
        String cachedPrefix = PREFIX_CACHE.get(configName);
        if (cachedPrefix != null) {
            return joinCacheKey(cachedPrefix, key);
        }
        
        // 从Spring容器中获取缓存键配置属性
        CacheKeysProperties cacheKeysProperties = GXSpringContextUtils.getBean(CacheKeysProperties.class);
        if (Objects.isNull(cacheKeysProperties)) {
            log.debug("未配置缓存键列表，使用默认前缀");
            return getDefaultCacheKey(key);
        }
        
        // 获取配置的键列表
        final List<Map<String, String>> list = cacheKeysProperties.getKeys();
        if (log.isDebugEnabled()) {
            log.debug("缓存键配置: {}", list);
        }
        
        // 查找匹配的配置前缀
        for (Map<String, String> map : list) {
            final String prefix = map.get(configName);
            if (CharSequenceUtil.isNotEmpty(prefix)) {
                // 缓存找到的前缀，提高后续查询性能
                PREFIX_CACHE.put(configName, prefix);
                return joinCacheKey(prefix, key);
            }
        }
        
        // 未找到匹配的配置，使用默认前缀
        return getDefaultCacheKey(key);
    }
    
    /**
     * 使用默认前缀构建缓存键
     *
     * @param key 缓存KEY的后缀部分
     * @return 完整的缓存KEY字符串
     */
    private String getDefaultCacheKey(String key) {
        if (CharSequenceUtil.isBlank(key)) {
            return DEFAULT_PREFIX + CACHE_KEY_SEPARATOR + "key";
        }
        return DEFAULT_PREFIX + CACHE_KEY_SEPARATOR + key;
    }
    
    /**
     * 连接前缀和键名生成完整缓存键
     *
     * @param prefix 缓存键前缀
     * @param key    缓存键名
     * @return 完整的缓存键
     */
    private String joinCacheKey(String prefix, String key) {
        if (CharSequenceUtil.isBlank(key)) {
            return prefix + CACHE_KEY_SEPARATOR + "key";
        }
        return prefix + CACHE_KEY_SEPARATOR + key;
    }

    /**
     * 缓存键配置属性类
     * 用于从配置文件中加载缓存键配置
     * 只有当cache.enable=1时才会加载配置
     */
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
        /**
         * 缓存键配置列表
         * 每个Map包含多个键值对，键为配置名称，值为对应的缓存前缀
         */
        private List<Map<String, String>> keys;
    }
}
