package cn.maple.core.framework.util;

import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.code.GXDefaultResultStatusCode;
import cn.maple.core.framework.exception.GXBusinessException;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一API响应结果封装工具类
 * <p>
 * 该工具类提供了一系列静态方法，用于创建标准格式的API响应对象。
 * 响应对象包含状态码、消息和数据三部分，便于前端统一处理。
 * </p>
 * <p>
 * 响应码规范：
 * - 200：操作成功
 * - 500：服务器内部错误
 * - 其他自定义状态码：参见GXDefaultResultStatusCode枚举
 * </p>
 * <p>
 * 线程安全说明：
 * - 所有方法均为静态方法，无状态设计
 * - 创建的GXResultUtils对象是不可变的，适合在多线程环境下使用
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 返回成功响应（无数据）
 * GXResultUtils<Object> result1 = GXResultUtils.ok();
 * 
 * // 2. 返回成功响应（带数据）
 * User user = userService.findById(1L);
 * GXResultUtils<User> result2 = GXResultUtils.ok(user);
 * 
 * // 3. 返回成功响应（自定义消息和数据）
 * GXResultUtils<User> result3 = GXResultUtils.ok("获取用户成功", user);
 * 
 * // 4. 返回错误响应（自定义消息）
 * GXResultUtils<Object> result4 = GXResultUtils.error("用户不存在");
 * 
 * // 5. 返回错误响应（使用预定义状态码）
 * GXResultUtils<Object> result5 = GXResultUtils.error(GXDefaultResultStatusCode.DATA_NOT_FOUND);
 * 
 * // 6. 处理业务异常
 * try {
 *     // 业务逻辑
 * } catch (GXBusinessException e) {
 *     return GXResultUtils.error(e);
 * }
 * </pre>
 * </p>
 * 
 * @author britton birtton@126.com
 * @param <T> 响应数据的类型
 */
@Data
public class GXResultUtils<T> implements Serializable {
    /**
     * 失败的默认提示消息
     * 当未指定具体错误消息时使用此默认值
     */
    private static final String FAIL_MSG = "fail";

    /**
     * 成功的默认提示消息
     * 当未指定具体成功消息时使用此默认值
     */
    private static final String SUCCESS_MSG = "success";

    /**
     * 成功的默认状态码
     * 使用HTTP标准状态码200表示成功
     */
    private static final int SUCCESS_CODE = HttpStatus.HTTP_OK;

    /**
     * 失败的默认状态码
     * 使用HTTP标准状态码500表示服务器内部错误
     */
    private static final int FAIL_CODE = HttpStatus.HTTP_INTERNAL_ERROR;

    /**
     * 返回状态码
     * 约定：200表示成功，其他状态码表示不同类型的失败
     * 默认为成功状态码
     */
    private int code = SUCCESS_CODE;

    /**
     * 响应消息
     * 用于向客户端传递操作结果的文字说明
     * 默认为成功消息
     */
    private String msg = SUCCESS_MSG;

    /**
     * 响应数据
     * 用于向客户端传递业务数据
     * 可以是任何类型，由泛型T指定
     */
    private T data = null;

