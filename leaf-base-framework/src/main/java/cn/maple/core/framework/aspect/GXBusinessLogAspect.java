package cn.maple.core.framework.aspect;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.annotation.GXBusinessLog;
import cn.maple.core.framework.dto.inner.GXBusinessLogDto;
import cn.maple.core.framework.service.GXBusinessLogService;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 业务日志切面
 * <p>
 * 用于拦截标记了{@link GXBusinessLog}注解的方法，记录业务操作日志
 * 该切面是线程安全的，不会对被拦截方法的执行造成线程安全问题
 * </p>
 *
 * @author maple
 */
@Component
@Aspect
@Slf4j
public class GXBusinessLogAspect implements Ordered {
    /**
     * 定义业务日志切面的切入点为标记@GXBusinessLog注解的方法
     * 只有被@GXBusinessLog注解标记的方法才会被此切面拦截
     */
    @Pointcut(value = "@annotation(cn.maple.core.framework.annotation.GXBusinessLog)")
    public void pointcut() {
        // 切点定义，无需实现
    }

    /**
     * 业务操作环绕通知
     * <p>
     * 在目标方法执行前后进行日志记录处理
     * 该方法是线程安全的，每个请求都会创建独立的方法栈和局部变量
     * </p>
     *
     * @param proceedingJoinPoint 切点对象，包含目标方法的信息和参数
     * @return Object 目标方法执行结果
     */
    @Around("pointcut()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) {
        long beginAt = System.currentTimeMillis();
        log.info("----GXBusinessLogAspect 环绕通知 START----");
        // 使用AtomicReference保证在多线程环境下的线程安全
        AtomicReference<Object> resultRef = new AtomicReference<>();
        try {
            // 执行目标方法并保存结果
            Object result = proceedingJoinPoint.proceed();
            resultRef.set(result);
        } catch (Throwable throwable) {
            // 记录详细的异常信息，包括堆栈跟踪
            log.error("业务方法执行异常: {}", throwable.getMessage(), throwable);
            // 不吞噬异常，将异常继续向上抛出，由上层处理
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            } else {
                throw new RuntimeException("业务方法执行异常", throwable);
            }
        } finally {
            // 无论方法是否成功执行，都记录业务日志
            // 执行时长(毫秒)
            long executionTime = System.currentTimeMillis() - beginAt;
            try {
                saveBusinessLog(proceedingJoinPoint, executionTime);
            } catch (Exception e) {
                // 日志记录异常不应影响主业务流程
                log.error("保存业务日志时发生异常: {}", e.getMessage(), e);
            }
            log.info("----GXBusinessLogAspect 环绕通知 END----");
        }
        return resultRef.get();
    }

    /**
     * 保存业务日志
     * <p>
     * 从切点中提取业务日志所需的信息并保存
     * 该方法是线程安全的，每个请求的日志信息相互独立
     * </p>
     *
     * @param proceedingJoinPoint 切点对象
     * @param executionTime 方法执行时间(毫秒)
     */
    private void saveBusinessLog(ProceedingJoinPoint proceedingJoinPoint, long executionTime) {
        try {
            // 目标方法执行完成后，获取目标类、目标方法上的业务日志注解上的功能名称和功能描述
            Object target = proceedingJoinPoint.getTarget();
            MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
            GXBusinessLog clazzAnnotation = target.getClass().getAnnotation(GXBusinessLog.class);
            GXBusinessLog methodAnnotation = signature.getMethod().getAnnotation(GXBusinessLog.class);
            
            // 创建业务日志数据传输对象
            GXBusinessLogDto businessLogDto = new GXBusinessLogDto();
            
            // 获取日志的详细描述信息，优先使用方法上的注解，如果为空则使用类上的注解
            String name = methodAnnotation.name();
            if (CharSequenceUtil.isBlank(name) && Objects.nonNull(clazzAnnotation)) {
                name = clazzAnnotation.name();
            }
            String description = methodAnnotation.description();
            businessLogDto.setBusinessName(name);
            businessLogDto.setBusinessDescription(description);
            
            // 设置请求的方法名（类名.方法名）
            String className = target.getClass().getName();
            String methodName = signature.getName();
            businessLogDto.setMethodName(className + "." + methodName + "()");
            
            // 序列化请求参数，注意可能包含敏感信息，实际应用中应考虑脱敏处理
            Object[] args = proceedingJoinPoint.getArgs();
            String params = JSONUtil.toJsonStr(args);
            businessLogDto.setParams(params);
            
            // 设置客户端IP地址
            businessLogDto.setIp(GXCurrentRequestContextUtils.getClientIP());
            
            // 设置方法执行时间(毫秒)
            businessLogDto.setExecutionTime(executionTime);
            
            // 设置请求时间(秒)
            businessLogDto.setRequestAt(DateUtil.currentSeconds());

            // 从Spring上下文中获取业务日志服务
            GXBusinessLogService businessLogService = GXSpringContextUtils.getBean(GXBusinessLogService.class);
            if (Objects.nonNull(businessLogService)) {
                // 获取并设置当前用户名
                String username = businessLogService.getUserName();
                businessLogDto.setUserName(username);
                
                // 调用业务日志服务保存日志数据
                businessLogService.saveBusinessLog(businessLogDto);
                log.debug("业务日志已保存: {}", businessLogDto.getBusinessName());
            } else {
                log.warn("未找到GXBusinessLogService的实现类，业务日志无法保存");
            }
        } catch (Exception e) {
            // 捕获日志保存过程中的异常，防止影响主业务流程
            log.error("保存业务日志时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取切面的执行顺序
     * <p>
     * 返回最低优先级，确保该切面在其他切面之后执行
     * 这样可以记录完整的方法执行信息，包括其他切面可能造成的影响
     * </p>
     *
     * @return 切面顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
