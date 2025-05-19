package cn.maple.core.framework.ddd.presenter;

/**
 * 领域驱动设计(DDD)中的展示者(Presenter)基础接口
 * <p>
 * 在DDD中，Presenter负责将领域模型转换为适合UI展示的数据格式。
 * 它是应用层与UI层之间的适配器，确保领域逻辑与UI展示关注点分离。
 * Presenter接收来自应用服务的领域对象，并将其转换为视图模型(ViewModel)或DTO。
 * </p>
 *
 * <p>使用示例:</p>
 * <pre>
 * {@code
 * // 1. 定义视图模型
 * public class UserViewModel {
 *     private String displayName;
 *     private String email;
 *     private String role;
 *
 *     // getter和setter方法
 * }
 *
 * // 2. 实现Presenter接口
 * @Component
 * public class UserPresenter implements GXBasePresenter {
 *
 *     /**
 *      * 将用户领域对象转换为视图模型
 *      *\/
 *     public UserViewModel presentUser(User user) {
 *         UserViewModel viewModel = new UserViewModel();
 *         viewModel.setDisplayName(user.getFirstName() + " " + user.getLastName());
 *         viewModel.setEmail(user.getEmail());
 *         viewModel.setRole(translateRole(user.getRole()));
 *         return viewModel;
 *     }
 *
 *     /**
 *      * 将用户角色代码转换为可读的角色名称
 *      *\/
 *     private String translateRole(String roleCode) {
 *         // 角色代码转换逻辑
 *         return switch (roleCode) {
 *             case "ADMIN" -> "系统管理员";
 *             case "USER" -> "普通用户";
 *             default -> "访客";
 *         };
 *     }
 * }
 *
 * // 3. 在应用服务中使用
 * @Service
 * public class UserApplicationService {
 *     private final UserRepository userRepository;
 *     private final UserPresenter userPresenter;
 *
 *     public UserApplicationService(UserRepository userRepository, UserPresenter userPresenter) {
 *         this.userRepository = userRepository;
 *         this.userPresenter = userPresenter;
 *     }
 *
 *     public UserViewModel getUserProfile(String userId) {
 *         // 获取领域对象
 *         User user = userRepository.findById(userId);
 *         if (user == null) {
 *             throw new UserNotFoundException(userId);
 *         }
 *
 *         // 使用Presenter转换为视图模型
 *         return userPresenter.presentUser(user);
 *     }
 * }
 * }
 * </pre>
 *
 * <p>
 * 实现此接口时应注意：
 * 1. Presenter应专注于数据转换和格式化，不应包含业务逻辑
 * 2. 可以在Presenter中处理国际化、日期格式化等UI相关的转换
 * 3. 保持Presenter的无状态性，确保线程安全
 * </p>
 *
 * @author britton
 * @since 2021-11-08
 */
public interface GXBasePresenter {
    // 作为标记接口，不定义具体方法
    // 具体的Presenter实现类应根据需要定义自己的方法
}
