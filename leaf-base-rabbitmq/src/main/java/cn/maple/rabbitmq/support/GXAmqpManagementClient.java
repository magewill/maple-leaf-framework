package cn.maple.rabbitmq.support;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.AbstractExchange;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RabbitMQ 拓扑管理客户端（Management HTTP API）。
 *
 * <p>AMQP 1.0 协议本身不提供 {@code queue.declare}/{@code exchange.declare} 等拓扑操作，
 * RabbitMQ 4.x 要求客户端先自行创建拓扑再消费。本类通过 Management HTTP API
 * （默认 15672 端口）完成队列、交换机、绑定的声明与查询，使
 * {@code GXSendRabbitMQService} 的拓扑契约在 AMQP 1.0 下继续可用。</p>
 *
 * <p>仅使用 JDK {@link HttpClient}，不引入额外依赖。</p>
 */
@Slf4j
public final class GXAmqpManagementClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final int BODY_SNIPPET_LENGTH = 200;

    private final HttpClient httpClient;

    private final String baseUrl;

    private final String encodedVhost;

    private final String authorization;

    private final JsonMapper jsonMapper;

    public GXAmqpManagementClient(String baseUrl, String vhost, @Nullable String username, @Nullable String password,
                                  JsonMapper jsonMapper) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.encodedVhost = GXAmqpAddress.encode(vhost);
        this.authorization = buildAuthorization(username, password);
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 声明队列。排他队列无法通过管理 API 创建，调用方需自行拒绝该入参。
     */
    public void declareQueue(String queueName, boolean durable, boolean autoDelete,
                             @Nullable Map<String, Object> arguments) {
        Map<String, Object> body = new LinkedHashMap<>(4);
        body.put("durable", durable);
        body.put("auto_delete", autoDelete);
        body.put("arguments", arguments == null ? Map.of() : arguments);
        execute("PUT", "/api/queues/" + encodedVhost + "/" + encode(queueName), body, 201, 204);
    }

    /**
     * 删除队列。
     *
     * @return {@code false} 表示队列不存在
     */
    public boolean deleteQueue(String queueName) {
        return executeAllowingMissing("DELETE", "/api/queues/" + encodedVhost + "/" + encode(queueName));
    }

    public boolean queueExists(String queueName) {
        HttpResponse<String> response = send("GET", "/api/queues/" + encodedVhost + "/" + encode(queueName), null);
        if (response.statusCode() == 404) {
            return false;
        }
        requireSuccess("GET", response);
        return true;
    }

    public void purgeQueue(String queueName) {
        execute("DELETE", "/api/queues/" + encodedVhost + "/" + encode(queueName) + "/contents", null, 204, 200);
    }

    public void declareExchange(AbstractExchange exchange) {
        Map<String, Object> body = new LinkedHashMap<>(5);
        body.put("type", exchange.getType());
        body.put("durable", exchange.isDurable());
        body.put("auto_delete", exchange.isAutoDelete());
        body.put("internal", exchange.isInternal());
        body.put("arguments", exchange.getArguments() == null ? Map.of() : exchange.getArguments());
        execute("PUT", "/api/exchanges/" + encodedVhost + "/" + encode(exchange.getName()), body, 201, 204);
    }

    /**
     * 删除交换机。
     *
     * @return {@code false} 表示交换机不存在
     */
    public boolean deleteExchange(String exchangeName) {
        return executeAllowingMissing("DELETE", "/api/exchanges/" + encodedVhost + "/" + encode(exchangeName));
    }

    public void bindQueue(String queueName, String exchangeName, String routingKey) {
        Map<String, Object> body = new LinkedHashMap<>(2);
        body.put("routing_key", routingKey);
        body.put("arguments", Map.of());
        execute("POST", bindingsPath(queueName, exchangeName), body, 201, 204);
    }

    /**
     * 解除绑定。管理 API 的删除接口要求 {@code properties_key}，因此先查询再删除。
     *
     * @return {@code false} 表示绑定不存在
     */
    public boolean unbindQueue(String queueName, String exchangeName, String routingKey) {
        String propertiesKey = findPropertiesKey(queueName, exchangeName, routingKey);
        if (propertiesKey == null) {
            return false;
        }
        return executeAllowingMissing("DELETE", bindingsPath(queueName, exchangeName) + "/" + encode(propertiesKey));
    }

    private @Nullable String findPropertiesKey(String queueName, String exchangeName, String routingKey) {
        HttpResponse<String> response = send("GET", bindingsPath(queueName, exchangeName), null);
        if (response.statusCode() == 404) {
            return null;
        }
        requireSuccess("GET", response);
        JsonNode bindings;
        try {
            bindings = jsonMapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw new GXAmqpManagementException(
                    "RabbitMQ management API response parse failed: path=" + bindingsPath(queueName, exchangeName)
                            + ", error=" + e.getClass().getSimpleName(), response.statusCode(), e);
        }
        for (JsonNode binding : bindings) {
            JsonNode routingKeyNode = binding.get("routing_key");
            if (routingKeyNode != null && routingKey.equals(routingKeyNode.asString())) {
                JsonNode propertiesKeyNode = binding.get("properties_key");
                return propertiesKeyNode == null ? null : propertiesKeyNode.asString();
            }
        }
        return null;
    }

    private String bindingsPath(String queueName, String exchangeName) {
        return "/api/bindings/" + encodedVhost + "/e/" + encode(exchangeName) + "/q/" + encode(queueName);
    }

    private boolean executeAllowingMissing(String method, String path) {
        HttpResponse<String> response = send(method, path, null);
        if (response.statusCode() == 404) {
            return false;
        }
        requireSuccess(method, response);
        return true;
    }

    private void execute(String method, String path, @Nullable Object body, int... expectedStatuses) {
        HttpResponse<String> response = send(method, path, body);
        for (int expected : expectedStatuses) {
            if (response.statusCode() == expected) {
                return;
            }
        }
        throw failure(method, path, response);
    }

    private HttpResponse<String> send(String method, String path, @Nullable Object body) {
        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", authorization);
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)));
            }
            request = builder.build();
        } catch (RuntimeException e) {
            // 非法 URL / 序列化失败也必须落在统一的异常类型上，避免裸 IllegalArgumentException 冒泡。
            throw new GXAmqpManagementException(
                    "RabbitMQ management API request build failed: method=" + method + ", path=" + path
                            + ", error=" + e.getClass().getSimpleName(), 0, e);
        }
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new GXAmqpManagementException(
                    "RabbitMQ management API request failed: method=" + method + ", path=" + path
                            + ", error=" + e.getClass().getSimpleName(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GXAmqpManagementException(
                    "RabbitMQ management API request interrupted: method=" + method + ", path=" + path, 0, e);
        }
    }

    private void requireSuccess(String method, HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw failure(method, response.request().uri().getPath(), response);
        }
    }

    private GXAmqpManagementException failure(String method, String path, HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body();
        // 先解析完整响应，再截断用于日志：截断后的 JSON 无法解析，会丢掉 RabbitMQ 给出的 reason。
        String reason = extractReason(body);
        String snippet = body.length() > BODY_SNIPPET_LENGTH ? body.substring(0, BODY_SNIPPET_LENGTH) : body;
        // 失败由服务层统一以 error 记录（带 operation/resource），这里只保留 debug 便于单测排查。
        log.debug("RabbitMQ management API failed: method={}, path={}, status={}, body={}",
                method, path, response.statusCode(), snippet);
        String message = "RabbitMQ management API failed: method=" + method + ", path=" + path
                + ", status=" + response.statusCode();
        if (reason != null) {
            message = message + ", reason=" + reason;
        }
        return new GXAmqpManagementException(message, response.statusCode());
    }

    private @Nullable String extractReason(String body) {
        if (!body.startsWith("{")) {
            return null;
        }
        try {
            JsonNode reasonNode = jsonMapper.readTree(body).get("reason");
            if (reasonNode == null) {
                return null;
            }
            String reason = reasonNode.asString();
            return reason.length() > BODY_SNIPPET_LENGTH ? reason.substring(0, BODY_SNIPPET_LENGTH) : reason;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String encode(String segment) {
        // URLEncoder 面向表单（空格编码为 +），路径片段必须用 RFC 3986 规则。
        return GXAmqpAddress.encode(segment);
    }

    private static String buildAuthorization(@Nullable String username, @Nullable String password) {
        String credentials = (username == null ? "" : username) + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
