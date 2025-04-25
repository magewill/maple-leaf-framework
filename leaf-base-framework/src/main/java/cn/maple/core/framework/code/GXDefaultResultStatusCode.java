package cn.maple.core.framework.code;

import cn.hutool.http.HttpStatus;
import lombok.Getter;

/**
 * 默认结果状态码枚举类
 * <p>
 * 该枚举类定义了系统中默认的结果状态码，包括成功状态、错误状态等。
 * 每个状态码都包含了代码值和提示信息。
 * 实现了GXResultStatusCode接口，可以在业务逻辑中统一处理状态码。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在业务逻辑中使用状态码
 * GXResultStatusCode statusCode = GXDefaultResultStatusCode.OK;
 * int code = statusCode.getCode(); // 获取状态码值
 * String message = statusCode.getMsg(); // 获取提示信息
 * 
 * // 2. 在响应结果中使用
 * return new GXResultEntity<>(GXDefaultResultStatusCode.OK, data);
 * 
 * // 3. 在异常处理中使用
 * throw new GXBusinessException(GXDefaultResultStatusCode.DATA_NOT_FOUND);
 * </pre>
 *
 * @author maple-leaf-framework
 */
@Getter
public enum GXDefaultResultStatusCode implements GXResultStatusCode {
    /** 操作成功 */
    OK(HttpStatus.HTTP_OK, "操作成功"),
    
    /** 短信发送成功 */
    SMS_SEND_SUCCESS(HttpStatus.HTTP_OK, "短信发送成功"),
    
    /** 短信发送失败 */
    SMS_SEND_FAILURE(HttpStatus.HTTP_OK, "短信发送失败"),
    
    /** 内部系统错误 */
    INTERNAL_SYSTEM_ERROR(HttpStatus.HTTP_INTERNAL_ERROR, "内部系统错误"),
    
    /** 输入内容过短或为空 */
    INPUT_TOO_SHORT(0x500, "输入为空,或者输入字数不够!"),
    
    /** 数据对象不存在 */
    DATA_NOT_FOUND(0x510, "数据对象不存在"),
    
    /** 短信验证码错误 */
    SMS_CAPTCHA_ERROR(0x520, "短信验证码错误"),
    
    /** 图形验证码错误 */
    GRAPH_CAPTCHA_ERROR(0x530, "图形验证码错误"),
    
    /** 手机号格式错误 */
    WRONG_PHONE(0x540, "手机号有误"),
    
    /** 权限不足 */
    NEED_PERMISSION(0x550, "权限不足"),
    
    /** 登录错误 */
    LOGIN_ERROR(0x560, "登录错误,账号或者密码错误!"),
    
    /** 状态错误 */
    STATUS_ERROR(0x570, "当前状态不正确"),
    
    /** 需要图形验证码 */
    NEED_GRAPH_CAPTCHA(0x580, "请传递图形验证码"),
    
    /** 用户名已存在 */
    USER_NAME_EXIST(0x600, "用户名已存在"),
    
    /** 参数验证错误 */
    PARAMETER_VALIDATION_ERROR(0x610, "参数验证错误"),
    
    /** 登录令牌过期 */
    TOKEN_TIMEOUT_EXIT(0x620, "登录信息已过期,请重新登录"),
    
    /** 文件格式错误 */
    FILE_ERROR(0x630, "不正确的文件格式"),
    
    /** JSON解析错误 */
    PARSE_REQUEST_JSON_ERROR(0x640, "解析请求的JSON参数出错"),
    
    /** JSON参数为空 */
    REQUEST_JSON_NOT_BODY(0x650, "请求的JSON参数出错为空"),
    
    /** 账号被冻结 */
    ACCOUNT_FREEZE(0x660, "账号被冻结"),
    
    /** 用户不存在 */
    USER_NOT_EXISTS(0x670, "用户不存在"),
    
    /** 通用业务错误 */
    COMMON_ERROR(0x1000, "业务处理错误");

    /**
     * 状态提示信息
     */
    private final String msg;

    /**
     * 状态码值
     */
    private final int code;

    /**
     * 构造函数
     *
     * @param code 状态码值，可以使用HTTP状态码或自定义状态码
     * @param msg  状态提示信息，用于前端展示
     */
    GXDefaultResultStatusCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
