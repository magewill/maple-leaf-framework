package cn.maple.core.datasource.annotation;

import cn.maple.core.datasource.service.GXValidateDBExistsService;
import cn.maple.core.datasource.service.impl.GXValidateDBExistsValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 数据库存在性验证注解
 * <p>
 * 该注解用于验证数据库中是否存在符合特定条件的记录。可以用于多种场景：
 * 1. 唯一性验证：确保字段值在数据库中是唯一的
 * 2. 关联性验证：确保字段值在关联表中存在
 * 3. 条件性验证：根据特定条件验证记录是否存在
 * </p>
 * <p>
 * 使用示例1：验证用户名是否已存在
 * <pre>
 * public class UserDTO {
 *     @GXValidateDBExists(
 *         service = UserExistsValidateService.class,
 *         fieldName = "username",
 *         tableName = "tb_user",
 *         message = "用户名已存在"
 *     )
 *     private String username;
 * }
 * </pre>
 * </p>
 * <p>
 * 使用示例2：验证关联记录是否存在，带附加条件
 * <pre>
 * public class OrderDTO {
 *     @GXValidateDBExists(
 *         service = OrderExistsValidateService.class,
 *         fieldName = "productId",
 *         tableName = "tb_product",
 *         condition = "status=1",
 *         message = "商品不存在或已下架"
 *     )
 *     private Long productId;
 * }
 * </pre>
 * </p>
 * <p>
 * 使用示例3：使用SpEL表达式进行复杂验证
 * <pre>
 * public class TransferDTO {
 *     @GXValidateDBExists(
 *         service = AccountExistsValidateService.class,
 *         fieldName = "accountId",
 *         tableName = "tb_account",
 *         spEL = "#result.balance >= #root.amount",
 *         message = "账户余额不足"
 *     )
 *     private Long accountId;
 *     
 *     private BigDecimal amount;
 * }
 * </pre>
 * </p>
 * 
 * @author britton chen <britton@126.com>
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = GXValidateDBExistsValidator.class)
@Documented
public @interface GXValidateDBExists {
    /**
     * 错误消息
     *
     * @return String
     */
    String message() default "{fieldName}对应的数据已经存在或是参数已经存在存在";

    /**
     * 分组验证
     *
     * @return Class
     */
    Class<?>[] groups() default {};

    /**
     * 数据
     *
     * @return Class
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * 目标服务
     *
     * @return Class
     */
    Class<? extends GXValidateDBExistsService> service();

    /**
     * 目标字段名字
     *
     * @return String
     */
    String fieldName();

    /**
     * 表名
     *
     * @return String
     */
    String tableName();

    /**
     * 附加的查询条件
     * eg:
     * type=news,phone=13800138000
     *
     * @return String
     */
    String condition() default "";

    /**
     * 附加条件 SpEL表达式
     * 用于计算结果是否满足预期
     *
     * @return String
     */
    String spEL() default "";

    /**
     * 需要依赖的字段名字
     *
     * @return String[]
     */
    String[] dependOnFields() default {};
}