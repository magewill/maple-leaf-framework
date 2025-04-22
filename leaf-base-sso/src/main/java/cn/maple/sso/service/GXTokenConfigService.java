package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;

/**
 * <p>
 * Token配置服务接口
 * </p>
 * 
 * 用户可以实现该接口来自定义Token的生成、存储和验证策略。
 * 在SSO系统中负责Token的全生命周期管理，包括：
 * 1. 提供Token加解密密钥
 * 2. 定义Token缓存策略
 * 3. 验证Token有效性
 * 4. 管理Token存储
 * 
 * 安全说明：
 * - 该接口的实现类直接影响系统的安全性，应谨慎实现
 * - 建议在生产环境中使用强密钥和安全的存储策略
 * - 应考虑Token过期、撤销和刷新机制
 * 
 * 使用示例：
 * <pre>
 * @Component
 * public class MyTokenConfigService implements GXTokenConfigService {
 *     // 自定义密钥（生产环境应从安全配置中读取）
 *     @Override
 *     public String getTokenSecret() {
 *         return "my-secure-token-key-12345";
 *     }
 *     
 *     // 自定义缓存键生成策略
 *     @Override
 *     public String getTokenCacheKey(Long userId, Dict extraData) {
 *         String platform = extraData.getStr("platform", "web");
 *         return getTokenCachePrefix() + userId + ":" + platform;
 *     }
 *     
 *     // 自定义Token有效性验证
 *     @Override
 *     public boolean verifyTokenEffectiveness() {
 *         // 实现自定义验证逻辑
 *         return redisTemplate.hasKey(getCurrentTokenKey());
 *     }
 * }
 * </pre>
 */
public interface GXTokenConfigService {
    /**
     * <p>
     * 获取通用token的加解密密钥
     * </p>
     * 
     * 该密钥用于Token的加密和解密操作，是确保Token安全性的关键。
     * 
     * 安全建议：
     * 1. 在生产环境中使用足够复杂且定期更换的密钥
     * 2. 密钥长度建议至少32字符
     * 3. 避免使用硬编码的密钥，应从安全的配置源获取
     * 4. 考虑使用非对称加密算法提高安全性
     * 5. 不同环境（开发、测试、生产）应使用不同的密钥
     *
     * @return token加解密密钥字符串
     */
    default String getTokenSecret() {
        return GXTokenConstant.TOKEN_SECRET_KEY;
    }

