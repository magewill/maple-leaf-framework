package cn.maple.core.datasource.annotation;

import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.service.GXMybatisListenerService;

import java.lang.annotation.*;

/**
 * MyBatis操作监听器注解
 * <p>
 * 该注解用于在MyBatis操作（如增删改查）执行时触发自定义的监听逻辑。
 * 可以应用于方法或类上，支持同步和异步两种执行方式。
 * 通过指定监听器类和运行类型，可以灵活地实现各种数据操作的监听和处理。
 * </p>
 * 
 * <p>
 * 使用示例1：在Mapper方法上添加同步监听器
 * <pre>
 * public interface UserMapper {
 *     @GXMyBatisListener(listenerClazz = UserSaveListener.class)
 *     void insert(User user);
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 使用示例2：在Service类上添加异步监听器
 * <pre>
 * @Service
 * @GXMyBatisListener(
 *     listenerClazz = UserChangeListener.class,
 *     runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT
 * )
 * public class UserServiceImpl implements UserService {
 *     // 该类中的所有数据操作都会触发UserChangeListener
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 监听器实现示例：
 * <pre>
 * @Component
 * public class UserSaveListener implements GXMybatisListenerService<User> {
 *     @Override
 *     public void saveEntityListener(User data) {
 *         // 用户保存后的处理逻辑
 *         log.info("用户{}已保存", data.getUsername());
 *     }
 * }
 * </pre>
 * </p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface GXMyBatisListener {
    /**
     * 监听器类
     * <p>
     * 指定用于处理监听事件的服务类，该类必须实现GXMybatisListenerService接口
     * </p>
     * 
     * @return 监听器服务类
     */
    Class<? extends GXMybatisListenerService<?>> listenerClazz();

    /**
     * 运行类型
     * <p>
     * 指定监听器的执行方式，支持同步(MYBATIS_SYNC_EVENT)和异步(MYBATIS_ASYNC_EVENT)两种模式
     * 同步模式下，监听器在主线程中执行，会阻塞主流程
     * 异步模式下，监听器在独立线程中执行，不会阻塞主流程
     * </p>
     * 
     * @return 运行类型，默认为同步执行
     */
    String runType() default GXMyBatisEventConstant.MYBATIS_SYNC_EVENT;
}
