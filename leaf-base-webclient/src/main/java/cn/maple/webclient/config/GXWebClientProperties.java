package cn.maple.webclient.config;

import cn.maple.core.framework.exception.GXBusinessException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Type-safe binding for every {@code maple.framework.web.client.*} key.
 * <p>Existing key names and default values are frozen; the eight keys added by
 * this release only carry optional behaviour and are safe to omit. One additional
 * unified switch, {@code maple.framework.enable.webclient-lb-guard}, lives outside
 * this prefix (constitution IV naming) and takes precedence over the legacy
 * {@code lb-guard-enabled} alias, so the metadata file declares 19 entries in
 * total.</p>
 * <p>Validation follows the data model "0 semantics" table: numeric zero is
 * rejected for pool/timeout sized keys, while it means "disabled" for the
 * retry, read-timeout and response-timeout keys.</p>
 *
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = GXWebClientProperties.PREFIX)
public class GXWebClientProperties {
    /**
     * Configuration prefix.
     */
    public static final String PREFIX = "maple.framework.web.client";

    /**
     * Unified feature switch prefix required by the constitution.
     */
    public static final String LB_GUARD_SWITCH_KEY = "maple.framework.enable.webclient-lb-guard";

    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public static final int DEFAULT_MEMORY_BUFFER_SIZE_MB = 16;

    public static final int DEFAULT_MAX_CONNECTIONS = 500;

    public static final int DEFAULT_ACQUIRE_TIMEOUT_MILLIS = 3000;

    public static final int DEFAULT_PENDING_ACQUIRE_MAX_COUNT = 1000;

    public static final int DEFAULT_IDLE_TIMEOUT_MILLIS = 30000;

    public static final int DEFAULT_MAX_LIFE_TIME_MILLIS = 300000;

    public static final int DEFAULT_EVICT_IN_BACKGROUND_SECONDS = 120;

    public static final int DEFAULT_RETRY_MAX_ATTEMPTS = 3;

    public static final int DEFAULT_RETRY_DELAY_MILLIS = 1000;

    public static final String DEFAULT_RETRY_METHODS = "GET,HEAD,OPTIONS";

    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    private int memoryBufferSizeMb = DEFAULT_MEMORY_BUFFER_SIZE_MB;

    private int maxConnections = DEFAULT_MAX_CONNECTIONS;

    private int acquireTimeoutMillis = DEFAULT_ACQUIRE_TIMEOUT_MILLIS;

    private int pendingAcquireMaxCount = DEFAULT_PENDING_ACQUIRE_MAX_COUNT;

    private int idleTimeoutMillis = DEFAULT_IDLE_TIMEOUT_MILLIS;

    private int maxLifeTimeMillis = DEFAULT_MAX_LIFE_TIME_MILLIS;

    private int evictInBackgroundSeconds = DEFAULT_EVICT_IN_BACKGROUND_SECONDS;

    private int retryMaxAttempts = DEFAULT_RETRY_MAX_ATTEMPTS;

    private int retryDelayMillis = DEFAULT_RETRY_DELAY_MILLIS;

    private String retryMethods = DEFAULT_RETRY_METHODS;

    private String userAgentSuffix = "";

    private @Nullable Integer connectTimeoutMillis;

    private @Nullable Integer readTimeoutMillis;

    private @Nullable Integer writeTimeoutMillis;

    private @Nullable Integer responseTimeoutMillis;

    private boolean lbGuardEnabled = true;

    /**
     * Value of {@code maple.framework.enable.webclient-lb-guard}; {@code null}
     * means "not configured" and the legacy alias applies.
     */
    private @Nullable Boolean frameworkLbGuardEnabled;

    private Set<String> lbGuardAllowHosts = new LinkedHashSet<>();

    private Set<String> invalidLbGuardAllowHosts = new LinkedHashSet<>();

