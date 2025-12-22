package cn.maple.core.framework.config;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXCommandLineRunnerService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 应用启动后任务执行器
 * <p>
 * 该类实现了Spring Boot的CommandLineRunner接口,用于在应用启动完成后执行一系列初始化任务。
 * 它会自动收集所有实现了GXCommandLineRunnerService接口的Bean,并并发执行它们的run方法。
 * <p>
 * 线程安全说明:
 * 1. 使用虚拟线程并发执行任务,提高启动效率
 * 2. 使用AtomicInteger计数器安全地跟踪任务执行情况
 * 3. 使用CountDownLatch确保所有任务完成后再继续
 * 4. 每个任务的异常都被单独捕获和处理,不会影响其他任务的执行
 * <p>
 * 使用示例:
 * <pre>
 * // 创建自定义的启动任务
 * @Component
 * public class MyStartupTask implements GXCommandLineRunnerService {
 *     @Override
 *     public void run() {
 *         // 执行初始化逻辑
 *     }
 * }
 * </pre>
 *
 * @author britton chen <britton@126.com>
 */
@Component
@Log4j2
@Order(Integer.MAX_VALUE) // 确保在其他CommandLineRunner之后执行
public class GXCommandLineRunner implements CommandLineRunner {
    // 配置超时时间,防止无限等待
    private static final long TIMEOUT_MINUTES = 10;

    @Override
    public void run(String... args) throws Exception {
        Map<String, GXCommandLineRunnerService> commandLineRunnerServiceBeans =
                GXSpringContextUtils.getApplicationContext().getBeansOfType(GXCommandLineRunnerService.class);

        if (commandLineRunnerServiceBeans.isEmpty()) {
            log.info("没有找到GXCommandLineRunnerService实现类,跳过启动任务执行");
            return;
        }

        log.info("开始执行{}个启动任务", commandLineRunnerServiceBeans.size());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(commandLineRunnerServiceBeans.size());

        // 并发执行所有启动任务
        commandLineRunnerServiceBeans.forEach((beanName, service) -> {
            Thread.ofVirtual().start(() -> {
                try {
                    log.debug("执行启动任务: {}", beanName);
                    service.run();
                    successCount.incrementAndGet();
                    log.debug("启动任务执行成功: {}", beanName);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("启动任务执行失败: {}, 错误信息: {}", beanName, e.getMessage(), e);
                } finally {
                    // 确保在finally块中释放latch,防止死锁
                    latch.countDown();
                }
            });
        });

        // 在主线程中等待所有任务完成
        try {
            boolean completed = latch.await(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                log.error("启动任务执行超时({}分钟),部分任务可能未完成", TIMEOUT_MINUTES);
                throw new GXBusinessException("启动任务执行超时");
            }
            log.info("启动任务执行完成,成功: {},失败: {}", successCount.get(), failCount.get());

            // 如果有任务失败,根据业务需求决定是否抛出异常
            if (failCount.get() > 0) {
                log.warn("存在{}个启动任务执行失败,请检查日志", failCount.get());
                // 可选: 抛出异常阻止应用启动
                // throw new GXBusinessException("部分启动任务执行失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
            throw new GXBusinessException("启动任务执行被中断", e);
        }
    }
}