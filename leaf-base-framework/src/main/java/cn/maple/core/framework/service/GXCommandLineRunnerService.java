package cn.maple.core.framework.service;

/**
 * 应用启动完成之后需要执行的逻辑服务接口
 * <p>
 * 该接口用于定义应用程序启动完成后需要执行的初始化逻辑，例如：
 * - 预热缓存数据
 * - 初始化系统参数
 * - 加载字典数据
 * - 启动定时任务
 * - 建立长连接
 * </p>
 *
 * <p>线程安全说明：</p>
 * <p>该接口的实现类应当考虑线程安全问题，因为在应用启动阶段可能会有多个初始化任务并行执行。</p>
 *
 * <p>性能考虑：</p>
 * <p>1. 实现类中的初始化逻辑应当尽量高效，避免阻塞应用启动</p>
 * <p>2. 对于耗时较长的初始化任务，可以考虑异步执行</p>
 * <p>3. 应当合理控制初始化任务的执行顺序，可以使用@Order注解指定优先级</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * @Component
 * @Order(10) // 指定执行优先级
 * public class CacheWarmUpService implements GXCommandLineRunnerService {
 *     @Autowired
 *     private UserService userService;
 *
 *     @Autowired
 *     private ProductService productService;
 *
 *     @Override
 *     public void run() {
 *         // 预热用户缓存
 *         userService.warmUpCache();
 *
 *         // 预热商品缓存
 *         productService.warmUpCache();
 *
 *         // 其他初始化逻辑...
 *     }
 * }
 * </pre>
 *
 * @author britton <britton@126.com>
 */
public interface GXCommandLineRunnerService {
    /**
     * 执行初始化逻辑
     * <p>
     * 该方法会在应用启动完成后被框架自动调用，用于执行各种初始化任务。
     * 实现类应当在此方法中编写具体的初始化逻辑。
     * </p>
     * <p>
     * 注意事项：
     * 1. 该方法不应抛出未捕获的异常，应当妥善处理各种异常情况
     * 2. 如果初始化失败，应当记录详细日志，但不应影响应用的正常启动
     * 3. 对于关键的初始化任务，可以实现重试机制
     * </p>
     */
    void run();
}
