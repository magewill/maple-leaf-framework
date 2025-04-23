package cn.maple.core.framework.validator.group;

/**
 * 修改密码验证分组接口
 * <p>
 * 该接口用于在使用Bean Validation进行密码修改相关操作的数据验证时，
 * 通过分组功能实现针对密码修改场景的特定验证规则。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * public class UserPasswordForm {
 *     @NotBlank(message = "用户ID不能为空", groups = {GXUpdatePasswordGroup.class})
 *     private String userId;
 *     
 *     @NotBlank(message = "原密码不能为空", groups = {GXUpdatePasswordGroup.class})
 *     private String oldPassword;
 *     
 *     @NotBlank(message = "新密码不能为空", groups = {GXUpdatePasswordGroup.class})
 *     @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{8,}$", 
 *             message = "密码必须包含大小写字母和数字，且长度不少于8位", 
 *             groups = {GXUpdatePasswordGroup.class})
 *     private String newPassword;
 *     
 *     @NotBlank(message = "确认密码不能为空", groups = {GXUpdatePasswordGroup.class})
 *     private String confirmPassword;
 * }
 * 
 * // 在Controller中使用
 * @PostMapping("/updatePassword")
 * public GXResultUtils<?> updatePassword(@Validated(GXUpdatePasswordGroup.class) @RequestBody UserPasswordForm form) {
 *     // 验证新密码与确认密码是否一致
 *     if (!form.getNewPassword().equals(form.getConfirmPassword())) {
 *         return GXResultUtils.error("两次输入的密码不一致");
 *     }
 *     // 执行密码修改逻辑
 *     return userService.updatePassword(form);
 * }
 * </pre>
 * 
 * <p>
 * 通过使用GXUpdatePasswordGroup分组，可以确保在密码修改场景下应用特定的验证规则，
 * 提高数据验证的精确性和安全性。
 * </p>
 */
public interface GXUpdatePasswordGroup {
}
