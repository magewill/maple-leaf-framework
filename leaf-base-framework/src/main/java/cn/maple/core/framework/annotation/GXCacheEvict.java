package cn.maple.core.framework.annotation;

import java.lang.annotation.*;

/**
 * 缓存清除注解
 * <p>
 * 该注解用于在方法上标注，实现方法执行后清除指定缓存的功能。
 * 通常用于数据更新操作后，清除相关缓存，确保数据一致性。
 * </p>
 * 
 * <p>
 * 主要功能：
 * - 支持自定义缓存键，精确控制需要清除的缓存
 * - 与@GXCacheable配合使用，实现完整的缓存管理
 * - 支持SpEL表达式，可以动态构建缓存键
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
 *     // 查询方法使用缓存
 *     @GXCacheable(cacheKey = "user:info:{0}")
 *     public UserVO getUserById(Long userId) {
 *         // 从数据库查询用户信息
 *         return userMapper.selectById(userId);
 *     }
 *     
 *     // 更新方法清除缓存
 *     @GXCacheEvict(cacheKey = "user:info:{0}")
 *     public void updateUser(Long userId, UserUpdateDTO updateDTO) {
 *         // 更新用户信息
 *         userMapper.updateById(userId, updateDTO);
 *     }
 * }
 * 
 * // 2. 清除多个相关缓存
 * @Service
 * public class ProductService {
 *     
 *     // 更新商品信息并清除相关缓存
 *     @GXCacheEvict(cacheKey = "product:detail:{0}")
 *     @Transactional
 *     public void updateProduct(Long productId, ProductUpdateDTO updateDTO) {
 *         // 更新商品信息
 *         productMapper.updateById(productId, updateDTO);
 *         
 *         // 可能还需要手动清除其他相关缓存
 *         String listCacheKey = "product:list:category:" + updateDTO.getCategoryId();
 *         cacheManager.evict(listCacheKey);
 *     }
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 注意事项：
 * - 缓存键应与@GXCacheable中的缓存键保持一致，确保正确清除
 * - 对于批量操作，可能需要手动清除多个缓存
 * - 在事务方法中使用时，应确保缓存清除在事务提交后执行
 * - 缓存清除操作应当尽可能精确，避免不必要的缓存失效
 * </p>
 *
 * @author britton
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface GXCacheEvict {
    /**
     * 缓存key
     * <p>
     * 用于标识需要清除的缓存的唯一键，支持SpEL表达式。
     * 表达式中可以使用{0}、{1}等占位符引用方法参数。
     * 例如："user:{0}:profile"，其中{0}会被替换为方法的第一个参数。
     * </p>
     *
     * @return 缓存键
     */
    String cacheKey() default "";
}
