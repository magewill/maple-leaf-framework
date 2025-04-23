package cn.maple.extension.register;

import cn.maple.extension.GXBizScenario;
import cn.maple.extension.GXExtensionCoordinate;
import cn.maple.extension.GXExtensionExecutor;
import cn.maple.extension.GXExtensionPoint;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 抽象组件执行器
 * <p>
 * 该类是扩展点执行框架的核心抽象类，提供了执行扩展点的通用方法。
 * 通过函数式编程的方式，支持有返回值和无返回值的扩展点执行。
 * <p>
 * 线程安全性：该类本身不存储状态，主要依赖子类实现的locateComponent方法的线程安全性。
 * <p>
 * 使用示例：
 * <pre>
 * // 假设有一个订单处理扩展点
 * public interface OrderProcessExtPoint extends GXExtensionPoint {
 *     OrderResult process(Order order);
 *     void notify(Order order);
 * }
 * 
 * // 在业务代码中使用
 * @Component
 * public class OrderService {
 *     @Resource
 *     private GXExtensionExecutor extensionExecutor;
 *     
 *     public OrderResult processOrder(Order order, String bizId) {
 *         // 执行有返回值的扩展点方法
 *         GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
 *         return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
 *                 extension -> extension.process(order));
 *     }
 *     
 *     public void notifyOrderStatus(Order order, String bizId) {
 *         // 执行无返回值的扩展点方法
 *         GXBizScenario scenario = GXBizScenario.valueOf(bizId, "notify", "normal");
 *         extensionExecutor.executeVoid(OrderProcessExtPoint.class, scenario, 
 *                 extension -> extension.notify(order));
 *     }
 * }
 * </pre>
 *
 * @author britton
 * @see GXExtensionExecutor 扩展执行器实现类
 * @see GXExtensionPoint 扩展点接口
 * @see GXBizScenario 业务场景
 */
public abstract class GXAbstractComponentExecutor {
    /**
     * 执行扩展点方法并返回结果
     * <p>
     * 该方法首先通过locateComponent方法定位到对应的扩展点实现，然后执行传入的函数。
     * 适用于有返回值的扩展点方法。
     *
     * @param targetClz   扩展点接口类型，不能为null
     * @param bizScenario 业务场景，不能为null
     * @param exeFunction 执行函数，定义如何调用扩展点方法，不能为null
     * @param <R>         返回值类型
     * @param <T>         扩展点接口类型
     * @return 扩展点方法的执行结果
     * @throws NullPointerException 如果任何参数为null
     * @throws cn.maple.extension.exception.GXExtensionException 如果找不到对应的扩展点实现
     */
    public <R, T> R execute(Class<T> targetClz, GXBizScenario bizScenario, Function<T, R> exeFunction) {
        if (targetClz == null || bizScenario == null || exeFunction == null) {
            throw new NullPointerException("targetClz, bizScenario and exeFunction cannot be null");
        }
        T component = locateComponent(targetClz, bizScenario);
        return exeFunction.apply(component);
    }

    /**
     * 执行扩展点方法并返回结果（使用扩展坐标）
     * <p>
     * 该方法是{@link #execute(Class, GXBizScenario, Function)}的重载版本，
     * 接受{@link GXExtensionCoordinate}参数来定位扩展点实现。
     *
     * @param extensionCoordinate 扩展坐标，包含扩展点类型和业务场景，不能为null
     * @param exeFunction         执行函数，定义如何调用扩展点方法，不能为null
     * @param <R>                 返回值类型
     * @param <T>                 扩展点接口类型
     * @return 扩展点方法的执行结果
     * @throws NullPointerException 如果任何参数为null
     * @throws cn.maple.extension.exception.GXExtensionException 如果找不到对应的扩展点实现
     */
    public <R, T> R execute(GXExtensionCoordinate extensionCoordinate, Function<T, R> exeFunction) {
        if (extensionCoordinate == null || exeFunction == null) {
            throw new NullPointerException("extensionCoordinate and exeFunction cannot be null");
        }
        return execute(extensionCoordinate.getExtensionPointClass(), extensionCoordinate.getBizScenario(), exeFunction);
    }

    /**
     * 执行扩展点方法，无返回值
     * <p>
     * 该方法首先通过locateComponent方法定位到对应的扩展点实现，然后执行传入的函数。
     * 适用于无返回值的扩展点方法。
     *
     * @param targetClz   扩展点接口类型，不能为null
     * @param context     业务场景，不能为null
     * @param exeFunction 执行函数，定义如何调用扩展点方法，不能为null
     * @param <T>         扩展点接口类型
     * @throws NullPointerException 如果任何参数为null
     * @throws cn.maple.extension.exception.GXExtensionException 如果找不到对应的扩展点实现
     */
    public <T> void executeVoid(Class<T> targetClz, GXBizScenario context, Consumer<T> exeFunction) {
        if (targetClz == null || context == null || exeFunction == null) {
            throw new NullPointerException("targetClz, context and exeFunction cannot be null");
        }
        T component = locateComponent(targetClz, context);
        exeFunction.accept(component);
    }

    /**
     * 执行扩展点方法，无返回值（使用扩展坐标）
     * <p>
     * 该方法是{@link #executeVoid(Class, GXBizScenario, Consumer)}的重载版本，
     * 接受{@link GXExtensionCoordinate}参数来定位扩展点实现。
     *
     * @param extensionCoordinate 扩展坐标，包含扩展点类型和业务场景，不能为null
     * @param exeFunction         执行函数，定义如何调用扩展点方法，不能为null
     * @param <T>                 扩展点接口类型
     * @throws NullPointerException 如果任何参数为null
     * @throws cn.maple.extension.exception.GXExtensionException 如果找不到对应的扩展点实现
     */
    public <T> void executeVoid(GXExtensionCoordinate extensionCoordinate, Consumer<T> exeFunction) {
        if (extensionCoordinate == null || exeFunction == null) {
            throw new NullPointerException("extensionCoordinate and exeFunction cannot be null");
        }
        executeVoid(extensionCoordinate.getExtensionPointClass(), extensionCoordinate.getBizScenario(), exeFunction);
    }

    /**
     * 定位组件（扩展点实现）
     * <p>
     * 该方法由子类实现，用于根据扩展点接口类型和业务场景定位到对应的扩展点实现。
     *
     * @param targetClz 扩展点接口类型
     * @param context   业务场景
     * @param <C>       扩展点接口类型
     * @return 扩展点实现实例
     * @throws cn.maple.extension.exception.GXExtensionException 如果找不到对应的扩展点实现
     */
    protected abstract <C> C locateComponent(Class<C> targetClz, GXBizScenario context);
}
