package cn.maple.core.framework.config;

import cn.maple.core.framework.service.GXCommandLineRunnerService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 应用启动后任务执行器
 * <p>
 * 该类实现了Spring Boot的CommandLineRunner接口，用于在应用启动完成后执行一系列初始化任务。
 * 它会自动收集所有实现了GXCommandLineRunnerService接口的Bean，并按顺序执行它们的run方法。
 * <p>
 * 线程安全说明：
 * 1. 任务执行在应用启动线程中串行进行，不存在并发问题
 * 2. 使用AtomicInteger计数器安全地跟踪任务执行情况
 * 3. 每个任务的异常都被单独捕获和处理，不会影响其他任务的执行
 * <p>
 * 使用示例：
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
    @Override
    public void run(String... args) throws Exception {
        Map<String, GXCommandLineRunnerService> commandLineRunnerServiceBeans = 
            GXSpringContextUtils.getApplicationContext().getBeansOfType(GXCommandLineRunnerService.class);
        
        if (commandLineRunnerServiceBeans.isEmpty()) {
            log.info("没有找到GXCommandLineRunnerService实现类，跳过启动任务执行");
            return;
        }
        
        log.info("开始执行{}个启动任务", commandLineRunnerServiceBeans.size());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        commandLineRunnerServiceBeans.forEach((beanName, service) -> {
            try {
                log.debug("执行启动任务: {}", beanName);
                service.run();
                successCount.incrementAndGet();
                log.debug("启动任务执行成功: {}", beanName);
            } catch (Exception e) {
                failCount.incrementAndGet();
                log.error("启动任务执行失败: {}, 错误信息: {}", beanName, e.getMessage(), e);
            }
        });
        
        log.info("启动任务执行完成，成功: {}，失败: {}", successCount.get(), failCount.get());
    }
}
