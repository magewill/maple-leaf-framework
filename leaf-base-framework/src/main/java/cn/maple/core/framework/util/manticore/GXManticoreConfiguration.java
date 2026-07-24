package cn.maple.core.framework.util.manticore;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Manticore Search HTTP API utility facade.
 *
 * <p>Remote operations return {@link GXMantiCoreResDto}. Configure the facade with
 * {@link #initConfig(String, String, String)} during application startup; legacy Spring
 * properties remain available as a fallback. SQL is sent as an UTF-8 form field named
 * {@code query}; bulk requests use NDJSON.</p>
 */
public final class GXManticoreConfiguration {
    static final int DEFAULT_BULK_BATCH_SIZE = 1000;
    // query_string 语法中作为操作符使用、必须转义的特殊字符
    static final String QUERY_STRING_SPECIAL_CHARS = "!\"$'()-/<@\\^|~";
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 15000;
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:9308/";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    // 请求体中表示"表/索引名称"的字段名。旧版本 Manticore 用 "index"，
    // 当前官方文档（Manticore 6.x+）已统一改为 "table"（"index" 作为兼容别名仍可使用）。
    // 默认保持 "index" 以兼容本类历史用法，若使用较新版本建议启动时调用 useTableKeyword()。
    static volatile String tableFieldName = "index";
    // 网络抖动重试策略，默认不重试（maxRetries=0），避免掩盖真实问题；
    // 可通过 setRetryPolicy 按需开启。仅对连接/IO 异常重试，HTTP 状态码错误不重试。
    private static volatile int maxRetries = 0;
    private static volatile long retryBackoffMs = 200L;
    // Bearer Token 鉴权（可选）。Manticore 开启鉴权后，HTTP 接口可用 Basic Auth 或 Bearer Token 两种方式之一。
    // 一旦设置了 token，sendPost 会优先用 "Authorization: Bearer <token>"，不再发送 Basic Auth 头。
    private static volatile String bearerToken;
    private static volatile GXManticoreConfig config;

    private GXManticoreConfiguration() {
    }

    public static void initConfig(String baseUrl, String username, String password) {
        initConfig(baseUrl, username, password, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public static void initConfig(String baseUrl, String username, String password,
                                  int connectTimeoutMs, int readTimeoutMs) {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("connectTimeoutMs and readTimeoutMs must be positive");
        }
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        validateCredentialTransport(normalizedBaseUrl, StrUtil.isNotBlank(username) && StrUtil.isNotBlank(password));
        config = new GXManticoreConfig(normalizedBaseUrl, username, password,
                connectTimeoutMs, readTimeoutMs);
    }

    /**
     * 改用 Bearer Token 鉴权（对应 Manticore 的 Authentication and authorization 功能，
     * 需要 Manticore 端已开启鉴权且该 token 有效）。设置后所有请求都会带上
     * "Authorization: Bearer &lt;token&gt;"，不再发送 Basic Auth 头。
     *
     * @param token 有效的 bearer token
     */
    public static void useBearerToken(String token) {
        requireNonBlank(token, "token");
        validateCredentialTransport(currentConfig().baseUrl(), true);
        bearerToken = token;
    }

    /**
     * 清除 Bearer Token，恢复使用 Basic Auth（initConfig 中配置的用户名密码）鉴权
     */
    public static void clearBearerToken() {
        bearerToken = null;
    }

    /**
     * 为当前 Basic Auth 用户创建或轮换一个 Bearer Token (POST /token)。
     * 该操作本身仍使用 Basic Auth 鉴权，与是否已调用过 {@link #useBearerToken(String)} 无关。
     * <p><b>注意：</b>响应中的 token 明文只会返回这一次，请调用后立即解析并妥善保存
     * （如写入配置中心），之后只能通过 SQL 的 {@code SHOW TOKEN} 看到哈希值，看不到明文。
     *
     * @return 接口原始响应 JSON 字符串
     */
    public static GXMantiCoreResDto<JSON> createOrRotateToken() {
        return sendPost("/token", "{}", "application/json", true);
    }

    public static GXMantiCoreResDto<JSON> post(String endpoint, Map<String, ?> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        return sendPost(endpoint, JSONUtil.toJsonStr(payload), "application/json", false);
    }

    /**
     * 请求体中改用 "table" 作为表名字段（较新版本 Manticore 推荐写法）
     */
    public static void useTableKeyword() {
        tableFieldName = "table";
    }

    /**
     * 请求体中改用 "index" 作为表名字段（默认，兼容老版本）
     */
    public static void useIndexKeyword() {
        tableFieldName = "index";
    }

    // ==================== 单条写入 ====================

    /**
     * 配置网络异常时的重试策略
     *
     * @param retries   最大重试次数，0 表示不重试
     * @param backoffMs 每次重试之间的基础退避时间（毫秒），实际等待时间 = backoffMs * 当前重试次数
     */
    public static void setRetryPolicy(int retries, long backoffMs) {
        maxRetries = Math.max(0, retries);
        retryBackoffMs = Math.max(0, backoffMs);
    }


    // ==================== 内部工具方法 ====================

    static List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) {
            list.add(f);
        }
        return list;
    }

    static GXMantiCoreResDto<JSON> sendPost(String endpoint, String body, String contentType) {
        return sendPost(endpoint, body, contentType, false);
    }

    static GXMantiCoreResDto<JSON> sendPost(String endpoint, String body, String contentType,
                                            boolean forceBasicAuth) {
        validateEndpoint(endpoint);
        GXManticoreConfig currentConfig = currentConfig();
        int attempt = 0;
        int localMaxRetries = maxRetries;
        long localRetryBackoffMs = retryBackoffMs;
        while (true) {
            HttpResponse response = null;
            try {
                HttpRequest request = HttpRequest.post(currentConfig.baseUrl() + endpoint)
                        .header(Header.CONTENT_TYPE, contentType)
                        .charset(StandardCharsets.UTF_8)
                        .setConnectionTimeout(currentConfig.connectTimeoutMs())
                        .setReadTimeout(currentConfig.readTimeoutMs())
                        .body(body);

                if (!forceBasicAuth && StrUtil.isNotBlank(bearerToken)) {
                    request.header("Authorization", "Bearer " + bearerToken);
                } else if (StrUtil.isNotBlank(currentConfig.username()) && StrUtil.isNotBlank(currentConfig.password())) {
                    request.basicAuth(currentConfig.username(), currentConfig.password());
                }

                response = request.execute();

                String result = response.body();
                JSON data;
                try {
                    data = parseJson(response.header(Header.CONTENT_TYPE), result);
                } catch (RuntimeException e) {
                    return GXMantiCoreResDto.failure(response.getStatus(), null, result,
                            "Invalid JSON response: " + e.getMessage());
                }
                if (!response.isOk()) {
                    String errorMessage = data instanceof JSONObject json ? json.getStr("error") : null;
                    return GXMantiCoreResDto.failure(response.getStatus(), data, result,
                            StrUtil.isBlank(errorMessage) ? "HTTP " + response.getStatus() : errorMessage);
                }
                return GXMantiCoreResDto.success(response.getStatus(), data, result);
            } catch (HttpException e) {
                attempt++;
                if (attempt > localMaxRetries) {
                    return GXMantiCoreResDto.failure(null, null, null,
                            "Manticore transport error: " + e.getMessage());
                }
                sleepQuietly(localRetryBackoffMs * attempt);
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }
    }

    static JSON parseJson(String responseContentType, String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        if (!StrUtil.containsIgnoreCase(responseContentType, "json")
                && !body.startsWith("{") && !body.startsWith("[")) {
            return null;
        }
        return JSONUtil.parse(body);
    }

    static GXMantiCoreResDto<JSON> toSqlResponse(GXMantiCoreResDto<JSON> response) {
        if (!response.isSuccess() || response.getData() == null) {
            return response;
        }
        String error = null;
        if (response.getData() instanceof JSONArray array) {
            for (int i = 0; i < array.size(); i++) {
                JSONObject resultSet = array.getJSONObject(i);
                error = resultSet == null ? null : resultSet.getStr("error");
                if (StrUtil.isNotBlank(error)) {
                    break;
                }
            }
        } else if (response.getData() instanceof JSONObject object) {
            error = object.getStr("error");
        }
        return StrUtil.isBlank(error) ? response
                : GXMantiCoreResDto.failure(response.getStatusCode(), response.getData(), response.getRawBody(), error);
    }

    static GXManticoreConfig currentConfig() {
        GXManticoreConfig configured = config;
        if (configured != null) {
            return configured;
        }
        Environment environment = GXSpringContextUtils.getEnvironment();
        if (environment == null) {
            throw new IllegalStateException("Manticore configuration is not initialized");
        }
        return new GXManticoreConfig(
                normalizeBaseUrl(environment.getProperty("maple.framework.manticore.base-url", String.class, DEFAULT_BASE_URL)),
                environment.getProperty("maple.framework.manticore.username", String.class),
                environment.getProperty("maple.framework.manticore.password", String.class),
                environment.getProperty("maple.framework.manticore.connect-timeout", Integer.class, DEFAULT_CONNECT_TIMEOUT_MS),
                environment.getProperty("maple.framework.manticore.read-timeout", Integer.class, DEFAULT_READ_TIMEOUT_MS));
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (StrUtil.isBlank(baseUrl)) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        URI uri = URI.create(baseUrl.trim());
        if ((!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || StrUtil.isBlank(uri.getHost()))) {
            throw new IllegalArgumentException("baseUrl must be an absolute HTTP URL");
        }
        return StrUtil.removeSuffix(baseUrl.trim(), "/");
    }

    static void validateCredentialTransport(String baseUrl, boolean hasCredentials) {
        if (!hasCredentials) {
            return;
        }
        URI uri = URI.create(baseUrl);
        if ("http".equalsIgnoreCase(uri.getScheme()) && !isLoopbackHost(uri.getHost())) {
            throw new IllegalArgumentException("credentials require HTTPS unless the Manticore host is loopback");
        }
    }

    static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    static void validateEndpoint(String endpoint) {
        if (StrUtil.isBlank(endpoint) || !endpoint.startsWith("/") || endpoint.contains("..")) {
            throw new IllegalArgumentException("endpoint must be an absolute in-server path without traversal");
        }
    }

    static void requireNonBlank(String value, String name) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    static void requireIdentifier(String value, String name) {
        if (StrUtil.isBlank(value) || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a valid Manticore identifier");
        }
    }

    static void requireNonEmpty(Map<?, ?> value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }

    static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    static void requireSearchArguments(String index, int offset, int limit) {
        requireNonBlank(index, "index");
        if (offset < 0 || limit <= 0) {
            throw new IllegalArgumentException("offset must be non-negative and limit must be positive");
        }
    }

    static void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private record GXManticoreConfig(String baseUrl, String username, String password,
                                     int connectTimeoutMs, int readTimeoutMs) {
        private GXManticoreConfig {
            if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
                throw new IllegalArgumentException("connectTimeoutMs and readTimeoutMs must be positive");
            }
        }
    }
}
