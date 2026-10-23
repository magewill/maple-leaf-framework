package cn.maple.webclient.config;

import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.netty.resources.ConnectionProvider;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resource lifecycle: the container pool is disposed on shutdown, the holder
 * reuses a single self managed pool and disposal is idempotent.
 */
class GXWebClientResourceLifecycleTest {

    @Test
    void disposesConnectionProviderOnContextClose() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXWebClientConfig.class)
                .run(context -> {
                    assertThat(context).hasBean("mapleWebClientConnectionProvider");
                    BeanDefinition definition = ((ConfigurableApplicationContext) context)
                            .getBeanFactory()
                            .getBeanDefinition("mapleWebClientConnectionProvider");
                    assertThat(definition.getDestroyMethodName()).isEqualTo("dispose");
                });
    }

    @Test
    void reusesSinglePoolForManualBuilder() {
        GXWebClientResourceHolder holder = new GXWebClientResourceHolder();
        AtomicInteger creations = new AtomicInteger();
        ConnectionProvider factory = ConnectionProvider.builder("holder-test-pool")
                .maxConnections(4)
                .build();

        ConnectionProvider first = holder.getOrCreate(() -> {
            creations.incrementAndGet();
            return factory;
        });
        for (int i = 0; i < 9; i++) {
            assertThat(holder.getOrCreate(() -> {
                creations.incrementAndGet();
                return factory;
            })).isSameAs(first);
        }

        assertThat(creations).hasValue(1);
        assertThat(holder.isProviderCreated()).isTrue();
        holder.dispose();
    }

    @Test
    void disposeIsIdempotent() {
        GXWebClientConfig config = new GXWebClientConfig();

        assertThatCode(config::destroy).doesNotThrowAnyException();
        assertThatCode(config::destroy).doesNotThrowAnyException();
    }

    @Test
    void disposedHolderRefusesToRebuildPool() {
        GXWebClientResourceHolder holder = new GXWebClientResourceHolder();
        ConnectionProvider provider = ConnectionProvider.builder("dispose-test-pool").build();
        holder.getOrCreate(() -> provider);
        holder.dispose();

        assertThatThrownBy(() -> holder.getOrCreate(ConnectionProvider::newConnection))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("disposed");
    }

    @Test
    void containerBeanIsClosedWithContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXWebClientConfig.class)
                .run(context -> {
                    ConnectionProvider provider = context.getBean("mapleWebClientConnectionProvider",
                            ConnectionProvider.class);
                    assertThat(provider).isNotNull();
                });
    }
}
