package cn.maple.statemachine.impl;

import lombok.extern.slf4j.Slf4j;

/**
 * 状态机调试工具类
 * <p>
 * 该类用于解耦日志框架依赖，提供统一的调试日志输出接口。
 * 通过开启或关闭调试模式，可以控制状态机内部日志的输出，便于开发和问题排查。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 开启调试模式
 * GXDebugger.enableDebug();
 *
 * // 输出调试信息
 * GXDebugger.debug("状态转换：从待支付状态转换到已支付状态");
 *
 * // 关闭调试模式
 * GXDebugger.disableDebug();
 * }
 * </pre>
 * </p>
 */
@Slf4j
public class GXDebugger {
    /**
     * 调试模式开关，默认关闭
     */
    private static volatile boolean isDebugOn = false;

    /**
     * 私有构造函数，防止实例化
     */
    private GXDebugger() {
        // 工具类不应被实例化
    }

    /**
     * 输出调试日志
     * <p>
     * 只有在调试模式开启时才会输出日志
     * </p>
     *
     * @param message 调试信息
     */
    public static void debug(String message) {
        if (isDebugOn) {
            log.debug(message);
        }
    }

    /**
     * 开启调试模式
     */
    public static void enableDebug() {
        isDebugOn = true;
    }

    /**
     * 关闭调试模式
     */
    public static void disableDebug() {
        isDebugOn = false;
    }

    /**
     * 获取当前调试模式状态
     *
     * @return 调试模式是否开启
     */
    public static boolean isDebugEnabled() {
        return isDebugOn;
    }
}
