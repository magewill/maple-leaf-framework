package cn.maple.feign.codec;

import cn.hutool.core.util.StrUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Feign自定义错误解码器
 * <p>
 * 该解码器负责将Feign客户端接收到的HTTP错误响应转换为Java异常，便于统一的异常处理。
 * 针对不同的HTTP状态码，提供了定制化的异常信息，提高错误信息的可读性和可追踪性。
 * </p>
 *
 * <p>
 * 功能特性：
 * 1. 针对常见HTTP错误状态码（404、401、403等）提供特定的异常处理
 * 2. 尝试从响应体中提取详细错误信息，增强异常的上下文信息
 * 3. 对于未特殊处理的状态码，委托给默认解码器处理
 * 4. 记录详细的错误日志，便于问题排查
 * </p>
 *
 * <p>
 * 使用说明：
 * - 该解码器会被自动应用于所有Feign客户端
 * - 抛出的异常会被Spring的全局异常处理器捕获并处理
 * - 可以通过扩展该类来处理更多特定的HTTP状态码
 * </p>
 *
 * @author maple
 */
@Slf4j
public class GXFeignCustomErrorDecoder implements ErrorDecoder {
    /**
     * 默认错误解码器，用于处理未特殊处理的状态码
     */
    private final ErrorDecoder defaultErrorDecoder = new Default();

    /**
     * 解码HTTP错误响应为Java异常
     * <p>
     * 该方法会根据HTTP响应的状态码，将错误响应转换为相应的Java异常。
     * 针对特定的状态码（如404、401、403等），提供了定制化的异常信息。
     * 对于未特殊处理的状态码，委托给默认解码器处理。
     * </p>
     *
     * @param methodKey 调用的方法标识，通常是接口全限定名+方法名
     * @param response  HTTP响应对象，包含状态码、响应头和响应体
     * @return 转换后的Java异常
     */
    @Override
    public Exception decode(String methodKey, Response response) {
        log.debug("Decoding error response for method: {}, status: {}", methodKey, response.status());

        // 尝试从响应体中提取错误信息
        String errorMessage = extractErrorMessage(response);
        String requestUrl = response.request().url();
        String fullErrorMessage = StrUtil.format("调用远程服务失败 - 方法: {}, URL: {}, 状态码: {}, 错误信息: {}",
                methodKey, requestUrl, response.status(), errorMessage);

        // 根据HTTP状态码处理不同类型的错误
        return switch (response.status()) {
            case HttpStatus.NOT_FOUND -> {
                log.warn("Resource not found: {}", fullErrorMessage);
                yield new GXBusinessException("请求的资源不存在: " + requestUrl);
            }
            case HttpStatus.UNAUTHORIZED -> {
                log.warn("Unauthorized access: {}", fullErrorMessage);
                yield new GXBusinessException("未授权的访问，请检查认证信息");
            }
            case HttpStatus.FORBIDDEN -> {
                log.warn("Access forbidden: {}", fullErrorMessage);
                yield new GXBusinessException("禁止访问，权限不足");
            }
            case HttpStatus.BAD_REQUEST -> {
                log.warn("Bad request: {}", fullErrorMessage);
                yield new GXBusinessException("请求参数错误: " + errorMessage);
            }
            case HttpStatus.INTERNAL_SERVER_ERROR -> {
                log.error("Remote service error: {}", fullErrorMessage);
                yield new GXBusinessException("远程服务内部错误，请稍后重试");
            }
            case HttpStatus.SERVICE_UNAVAILABLE -> {
                log.error("Service unavailable: {}", fullErrorMessage);
                yield new GXBusinessException("远程服务不可用，请稍后重试");
            }
            case HttpStatus.GATEWAY_TIMEOUT -> {
                log.error("Gateway timeout: {}", fullErrorMessage);
                yield new GXBusinessException("远程服务响应超时，请稍后重试");
            }
            default -> {
                // 对于未特殊处理的状态码，使用默认解码器
                log.debug("Using default error decoder for status: {}", response.status());
                yield defaultErrorDecoder.decode(methodKey, response);
            }
        };
    }

    /**
     * 从响应体中提取错误信息
     * <p>
     * 尝试读取响应体内容作为错误信息。如果响应体为空或读取失败，则返回默认错误信息。
     * </p>
     *
     * @param response HTTP响应对象
     * @return 提取的错误信息，如果提取失败则返回默认信息
     */
    private String extractErrorMessage(Response response) {
        if (response.body() == null) {
            return "无错误详情";
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body().asInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            log.warn("Failed to read error response body", e);
            return "无法读取错误详情";
        }
    }
}
