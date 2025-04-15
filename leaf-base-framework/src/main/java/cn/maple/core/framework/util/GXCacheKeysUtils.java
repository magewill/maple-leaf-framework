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
     * 获取系统配置的缓存KEY
     * 使用sys.config前缀构建缓存键
     *
     * @param key 缓存KEY的后缀部分
     * @return 完整的缓存KEY字符串
     */
    public String getSysConfigKey(String key) {
        return getCacheKey("sys.config", key);
    }

    /**
     * 获取图形验证码的缓存KEY
     * 使用sys.captcha前缀构建缓存键
     *
     * @param key 缓存KEY的后缀部分
     * @return 完整的缓存KEY字符串
     */
    public String getCaptchaConfigKey(String key) {
        return getCacheKey("sys.captcha", key);
    }

    /**
     * 获取网易云短信验证码的缓存KEY
     * 使用sys.net-ease前缀构建缓存键
     *
     * @param key 缓存KEY的后缀部分
     * @return 完整的缓存KEY字符串
     */
    public String getNetEaseSMSCodeConfigKey(String key) {
        return getCacheKey("sys.net-ease", key);
    }

    /**
     * 获取阿里云短信验证码的缓存KEY
     * 使用sys.ali-yun前缀构建缓存键
     *
     * @param key 缓存KEY的后缀部分
     * @return 完整的缓存KEY字符串
     */
    public String getAliYunSMSCodeConfigKey(String key) {
        return getCacheKey("sys.ali-yun", key);
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
        // 从Spring容器中获取缓存键配置属性
        CacheKeysProperties cacheKeysProperties = GXSpringContextUtils.getBean(CacheKeysProperties.class);
        if (Objects.isNull(cacheKeysProperties)) {
            log.debug("未配置缓存键列表，使用默认前缀");
            return CharSequenceUtil.format("geoxus:default:{}", key);
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
                return prefix + ":" + key;
            }
        }
        
        // 未找到匹配的配置，使用默认前缀
        if (CharSequenceUtil.isNotBlank(key)) {
            return CharSequenceUtil.format("geoxus:default:{}", key);
        }
        return "geoxus:default:key";
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
