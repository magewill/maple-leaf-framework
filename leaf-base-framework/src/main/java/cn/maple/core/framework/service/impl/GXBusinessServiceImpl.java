package cn.maple.core.framework.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.core.framework.util.GXCommonUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class GXBusinessServiceImpl implements GXBusinessService {
    @Override
    public String encryptedPhoneNumber(String phoneNumber, String key) {
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("Phone encryption key must not be empty");
        }
        Dict data = Dict.create().set("phone", phoneNumber);
        return GXCommonUtils.encryptedData(data, key, 0);
    }

    @Override
    public String decryptedPhoneNumber(String encryptPhoneNumber, String key) {
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("Phone decryption key must not be empty");
        }
        Dict data = GXCommonUtils.decryptedData(encryptPhoneNumber, key);
        return data.getStr("phone");
    }

    @Override
    public String hiddenPhoneNumber(CharSequence phoneNumber, int startInclude, int endExclude, char replacedChar) {
        return GXCommonUtils.hiddenPhoneNumber(phoneNumber, startInclude, endExclude, replacedChar);
    }

    @Override
    public <T, R> R getSingleFieldValueByEntity(T entity, String path, Class<R> type) {
        return getSingleFieldValueByEntity(entity, path, type, GXCommonUtils.getClassDefaultValue(type));
    }

    @Override
    public <T, R> R getSingleFieldValueByEntity(T entity, String path, Class<R> type, R defaultValue) {
        if (entity == null || CharSequenceUtil.isBlank(path) || type == null) {
            return defaultValue;
        }

        JSON json = JSONUtil.parse(JSONUtil.toJsonStr(entity));
        if (json == null) {
            return defaultValue;
        }

        int index = CharSequenceUtil.indexOfIgnoreCase(path, "::");
        if (index == -1) {
            Object value = json.getByPath(path);
            if (value == null) {
                return defaultValue;
            }
            if (JSONUtil.isTypeJSON(value.toString())) {
                Dict data = Dict.create();
                Dict dict = JSONUtil.toBean(value.toString(), Dict.class);
                if (dict != null && !dict.isEmpty()) {
                    for (Map.Entry<String, Object> entry : dict.entrySet()) {
                        data.set(entry.getKey(), entry.getValue());
                    }
                }
                return Convert.convert(type, data, defaultValue);
            }
            return Convert.convert(type, value, defaultValue);
        }

        String mainField = CharSequenceUtil.sub(path, 0, index);
        Object mainFieldValue = json.getByPath(mainField);
        if (mainFieldValue == null) {
            throw new GXBusinessException(CharSequenceUtil.format("Entity main field {} does not exist", mainField));
        }
        String subField = CharSequenceUtil.sub(path, index + 2, path.length());
        JSON parse = JSONUtil.parse(mainFieldValue);
        if (parse == null) {
            return defaultValue;
        }
        return Convert.convert(type, parse.getByPath(subField), defaultValue);
    }

    @Override
    public <S, T> T convertSourceToTarget(S source, Class<T> tClass) {
        return convertSourceToTarget(source, tClass, GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME, null);
    }

    @Override
    public <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions, Dict extraData) {
        return GXCommonUtils.convertSourceToTarget(source, tClass, methodName, copyOptions, extraData);
    }

    @Override
    public <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions) {
        return convertSourceToTarget(source, tClass, methodName, copyOptions, Dict.create());
    }

    @Override
    public <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass, String methodName, CopyOptions copyOptions, Dict extraData) {
        return GXCommonUtils.convertSourceListToTargetList(collection, tClass, methodName, copyOptions, extraData);
    }
}
