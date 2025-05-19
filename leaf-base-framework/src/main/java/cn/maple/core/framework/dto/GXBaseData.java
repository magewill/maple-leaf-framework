package cn.maple.core.framework.dto;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.util.GXCommonUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 基础数据传输对象抽象类
 * <p>
 * 该类为所有DTO对象的基础类，提供了通用的数据处理方法，包括自定义处理、参数校验和JSON转换等功能。
 * 所有需要在系统中传输的数据对象都应该继承此类或其子类。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * public class MyDataDto extends GXBaseData {
 *     private String name;
 *     private Integer age;
 *
 *     // 重写自定义处理方法
 *     @Override
 *     protected void customizeProcess(Dict data) {
 *         // 对数据进行自定义处理
 *         if (data.containsKey("fullName")) {
 *             this.name = data.getStr("fullName");
 *         }
 *     }
 *
 *     // 重写校验方法
 *     @Override
 *     protected void verify() {
 *         // 进行数据校验
 *         if (StringUtils.isBlank(name)) {
 *             throw new IllegalArgumentException("名称不能为空");
 *         }
 *     }
 * }
 * </pre>
 */
public abstract class GXBaseData implements Serializable {
    /**
     * 调用自定义的方法进行参数的处理
     * <p>
     * 子类可以重写此方法，实现对传入数据的自定义处理逻辑。
     * 例如，可以在此方法中进行数据转换、默认值设置、特殊业务规则处理等。
     * </p>
     *
     * @param data 需要处理的参数数据，通常包含请求中的原始数据
     * @author britton
     */
    protected void customizeProcess(Dict data) {
        // 默认实现为空，由子类根据需要重写
    }

    /**
     * 对请求参数进行补充校验
     * <p>
     * 子类可以重写此方法，实现对数据的校验逻辑。
     * 当数据不符合业务规则时，可以在此方法中抛出异常。
     * </p>
     *
     * @throws IllegalArgumentException 当数据校验失败时抛出
     * @author britton
     */
    protected void verify() {
        // 默认实现为空，由子类根据需要重写
    }

    /**
     * 将JSON数组字符串转换为指定类型的对象列表
     * <p>
     * 该方法用于将JSON数组格式的字符串安全地转换为Java对象列表。
     * 如果输入的字符串不是有效的JSON数组，则返回空列表。
     * </p>
     *
     * @param jsonArray   JSON数组字符串，必须是有效的JSON数组格式
     * @param targetClass 目标对象类型，转换后的列表元素类型
     * @param <E>         目标对象的泛型类型
     * @return 转换后的对象列表，如果转换失败则返回空列表
     */
    protected <E> List<E> convertJsonArrayToTarget(String jsonArray, Class<E> targetClass) {
        if (JSONUtil.isTypeJSONArray(jsonArray)) {
            return JSONUtil.toList(jsonArray, targetClass);
        }
        return Collections.emptyList();
    }

    /**
     * 将JSON对象字符串转换为指定类型的对象
     * <p>
     * 该方法用于将JSON对象格式的字符串安全地转换为Java对象。
     * 如果输入的字符串不是有效的JSON对象，则返回目标类型的默认值。
     * </p>
     *
     * @param jsonObject  JSON对象字符串，必须是有效的JSON对象格式
     * @param targetClass 目标对象类型
     * @param <E>         目标对象的泛型类型
     * @return 转换后的对象，如果转换失败则返回目标类型的默认值
     */
    protected <E> E convertJsonObjectToTarget(String jsonObject, Class<E> targetClass) {
        if (JSONUtil.isTypeJSONObject(jsonObject)) {
            return JSONUtil.toBean(jsonObject, targetClass);
        }
        return GXCommonUtils.getClassDefaultValue(targetClass);
    }
}
