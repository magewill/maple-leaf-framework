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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Debezium引擎配置类 - 高性能CDC数据变更捕获
 * <p>
 * 该类负责初始化和管理Debezium引擎，用于捕获数据库变更事件(CDC - Change Data Capture)。
 * 采用现代Java虚拟线程技术，实现轻量级、高并发的数据库变更事件处理，显著降低系统资源占用。
 * </p>
 *
 * <p>
 * <b>核心特性：</b>
 * <ul>
 *   <li>基于Java 21+虚拟线程，极低内存占用和线程创建开销</li>
 *   <li>分布式锁机制确保集群环境下单实例运行，避免重复消费</li>
 *   <li>优雅关闭机制，确保数据完整性和资源正确释放</li>
 *   <li>异步事件处理，提高系统吞吐量和响应性能</li>
 *   <li>完善的异常处理和监控日志，便于问题排查</li>
 *   <li>条件化配置，支持动态启用/禁用功能</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>资源优化策略：</b>
 * <ul>
 *   <li>使用虚拟线程替代传统线程池，减少内存占用</li>
 *   <li>懒加载机制，仅在需要时创建虚拟线程</li>
 *   <li>无状态设计，避免不必要的对象缓存</li>
 *   <li>快速失败策略，及时释放无效资源</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>使用示例：</b>
 * <pre>
 * // 1. 实现GXDebeziumService接口
 * &#64;Service
 * public class MyDebeziumServiceImpl implements GXDebeziumService {
 *     &#64;Override
 *     public void initialEngineFlag(String lockKey) {
 *         // 实现分布式锁获取逻辑
 *         redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofMinutes(5));
 *     }
 *
 *     &#64;Override
 *     public void removeInitialEngineFlag(String lockKey) {
 *         // 实现分布式锁释放逻辑
 *         redisTemplate.delete(lockKey);
 *     }
 *
 *     &#64;Override
 *     public void processCaptureDataChange(Dict payload) {
 *         // 处理数据变更事件
 *         String operation = payload.getStr("op"); // c=create, u=update, d=delete
 *         Dict before = payload.getDict("before");
 *         Dict after = payload.getDict("after");
 *
 *         switch (operation) {
 *             case "c" -> handleInsert(after);
 *             case "u" -> handleUpdate(before, after);
 *             case "d" -> handleDelete(before);
 *         }
 *     }
 * }
 *
 * // 2. 配置Debezium属性 (application.yml)
 * maple:
 *   framework:
 *     enable:
 *       debezium: true
 *
 * debezium:
 *   config:
 *     name: "my-connector"
 *     connector.class: "io.debezium.connector.mysql.MySqlConnector"
 *     database.hostname: "localhost"
 *     database.port: "3306"
 *     database.user: "debezium"
 *     database.password: "password"
 *     database.server.id: "184054"
 *     database.server.name: "my-app-connector"
 *     database.include.list: "inventory"
 *     table.include.list: "inventory.customers,inventory.orders"
 *     database.history.kafka.bootstrap.servers: "localhost:9092"
 *     database.history.kafka.topic: "schema-changes.inventory"
 *
 * // 3. Spring容器会自动初始化该配置类并启动Debezium引擎
 * </pre>
 * </p>
 *
 * <p>
 * <b>性能监控指标：</b>
 * <ul>
 *   <li>虚拟线程创建数量和存活时间</li>
 *   <li>事件处理延迟和吞吐量</li>
 *   <li>异常发生频率和类型</li>
 *   <li>资源使用情况（内存、CPU）</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 *   <li>需要Java 21+支持虚拟线程特性</li>
 *   <li>确保数据库开启binlog且格式为ROW</li>
 *   <li>在集群环境下必须实现分布式锁</li>
 *   <li>建议配置适当的数据库连接池大小</li>
 * </ul>
 * </p>
 *
 * @author 系统架构师
 * @version 2.0.0
 * @see GXDebeziumService 数据变更处理服务接口
 * @see GXDebeziumProperties Debezium配置属性类
 * @since 1.0.0
 */
@Configuration
@Log4j2
@ConditionalOnExpression("${maple.framework.enable.debezium:false}")
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

        Object initialEngineFlag = debeziumService.getInitialEngineFlag();
        if (ObjectUtil.isNotNull(initialEngineFlag)) {
            log.info("其他服务实例已初始化Debezium引擎，当前服务实例不执行初始化操作");
            return;
        }

        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        log.info("开始初始化应用[{}]的Debezium引擎", appName);

        // 确保只有一个服务实例初始化引擎
        debeziumService.initialEngineFlag();
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
                                EXECUTOR_SERVICE.submit(() -> {
                                    debeziumService.processCaptureDataChange(payload);
                                });
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
        try {
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
        } finally {
            GXDebeziumService debeziumService = GXSpringContextUtils.getBean(GXDebeziumService.class);
            try {
                assert debeziumService != null;
                debeziumService.removeInitialEngineFlag();
            } catch (Exception e) {
                log.error("释放Debezium初始化锁时发生异常", e);
            }
        }
    }
}
