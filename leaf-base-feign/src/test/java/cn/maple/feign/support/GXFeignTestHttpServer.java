package cn.maple.feign.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process HTTP server used to verify real Feign round trips without external
 * dependencies.
 */
public final class GXFeignTestHttpServer implements AutoCloseable {
    private static final int DEFAULT_BACKLOG = 0;

    private final HttpServer server;

    private final Map<String, StubResponse> stubs = new ConcurrentHashMap<>();

    private final List<RecordedRequest> recordedRequests = Collections.synchronizedList(new ArrayList<>());

    private GXFeignTestHttpServer(HttpServer server) {
        this.server = server;
    }

    public static GXFeignTestHttpServer start() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), DEFAULT_BACKLOG);
            GXFeignTestHttpServer testServer = new GXFeignTestHttpServer(httpServer);
            httpServer.createContext("/", testServer::handle);
            httpServer.setExecutor(null);
            httpServer.start();
            return testServer;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to start in-process HTTP server", e);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stub(String path, int status, String body) {
        stub(path, status, body, "application/json");
    }

    public void stub(String path, int status, String body, String contentType) {
        stubs.put(path, new StubResponse(status, body, contentType));
    }

    public int hits(String path) {
        return (int) recordedRequests.stream().filter(request -> request.path().equals(path)).count();
    }

    public RecordedRequest lastRequest(String path) {
        List<RecordedRequest> matched = requests(path);
        if (matched.isEmpty()) {
            throw new IllegalStateException("No request recorded for path: " + path);
        }
        return matched.getLast();
    }

    public List<RecordedRequest> requests(String path) {
        synchronized (recordedRequests) {
            return recordedRequests.stream().filter(request -> request.path().equals(path)).toList();
        }
    }

    public List<RecordedRequest> allRequests() {
        synchronized (recordedRequests) {
            return List.copyOf(recordedRequests);
        }
    }

    public void reset() {
        recordedRequests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, List<String>> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
            recordedRequests.add(new RecordedRequest(exchange.getRequestMethod(), path, headers, body));

            StubResponse stub = stubs.get(path);
            if (stub == null) {
                writeResponse(exchange, 404, "no stub for " + path, "text/plain");
                return;
            }
            writeResponse(exchange, stub.status(), stub.body(), stub.contentType());
        }
    }

    private void writeResponse(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        if (payload.length == 0) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
    }

    public record StubResponse(int status, String body, String contentType) {
    }

    public record RecordedRequest(String method, String path, Map<String, List<String>> headers, String body) {
        public List<String> headerValues(String headerName) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(headerName))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElseGet(List::of);
        }
    }
}
