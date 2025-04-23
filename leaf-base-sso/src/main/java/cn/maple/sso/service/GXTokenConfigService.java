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
 * <p>
 * 安全说明：
 * - 该接口的实现类直接影响系统的安全性，应谨慎实现
 * - 建议在生产环境中使用强密钥和安全的存储策略
 * - 应考虑Token过期、撤销和刷新机制
 * - 避免使用固定密钥，应支持密钥轮换机制
 * - 考虑实现Token使用限制，如IP绑定、设备绑定等
 * </p>
 * 
 * <p>
 * 性能优化建议：
 * - 使用高效的缓存系统存储Token
 * - 实现合理的缓存过期策略，避免缓存爆炸
 * - 对频繁访问的Token信息进行本地缓存
 * - 使用异步方式处理Token统计和审计信息
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * @Component
 * public class MyTokenConfigService implements GXTokenConfigService {
 *     @Autowired
 *     private RedisTemplate<String, Object> redisTemplate;
 *     
 *     @Value("${app.token.secret}")
 *     private String tokenSecret;
 *     
 *     // 自定义密钥（从配置中读取，支持环境变量覆盖）
 *     @Override
 *     public String getTokenSecret() {
 *         // 从环境变量获取密钥，如果不存在则使用配置值
 *         String envSecret = System.getenv("APP_TOKEN_SECRET");
 *         return CharSequenceUtil.isNotBlank(envSecret) ? envSecret : tokenSecret;
 *     }
 *     
 *     // 自定义缓存键生成策略，支持多终端登录
 *     @Override
 *     public String getTokenCacheKey(Long userId, Dict extraData) {
 *         // 获取终端类型，默认为web
 *         String platform = extraData.getStr("platform", "web");
 *         // 获取设备ID，用于区分同一终端的不同设备
 *         String deviceId = extraData.getStr("deviceId", "");
 *         
 *         // 构建缓存键，格式：前缀:用户ID:平台:设备ID
 *         String key = getTokenCachePrefix() + userId;
 *         if (CharSequenceUtil.isNotBlank(platform)) {
 *             key += ":" + platform;
 *         }
 *         if (CharSequenceUtil.isNotBlank(deviceId)) {
 *             key += ":" + deviceId;
 *         }
 *         return key;
 *     }
 *     
 *     // 自定义Token有效性验证，增加IP和设备检查
 *     @Override
 *     public boolean verifyTokenEffectiveness() {
 *         // 获取当前请求上下文中的Token信息
 *         Dict token = getCurrentToken();
 *         if (token == null || token.isEmpty()) {
 *             return false;
 *         }
 *         
 *         // 检查Token是否在Redis中存在
 *         String cacheKey = getTokenCacheKey(token.getLong("userId"), token);
 *         Boolean hasKey = redisTemplate.hasKey(cacheKey);
 *         if (hasKey == null || !hasKey) {
 *             return false;
 *         }
 *         
 *         // 检查Token是否被加入黑名单
 *         String blacklistKey = "token:blacklist:" + token.getStr("tokenId");
 *         Boolean inBlacklist = redisTemplate.hasKey(blacklistKey);
 *         if (inBlacklist != null && inBlacklist) {
 *             return false;
 *         }
 *         
 *         // 检查Token是否过期
 *         Long expireTime = token.getLong("expireTime");
 *         if (expireTime != null && System.currentTimeMillis() > expireTime) {
 *             return false;
 *         }
 *         
 *         // 可选：检查IP地址是否匹配
 *         String tokenIp = token.getStr("ip");
 *         String currentIp = getCurrentRequestIp();
 *         if (CharSequenceUtil.isNotBlank(tokenIp) && !tokenIp.equals(currentIp)) {
 *             // 记录可能的Token盗用尝试
 *             logSecurityEvent("IP不匹配", token, currentIp);
 *             return false;
 *         }
 *         
 *         return true;
 *     }
 *     
 *     // 获取当前请求的IP地址
 *     private String getCurrentRequestIp() {
 *         HttpServletRequest request = GXCurrentRequestContextUtils.getHttpServletRequest();
 *         return GXIpHelperUtil.getIpAddr(request);
 *     }
 *     
 *     // 记录安全事件
 *     private void logSecurityEvent(String eventType, Dict token, String detail) {
 *         // 实际项目中应该将安全事件记录到日志系统或安全审计表
 *         log.warn("安全事件: {}, 用户ID: {}, 详情: {}", 
 *                 eventType, token.getLong("userId"), detail);
 *     }
 * }
 * </pre>
 * </p>
 */
