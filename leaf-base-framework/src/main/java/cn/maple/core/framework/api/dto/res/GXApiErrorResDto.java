/**
 * API错误响应数据传输对象
 * 包含标准的错误响应字段定义
 */
package cn.maple.core.framework.api.dto.res;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class GXApiErrorResDto extends GXBaseApiResDto {
    /**
     * 错误发生的时间戳
     */
    private String timestamp;

    /**
     * HTTP状态码（如404, 500）
     */
    private int status;

    /**
     * 错误码
     */
    private int code;

    /**
     * 错误详细信息描述
     */
    private String message;

    /**
     * 出现错误的请求路径
     */
    private String path;
}
