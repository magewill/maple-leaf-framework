package cn.maple.core.datasource.listener;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.TypeUtil;
import cn.maple.core.datasource.event.*;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;

import java.beans.Introspector;
import java.lang.reflect.Type;

/**
 * Shared dispatch logic for MyBatis CRUD events.
 */
@SuppressWarnings("unchecked")
interface GXMyBatisBaseListener {
    default void listenerSaveEntity(GXMyBatisModelSaveEntityEvent<Dict> saveEntityEvent) {
        Dict source = saveEntityEvent.getSource();
        Dict param = saveEntityEvent.getParam();

        Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(param);
        GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);
        Object targetParamObject = convertEntityPayload(listenerClazz, source);

        listener.saveEntityListener(targetParamObject);
    }

    default void listenerUpdateEntity(GXMyBatisModelUpdateEntityEvent<Dict> updateEntityEvent) {
        Dict source = updateEntityEvent.getSource();
        Dict param = updateEntityEvent.getParam();

        Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(param);
        GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);
        Object entityData = convertEntityPayload(listenerClazz, source.get("entityData"));
        Dict keyOperatorPairs = convertToDict(source.get("keyOperatorPairs"));
        Dict keyValuePairs = convertToDict(source.get("keyValuePairs"));

        listener.updateEntityListener(entityData, keyValuePairs, keyOperatorPairs, source.getStr("rawWhereSql"));
    }

    default void listenerUpdateField(GXMyBatisModelUpdateFieldEvent<Dict> updateFieldEvent) {
        Dict source = updateFieldEvent.getSource();
        Dict param = updateFieldEvent.getParam();

        Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(param);
        GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);
        Dict updateFieldData = convertToDict(source.getObj("updateFieldData"));
        Dict conditionFieldData = convertToDict(source.get("conditionFieldData"));

        listener.updateFieldListener(updateFieldData, conditionFieldData);
    }

    default void listenerDeleteSoft(GXMyBatisModelDeleteSoftEvent<Dict> deleteSoftEvent) {
        Dict source = deleteSoftEvent.getSource();
        Dict param = deleteSoftEvent.getParam();

        Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(param);
        GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);

        listener.deleteSoftListener(convertToDict(source));
    }

    default void listenerDelete(GXMyBatisModelDeleteEvent<Dict> deleteEvent) {
        Dict source = deleteEvent.getSource();
        Dict param = deleteEvent.getParam();

        Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(param);
        GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);

        listener.deleteListener(convertToDict(source));
    }

    default void listenerSaveBatch(GXMyBatisModelSaveBatchEntityEvent<Dict> saveBatchEntityEvent) {
        Dict source = saveBatchEntityEvent.getSource();
        Dict param = saveBatchEntityEvent.getParam();

        Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(param);
        GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);

        listener.saveBatchListener(convertToDict(source));
    }

    default void listenerBatchChange(GXMyBatisModelSaveBatchEntityEvent<Dict> batchChangeEvent) {
        Dict source = batchChangeEvent.getSource();
        Dict param = batchChangeEvent.getParam();

        Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(param);
        GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);

        listener.batchChangeListener(convertToDict(source));
    }

    private Class<? extends GXMybatisListenerService<?>> resolveListenerClass(Dict param) {
        Object listenerClazzValue = ObjectUtil.isNull(param) ? null : param.getObj("listenerClazz");
        Class<? extends GXMybatisListenerService<?>> listenerClazz =
                Convert.convert(new TypeReference<>() {
                }, listenerClazzValue);
        if (ObjectUtil.isNull(listenerClazz)) {
            throw new GXBusinessException("MyBatis listener class is missing");
        }
        return listenerClazz;
    }

    private GXMybatisListenerService<Object> resolveListenerBean(Class<? extends GXMybatisListenerService<?>> listenerClazz) {
        String listenerBeanName = Introspector.decapitalize(listenerClazz.getSimpleName());
        Object bean = GXSpringContextUtils.getBean(listenerBeanName, listenerClazz);
        if (ObjectUtil.isNull(bean)) {
            String lowerFirstBeanName = CharSequenceUtil.lowerFirst(listenerClazz.getSimpleName());
            bean = GXSpringContextUtils.getBean(lowerFirstBeanName, listenerClazz);
        }
        if (ObjectUtil.isNull(bean)) {
            bean = GXSpringContextUtils.getBean(listenerBeanName);
        }
        if (ObjectUtil.isNull(bean)) {
            bean = GXSpringContextUtils.getBean(listenerClazz);
        }
        if (ObjectUtil.isNull(bean)) {
            throw new GXBusinessException("MyBatis listener bean not found: " + listenerClazz.getName());
        }
        if (!(bean instanceof GXMybatisListenerService<?>)) {
            throw new GXBusinessException("MyBatis listener bean type mismatch: " + listenerClazz.getName());
        }
        return (GXMybatisListenerService<Object>) bean;
    }

    private Dict convertToDict(Object source) {
        if (ObjectUtil.isNull(source)) {
            return Dict.create();
        }
        Dict dict = Convert.convert(Dict.class, source);
        return ObjectUtil.defaultIfNull(dict, Dict.create());
    }

    private Object convertEntityPayload(Class<? extends GXMybatisListenerService<?>> listenerClazz, Object source) {
        Type targetParamType = TypeUtil.getTypeArgument(listenerClazz, 0);
        if (ObjectUtil.isNull(targetParamType) || ObjectUtil.isNull(source)) {
            return source;
        }
        return Convert.convert(targetParamType, source);
    }
}
