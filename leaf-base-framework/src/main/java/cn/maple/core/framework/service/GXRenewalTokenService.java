package cn.maple.core.framework.service;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;

import java.util.Date;

/**
 * Token续约服务接口
 * <p>
 * 该接口用于实现Token的无感刷新机制，使用户在使用系统过程中不需要频繁登录。
 * 无感刷新机制需要前端配合实现，通常的流程如下：
 * 1. 前端发送请求时携带当前Token
 * 2. 后端验证Token有效性，并检查是否需要刷新
 * 3. 如需刷新，在响应头中添加新Token
 * 4. 前端检测到响应头中有新Token时，更新本地存储的Token
 * </p>
 * <p>
 * 安全建议：
 * 1. Token刷新应考虑防重放攻击，可使用一次性Token或时间戳+签名机制
 * 2. 刷新Token的有效期应适当设置，避免无限期延长会话
 * 3. 敏感操作仍应要求用户重新验证身份
 * 4. 考虑使用双Token机制（Access Token + Refresh Token）增强安全性
 * </p>
 */
public interface GXRenewalTokenService {
    /**
     * 验证token是否需要刷新
     * <p>
     * 根据当前Token的状态判断是否需要刷新。如果需要刷新，当前请求的响应头中会新增Renewal-Token头。
     * 判断是否需要刷新的常见策略包括：
     * 1. 基于Token剩余有效期（如剩余有效期小于总有效期的30%时刷新）
     * 2. 基于用户活跃度（如用户频繁操作时延长Token有效期）
     * 3. 基于安全策略（如检测到异常行为时强制刷新Token）
     * </p>
     *
     * @return 需要刷新返回true，不需要刷新返回false
     */
    default boolean renewalToken() {
        throw new GXBusinessException("请自定义实现Token无感刷新的逻辑");
    }

    /**
     * 刷新获取新Token
     * <p>
     * 根据当前用户信息和额外参数生成新的Token。新Token通常应包含：
     * 1. 用户标识信息（如用户ID、角色等）
     * 2. Token的有效期信息
     * 3. 必要的安全信息（如设备标识、签名等）
     * </p>
     *
     * @param extraData 额外参数，可包含用户ID、角色、权限等信息
     * @return 新生成的token字符串
     * @throws GXBusinessException 当Token刷新失败时抛出业务异常
     */
    default String refreshToken(Dict extraData) {
        throw new GXBusinessException("请自定义实现Token无感刷新的逻辑");
    }

    /**
     * 获取Token的过期时间
     * <p>
     * 解析Token获取其过期时间，用于判断Token是否即将过期需要刷新
     * </p>
     *
     * @param token Token字符串
     * @return Token的过期时间
     */
    default Date getTokenExpireTime(String token) {
        throw new GXBusinessException("请自定义实现获取Token过期时间的逻辑");
    }

    /**
     * 验证Token是否有效
     * <p>
     * 检查Token的有效性，包括格式正确性、签名验证、是否过期等
     * </p>
     *
     * @param token Token字符串
     * @return Token有效返回true，无效返回false
     */
    default boolean validateToken(String token) {
        throw new GXBusinessException("请自定义实现Token验证的逻辑");
    }

    /**
     * 使Token失效
     * <p>
     * 在用户登出或检测到安全风险时，主动使Token失效
     * </p>
     *
     * @param token Token字符串
     * @return 操作成功返回true，失败返回false
     */
    default boolean invalidateToken(String token) {
        throw new GXBusinessException("请自定义实现使Token失效的逻辑");
    }
}
