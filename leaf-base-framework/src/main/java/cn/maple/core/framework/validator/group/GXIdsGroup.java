package cn.maple.core.framework.validator.group;

/**
 * 多个ID验证分组接口
 * <p>
 * 该接口用于在使用Bean Validation进行多个ID验证时，
 * 通过分组功能实现针对批量ID验证场景的特定验证规则。
 * 主要用于批量查询、批量删除或批量更新多条记录的场景。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * public class IdsRequest {
 *     @NotEmpty(message = "ID列表不能为空", groups = {GXIdsGroup.class})
 *     @Size(min = 1, max = 100, message = "ID列表大小必须在1-100之间", groups = {GXIdsGroup.class})
 *     private List&lt;Long&gt; ids;
 *     
 *     // 自定义验证方法
 *     @AssertTrue(message = "ID列表中不能包含无效ID", groups = {GXIdsGroup.class})
 *     public boolean isValidIds() {
 *         if (ids == null || ids.isEmpty()) {
 *             return true; // 空列表由@NotEmpty处理
 *         }
 *         // 检查是否所有ID都大于0
 *         return ids.stream().allMatch(id -> id != null && id > 0);
 *     }
 *     
 *     // getter and setter
 * }
 * 
 * // 在Controller中使用
 * @PostMapping("/users/batch")
 * public GXResultUtils<?> batchGetUsers(@Validated(GXIdsGroup.class) @RequestBody IdsRequest request) {
 *     // 执行批量查询逻辑
 *     return userService.batchGetByIds(request.getIds());
 * }
 * 
 * @DeleteMapping("/users/batch")
 * public GXResultUtils<?> batchDeleteUsers(@Validated(GXIdsGroup.class) @RequestBody IdsRequest request) {
 *     // 执行批量删除逻辑
 *     return userService.batchDeleteByIds(request.getIds());
 * }
 * </pre>
 * 
 * <p>
 * 通过使用GXIdsGroup分组，可以确保在需要验证多个ID的场景下应用特定的验证规则，
 * 提高批量操作的安全性和可靠性，防止恶意请求或无效数据。
 * </p>
 */
public interface GXIdsGroup {
}
