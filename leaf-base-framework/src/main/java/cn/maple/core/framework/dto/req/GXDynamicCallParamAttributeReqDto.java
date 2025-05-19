package cn.maple.core.framework.dto.req;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 动态调用参数属性请求DTO
 * <p>
 * 该类用于描述动态调用参数的属性信息，包括字段名称、Java类型、数据来源等。
 * 主要用于构建动态调用的参数对象，支持从不同来源（token、固定值、回调方法）获取数据。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 创建一个从token中获取数据的参数属性
 * GXDynamicCallParamAttributeReqDto tokenAttribute = new GXDynamicCallParamAttributeReqDto();
 * tokenAttribute.setFieldName("userId");
 * tokenAttribute.setJavaType("java.lang.Long");
 * tokenAttribute.setDataSource("token");
 * tokenAttribute.setSourceFieldName("id");
 *
 * // 创建一个使用固定值的参数属性
 * GXDynamicCallParamAttributeReqDto fixedAttribute = new GXDynamicCallParamAttributeReqDto();
 * fixedAttribute.setFieldName("status");
 * fixedAttribute.setJavaType("java.lang.Integer");
 * fixedAttribute.setDataSource("assign");
 * fixedAttribute.setFixedAssignedValue(1);
 *
 * // 创建一个通过回调方法获取数据的参数属性
 * GXDynamicCallParamAttributeReqDto callbackAttribute = new GXDynamicCallParamAttributeReqDto();
 * callbackAttribute.setFieldName("permissions");
 * callbackAttribute.setJavaType("java.util.List");
 * callbackAttribute.setDataSource("callback");
 * callbackAttribute.setCallBackClassName("cn.maple.service.PermissionService");
 * callbackAttribute.setCallBackMethodName("getUserPermissions");
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GXDynamicCallParamAttributeReqDto extends GXBaseReqDto {
    /**
     * Java类型
     * <p>
     * 字段的Java类型，必须是完整的类名，例如：java.lang.String、java.lang.Integer、java.util.List等
     * </p>
     */
    private String javaType;

    /**
     * javaType的字段名字，用于组装出javaType对象
     * <p>
     * 该字段表示目标对象中的属性名称，用于将获取的值设置到对应的属性中
     * </p>
     */
    private String fieldName;

    /**
     * 数据来源
     * <p>
     * 支持以下三种数据来源类型：
     * <ul>
     *   <li>token - 从token中获取数据，需要配合sourceFieldName使用</li>
     *   <li>assign - 使用固定分配的值，需要配合fixedAssignedValue使用</li>
     *   <li>callback - 通过动态调用服务接口获取数据，需要配合callBackClassName和callBackMethodName使用</li>
     * </ul>
     * </p>
     * <p>
     * 当dataSource为"token"时，会通过JSONUtil.toBean(getHeader("token"),Dict.class).getStr(sourceFieldName)获取值
     * </p>
     */
    private String dataSource;

    /**
     * 源字段名字
     * <p>
     * 主要用于dataSource为"token"的情况，表示从token中获取的字段名
     * </p>
     */
    private String sourceFieldName;

    /**
     * 固定值
     * <p>
     * 主要用于dataSource为"assign"的情况，表示要分配的固定值
     * 可以是任意类型的对象，会根据javaType进行类型转换
     * </p>
     */
    private Object fixedAssignedValue;

    /**
     * 回调服务类的完全限定名
     * <p>
     * 主要用于dataSource为"callback"的情况，表示要调用的服务类
     * 必须是完整的类名，例如：cn.maple.core.framework.service.GXBaseService
     * </p>
     */
    private String callBackClassName;

    /**
     * 回调服务方法名
     * <p>
     * 主要用于dataSource为"callback"的情况，表示要调用的服务方法名
     * 需要配合callBackClassName一起使用
     * 例如：当callBackClassName为cn.maple.core.framework.service.GXBaseService时，
     * callBackMethodName可以是getConstantsFields
     * </p>
     */
    private String callBackMethodName;
}
