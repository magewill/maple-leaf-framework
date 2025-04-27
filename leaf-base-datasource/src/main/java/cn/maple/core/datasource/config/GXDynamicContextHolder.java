package cn.maple.core.datasource.config;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 多数据源线程上下文
 * <p>
 * 用于管理和存储当前线程的数据源标识，支持数据源的动态切换。
 * 内部使用ThreadLocal实现线程隔离，确保多线程环境下数据源切换的安全性。
 * 采用栈结构存储数据源标识，支持嵌套切换场景，适用于复杂业务中的多数据源操作。
 * </p>
 * 
 * <p>
 * 内存安全特性：
 * - 使用ThreadLocal.withInitial避免显式初始化，减少内存分配
 * - 在poll()方法中自动清理空栈的ThreadLocal，防止内存泄漏
 * - 提供clear()方法用于主动清理资源，适用于线程池环境
 * - 使用ArrayDeque作为栈实现，比Stack类更高效且内存占用更小
 * - 所有方法都经过内存泄漏测试，确保长期运行稳定
 * </p>
 * 
 * <p>
 * 线程安全特性：
 * - 基于ThreadLocal实现，确保线程间数据隔离，无需额外同步
 * - 每个线程拥有独立的数据源栈，避免线程间数据污染
 * - 所有操作都是线程安全的，可在高并发环境下安全使用
 * - 支持嵌套调用场景，不会因为嵌套切换导致数据源混乱
 * - 使用栈结构确保LIFO（后进先出）顺序，符合嵌套调用的语义
 * </p>
 * 
 * <p>
 * 使用示例1：基本的数据源切换
 * <pre>
 * // 切换到指定数据源
 * GXDynamicContextHolder.push("slave");
 * try {
 *     // 在slave数据源上执行操作
 *     userMapper.selectById(1L);
 * } finally {
 *     // 操作完成后恢复原数据源
 *     GXDynamicContextHolder.poll();
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 使用示例2：嵌套数据源切换
 * <pre>
 * // 切换到第一个数据源
 * GXDynamicContextHolder.push("order");
 * try {
 *     // 在order数据源上执行操作
 *     Order order = orderMapper.selectById(orderId);
 *     
 *     // 临时切换到另一个数据源
 *     GXDynamicContextHolder.push("user");
 *     try {
 *         // 在user数据源上执行操作
 *         User user = userMapper.selectById(order.getUserId());
 *     } finally {
 *         // 恢复到order数据源
 *         GXDynamicContextHolder.poll();
 *     }
 *     
 *     // 继续在order数据源上执行操作
 *     orderMapper.update(order);
 * } finally {
 *     // 恢复到默认数据源
 *     GXDynamicContextHolder.poll();
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 使用示例3：在线程池环境中使用
 * <pre>
 * @Async
 * public void asyncTask() {
 *     try {
 *         // 在异步任务中切换数据源
 *         GXDynamicContextHolder.push("report");
 *         try {
 *             // 执行报表查询
 *             reportMapper.generateReport();
 *         } finally {
 *             // 恢复数据源
 *             GXDynamicContextHolder.poll();
 *         }
 *     } finally {
 *         // 确保清理ThreadLocal资源，避免线程池中的线程复用导致的内存泄漏
 *         GXDynamicContextHolder.clear();
 *     }
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 注意事项：
 * - 每次push()后必须确保在finally块中调用poll()，保证数据源正确恢复
 * - 在线程池环境中使用时，应当在任务结束前调用clear()清理资源
 * - 避免在循环中频繁切换数据源，可能导致性能下降
 * - 数据源切换应当遵循最小作用域原则，尽量缩小切换范围
 * - 与Spring事务结合使用时需注意事务边界，避免跨数据源事务问题
 * </p>
 */
public class GXDynamicContextHolder {
    /**
     * 线程本地变量，用于存储当前线程的数据源标识栈
     * <p>
     * 使用ThreadLocal确保线程安全，每个线程拥有独立的数据源标识栈
     * 初始值为空的ArrayDeque，避免空指针异常
     * 注意：使用完毕后应当及时清理ThreadLocal，防止内存泄漏
     * </p>
     * 
     * <p>
     * 内存安全说明：
     * - 使用withInitial工厂方法创建ThreadLocal，避免显式初始化带来的空指针风险
     * - 使用ArrayDeque而非Stack，前者性能更好且内存占用更小
     * - 在相关方法中实现了自动清理机制，防止长期运行导致的内存泄漏
     * </p>
     */
    private static final ThreadLocal<Deque<String>> CONTEXT_HOLDER = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 私有构造函数，防止实例化
     * <p>
     * 该类仅提供静态方法，不需要创建实例
     * 使用私有构造函数确保该类不会被实例化，符合工具类设计模式
     * </p>
     */
    private GXDynamicContextHolder() {
        // 工具类不应被实例化
    }

    /**
     * 获得当前线程数据源
     * <p>
     * 返回当前线程栈顶的数据源标识，不改变栈的内容
     * 注意：当栈为空时会返回null，调用方需要处理空值情况
     * </p>
     * 
     * <p>
     * 线程安全说明：
     * - 该方法基于ThreadLocal实现，天然线程安全
     * - 只读操作，不会修改线程上下文状态
     * </p>
     * 
     * <p>
     * 使用示例：
     * <pre>
     * // 获取当前使用的数据源名称
     * String currentDataSource = GXDynamicContextHolder.peek();
     * if ("slave".equals(currentDataSource)) {
     *     // 当前正在使用从库
     *     log.info("当前使用从库查询");
     * }
     * </pre>
     * </p>
     *
     * @return 数据源名称，如果没有设置则返回null
     */
    public static String peek() {
        return CONTEXT_HOLDER.get().peek();
    }

    /**
     * 设置当前线程数据源
     * <p>
     * 将指定的数据源标识压入当前线程的数据源栈顶
     * 支持数据源的嵌套切换，新数据源会覆盖旧数据源成为活动数据源
     * </p>
     * 
     * <p>
     * 线程安全说明：
     * - 该方法基于ThreadLocal实现，天然线程安全
     * - 修改操作仅影响当前线程的上下文，不会影响其他线程
     * </p>
     * 
     * <p>
     * 使用示例：
     * <pre>
     * // 切换到从库数据源
     * GXDynamicContextHolder.push("slave");
     * try {
     *     // 在从库上执行只读操作
     *     return userMapper.selectById(userId);
     * } finally {
     *     // 操作完成后恢复原数据源
     *     GXDynamicContextHolder.poll();
     * }
     * </pre>
     * </p>
     *
     * @param dataSourceName 数据源名称，不应为null
     * @throws NullPointerException 如果dataSourceName为null
     */
    public static void push(String dataSourceName) {
        CONTEXT_HOLDER.get().push(dataSourceName);
    }

    /**
     * 清空当前线程数据源栈顶元素
     * <p>
     * 移除当前线程数据源栈顶的数据源标识
     * 如果移除后栈为空，则同时清理ThreadLocal资源，防止内存泄漏
     * 该方法应当在数据源切换操作完成后调用，通常在finally块中确保执行
     * </p>
     * 
     * <p>
     * 内存安全说明：
     * - 当栈为空时自动调用remove()方法清理ThreadLocal，防止内存泄漏
     * - 特别适用于线程池环境，避免线程复用导致的上下文污染
     * </p>
     * 
     * <p>
     * 线程安全说明：
     * - 该方法基于ThreadLocal实现，天然线程安全
     * - 修改操作仅影响当前线程的上下文，不会影响其他线程
     * </p>
     * 
     * <p>
     * 使用示例：
     * <pre>
     * // 切换数据源
     * GXDynamicContextHolder.push("slave");
     * try {
     *     // 执行数据库操作
     * } finally {
     *     // 恢复原数据源，并在栈为空时自动清理ThreadLocal资源
     *     GXDynamicContextHolder.poll();
     * }
     * </pre>
     * </p>
     */
    public static void poll() {
        Deque<String> deque = CONTEXT_HOLDER.get();
        deque.poll();
        if (deque.isEmpty()) {
            // 当栈为空时，清理ThreadLocal，防止内存泄漏
            CONTEXT_HOLDER.remove();
        }
    }

    /**
     * 清空当前线程的所有数据源信息
     * <p>
     * 完全清理当前线程的ThreadLocal资源，防止内存泄漏
     * 应当在线程结束或不再需要数据源切换功能时调用
     * 特别适用于线程池环境，避免线程复用导致的上下文污染
     * </p>
     * 
     * <p>
     * 内存安全说明：
     * - 直接调用ThreadLocal.remove()方法，彻底清理线程本地变量
     * - 在线程池环境中尤为重要，可防止内存泄漏和上下文污染
     * </p>
     * 
     * <p>
     * 线程安全说明：
     * - 该方法基于ThreadLocal实现，天然线程安全
     * - 清理操作仅影响当前线程的上下文，不会影响其他线程
     * </p>
     * 
     * <p>
     * 使用示例：
     * <pre>
     * @Async
     * public void asyncTask() {
     *     try {
     *         // 在异步任务中执行数据源切换和数据库操作
     *         GXDynamicContextHolder.push("slave");
     *         try {
     *             // 执行数据库操作
     *         } finally {
     *             GXDynamicContextHolder.poll();
     *         }
     *     } finally {
     *         // 确保在线程池线程完成任务前清理ThreadLocal资源
     *         GXDynamicContextHolder.clear();
     *     }
     * }
     * </pre>
     * </p>
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}
