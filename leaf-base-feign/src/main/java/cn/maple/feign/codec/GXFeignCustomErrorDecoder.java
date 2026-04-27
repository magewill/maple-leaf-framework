package cn.maple.feign.codec;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBusinessException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Feign custom error decoder.
 */
@Slf4j
public class GXFeignCustomErrorDecoder implements ErrorDecoder {
    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        log.debug("Decoding error response for method: {}, status: {}", methodKey, status);

        if (!isCustomHandledStatus(status)) {
            log.debug("Using default error decoder for status: {}", status);
            return defaultErrorDecoder.decode(methodKey, response);
        }

        String errorMessage = extractErrorMessage(response);
        String requestUrl = response.request() == null ? "" : response.request().url();
        String fullErrorMessage = StrUtil.format(
                "Remote service call failed - method: {}, URL: {}, status: {}, message: {}",
                methodKey, requestUrl, status, errorMessage
        );

        return switch (status) {
            case HttpStatus.HTTP_NOT_FOUND -> {
                log.warn("Resource not found: {}", fullErrorMessage);
                yield new GXBusinessException("Remote resource not found: " + requestUrl);
            }
            case HttpStatus.HTTP_UNAUTHORIZED -> {
                log.warn("Unauthorized access: {}", fullErrorMessage);
                yield new GXBusinessException("Remote service unauthorized, please check authentication information");
            }
            case HttpStatus.HTTP_FORBIDDEN -> {
                log.warn("Access forbidden: {}", fullErrorMessage);
                yield new GXBusinessException("Remote service access forbidden");
            }
            case HttpStatus.HTTP_BAD_REQUEST -> {
                log.warn("Bad request: {}", fullErrorMessage);
                yield new GXBusinessException("Remote service bad request: " + errorMessage);
            }
            case HttpStatus.HTTP_INTERNAL_ERROR -> {
                log.error("Remote service error: {}", fullErrorMessage);
                yield new GXBusinessException("Remote service internal error, please try again later");
            }
            case HttpStatus.HTTP_UNAVAILABLE -> {
                log.error("Service unavailable: {}", fullErrorMessage);
                yield new GXBusinessException("Remote service unavailable, please try again later");
            }
            case HttpStatus.HTTP_GATEWAY_TIMEOUT -> {
                log.error("Gateway timeout: {}", fullErrorMessage);
                yield new GXBusinessException("Remote service response timeout, please try again later");
            }
            default -> defaultErrorDecoder.decode(methodKey, response);
        };
    }

    private boolean isCustomHandledStatus(int status) {
        return status == HttpStatus.HTTP_NOT_FOUND
                || status == HttpStatus.HTTP_UNAUTHORIZED
                || status == HttpStatus.HTTP_FORBIDDEN
                || status == HttpStatus.HTTP_BAD_REQUEST
                || status == HttpStatus.HTTP_INTERNAL_ERROR
                || status == HttpStatus.HTTP_UNAVAILABLE
                || status == HttpStatus.HTTP_GATEWAY_TIMEOUT;
    }

    private String extractErrorMessage(Response response) {
        if (response.body() == null) {
            return "No error response body";
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body().asInputStream(), StandardCharsets.UTF_8))) {
            String message = reader.lines().collect(Collectors.joining("\n"));
            return StrUtil.blankToDefault(message, "No error response body");
        } catch (IOException e) {
            log.warn("Failed to read error response body", e);
            return "Failed to read error response body";
        }
    }
}
