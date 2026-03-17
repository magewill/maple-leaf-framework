package cn.maple.core.datasource.listener;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.TypeUtil;
import cn.maple.core.datasource.event.*;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;

import java.lang.reflect.Type;

/**
 * MyBatis事件监听器基础接口
 * <p>
 * 该接口定义了处理MyBatis操作事件的基本方法，包括实体保存、更新、字段更新、软删除和批量保存等操作的事件监听。
 * 实现类可以选择同步或异步方式处理这些事件，分别通过{@link GXMyBatisSyncListener}和{@link GXMyBatisAsyncListener}实现。
 * <p>
 *
 * @author 塵渊 britton@126.com
 */
@SuppressWarnings("all")
interface GXMyBatisBaseListener {
    /**
     * 监听保存实体(Entity)事件
     * <p>
     * 该方法处理实体保存后触发的事件，通过反射调用目标监听器的saveEntityListener方法。
     * </p>
     *
     * @param saveEntityEvent 保存实体事件对象，包含已保存的实体数据
     * @throws GXBusinessException 如果反射调用失败或类型转换异常
     */
    default void listenerSaveEntity(GXMyBatisModelSaveEntityEvent<Dict> saveEntityEvent) {
        Dict source = saveEntityEvent.getSource();
        Dict param = saveEntityEvent.getParam();
        String listenerClazzName = CharSequenceUtil.lowerFirst(param.getStr("listenerClazzName"));
        Class<?> listenerClazz = Convert.convert(new TypeReference<>() {
        }, param.getObj("listenerClazz"));
        Type targetParamType = TypeUtil.getTypeArgument(listenerClazz.getGenericInterfaces()[0]);
        Object targetParamObject = Convert.convert(targetParamType, source);
        Object bean = GXSpringContextUtils.getBean(listenerClazzName, listenerClazz);
        GXCommonUtils.reflectCallObjectMethod(bean, "saveEntityListener", targetParamObject);
    }

    /**
     * 监听更新实体(Entity)事件
     * <p>
     * 该方法处理实体更新后触发的事件，通过反射调用目标监听器的updateEntityListener方法。
     * 处理流程：
     * 1. 从事件中获取源数据和参数
     * 2. 获取目标监听器类名和类对象
     * 3. 获取目标参数类型并进行类型转换
     * 4. 提取更新条件相关参数
     * 5. 通过Spring容器获取监听器实例
     * 6. 反射调用监听器的updateEntityListener方法，传入实体数据和条件参数
     *
     * @param updateEntityEvent 更新实体事件对象，包含已更新的实体数据和条件信息
     * @throws GXBusinessException 如果反射调用失败或类型转换异常
     */
    default void listenerUpdateEntity(GXMyBatisModelUpdateEntityEvent<Dict> updateEntityEvent) {
        Dict source = updateEntityEvent.getSource();
        Dict param = updateEntityEvent.getParam();
        String listenerClazzName = CharSequenceUtil.lowerFirst(param.getStr("listenerClazzName"));
        Class<?> listenerClazz = Convert.convert(new TypeReference<>() {
        }, param.getObj("listenerClazz"));
        Type targetParamType = TypeUtil.getTypeArgument(listenerClazz.getGenericInterfaces()[0]);
        Object targetParamObject = Convert.convert(targetParamType, source.get("entityData"));
        Object keyOperatorPairs = source.get("keyOperatorPairs");
        Object keyValuePairs = source.get("keyValuePairs");
        Object bean = GXSpringContextUtils.getBean(listenerClazzName, listenerClazz);
        GXCommonUtils.reflectCallObjectMethod(bean, "updateEntityListener", targetParamObject, keyValuePairs, keyOperatorPairs);
    }

