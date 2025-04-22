package cn.maple.sso.web.handler;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * SSO 默认拦截处理器，自定义 Handler 可继承该类。
 * <p>
 * 该处理器负责处理未登录用户的请求响应，主要功能包括：
 * 1. 处理AJAX请求的未登录情况
 * 2. 处理普通HTTP请求的未登录情况
 * 3. 提供单例模式的默认实现
 * </p>
 * <p>
 * 安全说明：
 * - 对未登录用户提供标准的401响应
 * - 支持自定义处理逻辑
 * - 使用单例模式减少对象创建
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-15
 */
public class GXSSODefaultHandler implements GXSSOHandler {
    /**
     * 默认处理器单例
     * <p>
     * 使用单例模式减少对象创建，提高性能
     * </p>
     */
    private static GXSSODefaultHandler handler;

    /**
     * 获取当前对象的单例实例
     * <p>
     * 线程安全的懒加载单例模式
     * </p>
     *
     * @return 默认处理器实例
     */
    public static GXSSODefaultHandler getInstance() {
        if (handler == null) {
            synchronized (GXSSODefaultHandler.class) {
                if (handler == null) {
                    handler = new GXSSODefaultHandler();
                }
            }
        }
        return handler;
    }

    /**
     * 处理未登录的AJAX请求
     * <p>
     * 返回标准的JSON格式响应，包含以下信息：
     * - HTTP状态码：401（未授权）
     * - 错误消息：已经登出，请重新登录
     * - 数据：null
     * </p>
     * <p>
     * 安全说明：
     * - 使用标准的HTTP状态码表示未授权
     * - 提供友好的错误消息
     * - 异常处理确保响应稳定性
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @return 始终返回false，表示拦截请求
     */
    public boolean preTokenIsNullAjax(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 构建标准的JSON响应
            Dict data = Dict.create()
                    .set("code", HttpStatus.HTTP_UNAUTHORIZED)
                    .set("msg", "已经登出,请重新登录!")
                    .set("data", null);

            // 配置JSON序列化选项
            JSONConfig jsonConfig = new JSONConfig();
            jsonConfig.setIgnoreNullValue(false);

            // 设置响应内容类型并写入响应
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSONUtil.toJsonStr(data, jsonConfig));
        } catch (IOException e) {
            // 异常处理，保持静默以确保不影响响应流程
            // 可以考虑添加日志记录：log.error("处理未登录AJAX请求时发生IO异常", e);
        }
        return false; // 拦截请求
    }

    /**
     * 处理未登录的普通HTTP请求
     * <p>
     * 默认实现返回true，表示继续执行后续的重定向逻辑
     * 子类可以重写此方法提供自定义处理逻辑
     * </p>
     * <p>
     * 安全说明：
     * - 提供扩展点允许自定义处理
     * - 默认行为确保用户被重定向到登录页面
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @return 默认返回true，表示继续执行后续的重定向逻辑
     */
    public boolean preTokenIsNull(HttpServletRequest request, HttpServletResponse response) {
        // 预留给子类实现自定义处理逻辑
        return true; // 继续执行后续的重定向逻辑
    }
}
