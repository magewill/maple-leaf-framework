package cn.maple.core.framework.exception;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.code.GXResultStatusCode;

public class GXRemoteServiceException extends GXBusinessException {
    public GXRemoteServiceException(String msg, int code, Dict data, Throwable e) {
        super(msg, code, data, e);
    }

    public GXRemoteServiceException(String msg, int code, Dict data) {
        super(msg, code, data);
    }

    public GXRemoteServiceException(String msg, int code, Throwable e) {
        super(msg, code, e);
    }

    public GXRemoteServiceException(String msg, int code) {
        super(msg, code);
    }

    public GXRemoteServiceException(String msg) {
        super(msg);
    }

    public GXRemoteServiceException(String msg, Throwable e) {
        super(msg, e);
    }

    public GXRemoteServiceException(GXResultStatusCode resultCode) {
        super(resultCode);
    }

    public GXRemoteServiceException(GXResultStatusCode resultCode, String msg) {
        super(resultCode, msg);
    }

    public GXRemoteServiceException(GXResultStatusCode resultCode, Throwable e) {
        super(resultCode, e);
    }

    public GXRemoteServiceException(GXResultStatusCode resultCode, String msg, Dict data) {
        super(resultCode, msg, data);
    }

    public GXRemoteServiceException(GXResultStatusCode resultCode, Throwable e, Dict data) {
        super(resultCode, e, data);
    }

    public GXRemoteServiceException(GXResultStatusCode resultCode, String msg, Dict data, Throwable e) {
        super(resultCode, msg, data, e);
    }
}
