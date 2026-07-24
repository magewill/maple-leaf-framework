package cn.maple.core.framework.exception;

/**
 * 封装 Manticore 接口调用过程中的错误，rawResponse 保留原始响应体便于排查问题
 */
public class GXManticoreException extends RuntimeException {

    private final String rawResponse;

    public GXManticoreException(String message, String rawResponse) {
        super(message);
        this.rawResponse = rawResponse;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}