package cn.maple.core.framework.dto;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.convert.GXCGLibDataConvert;
import cn.maple.core.framework.util.GXCommonUtils;
import org.springframework.util.ClassUtils;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class GXBaseData implements Serializable {

    protected void customizeProcess(Dict data) {
    }

    protected void verify() {
    }

    @SuppressWarnings("unchecked")
    protected <E> List<E> convertJsonArrayToTarget(String jsonArray, Class<E> targetClass) {
        if (targetClass == null) {
            return Collections.emptyList();
        }
        if (JSONUtil.isTypeJSONArray(jsonArray)) {
            Class<?> targetArrayClass = Array.newInstance(targetClass, 0).getClass();
            Object convertedArray = GXCGLibDataConvert.getConverter(targetClass).convert(jsonArray, targetArrayClass, null);
            if (convertedArray != null && convertedArray.getClass().isArray()) {
                Class<?> resultElementClass = ClassUtils.resolvePrimitiveIfNecessary(targetClass);
                int length = Array.getLength(convertedArray);
                List<E> result = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    result.add((E) resultElementClass.cast(Array.get(convertedArray, i)));
                }
                return result;
            }
        }
        return Collections.emptyList();
    }

    protected <E> E convertJsonObjectToTarget(String jsonObject, Class<E> targetClass) {
        if (targetClass == null) {
            return null;
        }
        if (JSONUtil.isTypeJSONObject(jsonObject)) {
            E target = GXCommonUtils.convertStrToTarget(jsonObject, targetClass);
            if (target != null) {
                return target;
            }
        }
        return GXCommonUtils.getClassDefaultValue(targetClass);
    }
}
