package cn.maple.core.framework.annotation;

import java.lang.annotation.*;

/**
 * 业务日志注解
 * <p>
 * 该注解用于记录业务操作日志，可以作用在控制器或其他业务类上，用于描述当前类的功能；
 * 也可以用于方法上，用于描述当前方法的作用。通过AOP或拦截器可以自动收集这些信息，
 * 用于系统审计、操作追踪和问题排查。
 * </p>
 * 
 * <p>
 * 使用场景：
 * - 记录用户关键操作（如登录、修改密码、支付等）
 * - 记录系统重要流程（如订单处理、审批流程等）
 * - 记录数据变更（如添加、修改、删除重要数据）
 * - 用于系统审计和合规要求
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在控制器类上使用，描述整个控制器的功能
 * @RestController
 * @RequestMapping("/api/user")
 * @GXBusinessLog(name = "用户管理", description = "用户信息管理模块")
 * public class UserController {
 *     
 *     // 2. 在方法上使用，描述具体操作
 *     @PostMapping("/create")
 *     @GXBusinessLog(name = "创建用户", description = "创建新用户并分配初始权限")
 *     public Result createUser(@RequestBody UserDTO userDTO) {
 *         // 业务逻辑
 *         return Result.success();
 *     }
 *     
 *     // 3. 在服务方法上使用
 *     @PutMapping("/update/{id}")
 *     @GXBusinessLog(name = "更新用户", description = "更新用户基本信息")
 *     public Result updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
 *         // 业务逻辑
 *         return Result.success();
 *     }
 * }
 * 
 * // 4. 在AOP中获取注解信息
 * @Aspect
 * @Component
 * public class BusinessLogAspect {
 *     
 *     @Pointcut("@annotation(cn.maple.core.framework.annotation.GXBusinessLog)")
 *     public void businessLogPointcut() {}
 *     
 *     @Around("businessLogPointcut()")
 *     public Object around(ProceedingJoinPoint point) throws Throwable {
 *         // 获取注解信息
 *         MethodSignature signature = (MethodSignature) point.getSignature();
 *         Method method = signature.getMethod();
 *         GXBusinessLog businessLog = method.getAnnotation(GXBusinessLog.class);
 *         
 *         // 记录操作日志
 *         String name = businessLog.name();
 *         String description = businessLog.description();
 *         // 记录日志逻辑...
 *         
 *         // 执行原方法
 *         return point.proceed();
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author 子曦
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface GXBusinessLog {
    /**
     * 功能名称
     * <p>
     * 简短描述当前操作的功能名称，如"用户登录"、"订单创建"等。
     * 建议使用简洁明了的词语，便于日志检索和统计。
     * </p>
     * 
     * @return 功能名称字符串
     */
    String name() default "";

    /**
     * 功能描述
     * <p>
     * 详细描述当前操作的具体内容，可以包含操作目的、影响范围等信息。
     * 当需要更详细地记录操作内容时使用，有助于问题排查和审计追踪。
     * </p>
     * 
     * @return 功能描述字符串
     */
    String description() default "";
}