    /**
     * <p>
     * 获取token的缓存存储桶名称
     * </p>
     * 
     * 定义Token在缓存系统中的存储位置，通常基于应用名称构建。
     * 可用于在分布式系统中隔离不同应用的Token存储空间。
     * 
     * 设计建议：
     * 1. 在多应用环境中，确保每个应用使用独立的存储桶
     * 2. 考虑在名称中包含环境标识（dev/test/prod）
     * 3. 避免使用可预测或过于简单的名称
     *
     * @return 缓存存储桶的名称字符串
     */
    default String getCacheBucketName() {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "");
        if (CharSequenceUtil.isNotBlank(appName)) {
            return CharSequenceUtil.format("token_bucket:{}", appName);
        }
        return "token_bucket";
    }

    /**
     * <p>
     * 检测当前用户的登录状态是否有效
     * </p>
     * 
     * 验证用户登录状态的有效性，可以通过以下方式实现：
     * 1. 调用其他微服务进行验证
     * 2. 检查本地缓存中的状态信息
     * 3. 查询数据库验证用户状态
     * 
     * 安全考虑：
     * 1. 检查用户账号是否被锁定或禁用
     * 2. 验证用户会话是否超时
     * 3. 检测是否存在异常登录行为
     * 4. 考虑实现会话固定攻击防护
     *
     * @return 如果登录状态有效返回true，否则返回false
     */
    default boolean checkLoginStatus() {
        return true;
    }

    /**
     * <p>
     * 获取token的缓存key的前缀
     * </p>
     * 
     * 定义Token缓存键的前缀，用于在缓存系统中组织和识别Token。
     * 合理的前缀设计有助于缓存管理和问题排查。
     * 
     * 设计建议：
     * 1. 使用有意义且唯一的前缀，避免与其他系统冲突
     * 2. 考虑在前缀中包含版本信息，便于系统升级时区分
     * 3. 保持前缀简短但有意义，减少存储开销
     *
     * @return 缓存key前缀字符串
     */
    default String getTokenCachePrefix() {
        return "ssoTokenKey_";
    }

    /**
     * <p>
     * 获取token的缓存键
     * </p>
     * 
     * 根据用户ID和额外参数生成唯一的缓存键。
     * 该键用于在缓存系统中存储和检索Token信息。
     * 实现类需要确保生成的键具有唯一性和一致性。
     * 
     * 实现建议：
     * 1. 结合用户ID和设备/平台信息生成唯一键
     * 2. 考虑多终端登录场景，可使用不同的键区分
     * 3. 避免使用敏感信息作为键的一部分
     * 4. 确保键的生成算法是确定性的（相同输入产生相同输出）
     * 
     * 示例实现：
     * <pre>
     * public String getTokenCacheKey(Long userId, Dict extraData) {
     *     String platform = extraData.getStr("platform", "web");
     *     return getTokenCachePrefix() + userId + ":" + platform;
     * }
     * </pre>
     *
     * @param userId    用户ID，用于标识Token所属用户
     * @param extraData 额外参数，根据业务需要自行传递，如设备信息、平台标识等
     * @return 生成的缓存键字符串
     * @throws GXBusinessException 当无法生成有效的缓存键时抛出异常
     */
    default String getTokenCacheKey(Long userId, Dict extraData) {
        throw new GXBusinessException("请实现缓存的cache键方法!");
    }

    /**
     * <p>
     * 获取业务有效的token数据
     * </p>
     * 
     * 根据请求中的Token获取完整有效的Token数据，可能的实现方式：
     * 1. 直接返回解码后的请求Token（默认实现）
     * 2. 从缓存系统中获取完整Token数据
     * 3. 调用其他服务获取Token信息
     * 4. 对Token数据进行验证和补充
     * 
     * 安全考虑：
     * 1. 验证Token的完整性和有效性
     * 2. 检查Token是否被篡改
     * 3. 确保敏感信息不被泄露
     * 4. 考虑实现Token数据的增强（如添加额外的安全信息）
     *
     * @param requestToken 当前请求中token字符串解码之后的数据
     * @return 包含完整Token信息的Dict对象
     */
    default Dict getEfficaciousToken(Dict requestToken) {
        return requestToken;
    }

    /**
     * <p>
     * 验证token的有效性
     * </p>
     * 
     * 验证当前Token是否有效，可以通过多种方式实现：
     * 1. 调用其他服务进行验证
     * 2. 通过缓存系统验证（如Redis中是否存在对应的Token缓存）
     * 3. 使用tokenSecret解密并验证Token内容
     * 4. 检查Token是否过期或被撤销
     * 
     * 安全建议：
     * 1. 验证Token的签名和内容完整性
     * 2. 检查Token的过期时间
     * 3. 验证Token的使用环境（如IP、设备信息等）
     * 4. 考虑实现Token黑名单机制
     * 5. 对于敏感操作，考虑要求二次验证
     * 6. 实现Token使用频率限制，防止重放攻击
     * 7. 记录验证失败的情况，用于安全审计
     * 
     * 示例实现：
     * <pre>
     * public boolean verifyTokenEffectiveness() {
     *     // 1. 检查Token是否在缓存中存在
     *     boolean exists = redisTemplate.hasKey(getCurrentTokenKey());
     *     if (!exists) {
     *         return false;
     *     }
     *     
     *     // 2. 检查Token是否在黑名单中
     *     boolean inBlacklist = tokenBlacklistService.isBlacklisted(getCurrentToken());
     *     if (inBlacklist) {
     *         return false;
     *     }
     *     
     *     // 3. 检查Token是否过期
     *     long expireTime = getCurrentTokenExpireTime();
     *     if (System.currentTimeMillis() > expireTime) {
     *         return false;
     *     }
     *     
     *     return true;
     * }
     * </pre>
     *
     * @return 如果Token有效返回true，否则返回false
     */
    default boolean verifyTokenEffectiveness() {
        return Boolean.TRUE;
    }
}
