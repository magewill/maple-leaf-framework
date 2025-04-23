package cn.maple.core.framework.validator.group;

/**
 * 单个ID验证分组接口
 * <p>
 * 该接口用于在使用Bean Validation进行单个ID验证时，
 * 通过分组功能实现针对ID验证场景的特定验证规则。
 * 主要用于根据ID查询、删除或更新单条记录的场景。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * public class IdRequest {
 *     @NotNull(message = "ID不能为空", groups = {GXIdGroup.class})
 *     @Min(value = 1, message = "ID必须大于0", groups = {GXIdGroup.class})
 *     private Long id;
 *     
 *     // getter and setter
 * }
 * 
 * // 在Controller中使用
 * @GetMapping("/user/{id}")
 * public GXResultUtils<?> getUserById(@Validated(GXIdGroup.class) IdRequest request) {
 *     // 执行根据ID查询的逻辑
 *     return userService.getById(request.getId());
 * }
 * 
 * @DeleteMapping("/user/{id}")
 * public GXResultUtils<?> deleteUserById(@Validated(GXIdGroup.class) IdRequest request) {
 *     // 执行根据ID删除的逻辑
 *     return userService.deleteById(request.getId());
 * }
 * </pre>
 * 
 * <p>
 * 通过使用GXIdGroup分组，可以确保在需要验证单个ID的场景下应用特定的验证规则，
 * 提高API的安全性和可靠性。
 * </p>
 */
public interface GXIdGroup {
}
