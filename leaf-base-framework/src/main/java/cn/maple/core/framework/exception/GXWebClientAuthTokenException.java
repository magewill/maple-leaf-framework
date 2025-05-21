package cn.maple.core.framework.exception;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.code.GXResultStatusCode;

public class GXWebClientAuthTokenException  extends GXBusinessException{
    public GXWebClientAuthTokenException(String msg, int code, Dict data, Throwable e) {
        super(msg, code, data, e);
    }

    public GXWebClientAuthTokenException(String msg, int code, Dict data) {
        super(msg, code, data);
    }

    public GXWebClientAuthTokenException(String msg, int code, Throwable e) {
        super(msg, code, e);
    }

    public GXWebClientAuthTokenException(String msg, int code) {
        super(msg, code);
    }

    public GXWebClientAuthTokenException(String msg) {
        super(msg);
    }

    public GXWebClientAuthTokenException(String msg, Throwable e) {
        super(msg, e);
    }

    public GXWebClientAuthTokenException(GXResultStatusCode resultCode) {
        super(resultCode);
    }

    public GXWebClientAuthTokenException(GXResultStatusCode resultCode, String msg) {
        super(resultCode, msg);
    }

    public GXWebClientAuthTokenException(GXResultStatusCode resultCode, Throwable e) {
        super(resultCode, e);
    }

    public GXWebClientAuthTokenException(GXResultStatusCode resultCode, String msg, Dict data) {
        super(resultCode, msg, data);
    }

    public GXWebClientAuthTokenException(GXResultStatusCode resultCode, Throwable e, Dict data) {
        super(resultCode, e, data);
    }

    public GXWebClientAuthTokenException(GXResultStatusCode resultCode, String msg, Dict data, Throwable e) {
        super(resultCode, msg, data, e);
    }
}
