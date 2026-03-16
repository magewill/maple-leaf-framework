package cn.maple.core.framework.aspect;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBeanValidateException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * 请求参数验证切面
 * <p>
 * 用于拦截标记了@GetMapping注解的方法，对请求参数进行自动验证。
 * 该切面是线程安全的，使用了线程安全的验证器实例。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * 
 * // 1. 在Controller方法上添加@GetMapping注解
 * @GetMapping("/user/{id}")
 * public ResponseEntity<UserDto> getUser(
 *     @PathVariable("id") @NotNull Long id,
 *     @RequestParam(required = false) @Size(min = 3, max = 50) String name
 * ) {
 *     // 方法实现...
 *     return ResponseEntity.ok(userDto);
 * }
 * 
 * // 2. 参数验证失败时会抛出GXBeanValidateException异常
 * // 3. 可以通过全局异常处理器统一处理验证异常
 * </pre>
 * </p>
 * <p>
 * 优点：
 * <ul>
 *   <li>自动验证所有标记了@GetMapping的方法参数，无需手动调用验证逻辑</li>
 *   <li>统一的验证异常处理，提供友好的错误信息</li>
 *   <li>线程安全设计，适用于高并发环境</li>
 * </ul>
 * </p>
 *
 * @author maple
 */
@Aspect
@Component
@Slf4j
public class GXValidateRequestParamAspect implements Ordered {
    /**
     * 参数验证器实例
     * 使用static final修饰，确保线程安全且只初始化一次
     * Validator实现是线程安全的，可以在多个线程间共享
     */
    private static final Validator validator;

    static {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /**
     * 定义请求参数验证切点
     * <p>
     * 拦截标记了@GetMapping注解的方法，实现对GET请求参数的自动验证
     * 可以根据需要扩展到其他类型的请求方法，如POST、PUT等
     * </p>
     */
    @Pointcut("@annotation(org.springframework.web.bind.annotation.GetMapping)")
    public void requestParamValidate() {
        // 切点定义，无需实现
    }

    /**
     * 请求参数验证环绕通知
     * <p>
     * 在目标方法执行前验证请求参数是否符合约束条件。
     * 该方法是线程安全的，每个请求都有独立的执行上下文。
     * 验证失败时会抛出GXBeanValidateException异常，包含详细的验证错误信息。
     * </p>
     * <p>
     * 验证流程：
     * 1. 获取目标方法的签名和参数信息
     * 2. 从Spring容器中获取目标类的实例
     * 3. 使用验证器对方法参数进行验证
     * 4. 如果验证失败，收集错误信息并抛出异常
     * 5. 验证通过则执行目标方法并返回结果
     * </p>
     *
     * @param point 切点对象，包含目标方法的信息和参数
     * @return 目标方法的执行结果
     * @throws Throwable 如果参数验证失败或目标方法执行异常
     */
    @Around("requestParamValidate()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        try {
            // 获取方法签名和相关信息
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();

            // 获取目标类的实例
            Object targetBean = GXSpringContextUtils.getBean(method.getDeclaringClass());
            if (targetBean == null) {
                log.error("无法获取目标类的Bean实例: {}", method.getDeclaringClass().getName());
                return point.proceed(point.getArgs());
            }

            // 验证方法参数
            log.debug("验证请求参数: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
            Set<? extends ConstraintViolation<Object>> constraintViolations =
                    validator.forExecutables().validateParameters(targetBean, method, point.getArgs());

            // 如果存在验证错误，收集错误信息并抛出异常
            if (!constraintViolations.isEmpty()) {
                final Dict dict = Dict.create();
                for (ConstraintViolation<Object> constraint : constraintViolations) {
                    final String currentFormName = constraint.getPropertyPath().toString();
                    dict.set(currentFormName, constraint.getMessage());
                    log.debug("参数验证失败: {} - {}", currentFormName, constraint.getMessage());
                }
                throw new GXBeanValidateException("请求参数验证失败", HttpStatus.HTTP_INTERNAL_ERROR, dict);
            }

            // 验证通过，执行目标方法
            return point.proceed(point.getArgs());
        } catch (GXBeanValidateException e) {
            // 直接抛出验证异常
            throw e;
        } catch (Throwable e) {
            // 记录其他异常并重新抛出
            //log.error("参数验证过程中发生异常: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 获取切面的执行顺序
     * <p>
     * 返回最高优先级，确保该切面在其他切面之前执行
     * 这样可以在业务逻辑执行前先验证参数的有效性
     * </p>
     *
     * @return 切面顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100; // 确保在事务切面之前执行
    }
}
