package cn.maple.core.datasource.listener;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.TypeUtil;
import cn.maple.core.datasource.event.GXMyBatisModelDeleteSoftEvent;
import cn.maple.core.datasource.event.GXMyBatisModelSaveBatchEntityEvent;
import cn.maple.core.datasource.event.GXMyBatisModelSaveEntityEvent;
import cn.maple.core.datasource.event.GXMyBatisModelUpdateEntityEvent;
import cn.maple.core.datasource.event.GXMyBatisModelUpdateFieldEvent;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;

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
        Dict keyOperatorPairs = Convert.convert(Dict.class, source.get("keyOperatorPairs"));
        Dict keyValuePairs = Convert.convert(Dict.class, source.get("keyValuePairs"));

        listener.updateEntityListener(entityData, keyValuePairs, keyOperatorPairs);
    }

    default void listenerUpdateField(GXMyBatisModelUpdateFieldEvent<Dict> updateFieldEvent) {
        Dict source = updateFieldEvent.getSource();
        Dict param = updateFieldEvent.getParam();

        Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(param);
        GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);
        Dict updateFieldData = Convert.convert(Dict.class, source.getObj("updateFieldData"));
        Dict conditionFieldData = Convert.convert(Dict.class, source.get("conditionFieldData"));

        listener.updateFieldListener(updateFieldData, conditionFieldData);
    }

    default void listenerDeleteSoft(GXMyBatisModelDeleteSoftEvent<Dict> deleteSoftEvent) {
        Dict source = deleteSoftEvent.getSource();
        Dict param = deleteSoftEvent.getParam();

        Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(param);
        GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);

        listener.deleteSoftListener(source);
    }

    default void listenerSaveBatch(GXMyBatisModelSaveBatchEntityEvent<Dict> saveBatchEntityEvent) {
        Dict source = saveBatchEntityEvent.getSource();
        Dict param = saveBatchEntityEvent.getParam();

        Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(param);
        GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);

        listener.saveBatchListener(source);
    }

    private Class<? extends GXMybatisListenerService<?>> resolveListenerClass(Dict param) {
        Class<? extends GXMybatisListenerService<?>> listenerClazz =
                Convert.convert(new TypeReference<>() {}, param.getObj("listenerClazz"));
        if (ObjectUtil.isNull(listenerClazz)) {
            throw new GXBusinessException("MyBatis listener class is missing");
        }
        return listenerClazz;
    }

    private GXMybatisListenerService<Object> resolveListenerBean(Class<? extends GXMybatisListenerService<?>> listenerClazz) {
        String listenerBeanName = CharSequenceUtil.lowerFirst(listenerClazz.getSimpleName());
        Object bean = GXSpringContextUtils.getBean(listenerBeanName, listenerClazz);
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

    private Object convertEntityPayload(Class<? extends GXMybatisListenerService<?>> listenerClazz, Object source) {
        Type targetParamType = TypeUtil.getTypeArgument(listenerClazz, 0);
        if (ObjectUtil.isNull(targetParamType) || ObjectUtil.isNull(source)) {
            return source;
        }
        return Convert.convert(targetParamType, source);
    }
}
