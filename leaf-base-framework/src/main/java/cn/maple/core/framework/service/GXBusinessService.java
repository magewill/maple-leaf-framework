package cn.maple.core.framework.service;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.core.framework.dto.res.GXBaseResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBeanNotExistsException;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public interface GXBusinessService {
    Logger LOG = LoggerFactory.getLogger(GXBusinessService.class);

    String encryptedPhoneNumber(String phoneNumber, String key);

    String decryptedPhoneNumber(String encryptPhoneNumber, String key);

    String hiddenPhoneNumber(CharSequence phoneNumber, int startInclude, int endExclude, char replacedChar);

    <T, R> R getSingleFieldValueByEntity(T entity, String path, Class<R> type);

    <T, R> R getSingleFieldValueByEntity(T entity, String path, Class<R> type, R defaultValue);

    <S, T> T convertSourceToTarget(S source, Class<T> tClass);

    <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions, Dict extraData);

    <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions);

    <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass, String methodName, CopyOptions copyOptions, Dict extraData);

    default Object callMethod(Class<?> serveClass, String methodName, Object... params) {
        return GXCommonUtils.reflectCallObjectMethod(serveClass, methodName, params);
    }

    default Object callMethod(Object targetObject, String methodName, Object... params) {
        return GXCommonUtils.reflectCallObjectMethod(targetObject, methodName, params);
    }

    default <S extends GXBaseDBResDto, T extends GXBaseResDto> GXPaginationResDto<T> convertPaginationDBResDtoToResDto(GXPaginationResDto<S> pagination, Class<T> targetClazz, String methodName, CopyOptions copyOptions, Dict extraData) {
        List<S> records = pagination.getRecords();
        long total = pagination.getTotal();
        long pages = pagination.getPages();
        long pageSize = pagination.getPageSize();
        long currentPage = pagination.getCurrentPage();
        List<T> list = convertSourceListToTargetList(records, targetClazz, methodName, copyOptions, extraData);
        return new GXPaginationResDto<>(list, total, pages, pageSize, currentPage);
    }

    default <S extends GXBaseDBResDto, T extends GXBaseResDto> GXPaginationResDto<T> convertPaginationDBResDtoToResDto(GXPaginationResDto<S> pagination, Class<T> targetClazz, Dict extraData) {
        return convertPaginationDBResDtoToResDto(pagination, targetClazz, null, new CopyOptions(), extraData);
    }

    default <S extends GXBaseDBResDto, T extends GXBaseResDto> GXPaginationResDto<T> convertPaginationDBResDtoToResDto(GXPaginationResDto<S> pagination, Class<T> targetClazz) {
        return convertPaginationDBResDtoToResDto(pagination, targetClazz, Dict.create());
    }

    default <R> R getFrontEndUserId(String tokenName, String tokenSecretKey, Class<R> targetClass) {
        if (Objects.isNull(tokenSecretKey)) {
            throw new GXBusinessException("请传递token密钥!");
        }
        return getLoginFieldFromToken(tokenName, GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, targetClass, tokenSecretKey);
    }

    default <R> R getFrontEndUserId(String tokenSecretKey, Class<R> targetClass) {
        return getFrontEndUserId(GXTokenConstant.TOKEN_NAME, tokenSecretKey, targetClass);
    }

    default <R> R getManagerUserId(String tokenName, String tokenSecretKey, Class<R> targetClass) {
        if (Objects.isNull(tokenSecretKey)) {
            throw new GXBusinessException("请传递token密钥!");
        }
        return getLoginFieldFromToken(tokenName, GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, targetClass, tokenSecretKey);
    }

    default <R> R getManagerUserId(String tokenSecretKey, Class<R> targetClass) {
        return getManagerUserId(GXTokenConstant.TOKEN_NAME, tokenSecretKey, targetClass);
    }

    default <R> R getLoginFieldFromToken(String tokenName, String tokenFieldName, Class<R> clazz, String secretKey) {
        R fieldFromToken = GXCurrentRequestContextUtils.getLoginFieldFromToken(tokenName, tokenFieldName, clazz, secretKey);
        if (Objects.isNull(fieldFromToken)) {
            LOG.error("token中不存在键为{}的值", tokenFieldName);
        }
        return fieldFromToken;
    }

    default Object getDataFromCache(String cacheKey, Object... params) {
        GXBaseCacheService cacheService = getCacheService();
        if (Objects.nonNull(cacheService)) {
            return cacheService.getCache(getCacheBucketName(), cacheKey);
        }
        return null;
    }

    default void setCacheData(String cacheKey, Object data, Object... params) {
        GXBaseCacheService cacheService = getCacheService();
        GXBaseCacheLockService cacheLockService = Objects.requireNonNull(GXSpringContextUtils.getBean(GXBaseCacheLockService.class));
        if (Objects.nonNull(cacheService)) {
            try {
                cacheLockService.tryLock(cacheKey);
                Object dataFromCache = getDataFromCache(cacheKey, params);
                if (Objects.isNull(dataFromCache)) {
                    cacheService.setCache(getCacheBucketName(), cacheKey, data);
                }
            } finally {
                cacheLockService.releaseLock(cacheKey);
            }
        }
    }

    default void evictCacheData(String cacheKey, Object... params) {
        GXBaseCacheService cacheService = getCacheService();
        if (Objects.nonNull(cacheService)) {
            cacheService.deleteCache(getCacheBucketName(), cacheKey);
        }
    }

    default GXBaseCacheService getCacheService() {
        GXBaseCacheService cacheService = GXSpringContextUtils.getBean(GXBaseCacheService.class);
        if (Objects.nonNull(cacheService)) {
            return cacheService;
        }
        LOG.warn("请提供缓存组件");
        return null;
    }

    default String getCacheBucketName() {
        String s = ReUtil.replaceAll(getClass().getSimpleName(), "GX|ServiceImpl", "");
        return "mapleaf_default_cache:" + CharSequenceUtil.toUnderlineCase(s) + "_bucket";
    }

    default Dict getLoginCredentials() {
        try {
            Class<?> tokenConfigServiceKlass = ClassUtils.forName("cn.maple.sso.service.GXTokenConfigService", ClassUtils.getDefaultClassLoader());
            Object tokenConfigService = GXSpringContextUtils.getBean(tokenConfigServiceKlass);
            if (ObjectUtil.isNull(tokenConfigService)) {
                throw new GXBeanNotExistsException("'cn.maple.sso.service.GXTokenConfigService' Bean is not defined!");
            }
            String tokenSecret = (String) GXCommonUtils.reflectCallObjectMethod(tokenConfigService, "getTokenSecret");
            return GXCurrentRequestContextUtils.getLoginCredentials(GXTokenConstant.TOKEN_NAME, tokenSecret);
        } catch (ClassNotFoundException e) {
            LOG.error("'cn.maple.sso.service.GXTokenConfigService' class not found! please import 'leaf-base-sso' module!");
            return Dict.create();
            //throw new GXBusinessException(e.getMessage(), e);
        }
    }

    default Long getLoginUserId() {
        Dict dict = getLoginCredentials();
        return dict.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
    }

    default String getLoginUserName() {
        if (GXCurrentRequestContextUtils.isHTTP() && GXCurrentRequestContextUtils.tokenExists()) {
            Dict dict = getLoginCredentials();
            return dict.getStr(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME);
        }
        LOG.info("不是HTTP请求,不能获取登录的用户名!");
        return "RPC_USER";
    }
}
