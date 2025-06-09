package cn.maple.core.framework.exception;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.code.GXResultStatusCode;

public class GXFeignAuthTokenException extends GXBusinessException {
    public GXFeignAuthTokenException(String msg, int code, Dict data, Throwable e) {
        super(msg, code, data, e);
    }

    public GXFeignAuthTokenException(String msg, int code, Dict data) {
        super(msg, code, data);
    }

    public GXFeignAuthTokenException(String msg, int code, Throwable e) {
        super(msg, code, e);
    }

    public GXFeignAuthTokenException(String msg, int code) {
        super(msg, code);
    }

    public GXFeignAuthTokenException(String msg) {
        super(msg);
    }

    public GXFeignAuthTokenException(String msg, Throwable e) {
        super(msg, e);
    }

    public GXFeignAuthTokenException(GXResultStatusCode resultCode) {
        super(resultCode);
    }

    public GXFeignAuthTokenException(GXResultStatusCode resultCode, String msg) {
        super(resultCode, msg);
    }

    public GXFeignAuthTokenException(GXResultStatusCode resultCode, Throwable e) {
        super(resultCode, e);
    }

    public GXFeignAuthTokenException(GXResultStatusCode resultCode, String msg, Dict data) {
        super(resultCode, msg, data);
    }

    public GXFeignAuthTokenException(GXResultStatusCode resultCode, Throwable e, Dict data) {
        super(resultCode, e, data);
    }

    public GXFeignAuthTokenException(GXResultStatusCode resultCode, String msg, Dict data, Throwable e) {
        super(resultCode, msg, data, e);
    }
}
