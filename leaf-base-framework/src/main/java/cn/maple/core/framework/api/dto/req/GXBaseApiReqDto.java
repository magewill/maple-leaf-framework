package cn.maple.core.framework.api.dto.req;

import cn.maple.core.framework.dto.req.GXBaseReqDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * API层请求数据传输对象基类
 * <p>
 * 该类是所有API请求DTO的基类，继承自GXBaseReqDto，用于规范API层的请求数据结构。
 * 在微服务架构或前后端分离的应用中，该类作为控制器层接收前端请求数据的标准模型。
 * </p>
 *
 * <p>主要特点：</p>
 * <ul>
 *   <li>提供API层请求数据的统一抽象</li>
 *   <li>区分内部服务DTO和外部API DTO，增强代码可读性</li>
 *   <li>便于在API层统一添加安全验证、参数校验等功能</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * public class UserLoginApiReqDto extends GXBaseApiReqDto {
 *     private String username;
 *     private String password;
 *     private String captchaCode;
 *
 *     @Override
 *     protected void verify() {
 *         super.verify(); // 调用父类验证
 *         // 添加特定的API参数验证逻辑
 *         if (StringUtils.isBlank(captchaCode)) {
 *             throw new IllegalArgumentException("验证码不能为空");
 *         }
 *     }
 * }
 * </pre>
 *
 * @see cn.maple.core.framework.dto.req.GXBaseReqDto
 * @see cn.maple.core.framework.dto.GXBaseDto
 * @see cn.maple.core.framework.dto.GXBaseData
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GXBaseApiReqDto extends GXBaseReqDto {
    // 目前没有添加额外属性，仅作为API层请求DTO的标识和未来扩展的基础
}
