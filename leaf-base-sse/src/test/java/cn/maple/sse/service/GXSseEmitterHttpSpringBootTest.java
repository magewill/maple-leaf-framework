package cn.maple.sse.service;

import cn.maple.sse.service.impl.GXSseEmitterServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check of the real SSE wire format over a running servlet container.
 */
@SpringBootTest(classes = GXSseEmitterHttpSpringBootTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GXSseEmitterHttpSpringBootTest {
    @LocalServerPort
    private int port;

    @Autowired
    private GXSseEmitterService sseEmitterService;

    @Test
    void splitFramesShareIdAndCarryChunkIndex() throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/sse/connect?clientId=split-client"))
                .timeout(Duration.ofSeconds(10L))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (StreamReader reader = new StreamReader(response.body())) {
            assertTrue(reader.awaitLine("id:201", 5_000L), reader::describe);

            sseEmitterService.sendMessageToOneClient("split-client", "x".repeat(250), true);

            assertTrue(reader.awaitCount("chunk=1/", 1, 5_000L), reader::describe);
            List<String> sharedIds = reader.idValues().stream().filter(id -> !"201".equals(id)).toList();
            assertTrue(sharedIds.size() >= 2, "a 250-char message splits into multiple frames sharing one id: "
                    + reader.idValues());
            assertTrue(sharedIds.stream().distinct().count() == 1,
                    "all chunks share the same message id: " + sharedIds);
            sseEmitterService.closeConnect("split-client");
        }
    }

    @Test
    void clientDisconnectCleansUpOnNextWrite() throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/sse/connect?clientId=drop-client"))
                .timeout(Duration.ofSeconds(10L))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        StreamReader reader = new StreamReader(response.body());
        assertTrue(reader.awaitLine("id:201", 5_000L), reader::describe);
        reader.close();

        sseEmitterService.sendMessageToOneClient("drop-client", "y".repeat(64 * 1024), false);
        awaitCondition(() -> ((GXSseEmitterServiceImpl) sseEmitterService).getActiveConnectionCount() == 0, 10_000L);

        assertEquals(0, ((GXSseEmitterServiceImpl) sseEmitterService).getActiveConnectionCount(),
                "a disconnected client must be cleaned up once a write fails");
    }

    @Test
    void httpStreamCarriesHandshakeMessageAndCloseFrames() throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/sse/connect?clientId=http-client"))
                .timeout(Duration.ofSeconds(10L))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        assertEquals("text/event-stream", response.headers().firstValue("content-type").orElse(""));

        try (StreamReader reader = new StreamReader(response.body())) {
            assertTrue(reader.awaitLine("id:201", 5_000L), reader::describe);
            assertTrue(reader.awaitLine("http-client", 5_000L), reader::describe);

            sseEmitterService.sendMessageToOneClient("http-client", "hello-sse", false);
            assertTrue(reader.awaitLine("event:Push Msg", 5_000L), reader::describe);
            assertTrue(reader.awaitLine("\"hello-sse\"", 5_000L), reader::describe);
            assertTrue(reader.awaitLine("retry:2000", 5_000L), reader::describe);

            sseEmitterService.closeConnect("http-client");
            assertTrue(reader.awaitLine("event:close", 5_000L), reader::describe);
            assertTrue(reader.awaitEof(5_000L), reader::describe);
            assertNotNull(response.body());
        }

        assertEquals(0, ((GXSseEmitterServiceImpl) sseEmitterService).getActiveConnectionCount());

        HttpResponse<InputStream> reconnected = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, reconnected.statusCode(), "server side close must not block a reconnect");
        try (StreamReader reader = new StreamReader(reconnected.body())) {
            assertTrue(reader.awaitLine("id:201", 5_000L), reader::describe);
            assertTrue(reader.awaitLine("http-client", 5_000L), reader::describe);
            sseEmitterService.closeConnect("http-client");
            assertTrue(reader.awaitEof(5_000L), reader::describe);
        }
    }

    /**
     * Collects the SSE stream on a virtual thread so assertions can wait for frames.
     */
    private static void awaitCondition(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
    }

    private static final class StreamReader implements AutoCloseable {
        private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
        private final InputStream inputStream;
        private final Thread reader;
        private volatile boolean eof;

        private StreamReader(InputStream inputStream) {
            this.inputStream = inputStream;
            this.reader = Thread.ofVirtual().start(() -> {
                try (BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (IOException ignored) {
                    // stream closed by the client or the server
                } finally {
                    eof = true;
                }
            });
        }

        private boolean awaitLine(String needle, long timeoutMillis) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
            while (System.nanoTime() < deadline) {
                if (snapshot().stream().anyMatch(line -> line.contains(needle))) {
                    return true;
                }
                if (eof) {
                    return snapshot().stream().anyMatch(line -> line.contains(needle));
                }
                Thread.sleep(20L);
            }
            return false;
        }

        private boolean awaitCount(String needle, int minimum, long timeoutMillis) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
            while (System.nanoTime() < deadline) {
                if (snapshot().stream().filter(line -> line.contains(needle)).count() >= minimum) {
                    return true;
                }
                if (eof) {
                    return false;
                }
                Thread.sleep(20L);
            }
            return false;
        }

        private List<String> idValues() {
            return snapshot().stream()
                    .filter(line -> line.startsWith("id:"))
                    .map(line -> line.substring("id:".length()))
                    .toList();
        }

        private boolean awaitEof(long timeoutMillis) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
            while (!eof && System.nanoTime() < deadline) {
                Thread.sleep(20L);
            }
            return eof;
        }

        private String describe() {
            return "received frames: " + snapshot();
        }

        private List<String> snapshot() {
            synchronized (lines) {
                return new ArrayList<>(lines);
            }
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
            reader.interrupt();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({GXSseEmitterServiceImpl.class, SseTestController.class})
    static class TestApplication {
    }

    @RestController
    static class SseTestController {
        @Autowired
        private GXSseEmitterService sseEmitterService;

        @GetMapping("/sse/connect")
        SseEmitter connect(@RequestParam(required = false) String clientId) {
            return sseEmitterService.createSseConnect(clientId, 600L);
        }
    }
}
