package cn.maple.core.framework.dto.inner;

import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务日志请求DTO
 * <p>
 * 该类用于记录系统中的业务操作日志，包括操作名称、描述、执行时间等信息
 * 通过记录详细的业务日志，可以追踪系统操作，便于问题排查和审计
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建业务日志对象
 * GXBusinessLogDto logDto = new GXBusinessLogDto();
 * logDto.setBusinessName("用户管理");
 * logDto.setBusinessDescription("创建新用户");
 * logDto.setMethodName("createUser");
 * logDto.setParams(JSON.toJSONString(userDto));
 * logDto.setUserName("admin");
 * logDto.setIp("192.168.1.1");
 * logDto.setExecutionTime(100L); // 执行时间(毫秒)
 * logDto.setRequestAt(System.currentTimeMillis());
 * 
 * // 保存业务日志
 * businessLogService.saveBusinessLog(logDto);
 * </pre>
 * </p>
 * 
 * <p>
 * 安全性说明：
 * 1. 在记录params参数时，应注意敏感信息的处理，如密码等应进行脱敏处理
 * 2. 日志中的用户信息应当与系统认证体系一致，确保用户身份可追溯
 * 3. IP地址等信息应当从可靠来源获取，避免伪造
 * 4. 日志存储应考虑安全性，防止未授权访问和篡改
 * </p>
 * 
 * <p>
 * 性能优化说明：
 * 1. 日志记录应当异步进行，避免影响主业务流程
 * 2. 对于高频操作，可考虑批量记录日志，减少数据库操作次数
 * 3. 合理设置日志保留期限，避免日志数据过度膨胀
 * </p>
 *
 * @author 子曦 godbolt@163.com
 */
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("all")
@Data
public class GXBusinessLogDto extends GXBaseDto {
    /**
     * 业务的日志名字
     */
    private String businessName;

    /**
     * 业务的日志描述
     */
    private String businessDescription;

    /**
     * 调用的方法名字
     */
    private String methodName;

    /**
     * 请求参数
     */
    private String params;

    /**
     * 请求用户名字
     */
    private String userName;

    /**
     * 请求IP地址
     */
    private String ip;

    /**
     * 执行时间
     */
    private Long executionTime;

    /**
     * 请求时间
     */
    private Long requestAt;
}
