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

public class GXTokenManagerUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXTokenManagerUtils.class);

    private GXTokenManagerUtils() {
    }

    public static String generateManagerToken(Object userId, Dict param, String secretKey, int expires) {
        return generateToken(userId, param, secretKey, expires);
    }

    public static Dict decodeManagerToken(String source, String secretKey) {
        return decodeToken(source, secretKey);
    }

    public static String generateUserToken(Object userId, Dict param, String secretKey, int expires) {
        return generateToken(userId, param, secretKey, expires);
    }

    public static Dict decodeUserToken(String source, String secretKey) {
        return decodeToken(source, secretKey);
    }

    private static String generateToken(Object userId, Dict param, String secretKey, int expires) {
        if (ObjectUtil.isNull(param)) {
            throw new GXBusinessException("Token param must not be null");
        }
        if (CharSequenceUtil.isEmpty(param.getStr(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME))) {
            throw new GXBusinessException("Token param must include userName");
        }
        if (CharSequenceUtil.isEmpty(secretKey)) {
            throw new GXBusinessException("Token secret key must not be empty");
        }
        if (expires <= 0) {
            throw new GXBusinessException("Token expires must be greater than 0");
        }
        param.putIfAbsent(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, userId);
        param.putIfAbsent(GXTokenConstant.LOGIN_AT_FIELD_NAME, DateUtil.currentSeconds());
        param.putIfAbsent("platform", GXTokenConstant.PLATFORM);
        return GXAuthCodeUtils.authCodeEncode(JSONUtil.toJsonStr(param), secretKey, expires);
    }

    private static Dict decodeToken(String source, String secretKey) {
        if (CharSequenceUtil.isEmpty(source)) {
            throw new GXTokenInvalidException("Token must not be empty");
        }
        if (CharSequenceUtil.isEmpty(secretKey)) {
            throw new GXTokenInvalidException("Token secret key must not be empty");
        }
        try {
            String s = GXAuthCodeUtils.authCodeDecode(source, secretKey);
            if (CharSequenceUtil.equalsIgnoreCase("{}", s)) {
                throw new GXTokenInvalidException("Invalid token identity");
            }
            return JSONUtil.toBean(s, Dict.class);
        } catch (GXTokenInvalidException exception) {
            throw exception;
        } catch (JSONException exception) {
            LOG.error("Token decode JSON failed: {}", exception.getMessage());
            throw new GXTokenInvalidException("Token decode failed");
        } catch (Exception e) {
            LOG.error("Token decode failed: {}", e.getMessage());
            throw new GXTokenInvalidException("Token decode failed");
        }
    }

    public static boolean verifyTokenEffectiveness() {
        try {
            Object tokenConfigService = GXSpringContextUtils.getBean(
                    ClassUtils.forName("cn.maple.sso.service.GXTokenConfigService", GXTokenManagerUtils.class.getClassLoader()));
            if (ObjectUtil.isNull(tokenConfigService)) {
                LOG.debug("Token config service not found, skip token verification");
                return Boolean.TRUE;
            }
            Object result = GXCommonUtils.reflectCallObjectMethod(tokenConfigService, "verifyTokenEffectiveness");
            return Boolean.TRUE.equals(result);
        } catch (ClassNotFoundException e) {
            LOG.debug("leaf-base-sso module is unavailable, skip token verification");
        } catch (Exception e) {
            LOG.error("Token verification failed: {}", e.getMessage());
        }
        return Boolean.TRUE;
    }
}
