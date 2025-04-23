package cn.maple.core.framework.annotation;

import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.res.GXBaseResDto;

import java.lang.annotation.*;

/**
 * 缓存注解
 * <p>
 * 该注解用于在方法上标注，实现方法结果的缓存功能。
 * 被标注的方法在首次调用时会执行并将结果存入缓存，后续调用时直接从缓存获取结果，提高系统性能。
 * </p>
 * 
 * <p>
 * 主要功能：
 * - 支持自定义缓存键，灵活控制缓存粒度
 * - 支持缓存结果类型转换，适应不同的业务场景
 * - 支持自定义转换规则，满足复杂的数据处理需求
 * - 与Spring Cache兼容，可以利用Spring的缓存管理机制
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 基本使用方式
 * @Service
 * public class UserService {
 *     
 *     @GXCacheable(cacheKey = "user:info:{0}")
 *     public UserVO getUserById(Long userId) {
 *         // 从数据库查询用户信息
 *         return userMapper.selectById(userId);
 *     }
 * }
 * 
 * // 2. 指定返回类型转换
 * @Service
 * public class ProductService {
 *     
 *     @GXCacheable(cacheKey = "product:detail:{0}", retType = ProductDetailVO.class)
 *     public GXBaseResDto getProductDetail(Long productId) {
 *         // 查询商品详情
 *         ProductDetailVO detail = productMapper.selectDetailById(productId);
 *         return new GXBaseResDto().setData(detail);
 *     }
 * }
 * 
 * // 3. 自定义转换方法
 * @Service
 * public class OrderService {
 *     
 *     @GXCacheable(cacheKey = "order:list:user:{0}", retType = OrderListVO.class, methodName = "convertToOrderList")
 *     public GXBaseResDto getUserOrders(Long userId) {
 *         // 查询用户订单列表
 *         List<Order> orders = orderMapper.selectByUserId(userId);
 *         return new GXBaseResDto().setData(orders);
 *     }
 *     
 *     // 自定义转换方法
 *     public OrderListVO convertToOrderList(Object cacheData) {
 *         // 将缓存数据转换为OrderListVO
 *         // ...
 *         return orderListVO;
 *     }
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 注意事项：
 * - 缓存键支持SpEL表达式，可以引用方法参数
 * - 缓存的有效期和清除策略需要在缓存配置中设置
 * - 对于频繁变化的数据，应当谨慎使用缓存或设置较短的过期时间
 * - 建议与@GXCacheEvict配合使用，确保数据更新时及时清除缓存
 * </p>
 *
 * @author britton
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface GXCacheable {
    /**
     * 如果命中缓存 将缓存的类型转换为该类型
     * <p>
     * 当缓存命中时，系统会尝试将缓存中的数据转换为指定的类型。
     * 默认为GXBaseResDto类型，可以根据业务需求指定为其子类。
     * </p>
     *
     * @return 返回数据类型
     */
    Class<? extends GXBaseResDto> retType() default GXBaseResDto.class;

    /**
     * 转换到指定类型可以指定该值来进行自定义转换规则
     * <p>
     * 当需要自定义缓存数据的转换逻辑时，可以指定一个方法名。
     * 该方法应当存在于当前类中，接收Object类型参数，返回retType指定的类型。
     * </p>
     *
     * @return 转换方法名
     */
    String methodName() default GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;

    /**
     * 缓存key
     * <p>
     * 用于标识缓存的唯一键，支持SpEL表达式。
     * 表达式中可以使用{0}、{1}等占位符引用方法参数。
     * 例如："user:{0}:profile"，其中{0}会被替换为方法的第一个参数。
     * </p>
     *
     * @return 缓存键
     */
    String cacheKey() default "";
}
