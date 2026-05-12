package cn.maple.core.framework.controller;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrPool;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.dto.protocol.res.GXBaseResProtocol;
import cn.maple.core.framework.dto.protocol.res.GXPaginationResProtocol;
import cn.maple.core.framework.dto.res.GXBaseResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public interface GXBaseController {
    Logger LOG = LoggerFactory.getLogger(GXBaseController.class);

    default <S, T> T convertSourceToTarget(S source, Class<T> clazz) {
        return convertSourceToTarget(source, clazz, null, null, Dict.create());
    }

    default <S, T> T convertSourceToTarget(S source, Class<T> clazz, @Nullable String methodName) {
        return convertSourceToTarget(source, clazz, methodName, null, Dict.create());
    }

    default <S, T> T convertSourceToTarget(S source, Class<T> clazz, Dict extraData) {
        return convertSourceToTarget(source, clazz, null, null, extraData);
    }

    default <S, T> T convertSourceToTarget(S source, Class<T> clazz, @Nullable String methodName, Dict extraData) {
        return convertSourceToTarget(source, clazz, methodName, null, extraData);
    }

    default <S, T> T convertSourceToTarget(S source, Class<T> clazz, @Nullable CopyOptions copyOptions, Dict extraData) {
        return convertSourceToTarget(source, clazz, null, copyOptions, extraData);
    }

    default <S, T> T convertSourceToTarget(S source, Class<T> clazz, @Nullable String methodName, @Nullable CopyOptions copyOptions, Dict extraData) {
        if (clazz == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }
        if (Objects.isNull(methodName)) {
            methodName = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        return GXCommonUtils.convertSourceToTarget(source, clazz, methodName, copyOptions, extraData);
    }

    default <S, T> List<T> convertSourceListToTargetList(Collection<S> collection, Class<T> clazz, @Nullable String methodName, @Nullable CopyOptions copyOptions, Dict extraData) {
        if (clazz == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }
        if (Objects.isNull(methodName)) {
            methodName = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        return GXCommonUtils.convertSourceListToTargetList(collection, clazz, methodName, copyOptions, extraData);
    }

    default <S, T> List<T> convertSourceListToTargetList(Collection<S> collection, Class<T> clazz, @Nullable String methodName, @Nullable CopyOptions copyOptions) {
        return convertSourceListToTargetList(collection, clazz, methodName, copyOptions, Dict.create());
    }

    default <S, T> List<T> convertSourceListToTargetList(Collection<S> collection, Class<T> clazz) {
        return convertSourceListToTargetList(collection, clazz, null, null, Dict.create());
    }

    default <S, T> List<T> convertSourceListToTargetList(Collection<S> collection, Class<T> clazz, Dict extraData) {
        return convertSourceListToTargetList(collection, clazz, null, null, extraData);
    }

    default <R> @Nullable R getLoginFieldFromToken(String tokenName, String tokenFieldName, Class<R> clazz, String secretKey) {
        R fieldFromToken = GXCurrentRequestContextUtils.getLoginFieldFromToken(tokenName, tokenFieldName, clazz, secretKey);
        if (Objects.isNull(fieldFromToken)) {
            LOG.warn("Token field is missing: fieldName={}", tokenFieldName);
        }
        return fieldFromToken;
    }

    default <S extends GXBaseResDto, T extends GXBaseResProtocol> GXPaginationResProtocol<T> convertPaginationResToProtocol(GXPaginationResDto<S> pagination, Class<T> targetClazz) {
        return convertPaginationResToProtocol(pagination, targetClazz, null);
    }

    default <S extends GXBaseResDto, T extends GXBaseResProtocol> GXPaginationResProtocol<T> convertPaginationResToProtocol(@Nullable GXPaginationResDto<S> pagination, Class<T> targetClazz, @Nullable CopyOptions copyOptions) {
        if (pagination == null) {
            return new GXPaginationResProtocol<>(List.of(), 0, 0, 0, 0);
        }
        if (targetClazz == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }
        List<S> records = pagination.getRecords();
        long total = pagination.getTotal();
        long pages = pagination.getPages();
        long pageSize = pagination.getPageSize();
        long currentPage = pagination.getCurrentPage();
        List<T> list = convertSourceListToTargetList(records, targetClazz, null, copyOptions);
        return new GXPaginationResProtocol<>(list, total, pages, pageSize, currentPage);
    }

    default <R extends GXBaseResProtocol> List<R> buildTree(List<R> sourceList, Object rootParentValue, String getParentMethodName) {
        return GXCommonUtils.buildTree(sourceList, rootParentValue, getParentMethodName);
    }

    default <R> @Nullable R getFrontEndUserId(String tokenName, String tokenSecretKey, Class<R> targetClass) {
        if (CharSequenceUtil.isBlank(tokenSecretKey)) {
            throw new GXBusinessException("Token secret key must not be blank");
        }
        return getLoginFieldFromToken(tokenName, GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, targetClass, tokenSecretKey);
    }

    default <R> @Nullable R getFrontEndUserId(String tokenSecretKey, Class<R> targetClass) {
        return getFrontEndUserId(GXTokenConstant.TOKEN_NAME, tokenSecretKey, targetClass);
    }

    default <R> @Nullable R getManagerUserId(String tokenName, String tokenSecretKey, Class<R> targetClass) {
        if (CharSequenceUtil.isBlank(tokenSecretKey)) {
            throw new GXBusinessException("Token secret key must not be blank");
        }
        return getLoginFieldFromToken(tokenName, GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, targetClass, tokenSecretKey);
    }

    default <R> @Nullable R getManagerUserId(String tokenSecretKey, Class<R> targetClass) {
        return getManagerUserId(GXTokenConstant.TOKEN_NAME, tokenSecretKey, targetClass);
    }

    default Dict getLoginCredentials(String tokenName, String tokenSecretKey) {
        return GXCurrentRequestContextUtils.getLoginCredentials(tokenName, tokenSecretKey);
    }

    default String concatAssertMsg(String msg, String fieldName) {
        return CharSequenceUtil.format("{}{}{}", fieldName, StrPool.COLON, msg);
    }
}
