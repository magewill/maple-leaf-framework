package cn.maple.core.framework.config.web;

import cn.maple.core.framework.filter.GXBaseRequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web基础配置类
 * <p>
 * 该配置类提供了Web应用的基础配置，包括请求日志记录过滤器等组件。
 * 通过Spring的@Configuration注解，在应用启动时自动注册相关Bean。
 * 该配置类是线程安全的，所有Bean都是单例模式，在应用上下文中共享。
 * </p>
 * <p>
 * <b>主要功能</b>：
 * 1. 注册请求日志记录过滤器(GXBaseRequestLoggingFilter)，用于记录HTTP请求的详细信息
 * 2. 支持分布式系统中的请求链路追踪，通过TraceId机制关联同一请求的所有日志
 * 3. 自动统计请求处理时间，便于性能监控和问题排查
 * 4. 提供请求上下文管理，确保请求信息在整个处理链路中可用
 * 5. 支持与GXWebClientConfig配合，实现全链路追踪和日志记录
 * 6. 自动处理请求头中的TraceId，支持跨服务调用的链路追踪
 * 7. 提供请求时间统计，记录请求处理耗时，便于性能分析
 * </p>
 * <p>
 * <b>技术特点</b>：
 * 1. 基于Spring的过滤器链机制，每个请求由独立的线程处理，不存在线程安全问题
 * 2. 使用ThreadLocal(通过MDC实现)存储TraceId，确保在高并发环境下的线程隔离
 * 3. 在请求结束时自动清理ThreadLocal资源，防止可能的内存泄漏
 * 4. 采用AOP思想，无侵入式地为所有请求添加日志记录和追踪功能
 * 5. 支持异步处理场景，通过GXMdcThreadUtils确保TraceId在子线程中传递
 * 6. 使用try-finally结构确保资源清理，即使在异常情况下也能正确释放资源
 * 7. 支持多种TraceId获取方式，包括请求头、MDC和自动生成
 * </p>
 * <p>
 * <b>使用示例</b>：
 * <pre>
 * // 1. 在Spring Boot应用中，只需引入该配置类所在的包即可自动启用配置
 * // 例如在启动类上添加包扫描
 * @SpringBootApplication(scanBasePackages = {"cn.maple.core.framework"})
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * <p>
 * // 2. 在日志配置文件中添加%X{X-B3-TraceId}占位符，输出TraceId
 * // logback.xml示例
 * &lt;configuration&gt;
 *     &lt;appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender"&gt;
 *         &lt;encoder&gt;
 *             &lt;pattern&gt;%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{X-B3-TraceId}] %-5level %logger{36} - %msg%n&lt;/pattern&gt;
 *         &lt;/encoder&gt;
 *     &lt;/appender&gt;
 * <p>
 *     &lt;appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender"&gt;
 *         &lt;file&gt;logs/application.log&lt;/file&gt;
 *         &lt;rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy"&gt;
 *             &lt;fileNamePattern&gt;logs/application.%d{yyyy-MM-dd}.log&lt;/fileNamePattern&gt;
 *             &lt;maxHistory&gt;30&lt;/maxHistory&gt;
 *         &lt;/rollingPolicy&gt;
 *         &lt;encoder&gt;
 *             &lt;pattern&gt;%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{X-B3-TraceId}] %-5level %logger{36} - %msg%n&lt;/pattern&gt;
 *         &lt;/encoder&gt;
 *     &lt;/appender&gt;
 * <p>
 *     &lt;root level="INFO"&gt;
 *         &lt;appender-ref ref="CONSOLE" /&gt;
 *         &lt;appender-ref ref="FILE" /&gt;
 *     &lt;/root&gt;
 * &lt;/configuration&gt;
 * <p>
 * // 3. 在Controller中可以通过MDC或GXTraceIdContextUtils获取当前请求的TraceId
 * @RestController
 * public class TestController {
 *     private static final Logger log = LoggerFactory.getLogger(TestController.class);
 *
 *     @GetMapping("/test")
 *     public String test() {
 *         // 方式一：直接从MDC获取
 *         String traceId = MDC.get("X-B3-TraceId");
 *         log.info("当前请求的TraceId: {}", traceId);
 * <p>
 *         // 方式二：通过工具类获取
 *         String traceId2 = GXTraceIdContextUtils.getTraceId();
 *         log.info("通过工具类获取的TraceId: {}", traceId2);
 * <p>
 *         // 业务处理...
 *         return "success";
 *     }
 * <p>
 *     // 4. 在异步方法中传递TraceId
 *     @GetMapping("/async")
 *     public CompletableFuture<String> asyncTest() {
 *         log.info("主线程开始处理请求");
 * <p>
 *         // 使用GXMdcThreadUtils包装异步任务，确保TraceId传递到子线程
 *         return CompletableFuture.supplyAsync(GXMdcThreadUtils.wrap(() -> {
 *             log.info("子线程处理中，TraceId: {}", GXTraceIdContextUtils.getTraceId());
 *             return "async success";
 *         }));
 *     }
 * <p>
 *     // 5. 在定时任务中使用TraceId
 *     @Scheduled(fixedRate = 60000)
 *     public void scheduledTask() {
 *         // 为定时任务设置TraceId
 *         GXTraceIdContextUtils.setTraceIdIfAbsent();
 *         try {
 *             log.info("定时任务执行中，TraceId: {}", GXTraceIdContextUtils.getTraceId());
 *             // 业务处理...
 *         } finally {
 *             // 清理TraceId，防止内存泄漏
 *             GXTraceIdContextUtils.removeTraceId();
 *         }
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <b>分布式追踪集成</b>：
 * 1. 该过滤器会自动从请求头中提取TraceId，支持与其他微服务系统的链路追踪集成
 * 2. 如果请求头中不存在TraceId，则会自动生成新的TraceId，格式为：应用名称:UUID
 * 3. 响应头中会包含TraceId和请求处理时间，便于全链路追踪和性能监控
 * 4. 与GXWebClientConfig配合使用时，会自动在HTTP请求间传递TraceId
 * 5. 与Dubbo过滤器配合使用时，会自动在RPC调用间传递TraceId
 * 6. 支持与Spring Cloud Sleuth/Zipkin集成，使用兼容的TraceId格式
 * 7. 支持与ELK日志系统集成，便于集中式日志分析和链路追踪
 * </p>
 * <p>
 * <b>性能与安全考虑</b>：
 * 1. 过滤器的性能开销很小，主要是字符串处理和ThreadLocal操作
 * 2. 在高并发场景下，确保在请求结束时清理ThreadLocal资源，防止内存泄漏
 * 3. TraceId不包含敏感信息，可以安全地在日志和请求头中传递
 * 4. 日志记录遵循最小化原则，只记录必要的信息，避免敏感数据泄露
 * 5. 使用静默异常处理，确保过滤器不会因异常而中断请求处理流程
 * 6. 支持配置是否记录请求体，避免记录敏感信息或大量数据
 * </p>
 * <p>
 * <b>与其他组件的集成</b>：
 * 1. 与GXWebClientConfig配合，实现HTTP客户端请求的TraceId传递
 * 2. 与GXDubboClientTraceIdFilter/GXDubboServerTraceIdFilter配合，实现RPC调用的TraceId传递
 * 3. 与GXTraceIdContextUtils工具类配合，提供统一的TraceId管理接口
 * 4. 与GXMdcThreadUtils配合，支持异步任务和线程池中的TraceId传递
 * 5. 与GXLoggerUtils配合，提供带有TraceId的日志记录功能
 * </p>
 *
 * @author britton chen <britton@126.com>
 */
@Configuration
public class GXBaseWebConfig {
    /**
     * 创建请求日志记录过滤器Bean
     * <p>
     * 该方法创建并配置GXBaseRequestLoggingFilter实例，用于记录HTTP请求的详细信息。
     * 过滤器会记录请求的URL、请求头、请求参数、请求体等信息，便于调试和问题排查。
     * 同时，过滤器还负责管理TraceId，确保分布式系统中的请求链路可追踪。
     * </p>
     * <p>
     * <b>过滤器的主要功能</b>：
     * 1. 在请求处理前(beforeRequest)设置或获取TraceId，确保请求链路可追踪
     * 2. 在请求处理过程中(doFilterInternal)记录请求开始时间和计算处理耗时
     * 3. 在请求处理后(afterRequest)清理TraceId，防止内存泄漏
     * 4. 自动从请求头中提取TraceId，支持跨服务调用时的链路追踪
     * 5. 如果请求头中不存在TraceId，则生成新的TraceId并设置到MDC中
     * 6. 支持从请求头中获取X-Request-Start-Time，用于计算全链路耗时
     * 7. 在响应头中添加X-Response-Time，记录请求处理时间
     * </p>
     * <p>
     * <b>线程安全说明</b>：
     * - 该过滤器是线程安全的，每个请求都有独立的处理上下文
     * - 过滤器在应用启动时创建单例，在多线程环境中共享使用
     * - TraceId的存储基于ThreadLocal(通过MDC实现)，确保了在高并发环境下的线程隔离
     * - 在请求结束时自动清理ThreadLocal资源，防止内存泄漏和上下文污染
     * - 使用try-finally结构确保即使发生异常也能正确清理资源
     * - 过滤器的所有操作都是幂等的，多次调用不会产生副作用
     * </p>
     * <p>
     * <b>使用场景</b>：
     * - 微服务架构中的请求链路追踪，通过TraceId关联同一请求的所有日志
     * - 分布式系统中的日志聚合与分析，便于问题排查和性能优化
     * - 多线程环境下的请求上下文传递，确保异步任务能够获取到正确的TraceId
     * - 请求性能监控和耗时统计，自动记录请求处理时间
     * - 与GXWebClientConfig配合，实现HTTP请求间的TraceId传递
     * - 与ELK日志系统集成，实现分布式日志追踪和分析
     * - 与APM工具集成，提供请求性能监控和分析
     * </p>
     * <p>
     * <b>工作原理</b>：
     * 1. 请求进入系统时，过滤器会尝试从请求头中获取TraceId（键名为X-B3-TraceId，兼容Spring Cloud Sleuth）
     * 2. 如果请求头中不存在TraceId，则从当前线程上下文获取
     * 3. 如果上下文中也不存在，则通过GXTraceIdContextUtils.generateTraceId()生成新的TraceId
     * 4. 将TraceId设置到MDC中，使其在日志输出时自动包含
     * 5. 记录请求开始时间，用于计算请求处理耗时
     * 6. 请求处理完成后，计算处理耗时并添加到响应头
     * 7. 从MDC中移除TraceId，释放资源
     * 8. 所有异常都会被捕获并记录，不会影响请求的正常处理
     * </p>
     * <p>
     * <b>配置示例</b>：
     * 在logback.xml中配置日志输出格式，包含TraceId：
     * <pre>
     * &lt;appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender"&gt;
     *     &lt;encoder&gt;
     *         &lt;pattern&gt;%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{X-B3-TraceId}] %-5level %logger{36} - %msg%n&lt;/pattern&gt;
     *     &lt;/encoder&gt;
     * &lt;/appender&gt;
     *
     * &lt;appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender"&gt;
     *     &lt;file&gt;logs/application.log&lt;/file&gt;
     *     &lt;rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy"&gt;
     *         &lt;fileNamePattern&gt;logs/application.%d{yyyy-MM-dd}.log&lt;/fileNamePattern&gt;
     *         &lt;maxHistory&gt;30&lt;/maxHistory&gt;
     *     &lt;/rollingPolicy&gt;
     *     &lt;encoder&gt;
     *         &lt;pattern&gt;%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{X-B3-TraceId}] %-5level %logger{36} - %msg%n&lt;/pattern&gt;
     *     &lt;/encoder&gt;
     * &lt;/appender&gt;
     * </pre>
     * </p>
     * <p>
     * <b>与GXWebClientConfig的集成</b>：
     * GXBaseRequestLoggingFilter与GXWebClientConfig配合使用，可以实现全链路追踪：
     * 1. GXBaseRequestLoggingFilter负责在服务端接收请求时设置TraceId
     * 2. GXWebClientConfig负责在客户端发送请求时传递TraceId
     * 3. 两者配合，可以在微服务调用链中传递TraceId，实现全链路追踪
     * </p>
     * <p>
     * <b>性能优化</b>：
     * 1. 过滤器使用静默异常处理，不会因异常而中断请求处理流程
     * 2. 使用try-finally结构确保资源清理，防止内存泄漏
     * 3. 日志记录使用条件判断，只在需要时记录详细信息
     * 4. TraceId生成使用高性能的UUID生成算法
     * 5. 请求体记录可配置，避免记录大量数据影响性能
     * </p>
     * <p>
     * <b>注意事项</b>：
     * 1. 该过滤器应该配置在过滤器链的最前面，确保所有请求都能被正确记录
     * 2. 在异步处理场景中，需要使用GXMdcThreadUtils包装异步任务，确保TraceId传递
     * 3. 在定时任务中，需要手动设置和清理TraceId
     * 4. 敏感信息不应该记录在日志中，可以通过配置过滤器的includePayload属性控制
     * 5. 在高并发场景下，应确保日志系统能够承受大量日志写入
     * </p>
     *
     * @return 配置好的GXBaseRequestLoggingFilter实例，用于记录请求日志和管理TraceId
     */
    @Bean
    public GXBaseRequestLoggingFilter requestLoggingFilter() {
        return new GXBaseRequestLoggingFilter();
    }
}