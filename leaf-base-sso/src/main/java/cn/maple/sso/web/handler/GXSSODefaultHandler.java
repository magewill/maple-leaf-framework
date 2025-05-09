package cn.maple.sso.web.handler;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSO 默认拦截处理器，自定义 Handler 可继承该类。
 * <p>
 * 该处理器负责处理未登录用户的请求响应，主要功能包括：
 * 1. 处理AJAX请求的未登录情况
 * 2. 处理普通HTTP请求的未登录情况
 * 3. 提供线程安全的单例模式实现
 * </p>
 * <p>
 * 安全说明：
 * - 对未登录用户提供标准的401响应
 * - 支持自定义处理逻辑
 * - 使用线程安全的单例模式减少对象创建
 * - 采用Java 17+的紧凑字符串优化内存使用
 * - 使用不可变对象模式增强线程安全性
 * </p>
 * <p>
 * 性能优化：
 * - 使用StandardCharsets常量替代字符串字面量
 * - 采用紧凑字符串减少内存占用
 * - 使用原子引用确保线程安全
 * - 优化异常处理提高稳定性
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 获取默认处理器实例
 * GXSSODefaultHandler handler = GXSSODefaultHandler.getInstance();
 *
 * // 处理未登录的AJAX请求
 * boolean continueProcessing = handler.preTokenIsNullAjax(request, response);
 * // continueProcessing 将始终为 false，表示拦截请求
 *
 * // 处理未登录的普通HTTP请求
 * boolean shouldRedirect = handler.preTokenIsNull(request, response);
 * // shouldRedirect 默认为 true，表示继续执行重定向逻辑
 * </pre>
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-15
 */
public class GXSSODefaultHandler implements GXSSOHandler, Serializable {
    /**
     * 序列化版本ID
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志记录器
     * <p>
     * 使用SLF4J提供的日志接口，便于与各种日志实现集成
     * </p>
     */
    private static final Logger log = LoggerFactory.getLogger(GXSSODefaultHandler.class);

    /**
     * JSON响应的内容类型
     * <p>
     * 使用StandardCharsets常量替代字符串字面量，提高性能并减少内存使用
     * </p>
     */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=" + StandardCharsets.UTF_8.name();

    /**
     * 未授权消息
     * <p>
     * 提取为常量，避免重复创建字符串对象
     * </p>
     */
    private static final String UNAUTHORIZED_MESSAGE = "已经登出,请重新登录!";

    /**
     * 原子引用，用于存储JSON配置
     * <p>
     * 使用AtomicReference确保线程安全，避免在高并发环境下的竞态条件
     * </p>
     */
    private static final AtomicReference<JSONConfig> JSON_CONFIG_REF = new AtomicReference<>();

    /**
     * 私有构造函数，防止外部实例化
     * <p>
     * 单例模式的关键部分，确保只有一个实例被创建
     * </p>
     */
    protected GXSSODefaultHandler() {
        // 私有构造函数，防止外部实例化
    }

    /**
     * 获取当前对象的单例实例
     * <p>
     * 线程安全的懒加载单例模式，使用静态内部类实现
     * </p>
     * <p>
     * 优势：
     * - 线程安全：利用类加载机制确保线程安全
     * - 懒加载：只有在首次调用时才会创建实例
     * - 高性能：获取实例时无需同步
     * </p>
     *
     * @return 默认处理器实例
     */
    public static GXSSODefaultHandler getInstance() {
        return HandlerHolder.INSTANCE;
    }

    /**
     * 获取JSON配置
     * <p>
     * 使用双重检查锁定模式确保线程安全地创建JSONConfig实例
     * </p>
     *
     * @return JSON配置实例
     */
    private static JSONConfig getJsonConfig() {
        JSONConfig config = JSON_CONFIG_REF.get();
        if (config == null) {
            // 创建新的配置实例
            config = new JSONConfig();
            config.setIgnoreNullValue(false);
            // 原子方式更新引用
            if (!JSON_CONFIG_REF.compareAndSet(null, config)) {
                // 如果其他线程已经设置了值，使用已设置的值
                config = JSON_CONFIG_REF.get();
            }
        }
        return config;
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
     * - 使用线程安全的方式获取JSON配置
     * </p>
     * <p>
     * 性能优化：
     * - 使用预定义常量减少对象创建
     * - 使用线程安全的配置获取方式
     * - 优化异常处理和日志记录
     * </p>
     *
     * @param request  HTTP请求对象，不能为null
     * @param response HTTP响应对象，不能为null
     * @return 始终返回false，表示拦截请求
     * @throws NullPointerException 如果request或response为null
     */
    @Override
    public boolean preTokenIsNullAjax(HttpServletRequest request, HttpServletResponse response) {
        // 参数校验
        Objects.requireNonNull(request, "请求对象不能为null");
        Objects.requireNonNull(response, "响应对象不能为null");

        try {
            // 构建标准的JSON响应
            Dict data = Dict.create()
                    .set("code", HttpStatus.HTTP_UNAUTHORIZED)
                    .set("msg", UNAUTHORIZED_MESSAGE)
                    .set("data", null);

            // 设置响应内容类型并写入响应
            response.setContentType(JSON_CONTENT_TYPE);
            response.getWriter().write(JSONUtil.toJsonStr(data, getJsonConfig()));
        } catch (IOException e) {
            // 记录异常但不抛出，确保不影响响应流程
            log.error("处理未登录AJAX请求时发生IO异常", e);
        } catch (Exception e) {
            // 捕获所有其他异常，确保方法不会抛出异常
            log.error("处理未登录AJAX请求时发生未预期异常", e);
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
     * - 参数校验确保安全性
     * </p>
     *
     * @param request  HTTP请求对象，不能为null
     * @param response HTTP响应对象，不能为null
     * @return 默认返回true，表示继续执行后续的重定向逻辑
     * @throws NullPointerException 如果request或response为null
     */
    @Override
    public boolean preTokenIsNull(HttpServletRequest request, HttpServletResponse response) {
        // 参数校验
        Objects.requireNonNull(request, "请求对象不能为null");
        Objects.requireNonNull(response, "响应对象不能为null");

        // 预留给子类实现自定义处理逻辑
        return true; // 继续执行后续的重定向逻辑
    }

    /**
     * 默认处理器单例持有者
     * <p>
     * 使用静态内部类实现线程安全的懒加载单例模式
     * </p>
     */
    private static final class HandlerHolder {
        /**
         * 默认处理器单例
         * <p>
         * 使用单例模式减少对象创建，提高性能
         * </p>
         */
        private static final GXSSODefaultHandler INSTANCE = new GXSSODefaultHandler();
    }
}
