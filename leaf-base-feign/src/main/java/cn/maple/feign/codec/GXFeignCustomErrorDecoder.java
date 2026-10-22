package cn.maple.feign.codec;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Feign custom error decoder.
 *
 * <p>Maps the handled HTTP statuses to {@link GXBusinessException} while keeping the
 * remote HTTP status (or the remote business code) as the exception code. Response
 * bodies are read with a hard limit and are never echoed for authentication related
 * statuses.</p>
 */
@Slf4j
public class GXFeignCustomErrorDecoder implements ErrorDecoder {
    private static final int MAX_ERROR_BODY_CHARS = 8192;

    private static final String NO_BODY_MESSAGE = "";

    private static final String READ_FAILURE_MESSAGE = "Failed to read error response body";

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        log.debug("Decoding error response for method: {}, status: {}", toAsciiLogValue(methodKey), status);

        if (!isCustomHandledStatus(status)) {
            log.debug("Using default error decoder for status: {}", status);
            return defaultErrorDecoder.decode(methodKey, response);
        }

        String requestUrl = response.request() == null ? "" : response.request().url();
        String logContext = StrUtil.format(
                "method: {}, URL: {}, status: {}",
                toAsciiLogValue(methodKey), toAsciiLogValue(requestUrl), status
        );

        ResolvedError resolvedError = resolveError(status, requestUrl, response);
        logAtStatusLevel(status, logContext);
        return new GXBusinessException(resolvedError.message(), resolvedError.code(), resolvedError.data());
    }

    private ResolvedError resolveError(int status, String requestUrl, Response response) {
        if (status == HttpStatus.HTTP_UNAUTHORIZED) {
            return new ResolvedError(status, "Remote service unauthorized, please check authentication information");
        }
        if (status == HttpStatus.HTTP_FORBIDDEN) {
            return new ResolvedError(status, "Remote service access forbidden");
        }
        if (status == HttpStatus.HTTP_NOT_FOUND) {
            return new ResolvedError(status, "Remote resource not found: " + requestUrl);
        }
        if (isBodyMessageAllowedStatus(status)) {
            return resolveWithBody(status, response);
        }
        return new ResolvedError(status, fixedMessage(status));
    }

    private ResolvedError resolveWithBody(int status, Response response) {
        String body = extractBody(response);
        RemoteResult remoteResult = parseRemoteResult(body);
        if (remoteResult != null) {
            int code = remoteResult.code() > 0 ? remoteResult.code() : status;
            String message = StrUtil.isBlank(remoteResult.msg()) ? fixedMessage(status) : remoteResult.msg();
            return new ResolvedError(code, message, remoteResult.data());
        }
        String message = StrUtil.isBlank(body) ? fixedMessage(status) : fixedMessage(status) + ": " + body;
        return new ResolvedError(status, message);
    }

    private boolean isBodyMessageAllowedStatus(int status) {
        return status == HttpStatus.HTTP_BAD_REQUEST
                || status == HttpStatus.HTTP_CONFLICT
                || status == HttpStatus.HTTP_UNPROCESSABLE_ENTITY
                || status == HttpStatus.HTTP_TOO_MANY_REQUESTS;
    }

    private String fixedMessage(int status) {
        return switch (status) {
            case HttpStatus.HTTP_BAD_REQUEST -> "Remote service bad request";
            case HttpStatus.HTTP_CONFLICT -> "Remote service conflict";
            case HttpStatus.HTTP_UNPROCESSABLE_ENTITY -> "Remote service unprocessable entity";
            case HttpStatus.HTTP_TOO_MANY_REQUESTS -> "Remote service too many requests";
            case HttpStatus.HTTP_INTERNAL_ERROR -> "Remote service internal error, please try again later";
            case HttpStatus.HTTP_UNAVAILABLE -> "Remote service unavailable, please try again later";
            case HttpStatus.HTTP_GATEWAY_TIMEOUT -> "Remote service response timeout, please try again later";
            default -> "Remote service request failed, status: " + status;
        };
    }

    private void logAtStatusLevel(int status, String logContext) {
        if (status >= HttpStatus.HTTP_INTERNAL_ERROR) {
            log.error("Remote service error: {}", logContext);
            return;
        }
        log.warn("Remote service rejected request: {}", logContext);
    }

    @Nullable
    private RemoteResult parseRemoteResult(String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        try {
            JSONObject jsonObject = JSONUtil.parseObj(body);
            if (!jsonObject.containsKey("code") || !jsonObject.containsKey("msg")) {
                return null;
            }
            Dict data = Dict.create();
            Object dataValue = jsonObject.get("data");
            if (dataValue instanceof JSONObject dataObject) {
                dataObject.forEach(data::put);
            }
            return new RemoteResult(jsonObject.getInt("code", 0), jsonObject.getStr("msg"), data);
        } catch (Exception e) {
            log.debug("Failed to parse remote error body as business result, fallback to raw text");
            return null;
        }
    }

    private boolean isCustomHandledStatus(int status) {
        return status == HttpStatus.HTTP_NOT_FOUND
                || status == HttpStatus.HTTP_UNAUTHORIZED
                || status == HttpStatus.HTTP_FORBIDDEN
                || status == HttpStatus.HTTP_BAD_REQUEST
                || status == HttpStatus.HTTP_CONFLICT
                || status == HttpStatus.HTTP_UNPROCESSABLE_ENTITY
                || status == HttpStatus.HTTP_TOO_MANY_REQUESTS
                || status == HttpStatus.HTTP_INTERNAL_ERROR
                || status == HttpStatus.HTTP_UNAVAILABLE
                || status == HttpStatus.HTTP_GATEWAY_TIMEOUT;
    }

    private String extractBody(Response response) {
        if (response.body() == null) {
            return NO_BODY_MESSAGE;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body().asInputStream(), StandardCharsets.UTF_8))) {
            String message = readLimitedBody(reader);
            return StrUtil.blankToDefault(message, NO_BODY_MESSAGE);
        } catch (IOException e) {
            log.warn("Failed to read error response body", e);
            return READ_FAILURE_MESSAGE;
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

    private String toAsciiLogValue(@Nullable String value) {
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

    private record RemoteResult(int code, @Nullable String msg, Dict data) {
    }

    private record ResolvedError(int code, String message, Dict data) {
        private ResolvedError(int code, String message) {
            this(code, message, Dict.create());
        }
    }
}
