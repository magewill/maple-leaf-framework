package cn.maple.webclient.aspect;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.annotation.GXHttpInvokerAuthToken;
import cn.maple.core.framework.constant.GXHttpInvokerConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXWebClientAuthTokenException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.webclient.service.GXWebClientService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
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
 * 使用场景：
 * 1. 微服务间的安全调用，确保调用方持有有效的认证Token
 * 2. 内部API的访问控制，限制只有持有有效Token的客户端才能访问
 * 3. 第三方系统集成时的认证机制，验证第三方系统的调用权限
 * 4. 保护敏感操作或数据，增加额外的安全验证层
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 首先实现GXWebClientService接口
 * @Service
 * public class CustomWebClientService implements GXWebClientService {
 *     // 使用线程安全的缓存存储Token
 *     private final LoadingCache<String, String> tokenCache;
 * <p>
 *     public CustomWebClientService() {
 *         // 创建带有过期时间的缓存
 *         this.tokenCache = Caffeine.newBuilder()
 *             .expireAfterWrite(3600, TimeUnit.SECONDS) // Token有效期1小时
 *             .build(this::generateFreshToken); // 当缓存未命中时自动生成新Token
 *     }
 *
 *     @Override
 *     public String generateHttpAuthToken() {
 *         // 从缓存中获取Token，如果不存在或已过期，会自动调用generateFreshToken生成新Token
 *         return tokenCache.get("webClientToken");
 *     }
 *
 *     @Override
 *     public boolean checkTokenValidity() {
 *         // 实现Token验证逻辑
 *         String token = GXCurrentRequestContextUtils.getHeader("X-Auth-Token");
 *         if (CharSequenceUtil.isBlank(token)) {
 *             return false;
 *         }
 * <p>
 *         try {
 *             // 验证Token的有效性，例如解析JWT Token
 *             // ...
 *             return true;
 *         } catch (Exception e) {
 *             return false;
 *         }
 *     }
 * <p>
 *     private String generateFreshToken(String key) {
 *         // 实际的Token生成逻辑
 *         // ...
 *         return "generated-token";
 *     }
 * }
 * <p>
 * // 2. 在类级别应用@GXWebClientAuthToken注解，类中所有方法都会进行Token验证
 * @GXWebClientAuthToken
 * @RestController
 * @RequestMapping("/api/v1/products")
 * public class ProductController {
 *     // 所有方法都会进行Token验证
 *     @GetMapping("/{id}")
 *     public Product getProduct(@PathVariable Long id) {
 *         // 方法实现...
 *     }
 * }
 * <p>
 * // 3. 在方法级别应用@GXWebClientAuthToken注解，只对特定方法进行Token验证
 * @RestController
 * @RequestMapping("/api/v1/users")
 * public class UserController {
 *     // 此方法会进行Token验证
 *     @GXWebClientAuthToken
 *     @GetMapping("/sensitive-data")
 *     public SensitiveData getSensitiveData() {
 *         // 方法实现...
 *     }
 * <p>
 *     // 此方法不会进行Token验证
 *     @GetMapping("/public-data")
 *     public PublicData getPublicData() {
 *         // 方法实现...
 *     }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 安全性考虑：
 * 1. Token生成应使用安全的算法和足够长度的密钥，推荐使用JWT或类似标准
 * 2. Token应包含过期时间，避免永久有效导致的安全风险
 * 3. 敏感操作应考虑使用更短的Token有效期或额外的验证机制
 * 4. 生产环境中应配置HTTPS，确保Token传输过程的安全性
 * 5. 考虑实现Token黑名单机制，支持在必要时撤销特定Token
 * 6. 日志中应避免记录完整的Token值，防止敏感信息泄露
 * 7. 定期轮换Token密钥，提高系统安全性
 * </p>
 *
 * <p>
 * 线程安全说明：
 * 1. 本切面不维护任何可变状态，是线程安全的
 * 2. 使用ConcurrentHashMap缓存方法签名信息，提高高并发场景下的性能
 * 3. 依赖的GXWebClientService实现类应确保其checkTokenValidity方法是线程安全的
 * 4. 在高并发环境下，应避免在checkTokenValidity方法中使用同步块，可能导致性能瓶颈
 * 5. 如需在GXWebClientService实现类中缓存Token信息，应使用线程安全的缓存实现，如ConcurrentHashMap或Caffeine
 * </p>
 *
 * <p>
 * 性能优化：
 * 1. 使用缓存减少方法签名的重复构建
 * 2. 日志记录采用参数化方式，避免不必要的字符串拼接
 * 3. 异常信息包含详细的方法信息，便于问题定位
 * 4. Token验证逻辑应尽可能高效，避免复杂的计算或远程调用
 * 5. 考虑使用本地缓存存储已验证的Token，减少重复验证开销（注意设置合理的缓存过期时间）
 * 6. 在高并发场景下，可考虑使用令牌桶算法限制Token验证频率，防止DoS攻击
 * 7. 对于非关键路径，可考虑异步验证Token，提高响应速度
 * </p>
 *
 * <p>
 * 扩展建议：
 * 1. 可扩展@GXWebClientAuthToken注解，支持配置不同的验证策略或安全级别
 * 2. 考虑支持多种Token验证方式，如JWT、OAuth2等
 * 3. 可集成Spring Security，实现更完善的安全控制
 * 4. 添加Token使用统计和监控功能，及时发现异常访问模式
 * 5. 实现分布式Token验证，支持集群环境下的一致性验证
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
     * <p>
     * 使用ConcurrentHashMap确保线程安全，同时提供高并发读取性能。
     * 缓存的键为方法签名的字符串表示，值为格式化后的方法名称（类名.方法名）。
     * </p>
     * <p>
     * 注意：该缓存没有大小限制和过期策略，适用于有限数量的方法。
     * 如果应用中有大量动态生成的类和方法，应考虑添加缓存大小限制或使用LRU策略。
     * </p>
     */
    private static final ConcurrentHashMap<String, String> METHOD_SIGNATURE_CACHE = new ConcurrentHashMap<>();

    /**
     * 定义切点，匹配标记了@GXWebClientAuthToken注解的方法或类
     * <p>
     * 切点表达式说明：
     * - @annotation：匹配标记了指定注解的方法
     * - @within：匹配标记了指定注解的类中的所有方法
     * </p>
     * <p>
     * 该切点将捕获以下两种情况：
     * 1. 直接在方法上标记了@GXWebClientAuthToken注解的方法
     * 2. 在类上标记了@GXWebClientAuthToken注解的类中的所有方法
     * </p>
     * <p>
     * 性能说明：
     * - 切点表达式的解析在应用启动时完成，不影响运行时性能
     * - 切点匹配操作在方法调用时执行，但开销很小
     * </p>
     */
    @Pointcut("@annotation(cn.maple.core.framework.annotation.GXHttpInvokerAuthToken) || " +
            "@within(cn.maple.core.framework.annotation.GXHttpInvokerAuthToken) ")
    public void webClientAuthTokenPointCut() {
        // 这是切点标记，用于拦截需要进行验证WebClient调用token的方法
    }

    /**
     * 在目标方法执行前验证WebClient认证Token
     * <p>
     * 该方法在匹配切点的方法执行前被调用，负责验证WebClient的认证Token是否有效。
     * 验证流程：
     * 1. 从Spring容器中获取GXWebClientService实例
     * 2. 获取当前方法的签名信息，用于日志记录和异常信息
     * 3. 调用GXWebClientService.checkTokenValidity()方法验证Token
     * 4. 如果Token无效，记录错误日志并抛出GXWebClientAuthTokenException异常
     * </p>
     * <p>
     * 异常处理：
     * - 如果未找到GXWebClientService实现类，抛出GXBusinessException异常
     * - 如果Token验证失败，抛出GXWebClientAuthTokenException异常
     * </p>
     * <p>
     * 日志记录：
     * - 验证开始时记录debug级别日志
     * - 验证失败时记录error级别日志
     * - 验证成功时记录debug级别日志
     * </p>
     * <p>
     * 性能优化：
     * - 使用缓存减少方法签名的重复构建
     * - 日志级别判断，避免不必要的字符串拼接
     * </p>
     *
     * @param point 连接点，包含被拦截方法的信息
     * @throws GXBusinessException           当未找到GXWebClientService实现类时抛出
     * @throws GXWebClientAuthTokenException 当Token验证失败时抛出
     */
    @Before("webClientAuthTokenPointCut()")
    public void before(JoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        GXHttpInvokerAuthToken httpInvokerAuthToken = findAuthTokenAnnotation(point, method);
        if (Objects.isNull(httpInvokerAuthToken)) {
            return;
        }
        String value = httpInvokerAuthToken.value();
        if (!CharSequenceUtil.equalsIgnoreCase(value, GXHttpInvokerConstant.WEB_CLIENT_INVOKER)) {
            return;
        }
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
     * 该方法从连接点获取方法签名信息，并使用缓存减少重复构建的开销。
     * 方法签名格式为：类全限定名.方法名
     * </p>
     * <p>
     * 缓存策略：
     * - 使用ConcurrentHashMap存储已解析的方法签名
     * - 使用computeIfAbsent方法确保线程安全的懒加载
     * - 缓存键为JoinPoint.getSignature().toString()的结果
     * - 缓存值为格式化后的方法名称（类名.方法名）
     * </p>
     * <p>
     * 性能说明：
     * - 首次调用某方法时会解析签名并存入缓存
     * - 后续调用相同方法时直接从缓存获取，避免重复解析
     * - ConcurrentHashMap的get操作非常高效，几乎不影响性能
     * </p>
     *
     * @param point 连接点
     * @return 格式化的方法签名字符串，格式为：类全限定名.方法名
     */
    private GXHttpInvokerAuthToken findAuthTokenAnnotation(JoinPoint point, Method method) {
        GXHttpInvokerAuthToken annotation = AnnotationUtils.findAnnotation(method, GXHttpInvokerAuthToken.class);
        if (Objects.nonNull(annotation)) {
            return annotation;
        }

        Class<?> targetClass = Objects.nonNull(point.getTarget())
                ? AopUtils.getTargetClass(point.getTarget())
                : method.getDeclaringClass();
        annotation = AnnotationUtils.findAnnotation(targetClass, GXHttpInvokerAuthToken.class);
        if (Objects.nonNull(annotation)) {
            return annotation;
        }

        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), GXHttpInvokerAuthToken.class);
    }

    private String getMethodSignature(JoinPoint point) {
        String signatureKey = point.getSignature().toString();
        return METHOD_SIGNATURE_CACHE.computeIfAbsent(signatureKey, key -> {
            MethodSignature signature = (MethodSignature) point.getSignature();
            return signature.getDeclaringTypeName() + "." + signature.getName();
        });
    }
}
