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
import cn.maple.core.datasource.event.GXMyBatisModelDeleteSoftEvent;
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
 * 软删除数据切面类
 */
@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusDeleteSoftAspect {
    @Around("target(cn.maple.core.datasource.mapper.GXBaseMapper) && execution(* deleteSoftCondition(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        log.debug("发布软删除事件开始");
        Object proceed = point.proceed();
        publishEvent(point);
        log.debug("发布软删除事件结束");
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
            GXBaseQueryParamInnerDto baseQueryParamInnerDto = Convert.convert(new TypeReference<>() {
            }, args[0]);
            List<GXUpdateField<?>> updateFieldList = Convert.convert(new TypeReference<>() {
            }, args[1]);
            List<GXCondition<?>> conditionList = baseQueryParamInnerDto.getCondition();
            Dict conditionFieldData = Dict.create();
            conditionList.forEach(condition -> {
                Object fieldValue = condition.getFieldValue();
                String fieldName = condition.getFieldExpression();
                conditionFieldData.set(CharSequenceUtil.toCamelCase(fieldName), fieldValue);
            });
            Dict updateFieldData = Dict.create();
            updateFieldList.forEach(updateField -> {
                Object fieldValue = updateField.getFieldValue();
                String fieldName = updateField.getFieldName();
                updateFieldData.set(CharSequenceUtil.toCamelCase(fieldName), fieldValue);
            });
            retDict.set("conditionFieldData", conditionFieldData).set("updateFieldData", updateFieldData);
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
                String eventType = GXModelEventNamingEnums.SYNC_DELETE_SOFT.getEventType();
                String eventName = GXModelEventNamingEnums.SYNC_DELETE_SOFT.getEventName();
                String runType = myBatisListener.runType();
                if (runType.equals(GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                    eventType = GXModelEventNamingEnums.ASYNC_DELETE_SOFT.getEventType();
                    eventName = GXModelEventNamingEnums.ASYNC_DELETE_SOFT.getEventName();
                }
                Dict eventParam = Dict.create().set("listenerClazzName", aClass.getSimpleName()).set("listenerClazz", aClass);
                GXMyBatisModelDeleteSoftEvent<Dict> deleteSoftEvent = new GXMyBatisModelDeleteSoftEvent<>(source, eventType, eventParam, eventName);
                GXEventPublisherUtils.publishEvent(deleteSoftEvent);
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
