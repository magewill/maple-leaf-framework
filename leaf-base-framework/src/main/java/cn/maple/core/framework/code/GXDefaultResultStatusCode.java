package cn.maple.core.framework.code;

import cn.hutool.http.HttpStatus;
import lombok.Getter;

@Getter
public enum GXDefaultResultStatusCode implements GXResultStatusCode {
    OK(HttpStatus.HTTP_OK, "操作成功"),

    SMS_SEND_SUCCESS(HttpStatus.HTTP_OK, "短信发送成功"),

    SMS_SEND_FAILURE(HttpStatus.HTTP_OK, "短信发送失败"),

    INTERNAL_SYSTEM_ERROR(HttpStatus.HTTP_INTERNAL_ERROR, "内部系统错误"),

    INPUT_TOO_SHORT(0x500, "输入为空,或者输入字数不够!"),

    DATA_NOT_FOUND(0x510, "数据对象不存在"),

    SMS_CAPTCHA_ERROR(0x520, "短信验证码错误"),

    GRAPH_CAPTCHA_ERROR(0x530, "图形验证码错误"),

    WRONG_PHONE(0x540, "手机号有误"),

    NEED_PERMISSION(0x550, "权限不足"),

    LOGIN_ERROR(0x560, "登录错误,账号或者密码错误!"),

    STATUS_ERROR(0x570, "当前状态不正确"),

    NEED_GRAPH_CAPTCHA(0x580, "请传递图形验证码"),

    USER_NAME_EXIST(0x600, "用户名已存在"),

    PARAMETER_VALIDATION_ERROR(0x610, "参数验证错误"),

    TOKEN_TIMEOUT_EXIT(0x620, "登录信息已过期,请重新登录"),

    FILE_ERROR(0x630, "不正确的文件格式"),

    PARSE_REQUEST_JSON_ERROR(0x640, "解析请求的JSON参数出错"),

    REQUEST_JSON_NOT_BODY(0x650, "请求的JSON参数出错为空"),

    ACCOUNT_FREEZE(0x660, "账号被冻结"),

    USER_NOT_EXISTS(0x670, "用户不存在"),

    COMMON_ERROR(0x1000, "业务处理错误");

    private final String msg;

    private final int code;

    GXDefaultResultStatusCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
