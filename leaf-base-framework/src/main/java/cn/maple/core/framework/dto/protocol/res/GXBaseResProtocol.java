package cn.maple.core.framework.dto.protocol.res;

import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 基础响应协议抽象类
 * <p>
 * 该类是所有响应协议的基类，用于规范化前后端交互的响应数据格式
 * 继承自GXBaseDto，可以利用其中的通用属性和方法
 * 主要用于封装API响应数据，提供统一的响应结构
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个具体的响应协议类
 * public class UserResProtocol extends GXBaseResProtocol {
 *     private String username;
 *     private String email;
 *     // 其他用户相关字段
 * }
 *
 * // 在Controller中使用
 * @GetMapping("/user/{id}")
 * public UserResProtocol getUser(@PathVariable Long id) {
 *     UserEntity user = userService.getById(id);
 *     UserResProtocol response = new UserResProtocol();
 *     // 设置响应数据
 *     response.setUsername(user.getUsername());
 *     response.setEmail(user.getEmail());
 *     return response;
 * }
 *
 * // 使用通用响应包装器
 * @GetMapping("/api/data")
 * public ResponseEntity<GXBaseResProtocol> getData() {
 *     DataResProtocol data = new DataResProtocol();
 *     // 设置数据...
 *     return ResponseEntity.ok(data);
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 安全性说明：
 * 1. 响应数据中不应包含敏感信息，如密码、内部系统信息等
 * 2. 对于需要返回的敏感数据，应当进行适当的脱敏处理
 * 3. 避免在响应中包含过多的内部错误详情，以防信息泄露
 * 4. 考虑使用数据过滤器，在序列化前移除敏感字段
 * </p>
 *
 * <p>
 * 性能优化说明：
 * 1. 响应数据应当精简，只包含前端实际需要的字段
 * 2. 对于大型对象，考虑使用懒加载或分页机制
 * 3. 可以使用@JsonIgnore注解排除不需要序列化的字段
 * 4. 考虑使用缓存机制缓存热门API的响应数据
 * </p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public abstract class GXBaseResProtocol extends GXBaseDto {
}
