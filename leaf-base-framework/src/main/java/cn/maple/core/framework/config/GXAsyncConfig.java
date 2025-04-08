package cn.maple.core.framework.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Log4j2
@Configuration
public class GXAsyncConfig implements AsyncConfigurer {
    @Override
    @Bean("asyncExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        // 获取核心线程数：根据 CPU 核心数设置，推荐值为 CPU 核心数 * 2
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        // 设置核心线程数
        threadPoolTaskExecutor.setCorePoolSize(corePoolSize);
        // 设置最大线程数：核心线程数的 2 倍，确保有足够的线程处理突发任务
        threadPoolTaskExecutor.setMaxPoolSize(corePoolSize * 2);
        /// 设置队列容量：根据任务量调整，避免过大导致内存溢出
        threadPoolTaskExecutor.setQueueCapacity(500);
        // 设置线程空闲存活时间（秒）：60 秒足够大多数场景
        threadPoolTaskExecutor.setKeepAliveSeconds(60);
        // 设置线程名前缀：便于日志追踪
        threadPoolTaskExecutor.setThreadNamePrefix("maple-framework-async-thread-");
        // 设置线程分组名称
        threadPoolTaskExecutor.setThreadGroupName("maple-framework-async-thread-group");
        // 设置拒绝策略：CallerRunsPolicy 让调用者线程执行任务，避免任务丢失
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等待所有任务完成后再关闭线程池
        threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        // 最多等待 60 秒
        threadPoolTaskExecutor.setAwaitTerminationSeconds(60);
        // 初始化线程池
        threadPoolTaskExecutor.initialize();
        // 打印线程池配置信息，便于调试
        log.info("Async thread pool initialized with corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, corePoolSize * 2, 500);
        return threadPoolTaskExecutor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, obj) -> {
            List<String> errors = CollUtil.newArrayList();
            errors.add("--------------Maple Leaf FrameWork异步调用，异常捕获--------------");
            errors.add(CharSequenceUtil.format("Exception message - {}", throwable.getMessage()));
            errors.add(CharSequenceUtil.format("Method name - {}", method.getName()));
            for (Object param : obj) {
                errors.add(CharSequenceUtil.format("Parameter value - {}", param));
            }
            errors.add("--------------Maple Leaf FrameWork异步调用，异常捕获--------------");
            String errorMsg = CollUtil.join(errors, System.lineSeparator());
            log.error(errorMsg, throwable);
        };
    }
}
