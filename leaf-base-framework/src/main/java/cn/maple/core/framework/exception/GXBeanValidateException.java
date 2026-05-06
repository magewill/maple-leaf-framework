package cn.maple.core.framework.exception;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.code.GXResultStatusCode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
public class GXBeanValidateException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String msg;

    private final int code;

    private final Dict data;

    public GXBeanValidateException(String msg, int code, Dict data, Throwable e) {
        super(msg, e);
        this.msg = msg;
        this.code = code;
        this.data = data;
    }

    public GXBeanValidateException(String msg, int code, Dict data) {
        this(msg, code, data, null);
    }

    public GXBeanValidateException(String msg, int code, Throwable e) {
        this(msg, code, Dict.create(), e);
    }

    public GXBeanValidateException(String msg, int code) {
        this(msg, code, (Dict) null);
    }

    public GXBeanValidateException(String msg) {
        this(msg, HttpStatus.HTTP_INTERNAL_ERROR);
    }

    public GXBeanValidateException(String msg, Throwable e) {
        this(msg, HttpStatus.HTTP_INTERNAL_ERROR, e);
    }

    public GXBeanValidateException(GXResultStatusCode resultCode) {
        this(resultCode, "");
    }

    public GXBeanValidateException(GXResultStatusCode resultCode, @NotNull String msg) {
        this(resultCode, msg, Dict.create());
    }

    public GXBeanValidateException(GXResultStatusCode resultCode, Throwable e) {
        this(resultCode, e, Dict.create());
    }

    public GXBeanValidateException(GXResultStatusCode resultCode, @NotNull String msg, @NotNull Dict data) {
        this(resultCode, msg, data, null);
    }

    public GXBeanValidateException(GXResultStatusCode resultCode, Throwable e, @NotNull Dict data) {
        this(resultCode, "", data, e);
    }

    public GXBeanValidateException(GXResultStatusCode resultCode, @NotNull String msg, @NotNull Dict data, Throwable e) {
        super(CharSequenceUtil.format("{}{}", msg, resultCode.getMsg()), e);
        this.msg = CharSequenceUtil.format("{}{}", msg, resultCode.getMsg());
        this.code = resultCode.getCode();
        if (MapUtil.isEmpty(data)) {
            data = Dict.create();
        }
        data.putAll(resultCode.getExtraData());
        this.data = data;
    }
}
