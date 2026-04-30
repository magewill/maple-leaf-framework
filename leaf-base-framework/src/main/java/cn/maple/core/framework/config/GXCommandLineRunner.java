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

@Component
@Log4j2
@Order(Integer.MAX_VALUE)
public class GXCommandLineRunner implements CommandLineRunner {
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
                    latch.countDown();
                }
            });
        });

        try {
            boolean completed = latch.await(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                log.error("启动任务执行超时({}分钟),部分任务可能未完成", TIMEOUT_MINUTES);
                throw new GXBusinessException("启动任务执行超时");
            }
            log.info("启动任务执行完成,成功: {},失败: {}", successCount.get(), failCount.get());

            if (failCount.get() > 0) {
                log.warn("存在{}个启动任务执行失败,请检查日志", failCount.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GXBusinessException("启动任务执行被中断", e);
        }
    }
}