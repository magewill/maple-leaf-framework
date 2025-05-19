package cn.maple.core.framework.dto.req;

import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 基础请求数据传输对象
 * <p>
 * 该类是所有请求DTO的基类，继承自GXBaseDto，提供了请求数据的基本功能。
 * 所有用于接收前端或外部系统请求数据的DTO都应该继承此类。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * public class UserLoginReqDto extends GXBaseReqDto {
 *     private String username;
 *     private String password;
 *
 *     @Override
 *     protected void verify() {
 *         // 验证用户名和密码不能为空
 *         if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
 *             throw new IllegalArgumentException("用户名或密码不能为空");
 *         }
 *     }
 * }
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GXBaseReqDto extends GXBaseDto {
}
