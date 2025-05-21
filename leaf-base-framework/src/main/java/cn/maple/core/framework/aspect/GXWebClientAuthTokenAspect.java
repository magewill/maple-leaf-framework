package cn.maple.core.framework.aspect;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXWebClientAuthTokenException;
import cn.maple.core.framework.service.GXWebClientService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebClient认证Token切面
 * <p>
 * 该切面负责拦截标记了@GXWebClientAuthToken注解的方法或类，并在方法执行前验证WebClient的认证Token是否有效。
 * 如果Token无效，将抛出GXWebClientAuthTokenException异常阻止方法执行，保障API的安全性。
 * </p>
 *
 * <p>
 * 工作流程：
 * 1. 定义切点，匹配标记了@GXWebClientAuthToken注解的方法或类
 * 2. 在方法执行前（@Before），获取GXWebClientService实例
 * 3. 调用GXWebClientService的checkTokenValidity方法验证Token
 * 4. 如果Token无效，记录错误日志并抛出异常
 * </p>
 *
 * <p>
 * 线程安全说明：
 * 1. 本切面不维护任何可变状态，是线程安全的
 * 2. 使用ConcurrentHashMap缓存方法签名信息，提高高并发场景下的性能
 * 3. 依赖的GXWebClientService实现类应确保其checkTokenValidity方法是线程安全的
 * </p>
 *
 * <p>
 * 性能优化：
 * 1. 使用缓存减少方法签名的重复构建
 * 2. 日志记录采用参数化方式，避免不必要的字符串拼接
 * 3. 异常信息包含详细的方法信息，便于问题定位
 * </p>
 *
 * @author britton gapleaf@63.com
 * @since 1.0.0
 */
@Aspect
@Component
@Slf4j
@Order(100) // 设置切面优先级，确保在事务等其他切面之前执行
public class GXWebClientAuthTokenAspect {

    /**
     * 方法签名缓存，用于提高高并发场景下的性能
     */
    private static final ConcurrentHashMap<String, String> METHOD_SIGNATURE_CACHE = new ConcurrentHashMap<>();

    /**
     * 定义切点，匹配标记了@GXWebClientAuthToken注解的方法或类
     * <p>
     * 切点表达式说明：
     * - @annotation：匹配标记了指定注解的方法
     * - @within：匹配标记了指定注解的类中的所有方法
     * </p>
     */
    @Pointcut("@annotation(cn.maple.core.framework.annotation.GXWebClientAuthToken) || " +
            "@within(cn.maple.core.framework.annotation.GXWebClientAuthToken) ")
    public void webClientAuthTokenPointCut() {
        // 这是切点标记，用于拦截需要进行验证WebClient调用token的方法
    }

    /**
     * 在目标方法执行前验证WebClient认证Token
     * <p>
     * 如果Token无效，将抛出GXWebClientAuthTokenException异常阻止方法执行
     * </p>
     *
     * @param point 连接点，包含被拦截方法的信息
     * @throws GXBusinessException           当未找到GXWebClientService实现类时抛出
     * @throws GXWebClientAuthTokenException 当Token验证失败时抛出
     */
    @Before("webClientAuthTokenPointCut()")
    public void before(JoinPoint point) {
        GXWebClientService webClientService = GXSpringContextUtils.getBean(GXWebClientService.class);
        if (Objects.isNull(webClientService)) {
            String errorMsg = CharSequenceUtil.format("请实现{}接口", GXWebClientService.class.getName());
            log.error(errorMsg);
            throw new GXBusinessException(errorMsg);
        }

        // 获取方法签名信息
        String methodName = getMethodSignature(point);
        log.debug("正在验证WebClient调用token，方法: {}", methodName);

        // 验证Token有效性
        if (!webClientService.checkTokenValidity()) {
            String errorMsg = CharSequenceUtil.format("WebClient调用token校验失败，方法: {}", methodName);
            log.error(errorMsg);
            throw new GXWebClientAuthTokenException(errorMsg);
        }

        log.debug("WebClient调用token校验成功，方法: {}", methodName);
    }

    /**
     * 获取方法签名信息
     * <p>
     * 使用缓存减少方法签名的重复构建，提高性能
     * </p>
     *
     * @param point 连接点
     * @return 格式化的方法签名字符串
     */
    private String getMethodSignature(JoinPoint point) {
        String signatureKey = point.getSignature().toString();
        return METHOD_SIGNATURE_CACHE.computeIfAbsent(signatureKey, key -> {
            MethodSignature signature = (MethodSignature) point.getSignature();
            return signature.getDeclaringTypeName() + "." + signature.getName();
        });
    }
}
