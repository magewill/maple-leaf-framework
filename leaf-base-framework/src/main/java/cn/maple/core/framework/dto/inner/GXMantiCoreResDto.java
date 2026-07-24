package cn.maple.core.framework.dto.inner;

import cn.maple.core.framework.dto.req.GXBaseReqDto;
import lombok.Getter;

/**
 * Manticore HTTP API 调用结果。
 *
 * @param <T> 已解析的响应内容类型
 */
@Getter
public final class GXMantiCoreResDto<T> extends GXBaseReqDto {
    private final boolean success;
    private final Integer statusCode;
    private final T data;
    private final String rawBody;
    private final String errorMessage;

    private GXMantiCoreResDto(boolean success, Integer statusCode, T data, String rawBody,
                              String errorMessage) {
        this.success = success;
        this.statusCode = statusCode;
        this.data = data;
        this.rawBody = rawBody;
        this.errorMessage = errorMessage;
    }

    public static <T> GXMantiCoreResDto<T> success(Integer statusCode, T data, String rawBody) {
        return new GXMantiCoreResDto<>(true, statusCode, data, rawBody, null);
    }

    public static <T> GXMantiCoreResDto<T> failure(Integer statusCode, T data, String rawBody,
                                                   String errorMessage) {
        return new GXMantiCoreResDto<>(false, statusCode, data, rawBody, errorMessage);
    }

}
