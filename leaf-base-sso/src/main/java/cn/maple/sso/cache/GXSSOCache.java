package cn.maple.sso.cache;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXTokenInvalidException;
import cn.maple.core.framework.service.GXBaseCacheService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.sso.service.GXTokenConfigService;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * SSO 缓存接口
 * </p>
 * 
 * <p>
 * 该接口负责SSO系统中Token的缓存管理，包括：
 * 1. 获取缓存中的Token数据
 * 2. 将Token数据存入缓存
 * 3. 从缓存中删除Token数据
 * 4. 验证缓存Token与请求Token的一致性
 * </p>
 * 
 * <p>
 * 安全说明：
 * - 实现了Token的集中式管理，支持分布式环境
 * - 提供Token一致性验证，防止会话固定攻击
 * - 支持Token失效和踢出用户功能
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public interface GXSSOCache {
    /**
     * <p>
     * 根据key获取SSO票据
     * </p>
     * <p>
     * 如果缓存服务宕机，返回 token 设置 flag 为 Token.FLAG_CACHE_SHUT
     * </p>
     * <p>
     * 安全说明：
     * - 支持RPC调用场景的特殊处理
     * - 通过tokenConfigService验证用户登录状态
     * - 对无效Token抛出异常并返回401状态码
     * - 支持自定义Token有效性验证逻辑
     * </p>
     *
     * @param expires      过期时间（延时心跳时间）
     * @param requestToken cookie中存储的ssoToken
     * @return SSO票据
     * @throws GXTokenInvalidException 当token无效时抛出此异常
     */
    default Dict get(int expires, Dict requestToken) {
        // 如果是RPC调用 直接返回
        if (GXCurrentRequestContextUtils.isRPC()) {
            return Dict.create();
        }
        // 获取Token配置服务
        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        if (tokenConfigService == null) {
            throw new GXBusinessException("未找到TokenConfigService实现类");
        }
        // 调用业务端的逻辑验证用户是否有效
        boolean isValid = tokenConfigService.checkLoginStatus();
        if (!isValid) {
            throw new GXTokenInvalidException("token已经失效,请重新登录!", HttpStatus.HTTP_UNAUTHORIZED);
        }
        // 获取有效的Token数据
        return tokenConfigService.getEfficaciousToken(requestToken);
    }

    /**
     * 设置SSO票据
     * <p>
     * 将Token数据存储到缓存系统中，主要流程：
     * 1. 获取缓存服务和Token配置服务
     * 2. 从Token中提取用户ID
     * 3. 生成缓存键
     * 4. 将Token序列化后存入缓存
     * </p>
     * <p>
     * 安全说明：
     * - 验证Token中必须包含有效的用户ID
     * - 使用专用的缓存桶隔离Token数据
     * - 支持配置Token的过期时间
     * - 对Token进行JSON序列化存储，便于跨语言使用
     * </p>
     *
     * @param ssoToken SSO票据
     * @param expires  过期时间（秒）
     * @return 操作是否成功
     * @throws GXTokenInvalidException 当Token无效时抛出此异常
     */
    @SuppressWarnings("all")
    default boolean set(Dict ssoToken, int expires) {
        // 获取缓存服务和Token配置服务
        GXBaseCacheService cacheService = GXSpringContextUtils.getBean(GXBaseCacheService.class);
        GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
        
        // 验证服务是否可用
        if (tokenConfigService == null || cacheService == null) {
            throw new GXBusinessException("缓存服务或Token配置服务不可用");
        }
        
        // 验证Token中的用户ID
        Long id = Optional.ofNullable(ssoToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME)).orElse(0L);
        if (Objects.isNull(id) || id <= 0) {
            throw new GXTokenInvalidException("token中未包含有效的id标识");
        }
        
        // 生成缓存键并存储Token
        String tokenCacheKey = tokenConfigService.getTokenCacheKey(id, ssoToken);
        String cacheBucketName = tokenConfigService.getCacheBucketName();
        
        try {
            cacheService.setCache(cacheBucketName, tokenCacheKey, JSONUtil.toJsonStr(ssoToken), expires, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            //log.error("存储Token到缓存时发生错误", e);
            return false;
        }
    }

    /**
     * 删除SSO票据
     * <p>
     * 从缓存系统中删除Token数据，主要流程：
     * 1. 获取缓存服务和Token配置服务
     * 2. 如果传入的Token为空，尝试从当前请求中获取
     * 3. 从Token中提取用户ID
     * 4. 生成缓存键并删除对应的缓存数据
     * </p>
     * <p>
     * 安全说明：
     * - 支持传入Token为空的情况，自动从当前请求获取
     * - 验证Token中必须包含有效的用户ID
     * - 返回删除操作的结果，便于调用方确认操作是否成功
     * - 异常情况下提供详细的错误信息
     * </p>
     *
     * @param ssoToken 要删除的SSO票据，如为空则从当前请求获取
     * @return 操作是否成功
     * @throws GXTokenInvalidException 当Token无效时抛出此异常
     */
    @SuppressWarnings("all")
    default boolean delete(Dict ssoToken) {
        try {
            // 获取缓存服务和Token配置服务
            GXBaseCacheService cacheService = GXSpringContextUtils.getBean(GXBaseCacheService.class);
            GXTokenConfigService tokenConfigService = GXSpringContextUtils.getBean(GXTokenConfigService.class);
            
            // 验证服务是否可用
            if (tokenConfigService == null || cacheService == null) {
                throw new GXBusinessException("缓存服务或Token配置服务不可用");
            }
            
            // 如果传入的Token为空，尝试从当前请求中获取
            Dict tmpToken = ssoToken;
            if (CollUtil.isEmpty(tmpToken)) {
                String tokenSecret = tokenConfigService.getTokenSecret();
                tmpToken = GXCurrentRequestContextUtils.getLoginCredentials(GXTokenConstant.TOKEN_NAME, tokenSecret);
                if (CollUtil.isEmpty(tmpToken)) {
                    return false; // 无法获取有效的Token
                }
            }
            
            // 验证Token中的用户ID
            Long id = Optional.ofNullable(tmpToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME)).orElse(0L);
            if (Objects.isNull(id) || id <= 0) {
                throw new GXTokenInvalidException("token中未包含有效的id标识");
            }
            
            // 生成缓存键并删除缓存
            String tokenCacheKey = tokenConfigService.getTokenCacheKey(id, tmpToken);
            String cacheBucketName = tokenConfigService.getCacheBucketName();
            Object result = cacheService.deleteCache(cacheBucketName, tokenCacheKey);
            
            return Objects.nonNull(result);
        } catch (GXTokenInvalidException e) {
            throw e; // 直接抛出Token无效异常
        } catch (Exception e) {
            //log.error("从缓存中删除Token时发生错误", e);
            return false;
        }
    }

    /**
     * 自定义验证缓存中的token与header或者Cookie中的token是否一致
     * <p>
     * 该验证逻辑会被拦截器自动调用，用于防止会话固定攻击和Token被盗用
     * 主要通过比较两个Token中的登录时间戳来判断是否一致
     * </p>
     * <p>
     * 安全说明：
     * - 通过比较登录时间戳验证Token一致性
     * - 支持本地开发环境的特殊处理
     * - 防止会话固定攻击和Token复制攻击
     * - 当用户在其他设备登录时，可以使旧Token失效
     * </p>
     *
     * @param cacheSSOToken  缓存中存储的token数据
     * @param cookieSSOToken 从header或者cookie中获取的token数据
     * @return 验证通过返回true，否则返回false
     */
    default boolean verifyTokenConsistency(Dict cacheSSOToken, Dict cookieSSOToken) {
        // 参数有效性检查
        if (cacheSSOToken == null || cookieSSOToken == null) {
            return false;
        }
        
        // 获取登录时间戳
        Long cookieLoginAt = Optional.ofNullable(cookieSSOToken.getLong("loginAt")).orElse(0L);
        Long cacheLoginAt = Optional.ofNullable(cacheSSOToken.getLong("loginAt")).orElse(1L);
        
        // 本地开发环境特殊处理
        String activeProfile = GXCommonUtils.getActiveProfile();
        if (CharSequenceUtil.equalsIgnoreCase(activeProfile, "local")) {
            return true;
        }
        
        // 比较登录时间戳是否一致
        return cookieLoginAt.equals(cacheLoginAt);
    }
}
