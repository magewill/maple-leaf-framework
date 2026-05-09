package cn.maple.core.framework.util;

import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.code.GXDefaultResultStatusCode;
import cn.maple.core.framework.exception.GXBusinessException;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class GXResultUtils<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final String FAIL_MSG = "fail";

    private static final String SUCCESS_MSG = "success";

    private static final int SUCCESS_CODE = HttpStatus.HTTP_OK;

    private static final int FAIL_CODE = HttpStatus.HTTP_INTERNAL_ERROR;

    private int code = SUCCESS_CODE;

    private String msg = SUCCESS_MSG;

    private T data = null;

    public static <T> GXResultUtils<T> ok(GXDefaultResultStatusCode resultCode) {
        if (resultCode == null) {
            return ok();
        }
        return ok(resultCode.getCode(), resultCode.getMsg());
    }

    public static <T> GXResultUtils<T> ok(GXDefaultResultStatusCode resultCode, T data) {
        if (resultCode == null) {
            return ok(data);
        }
        return ok(resultCode.getCode(), resultCode.getMsg(), data);
    }

    public static <T> GXResultUtils<T> ok(String msg) {
        return ok(SUCCESS_CODE, msg, null);
    }

    public static <T> GXResultUtils<T> ok(int code, String msg) {
        return ok(code, msg, null);
    }

    public static <T> GXResultUtils<T> ok(int code) {
        return ok(code, SUCCESS_MSG, null);
    }

    public static <T> GXResultUtils<T> ok(T data) {
        return ok(SUCCESS_CODE, SUCCESS_MSG, data);
    }

    public static <T> GXResultUtils<T> ok(String msg, T data) {
        return ok(SUCCESS_CODE, msg, data);
    }

    public static <T> GXResultUtils<T> ok(int code, String msg, T data) {
        GXResultUtils<T> r = new GXResultUtils<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    public static <T> GXResultUtils<T> ok() {
        return new GXResultUtils<>();
    }

    public static <T> GXResultUtils<T> error() {
        return error(FAIL_CODE, "未知异常，请联系管理员");
    }

    public static <T> GXResultUtils<T> error(T data) {
        return error(FAIL_CODE, FAIL_MSG, data);
    }

    public static <T> GXResultUtils<T> error(int code, Throwable throwable) {
        if (throwable == null) {
            return error(code);
        }
        return error(code, throwable.getMessage(), null);
    }

    public static <T> GXResultUtils<T> error(GXBusinessException e) {
        if (e == null) {
            return error();
        }
        return error(e.getCode(), e.getMessage(), null);
    }

    public static <T> GXResultUtils<T> error(String msg) {
        return error(FAIL_CODE, msg);
    }

    public static <T> GXResultUtils<T> error(int code) {
        return error(code, FAIL_MSG, null);
    }

    public static <T> GXResultUtils<T> error(int code, String msg) {
        return error(code, msg, null);
    }

    public static <T> GXResultUtils<T> error(GXDefaultResultStatusCode resultCode) {
        if (resultCode == null) {
            return error();
        }
        return error(resultCode.getCode(), resultCode.getMsg());
    }

    public static <T> GXResultUtils<T> error(GXDefaultResultStatusCode resultCode, T data) {
        if (resultCode == null) {
            return error(data);
        }
        return error(resultCode.getCode(), resultCode.getMsg(), data);
    }

    public static <T> GXResultUtils<T> error(int code, String msg, T data) {
        GXResultUtils<T> r = new GXResultUtils<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
