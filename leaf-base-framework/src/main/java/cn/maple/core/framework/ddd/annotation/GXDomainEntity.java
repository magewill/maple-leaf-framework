package cn.maple.core.framework.ddd.annotation;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 领域驱动设计(DDD)中的实体注解
 * <p>
 * 该注解用于标识一个类是领域模型中的实体(Entity)。在DDD中，实体是具有唯一标识的对象，
 * 其身份在整个生命周期中保持不变，即使其属性发生变化。实体通常代表业务领域中的核心概念。
 * </p>
 *
 * <p>
 * 该注解具有以下特性：
 * 1. 被标记的类会自动注册为Spring组件
 * 2. 作用域为原型(prototype)，每次获取都会创建新实例
 * 3. 实体对象不是线程安全的，在多线程环境中需要特别注意
 * </p>
 *
 * <p>使用示例:</p>
 * <pre>
 * // 1. 定义用户实体
 * @GXDomainEntity
 * public class User {
 *     private final String id; // 实体标识
 *     private String username;
 *     private String email;
 *     private String password;
 *     private UserStatus status;
 * <p>
 *     // 构造函数确保实体创建时处于有效状态
 *     public User(String id, String username, String email, String password) {
 *         // 验证参数
 *         if (StringUtils.isBlank(id)) {
 *             throw new IllegalArgumentException("用户ID不能为空");
 *         }
 *         if (StringUtils.isBlank(username)) {
 *             throw new IllegalArgumentException("用户名不能为空");
 *         }
 *         if (StringUtils.isBlank(email)) {
 *             throw new IllegalArgumentException("邮箱不能为空");
 *         }
 *         if (!isValidEmail(email)) {
 *             throw new IllegalArgumentException("邮箱格式不正确");
 *         }
 *         if (StringUtils.isBlank(password)) {
 *             throw new IllegalArgumentException("密码不能为空");
 *         }
 * <p>
 *         this.id = id;
 *         this.username = username;
 *         this.email = email;
 *         this.password = password;
 *         this.status = UserStatus.INACTIVE; // 初始状态为未激活
 *     }
 * <p>
 *     // 领域行为方法
 *     public void activate() {
 *         if (this.status == UserStatus.ACTIVE) {
 *             throw new IllegalStateException("用户已经处于激活状态");
 *         }
 *         this.status = UserStatus.ACTIVE;
 *     }
 * <p>
 *     public void deactivate() {
 *         if (this.status == UserStatus.INACTIVE) {
 *             throw new IllegalStateException("用户已经处于未激活状态");
 *         }
 *         this.status = UserStatus.INACTIVE;
 *     }
 * <p>
 *     public void changeEmail(String newEmail) {
 *         if (StringUtils.isBlank(newEmail)) {
 *             throw new IllegalArgumentException("新邮箱不能为空");
 *         }
 *         if (!isValidEmail(newEmail)) {
 *             throw new IllegalArgumentException("新邮箱格式不正确");
 *         }
 *         this.email = newEmail;
 *     }
 * <p>
 *     public void changePassword(String oldPassword, String newPassword) {
 *         if (!this.password.equals(oldPassword)) {
 *             throw new IllegalArgumentException("原密码不正确");
 *         }
 *         if (StringUtils.isBlank(newPassword)) {
 *             throw new IllegalArgumentException("新密码不能为空");
 *         }
 *         this.password = newPassword;
 *     }
 *
 *     // 验证邮箱格式的私有方法
 *     private boolean isValidEmail(String email) {
 *         // 邮箱格式验证逻辑
 *         return email.matches("^[\\w-]+(\\.[\\w-]+)*@[\\w-]+(\\.[\\w-]+)+$");
 *     }
 * <p>
 *     // getter方法
 *     public String getId() {
 *         return id;
 *     }
 * <p>
 *     public String getUsername() {
 *         return username;
 *     }
 * <p>
 *     public String getEmail() {
 *         return email;
 *     }
 * <p>
 *     public UserStatus getStatus() {
 *         return status;
 *     }
 * <p>
 *     // 不提供密码的getter方法，保护敏感信息
 * <p>
 *     // 用户状态枚举
 *     public enum UserStatus {
 *         ACTIVE, INACTIVE
 *     }
 * }
 * <p>
 * // 2. 在应用服务中使用
 * @Service
 * public class UserService {
 *     private final UserRepository userRepository;
 *     private final UserFactory userFactory;
 * <p>
 *     public UserService(UserRepository userRepository, UserFactory userFactory) {
 *         this.userRepository = userRepository;
 *         this.userFactory = userFactory;
 *     }
 * <p>
 *     public String registerUser(RegisterUserCommand command) {
 *         // 使用工厂创建用户实体
 *         User user = userFactory.createUser(
 *             command.getUsername(),
 *             command.getEmail(),
 *             command.getPassword()
 *         );
 * <p>
 *         // 保存用户实体
 *         userRepository.save(user);
 * <p>
 *         return user.getId();
 *     }
 * <p>
 *     public void activateUser(String userId) {
 *         // 获取用户实体
 *         User user = userRepository.findById(userId);
 *         if (user == null) {
 *             throw new UserNotFoundException("用户不存在: " + userId);
 *         }
 * <p>
 *         // 执行领域行为
 *         user.activate();
 * <p>
 *         // 保存更改
 *         userRepository.save(user);
 *     }
 * }
 * </pre>
 *
 * <p>
 * 使用此注解时应注意：
 * 1. 实体应该封装业务规则和不变量，确保自身始终处于有效状态
 * 2. 实体的标识(ID)应该在构造时确定，且在整个生命周期中保持不变
 * 3. 实体应该提供反映业务行为的方法，而不仅仅是getter/setter
 * 4. 实体之间的关系应该通过标识引用，而不是直接对象引用
 * 5. 由于是原型作用域，每次注入都会创建新实例，应避免过度依赖注入
 * </p>
 *
 * @author britton
 * @since 2021-11-08
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public @interface GXDomainEntity {
}