    /**
     * Binds properties from the given environment without requiring a Spring
     * container, so bean factory methods can stay source-compatible.
     *
     * @param environment environment, may be {@code null}
     * @return validated properties instance
     */
    public static GXWebClientProperties from(@Nullable Environment environment) {
        GXWebClientProperties properties = new GXWebClientProperties();
        if (environment == null) {
            properties.validate();
            return properties;
        }

        properties.timeoutSeconds = intValue(environment, "timeout-seconds", DEFAULT_TIMEOUT_SECONDS, 1);
        properties.memoryBufferSizeMb = intValue(environment, "memory-buffer-size-mb", DEFAULT_MEMORY_BUFFER_SIZE_MB, 1);
        properties.maxConnections = intValue(environment, "max-connections", DEFAULT_MAX_CONNECTIONS, 1);
        properties.acquireTimeoutMillis = intValue(environment, "acquire-timeout-millis", DEFAULT_ACQUIRE_TIMEOUT_MILLIS, 1);
        properties.pendingAcquireMaxCount = intValue(environment, "pending-acquire-max-count", DEFAULT_PENDING_ACQUIRE_MAX_COUNT, 1);
        properties.evictInBackgroundSeconds = intValue(environment, "evict-in-background-seconds", DEFAULT_EVICT_IN_BACKGROUND_SECONDS, 1);
        properties.idleTimeoutMillis = intValue(environment, "idle-timeout-millis", DEFAULT_IDLE_TIMEOUT_MILLIS, 0);
        properties.maxLifeTimeMillis = intValue(environment, "max-life-time-millis", DEFAULT_MAX_LIFE_TIME_MILLIS, 0);
        properties.retryMaxAttempts = intValue(environment, "retry-max-attempts", DEFAULT_RETRY_MAX_ATTEMPTS, 0);
        properties.retryDelayMillis = intValue(environment, "retry-delay-millis", DEFAULT_RETRY_DELAY_MILLIS, 0);
        properties.retryMethods = environment.getProperty(PREFIX + ".retry-methods", DEFAULT_RETRY_METHODS);
        properties.userAgentSuffix = environment.getProperty(PREFIX + ".user-agent-suffix", "");
        properties.connectTimeoutMillis = nullableIntValue(environment, "connect-timeout-millis", 1);
        properties.readTimeoutMillis = nullableIntValue(environment, "read-timeout-millis", 0);
        properties.writeTimeoutMillis = nullableIntValue(environment, "write-timeout-millis", 1);
        properties.responseTimeoutMillis = nullableIntValue(environment, "response-timeout-millis", 0);
        properties.lbGuardEnabled = environment.getProperty(PREFIX + ".lb-guard-enabled", Boolean.class, Boolean.TRUE);
        properties.frameworkLbGuardEnabled = environment.getProperty(LB_GUARD_SWITCH_KEY, Boolean.class);
        Set<String> invalidHosts = new LinkedHashSet<>();
        properties.lbGuardAllowHosts = parseHosts(
                environment.getProperty(PREFIX + ".lb-guard-allow-hosts", ""), invalidHosts);
        properties.invalidLbGuardAllowHosts = invalidHosts;
        properties.validate();
        return properties;
    }

