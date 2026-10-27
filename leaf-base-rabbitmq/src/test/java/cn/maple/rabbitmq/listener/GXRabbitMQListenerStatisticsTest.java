package cn.maple.rabbitmq.listener;

import cn.maple.rabbitmq.listener.impl.GXRabbitMQListenerStatistics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXRabbitMQListenerStatisticsTest {
    @Test
    void shouldRegisterCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GXRabbitMQListenerStatistics statistics = new GXRabbitMQListenerStatistics(providerOf(registry));

        statistics.processed();
        statistics.processed();
        statistics.rejected("empty");
        statistics.rejected("unparseable");
        statistics.failed(new IllegalArgumentException("bad payload"));

        assertThat(registry.get("rabbitmq.listener.messages.processed").counter().count()).isEqualTo(2.0d);
        assertThat(registry.get("rabbitmq.listener.messages.rejected")
                .tag("reason", "empty").counter().count()).isEqualTo(1.0d);
        assertThat(registry.get("rabbitmq.listener.messages.rejected")
                .tag("reason", "unparseable").counter().count()).isEqualTo(1.0d);
        assertThat(registry.get("rabbitmq.listener.messages.failed")
                .tag("type", "IllegalArgumentException").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void shouldMapUnknownFailureTypeToOther() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GXRabbitMQListenerStatistics statistics = new GXRabbitMQListenerStatistics(providerOf(registry));

        statistics.failed(new UnsupportedOperationException("boom"));

        assertThat(registry.get("rabbitmq.listener.messages.failed")
                .tag("type", "other").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void shouldDegradeWhenRegistryMissing() {
        GXRabbitMQListenerStatistics statistics = new GXRabbitMQListenerStatistics(providerOf(null));

        assertThatCode(() -> {
            statistics.processed();
            statistics.rejected("empty");
            statistics.failed(new IllegalStateException("boom"));
        }).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> providerOf(MeterRegistry registry) {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return provider;
    }
}
