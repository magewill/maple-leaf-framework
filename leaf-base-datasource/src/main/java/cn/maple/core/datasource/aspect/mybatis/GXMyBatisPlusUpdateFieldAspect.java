package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.event.GXMyBatisModelUpdateFieldEvent;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 更新指定字段切面类
 */
@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusUpdateFieldAspect {
    @Around("target(cn.maple.core.datasource.mapper.GXBaseMapper) && execution(* updateFieldByCondition(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        log.debug("发布更新数据库指定字段事件开始");
        Object proceed = point.proceed();
        publishEvent(point);
        log.debug("发布更新数据库指定字段事件结束");
        return proceed;
    }

    /**
     * 处理切点的参数
     *
     * @param type  类型
     * @param point 切点对象
     */
    private Dict handlePointArgs(Type type, ProceedingJoinPoint point) {
        Dict retDict = Dict.create();
        Class<Mapper> mapper = convertTypeToMapper(type);
        if (ObjectUtil.isNotNull(mapper)) {
            Object[] args = point.getArgs();
            List<GXUpdateField<?>> updateFieldList = Convert.convert(new TypeReference<>() {
            }, args[1]);
            Dict updateFieldData = Dict.create();
            updateFieldList.forEach(field -> {
                Object fieldValue = field.getFieldValue();
                String fieldName = field.getFieldName();
                if (CharSequenceUtil.isNotEmpty(fieldName)) {
                    updateFieldData.set(CharSequenceUtil.toCamelCase(fieldName), fieldValue);
                }
            });
            Dict conditionFieldData = Dict.create();
            GXBaseQueryParamInnerDto baseQueryParamInnerDto = Convert.convert(new TypeReference<>() {
            }, args[0]);
            List<GXCondition<?>> conditionList = baseQueryParamInnerDto.getCondition();
            conditionList.forEach(condition -> {
                Object fieldValue = condition.getFieldValue();
                String fieldName = condition.getFieldExpression();
                conditionFieldData.set(CharSequenceUtil.toCamelCase(fieldName), fieldValue);
            });
            retDict.set("updateFieldData", updateFieldData).set("conditionFieldData", conditionFieldData);
        }
        return retDict;
    }

    /**
     * 发布事件
     *
     * @param point 切点对象
     */
    private void publishEvent(ProceedingJoinPoint point) {
        Type[] myBatisMapper = AopUtils.getTargetClass(point.getTarget()).getInterfaces();
        for (Type type : myBatisMapper) {
            Class<Mapper> mapper = convertTypeToMapper(type);
            if (ObjectUtil.isNotNull(mapper)) {
                GXMyBatisListener myBatisListener = AnnotationUtil.getAnnotation(mapper, GXMyBatisListener.class);
                if (ObjectUtil.isNull(myBatisListener)) {
                    return;
                }
                Dict source = handlePointArgs(type, point);
                Class<? extends GXMybatisListenerService> aClass = myBatisListener.listenerClazz();
                String eventType = GXModelEventNamingEnums.SYNC_UPDATE_FIELD.getEventType();
                String eventName = GXModelEventNamingEnums.SYNC_UPDATE_FIELD.getEventName();
                String runType = myBatisListener.runType();
                if (runType.equals(GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                    eventType = GXModelEventNamingEnums.ASYNC_UPDATE_FIELD.getEventType();
                    eventName = GXModelEventNamingEnums.ASYNC_UPDATE_FIELD.getEventName();
                }
                Dict eventParam = Dict.create().set("listenerClazzName", aClass.getSimpleName()).set("listenerClazz", aClass);
                GXMyBatisModelUpdateFieldEvent<Dict> updateFieldEvent = new GXMyBatisModelUpdateFieldEvent<>(source, eventType, eventParam, eventName);
                GXEventPublisherUtils.publishEvent(updateFieldEvent);
            }
        }
    }

    /**
     * 将Type转换为Mapper
     *
     * @param type 待转换的对象
     */
    private Class<Mapper> convertTypeToMapper(Type type) {
        return Convert.convert(new TypeReference<>() {
        }, type);
    }
}
