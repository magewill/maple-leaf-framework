package cn.maple.core.framework.util.manticore;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXManticoreException;
import com.google.common.net.InetAddresses;
import lombok.Getter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Manticore Search HTTP client.
 *
 * <p>Construct one client for each Manticore endpoint configuration.</p>
 */
public final class GXManticoreClient {
    private static final int DEFAULT_BULK_BATCH_SIZE = 1000;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 15000;
    private final GXManticoreConfig config;
    @Getter
    private final GXManticoreDocumentOperations documentOperations;
    @Getter
    private final GXManticoreSqlOperations sqlOperations;
    @Getter
    private final GXManticorePercolateOperations percolateOperations;
    @Getter
    private final GXManticoreQueryOperations queryOperations;
    @Getter
    private final GXManticoreAutocompleteOperations autocompleteOperations;
    private final TableField tableField;
    private volatile RetryPolicy retryPolicy = new RetryPolicy(0, 200L);
    private volatile String bearerToken;

    public GXManticoreClient(String baseUrl, String username, String password) {
        this(baseUrl, username, password, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, TableField.INDEX);
    }

    public GXManticoreClient(String baseUrl, String username, String password,
                             int connectTimeoutMs, int readTimeoutMs) {
        this(baseUrl, username, password, connectTimeoutMs, readTimeoutMs, TableField.INDEX);
    }

    public GXManticoreClient(String baseUrl, String username, String password, TableField tableField) {
        this(baseUrl, username, password, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, tableField);
    }

    public GXManticoreClient(String baseUrl, String username, String password,
                             int connectTimeoutMs, int readTimeoutMs, TableField tableField) {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("connectTimeoutMs and readTimeoutMs must be positive");
        }
        if (tableField == null) {
            throw new IllegalArgumentException("tableField must not be null");
        }
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        validateCredentialTransport(normalizedBaseUrl, StrUtil.isNotBlank(username) && StrUtil.isNotBlank(password));
        config = new GXManticoreConfig(normalizedBaseUrl, username, password,
                connectTimeoutMs, readTimeoutMs);
        this.tableField = tableField;
        documentOperations = new GXManticoreDocumentOperations(this);
        sqlOperations = new GXManticoreSqlOperations(this);
        percolateOperations = new GXManticorePercolateOperations(this);
        queryOperations = new GXManticoreQueryOperations(this);
        autocompleteOperations = new GXManticoreAutocompleteOperations(this);
    }

    public void useBearerToken(String token) {
        GXManticoreUtils.requireNonBlank(token, "token");
        validateCredentialTransport(currentConfig().baseUrl(), true);
        bearerToken = token;
    }

    public void clearBearerToken() {
        bearerToken = null;
    }

    public String createOrRotateToken() {
        return sendPost("/token", "{}", "application/json", true);
    }

    public String post(String endpoint, Map<String, ?> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        return postRaw(endpoint, JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * Sends a POST request without changing the caller-provided body or content type.
     */
    public String postRaw(String endpoint, String body, String contentType) {
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        GXManticoreUtils.requireNonBlank(contentType, "contentType");
        return sendPost(endpoint, body, contentType, false);
    }

    public void setRetryPolicy(int retries, long backoffMs) {
        retryPolicy = new RetryPolicy(Math.max(0, retries), Math.max(0, backoffMs));
    }

    int getDefaultBulkBatchSize() {
        return DEFAULT_BULK_BATCH_SIZE;
    }

    String getTableFieldName() {
        return tableField.requestFieldName;
    }

    String sendPost(String endpoint, String body, String contentType) {
        return sendPost(endpoint, body, contentType, false);
    }

    String sendPost(String endpoint, String body, String contentType,
                    boolean forceBasicAuth) {
        validateEndpoint(endpoint);
        GXManticoreConfig currentConfig = currentConfig();
        int attempt = 0;
        RetryPolicy localRetryPolicy = retryPolicy;
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
                return response.body();
            } catch (HttpException e) {
                attempt++;
                if (attempt > localRetryPolicy.maxRetries()) {
                    throw new GXManticoreException(StrUtil.format("Manticore transport error: {}", e.getMessage()), "");
                }
                sleepQuietly(localRetryPolicy.backoffMs() * attempt);
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }
    }

    GXManticoreConfig currentConfig() {
        return config;
    }

    String normalizeBaseUrl(String baseUrl) {
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

    void validateCredentialTransport(String baseUrl, boolean hasCredentials) {
        if (!hasCredentials) {
            return;
        }
        URI uri = URI.create(baseUrl);
        String host = uri.getHost();

        if ("http".equalsIgnoreCase(uri.getScheme()) && !isIpAddress(host) && !isLoopbackHost(host)) {
            throw new IllegalArgumentException("credentials require HTTPS unless the host is an IP address or loopback");
        }
    }

    boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
    }

    private boolean isIpAddress(String host) {
        if (host == null) {
            return false;
        }
        String normalizedHost = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        return InetAddresses.isInetAddress(normalizedHost);
    }

    void validateEndpoint(String endpoint) {
        if (StrUtil.isBlank(endpoint) || !endpoint.startsWith("/") || endpoint.contains("..")) {
            throw new IllegalArgumentException("endpoint must be an absolute in-server path without traversal");
        }
    }

    void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public enum TableField {
        INDEX("index"),
        TABLE("table");

        private final String requestFieldName;

        TableField(String requestFieldName) {
            this.requestFieldName = requestFieldName;
        }
    }

    /**
     * 判断给定的 host 是否为 IP 地址（IPv4 或 IPv6）
     */
    private record GXManticoreConfig(String baseUrl, String username, String password,
                                     int connectTimeoutMs, int readTimeoutMs) {
        private GXManticoreConfig {
            if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
                throw new IllegalArgumentException("connectTimeoutMs and readTimeoutMs must be positive");
            }
        }
    }

    private record RetryPolicy(int maxRetries, long backoffMs) {
    }
}
