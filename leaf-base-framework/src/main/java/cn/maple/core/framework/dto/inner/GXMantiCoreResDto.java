package cn.maple.core.framework.dto.inner;

import cn.maple.core.framework.dto.res.GXBaseResDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Manticore HTTP API 调用结果。
 *
 * @param <T> 已解析的响应内容类型
 */
@EqualsAndHashCode(callSuper = true)
@Data
public final class GXMantiCoreResDto<T> extends GXBaseResDto {
    private final boolean success;
    private final Integer statusCode;
    private final T data;
    private final String errorMessage;

    private GXMantiCoreResDto(boolean success, Integer statusCode, T data, String errorMessage) {
        this.success = success;
        this.statusCode = statusCode;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    public static <T> GXMantiCoreResDto<T> success(Integer statusCode, T data) {
        return new GXMantiCoreResDto<>(true, statusCode, data, "");
    }

    public static <T> GXMantiCoreResDto<T> failure(Integer statusCode, T data, String errorMessage) {
        return new GXMantiCoreResDto<>(false, statusCode, data, errorMessage);
    }
}
