package cn.maple.core.framework.validator.group;

/**
 * 更新数据验证分组接口
 * <p>
 * 该接口用于在使用Bean Validation进行数据更新操作的验证时，
 * 通过分组功能实现针对更新场景的特定验证规则。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * public class UserUpdateForm {
 *     @NotNull(message = "用户ID不能为空", groups = {GXUpdateGroup.class})
 *     private Long id;
 *     
 *     @NotBlank(message = "用户名不能为空", groups = {GXUpdateGroup.class})
 *     @Length(min = 2, max = 20, message = "用户名长度必须在2-20个字符之间", groups = {GXUpdateGroup.class})
 *     private String username;
 *     
 *     @Email(message = "邮箱格式不正确", groups = {GXUpdateGroup.class})
 *     private String email;
 *     
 *     @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确", groups = {GXUpdateGroup.class})
 *     private String mobile;
 * }
 * 
 * // 在Controller中使用
 * @PutMapping("/user")
 * public GXResultUtils<?> updateUser(@Validated(GXUpdateGroup.class) @RequestBody UserUpdateForm form) {
 *     // 执行更新逻辑
 *     return userService.updateUser(form);
 * }
 * </pre>
 * 
 * <p>
 * 通过使用GXUpdateGroup分组，可以确保在数据更新场景下应用特定的验证规则，
 * 例如要求必须提供ID字段，而在其他场景（如新增）可能不需要ID字段。
 * </p>
 *
 * @author britton britton@126.com
 */
public interface GXUpdateGroup {
}
