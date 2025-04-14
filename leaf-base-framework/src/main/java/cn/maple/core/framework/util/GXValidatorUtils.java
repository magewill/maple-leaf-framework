package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBeanValidateException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;

/**
 * Hibernate Validator 校验工具类
 * <p>
 * 该工具类提供了基于 Hibernate Validator 的数据验证功能，支持对象级别的验证和分组验证。
 * 可以用于验证实体对象的属性是否符合预定义的约束条件。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 创建待验证的对象
 * User user = new User();
 * user.setName("test");
 * user.setAge(15);
 *
 * // 验证对象
 * GXValidatorUtils.validateEntity(user);
 *
 * // 分组验证
 * GXValidatorUtils.validateEntity(user, CreateGroup.class);
 *
 * // 指定JSON名称的验证
 * GXValidatorUtils.validateEntity(user, "userInfo");
 * }
 * </pre>
 * </p>
 * <p>
 * 参考文档：http://docs.jboss.org/hibernate/validator/5.4/reference/en-US/html_single/
 * </p>
 *
 * @author zj chen <britton@126.com>
 */
public class GXValidatorUtils {
    /**
     * Hibernate Validator 验证器实例
     */
    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 私有构造函数，防止实例化
     */
    private GXValidatorUtils() {
    }

    /**
     * 校验对象
     * <p>
     * 使用 Hibernate Validator 对对象进行验证，如果验证失败则抛出 GXBeanValidateException 异常。
     * 验证结果会包含所有违反约束的字段信息。
     * </p>
     *
     * @param object 待校验对象
     * @param groups 待校验的组
     * @throws GXBeanValidateException 当对象验证失败时抛出，包含详细的错误信息
     * @throws IllegalArgumentException 当待校验对象为null时抛出
     */
    public static void validateEntity(Object object, Class<?>... groups) {
        if (object == null) {
            throw new IllegalArgumentException("待校验对象不能为空");
        }
        Set<ConstraintViolation<Object>> constraintViolations = validator.validate(object, groups);
        if (!constraintViolations.isEmpty()) {
            final Dict dict = Dict.create();
            for (ConstraintViolation<Object> constraint : constraintViolations) {
                final String rootBeanName = CharSequenceUtil.lowerFirst(constraint.getRootBean().getClass().getSimpleName());
                final String currentFormName =/* rootBeanName + "." +*/ constraint.getPropertyPath().toString();
                dict.set(currentFormName, CharSequenceUtil.format("{} , value = {}", constraint.getMessage(), constraint.getInvalidValue()));
            }
            throw new GXBeanValidateException("数据验证错误", HttpStatus.HTTP_INTERNAL_ERROR, dict);
        }
    }

    /**
     * 校验对象
     *
     * @param object   待校验对象
     * @param jsonName json的名字
     * @param groups   待校验的组
     * @throws GXBeanValidateException 校验不通过，则报GXBeanValidateException异常
     */
    public static void validateEntity(Object object, String jsonName, Class<?>... groups) {
        Set<ConstraintViolation<Object>> constraintViolations = validator.validate(object, groups);
        if (!constraintViolations.isEmpty()) {
            final Dict dict = Dict.create();
            for (ConstraintViolation<Object> constraint : constraintViolations) {
                String currentFormName = constraint.getPropertyPath().toString();
                if (CharSequenceUtil.isNotBlank(jsonName)) {
                    currentFormName = jsonName + "." + currentFormName;
                }
                String message = constraint.getMessage();
                //currentFormName = CharSequenceUtil.toSymbolCase(currentFormName, '_');
                if (constraint.getMessageTemplate().contains("{fieldName}")) {
                    final Dict param = Dict.create().set("fieldName", currentFormName);
                    message = StrUtil.format(constraint.getMessageTemplate(), param);
                }
                dict.putIfAbsent(currentFormName, message);
            }
            throw new GXBeanValidateException("数据验证错误", HttpStatus.HTTP_INTERNAL_ERROR, dict);
        }
    }
}
