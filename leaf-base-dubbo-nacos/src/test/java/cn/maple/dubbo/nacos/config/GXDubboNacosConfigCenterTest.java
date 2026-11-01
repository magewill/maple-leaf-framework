package cn.maple.dubbo.nacos.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the mandatory provider-filter wiring against a real Nacos config center.
 *
 * <p>The filter string is published to the Nacos server described by {@code -Dgx.nacos.serverAddr}
 * / {@code NACOS_SERVER_ADDR} (default {@code 127.0.0.1:8848}, same default as
 * {@code config/application-4.0-dev.yml}), read back through the official client, and fed to
 * {@link GXDubboProviderFilterValidator} exactly as a runtime configuration would be. When the server
 * is unreachable the whole class is skipped, so the offline build stays green (FR-025).
 */
class GXDubboNacosConfigCenterTest {
    private static final String SERVER_ADDR = resolve("gx.nacos.serverAddr", "NACOS_SERVER_ADDR", "127.0.0.1:8848")
            .replaceFirst("^nacos://", "");
    private static final String NAMESPACE = resolve("gx.nacos.namespace", "NACOS_NAMESPACE", "public");
    private static final String GROUP = "DEFAULT_GROUP";
    private static final String REQUIRED_PROPERTY = "dubbo.provider.filter";
    private static final int FETCH_ATTEMPTS = 50;
    private static final long FETCH_INTERVAL_MILLIS = 200L;

    private static ConfigService configService;

    @BeforeAll
    static void connectRealNacos() throws NacosException {
        Assumptions.assumeTrue(isReadinessOk(), "local Nacos at " + SERVER_ADDR + " is not reachable, skipping");

        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, SERVER_ADDR);
        properties.setProperty(PropertyKeyConst.NAMESPACE, NAMESPACE);
        configService = NacosFactory.createConfigService(properties);
    }

    @AfterAll
    static void disconnect() throws NacosException {
        if (configService != null) {
            configService.shutDown();
        }
    }

    @Test
    void deliversMandatoryFilterConfigFromRealNacos() throws NacosException, IOException {
        assertWiring("gx-dubbo-e2e-required.properties", "dubboExceptionFilter,-exception", true, true);
    }

    @Test
    void reportsIncompleteWiringWhenExclusionIsMissing() throws NacosException, IOException {
        assertWiring("gx-dubbo-e2e-missing-exclusion.properties", "dubboExceptionFilter", false, true);
    }

    @Test
    void reportsIncompleteWiringWhenCustomFilterIsMissing() throws NacosException, IOException {
        assertWiring("gx-dubbo-e2e-missing-custom.properties", "-exception", true, false);
    }

    /**
     * Publishes the filter value to the real Nacos, reads it back and lets the startup validator judge
     * the wiring. The validator must never block startup, whatever the outcome.
     */
    private static void assertWiring(String dataId, String filterValue, boolean expectExcluded, boolean expectCustomEnabled)
            throws NacosException, IOException {
        try {
            StandardEnvironment environment = loadEnvironment(publishAndFetch(dataId, REQUIRED_PROPERTY + "=" + filterValue));
            String filter = environment.getProperty(REQUIRED_PROPERTY);

            assertEquals(filterValue, filter, "Nacos must deliver the published value unchanged");
            assertEquals(expectExcluded, GXDubboProviderFilterValidator.isBuiltInExceptionFilterExcluded(filter));
            assertEquals(expectCustomEnabled, GXDubboProviderFilterValidator.isCustomExceptionFilterEnabled(filter));
            assertDoesNotThrow(() -> new GXDubboProviderFilterValidator(environment).afterSingletonsInstantiated(),
                    "the validator must only log, never block startup");
        } finally {
            removeQuietly(dataId);
        }
    }

    private static String publishAndFetch(String dataId, String content) throws NacosException {
        assertTrue(configService.publishConfig(dataId, GROUP, content), "publishConfig must succeed against the real Nacos");
        String fetched = null;
        for (int attempt = 0; attempt < FETCH_ATTEMPTS; attempt++) {
            fetched = configService.getConfig(dataId, GROUP, 1000);
            if (fetched != null) {
                break;
            }
            sleepQuietly();
        }
        assertNotNull(fetched, "config published to Nacos must be readable back");
        return fetched;
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(FETCH_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static StandardEnvironment loadEnvironment(String propertiesContent) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(propertiesContent));

        Map<String, Object> values = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("nacos-gx-dubbo-e2e", values));
        return environment;
    }

    private static void removeQuietly(String dataId) {
        try {
            configService.removeConfig(dataId, GROUP);
        } catch (NacosException ignored) {
            // cleanup only: a leftover test dataId must not fail the build
        }
    }

    private static String resolve(String propertyName, String envName, String defaultValue) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv().getOrDefault(envName, defaultValue);
        }
        return configured;
    }

    private static boolean isReadinessOk() {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + SERVER_ADDR + "/nacos/v1/console/health/readiness"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
