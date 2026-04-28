package cn.maple.core.framework.util;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.Objects;

/**
 * Spring上下文工具类
 * <p>
 * 提供对Spring ApplicationContext的访问能力，用于在非Spring管理的类中
 * 获取Spring Bean、判断Bean的特性、获取环境配置以及注册单例Bean等操作。
 * 该类通过GXApplicationContextSingleton枚举单例获取ApplicationContext实例。
 * </p>
 *
 * <p>
 * 功能特点：
 * - 提供多种方式获取Bean实例（按名称、按类型、按名称和类型）
 * - 支持判断Bean的特性（是否存在、是否单例）
 * - 提供获取环境配置的便捷方法
 * - 支持动态注册单例Bean到Spring容器
 * - 安全处理异常，避免因Bean不存在导致应用崩溃
 * </p>
 *
 * <p>
 * 线程安全说明：
 * - 所有方法均为静态方法，无需创建实例
 * - ApplicationContext本身是线程安全的
 * - 日志记录使用线程安全的SLF4J实现
 * - 适合在多线程环境下使用
 * </p>
 *
 * <p>
 * 性能优化：
 * - 使用单例模式获取ApplicationContext，避免重复查找
 * - 异常处理时只记录必要信息，减少日志开销
 * - 提供类型安全的泛型方法，减少类型转换
 * - 空值检查使用Objects工具类，提高代码可读性和安全性
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 获取指定类型的Bean
 * UserService userService = GXSpringContextUtils.getBean(UserService.class);
 * if (userService != null) {
 *     userService.doSomething();
 * }
 *
 * // 2. 获取指定名称的Bean
 * UserService userService = GXSpringContextUtils.getBean("userServiceImpl", UserService.class);
 *
 * // 3. 判断Bean是否存在
 * if (GXSpringContextUtils.containsBean("userService")) {
 *     // Bean存在，可以安全获取
 *     Object userServiceBean = GXSpringContextUtils.getBean("userService");
 * }
 *
 * // 4. 获取环境配置
 * Environment env = GXSpringContextUtils.getEnvironment();
 * String serverPort = env.getProperty("server.port");
 * String activeProfiles = String.join(",", env.getActiveProfiles());
 *
 * // 5. 注册单例Bean
 * UserService customService = new UserServiceImpl();
 * GXSpringContextUtils.registerSingleton("customUserService", customService);
 *
 * // 6. 获取指定类型的所有Bean
 * Map<String, UserService> userServices = GXSpringContextUtils.getBeans(UserService.class);
 * userServices.forEach((name, service) -> {
 *     System.out.println("Bean名称: " + name);
 *     service.doSomething();
 * });
 * </pre>
 * </p>
 *
 * <p>
 * 安全注意事项：
 * - 所有方法都进行了异常捕获，防止因Bean不存在导致应用崩溃
 * - 获取Bean失败时返回null而不是抛出异常，调用方需要进行空值检查
 * - 注册单例Bean时会检查是否已存在相同类型的Bean，避免重复注册
 * </p>
 *
 * @author maple
 * @since 1.0.0
 */
