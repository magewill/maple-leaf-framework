package cn.maple.core.framework.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXTokenInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

import java.util.Objects;

/**
 * Token管理工具类
 * <p>
 * 该工具类用于管理前后端用户的Token生成、解码和验证。
 * 支持管理端和前端用户的Token处理，包括生成、解码和验证功能。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 生成管理端Token
 * Dict param = Dict.create().set("userName", "admin");
 * String token = GXTokenManagerUtils.generateManagerToken(1, param, "secretKey", 120);
 * 
 * // 解码Token
 * Dict tokenInfo = GXTokenManagerUtils.decodeManagerToken(token, "secretKey");
 * 
 * // 验证Token有效性
 * boolean isValid = GXTokenManagerUtils.verifyTokenEffectiveness();
 * }
 * </pre>
 * </p>
 *
 * @author gapleaf@163.com
 */
public class GXTokenManagerUtils {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXTokenManagerUtils.class);

    /**
     * 私有构造函数，防止实例化
     */
    private GXTokenManagerUtils() {
    }

    /**
     * 生成管理端登录的token
     * <p>
     * 生成的管理端Token包含用户ID、用户名、登录时间等信息。
     * Token会使用指定的密钥进行加密，并设置过期时间。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * Dict param = Dict.create().set("userName", "admin");
     * String token = GXTokenManagerUtils.generateManagerToken(1, param, "secretKey", 120);
     * }
     * </pre>
     * </p>
     *
     * @param userId    管理员ID
     * @param param     附加信息，必须包含userName字段
     * @param secretKey 加解密key
     * @param expires   过期时间（秒）
     * @return String 生成的Token
     * @throws GXBusinessException 当param中缺少userName字段时抛出
     */
    public static String generateManagerToken(Object userId, Dict param, String secretKey, int expires) {
        if (ObjectUtil.isNull(param)) {
            throw new GXBusinessException("参数param不能为空");
        }
        if (CharSequenceUtil.isEmpty(param.getStr(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME))) {
            throw new GXBusinessException("请在param参数中设置userName!!!");
        }
        if (CharSequenceUtil.isEmpty(secretKey)) {
            throw new GXBusinessException("密钥不能为空");
        }
        if (expires <= 0) {
            throw new GXBusinessException("过期时间必须大于0");
        }
        param.putIfAbsent(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, userId);
        param.putIfAbsent(GXTokenConstant.LOGIN_AT_FIELD_NAME, DateUtil.currentSeconds());
        param.putIfAbsent("platform", GXTokenConstant.PLATFORM);
        return GXAuthCodeUtils.authCodeEncode(JSONUtil.toJsonStr(param), secretKey, expires);
    }

    /**
     * 解码后端用户的加密TOKEN字符串
     * <p>
     * 注意: 由于后端用户不需要保持长时间的登录操作, 所以在生成token时, 为token指定了过期时间
     * </p>
     *
     * @param source    加密TOKEN字符串
     * @param secretKey 加解密KEY
     * @return Dict Token解码后的信息
     * @throws GXTokenInvalidException 当Token无效时抛出
     */
    public static Dict decodeManagerToken(String source, String secretKey) {
        if (CharSequenceUtil.isEmpty(source)) {
            throw new GXTokenInvalidException("Token不能为空");
        }
        if (CharSequenceUtil.isEmpty(secretKey)) {
            throw new GXTokenInvalidException("密钥不能为空");
        }
        try {
            String s = GXAuthCodeUtils.authCodeDecode(source, secretKey);
            if (CharSequenceUtil.equalsIgnoreCase("{}", s)) {
                throw new GXTokenInvalidException("无效用户身份!!!");
            }
            return JSONUtil.toBean(s, Dict.class);
        } catch (JSONException exception) {
            LOG.error("Token解码失败: {}", exception.getMessage());
            throw new GXTokenInvalidException("Token解码失败");
        }
    }

    /**
     * 生成前端用户的登录Token
     * <p>
     * 生成的前端用户Token包含用户ID、用户名、登录时间等信息。
     * Token会使用指定的密钥进行加密，并设置过期时间。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * Dict param = Dict.create().set("userName", "user");
     * String token = GXTokenManagerUtils.generateUserToken(1, param, "secretKey", 600);
     * }
     * </pre>
     * </p>
     *
     * @param userId    前端用户的ID
     * @param param     附加信息，必须包含userName字段
     * @param secretKey 加解密KEY
     * @param expires   过期时间（秒）
     * @return String 生成的Token
     * @throws GXBusinessException 当param中缺少userName字段时抛出
     */
    public static String generateUserToken(Object userId, Dict param, String secretKey, int expires) {
        if (Objects.isNull(param)) {
            throw new GXBusinessException("参数param不能为空");
        }
        if (CharSequenceUtil.isEmpty(param.getStr(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME))) {
            throw new GXBusinessException("请在param参数中设置userName!!!");
        }
        if (CharSequenceUtil.isEmpty(secretKey)) {
            throw new GXBusinessException("密钥不能为空");
        }
        if (expires <= 0) {
            throw new GXBusinessException("过期时间必须大于0");
        }
        param.putIfAbsent(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, userId);
        param.putIfAbsent(GXTokenConstant.LOGIN_AT_FIELD_NAME, DateUtil.currentSeconds());
        param.putIfAbsent("platform", GXTokenConstant.PLATFORM);
        return GXAuthCodeUtils.authCodeEncode(JSONUtil.toJsonStr(param), secretKey, expires);
    }

    /**
     * 解码前端用户的TOKEN字符串
     * <p>
     * 注意: 由于前端用户需要保持长时间的登录信息, 在生成token字符串时, 不需要指定token的过期时间,
     * 过期时间时存放在s_user_token表中进行维护
     * </p>
     *
     * @param source    加密TOKEN字符串
     * @param secretKey 加解密KEY
     * @return Dict Token解码后的信息
     * @throws GXTokenInvalidException 当Token无效时抛出
     */
    public static Dict decodeUserToken(String source, String secretKey) {
        if (CharSequenceUtil.isEmpty(source)) {
            throw new GXTokenInvalidException("Token不能为空");
        }
        if (CharSequenceUtil.isEmpty(secretKey)) {
            throw new GXTokenInvalidException("密钥不能为空");
        }
        try {
            String s = GXAuthCodeUtils.authCodeDecode(source, secretKey);
            if (CharSequenceUtil.equalsIgnoreCase("{}", s)) {
                throw new GXTokenInvalidException("无效用户身份!!!");
            }
            return JSONUtil.toBean(s, Dict.class);
        } catch (Exception e) {
            LOG.error("Token解码失败: {}", e.getMessage());
            throw new GXTokenInvalidException("Token解码失败");
        }
    }

    /**
     * 验证token的有效性
     * <p>
     * 验证规则可以调用别的服务，亦可以自身验证。
     * 自身验证可以通过redis的token缓存key+tokenSecret来进行验证解密并验证是否有效，
     * 减少服务间的通信。
     * </p>
     *
     * @return true 有效 ; false 无效
     */
    public static boolean verifyTokenEffectiveness() {
        try {
            Object tokenConfigService = GXSpringContextUtils.getBean(
                    ClassUtils.forName("cn.maple.sso.service.GXTokenConfigService", GXTokenManagerUtils.class.getClassLoader()));
            if (ObjectUtil.isNull(tokenConfigService)) {
                LOG.warn("未找到Token配置服务，跳过Token验证");
                return Boolean.TRUE;
            }
            Object result = GXCommonUtils.reflectCallObjectMethod(tokenConfigService, "verifyTokenEffectiveness");
            return Boolean.TRUE.equals(result);
        } catch (ClassNotFoundException e) {
            LOG.error("请导入leaf-base-sso模块!");
        } catch (Exception e) {
            LOG.error("Token验证失败: {}", e.getMessage());
        }
        return Boolean.TRUE;
    }
}