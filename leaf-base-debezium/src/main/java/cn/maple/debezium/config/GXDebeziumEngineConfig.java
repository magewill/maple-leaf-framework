package cn.maple.debezium.config;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.debezium.properties.GXDebeziumProperties;
import cn.maple.debezium.services.GXDebeziumService;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the lifecycle of the embedded Debezium engine.
 */
@Configuration
@Log4j2
@ConditionalOnExpression("${maple.framework.enable.debezium:false}")
public class GXDebeziumEngineConfig implements DisposableBean {
    // Keep the total tolerated renewal outage below the 5-minute lock TTL to avoid dual engines.
    private static final long LOCK_RENEW_INTERVAL_MINUTES = 1L;
    private static final long LOCK_RENEW_WATCHDOG_INTERVAL_SECONDS = 10L;
    private static final long LOCK_RENEW_STALE_TIMEOUT_MILLIS =
            Math.max(TimeUnit.SECONDS.toMillis(30L),
                    TimeUnit.MINUTES.toMillis(GXDebeziumEngineLockConfig.LOCK_TTL_MINUTES - 1L));
    private static final int MAX_LOCK_RENEW_FAILURES = 3;

    private final AtomicReference<ExecutorService> executorServiceRef = new AtomicReference<>();
    private final AtomicReference<ScheduledExecutorService> lockRenewalExecutorRef = new AtomicReference<>();
    private final AtomicReference<DebeziumEngine<ChangeEvent<String, String>>> debeziumEngineRef = new AtomicReference<>();
    private final AtomicBoolean engineInitialized = new AtomicBoolean(false);
    private final AtomicBoolean engineShutdown = new AtomicBoolean(false);
    private final AtomicBoolean engineLockAcquired = new AtomicBoolean(false);
    private final AtomicBoolean engineStopRequested = new AtomicBoolean(false);
    private final AtomicInteger renewalFailureCount = new AtomicInteger(0);
    private final AtomicLong lastLockRenewalSuccessTimeMillis = new AtomicLong(0L);

    @Resource
    private GXDebeziumProperties debeziumProperties;

    @Resource
    private GXDebeziumEngineLockConfig engineLockConfig;

    private ExecutorService getExecutorService() {
        if (engineShutdown.get()) {
            throw new RejectedExecutionException("Debezium engine is shutting down");
        }

        ExecutorService executorService = executorServiceRef.get();
        if (executorService == null) {
            synchronized (this) {
                executorService = executorServiceRef.get();
                if (executorService == null) {
                    Thread.Builder.OfVirtual ofVirtual = Thread.ofVirtual().name("debezium-virtual-thread#", 0)
                            .uncaughtExceptionHandler((thread, throwable) ->
                                    log.error("Unhandled Debezium virtual thread failure: thread={}, error={}",
                                            thread.getName(), throwable.getMessage(), throwable));
                    ThreadFactory factory = ofVirtual.factory();
                    ExecutorService newExecutorService = Executors.newThreadPerTaskExecutor(factory);
                    if (!executorServiceRef.compareAndSet(null, newExecutorService)) {
                        newExecutorService.shutdownNow();
                    }
                    executorService = executorServiceRef.get();
                    log.info("Debezium virtual thread executor created");
                }
            }
        }
        return executorService;
    }

    @PostConstruct
    public void initDebeziumEngine() {
        if (engineInitialized.get() || engineShutdown.get()) {
            log.info("Debezium engine init skipped: initialized={}, shutdown={}", engineInitialized.get(), engineShutdown.get());
            return;
        }

        GXDebeziumService debeziumService = GXSpringContextUtils.getBean(GXDebeziumService.class);
        if (ObjectUtil.isNull(debeziumService)) {
            log.error("GXDebeziumService bean is required to start Debezium engine");
            return;
        }
        if (ObjectUtil.isNull(engineLockConfig)) {
            log.error("GXDebeziumEngineLockConfig bean is required to start Debezium engine");
            return;
        }

        String lockKey = getLockKey();
        try {
            if (!engineLockConfig.tryLock(lockKey)) {
                log.info("Debezium engine lock is owned by another instance");
                return;
            }
            engineLockAcquired.set(true);
            engineStopRequested.set(false);
            markLockRenewalSuccess();
            startLockRenewal(engineLockConfig, lockKey);

            log.info("Starting Debezium engine: app={}", GXCommonUtils.getEnvironmentValue("spring.application.name", String.class));
            Map<String, String> config = debeziumProperties.getConfig();
            if (config == null || config.isEmpty()) {
                log.error("Debezium config is empty");
                return;
            }

            if (!validateConfiguration(config)) {
                log.error("Debezium config validation failed");
                return;
            }

            DebeziumEngine<ChangeEvent<String, String>> engine = createDebeziumEngine(config, debeziumService);
            debeziumEngineRef.set(engine);

            try {
                getExecutorService().execute(() -> runDebeziumEngine(engine, debeziumService, lockKey));
                log.info("Debezium engine start task submitted: app={}", GXCommonUtils.getEnvironmentValue("spring.application.name", String.class));
            } catch (RejectedExecutionException e) {
                log.error("Debezium engine start rejected: {}", e.getMessage(), e);
                debeziumEngineRef.compareAndSet(engine, null);
            }
        } catch (Exception e) {
            log.error("Debezium engine init failed: {}", e.getMessage(), e);
        } finally {
            if (!engineInitialized.get() && debeziumEngineRef.get() == null) {
                releaseEngineLock(engineLockConfig, lockKey);
            }
        }
    }

