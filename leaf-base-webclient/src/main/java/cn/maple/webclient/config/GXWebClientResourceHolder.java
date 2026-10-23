package cn.maple.webclient.config;

import cn.maple.core.framework.exception.GXBusinessException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.netty.resources.ConnectionProvider;

import java.util.function.Supplier;

/**
 * Owns at most one self-managed {@link ConnectionProvider} for the non Spring
 * builder path and releases it deterministically.
 * <p>{@code dispose()} is idempotent. Once disposed, requesting a provider throws
 * instead of silently rebuilding a pool (a rebuild after context close would
 * leak again).</p>
 *
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
public class GXWebClientResourceHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXWebClientResourceHolder.class);

    private final Object monitor = new Object();

    private @Nullable ConnectionProvider provider;

    private boolean disposed;

    /**
     * Returns the single managed provider, creating it on first use.
     *
     * @param factory provider factory, invoked at most once
     * @return managed provider
     */
    public ConnectionProvider getOrCreate(Supplier<ConnectionProvider> factory) {
        synchronized (monitor) {
            if (disposed) {
                throw new GXBusinessException("webClientBuilder is disposed and cannot be reused");
            }
            if (provider == null) {
                provider = factory.get();
            }
            return provider;
        }
    }

    /**
     * Releases the managed provider. Safe to call multiple times.
     */
    public void dispose() {
        ConnectionProvider toDispose;
        synchronized (monitor) {
            if (disposed) {
                return;
            }
            disposed = true;
            toDispose = provider;
            provider = null;
        }
        if (toDispose != null) {
            try {
                toDispose.dispose();
            } catch (Exception e) {
                LOGGER.warn("Dispose self-managed connection provider failed: exceptionType={}",
                        e.getClass().getName());
            }
        }
    }

    /**
     * Returns whether a provider has been created by this holder.
     *
     * @return {@code true} when a provider exists and has not been disposed
     */
    public boolean isProviderCreated() {
        synchronized (monitor) {
            return provider != null;
        }
    }

    /**
     * Returns whether this holder has been disposed.
     *
     * @return {@code true} after {@link #dispose()}
     */
    public boolean isDisposed() {
        synchronized (monitor) {
            return disposed;
        }
    }
}
