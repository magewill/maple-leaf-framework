package cn.maple.extension.exception;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.code.GXResultStatusCode;
import cn.maple.core.framework.exception.GXBusinessException;

import java.io.Serial;

/**
 * Business exception used by the extension framework.
 */
public class GXExtensionException extends GXBusinessException {
    @Serial
    private static final long serialVersionUID = 1L;
    
    public GXExtensionException(String msg, int code, Dict data, Throwable e) {
        super(msg, code, data, e);
    }

    public GXExtensionException(String msg, int code, Dict data) {
        this(msg, code, data, null);
    }

    public GXExtensionException(String msg, int code, Throwable e) {
        this(msg, code, Dict.create(), e);
    }

    public GXExtensionException(String msg, int code) {
        this(msg, code, Dict.create(), null);
    }

    public GXExtensionException(String msg, Throwable e) {
        this(msg, HttpStatus.HTTP_OK, e);
    }

    public GXExtensionException(String msg) {
        super(msg, HttpStatus.HTTP_OK, Dict.create(), null);
    }
    
    public GXExtensionException(GXResultStatusCode resultCode) {
        super(resultCode);
    }
    
    public GXExtensionException(GXResultStatusCode resultCode, String msg) {
        super(resultCode, msg);
    }
    
    public GXExtensionException(GXResultStatusCode resultCode, Throwable e) {
        super(resultCode, e);
    }
}
