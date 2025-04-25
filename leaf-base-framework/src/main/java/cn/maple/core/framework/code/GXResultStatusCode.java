package cn.maple.core.framework.code;

import cn.hutool.core.lang.Dict;

/**
 * 结果状态码接口
 * <p>
 * 该接口定义了系统中所有状态码的基本行为，包括获取状态码、获取提示信息和获取额外数据。
 * 系统中的所有状态码枚举类都应该实现此接口，以保证统一的状态码处理机制。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在枚举类中实现该接口
 * public enum MyStatusCode implements GXResultStatusCode {
 *     SUCCESS(200, "操作成功"),
 *     ERROR(500, "操作失败");
 *     
 *     private final int code;
 *     private final String msg;
 *     
 *     MyStatusCode(int code, String msg) {
 *         this.code = code;
 *         this.msg = msg;
 *     }
 *     
 *     @Override
 *     public int getCode() {
 *         return code;
 *     }
 *     
 *     @Override
 *     public String getMsg() {
 *         return msg;
 *     }
 * }
 * 
 * // 2. 在业务代码中使用
 * GXResultStatusCode statusCode = MyStatusCode.SUCCESS;
 * int code = statusCode.getCode(); // 获取状态码
 * String message = statusCode.getMsg(); // 获取提示信息
 * Dict extraData = statusCode.getExtraData(); // 获取额外数据
 * </pre>
 *
 * @author maple-leaf-framework
 */
@SuppressWarnings("all")
public interface GXResultStatusCode {
    /**
     * 获取状态码
     * <p>
     * 状态码用于标识操作的结果状态，通常用于前端判断操作是否成功。
     * 建议使用HTTP状态码或自定义的业务状态码。
     * </p>
     *
     * @return 状态码整数值
     */
    int getCode();

    /**
     * 获取提示信息
     * <p>
     * 提示信息用于描述操作结果，通常用于前端展示给用户。
     * 建议使用简洁明了的文字描述操作结果。
     * </p>
     *
     * @return 提示信息字符串
     */
    String getMsg();

    /**
     * 获取额外信息
     * <p>
     * 额外信息用于存储与状态码相关的附加数据，可以在异常处理或响应结果中使用。
     * 默认返回空的Dict对象，子类可以重写此方法提供额外数据。
     * </p>
     *
     * @return 包含额外信息的Dict对象
     */
    default Dict getExtraData() {
        return Dict.create();
    }
}
