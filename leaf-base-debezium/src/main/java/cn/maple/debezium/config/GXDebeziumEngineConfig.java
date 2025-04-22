package cn.maple.debezium.config;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
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
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Debezium引擎配置类
 * <p>
 * 该类负责初始化和管理Debezium引擎，用于捕获数据库变更事件(CDC)。
 * 它使用虚拟线程执行Debezium引擎，并在应用关闭时负责优雅地关闭引擎和释放资源。
 * </p>
 * <p>
 * 在分布式环境下，通过{@link GXDebeziumService}接口提供的分布式锁机制，
 * 确保只有一个服务实例初始化并运行Debezium引擎，避免重复消费数据库变更事件。
 * </p>
 * <p>
 * 使用示例：
 * 1. 实现{@link GXDebeziumService}接口
 * 2. 配置Debezium相关属性（通过{@link GXDebeziumProperties}）
 * 3. Spring容器会自动初始化该配置类并启动Debezium引擎
 * </p>
 *
 * @see GXDebeziumService
 * @see GXDebeziumProperties
 */
@Configuration
@Log4j2
public class GXDebeziumEngineConfig implements DisposableBean {
    /**
     * 用于执行Debezium引擎的线程池
     * <p>
     * 使用Java虚拟线程(Virtual Thread)实现，相比传统线程更加轻量级，
     * 适合IO密集型任务，如数据库事件监听。
     * </p>
     */
    private static final ExecutorService EXECUTOR_SERVICE;

    static {
        Thread.Builder.OfVirtual ofVirtual = Thread.ofVirtual().name("debezium-virtual-thread#", 1);
        ThreadFactory factory = ofVirtual.factory();
        EXECUTOR_SERVICE = Executors.newThreadPerTaskExecutor(factory);
    }

    /**
     * Debezium引擎实例
     * <p>
     * 负责连接数据库并捕获数据变更事件
     * </p>
     */
    private DebeziumEngine<ChangeEvent<String, String>> debeziumEngine = null;

    /**
     * Debezium配置属性
     * <p>
     * 包含连接数据库的配置信息以及Debezium的相关配置
     * </p>
     */
    @Resource
    private GXDebeziumProperties debeziumProperties;

    /**
     * 初始化Debezium引擎
     * <p>
     * 该方法在Spring容器启动时自动执行，负责初始化Debezium引擎并启动数据库变更事件监听。
     * 通过分布式锁机制确保在分布式环境下只有一个服务实例初始化并运行Debezium引擎。
     * </p>
     * <p>
     * 初始化流程：
     * 1. 获取GXDebeziumService实现类
     * 2. 获取分布式锁，确保只有一个服务实例初始化引擎
     * 3. 加载Debezium配置
     * 4. 创建并配置Debezium引擎
     * 5. 启动引擎
     * 6. 释放分布式锁
     * </p>
     */
    @PostConstruct
    public void initDebeziumEngine() {
        GXDebeziumService debeziumService = GXSpringContextUtils.getBean(GXDebeziumService.class);
        if (ObjectUtil.isNull(debeziumService)) {
            log.error("请实现GXDebeziumService接口，Debezium引擎初始化失败");
            return;
        }

        String initialLocKey = "debezium.initialLock";
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        log.info("开始初始化应用[{}]的Debezium引擎", appName);

        // 获取分布式锁，确保只有一个服务实例初始化引擎
        debeziumService.initialEngineLock(initialLocKey);
        try {
            // 加载Debezium配置
            Map<String, String> config = debeziumProperties.getConfig();
            if (config == null || config.isEmpty()) {
                log.error("Debezium配置为空，请检查配置信息");
                return;
            }

            Properties properties = new Properties();
            Set<Map.Entry<String, String>> entries = config.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                properties.setProperty(entry.getKey(), entry.getValue());
            }

            // 创建并配置Debezium引擎
            try {
                debeziumEngine = DebeziumEngine.create(Json.class)
                        .using(properties)
                        .notifying(record -> {
                            try {
                                if (record == null || record.value() == null) {
                                    log.warn("接收到空的数据库变更记录");
                                    return;
                                }

                                log.debug("监听到数据库数据变化 : {}", record);
                                String value = record.value();
                                Dict dbChangeData = JSONUtil.toBean(value, Dict.class);
                                Dict payload = Convert.convert(Dict.class, dbChangeData.getObj("payload"));

                                // 调用自定义处理逻辑
                                debeziumService.processCaptureDataChange(payload);
                            } catch (Exception e) {
                                log.error("处理数据库变更事件时发生异常", e);
                            }
                        }).build();

                // 启动引擎
                try {
                    EXECUTOR_SERVICE.execute(debeziumEngine);
                    log.info("应用[{}]的Debezium引擎启动成功", appName);
                } catch (RejectedExecutionException e) {
                    log.error("Debezium引擎启动失败，线程池已关闭或已满", e);
                }
            } catch (Exception e) {
                log.error("创建Debezium引擎时发生异常", e);
            }
        } catch (Exception e) {
            log.error("初始化Debezium引擎时发生异常", e);
        } finally {
            // 释放分布式锁
            try {
                debeziumService.initialEngineUnLock(initialLocKey);
            } catch (Exception e) {
                log.error("释放Debezium初始化锁时发生异常", e);
            }
        }
    }

    /**
     * 销毁Debezium引擎并释放资源
     * <p>
     * 该方法在Spring容器关闭时自动执行，负责优雅地关闭Debezium引擎和释放相关资源。
     * 确保在应用关闭时能够正确地释放数据库连接和线程资源，避免资源泄漏。
     * </p>
     * <p>
     * 销毁流程：
     * 1. 关闭Debezium引擎
     * 2. 关闭线程池
     * 3. 等待线程池中的任务完成
     * </p>
     *
     * @throws Exception 在关闭过程中可能发生的异常，这些异常会被记录但不会重新抛出，
     *                   以允许其他Bean也能释放它们的资源
     */
    @Override
    public void destroy() throws Exception {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        log.info("开始关闭应用[{}]的Debezium引擎", appName);

        // 关闭Debezium引擎
        if (debeziumEngine != null) {
            try {
                debeziumEngine.close();
                log.info("Debezium引擎已关闭");
            } catch (Exception e) {
                log.error("关闭Debezium引擎时发生异常", e);
            }
        }

        // 关闭线程池并等待任务完成
        if (!EXECUTOR_SERVICE.isShutdown()) {
            EXECUTOR_SERVICE.shutdown();
            try {
                // 等待任务完成，最多等待2分钟
                boolean terminated = false;
                for (int i = 0; i < 2 && !terminated; i++) {
                    log.info("等待Debezium线程池关闭，最多再等待60秒...");
                    terminated = EXECUTOR_SERVICE.awaitTermination(60, TimeUnit.SECONDS);
                }

                if (!terminated) {
                    log.warn("Debezium线程池未能在指定时间内完全关闭，将强制关闭");
                    EXECUTOR_SERVICE.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("等待Debezium线程池关闭时被中断，将强制关闭线程池", e);
                EXECUTOR_SERVICE.shutdownNow();
                Thread.currentThread().interrupt(); // 重新设置中断标志
            }
        }

        log.info("~~~~ 应用[{}]的Debezium引擎关闭完成，再见 ~~~~~", appName);
    }
}