public class GXSpringContextUtils {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXSpringContextUtils.class);

    /**
     * Spring应用上下文，通过GXApplicationContextSingleton单例获取
     * -- GETTER --
     * 获取Spring应用上下文
     */
    @Getter
    private static final ApplicationContext applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();

    /**
     * 私有构造函数，防止实例化
     */
    private GXSpringContextUtils() {
    }

    /**
     * 根据Bean名称获取Bean实例
     * <p>
     * 该方法通过Bean名称从Spring容器中获取Bean实例。如果Bean不存在或获取过程中发生异常，
     * 将记录错误日志并返回null，而不是抛出异常，避免因Bean不存在导致应用崩溃。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取名为"userDao"的Bean
     * Object userDao = GXSpringContextUtils.getBean("userDao");
     * if (userDao != null) {
     *     // 使用Bean进行操作
     * } else {
     *     // Bean不存在，进行替代处理
     * }
     * </pre>
     * </p>
     *
     * @param name Bean的名称，不能为null
     * @return 返回Bean实例，如果获取失败则返回null
     */
    public static Object getBean(String name) {
        if (Objects.isNull(name) || Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getBean(name);
        } catch (Exception e) {
            LOG.warn("获取Bean失败: 名称={}, 错误={}", name, e.getMessage());
        }
        return null;
    }

    /**
     * 根据Bean类型获取Bean实例
     * <p>
     * 该方法通过Bean类型从Spring容器中获取Bean实例。如果该类型的Bean不存在或获取过程中发生异常，
     * 将记录警告日志并返回null，而不是抛出异常，避免因Bean不存在导致应用崩溃。
     * </p>
     * <p>
     * 注意：如果容器中存在多个相同类型的Bean，将抛出NoUniqueBeanDefinitionException异常。
     * 此时应使用{@link #getBean(String, Class)}方法，通过名称和类型共同确定唯一的Bean。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取UserService类型的Bean
     * UserService userService = GXSpringContextUtils.getBean(UserService.class);
     * if (userService != null) {
     *     // 使用Bean进行操作
     *     userService.createUser(user);
     * }
     * </pre>
     * </p>
     *
     * @param clazz Bean的类型，不能为null
     * @param <T>   Bean的泛型类型
     * @return 返回指定类型的Bean实例，如果获取失败则返回null
     */
    public static <T> T getBean(Class<T> clazz) {
        if (Objects.isNull(clazz) || Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getBean(clazz);
        } catch (Exception e) {
            LOG.debug("获取Bean失败: 类型={}, 错误={}", clazz.getSimpleName(), e.getMessage());
        }
        return null;
    }

    /**
     * 根据Bean名称和类型获取Bean实例
     * <p>
     * 该方法通过Bean名称和类型从Spring容器中获取Bean实例。这是最精确的获取Bean的方式，
     * 特别适用于同一类型有多个Bean实例的情况。如果指定名称和类型的Bean不存在或获取过程中发生异常，
     * 将记录错误日志并返回null，而不是抛出异常，避免因Bean不存在导致应用崩溃。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取名为"masterDataSource"，类型为DataSource的Bean
     * DataSource dataSource = GXSpringContextUtils.getBean("masterDataSource", DataSource.class);
     * if (dataSource != null) {
     *     // 使用数据源进行操作
     *     Connection conn = dataSource.getConnection();
     * }
     * </pre>
     * </p>
     *
     * @param name         Bean的名称，不能为null
     * @param requiredType Bean的类型，不能为null
     * @param <T>          Bean的泛型类型
     * @return 返回指定名称和类型的Bean实例，如果获取失败则返回null
     */
    public static <T> T getBean(String name, Class<T> requiredType) {
        if (Objects.isNull(name) || Objects.isNull(requiredType) || Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getBean(name, requiredType);
        } catch (Exception e) {
            LOG.debug("获取Bean失败: 名称={}, 类型={}, 错误={}", name, requiredType.getSimpleName(), e.getMessage());
        }
        return null;
    }

    /**
     * 判断是否包含指定名称的Bean
     * <p>
     * 该方法用于检查Spring容器中是否存在指定名称的Bean。在尝试获取Bean之前，
     * 可以先使用此方法检查Bean是否存在，避免因Bean不存在而抛出异常。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 检查名为"transactionManager"的Bean是否存在
     * if (GXSpringContextUtils.containsBean("transactionManager")) {
     *     // Bean存在，可以安全获取
     *     Object txManager = GXSpringContextUtils.getBean("transactionManager");
     * } else {
     *     // Bean不存在，进行替代处理
     *     log.warn("事务管理器不存在，将使用默认事务管理");
     * }
     * </pre>
     * </p>
     *
     * @param name Bean的名称，不能为null
     * @return 如果包含返回true，否则返回false；如果name为null或applicationContext为null，返回false
     */
    public static boolean containsBean(String name) {
        if (Objects.isNull(name) || Objects.isNull(applicationContext)) {
            return false;
        }
        try {
            return applicationContext.containsBean(name);
        } catch (Exception e) {
            LOG.debug("检查Bean是否存在时发生错误: 名称={}, 错误={}", name, e.getMessage());
            return false;
        }
    }

    /**
     * 判断指定名称的Bean是否为单例
     * <p>
     * 该方法用于检查Spring容器中指定名称的Bean是否为单例模式。单例Bean在容器中只有一个实例，
     * 而非单例Bean（如prototype作用域的Bean）每次请求都会创建新的实例。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 检查名为"userService"的Bean是否为单例
     * if (GXSpringContextUtils.isSingleton("userService")) {
     *     System.out.println("userService是单例Bean");
     * } else {
     *     System.out.println("userService不是单例Bean");
     * }
     * </pre>
     * </p>
     *
     * @param name Bean的名称，不能为null
     * @return 如果是单例返回true，否则返回false；如果name为null、Bean不存在或applicationContext为null，返回false
     */
    public static boolean isSingleton(String name) {
        if (Objects.isNull(name) || Objects.isNull(applicationContext) || !containsBean(name)) {
            return false;
        }
        try {
            return applicationContext.isSingleton(name);
        } catch (Exception e) {
            LOG.debug("检查Bean是否为单例时发生错误: 名称={}, 错误={}", name, e.getMessage());
            return false;
        }
    }

    /**
     * 获取指定名称Bean的类型
     * <p>
     * 该方法用于获取Spring容器中指定名称Bean的类型。在不需要获取Bean实例，
     * 只需要了解Bean类型的场景下非常有用。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取名为"dataSource"的Bean的类型
     * Class<?> dataSourceType = GXSpringContextUtils.getType("dataSource");
     * if (dataSourceType != null) {
     *     System.out.println("数据源类型: " + dataSourceType.getName());
     *     // 检查是否是特定类型的数据源
     *     if (HikariDataSource.class.isAssignableFrom(dataSourceType)) {
     *         System.out.println("使用的是HikariCP连接池");
     *     }
     * }
     * </pre>
     * </p>
     *
     * @param name Bean的名称，不能为null
     * @return 返回Bean的类型，如果name为null、Bean不存在或applicationContext为null，则返回null
     */
    public static Class<?> getType(String name) {
        if (Objects.isNull(name) || Objects.isNull(applicationContext) || !containsBean(name)) {
            return null;
        }
        try {
            return applicationContext.getType(name);
        } catch (Exception e) {
            LOG.debug("获取Bean类型失败: 名称={}, 错误={}", name, e.getMessage());
            return null;
        }
    }

    /**
     * 获取指定类型的所有Bean
     * <p>
     * 该方法用于获取Spring容器中所有指定类型的Bean。这对于需要使用同一接口的多个实现，
     * 或需要获取特定类型的所有Bean的场景非常有用。返回的Map中，key为Bean名称，value为Bean实例。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取所有实现了EventListener接口的Bean
     * Map<String, EventListener> listeners = GXSpringContextUtils.getBeans(EventListener.class);
     * if (listeners != null && !listeners.isEmpty()) {
     *     System.out.println("找到" + listeners.size() + "个事件监听器");
     *     listeners.forEach((name, listener) -> {
     *         System.out.println("监听器名称: " + name + ", 类型: " + listener.getClass().getSimpleName());
     *         // 使用监听器进行操作
     *     });
     * }
     * </pre>
     * </p>
     *
     * @param clazz Bean的类型，不能为null
     * @param <T>   Bean的泛型类型
     * @return 返回指定类型的所有Bean的Map，key为Bean名称，value为Bean实例；如果clazz为null或applicationContext为null，返回空Map
     */
    public static <T> Map<String, T> getBeans(Class<T> clazz) {
        if (Objects.isNull(clazz) || Objects.isNull(applicationContext)) {
            return Map.of();
        }
        try {
            return applicationContext.getBeansOfType(clazz);
        } catch (Exception e) {
            LOG.debug("获取所有Bean失败: 类型={}, 错误={}", clazz.getSimpleName(), e.getMessage());
            return Map.of();
        }
    }

    /**
     * 获取Spring环境配置
     * <p>
     * 该方法用于获取Spring的Environment对象，通过该对象可以访问配置属性，
     * 包括应用程序属性文件、系统环境变量、JVM系统属性等。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取Environment对象
     * Environment env = GXSpringContextUtils.getEnvironment();
     * if (env != null) {
     *     // 获取配置属性
     *     String serverPort = env.getProperty("server.port", "8080"); // 默认值为8080
     *     String[] activeProfiles = env.getActiveProfiles(); // 获取激活的配置文件
     *
     *     // 获取类型安全的配置属性
     *     Integer maxConnections = env.getProperty("database.max-connections", Integer.class, 100);
     *
     *     // 检查是否存在某个属性
     *     boolean hasDataSourceUrl = env.containsProperty("spring.datasource.url");
     * }
     * </pre>
     * </p>
     *
     * @return 返回Environment对象，如果applicationContext为null，返回null
     */
    public static Environment getEnvironment() {
        if (Objects.isNull(applicationContext)) {
            return null;
        }
        try {
            return applicationContext.getEnvironment();
        } catch (Exception e) {
            LOG.debug("获取Environment失败: 错误={}", e.getMessage());
            return null;
        }
    }

    /**
     * 注册单例Bean到Spring容器
     * <p>
     * 该方法用于动态注册单例Bean到Spring容器中。只有当容器中不存在该类型的Bean时才会注册，
     * 避免重复注册导致的问题。这对于需要在运行时动态创建Bean的场景非常有用。
     * </p>
     * <p>
     * 注意：
     * 1. 通过此方法注册的Bean不会经过Spring的生命周期管理（如@PostConstruct等注解不会生效）
     * 2. 如果Bean依赖其他Bean，需要手动注入依赖
     * 3. 此方法只适用于注册单例Bean，不支持其他作用域
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 创建一个自定义数据源并注册到Spring容器
     * DataSource customDataSource = new BasicDataSource();
     * ((BasicDataSource) customDataSource).setDriverClassName("com.mysql.cj.jdbc.Driver");
     * ((BasicDataSource) customDataSource).setUrl("jdbc:mysql://localhost:3306/mydb");
     * ((BasicDataSource) customDataSource).setUsername("root");
     * ((BasicDataSource) customDataSource).setPassword("password");
     *
     * // 注册到Spring容器
     * GXSpringContextUtils.registerSingleton("customDataSource", customDataSource);
     *
     * // 之后可以通过Spring容器获取该Bean
     * DataSource ds = GXSpringContextUtils.getBean("customDataSource", DataSource.class);
     * </pre>
     * </p>
     *
     * @param beanName        Bean的名称，不能为null或空字符串
     * @param singletonObject Bean的实例对象，不能为null
     * @throws IllegalArgumentException 如果beanName为null或空，或singletonObject为null
     */
    public static void registerSingleton(String beanName, Object singletonObject) {
        if (Objects.isNull(beanName) || beanName.trim().isEmpty()) {
            throw new IllegalArgumentException("Bean名称不能为null或空");
        }
        if (Objects.isNull(singletonObject)) {
            throw new IllegalArgumentException("Bean实例不能为null");
        }
        if (Objects.isNull(applicationContext)) {
            LOG.error("ApplicationContext为null，无法注册Bean: {}", beanName);
            return;
        }

        try {
            // 检查是否已存在同类型的Bean
            if (null == getBean(singletonObject.getClass())) {
                // 确保ApplicationContext是AbstractApplicationContext类型
                if (applicationContext instanceof AbstractApplicationContext) {
                    ((AbstractApplicationContext) applicationContext).getBeanFactory().registerSingleton(beanName, singletonObject);
                    LOG.debug("成功注册单例Bean: 名称={}, 类型={}", beanName, singletonObject.getClass().getName());
                } else {
                    LOG.debug("无法注册Bean: ApplicationContext不是AbstractApplicationContext类型");
                }
            } else {
                LOG.debug("已存在类型为{}的Bean，跳过注册", singletonObject.getClass().getName());
            }
        } catch (Exception e) {
            LOG.error("注册单例Bean失败: 名称={}, 类型={}, 错误={}", beanName, singletonObject.getClass().getName(), e.getMessage(), e);
        }
    }
}
