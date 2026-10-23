package cn.maple.sse.support;

import org.slf4j.MDC;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Emitter doubles used to reach the failure paths of {@code GXSseEmitterServiceImpl}
 * without reflection. {@link SseEmitter#send(SseEventBuilder)} and
 * {@link SseEmitter#complete()} are public and not final in Spring Framework 7,
 * so they can be overridden safely.
 */
public final class GXSseTestEmitters {
    private GXSseTestEmitters() {
    }

    /**
     * @return emitter that records every delivered payload and can be switched to failure mode
     */
    public static ScriptedEmitter recording() {
        return new ScriptedEmitter();
    }

    public static final class ScriptedEmitter extends SseEmitter {
        private final List<Object> payloads = Collections.synchronizedList(new ArrayList<>());
        private final List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        private final List<String> traceIds = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger sendCount = new AtomicInteger();
        private final AtomicInteger failFrom = new AtomicInteger(Integer.MAX_VALUE);
        private volatile int failExactlyAt = -1;
        private volatile int runtimeFailureAt = -1;
        private volatile RuntimeException runtimeError;
        private volatile boolean dead;
        private volatile boolean completed;
        private volatile int blockAt = -1;
        private volatile long blockAtMillis;
        private volatile CountDownLatch entered = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);

        private ScriptedEmitter() {
            super(600_000L);
        }

        /**
         * Makes the first send wait until {@link #releaseFirstSend()} is called.
         *
         * @return this emitter
         */
        public ScriptedEmitter holdFirstSend() {
            this.entered = new CountDownLatch(1);
            this.release = new CountDownLatch(1);
            return this;
        }

        public void releaseFirstSend() {
            this.release.countDown();
        }

        /**
         * @param index 1-based send index from which sends start failing with {@link IOException}
         */
        public ScriptedEmitter failFrom(int index) {
            this.failFrom.set(index);
            return this;
        }

        /**
         * @param index 1-based send index that fails with {@link IOException} exactly once
         */
        public ScriptedEmitter failOnceAt(int index) {
            this.failExactlyAt = index;
            return this;
        }

        /**
         * @param index 1-based send index that blocks before returning or failing
         * @param millis blocking duration
         */
        public ScriptedEmitter blockOnSend(int index, long millis) {
            this.blockAt = index;
            this.blockAtMillis = millis;
            return this;
        }

        /**
         * @param index 1-based send index that throws the given runtime exception
         * @param error error to throw at that index
         */
        public ScriptedEmitter failWithAt(int index, RuntimeException error) {
            this.runtimeFailureAt = index;
            this.runtimeError = error;
            return this;
        }

        public ScriptedEmitter markDead() {
            this.dead = true;
            return this;
        }

        public List<Object> payloads() {
            synchronized (payloads) {
                return new ArrayList<>(payloads);
            }
        }

        public List<String> threadNames() {
            synchronized (threadNames) {
                return new ArrayList<>(threadNames);
            }
        }

        public List<String> traceIds() {
            synchronized (traceIds) {
                return new ArrayList<>(traceIds);
            }
        }

        public int sendCount() {
            return sendCount.get();
        }

        public boolean isCompleted() {
            return completed;
        }

        public boolean awaitEntered(long timeout, TimeUnit unit) throws InterruptedException {
            return entered.await(timeout, unit);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            int index = sendCount.incrementAndGet();
            threadNames.add(Thread.currentThread().getName());
            traceIds.add(MDC.get("traceId"));
            if (index == 1) {
                entered.countDown();
                if (release.getCount() > 0L) {
                    awaitQuietly(release);
                }
            }
            if (completed) {
                throw new IllegalStateException("ResponseBodyEmitter has already completed");
            }
            if (dead) {
                throw new IllegalStateException("ResponseBodyEmitter has already completed");
            }
            if (index == blockAt) {
                sleepQuietly(blockAtMillis);
            }
            if (index == runtimeFailureAt) {
                throw runtimeError;
            }
            if (index >= failFrom.get() || index == failExactlyAt) {
                throw new IOException("scripted failure at send " + index);
            }
            for (DataWithMediaType item : builder.build()) {
                payloads.add(item.getData());
            }
        }

        @Override
        public void complete() {
            completed = true;
            super.complete();
        }

        private static void awaitQuietly(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("scripted emitter was never released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private static void sleepQuietly(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
