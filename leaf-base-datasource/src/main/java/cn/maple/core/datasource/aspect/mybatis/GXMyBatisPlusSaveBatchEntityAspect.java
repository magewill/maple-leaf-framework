package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.event.GXMyBatisModelSaveBatchEntityEvent;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

/**
 * 批量保存实体(Entity)切面类
 * <p>
 * 该切面用于拦截ServiceImpl接口的saveBatch和saveOrUpdateBatch方法调用，实现批量保存或更新操作的事件发布。
 * 该切面会在批量保存或更新操作执行后，根据Mapper上的GXMyBatisListener注解配置发布相应的同步或异步事件。
 * 内存安全考虑：事件发布后不保留对原始数据的引用，确保GC能正常回收不再使用的对象。
 */
@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusSaveBatchEntityAspect {
    /**
     * 拦截ServiceImpl接口的saveBatch和saveOrUpdateBatch方法调用
     * <p>
     * 该方法在目标方法执行前后进行拦截，并在方法执行后发布批量保存或更新事件。
     * 采用环绕通知模式，确保在原始方法执行完成后再发布事件，保证数据一致性。
     *
     * @param point 切点对象，包含被拦截的方法信息和参数
     * @return 原始方法的返回值
     * @throws Throwable 执行过程中可能抛出的异常
     */
    @Around("target(com.baomidou.mybatisplus.extension.service.impl.ServiceImpl) && (execution(* saveBatch(..)) || execution(* saveOrUpdateBatch(..)))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        log.debug("发布批量更新前的事件开始");
        Object proceed = point.proceed();
        publishEvent(point);
        log.debug("发布批量更新后的事件结束");
        return proceed;
    }


    /**
     * 处理切点的参数，提取批量保存或更新的实体数据
     * <p>
     * 该方法从切点中提取批量保存或更新的实体数据，并转换为Dict格式。
     * 内存安全考虑：使用Dict对象存储数据，避免直接操作原始对象引用，防止内存泄漏。
     *
     * @param type  Mapper接口类型
     * @param point 切点对象，包含方法参数信息
     * @return Dict 包含实体数据的字典对象
     */
    private Dict handlePointArgs(Type type, ProceedingJoinPoint point) {
        Dict retDict = Dict.create();
        Class<Mapper> mapper = convertTypeToMapper(type);
        if (ObjectUtil.isNotNull(mapper)) {
            Object[] args = point.getArgs();
            Collection<Object> entity = (Collection<Object>) args[0];
            Collection<Dict> entityData = Convert.convert(new TypeReference<List>() {
            }, entity);
            retDict.set("entityData", entityData);
        }
        return retDict;
    }

    /**
     * 发布批量保存或更新事件
     * <p>
     * 根据Mapper上的GXMyBatisListener注解配置，发布同步或异步的批量保存或更新事件。
     * 事件中包含批量保存或更新的实体数据，以及监听器类信息。
     * 内存安全考虑：
     * 1. 使用局部变量存储中间结果，避免跨方法引用导致的内存泄漏
     * 2. 事件发布后不保留对原始数据的引用，确保GC能正常回收不再使用的对象
     *
     * @param point 切点对象，包含被拦截的方法信息和参数
     */
    private void publishEvent(ProceedingJoinPoint point) {
        Type myBatisMapper = GXCommonUtils.getGenericClassType(AopUtils.getTargetClass(point.getTarget()), 0);//AopUtils.getTargetClass(point.getTarget()).getInterfaces();
        Class<Mapper> mapper = convertTypeToMapper(myBatisMapper);
        if (ObjectUtil.isNotNull(mapper)) {
            GXMyBatisListener myBatisListener = AnnotationUtil.getAnnotation(mapper, GXMyBatisListener.class);
            if (ObjectUtil.isNull(myBatisListener)) {
                return;
            }
            Dict source = handlePointArgs(myBatisMapper, point);
            Class<? extends GXMybatisListenerService> aClass = myBatisListener.listenerClazz();
            String eventType = GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY.getEventType();
            String eventName = GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY.getEventName();
            Dict eventParam = Dict.create().set("listenerClazzName", aClass.getSimpleName()).set("listenerClazz", aClass);
            String runType = myBatisListener.runType();
            if (runType.equals(GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                eventType = GXModelEventNamingEnums.ASYNC_SAVE_BATCH_ENTITY.getEventType();
                eventName = GXModelEventNamingEnums.ASYNC_SAVE_BATCH_ENTITY.getEventName();
            }
            GXMyBatisModelSaveBatchEntityEvent<Dict> updateEntityEvent = new GXMyBatisModelSaveBatchEntityEvent<>(source, eventType, eventParam, eventName);
            GXEventPublisherUtils.publishEvent(updateEntityEvent);
        }
    }

    /**
     * 将Type转换为Mapper接口类
     * <p>
     * 使用Hutool工具类的Convert进行类型转换，将Type对象转换为Mapper接口类。
     * 内存安全考虑：使用TypeReference进行泛型转换，避免类型擦除问题，确保类型安全。
     *
     * @param type 待转换的Type对象
     * @return Mapper接口类，如果转换失败则可能返回null
     */
    private Class<Mapper> convertTypeToMapper(Type type) {
        return Convert.convert(new TypeReference<>() {
        }, type);
    }
}
