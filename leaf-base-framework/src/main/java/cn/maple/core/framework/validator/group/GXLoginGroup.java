package cn.maple.core.framework.validator.group;

/**
 * 登录操作验证分组接口
 * <p>
 * 该接口用于在使用Bean Validation进行登录操作的数据验证时，
 * 通过分组功能实现针对登录场景的特定验证规则。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * public class LoginForm {
 *     @NotBlank(message = "用户名不能为空", groups = {GXLoginGroup.class})
 *     private String username;
 *     
 *     @NotBlank(message = "密码不能为空", groups = {GXLoginGroup.class})
 *     private String password;
 *     
 *     @NotBlank(message = "验证码不能为空", groups = {GXLoginGroup.class})
 *     private String captcha;
 *     
 *     @NotBlank(message = "验证码标识不能为空", groups = {GXLoginGroup.class})
 *     private String captchaKey;
 * }
 * 
 * // 在Controller中使用
 * @PostMapping("/login")
 * public GXResultUtils<?> login(@Validated(GXLoginGroup.class) @RequestBody LoginForm form) {
 *     // 验证验证码
 *     boolean valid = captchaService.validate(form.getCaptchaKey(), form.getCaptcha());
 *     if (!valid) {
 *         return GXResultUtils.error("验证码不正确");
 *     }
 *     // 执行登录逻辑
 *     return authService.login(form.getUsername(), form.getPassword());
 * }
 * </pre>
 * 
 * <p>
 * 通过使用GXLoginGroup分组，可以确保在登录场景下应用特定的验证规则，
 * 提高登录操作的安全性，防止恶意登录尝试。
 * </p>
 */
public interface GXLoginGroup {
}
