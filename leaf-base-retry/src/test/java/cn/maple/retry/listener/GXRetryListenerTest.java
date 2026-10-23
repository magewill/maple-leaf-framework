package cn.maple.retry.listener;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.Retryable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXRetryListenerTest {

    private static final String LOG_LEVEL_PROPERTY = "maple.framework.retry.log.level";

    private static final String VERBOSE_PROPERTY = "maple.framework.retry.log.verbose";

    @AfterEach
    void clearProperties() {
        System.clearProperty(LOG_LEVEL_PROPERTY);
        System.clearProperty(VERBOSE_PROPERTY);
    }

    @Test
    void getRetryableTypeDescription_PrefersRetryableName() throws Exception {
        GXRetryListener listener = new GXRetryListener();
        Method method = GXRetryListener.class.getDeclaredMethod("getRetryableTypeDescription", Retryable.class);
        method.setAccessible(true);

        assertEquals("firstName", method.invoke(listener, new NamedRetryable("firstName")));
        assertEquals("secondName", method.invoke(listener, new NamedRetryable("secondName")));
    }

    @Test
    void getRetryableTypeDescription_FallsBackToClassSimpleNameForBlankName() throws Exception {
        GXRetryListener listener = new GXRetryListener();
        Method method = GXRetryListener.class.getDeclaredMethod("getRetryableTypeDescription", Retryable.class);
        method.setAccessible(true);

        assertEquals("NamedRetryable", method.invoke(listener, new NamedRetryable("  ")));
    }

    @Test
    void configuredLevel_UsesThresholdSemantics() throws Exception {
        GXRetryListener listener = new GXRetryListener();
        Method method = GXRetryListener.class.getDeclaredMethod("configuredLevel");
        method.setAccessible(true);

        System.setProperty(LOG_LEVEL_PROPERTY, "OFF");
        assertEquals(0, method.invoke(listener));

        System.setProperty(LOG_LEVEL_PROPERTY, "WARN");
        assertEquals(2, method.invoke(listener));

        System.setProperty(LOG_LEVEL_PROPERTY, "DEBUG");
        assertEquals(4, method.invoke(listener));

        System.setProperty(LOG_LEVEL_PROPERTY, "TRACE");
        assertEquals(4, method.invoke(listener));

        System.setProperty(LOG_LEVEL_PROPERTY, "not-a-level");
        assertEquals(3, method.invoke(listener));
    }

    @Test
    void beforeRetry_DoesNotThrowForFirstAndLaterRetries() {
        GXRetryListener listener = new GXRetryListener();

        assertDoesNotThrow(() -> listener.beforeRetry(null, new NamedRetryable("retryable"), retryState(0)));
        assertDoesNotThrow(() -> listener.beforeRetry(null, new NamedRetryable("retryable"), retryState(1)));
        assertDoesNotThrow(() -> listener.beforeRetry(null, new NamedRetryable("retryable"), retryState(2)));
    }

    @Test
    void beforeRetry_LogsExactlyOnceAtFirstRetryOnly() {
        CollectingAppender appender = new CollectingAppender();
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(GXRetryListener.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            GXRetryListener listener = new GXRetryListener();

            listener.beforeRetry(null, new NamedRetryable("retryable"), retryState(0));
            assertEquals(0, appender.messages().size(), "initial attempt must not log 'retry started'");

            listener.beforeRetry(null, new NamedRetryable("retryable"), retryState(1));
            assertEquals(1, appender.messages().size(), "first retry must log exactly one line");
            assertTrue(appender.messages().get(0).contains("retry started"), appender.messages().get(0));

            listener.beforeRetry(null, new NamedRetryable("retryable"), retryState(2));
            assertEquals(1, appender.messages().size(), "later retries must not log again");
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void onRetryableExecution_AllowsNullLastExceptionAndEmptyExceptions() {
        GXRetryListener listener = new GXRetryListener();

        assertDoesNotThrow(() -> listener.onRetryableExecution(null, new NamedRetryable("retryable"), new RetryState() {
            @Override
            public int getRetryCount() {
                return 1;
            }

            @Override
            public List<Throwable> getExceptions() {
                return List.of();
            }

            @Override
            public boolean isSuccessful() {
                return false;
            }
        }));
    }

    @Test
    void onRetryPolicyExhaustion_LogsRetryException() {
        GXRetryListener listener = new GXRetryListener();
        RetryException exception = new RetryException("retry failed", new IllegalStateException("failed"));

        assertDoesNotThrow(() -> listener.onRetryPolicyExhaustion(null, new NamedRetryable("retryable"), exception));
    }

    @Test
    void getConfigInfo_ReportsCurrentSettings() {
        System.setProperty(LOG_LEVEL_PROPERTY, "WARN");
        System.setProperty(VERBOSE_PROPERTY, "true");

        String info = GXRetryListener.getConfigInfo().get();

        assertTrue(info.contains("logLevel=WARN"), info);
        assertTrue(info.contains("verboseLogging=true"), info);
    }

    @Test
    void verboseExecutionLogIncludesExceptionCount() {
        System.setProperty(VERBOSE_PROPERTY, "true");

        List<String> messages = captureLogs(Level.WARN, () ->
                new GXRetryListener().onRetryableExecution(null, new NamedRetryable("retryable"), failingRetryState()));

        assertEquals(1, messages.size(), messages.toString());
        assertTrue(messages.get(0).contains("exceptionCount: 2"), messages.get(0));
    }

    @Test
    void retrySuccessLogsSingleInfoLine() {
        List<String> messages = captureLogs(Level.INFO, () ->
                new GXRetryListener().onRetrySuccess(null, new NamedRetryable("retryable"), "ok"));

        assertEquals(1, messages.size(), messages.toString());
        assertTrue(messages.get(0).contains("completed successfully"), messages.get(0));
    }

    @Test
    void terminationLogsUseDistinctReasonText() {
        List<String> interrupted = captureLogs(Level.WARN, () ->
                new GXRetryListener().onRetryPolicyInterruption(null, new NamedRetryable("retryable"), retryException()));
        assertEquals(1, interrupted.size(), interrupted.toString());
        assertTrue(interrupted.get(0).contains("retry policy interrupted"), interrupted.get(0));

        List<String> timedOut = captureLogs(Level.WARN, () ->
                new GXRetryListener().onRetryPolicyTimeout(null, new NamedRetryable("retryable"), retryException()));
        assertEquals(1, timedOut.size(), timedOut.toString());
        assertTrue(timedOut.get(0).contains("retry policy timed out"), timedOut.get(0));
    }

    @Test
    void debugLevelAddsFailureStackEvent() {
        System.setProperty(LOG_LEVEL_PROPERTY, "DEBUG");
        System.setProperty(VERBOSE_PROPERTY, "true");

        List<String> messages = captureLogs(Level.DEBUG, () ->
                new GXRetryListener().onRetryableExecution(null, new NamedRetryable("retryable"), failingRetryState()));

        assertEquals(2, messages.size(), messages.toString());
        assertTrue(messages.get(0).contains("retryable execution failed"), messages.get(0));
        assertTrue(messages.get(1).contains("retry failure stack"), messages.get(1));
    }

    private List<String> captureLogs(Level level, Runnable action) {
        CollectingAppender appender = new CollectingAppender();
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(GXRetryListener.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(level);
        logger.addAppender(appender);
        try {
            action.run();
            return List.copyOf(appender.messages());
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    private RetryState failingRetryState() {
        return new RetryState() {
            @Override
            public int getRetryCount() {
                return 1;
            }

            @Override
            public List<Throwable> getExceptions() {
                return List.of(new IllegalStateException("first"), new IllegalStateException("second"));
            }
        };
    }

    private RetryException retryException() {
        return new RetryException("retry failed", new IllegalStateException("boom"));
    }

    private RetryState retryState(int retryCount) {
        return new RetryState() {
            @Override
            public int getRetryCount() {
                return retryCount;
            }

            @Override
            public List<Throwable> getExceptions() {
                return List.of(new IllegalStateException("boom"));
            }
        };
    }

    private static final class CollectingAppender extends AbstractAppender {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        private CollectingAppender() {
            super("GXRetryListenerTestCollector", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }

        List<String> messages() {
            return messages;
        }
    }

    private record NamedRetryable(String name) implements Retryable<Object> {

        @Override
        public Object execute() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
