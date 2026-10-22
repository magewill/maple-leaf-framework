package cn.maple.eureka.client.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal in-process Eureka endpoint used to verify that registration actually leaves the client.
 *
 * <p>Backed by the JDK {@code com.sun.net.httpserver} module so no test dependency is added. Instance
 * registration is acknowledged with {@code 204}; registry lookups return an empty application list.</p>
 */
public class GXTestRegistrationServer implements AutoCloseable {
    private static final String EMPTY_REGISTRY =
            "{\"applications\":{\"versions__delta\":\"1\",\"apps__hashcode\":\"\",\"application\":[]}}";

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    private static final long POLL_MILLIS = 100L;

    private final HttpServer server;

    private final List<String> requests = new CopyOnWriteArrayList<>();

    private GXTestRegistrationServer(HttpServer server) {
        this.server = server;
    }

    /**
     * Starts the endpoint on an ephemeral loopback port.
     *
     * @return started server, never {@code null}
     */
    public static GXTestRegistrationServer start() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            GXTestRegistrationServer registrationServer = new GXTestRegistrationServer(httpServer);
            httpServer.createContext("/", registrationServer::handle);
            httpServer.start();
            return registrationServer;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start the test Eureka endpoint", exception);
        }
    }

    /**
     * @return base URL without trailing slash, for example {@code http://127.0.0.1:54321}
     */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * @return recorded requests in {@code METHOD path} form
     */
    public List<String> recordedRequests() {
        return List.copyOf(requests);
    }

    /**
     * Waits until the client posts a registration for the given application.
     *
     * @param appName application name as it appears in the request path
     * @return {@code true} when the registration was observed before the timeout
     */
    public boolean awaitRegistration(String appName) {
        String expected = "POST /eureka/apps/" + appName;
        long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (requests.contains(expected)) {
                return true;
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return requests.contains(expected);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(exchange.getRequestMethod() + " " + path);

        if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/eureka/apps")) {
            respond(exchange, 200, EMPTY_REGISTRY);
            return;
        }

        respond(exchange, 204, "");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
