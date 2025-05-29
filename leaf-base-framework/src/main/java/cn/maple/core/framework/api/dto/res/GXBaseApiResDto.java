package cn.maple.core.framework.api.dto.res;

import cn.maple.core.framework.dto.res.GXBaseResDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * API层响应数据传输对象基类
 * <p>
 * 该类是所有API响应DTO的基类，继承自GXBaseResDto，用于规范API层的响应数据结构。
 * 在微服务架构或前后端分离的应用中，该类作为控制器层向前端返回数据的标准模型。
 * </p>
 *
 * <p>主要特点：</p>
 * <ul>
 *   <li>提供API层响应数据的统一抽象</li>
 *   <li>区分内部服务DTO和外部API DTO，增强代码可读性</li>
 *   <li>便于在API层统一添加数据脱敏、格式转换等功能</li>
 *   <li>继承了GXBaseResDto中获取Spring Bean的能力，便于在DTO中进行业务处理</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * public class UserInfoApiResDto extends GXBaseApiResDto {
 *     private Long userId;
 *     private String username;
 *     private String email;
 *     private String mobile;
 *
 *     /&ast;&ast;
 *      * 对手机号进行脱敏处理
 *      &ast;/
 *     public String getMaskedMobile() {
 *         // 使用继承自GXBaseResDto的getBean方法获取工具类Bean
 *         return getBean("maskingService", MaskingService.class).maskMobile(mobile);
 *     }
 * }
 * </pre>
 *
 * @see cn.maple.core.framework.dto.res.GXBaseResDto
 * @see cn.maple.core.framework.dto.GXBaseDto
 * @see cn.maple.core.framework.dto.GXBaseData
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GXBaseApiResDto extends GXBaseResDto {
    // 目前没有添加额外属性，仅作为API层响应DTO的标识和未来扩展的基础
    // 继承了GXBaseResDto中的getBean方法，可以在DTO中方便地获取Spring Bean
}