    /**
     * Parses the configured retry method list.
     *
     * @return retry methods, empty when retry is explicitly disabled
     */
    public Set<HttpMethod> toRetryMethods() {
        Set<HttpMethod> methods = new LinkedHashSet<>();
        if (retryMethods == null || retryMethods.isBlank()) {
            return methods;
        }
        for (String token : retryMethods.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                methods.add(HttpMethod.valueOf(trimmed.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new GXBusinessException("Invalid " + PREFIX + ".retry-methods value: " + trimmed, e);
            }
        }
        return methods;
    }

    /**
     * Effective connect timeout in milliseconds.
     *
     * @return milliseconds, or zero when unset and the base timeout is zero
     */
    public int effectiveConnectTimeoutMillis() {
        return connectTimeoutMillis != null ? connectTimeoutMillis : timeoutSeconds * 1000;
    }

    /**
     * Effective read timeout in milliseconds; zero disables the read timeout.
     *
     * @return milliseconds
     */
    public int effectiveReadTimeoutMillis() {
        return readTimeoutMillis != null ? readTimeoutMillis : timeoutSeconds * 1000;
    }

    /**
     * Effective write timeout in milliseconds; zero disables the write timeout.
     *
     * @return milliseconds
     */
    public int effectiveWriteTimeoutMillis() {
        return writeTimeoutMillis != null ? writeTimeoutMillis : timeoutSeconds * 1000;
    }

    /**
     * Effective response timeout in milliseconds; zero disables the response timeout.
     *
     * @return milliseconds
     */
    public int effectiveResponseTimeoutMillis() {
        return responseTimeoutMillis != null ? responseTimeoutMillis : timeoutSeconds * 1000;
    }

    /**
     * User agent advertised to remote peers.
     *
     * @return user agent value without server OS details
     */
    public String userAgent() {
        String base = "Maple-Leaf-WebClient/1.0";
        if (userAgentSuffix == null || userAgentSuffix.isBlank()) {
            return base;
        }
        return base + "+" + userAgentSuffix.trim();
    }

    private void validate() {
        requireAtLeast("timeout-seconds", timeoutSeconds, 1);
        requireAtLeast("memory-buffer-size-mb", memoryBufferSizeMb, 1);
        requireAtLeast("max-connections", maxConnections, 1);
        requireAtLeast("acquire-timeout-millis", acquireTimeoutMillis, 1);
        requireAtLeast("pending-acquire-max-count", pendingAcquireMaxCount, 1);
        requireAtLeast("evict-in-background-seconds", evictInBackgroundSeconds, 1);
        requireAtLeast("idle-timeout-millis", idleTimeoutMillis, 0);
        requireAtLeast("max-life-time-millis", maxLifeTimeMillis, 0);
        requireAtLeast("retry-max-attempts", retryMaxAttempts, 0);
        requireAtLeast("retry-delay-millis", retryDelayMillis, 0);
        if (connectTimeoutMillis != null) {
            requireAtLeast("connect-timeout-millis", connectTimeoutMillis, 1);
        }
        if (writeTimeoutMillis != null) {
            requireAtLeast("write-timeout-millis", writeTimeoutMillis, 1);
        }
        if (readTimeoutMillis != null) {
            requireAtLeast("read-timeout-millis", readTimeoutMillis, 0);
        }
        if (responseTimeoutMillis != null) {
            requireAtLeast("response-timeout-millis", responseTimeoutMillis, 0);
        }
    }

    private void requireAtLeast(String key, int value, int minimum) {
        if (value < minimum) {
            throw new GXBusinessException("Invalid " + PREFIX + "." + key + " value: " + value
                    + ", expected >= " + minimum);
        }
    }

    private static int intValue(Environment environment, String key, int defaultValue, int minimum) {
        String raw = environment.getProperty(PREFIX + "." + key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new GXBusinessException("Invalid " + PREFIX + "." + key + " value: " + raw, e);
        }
        if (value < minimum) {
            throw new GXBusinessException("Invalid " + PREFIX + "." + key + " value: " + value
                    + ", expected >= " + minimum);
        }
        return value;
    }

    private static @Nullable Integer nullableIntValue(Environment environment, String key, int minimum) {
        String raw = environment.getProperty(PREFIX + "." + key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new GXBusinessException("Invalid " + PREFIX + "." + key + " value: " + raw, e);
        }
        if (value < minimum) {
            throw new GXBusinessException("Invalid " + PREFIX + "." + key + " value: " + value
                    + ", expected >= " + minimum);
        }
        return value;
    }

    private static Set<String> parseHosts(String raw, Set<String> invalidHosts) {
        Set<String> hosts = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return hosts;
        }
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String normalized = normalizeHostEntry(trimmed);
            if (normalized == null) {
                invalidHosts.add(trimmed);
            } else {
                hosts.add(normalized);
            }
        }
        return hosts;
    }

    private static @Nullable String normalizeHostEntry(String entry) {
        String candidate = entry.toLowerCase(Locale.ROOT);
        if (candidate.contains("://") || candidate.contains("/") || candidate.contains("?")) {
            return null;
        }
        int portIndex = candidate.lastIndexOf(':');
        if (portIndex > 0 && !candidate.startsWith("[")) {
            String port = candidate.substring(portIndex + 1);
            if (!port.isEmpty() && port.chars().allMatch(Character::isDigit)) {
                candidate = candidate.substring(0, portIndex);
            } else {
                return null;
            }
        }
        return candidate.isBlank() ? null : candidate;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMemoryBufferSizeMb() {
        return memoryBufferSizeMb;
    }

    public void setMemoryBufferSizeMb(int memoryBufferSizeMb) {
        this.memoryBufferSizeMb = memoryBufferSizeMb;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getAcquireTimeoutMillis() {
        return acquireTimeoutMillis;
    }

    public void setAcquireTimeoutMillis(int acquireTimeoutMillis) {
        this.acquireTimeoutMillis = acquireTimeoutMillis;
    }

    public int getPendingAcquireMaxCount() {
        return pendingAcquireMaxCount;
    }

    public void setPendingAcquireMaxCount(int pendingAcquireMaxCount) {
        this.pendingAcquireMaxCount = pendingAcquireMaxCount;
    }

    public int getIdleTimeoutMillis() {
        return idleTimeoutMillis;
    }

    public void setIdleTimeoutMillis(int idleTimeoutMillis) {
        this.idleTimeoutMillis = idleTimeoutMillis;
    }

    public int getMaxLifeTimeMillis() {
        return maxLifeTimeMillis;
    }

    public void setMaxLifeTimeMillis(int maxLifeTimeMillis) {
        this.maxLifeTimeMillis = maxLifeTimeMillis;
    }

    public int getEvictInBackgroundSeconds() {
        return evictInBackgroundSeconds;
    }

    public void setEvictInBackgroundSeconds(int evictInBackgroundSeconds) {
        this.evictInBackgroundSeconds = evictInBackgroundSeconds;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public int getRetryDelayMillis() {
        return retryDelayMillis;
    }

    public void setRetryDelayMillis(int retryDelayMillis) {
        this.retryDelayMillis = retryDelayMillis;
    }

    public String getRetryMethods() {
        return retryMethods;
    }

    public void setRetryMethods(String retryMethods) {
        this.retryMethods = retryMethods;
    }

    public String getUserAgentSuffix() {
        return userAgentSuffix;
    }

    public void setUserAgentSuffix(String userAgentSuffix) {
        this.userAgentSuffix = userAgentSuffix;
    }

    public @Nullable Integer getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(@Nullable Integer connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public @Nullable Integer getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(@Nullable Integer readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public @Nullable Integer getWriteTimeoutMillis() {
        return writeTimeoutMillis;
    }

    public void setWriteTimeoutMillis(@Nullable Integer writeTimeoutMillis) {
        this.writeTimeoutMillis = writeTimeoutMillis;
    }

    public @Nullable Integer getResponseTimeoutMillis() {
        return responseTimeoutMillis;
    }

    public void setResponseTimeoutMillis(@Nullable Integer responseTimeoutMillis) {
        this.responseTimeoutMillis = responseTimeoutMillis;
    }

    /**
     * Effective load balancer guard switch.
     * <p>{@code maple.framework.enable.webclient-lb-guard} wins when configured;
     * otherwise the legacy {@code maple.framework.web.client.lb-guard-enabled}
     * alias applies. Default is {@code true} because the guard closes the F-01
     * misleading synthetic 503 defect (registered as a constitution IV exemption).</p>
     *
     * @return {@code true} when the guard is active
     */
    public boolean isLbGuardEnabled() {
        return frameworkLbGuardEnabled != null ? frameworkLbGuardEnabled : lbGuardEnabled;
    }

    public void setLbGuardEnabled(boolean lbGuardEnabled) {
        this.lbGuardEnabled = lbGuardEnabled;
    }

    public @Nullable Boolean getFrameworkLbGuardEnabled() {
        return frameworkLbGuardEnabled;
    }

    public void setFrameworkLbGuardEnabled(@Nullable Boolean frameworkLbGuardEnabled) {
        this.frameworkLbGuardEnabled = frameworkLbGuardEnabled;
    }

    public Set<String> getLbGuardAllowHosts() {
        return lbGuardAllowHosts;
    }

    public void setLbGuardAllowHosts(Set<String> lbGuardAllowHosts) {
        this.lbGuardAllowHosts = lbGuardAllowHosts;
    }

    public Set<String> getInvalidLbGuardAllowHosts() {
        return invalidLbGuardAllowHosts;
    }

    public void setInvalidLbGuardAllowHosts(Set<String> invalidLbGuardAllowHosts) {
        this.invalidLbGuardAllowHosts = invalidLbGuardAllowHosts;
    }
}
