package cn.maple.core.framework.dto.req;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态调用参数DTO
 * <p>
 * 该类用于描述动态调用的参数信息，支持两种调用方式：
 * 1. 基本类型参数调用：当javaType不存在或为空时，微服务的调用方式是 XXXObj.xxxMethod(String username, Integer age)
 * 2. 对象类型参数调用：当javaType存在且不为空时，微服务的调用方式是 XXXObj.xxxMethod(TestReqDto testReqDto)
 * </p>
 * <p>
 * 通过attributes属性可以定义参数的各个字段及其数据来源，支持从token、固定值、回调方法等多种来源获取数据。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 创建一个动态调用参数DTO
 * GXDynamicCallParamReqDto paramReqDto = new GXDynamicCallParamReqDto();
 *
 * // 设置Java类型（目标参数类型）
 * paramReqDto.setJavaType("cn.maple.core.common.dto.PersonDto");
 *
 * // 创建属性列表
 * List<GXDynamicCallParamAttributeReqDto> attributes = new ArrayList<>();
 *
 * // 添加一个从回调方法获取数据的属性
 * GXDynamicCallParamAttributeReqDto permissionsAttr = new GXDynamicCallParamAttributeReqDto();
 * permissionsAttr.setFieldName("permissions");
 * permissionsAttr.setJavaType("java.util.List");
 * permissionsAttr.setDataSource("callback");
 * permissionsAttr.setCallBackClassName("cn.maple.service.TestService");
 * permissionsAttr.setCallBackMethodName("myTest");
 * attributes.add(permissionsAttr);
 *
 * // 添加一个使用固定值的属性
 * GXDynamicCallParamAttributeReqDto userNameAttr = new GXDynamicCallParamAttributeReqDto();
 * userNameAttr.setFieldName("userName");
 * userNameAttr.setJavaType("java.lang.String");
 * userNameAttr.setDataSource("assign");
 * userNameAttr.setFixedAssignedValue("testUser");
 * attributes.add(userNameAttr);
 *
 * // 添加一个从token中获取数据的属性
 * GXDynamicCallParamAttributeReqDto nickNameAttr = new GXDynamicCallParamAttributeReqDto();
 * nickNameAttr.setFieldName("nickName");
 * nickNameAttr.setJavaType("java.lang.String");
 * nickNameAttr.setDataSource("token");
 * nickNameAttr.setSourceFieldName("nickName");
 * attributes.add(nickNameAttr);
 *
 * // 设置属性列表
 * paramReqDto.setAttributes(attributes);
 * </pre>
 *
 * <p>JSON格式示例：</p>
 * <pre>
 * {
 *   "javaType": "cn.maple.core.common.dto.PersonDto",
 *   "attributes": [
 *     {
 *       "fieldName": "permissions",
 *       "javaType": "java.util.List",
 *       "dataSource": "callback",
 *       "callBackClassName": "cn.maple.service.TestService",
 *       "callBackMethodName": "myTest"
 *     },
 *     {
 *       "fieldName": "userName",
 *       "javaType": "java.lang.String",
 *       "dataSource": "assign",
 *       "fixedAssignedValue": "testUser"
 *     },
 *     {
 *       "fieldName": "nickName",
 *       "javaType": "java.lang.String",
 *       "dataSource": "token",
 *       "sourceFieldName": "nickName"
 *     }
 *   ]
 * }
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GXDynamicCallParamReqDto extends GXBaseReqDto {
    /**
     * Java类型
     * <p>
     * 表示动态调用参数的Java类型，必须是完整的类名
     * 当该字段不存在或为空时，微服务的调用方式是 XXXObj.xxxMethod(String username, Integer age)
     * 当该字段存在且不为空时，微服务的调用方式是 XXXObj.xxxMethod(TestReqDto testReqDto)
     * </p>
     */
    private String javaType;

    /**
     * 具体参数信息
     * <p>
     * 包含参数的各个字段及其数据来源信息，每个元素都是一个GXDynamicCallParamAttributeReqDto对象
     * 用于描述参数对象的各个属性，包括字段名、Java类型、数据来源等
     * </p>
     */
    private List<GXDynamicCallParamAttributeReqDto> attributes = new ArrayList<>(0);
}