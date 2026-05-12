package cn.maple.core.framework.api.dto.res;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class GXHttpInvokerApiErrorResDto extends GXBaseApiResDto {
    private @Nullable String timestamp;

    private int status;

    private int code;

    private @Nullable String message;

    private @Nullable String path;
}
