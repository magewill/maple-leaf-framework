package cn.maple.feign.properties;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@ConfigurationProperties(prefix = "maple.framework.feign.circuit-breaker")
public class GXCircuitBreakerProperties {
    private int maxRetries = 3;

    private Duration backoff = Duration.ofMillis(100);

    private double backoffMultiplier = 2.0;

    private Duration openTimeout = Duration.ofSeconds(20);

    private Duration resetTimeout = Duration.ofSeconds(5);

    public void setMaxRetries(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be greater than or equal to 0");
        }
        this.maxRetries = maxRetries;
    }

    public void setBackoff(Duration backoff) {
        if (backoff == null) {
            throw new IllegalArgumentException("backoff must not be null");
        }
        this.backoff = backoff;
    }

    public void setBackoffMultiplier(double backoffMultiplier) {
        if (backoffMultiplier <= 0) {
            throw new IllegalArgumentException("backoffMultiplier must be greater than 0");
        }
        this.backoffMultiplier = backoffMultiplier;
    }

    public void setOpenTimeout(Duration openTimeout) {
        if (openTimeout == null) {
            throw new IllegalArgumentException("openTimeout must not be null");
        }
        this.openTimeout = openTimeout;
    }

    public void setResetTimeout(Duration resetTimeout) {
        if (resetTimeout == null) {
            throw new IllegalArgumentException("resetTimeout must not be null");
        }
        this.resetTimeout = resetTimeout;
    }
}
