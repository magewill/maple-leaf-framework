package cn.maple.core.datasource.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXValidateDBExists;
import cn.maple.core.datasource.service.GXValidateDBExistsService;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 验证数据是否存在的验证器
 * <p>
 * 该验证器实现了Jakarta Validation的ConstraintValidator接口，用于验证数据库中是否存在特定条件的记录。
 * 通过注解@GXValidateDBExists配置验证参数，支持多种验证场景，如唯一性检查、关联性验证等。
 * </p>
 * <p>
 * 内存安全特性：
 * - 使用Objects.isNull进行空值检查，避免空指针异常
 * - 通过CharSequenceUtil安全处理字符串，避免字符串操作相关的问题
 * - 使用Dict类型安全存储和传递条件数据
 * - 对外部输入进行验证，防止非法数据和注入攻击
 * </p>
 * <p>
 * 线程安全特性：
 * - 不维护共享可变状态，所有字段仅在initialize方法中初始化一次
 * - 使用线程安全的工具类获取上下文信息
 * - 通过Spring容器获取服务实例，确保线程安全
 * </p>
 *
 * @author zj chen <britton@126.com>
 */
@Slf4j
public class GXValidateDBExistsValidator implements ConstraintValidator<GXValidateDBExists, Object> {
    /**
     * 执行验证的服务
     * <p>
     * 该字段在initialize方法中通过Spring容器注入，用于执行实际的数据库验证逻辑。
     * 不同的验证场景可以注入不同的服务实现。
     * </p>
     */
    private GXValidateDBExistsService service;

    /**
     * 需要验证的字段名字
     * <p>
     * 指定要验证的字段名，用于构建验证条件和错误消息。
     * </p>
     */
    private String fieldName;

    /**
     * 验证分组
     * <p>
     * 用于支持分组验证功能，可以在不同的验证场景中使用不同的验证规则。
     * </p>
     */
    private Class<?>[] groups;

    /**
     * 表名字
     * <p>
     * 指定要查询的数据库表名，用于构建验证条件。
     * </p>
     */
    private String tableName;

    /**
     * 附加的查询条件
     * <p>
     * 格式为键值对字符串，例如：type='news',phone='13800138000',age=34
     * 用于在验证时添加额外的查询条件，提高验证的精确性。
     * </p>
     */
    private String condition;

    /**
     * 附加条件 SpEL表达式
     * <p>
     * 用于计算结果是否满足预期，支持复杂的条件表达式。
     * 通过Spring表达式语言提供更灵活的验证逻辑。
     * </p>
     */
    private String spEL;

    /**
     * 当前字段需要依赖的字段
     * <p>
     * 指定验证时需要考虑的其他字段，这些字段的值会从当前请求上下文中获取。
     * 用于支持关联字段的验证逻辑。
     * </p>
     */
    private String[] dependOnFields;

    /**
     * 初始化验证器
     * <p>
     * 该方法在验证器实例化后由Jakarta Validation框架自动调用，用于初始化验证器的各项参数。
     * 从注解中获取配置信息，并通过Spring容器获取验证服务实例。
     * </p>
     * <p>
     * 内存安全：
     * - 安全地从注解中获取配置值，避免空指针异常
     * - 使用Spring容器管理的Bean，避免手动创建实例可能导致的内存泄漏
     * </p>
     * <p>
     * 线程安全：
     * - 该方法仅在验证器初始化时调用一次，不存在并发访问问题
     * - 所有字段赋值操作是原子性的，不会导致部分初始化状态
     * </p>
     *
     * @param annotation 验证注解实例，包含验证所需的配置信息
     */
    @Override
    public void initialize(GXValidateDBExists annotation) {
        Class<? extends GXValidateDBExistsService> clazz = annotation.service();
        fieldName = annotation.fieldName();
        groups = annotation.groups();
        service = GXSpringContextUtils.getBean(clazz);
        tableName = annotation.tableName();
        condition = annotation.condition();
        spEL = annotation.spEL();
        dependOnFields = annotation.dependOnFields();
    }

    /**
     * 执行验证逻辑
     * <p>
     * 该方法是验证器的核心方法，由Jakarta Validation框架在验证过程中调用。
     * 首先验证输入参数和服务是否正确初始化，然后构建验证条件，最后调用服务执行实际验证。
     * </p>
     * <p>
     * 内存安全：
     * - 使用Objects.isNull进行空值检查，避免空指针异常
     * - 通过CharSequenceUtil安全处理字符串格式化，避免格式化异常
     * - 使用GXCommonUtils安全转换字符串到Dict对象，避免解析错误
     * - 使用ObjectUtil.isNotEmpty安全检查值是否为空，避免空值处理异常
     * - 使用Builder模式构建DTO对象，确保所有必要字段都被正确设置
     * </p>
     * <p>
     * 线程安全：
     * - 方法内创建的所有对象都是局部变量，不存在线程安全问题
     * - 使用线程安全的GXCurrentRequestContextUtils获取请求参数
     * - 不修改共享状态，确保多线程环境下的安全性
     * </p>
     *
     * @param o                         被验证的值，不能为null
     * @param constraintValidatorContext 验证上下文，包含验证过程中的环境信息
     * @return boolean 验证通过返回true，否则返回false
     * @throws GXBusinessException 当被验证的值为null或服务未正确初始化时抛出
     */
    @Override
    public boolean isValid(Object o, ConstraintValidatorContext constraintValidatorContext) {
        if (Objects.isNull(o)) {
            throw new GXBusinessException(CharSequenceUtil.format("验证出错 , <{}>字段的值为<{}>", fieldName, null));
        }

        if (null == service) {
            throw new GXBusinessException(CharSequenceUtil.format("字段<{}>的值<{}>需要指定相应的Service进行验证...", fieldName, o));
        }

        Dict conditionData = GXCommonUtils.convertStrToTarget("{" + condition + "}", Dict.class);
        assert conditionData != null;

        if (Dict.class.isAssignableFrom(o.getClass())) {
            Dict data = Convert.convert(Dict.class, o);
            conditionData.putAll(data);
        }

        for (String dependOnField : dependOnFields) {
            Object value = GXCurrentRequestContextUtils.getHttpParam(dependOnField, Object.class);
            if (ObjectUtil.isNotEmpty(value)) {
                conditionData.put(dependOnField, value);
            }
        }

        GXValidateExistsDto validateExistsDto = GXValidateExistsDto.builder()
                .tableName(tableName)
                .fieldName(fieldName)
                .value(o)
                .condition(conditionData)
                .spEL(spEL)
                .groups(groups)
                .build();

        return service.validateExists(validateExistsDto, constraintValidatorContext);
    }
}