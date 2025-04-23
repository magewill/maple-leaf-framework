package cn.maple.core.framework.dto.inner;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据存在性验证DTO
 * <p>
 * 该类用于构建数据库记录存在性验证的参数，支持复杂的验证条件和SpEL表达式
 * 主要用于表单验证中的唯一性检查、关联数据存在性检查等场景
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 验证用户名是否已存在
 * GXValidateExistsDto validateDto = GXValidateExistsDto.builder()
 *     .tableName("users")
 *     .fieldName("username")
 *     .value("test_user")
 *     .build();
 * boolean exists = validateService.recordExists(validateDto);
 * 
 * // 带附加条件的验证（排除当前记录）
 * Dict condition = Dict.create();
 * condition.set("id", "!=", currentUserId);
 * 
 * GXValidateExistsDto validateDto = GXValidateExistsDto.builder()
 *     .tableName("users")
 *     .fieldName("email")
 *     .value("test@example.com")
 *     .condition(condition)
 *     .build();
 * 
 * // 使用SpEL表达式的复杂验证
 * GXValidateExistsDto validateDto = GXValidateExistsDto.builder()
 *     .tableName("orders")
 *     .fieldName("order_no")
 *     .value("ORD20230101001")
 *     .spEL("#result == false")
 *     .build();
 * 
 * // 在特定验证分组中使用
 * GXValidateExistsDto validateDto = GXValidateExistsDto.builder()
 *     .tableName("products")
 *     .fieldName("sku")
 *     .value("SKU001")
 *     .groups(new Class[]{Create.class})
 *     .build();
 * </pre>
 * </p>
 * 
 * <p>
 * 安全性说明：
 * 1. 该类通过参数化查询处理字段值，防止SQL注入攻击
 * 2. 表名和字段名应当通过白名单验证，避免非法输入
 * 3. SpEL表达式的使用应当受到严格控制，避免执行危险代码
 * 4. 验证逻辑应当在服务端执行，不依赖客户端验证
 * 5. 对于敏感数据的验证，应当采取额外的安全措施
 * </p>
 * 
 * <p>
 * 性能优化说明：
 * 1. 验证查询应当使用索引字段，提高查询效率
 * 2. 对于高频验证，可考虑使用缓存减少数据库查询
 * 3. 合理设置验证条件，避免不必要的复杂查询
 * </p>
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class GXValidateExistsDto extends GXBaseDto {
    /**
     * 字段名字
     */
    private String fieldName;

    /**
     * 表名字
     */
    private String tableName;

    /**
     * 需要验证的值
     */
    @SuppressWarnings("all")
    private Object value;

    /**
     * 附加条件 SpEL表达式
     * 用于计算结果是否满足预期
     */
    private String spEL;

    /**
     * 附加验证条件 用于指定一些固定条件值
     */
    private Dict condition;

    /**
     * 验证器分组
     */
    private Class<?>[] groups;
}
