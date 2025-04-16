package cn.maple.core.datasource.config;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 多数据源线程上下文
 * <p>
 * 用于管理和存储当前线程的数据源标识，支持数据源的动态切换。
 * 内部使用ThreadLocal实现线程隔离，确保多线程环境下数据源切换的安全性。
 * 采用栈结构存储数据源标识，支持嵌套切换场景。
 */
public class GXDynamicContextHolder {
    /**
     * 线程本地变量，用于存储当前线程的数据源标识栈
     * <p>
     * 使用ThreadLocal确保线程安全，每个线程拥有独立的数据源标识栈
     * 初始值为空的ArrayDeque，避免空指针异常
     * 注意：使用完毕后应当及时清理ThreadLocal，防止内存泄漏
     */
    private static final ThreadLocal<Deque<String>> CONTEXT_HOLDER = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 私有构造函数，防止实例化
     * <p>
     * 该类仅提供静态方法，不需要创建实例
     */
    private GXDynamicContextHolder() {
        // 工具类不应被实例化
    }

    /**
     * 获得当前线程数据源
     * <p>
     * 返回当前线程栈顶的数据源标识，不改变栈的内容
     * 注意：当栈为空时会返回null，调用方需要处理空值情况
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
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}
