package cn.maple.core.framework.exception;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.code.GXResultStatusCode;

/**
 * 框架数据转换异常
 */
public class GXConvertException extends GXBusinessException {

    public GXConvertException(String msg, int code, Dict data, Throwable e) {
        super(msg, code, data, e);
    }

    public GXConvertException(GXResultStatusCode resultCode, String msg, Dict data, Throwable e) {
        super(resultCode, msg, data, e);
    }

    public GXConvertException(GXResultStatusCode resultCode, Throwable e, Dict data) {
        super(resultCode, e, data);
    }

    public GXConvertException(GXResultStatusCode resultCode, String msg, Dict data) {
        super(resultCode, msg, data);
    }

    public GXConvertException(GXResultStatusCode resultCode, Throwable e) {
        super(resultCode, e);
    }

    public GXConvertException(GXResultStatusCode resultCode, String msg) {
        super(resultCode, msg);
    }

    public GXConvertException(GXResultStatusCode resultCode) {
        super(resultCode);
    }

    public GXConvertException(String msg, Throwable e) {
        super(msg, e);
    }

    public GXConvertException(String msg) {
        super(msg);
    }

    public GXConvertException(String msg, int code) {
        super(msg, code);
    }

    public GXConvertException(String msg, int code, Throwable e) {
        super(msg, code, e);
    }

    public GXConvertException(String msg, int code, Dict data) {
        super(msg, code, data);
    }
}