    private DebeziumEngine<ChangeEvent<String, String>> createDebeziumEngine(
            Map<String, String> config, GXDebeziumService debeziumService) {
        Properties properties = new Properties();
        Set<Map.Entry<String, String>> entries = config.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            properties.setProperty(entry.getKey(), entry.getValue());
        }

        return DebeziumEngine.create(Json.class)
                .using(properties)
                .notifying(record -> handleChangeEvent(record, debeziumService))
                .build();
    }

    private void handleChangeEvent(ChangeEvent<String, String> record, GXDebeziumService debeziumService) {
        try {
            if (engineShutdown.get()) {
                log.debug("Debezium event ignored because engine is shutting down");
                return;
            }
            if (engineStopRequested.get()) {
                throw new IllegalStateException("Debezium engine stop was requested after lock renewal failure");
            }
            if (record == null || record.value() == null) {
                log.warn("Debezium event ignored because value is empty");
                return;
            }

            log.debug("Debezium event received: key={}", record.key());
            Dict dbChangeData = JSONUtil.toBean(record.value(), Dict.class);
            Dict payload = Convert.convert(Dict.class, dbChangeData.getObj("payload"));
            if (payload == null) {
                log.warn("Debezium event ignored because payload is empty: key={}", record.key());
                return;
            }

            processPayload(debeziumService, payload);
        } catch (RuntimeException e) {
            log.error("Debezium event handling failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Debezium event handling failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Debezium event handling failed", e);
        }
    }

    private void processPayload(GXDebeziumService debeziumService, Dict payload) {
        long startTime = System.currentTimeMillis();
        debeziumService.processCaptureDataChange(payload);
        log.debug("Debezium event processed: cost={}ms", System.currentTimeMillis() - startTime);
    }

    private void runDebeziumEngine(DebeziumEngine<ChangeEvent<String, String>> engine,
                                   GXDebeziumService debeziumService,
                                   String lockKey) {
        engineInitialized.set(true);
        log.info("Debezium engine started: app={}", GXCommonUtils.getEnvironmentValue("spring.application.name", String.class));
        try {
            engine.run();
        } catch (Exception e) {
            log.error("Debezium engine run failed: {}", e.getMessage(), e);
        } finally {
            engineInitialized.set(false);
            debeziumEngineRef.compareAndSet(engine, null);
            releaseEngineLock(engineLockConfig, lockKey);
            log.info("Debezium engine task exited: app={}", GXCommonUtils.getEnvironmentValue("spring.application.name", String.class));
        }
    }

    private void startLockRenewal(GXDebeziumEngineLockConfig engineLockConfig, String lockKey) {
        ScheduledExecutorService existingExecutor = lockRenewalExecutorRef.get();
        if (existingExecutor != null && !existingExecutor.isShutdown()) {
            return;
        }

        AtomicInteger threadIndex = new AtomicInteger(0);
        ScheduledExecutorService renewalExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "debezium-lock-renewal-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        if (!lockRenewalExecutorRef.compareAndSet(existingExecutor, renewalExecutor)) {
            renewalExecutor.shutdownNow();
            return;
        }

        renewalExecutor.scheduleWithFixedDelay(() -> {
            if (!engineLockAcquired.get() || engineShutdown.get()) {
                return;
            }
            try {
                if (!engineLockConfig.renew(lockKey)) {
                    log.error("Debezium engine lock ownership was lost; closing engine");
                    closeEngineAfterLockProblem();
                    return;
                }
                markLockRenewalSuccess();
            } catch (Exception e) {
                int failures = renewalFailureCount.incrementAndGet();
                log.error("Debezium engine lock renewal failed: failures={}, maxFailures={}, error={}",
                        failures, MAX_LOCK_RENEW_FAILURES, e.getMessage(), e);
                if (failures >= MAX_LOCK_RENEW_FAILURES) {
                    log.error("Debezium engine lock renewal failure threshold reached; closing engine");
                    closeEngineAfterLockProblem();
                }
            }
        }, LOCK_RENEW_INTERVAL_MINUTES, LOCK_RENEW_INTERVAL_MINUTES, TimeUnit.MINUTES);

        renewalExecutor.scheduleWithFixedDelay(this::checkLockRenewalFreshness,
                LOCK_RENEW_WATCHDOG_INTERVAL_SECONDS,
                LOCK_RENEW_WATCHDOG_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private void markLockRenewalSuccess() {
        renewalFailureCount.set(0);
        lastLockRenewalSuccessTimeMillis.set(System.currentTimeMillis());
    }

    private void checkLockRenewalFreshness() {
        if (!engineLockAcquired.get() || engineShutdown.get() || engineStopRequested.get()) {
            return;
        }

        long lastSuccessTime = lastLockRenewalSuccessTimeMillis.get();
        if (lastSuccessTime <= 0L) {
            return;
        }

        long elapsedMillis = System.currentTimeMillis() - lastSuccessTime;
        if (elapsedMillis < LOCK_RENEW_STALE_TIMEOUT_MILLIS) {
            return;
        }

        log.error("Debezium engine lock renewal watchdog timeout: elapsedMillis={}, timeoutMillis={}; closing engine",
                elapsedMillis, LOCK_RENEW_STALE_TIMEOUT_MILLIS);
        closeEngineAfterLockProblem();
    }

    private void closeEngineAfterLockProblem() {
        if (!engineStopRequested.compareAndSet(false, true)) {
            return;
        }

        DebeziumEngine<ChangeEvent<String, String>> engine = debeziumEngineRef.get();
        if (engine == null) {
            return;
        }

        try {
            engine.close();
        } catch (Exception e) {
            log.error("Debezium engine close after lock problem failed: {}", e.getMessage(), e);
        }
    }

    private void stopLockRenewal() {
        ScheduledExecutorService renewalExecutor = lockRenewalExecutorRef.getAndSet(null);
        if (renewalExecutor != null) {
            renewalExecutor.shutdownNow();
        }
    }

    private void releaseEngineLock(GXDebeziumEngineLockConfig engineLockConfig, String lockKey) {
        if (!engineLockAcquired.compareAndSet(true, false)) {
            return;
        }

        renewalFailureCount.set(0);
        engineStopRequested.set(false);
        lastLockRenewalSuccessTimeMillis.set(0L);
        stopLockRenewal();
        try {
            engineLockConfig.unlock(lockKey);
            log.info("Debezium engine lock released");
        } catch (Exception e) {
            log.error("Debezium engine lock release failed; the Redis key will expire by TTL: {}", e.getMessage(), e);
        }
    }

    private boolean validateConfiguration(Map<String, String> config) {
        String[] requiredConfigs = {
                "name",
                "connector.class",
                "database.hostname",
                "database.port",
                "database.user",
                "database.password",
                "database.server.id",
                "topic.prefix"
        };

        for (String requiredConfig : requiredConfigs) {
            if (!config.containsKey(requiredConfig) || CharSequenceUtil.isBlank(config.get(requiredConfig))) {
                log.error("Required Debezium config is missing: {}", requiredConfig);
                return false;
            }
        }

        return true;
    }

    @Override
    public void destroy() {
        if (engineShutdown.getAndSet(true)) {
            log.info("Debezium engine shutdown skipped because it is already closed");
            return;
        }

        String lockKey = getLockKey();
        log.info("Stopping Debezium engine: app={}", GXCommonUtils.getEnvironmentValue("spring.application.name", String.class));

        DebeziumEngine<ChangeEvent<String, String>> engine = debeziumEngineRef.getAndSet(null);
        if (engine != null) {
            try {
                engine.close();
                log.info("Debezium engine closed");
            } catch (Exception e) {
                log.error("Debezium engine close failed: {}", e.getMessage(), e);
            }
        }

        shutdownExecutorService(executorServiceRef.getAndSet(null));
        stopLockRenewal();

        if (engineLockConfig != null) {
            releaseEngineLock(engineLockConfig, lockKey);
        }

        log.info("Debezium engine stopped: app={}", GXCommonUtils.getEnvironmentValue("spring.application.name", String.class));
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        if (executorService == null || executorService.isShutdown()) {
            return;
        }

        executorService.shutdown();
        try {
            boolean terminated = false;
            for (int i = 0; i < 2 && !terminated; i++) {
                log.info("Waiting for Debezium executor shutdown: timeout=60s");
                terminated = executorService.awaitTermination(60, TimeUnit.SECONDS);
            }

            if (!terminated) {
                log.warn("Debezium executor did not stop before timeout; forcing shutdown");
                executorService.shutdownNow();
                executorService.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for Debezium executor shutdown", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private String getLockKey() {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        String activeProfile = GXCommonUtils.getActiveProfile();
        return CharSequenceUtil.format(GXDebeziumService.LOCK_NAME_FORMAT, appName, activeProfile);
    }
}
