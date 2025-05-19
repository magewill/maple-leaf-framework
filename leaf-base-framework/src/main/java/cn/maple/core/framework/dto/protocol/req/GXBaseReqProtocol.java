package cn.maple.core.framework.dto.protocol.req;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;

/**
 * 请求协议基类
 * <p>
 * 该类用于接收和处理用户请求信息，提供了请求参数修复和获取用户登录信息的功能。
 * 继承自GXBaseReqDto，可以利用其中的通用属性和方法。
 * 主要用于规范化前后端交互的请求数据格式和处理流程。
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个具体的请求协议类
 * public class UserLoginReqProtocol extends GXBaseReqProtocol {
 *     private String username;
 *     private String password;
 *
 *     // 重写beforeRepair方法，在参数验证前进行处理
 *     @Override
 *     protected void beforeRepair() {
 *         // 对用户名进行trim处理
 *         if (username != null) {
 *             username = username.trim();
 *         }
 *     }
 *
 *     // 重写afterRepair方法，在参数验证后进行处理
 *     @Override
 *     protected void afterRepair() {
 *         // 可以在这里进行一些额外的安全检查
 *         if (username != null && username.length() < 3) {
 *             throw new IllegalArgumentException("用户名长度不能小于3");
 *         }
 *     }
 *
 *     // 在Controller中使用
 *     // @PostMapping("/login")
 *     // public ResponseEntity login(@RequestBody UserLoginReqProtocol protocol) {
 *     //     // 获取用户登录信息
 *     //     Dict loginInfo = protocol.getLoginCredentials("token", "secretKey");
 *     //     // 处理登录逻辑
 *     //     return ResponseEntity.ok().build();
 *     // }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 安全性说明：
 * 1. 请求参数应当进行严格的验证和过滤，防止XSS攻击和SQL注入
 * 2. 敏感信息（如密码）应当在传输前进行加密处理
 * 3. Token密钥应当妥善保管，避免泄露
 * 4. 在beforeRepair和afterRepair方法中应当避免执行耗时操作，以免影响请求处理性能
 * 5. 对于用户输入的数据，应当进行适当的长度限制和格式验证
 * </p>
 *
 * <p>
 * 性能优化说明：
 * 1. 避免在beforeRepair和afterRepair方法中执行复杂的业务逻辑或数据库操作
 * 2. 对于频繁使用的登录信息，可以考虑使用缓存机制
 * 3. 请求参数的验证和修复应当高效，避免使用过于复杂的正则表达式
 * </p>
 *
 * @author britton
 * @version 1.0
 * @since 2021-09-15
 */
public abstract class GXBaseReqProtocol extends GXBaseReqDto {
    /**
     * 在验证请求参数之前进行数据修复
     * <p>
     * 该方法在请求参数验证前被调用，用于对请求数据进行预处理，如自动填充默认值、格式化输入等。
     * 子类可以重写此方法以实现特定的数据修复逻辑。
     * </p>
     *
     * <p>
     * 使用场景：
     * 1. 对字符串类型参数进行trim处理
     * 2. 设置默认值
     * 3. 日期格式转换
     * 4. 自动填充当前用户ID、IP地址等信息
     * </p>
     *
     * <p>
     * 注意事项：
     * 1. 此方法应当只进行简单的数据处理，不应包含复杂的业务逻辑
     * 2. 不应在此方法中进行数据库操作或远程调用
     * 3. 处理过程中出现的异常应当被适当捕获和处理
     * </p>
     */
    protected void beforeRepair() {
    }

    /**
     * 验证请求参数之后进行数据修复
     * <p>
     * 该方法在请求参数验证后被调用，用于对验证通过的数据进行进一步处理，如关联数据补充、业务规则验证等。
     * 子类可以重写此方法以实现特定的后处理逻辑。
     * </p>
     *
     * <p>
     * 使用场景：
     * 1. 根据已验证的参数补充关联信息
     * 2. 进行更复杂的业务规则验证
     * 3. 数据格式转换或规范化
     * 4. 敏感数据处理（如脱敏）
     * </p>
     *
     * <p>
     * 注意事项：
     * 1. 此方法应当在所有基本参数验证通过后执行
     * 2. 可以抛出异常以中断请求处理流程
     * 3. 应当避免在此方法中修改已验证参数的核心含义
     * 4. 不建议在此方法中执行耗时的操作，以免影响请求响应时间
     * </p>
     */
    protected void afterRepair() {
    }

    /**
     * 获取用户的登录信息
     * <p>
     * 该方法用于从当前请求中获取并解析用户的登录凭证，返回包含用户信息的字典对象。
     * 内部调用GXCurrentRequestContextUtils工具类的方法，从请求头或Cookie中提取token并进行解密验证。
     * </p>
     *
     * <p>
     * 使用场景：
     * 1. 获取当前登录用户的ID、角色、权限等信息
     * 2. 验证用户是否已登录
     * 3. 在业务处理过程中需要用户信息时调用
     * </p>
     *
     * <p>
     * 安全注意事项：
     * 1. token密钥应当妥善保管，避免泄露
     * 2. 返回的用户信息不应包含敏感数据（如密码原文）
     * 3. 应当验证token的有效期和签名
     * 4. 考虑使用HTTPS传输token，防止中间人攻击
     * </p>
     *
     * @param tokenName      token的名字，通常对应请求头或Cookie中的键名
     * @param tokenSecretKey token密钥，用于解密和验证token的有效性
     * @return 解密之后的登录信息，包含用户ID、角色等信息的Dict对象；如果token无效或不存在，可能返回空Dict或抛出异常
     */
    protected Dict getLoginCredentials(String tokenName, String tokenSecretKey) {
        return GXCurrentRequestContextUtils.getLoginCredentials(tokenName, tokenSecretKey);
    }
}
