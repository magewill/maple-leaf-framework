package cn.maple.webclient.config;

import org.jspecify.annotations.Nullable;

/**
 * Error body shape accepted from remote peers.
 * <p>Supports both the framework unified response ({@code code}/{@code msg}/{@code data})
 * and the Spring default error body ({@code status}/{@code message}/{@code path}).</p>
 * <p>This type is a single-purpose parser for error mapping; it must not be reused
 * as a general purpose DTO.</p>
 *
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
public class GXWebClientErrorResDto {
    private @Nullable String msg;

    private @Nullable String message;

    private @Nullable Integer code;

    private @Nullable Integer status;

    public @Nullable String getMsg() {
        return msg;
    }

    public void setMsg(@Nullable String msg) {
        this.msg = msg;
    }

    public @Nullable String getMessage() {
        return message;
    }

    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    public @Nullable Integer getCode() {
        return code;
    }

    public void setCode(@Nullable Integer code) {
        this.code = code;
    }

    public @Nullable Integer getStatus() {
        return status;
    }

    public void setStatus(@Nullable Integer status) {
        this.status = status;
    }
}
