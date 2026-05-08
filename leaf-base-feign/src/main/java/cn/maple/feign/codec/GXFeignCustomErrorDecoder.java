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

/**
 * Feign custom error decoder.
 */
@Slf4j
public class GXFeignCustomErrorDecoder implements ErrorDecoder {
    private static final int MAX_ERROR_BODY_CHARS = 8192;

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        log.debug("Decoding error response for method: {}, status: {}", toAsciiLogValue(methodKey), status);

        if (!isCustomHandledStatus(status)) {
            log.debug("Using default error decoder for status: {}", status);
            return defaultErrorDecoder.decode(methodKey, response);
        }

        String errorMessage = extractErrorMessage(response);
        String requestUrl = response.request() == null ? "" : response.request().url();
        String logContext = StrUtil.format(
                "method: {}, URL: {}, status: {}",
                toAsciiLogValue(methodKey), toAsciiLogValue(requestUrl), status
        );

        return switch (status) {
            case HttpStatus.HTTP_NOT_FOUND -> {
                log.warn("Resource not found: {}", logContext);
                yield new GXBusinessException("Remote resource not found: " + requestUrl);
            }
            case HttpStatus.HTTP_UNAUTHORIZED -> {
                log.warn("Unauthorized access: {}", logContext);
                yield new GXBusinessException("Remote service unauthorized, please check authentication information");
            }
            case HttpStatus.HTTP_FORBIDDEN -> {
                log.warn("Access forbidden: {}", logContext);
                yield new GXBusinessException("Remote service access forbidden");
            }
            case HttpStatus.HTTP_BAD_REQUEST -> {
                log.warn("Bad request: {}", logContext);
                yield new GXBusinessException("Remote service bad request: " + errorMessage);
            }
            case HttpStatus.HTTP_INTERNAL_ERROR -> {
                log.error("Remote service error: {}", logContext);
                yield new GXBusinessException("Remote service internal error, please try again later");
            }
            case HttpStatus.HTTP_UNAVAILABLE -> {
                log.error("Service unavailable: {}", logContext);
                yield new GXBusinessException("Remote service unavailable, please try again later");
            }
            case HttpStatus.HTTP_GATEWAY_TIMEOUT -> {
                log.error("Gateway timeout: {}", logContext);
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
            String message = readLimitedBody(reader);
            return StrUtil.blankToDefault(message, "No error response body");
        } catch (IOException e) {
            log.warn("Failed to read error response body", e);
            return "Failed to read error response body";
        }
    }

    private String readLimitedBody(BufferedReader reader) throws IOException {
        char[] buffer = new char[1024];
        StringBuilder body = new StringBuilder();
        int remaining = MAX_ERROR_BODY_CHARS;
        int read;
        while (remaining > 0 && (read = reader.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
            body.append(buffer, 0, read);
            remaining -= read;
        }
        if (remaining == 0 && reader.read() != -1) {
            body.append("...[truncated]");
        }
        return body.toString();
    }

    private String toAsciiLogValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder asciiValue = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            asciiValue.append(current <= 0x7F ? current : '?');
        }
        return asciiValue.toString();
    }
}
