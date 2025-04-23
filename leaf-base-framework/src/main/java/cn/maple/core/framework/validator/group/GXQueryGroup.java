package cn.maple.core.framework.validator.group;

/**
 * 查询操作验证分组接口
 * <p>
 * 该接口用于在使用Bean Validation进行查询操作的数据验证时，
 * 通过分组功能实现针对查询场景的特定验证规则。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * public class UserQueryForm {
 *     @Pattern(regexp = "^[a-zA-Z0-9_]{0,20}$", message = "用户名只能包含字母、数字和下划线，最长20个字符", 
 *             groups = {GXQueryGroup.class})
 *     private String username;
 *     
 *     @Email(message = "邮箱格式不正确", groups = {GXQueryGroup.class})
 *     private String email;
 *     
 *     @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确", groups = {GXQueryGroup.class})
 *     private String mobile;
 *     
 *     @Min(value = 1, message = "页码最小为1", groups = {GXQueryGroup.class})
 *     private Integer pageNum = 1;
 *     
 *     @Min(value = 1, message = "每页条数最小为1", groups = {GXQueryGroup.class})
 *     @Max(value = 100, message = "每页条数最大为100", groups = {GXQueryGroup.class})
 *     private Integer pageSize = 10;
 * }
 * 
 * // 在Controller中使用
 * @GetMapping("/users")
 * public GXResultUtils<?> queryUsers(@Validated(GXQueryGroup.class) UserQueryForm form) {
 *     // 执行查询逻辑
 *     return userService.queryUsers(form);
 * }
 * </pre>
 * 
 * <p>
 * 通过使用GXQueryGroup分组，可以确保在查询场景下应用特定的验证规则，
 * 例如对分页参数进行合理性验证，对查询条件进行格式验证等，提高查询操作的安全性和可靠性。
 * </p>
 */
public interface GXQueryGroup {
}
