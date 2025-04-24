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
 * <p>
 * 该切面用于拦截GXBaseMapper接口的deleteSoftCondition方法调用，实现软删除操作的事件发布。
 * 软删除是指通过更新标记字段（如is_deleted=1）而非物理删除数据的操作方式。
 * 该切面会在软删除操作执行后，根据Mapper上的GXMyBatisListener注解配置发布相应的同步或异步事件。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在Mapper接口上添加监听器注解
 * @GXMyBatisListener(listenerClazz = UserDeleteListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
 * public interface UserMapper extends GXBaseMapper<UserEntity> {
 *     // 接口方法...
 * }
 * 
 * // 2. 实现监听器处理软删除事件
 * @Component
 * public class UserDeleteListener implements GXMybatisListenerService {
 *     @EventListener(condition = "#event.eventName == 'SYNC_DELETE_SOFT'")
 *     public void handleSoftDeleteEvent(GXMyBatisModelDeleteSoftEvent<Dict> event) {
 *         Dict source = event.getSource();
 *         Dict conditionFieldData = source.getDict("conditionFieldData");
 *         Dict updateFieldData = source.getDict("updateFieldData");
 *         // 处理软删除事件...
 *     }
 * }
 * 
 * // 3. 调用软删除方法
 * @Service
 * public class UserServiceImpl implements UserService {
 *     @Autowired
 *     private UserMapper userMapper;
 *     
 *     public void softDeleteUser(Long userId) {
 *         GXBaseQueryParamInnerDto queryParam = new GXBaseQueryParamInnerDto();
 *         queryParam.addCondition(new GXCondition<>("id", userId));
 *         
 *         List<GXUpdateField<?>> updateFields = new ArrayList<>();
 *         updateFields.add(new GXUpdateField<>("is_deleted", 1));
 *         updateFields.add(new GXUpdateField<>("deleted_at", new Date()));
 *         
 *         userMapper.deleteSoftCondition(queryParam, updateFields);
 *         // 此时会自动触发软删除事件
 *     }
 * }
 * </pre>
 * 
 * <p>工作原理：</p>
 * <ol>
 *   <li>通过AOP拦截GXBaseMapper接口的deleteSoftCondition方法调用</li>
 *   <li>执行原始的软删除操作</li>
 *   <li>提取软删除操作的条件和更新字段信息</li>
 *   <li>根据Mapper上的GXMyBatisListener注解配置发布相应的事件</li>
 *   <li>监听器可以监听并处理这些事件，执行额外的业务逻辑</li>
 * </ol>
 * 
 * <p>内存安全：</p>
 * <ol>
 *   <li>使用Dict对象存储数据，避免直接操作原始对象引用，防止内存泄漏</li>
 *   <li>使用局部变量存储中间结果，避免跨方法引用导致的内存泄漏</li>
 *   <li>事件发布后不保留对原始数据的引用，确保GC能正常回收不再使用的对象</li>
 * </ol>
 */
@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusDeleteSoftAspect {
    /**
     * 拦截GXBaseMapper接口的deleteSoftCondition方法调用
     * <p>
     * 该方法在目标方法执行前后进行拦截，并在方法执行后发布软删除事件。
     * 采用环绕通知模式，确保在原始方法执行完成后再发布事件，保证数据一致性。
     *
     * @param point 切点对象，包含被拦截的方法信息和参数
     * @return 原始方法的返回值
     * @throws Throwable 执行过程中可能抛出的异常
     */
    @Around("target(cn.maple.core.datasource.mapper.GXBaseMapper) && execution(* deleteSoftCondition(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        log.debug("发布软删除事件开始");
        Object proceed = point.proceed();
        publishEvent(point);
        log.debug("发布软删除事件结束");
        return proceed;
    }

    /**
     * 处理切点的参数，提取软删除操作的条件和更新字段信息
     * <p>
     * 该方法从切点中提取软删除操作的条件和更新字段信息，并转换为Dict格式。
     * 处理过程中会将字段名转换为驼峰格式，确保字段名的一致性。
     * 内存安全考虑：使用Dict对象存储数据，避免直接操作原始对象引用，防止内存泄漏。
     *
     * @param type  Mapper接口类型
     * @param point 切点对象，包含方法参数信息
     * @return Dict 包含条件字段数据和更新字段数据的字典对象
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
     * 发布软删除事件
     * <p>
     * 根据Mapper上的GXMyBatisListener注解配置，发布同步或异步的软删除事件。
     * 事件中包含软删除操作的条件和更新字段信息，以及监听器类信息。
     * 内存安全考虑：
     * 1. 使用局部变量存储中间结果，避免跨方法引用导致的内存泄漏
     * 2. 事件发布后不保留对原始数据的引用，确保GC能正常回收不再使用的对象
     * 3. 避免重复发布事件，提高系统性能
     * </p>
     *
     * @param point 切点对象，包含被拦截的方法信息和参数
     */
    private void publishEvent(ProceedingJoinPoint point) {
        try {
            // 获取目标对象的Mapper接口类型
            Type[] myBatisMapper = AopUtils.getTargetClass(point.getTarget()).getInterfaces();
            boolean eventPublished = false; // 标记是否已发布事件，避免重复发布
            
            for (Type type : myBatisMapper) {
                // 转换为Mapper接口类
                Class<Mapper> mapper = convertTypeToMapper(type);
                if (ObjectUtil.isNull(mapper)) {
                    continue; // 转换失败，跳过当前接口
                }
                
                // 获取Mapper上的监听器注解
                GXMyBatisListener myBatisListener = AnnotationUtil.getAnnotation(mapper, GXMyBatisListener.class);
                if (ObjectUtil.isNull(myBatisListener)) {
                    log.debug("Mapper接口{}未配置GXMyBatisListener注解，跳过事件发布", mapper.getName());
                    continue; // 未配置监听器，跳过当前接口
                }
                
                // 如果已经发布过事件，避免重复发布
                if (eventPublished) {
                    log.debug("已为当前操作发布过事件，跳过重复发布");
                    break;
                }
                
                // 处理参数，提取软删除操作的条件和更新字段信息
                Dict source = handlePointArgs(type, point);
                if (source.isEmpty()) {
                    log.warn("提取软删除参数失败，跳过事件发布");
                    continue;
                }
                
                // 获取监听器类和事件类型
                Class<? extends GXMybatisListenerService> listenerClass = myBatisListener.listenerClazz();
                String eventType = GXModelEventNamingEnums.SYNC_DELETE_SOFT.getEventType();
                String eventName = GXModelEventNamingEnums.SYNC_DELETE_SOFT.getEventName();
                String runType = myBatisListener.runType();
                
                // 根据运行类型确定事件类型（同步/异步）
                if (runType.equals(GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                    eventType = GXModelEventNamingEnums.ASYNC_DELETE_SOFT.getEventType();
                    eventName = GXModelEventNamingEnums.ASYNC_DELETE_SOFT.getEventName();
                }
                
                // 创建事件参数
                Dict eventParam = Dict.create()
                    .set("listenerClazzName", listenerClass.getSimpleName())
                    .set("listenerClazz", listenerClass);
                
                // 创建并发布事件
                log.debug("发布{}事件，监听器: {}", eventName, listenerClass.getSimpleName());
                GXMyBatisModelDeleteSoftEvent<Dict> deleteSoftEvent = 
                    new GXMyBatisModelDeleteSoftEvent<>(source, eventType, eventParam, eventName);
                GXEventPublisherUtils.publishEvent(deleteSoftEvent);
                
                // 标记已发布事件
                eventPublished = true;
                log.debug("软删除事件发布成功");
            }
            
            if (!eventPublished) {
                log.debug("未找到合适的监听器配置，软删除事件未发布");
            }
        } catch (Exception e) {
            // 捕获并记录异常，但不影响原始方法的执行结果
            log.error("发布软删除事件时发生异常: {}", e.getMessage(), e);
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
