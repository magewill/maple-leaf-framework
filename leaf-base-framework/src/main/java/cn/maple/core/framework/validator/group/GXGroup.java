package cn.maple.core.framework.validator.group;

import jakarta.validation.GroupSequence;

/**
 * 验证组序列定义
 * <p>
 * 该接口使用@GroupSequence注解定义了验证组的执行顺序。
 * 验证将按照数组中定义的顺序进行，如果前面的组验证失败，则后续组不会被验证。
 * 这种机制可以实现分层验证和条件验证。
 * </p>
 * 
 * <p>
 * 验证顺序：
 * 1. 首先验证GXAddGroup组的约束
 * 2. 如果GXAddGroup验证通过，则验证GXUpdateGroup组的约束
 * 3. 如果任一组验证失败，则不再继续后续组的验证
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在实体类中使用分组验证
 * public class UserEntity {
 *     @NotNull(message = "ID不能为空", groups = {GXUpdateGroup.class})
 *     private Long id;
 *     
 *     @NotBlank(message = "用户名不能为空", groups = {GXAddGroup.class, GXUpdateGroup.class})
 *     private String username;
 *     
 *     @NotBlank(message = "密码不能为空", groups = {GXAddGroup.class})
 *     private String password;
 *     
 *     // getter和setter方法
 * }
 * 
 * // 2. 在控制器中使用分组验证
 * @RestController
 * @RequestMapping("/user")
 * public class UserController {
 *     @PostMapping
 *     public Result add(@Validated(GXAddGroup.class) @RequestBody UserEntity user) {
 *         // 新增用户逻辑
 *         return Result.ok();
 *     }
 *     
 *     @PutMapping
 *     public Result update(@Validated(GXUpdateGroup.class) @RequestBody UserEntity user) {
 *         // 更新用户逻辑
 *         return Result.ok();
 *     }
 *     
 *     @PostMapping("/validate-sequence")
 *     public Result validateWithSequence(@Validated(GXGroup.class) @RequestBody UserEntity user) {
 *         // 按照GXGroup定义的顺序进行验证
 *         return Result.ok();
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @see GXAddGroup 新增数据验证组
 * @see GXUpdateGroup 更新数据验证组
 */
@GroupSequence({GXAddGroup.class, GXUpdateGroup.class})
public interface GXGroup {
}