public interface GXTokenConfigService {
    /**
     * <p>
     * 获取通用token的加解密密钥
     * </p>
     * 
     * 该密钥用于Token的加密和解密操作，是确保Token安全性的关键。
     * 
     * <p>
     * 安全建议：
     * 1. 在生产环境中使用足够复杂且定期更换的密钥
     * 2. 密钥长度建议至少32字符
     * 3. 避免使用硬编码的密钥，应从安全的配置源获取
     * 4. 考虑使用非对称加密算法提高安全性
     * 5. 不同环境（开发、测试、生产）应使用不同的密钥
     * 6. 实现密钥轮换机制，定期更新密钥但保持对旧密钥的兼容
     * 7. 密钥应存储在安全的位置，如密钥管理系统或环境变量
     * </p>
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
     * <p>
     * 设计建议：
     * 1. 在多应用环境中，确保每个应用使用独立的存储桶
     * 2. 考虑在名称中包含环境标识（dev/test/prod）
     * 3. 避免使用可预测或过于简单的名称
     * 4. 在集群环境中，确保所有节点使用相同的命名规则
     * 5. 考虑使用应用ID而非应用名称，避免名称变更带来的影响
     * 6. 为便于管理，可在名称中包含版本信息
     * </p>
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
     * <p>
     * 安全考虑：
     * 1. 检查用户账号是否被锁定或禁用
     * 2. 验证用户会话是否超时
     * 3. 检测是否存在异常登录行为
     * 4. 考虑实现会话固定攻击防护
     * 5. 实现登录IP限制，防止账号在多地同时登录
     * 6. 检查用户权限是否变更，及时更新或撤销Token
     * 7. 对敏感操作要求重新验证身份
     * </p>
     *
     * <p>
     * 性能优化：
     * 1. 使用本地缓存减少远程调用
     * 2. 采用异步方式记录登录状态检查日志
     * 3. 实现分级验证策略，对不同安全级别的操作采用不同的验证深度
     * </p>
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
     * <p>
     * 设计建议：
     * 1. 使用有意义且唯一的前缀，避免与其他系统冲突
     * 2. 考虑在前缀中包含版本信息，便于系统升级时区分
     * 3. 保持前缀简短但有意义，减少存储开销
     * 4. 可以使用层次化前缀，如 "app:module:token:"
     * 5. 在多租户系统中，考虑在前缀中包含租户标识
     * 6. 避免使用特殊字符，确保与各种缓存系统兼容
     * </p>
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
     * <p>
     * 实现建议：
     * 1. 结合用户ID和设备/平台信息生成唯一键
     * 2. 考虑多终端登录场景，可使用不同的键区分
     * 3. 避免使用敏感信息作为键的一部分
     * 4. 确保键的生成算法是确定性的（相同输入产生相同输出）
     * 5. 考虑加入时间戳或随机因子，支持同一用户多会话
     * 6. 使用一致的分隔符，如冒号，便于键的解析和管理
     * 7. 控制键的长度，过长的键会增加存储和查询开销
     * </p>
     * 
     * <p>
     * 多终端支持示例：
     * - 用户在Web端登录：ssoTokenKey_10001:web:chrome
     * - 同一用户在移动端登录：ssoTokenKey_10001:mobile:android
     * - 同一用户在桌面应用登录：ssoTokenKey_10001:desktop:windows
     * </p>
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
     * <p>
     * 安全考虑：
     * 1. 验证Token的完整性和有效性
     * 2. 检查Token是否被篡改
     * 3. 确保敏感信息不被泄露
     * 4. 考虑实现Token数据的增强（如添加额外的安全信息）
     * 5. 验证Token中的用户信息与缓存中的是否一致
     * 6. 检查Token是否在黑名单中
     * 7. 对可疑的Token访问进行安全审计
     * </p>
     *
     * <p>
     * 性能优化：
     * 1. 使用本地缓存减少远程调用
     * 2. 实现批量获取机制，减少频繁的单次查询
     * 3. 采用延迟加载策略，仅在需要时获取完整数据
     * </p>
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
     * <p>
     * 安全建议：
     * 1. 验证Token的签名和内容完整性
     * 2. 检查Token的过期时间
     * 3. 验证Token的使用环境（如IP、设备信息等）
     * 4. 考虑实现Token黑名单机制
     * 5. 对于敏感操作，考虑要求二次验证
     * 6. 实现Token使用频率限制，防止重放攻击
     * 7. 记录验证失败的情况，用于安全审计
     * 8. 实现渐进式验证策略，对不同安全级别的操作采用不同的验证深度
     * 9. 考虑实现Token的自动刷新机制，在即将过期时更新Token
     * </p>
     *
     * <p>
     * 高级安全特性：
     * 1. 地理位置验证：检查Token的使用位置是否异常
     * 2. 行为分析：监控Token的使用模式，检测异常行为
     * 3. 风险评分：根据多种因素计算Token使用的风险分数
     * 4. 自适应认证：根据风险级别动态调整验证要求
     * </p>
     *
     * @return 如果Token有效返回true，否则返回false
     */
    default boolean verifyTokenEffectiveness() {
        return Boolean.TRUE;
    }
}
