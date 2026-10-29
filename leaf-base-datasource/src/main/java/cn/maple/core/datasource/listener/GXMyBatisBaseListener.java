package cn.maple.core.datasource.listener;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.TypeUtil;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerTaskContext;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.event.*;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.Introspector;
import java.lang.reflect.Type;

/**
 * Shared dispatch logic for MyBatis CRUD events.
 *
 * <p>By default each callback is isolated: a failing listener is logged and counted, the remaining
 * listeners and the business write operation are unaffected. With {@code propagate-exception=true}
 * the original exception is surfaced to the business caller as-is and subsequent listeners stop.
 * Deferred deliveries (asynchronous, or synchronous with {@code sync-after-commit=true}) are always
 * isolated regardless of the switch, because the business caller is already gone and a callback
 * failure can never terminate the executor thread (contracts/listener-callback.md).</p>
 */
@SuppressWarnings("unchecked")
interface GXMyBatisBaseListener {
    Logger LOG = LoggerFactory.getLogger(GXMyBatisBaseListener.class);

    default void listenerSaveEntity(GXMyBatisModelSaveEntityEvent<Dict> saveEntityEvent) {
        dispatch("saveEntity", saveEntityEvent, () -> {
            Dict source = saveEntityEvent.getSource();
            Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(saveEntityEvent.getParam());
            GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);
            listener.saveEntityListener(convertEntityPayload(listenerClazz, source));
        });
    }

    default void listenerUpdateEntity(GXMyBatisModelUpdateEntityEvent<Dict> updateEntityEvent) {
        dispatch("updateEntity", updateEntityEvent, () -> {
            Dict source = updateEntityEvent.getSource();
            Class<? extends GXMybatisListenerService<?>> listenerClazz = resolveListenerClass(updateEntityEvent.getParam());
            GXMybatisListenerService<Object> listener = resolveListenerBean(listenerClazz);
            listener.updateEntityListener(
                    convertEntityPayload(listenerClazz, source.get(GXMyBatisEventConstant.ENTITY_DATA)),
                    convertToDict(source.get(GXMyBatisEventConstant.KEY_VALUE_PAIRS)),
                    convertToDict(source.get(GXMyBatisEventConstant.KEY_OPERATOR_PAIRS)),
                    source.getStr(GXMyBatisEventConstant.RAW_WHERE_SQL));
        });
    }

    default void listenerUpdateField(GXMyBatisModelUpdateFieldEvent<Dict> updateFieldEvent) {
        dispatch("updateField", updateFieldEvent, () -> {
            Dict source = updateFieldEvent.getSource();
            GXMybatisListenerService<Object> listener = resolveListener(updateFieldEvent.getParam());
            listener.updateFieldListener(convertToDict(source.getObj(GXMyBatisEventConstant.UPDATE_FIELD_DATA)),
                    convertToDict(source.get(GXMyBatisEventConstant.CONDITION_FIELD_DATA)));
        });
    }

    default void listenerDeleteSoft(GXMyBatisModelDeleteSoftEvent<Dict> deleteSoftEvent) {
        dispatch("deleteSoft", deleteSoftEvent, () -> {
            Dict source = deleteSoftEvent.getSource();
            GXMybatisListenerService<Object> listener = resolveListener(deleteSoftEvent.getParam());
            listener.deleteSoftListener(convertToDict(source));
        });
    }

    default void listenerDelete(GXMyBatisModelDeleteEvent<Dict> deleteEvent) {
        dispatch("delete", deleteEvent, () -> {
            Dict source = deleteEvent.getSource();
            GXMybatisListenerService<Object> listener = resolveListener(deleteEvent.getParam());
            listener.deleteListener(convertToDict(source));
        });
    }

    default void listenerSaveBatch(GXMyBatisModelSaveBatchEntityEvent<Dict> saveBatchEntityEvent) {
        dispatch("saveBatch", saveBatchEntityEvent, () -> {
            Dict source = saveBatchEntityEvent.getSource();
            GXMybatisListenerService<Object> listener = resolveListener(saveBatchEntityEvent.getParam());
            listener.saveBatchListener(convertToDict(source));
        });
    }

    default void listenerBatchChange(GXMyBatisModelSaveBatchEntityEvent<Dict> batchChangeEvent) {
        dispatch("batchChange", batchChangeEvent, () -> {
            Dict source = batchChangeEvent.getSource();
            GXMybatisListenerService<Object> listener = resolveListener(batchChangeEvent.getParam());
            listener.batchChangeListener(convertToDict(source));
        });
    }

    private void dispatch(String action, GXBaseEvent<Dict> event, Runnable task) {
        // Count the nesting depth around every callback so a listener that writes through an
        // annotated mapper cannot recurse without bound (spec edge case "递归触发"); the publisher
        // refuses deliveries beyond GXMyBatisListenerEventPublisher.MAX_DELIVERY_DEPTH.
        GXMyBatisListenerTaskContext.enterDelivery();
        try {
            task.run();
        } catch (RuntimeException exception) {
            // Callback failures are always counted and logged exactly once here, in both switch
            // modes (T024); the publish layer must not count them again.
            GXMyBatisListenerMetrics.recordFailed();
            Dict param = event.getParam();
            boolean hasParam = ObjectUtil.isNotNull(param);
            String listenerName = hasParam ? param.getStr(GXMyBatisEventConstant.LISTENER_CLAZZ_NAME) : null;
            String operationId = hasParam ? param.getStr(GXMyBatisEventConstant.OPERATION_ID) : null;
            boolean propagate = hasParam
                    && Boolean.TRUE.equals(param.getBool(GXMyBatisEventConstant.PROPAGATE_EXCEPTION));
            boolean asyncDelivery = isAsyncDelivery(event, param);
            boolean deferredDelivery = hasParam
                    && Boolean.TRUE.equals(param.getBool(GXMyBatisEventConstant.DEFERRED_DELIVERY));
            LOG.error("MyBatis listener callback failed: action={}, eventType={}, mode={}, operationId={}, listener={}, reason={}",
                    action, event.getEventType(), asyncDelivery ? "async" : "sync", operationId,
                    listenerName, exception.getMessage(), exception);
            if (!propagate || asyncDelivery || deferredDelivery) {
                // Isolated: the failure is already counted and logged. Deferred deliveries
                // (asynchronous, or synchronous with sync-after-commit) stay isolated even when
                // propagating because the business caller is already gone and an escaping exception
                // would terminate the executor thread (contracts/listener-callback.md).
                return;
            }
            if (!hasParam) {
                throw exception;
            }
            // Surface as-is via the publish layer: stashing the original exception keeps the failure
            // accounting exact (callback failures counted here, delivery failures there) while the
            // business caller still observes it and the publish loop stops (T077).
            param.set(GXMyBatisEventConstant.PENDING_CALLBACK_FAILURE, exception);
        } finally {
            GXMyBatisListenerTaskContext.exitDelivery();
        }
    }

    /**
     * Resolves whether the delivery is asynchronous. The explicit param key wins; when it is absent
     * (events published outside the framework publisher) the event type decides, so an unknown
     * delivery is never misclassified as synchronous and can never propagate an exception onto an
     * executor thread (contracts/listener-callback.md).
     */
    private boolean isAsyncDelivery(GXBaseEvent<Dict> event, @Nullable Dict param) {
        if (!ObjectUtil.isNull(param)) {
            Boolean declared = param.getBool(GXMyBatisEventConstant.ASYNC_DELIVERY);
            if (!ObjectUtil.isNull(declared)) {
                return declared;
            }
        }
        return CharSequenceUtil.startWith(event.getEventType(),
                GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT + "_");
    }

    private Class<? extends GXMybatisListenerService<?>> resolveListenerClass(@Nullable Dict param) {
        Object listenerClazzValue = ObjectUtil.isNull(param)
                ? null : param.getObj(GXMyBatisEventConstant.LISTENER_CLAZZ);
        Class<? extends GXMybatisListenerService<?>> listenerClazz =
                Convert.convert(new TypeReference<>() {
                }, listenerClazzValue);
        if (ObjectUtil.isNull(listenerClazz)) {
            throw new GXBusinessException("MyBatis listener class is missing");
        }
        return listenerClazz;
    }

    /**
     * Resolves the listener bean for a delivery, for callbacks that do not need the listener type.
     */
    private GXMybatisListenerService<Object> resolveListener(@Nullable Dict param) {
        return resolveListenerBean(resolveListenerClass(param));
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
