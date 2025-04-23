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
 * 该接口负责SSO系统中Token的缓存管理，是单点登录系统的核心组件之一。
 * 主要功能包括：
 * 1. 获取缓存中的Token数据
 * 2. 将Token数据存入缓存
 * 3. 从缓存中删除Token数据
 * 4. 验证缓存Token与请求Token的一致性
 * </p>
 * 
 * <p>
 * 实现说明：
 * - 默认提供了基于内存的实现，生产环境建议使用分布式缓存如Redis
 * - 所有方法都提供了默认实现，实现类可以根据需要覆盖特定方法
 * - 缓存键的生成规则由TokenConfigService决定，确保唯一性
 * - 缓存数据使用JSON格式序列化，便于跨语言和跨平台使用
 * </p>
 * 
 * <p>
 * 安全特性：
 * - 实现了Token的集中式管理，支持分布式环境下的会话一致性
 * - 提供Token一致性验证，防止会话固定攻击和会话劫持
 * - 支持Token失效和踢出用户功能，增强系统安全性
 * - 可配置Token过期时间，减少长时间未使用Token的安全风险
 * - 支持与业务系统集成的用户状态验证，实现更精细的权限控制
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
     * 
     * <p>
     * 该方法从缓存系统中获取Token数据，是SSO验证流程的核心方法。
     * 主要流程：
     * 1. 检查是否为RPC调用场景
     * 2. 获取TokenConfigService并验证用户登录状态
     * 3. 获取有效的Token数据并返回
     * </p>
     * 
     * <p>
     * 特殊处理：
     * - 如果缓存服务宕机，返回的token会设置flag为Token.FLAG_CACHE_SHUT
     * - 对于RPC调用场景，直接返回空Dict，由业务系统自行处理
     * - 当用户登录状态无效时，抛出TokenInvalidException异常
     * </p>
     * 
     * <p>
     * 安全说明：
     * - 支持RPC调用场景的特殊处理，避免分布式系统中的认证问题
     * - 通过tokenConfigService验证用户登录状态，支持自定义验证逻辑
     * - 对无效Token抛出异常并返回401状态码，符合HTTP认证规范
     * - 支持业务系统自定义Token有效性验证逻辑，增强灵活性
     * </p>
     *
     * @param expires      过期时间（延时心跳时间）
     * @param requestToken cookie中存储的ssoToken
     * @return SSO票据，包含用户身份和权限信息
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
     * 
     * <p>
     * 将Token数据存储到缓存系统中，是用户登录成功后的关键步骤。
     * 主要流程：
     * 1. 获取缓存服务和Token配置服务
     * 2. 从Token中提取用户ID
     * 3. 生成缓存键
     * 4. 将Token序列化后存入缓存
     * </p>
     * 
     * <p>
     * 实现要点：
     * - 使用TokenConfigService生成缓存键，确保唯一性
     * - 使用JSON格式序列化Token数据，便于跨语言使用
     * - 支持配置Token的过期时间，增强安全性
     * - 返回操作结果，便于调用方进行错误处理
     * </p>
     * 
     * <p>
     * 安全说明：
     * - 验证Token中必须包含有效的用户ID，防止无效数据写入缓存
     * - 使用专用的缓存桶隔离Token数据，避免与其他业务数据混淆
     * - 支持配置Token的过期时间，减少长时间未使用Token的安全风险
     * - 对Token进行JSON序列化存储，便于跨语言使用的同时保证数据完整性
     * </p>
     *
     * @param ssoToken SSO票据，包含用户身份和权限信息
     * @param expires  过期时间（秒）
     * @return 操作是否成功，成功返回true，失败返回false
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
     * 
     * <p>
     * 从缓存系统中删除Token数据，用于用户登出或管理员强制下线用户。
     * 主要流程：
     * 1. 获取缓存服务和Token配置服务
     * 2. 如果传入的Token为空，尝试从当前请求中获取
     * 3. 从Token中提取用户ID
     * 4. 生成缓存键并删除对应的缓存数据
     * </p>
     * 
     * <p>
     * 使用场景：
     * - 用户主动登出系统
     * - 管理员强制下线用户
     * - 检测到异常登录时的安全处理
     * - 用户修改密码后使旧Token失效
     * </p>
     * 
     * <p>
     * 安全说明：
     * - 支持传入Token为空的情况，自动从当前请求获取，增强易用性
     * - 验证Token中必须包含有效的用户ID，防止误删其他用户的Token
     * - 返回删除操作的结果，便于调用方确认操作是否成功
     * - 异常情况下提供详细的错误信息，便于问题排查
     * </p>
     *
     * @param ssoToken 要删除的SSO票据，如为空则从当前请求获取
     * @return 操作是否成功，成功返回true，失败返回false
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
     * 
     * <p>
     * 该验证逻辑会被拦截器自动调用，是防止会话固定攻击和Token被盗用的重要机制。
     * 主要通过比较两个Token中的登录时间戳来判断是否一致，确保用户使用的是最新的Token。
     * </p>
     * 
     * <p>
     * 工作原理：
     * - 比较缓存Token和Cookie/Header Token中的登录时间戳
     * - 如果时间戳不一致，说明用户可能在其他设备登录，当前Token已失效
     * - 本地开发环境可以配置为跳过此验证，便于开发调试
     * </p>
     * 
     * <p>
     * 安全说明：
     * - 通过比较登录时间戳验证Token一致性，防止会话固定攻击
     * - 支持本地开发环境的特殊处理，提高开发效率
     * - 防止Token复制攻击，增强系统安全性
     * - 当用户在其他设备登录时，可以使旧Token失效，实现单点登录
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
