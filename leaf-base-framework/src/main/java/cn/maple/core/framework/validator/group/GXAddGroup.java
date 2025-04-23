package cn.maple.core.framework.validator.group;

/**
 * 新增数据验证组
 * <p>
 * 该接口用作验证分组的标记接口，用于标识实体类中需要在新增操作时进行验证的字段。
 * 通过在实体类的字段上使用@Validated注解并指定GXAddGroup.class作为分组，
 * 可以实现针对新增操作的特定字段验证。
 * </p>
 * 
 * <p>
 * 使用场景：
 * - 新增数据时需要验证的字段
 * - 某些字段在新增时必填，但在更新时可选
 * - 需要区分新增和更新操作的验证规则
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * public class UserEntity {
 *     // ID在新增时不需要验证，可以自动生成
 *     private Long id;
 *     
 *     // 用户名在新增时必须验证
 *     @NotBlank(message = "用户名不能为空", groups = {GXAddGroup.class})
 *     private String username;
 *     
 *     // 密码在新增时必须验证
 *     @NotBlank(message = "密码不能为空", groups = {GXAddGroup.class})
 *     @Size(min = 6, max = 20, message = "密码长度必须在6-20之间", groups = {GXAddGroup.class})
 *     private String password;
 *     
 *     // getter和setter方法
 * }
 * 
 * // 在控制器中使用
 * @PostMapping
 * public Result add(@Validated(GXAddGroup.class) @RequestBody UserEntity user) {
 *     // 只会验证标记了GXAddGroup组的字段
 *     userService.save(user);
 *     return Result.ok();
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @see GXUpdateGroup 更新数据验证组
 * @see GXGroup 验证组序列
 */
public interface GXAddGroup {
}
