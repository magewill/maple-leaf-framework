package cn.maple.extension.exception;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.code.GXResultStatusCode;
import cn.maple.core.framework.exception.GXBusinessException;

/**
 * 扩展点异常类，用于处理扩展点框架中的异常情况
 * <p>
 * 该异常类用于以下场景：
 * 1. 扩展点初始化失败：例如扩展点实现类无法实例化
 * 2. 扩展点查找失败：例如找不到对应业务场景的扩展点实现
 * 3. 扩展点执行失败：例如扩展点方法执行过程中发生异常
 * <p>
 * 线程安全性：该类继承自GXBusinessException，是不可变的，所有字段都是final的，因此是线程安全的。
 * <p>
 * 使用示例：
 * <pre>
 * // 扩展点查找失败
 * try {
 *     PaymentExtPoint extension = extensionExecutor.findExtension(coordinate);
 *     if (extension == null) {
 *         throw new GXExtensionException("找不到对应的支付扩展点实现");
 *     }
 *     extension.pay(order);
 * } catch (GXExtensionException e) {
 *     // 处理异常
 *     log.error("扩展点异常: {}", e.getMsg(), e);
 * }
 * </pre>
 */
public class GXExtensionException extends GXBusinessException {
    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * 默认异常消息
     */
    private static final String MSG = "扩展点异常";

    /**
     * 创建扩展点异常对象（完整参数）
     * <p>
     * 该构造方法用于创建一个包含完整参数的扩展点异常对象
     *
     * @param msg  异常消息，详细描述异常原因
     * @param code 异常代码，用于标识异常类型
     * @param data 异常附加数据，可以包含更多的上下文信息
     * @param e    原始异常，用于保留异常堆栈
     */
    public GXExtensionException(String msg, int code, Dict data, Throwable e) {
        super(msg, code, data, e);
    }

    /**
     * 创建扩展点异常对象（无原始异常）
     * <p>
     * 该构造方法用于创建一个不包含原始异常的扩展点异常对象
     * 注意：该方法使用默认异常消息，而不是传入的消息
     *
     * @param msg  异常消息（注：实际使用默认消息）
     * @param code 异常代码，用于标识异常类型
     * @param data 异常附加数据，可以包含更多的上下文信息
     */
    public GXExtensionException(String msg, int code, Dict data) {
        this(MSG, code, data, null);
    }

    /**
     * 创建扩展点异常对象（无附加数据）
     * <p>
     * 该构造方法用于创建一个不包含附加数据的扩展点异常对象
     *
     * @param msg  异常消息，详细描述异常原因
     * @param code 异常代码，用于标识异常类型
     * @param e    原始异常，用于保留异常堆栈
     */
    public GXExtensionException(String msg, int code, Throwable e) {
        this(msg, code, Dict.create(), e);
    }

    /**
     * 创建扩展点异常对象（简化参数）
     * <p>
     * 该构造方法用于创建一个只包含消息和代码的扩展点异常对象
     *
     * @param msg  异常消息，详细描述异常原因
     * @param code 异常代码，用于标识异常类型
     */
    public GXExtensionException(String msg, int code) {
        this(msg, code, Dict.create(), null);
    }

    /**
     * 创建扩展点异常对象（使用HTTP_OK状态码）
     * <p>
     * 该构造方法用于创建一个使用HTTP_OK状态码的扩展点异常对象
     *
     * @param msg 异常消息，详细描述异常原因
     * @param e   原始异常，用于保留异常堆栈
     */
    public GXExtensionException(String msg, Throwable e) {
        this(msg, HttpStatus.HTTP_OK, e);
    }

    /**
     * 创建扩展点异常对象（最简参数）
     * <p>
     * 该构造方法用于创建一个只包含消息的扩展点异常对象，使用HTTP_OK状态码
     *
     * @param msg 异常消息，详细描述异常原因
     */
    public GXExtensionException(String msg) {
        super(msg, HttpStatus.HTTP_OK, Dict.create(), null);
    }
    
    /**
     * 创建扩展点异常对象（使用结果状态码）
     * <p>
     * 该构造方法用于创建一个使用预定义结果状态码的扩展点异常对象
     *
     * @param resultCode 结果状态码，包含代码、消息和附加数据
     */
    public GXExtensionException(GXResultStatusCode resultCode) {
        super(resultCode);
    }
    
    /**
     * 创建扩展点异常对象（使用结果状态码和附加消息）
     * <p>
     * 该构造方法用于创建一个使用预定义结果状态码和附加消息的扩展点异常对象
     *
     * @param resultCode 结果状态码，包含代码、消息和附加数据
     * @param msg        附加消息，会与结果状态码的消息拼接
     */
    public GXExtensionException(GXResultStatusCode resultCode, String msg) {
        super(resultCode, msg);
    }
    
    /**
     * 创建扩展点异常对象（使用结果状态码和原始异常）
     * <p>
     * 该构造方法用于创建一个使用预定义结果状态码和原始异常的扩展点异常对象
     *
     * @param resultCode 结果状态码，包含代码、消息和附加数据
     * @param e          原始异常，用于保留异常堆栈
     */
    public GXExtensionException(GXResultStatusCode resultCode, Throwable e) {
        super(resultCode, e);
    }
}