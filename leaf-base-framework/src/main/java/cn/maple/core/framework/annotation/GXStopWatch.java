package cn.maple.core.framework.annotation;

import java.lang.annotation.*;

/**
 * 方法执行时间监控注解
 * <p>
 * 该注解用于测量标注方法的执行时间，可用于性能分析和监控。
 * 通过AOP机制自动记录方法的开始和结束时间，计算执行耗时，
 * 有助于发现系统中的性能瓶颈和优化点。
 * </p>
 * 
 * <p>
 * 使用场景：
 * - 监控关键业务方法的执行性能
 * - 分析系统瓶颈和耗时较长的操作
 * - 在开发和测试阶段进行性能评估
 * - 生产环境中进行性能监控和告警
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在方法上使用
 * @Service
 * public class UserService {
 *     
 *     @GXStopWatch
 *     public List<UserVO> findAllUsers() {
 *         // 业务逻辑
 *         return userList;
 *     }
 * }
 * 
 * // 2. 在类上使用，监控类中所有方法
 * @Service
 * @GXStopWatch
 * public class OrderService {
 *     
 *     public void createOrder(OrderDTO orderDTO) {
 *         // 业务逻辑
 *     }
 *     
 *     public OrderVO getOrderDetail(Long orderId) {
 *         // 业务逻辑
 *         return orderVO;
 *     }
 * }
 * 
 * // 3. 在AOP中实现注解功能
 * @Aspect
 * @Component
 * public class StopWatchAspect {
 *     
 *     private static final Logger logger = LoggerFactory.getLogger(StopWatchAspect.class);
 *     
 *     @Pointcut("@annotation(cn.maple.core.framework.annotation.GXStopWatch) || @within(cn.maple.core.framework.annotation.GXStopWatch)")
 *     public void stopWatchPointcut() {}
 *     
 *     @Around("stopWatchPointcut()")
 *     public Object around(ProceedingJoinPoint point) throws Throwable {
 *         String methodName = point.getSignature().getName();
 *         String className = point.getTarget().getClass().getSimpleName();
 *         
 *         // 创建计时器
 *         StopWatch stopWatch = new StopWatch();
 *         stopWatch.start();
 *         
 *         try {
 *             // 执行原方法
 *             return point.proceed();
 *         } finally {
 *             // 停止计时并记录日志
 *             stopWatch.stop();
 *             logger.info("方法执行时间统计 - {}.{}: {} ms", 
 *                     className, methodName, stopWatch.getTotalTimeMillis());
 *         }
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author britton@126.com
 * @since 2021-10-19 15:20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXStopWatch {
}
