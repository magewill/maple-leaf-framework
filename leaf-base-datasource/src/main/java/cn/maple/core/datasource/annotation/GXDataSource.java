package cn.maple.core.datasource.annotation;

import java.lang.annotation.*;

/**
 * 多数据源注解
 * <p>
 * 该注解用于实现动态数据源切换功能，可以标记在类或方法上。
 * 当应用需要连接多个数据库时，通过此注解可以在运行时动态切换数据源，
 * 无需修改业务代码，提高了系统的灵活性和可扩展性。
 * </p>
 * 
 * <p>使用场景：</p>
 * <p>1. 读写分离：将查询操作路由到只读数据库，将写操作路由到主数据库</p>
 * <p>2. 多租户系统：不同租户的数据存储在不同的数据库中</p>
 * <p>3. 分库分表：按业务将数据分散到不同的数据库中</p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 在Repository类上使用，影响该类的所有方法
 * @GXDataSource("slave")
 * public class UserRepository extends GXMyBatisRepository<UserEntity> {
 *     // 所有方法都会使用slave数据源
 * }
 * 
 * // 在Service类上使用
 * @GXDataSource("master")
 * public class UserServiceImpl implements UserService {
 *     // 所有方法都会使用master数据源
 *     
 *     // 方法级注解会覆盖类级注解
 *     @GXDataSource("slave")
 *     public List<UserEntity> getUserList() {
 *         // 该方法使用slave数据源
 *         return userRepository.selectList(null);
 *     }
 * }
 * 
 * // 在具体方法上使用
 * public class OrderService {
 *     @GXDataSource("order_db")
 *     public void createOrder(OrderEntity order) {
 *         // 该方法使用order_db数据源
 *     }
 * }
 * </pre>
 * 
 * <p>注意事项：</p>
 * <p>1. 如果调用的是项目内的功能，需要在XXXRepository上添加@GXDataSource("other")</p>
 * <p>2. 如果需要调用MyBatis Plus封装的功能，需要在XXXService上添加@GXDataSource("other")</p>
 * <p>3. 方法级注解的优先级高于类级注解</p>
 * <p>4. 确保配置文件中已定义相应的数据源</p>
 * 
 * @author britton <britton@126.com>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface GXDataSource {
    /**
     * 数据源名称
     * <p>
     * 指定要使用的数据源名称，该名称必须在配置文件中已定义
     * 如果为空，则使用默认数据源
     * </p>
     * 
     * @return 数据源名称
     */
    String value() default "";
}
