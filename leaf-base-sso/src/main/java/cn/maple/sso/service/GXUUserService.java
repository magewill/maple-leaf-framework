package cn.maple.sso.service;

import cn.hutool.core.lang.Dict;

/**
 * 前端用户服务接口
 * <p>
 * 该接口定义了前端用户认证和信息管理的核心功能，包括：
 * 1. 用户Token验证
 * 2. 用户登录认证
 * 3. 用户信息获取
 * 4. 用户登出处理
 * </p>
 * <p>
 * 安全说明：
 * - 实现类应确保Token验证的安全性
 * - 登录过程应防范暴力破解和注入攻击
 * - 用户信息获取应进行权限控制
 * - 登出处理应清理所有会话状态
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
public interface GXUUserService {
    /**
     * 验证前端用户的Token是否有效
     * <p>
     * 验证用户提供的Token的有效性，包括：
     * - Token格式验证
     * - Token签名验证
     * - Token过期验证
     * - 用户状态验证
     * </p>
     *
     * @param token 用户token字符串
     * @return 验证成功返回包含用户信息的Dict，验证失败返回空Dict
     */
    default Dict verifyUserToken(String token) {
        return Dict.create();
    }

    /**
     * 用户账号登录
     * <p>
     * 验证用户登录凭证并生成安全的登录Token，处理流程：
     * 1. 验证用户名密码/验证码等登录凭证
     * 2. 检查用户状态（是否禁用、锁定等）
     * 3. 生成安全的登录Token
     * 4. 记录登录日志和更新登录状态
     * </p>
     * <p>
     * 安全建议：
     * - 实现登录失败次数限制
     * - 对敏感登录参数进行加密传输
     * - 生成的Token应具有适当的过期时间
     * - 考虑实现多因素认证机制
     * </p>
     *
     * @param loginParam 登录参数，包含用户名、密码等认证信息
     * @return 登录成功返回有效的Token字符串，失败返回空字符串
     */
    default String login(Dict loginParam) {
        return "";
    }

    /**
     * 通过用户ID获取用户信息
     * <p>
     * 根据用户ID查询并返回用户的详细信息，适用于：
     * - 获取当前登录用户的个人资料
     * - 管理员查询特定用户信息
     * - 用户关系展示等场景
     * </p>
     * <p>
     * 安全建议：
     * - 实现类应进行权限检查，确保只有授权用户可以访问数据
     * - 敏感字段（如密码）应在返回前移除
     * - 考虑数据脱敏处理（如手机号、邮箱部分隐藏）
     * </p>
     *
     * @param userId 要查询的用户ID
     * @return 包含用户信息的Dict对象，查询失败返回空Dict
     */
    default Dict getUserByUserId(Long userId) {
        return Dict.create();
    }

    /**
     * 用户登出处理
     * <p>
     * 执行用户安全登出流程，包括：
     * 1. 清除服务端的会话状态
     * 2. 使当前Token失效
     * 3. 记录登出日志
     * 4. 执行其他清理操作
     * </p>
     * <p>
     * 安全建议：
     * - 确保所有会话状态和缓存数据被完全清除
     * - 实现Token黑名单机制，防止已登出的Token被重用
     * - 考虑在多设备登录场景下的处理策略
     * </p>
     */
    default void loginOut() {
        // 默认实现为空，具体实现类需要提供完整的登出逻辑
    }
}