    /**
     * 监听更新指定字段事件
     * <p>
     * 该方法处理字段更新后触发的事件，通过反射调用目标监听器的updateFieldListener方法。
     * 处理流程：
     * 1. 从事件中获取源数据和参数
     * 2. 获取目标监听器类名和类对象
     * 3. 通过Spring容器获取监听器实例
     * 4. 提取更新字段数据和条件字段数据
     * 5. 反射调用监听器的updateFieldListener方法，传入更新字段和条件字段数据
     *
     * @param updateFieldEvent 更新字段事件对象，包含已更新的字段数据和条件信息
     * @throws GXBusinessException 如果反射调用失败或类型转换异常
     */
    default void listenerUpdateField(GXMyBatisModelUpdateFieldEvent<Dict> updateFieldEvent) {
        Dict source = updateFieldEvent.getSource();
        Dict param = updateFieldEvent.getParam();
        String listenerClazzName = CharSequenceUtil.lowerFirst(param.getStr("listenerClazzName"));
        Class<?> listenerClazz = Convert.convert(new TypeReference<>() {
        }, param.getObj("listenerClazz"));
        Object bean = GXSpringContextUtils.getBean(listenerClazzName, listenerClazz);
        Dict updateFieldData = Convert.convert(Dict.class, source.getObj("updateFieldData"));
        Dict conditionFieldData = Convert.convert(Dict.class, source.get("conditionFieldData"));
        GXCommonUtils.reflectCallObjectMethod(bean, "updateFieldListener", updateFieldData, conditionFieldData);
    }

    /**
     * 监听软删除事件
     * <p>
     * 该方法处理软删除操作后触发的事件，通过反射调用目标监听器的deleteSoftListener方法。
     * 软删除通常是将记录标记为已删除状态，而非物理删除。
     * 处理流程：
     * 1. 从事件中获取源数据和参数
     * 2. 获取目标监听器类名和类对象
     * 3. 通过Spring容器获取监听器实例
     * 4. 反射调用监听器的deleteSoftListener方法，传入源数据
     *
     * @param deleteSoftEvent 软删除事件对象，包含删除条件信息
     * @throws GXBusinessException 如果反射调用失败或类型转换异常
     */
    default void listenerDeleteSoft(GXMyBatisModelDeleteSoftEvent<Dict> deleteSoftEvent) {
        Dict source = deleteSoftEvent.getSource();
        Dict param = deleteSoftEvent.getParam();
        String listenerClazzName = CharSequenceUtil.lowerFirst(param.getStr("listenerClazzName"));
        Class<?> listenerClazz = Convert.convert(new TypeReference<>() {
        }, param.getObj("listenerClazz"));
        Object bean = GXSpringContextUtils.getBean(listenerClazzName, listenerClazz);
        GXCommonUtils.reflectCallObjectMethod(bean, "deleteSoftListener", source);
    }

    /**
     * 监听批量新增与更新事件
     * <p>
     * 该方法处理批量保存操作后触发的事件，通过反射调用目标监听器的saveBatchListener方法。
     * 处理流程：
     * 1. 从事件中获取源数据和参数
     * 2. 获取目标监听器类名和类对象
     * 3. 通过Spring容器获取监听器实例
     * 4. 反射调用监听器的saveBatchListener方法，传入源数据
     *
     * @param saveBatchEntityEvent 批量保存事件对象，包含已保存的实体集合数据
     * @throws GXBusinessException 如果反射调用失败或类型转换异常
     */
    default void listenerSaveBatch(GXMyBatisModelSaveBatchEntityEvent<Dict> saveBatchEntityEvent) {
        Dict source = saveBatchEntityEvent.getSource();
        Dict param = saveBatchEntityEvent.getParam();
        String listenerClazzName = CharSequenceUtil.lowerFirst(param.getStr("listenerClazzName"));
        Class<?> listenerClazz = Convert.convert(new TypeReference<>() {
        }, param.getObj("listenerClazz"));
        Object bean = GXSpringContextUtils.getBean(listenerClazzName, listenerClazz);
        GXCommonUtils.reflectCallObjectMethod(bean, "saveBatchListener", source);
    }
}
