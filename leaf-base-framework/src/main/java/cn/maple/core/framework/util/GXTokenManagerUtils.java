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

public class GXTokenManagerUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXTokenManagerUtils.class);

    private GXTokenManagerUtils() {
    }

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