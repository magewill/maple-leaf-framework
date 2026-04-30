package cn.maple.core.framework.api.dto.res;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class GXHttpInvokerApiErrorResDto extends GXBaseApiResDto {
    private String timestamp;

    private int status;

    private int code;

    private String message;

    private String path;
}
