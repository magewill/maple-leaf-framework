package cn.maple.webclient.testsupport;

import cn.maple.webclient.config.GXWebClientConfig;
import cn.maple.webclient.service.GXWebClientService;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.resources.ConnectionProvider;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared test fixtures: local HTTP server, log capture and client builders.
 *
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
public final class GXWebClientTestSupport {
    private static final ConnectionProvider SHARED_PROVIDER = ConnectionProvider.builder("webclient-test-pool")
            .maxConnections(100)
            .pendingAcquireMaxCount(200)
            .build();

    private GXWebClientTestSupport() {
    }

    /**
     * Starts a loopback server responding with a fixed status and body.
     *
     * @param status HTTP status
     * @param body   response body
     * @return started server
     */
    public static HttpServer startServer(int status, String body) {
        return startServer(status, body, 0L, new AtomicInteger());
    }

    /**
     * Starts a loopback server that counts received requests.
     *
     * @param status  HTTP status
     * @param body    response body
     * @param counter request counter
     * @return started server
     */
    public static HttpServer startServer(int status, String body, AtomicInteger counter) {
        return startServer(status, body, 0L, counter);
    }

    /**
     * Starts a loopback server delaying the response.
     *
     * @param status      HTTP status
     * @param body        response body
     * @param delayMillis response delay
     * @param counter     request counter
     * @return started server
     */
    public static HttpServer startServer(int status, String body, long delayMillis, AtomicInteger counter) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            server.setExecutor(scheduler);
            server.createContext("/", exchange -> {
                counter.incrementAndGet();
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                Runnable respond = () -> {
                    try {
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(status, payload.length);
                        try (OutputStream output = exchange.getResponseBody()) {
                            output.write(payload);
                        }
                    } catch (IOException e) {
                        exchange.close();
                    }
                };
                if (delayMillis > 0) {
                    scheduler.schedule(respond, delayMillis, TimeUnit.MILLISECONDS);
                } else {
                    respond.run();
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Start test HTTP server failed", e);
        }
    }

    /**
     * Returns the base url of the given server.
     *
     * @param server started server
     * @return base url such as {@code http://127.0.0.1:12345}
     */
    public static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Stops the given server.
     *
     * @param server server, may be {@code null}
     */
    public static void stop(@Nullable HttpServer server) {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * Builds a plain (non load balanced) builder for tests.
     *
     * @param config      config instance
     * @param environment environment
     * @param service     SPI, may be {@code null}
     * @return builder
     */
    public static WebClient.Builder plainBuilder(GXWebClientConfig config, Environment environment,
                                                 @Nullable GXWebClientService service) {
        ReactorClientHttpConnector connector = config.mapleWebClientConnector(SHARED_PROVIDER, environment);
        return config.webClientBuilder(serviceProvider(service), mapperProvider(), connector, environment);
    }

    /**
     * Builds a load balanced builder for tests.
     *
     * @param config      config instance
     * @param environment environment
     * @param service     SPI, may be {@code null}
     * @return builder
     */
    public static WebClient.Builder lbBuilder(GXWebClientConfig config, Environment environment,
                                              @Nullable GXWebClientService service) {
        ReactorClientHttpConnector connector = config.mapleWebClientConnector(SHARED_PROVIDER, environment);
        return config.loadBalancedWebClientBuilder(serviceProvider(service), mapperProvider(), connector, environment);
    }

    /**
     * Builds a direct builder for tests.
     *
     * @param config      config instance
     * @param environment environment
     * @param service     SPI, may be {@code null}
     * @return builder
     */
    public static WebClient.Builder directBuilder(GXWebClientConfig config, Environment environment,
                                                  @Nullable GXWebClientService service) {
        ReactorClientHttpConnector connector = config.mapleWebClientConnector(SHARED_PROVIDER, environment);
        return config.webClientDirectBuilder(serviceProvider(service), mapperProvider(), connector, environment);
    }

    /**
     * Wraps an SPI instance into an object provider.
     *
     * @param service SPI, may be {@code null}
     * @return provider
     */
    public static ObjectProvider<GXWebClientService> serviceProvider(@Nullable GXWebClientService service) {
        return new ObjectProvider<>() {
            @Override
            public GXWebClientService getIfAvailable() {
                return service;
            }
        };
    }

    /**
     * Provides no JSON mapper, keeping default codecs.
     *
     * @return provider
     */
    public static ObjectProvider<JsonMapper> mapperProvider() {
        return new ObjectProvider<>() {
            @Override
            public JsonMapper getIfAvailable() {
                return null;
            }
        };
    }

    /**
     * Captures log events emitted by the given logger.
     *
     * @param loggerName logger name
     * @return capture handle
     */
    public static CapturedLogs capture(String loggerName) {

        org.apache.logging.log4j.Logger logger = LogManager.getLogger(loggerName);
        if (!(logger instanceof Logger coreLogger)) {
            throw new IllegalStateException("Log4j2 core logger required, actual=" + logger.getClass().getName());
        }
        return new CapturedLogs(coreLogger);
    }

    /**
     * Log4j2 based log capture.
     */
    public static final class CapturedLogs implements AutoCloseable {
        private final Logger logger;

        private final Level previousLevel;

        private final CapturingAppender appender;

        private CapturedLogs(Logger logger) {
            this.logger = logger;
            this.previousLevel = logger.getLevel();
            this.appender = new CapturingAppender();
            this.appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.TRACE);
        }

        /**
         * Returns formatted messages captured so far.
         *
         * @return messages
         */
        public List<String> messages() {
            List<String> messages = new ArrayList<>();
            for (LogEvent event : appender.events()) {
                messages.add(event.getMessage().getFormattedMessage());
            }
            return messages;
        }

        /**
         * Returns whether any captured message contains the given text.
         *
         * @param text text
         * @return {@code true} when present
         */
        public boolean contains(String text) {
            return messages().stream().anyMatch(message -> message.contains(text));
        }

        /**
         * Returns the count of captured messages containing the given text.
         *
         * @param text text
         * @return count
         */
        public long count(String text) {
            return messages().stream().filter(message -> message.contains(text)).count();
        }

        /**
         * Clears captured messages.
         */
        public void clear() {
            appender.clear();
        }

        @Override
        public void close() {
            logger.removeAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    /**
     * Environment counting property lookups, used to prove the request path does
     * not touch configuration.
     */
    public static final class CountingEnvironment extends MockEnvironment {
        private final AtomicInteger lookups = new AtomicInteger();

        @Override
        public String getProperty(String key) {
            lookups.incrementAndGet();
            return super.getProperty(key);
        }

        @Override
        public <T> T getProperty(String key, Class<T> targetType) {
            lookups.incrementAndGet();
            return super.getProperty(key, targetType);
        }

        @Override
        public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
            lookups.incrementAndGet();
            return super.getProperty(key, targetType, defaultValue);
        }

        /**
         * Returns the number of lookups seen so far.
         *
         * @return count
         */
        public int lookups() {
            return lookups.get();
        }

        /**
         * Resets the lookup counter.
         */
        public void resetLookups() {
            lookups.set(0);
        }
    }

    /**
     * Appender collecting immutable log events.
     */
    private static final class CapturingAppender extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        private CapturingAppender() {
            super("GXWebClientTestCapture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        private List<LogEvent> events() {
            return events;
        }

        private void clear() {
            events.clear();
        }
    }
}
