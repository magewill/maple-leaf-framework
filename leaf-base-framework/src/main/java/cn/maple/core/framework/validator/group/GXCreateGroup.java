package cn.maple.core.framework.validator.group;

/**
 * 创建数据验证组
 * <p>
 * 该接口用作验证分组的标记接口，用于标识实体类中需要在创建操作时进行验证的字段。
 * 通过在实体类的字段上使用@Validated注解并指定GXCreateGroup.class作为分组，
 * 可以实现针对创建操作的特定字段验证。
 * </p>
 * 
 * <p>
 * 使用场景：
 * - 创建数据时需要验证的字段
 * - 某些字段在创建时必填，但在更新时可选
 * - 需要区分创建和更新操作的验证规则
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * public class ProductEntity {
 *     // ID在创建时不需要验证，可以自动生成
 *     private Long id;
 *     
 *     // 产品名称在创建时必须验证
 *     @NotBlank(message = "产品名称不能为空", groups = {GXCreateGroup.class})
 *     private String productName;
 *     
 *     // 产品价格在创建时必须验证
 *     @NotNull(message = "产品价格不能为空", groups = {GXCreateGroup.class})
 *     @DecimalMin(value = "0.01", message = "产品价格必须大于0", groups = {GXCreateGroup.class})
 *     private BigDecimal price;
 *     
 *     // getter和setter方法
 * }
 * 
 * // 在控制器中使用
 * @PostMapping
 * public Result create(@Validated(GXCreateGroup.class) @RequestBody ProductEntity product) {
 *     // 只会验证标记了GXCreateGroup组的字段
 *     productService.save(product);
 *     return Result.ok();
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @see GXUpdateGroup 更新数据验证组
 * @see GXGroup 验证组序列
 */
public interface GXCreateGroup {
}
