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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the lifecycle of the embedded Debezium engine.
 */
@Configuration
@Log4j2
@ConditionalOnExpression("${maple.framework.enable.debezium:false}")
public class GXDebeziumEngineConfig implements DisposableBean {
    private static final long LOCK_RENEW_INTERVAL_MINUTES = Math.max(1L, GXDebeziumService.LOCK_TTL_MINUTES / 2L);

    private final AtomicReference<ExecutorService> executorServiceRef = new AtomicReference<>();
    private final AtomicReference<ScheduledExecutorService> lockRenewalExecutorRef = new AtomicReference<>();
    private final AtomicReference<DebeziumEngine<ChangeEvent<String, String>>> debeziumEngineRef = new AtomicReference<>();
    private final AtomicBoolean engineInitialized = new AtomicBoolean(false);
    private final AtomicBoolean engineShutdown = new AtomicBoolean(false);
    private final AtomicBoolean engineLockAcquired = new AtomicBoolean(false);

    @Resource
    private GXDebeziumProperties debeziumProperties;

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

        String lockKey = getLockKey();
        try {
            if (!debeziumService.tryInitialEngineLock(lockKey)) {
                log.info("Debezium engine lock is owned by another instance");
                return;
            }
            engineLockAcquired.set(true);
            startLockRenewal(debeziumService, lockKey);

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
                releaseEngineLock(debeziumService, lockKey);
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

            getExecutorService().submit(() -> processPayload(debeziumService, payload));
        } catch (RejectedExecutionException e) {
            log.warn("Debezium event ignored because executor rejected the task: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Debezium event parse failed: {}", e.getMessage(), e);
        }
    }

    private void processPayload(GXDebeziumService debeziumService, Dict payload) {
        try {
            long startTime = System.currentTimeMillis();
            debeziumService.processCaptureDataChange(payload);
            log.debug("Debezium event processed: cost={}ms", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Debezium event processing failed: {}", e.getMessage(), e);
        }
    }

    private void runDebeziumEngine(DebeziumEngine<ChangeEvent<String, String>> engine, GXDebeziumService debeziumService, String lockKey) {
        engineInitialized.set(true);
        log.info("Debezium engine started: app={}", GXCommonUtils.getEnvironmentValue("spring.application.name", String.class));
        try {
            engine.run();
        } catch (Exception e) {
            log.error("Debezium engine run failed: {}", e.getMessage(), e);
        } finally {
            engineInitialized.set(false);
            debeziumEngineRef.compareAndSet(engine, null);
            releaseEngineLock(debeziumService, lockKey);
            log.info("Debezium engine task exited: app={}", GXCommonUtils.getEnvironmentValue("spring.application.name", String.class));
        }
    }

    private void startLockRenewal(GXDebeziumService debeziumService, String lockKey) {
        ScheduledExecutorService existingExecutor = lockRenewalExecutorRef.get();
        if (existingExecutor != null && !existingExecutor.isShutdown()) {
            return;
        }

        ScheduledExecutorService renewalExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "debezium-lock-renewal");
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
                if (!debeziumService.renewInitialEngineLock(lockKey)) {
                    log.error("Debezium engine lock ownership was lost; closing engine");
                    DebeziumEngine<ChangeEvent<String, String>> engine = debeziumEngineRef.get();
                    if (engine != null) {
                        engine.close();
                    }
                }
            } catch (Exception e) {
                log.error("Debezium engine lock renewal failed: {}", e.getMessage(), e);
            }
        }, LOCK_RENEW_INTERVAL_MINUTES, LOCK_RENEW_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void stopLockRenewal() {
        ScheduledExecutorService renewalExecutor = lockRenewalExecutorRef.getAndSet(null);
        if (renewalExecutor != null) {
            renewalExecutor.shutdownNow();
        }
    }

    private void releaseEngineLock(GXDebeziumService debeziumService, String lockKey) {
        if (!engineLockAcquired.compareAndSet(true, false)) {
            return;
        }

        stopLockRenewal();
        try {
            debeziumService.initialEngineUnLock(lockKey);
            log.info("Debezium engine lock released");
        } catch (Exception e) {
            engineLockAcquired.set(true);
            log.error("Debezium engine lock release failed: {}", e.getMessage(), e);
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

        try {
            GXDebeziumService debeziumService = GXSpringContextUtils.getBean(GXDebeziumService.class);
            if (debeziumService != null) {
                releaseEngineLock(debeziumService, lockKey);
            }
        } catch (Exception e) {
            log.error("Debezium engine lock release failed during shutdown: {}", e.getMessage(), e);
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