    /**
     * 使用预定义状态码创建成功响应
     * 
     * @param resultCode 预定义的结果状态码
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> GXResultUtils<T> ok(GXDefaultResultStatusCode resultCode) {
        return ok(resultCode.getCode(), resultCode.getMsg());
    }

    /**
     * 使用预定义状态码和数据创建成功响应
     * 
     * @param resultCode 预定义的结果状态码
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> GXResultUtils<T> ok(GXDefaultResultStatusCode resultCode, T data) {
        return ok(resultCode.getCode(), resultCode.getMsg(), data);
    }

    /**
     * 使用自定义消息创建成功响应
     * 
     * @param msg 自定义成功消息
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> GXResultUtils<T> ok(String msg) {
        return ok(SUCCESS_CODE, msg, null);
    }

    /**
     * 使用自定义状态码和消息创建成功响应
     * 
     * @param code 自定义状态码
     * @param msg 自定义成功消息
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> GXResultUtils<T> ok(int code, String msg) {
        return ok(code, msg, null);
    }

    /**
     * 使用自定义状态码创建成功响应
     * 
     * @param code 自定义状态码
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> GXResultUtils<T> ok(int code) {
        return ok(code, SUCCESS_MSG, null);
    }

    /**
     * 使用数据创建成功响应
     * 
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> GXResultUtils<T> ok(T data) {
        return ok(SUCCESS_CODE, SUCCESS_MSG, data);
    }

    /**
     * 使用自定义消息和数据创建成功响应
     * 
     * @param msg 自定义成功消息
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> GXResultUtils<T> ok(String msg, T data) {
        return ok(SUCCESS_CODE, msg, data);
    }

    /**
     * 使用自定义状态码、消息和数据创建成功响应
     * 这是所有ok方法的基础方法，其他重载方法最终都会调用此方法
     * 
     * @param code 自定义状态码
     * @param msg 自定义成功消息
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> GXResultUtils<T> ok(int code, String msg, T data) {
        GXResultUtils<T> r = new GXResultUtils<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    /**
     * 创建默认的成功响应
     * 使用默认状态码和默认成功消息
     * 
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> GXResultUtils<T> ok() {
        return new GXResultUtils<>();
    }

    /**
     * 创建默认的错误响应
     * 使用默认错误状态码和通用错误消息
     * 
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> GXResultUtils<T> error() {
        return error(FAIL_CODE, "未知异常，请联系管理员");
    }

    /**
     * 创建带数据的错误响应
     * 使用默认错误状态码和默认错误消息
     * 
     * @param data 错误相关的数据
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> GXResultUtils<T> error(T data) {
        return error(FAIL_CODE, FAIL_MSG, data);
    }

    /**
     * 根据异常创建错误响应
     * 使用自定义状态码和异常消息
     * 
     * @param code 自定义错误状态码
     * @param throwable 异常对象
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> GXResultUtils<T> error(int code, Throwable throwable) {
        return error(code, throwable.getMessage(), null);
    }

    /**
     * 根据业务异常创建错误响应
     * 使用业务异常中的状态码和消息
     * 
     * @param e 业务异常对象
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> GXResultUtils<T> error(GXBusinessException e) {
        return error(e.getCode(), e.getMessage(), null);
    }

    /**
     * 使用自定义消息创建错误响应
     * 使用默认错误状态码
     * 
     * @param msg 自定义错误消息
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> GXResultUtils<T> error(String msg) {
        return error(FAIL_CODE, msg);
    }

    /**
     * 使用自定义状态码创建错误响应
     * 使用默认错误消息
     * 
     * @param code 自定义错误状态码
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> GXResultUtils<T> error(int code) {
        return error(code, FAIL_MSG, null);
    }

    /**
     * 使用自定义状态码和消息创建错误响应
     * 
     * @param code 自定义错误状态码
     * @param msg 自定义错误消息
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> GXResultUtils<T> error(int code, String msg) {
        return error(code, msg, null);
    }

    /**
     * 使用预定义状态码创建错误响应
     * 
     * @param resultCode 预定义的结果状态码
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> GXResultUtils<T> error(GXDefaultResultStatusCode resultCode) {
        return error(resultCode.getCode(), resultCode.getMsg());
    }

    /**
     * 使用预定义状态码和数据创建错误响应
     * 
     * @param resultCode 预定义的结果状态码
     * @param data 错误相关的数据
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> GXResultUtils<T> error(GXDefaultResultStatusCode resultCode, T data) {
        return error(resultCode.getCode(), resultCode.getMsg(), data);
    }

    /**
     * 使用自定义状态码、消息和数据创建错误响应
     * 这是所有error方法的基础方法，其他重载方法最终都会调用此方法
     * 
     * @param code 自定义错误状态码
     * @param msg 自定义错误消息
     * @param data 错误相关的数据
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    public static <T> GXResultUtils<T> error(int code, String msg, T data) {
        GXResultUtils<T> r = new GXResultUtils<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
