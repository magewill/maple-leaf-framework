package cn.maple.extension.autoconfigure;

import cn.maple.extension.GXExtensionExecutor;
import cn.maple.extension.GXExtensionRepository;
import cn.maple.extension.register.GXExtensionBootstrap;
import cn.maple.extension.register.GXExtensionRegister;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 扩展点框架自动配置类
 * <p>
 * 该配置类负责自动注册扩展点框架所需的核心组件，包括：
 * 1. {@link GXExtensionBootstrap} - 扩展点启动引导类，负责在应用启动时扫描并注册所有扩展点实现
 * 2. {@link GXExtensionRepository} - 扩展点仓库，负责存储所有扩展点实现的注册信息
 * 3. {@link GXExtensionExecutor} - 扩展点执行器，负责根据业务场景定位并执行扩展点
 * 4. {@link GXExtensionRegister} - 扩展点注册器，负责将扩展点实现注册到扩展点仓库
 * <p>
 * 所有Bean都使用{@link ConditionalOnMissingBean}注解，允许用户自定义实现覆盖默认配置。
 * 组件之间的依赖关系和初始化顺序已经通过Spring的依赖注入机制和{@code initMethod}属性保证。
 * 使用示例：
 * <pre>
 * // 在Spring Boot应用中，只需要引入依赖，无需额外配置
 * // 在业务代码中使用
 * @Component
 * public class OrderService {
 *     @Resource
 *     private GXExtensionExecutor extensionExecutor;
 * <p>
 *     public OrderResult processOrder(Order order, String bizId) {
 *         // 创建业务场景
 *         GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
 *         // 执行扩展点方法
 *         return extensionExecutor.execute(OrderProcessExtPoint.class, scenario,
 *                 extension -> extension.process(order));
 *     }
 * }
 * </pre>
 *
 * @author britton
 * @see GXExtensionBootstrap 扩展点启动引导类
 * @see GXExtensionRepository 扩展点仓库
 * @see GXExtensionExecutor 扩展点执行器
 * @see GXExtensionRegister 扩展点注册器
 */
@AutoConfiguration
public class GXExtensionAutoConfiguration {
    /**
     * 创建扩展点启动引导类Bean
     * <p>
     * 该Bean负责在应用启动时自动扫描并注册所有标记了{@link cn.maple.extension.GXExtension}或
     * {@link cn.maple.extension.GXExtensions}注解的扩展点实现类。通过{@code initMethod="init"}属性，
     * 确保在Bean创建后自动调用{@code init()}方法完成初始化。
     * <p>
     * 线程安全性：该Bean在应用启动时执行一次初始化，之后不再修改状态，因此是线程安全的。
     *
     * @return GXExtensionBootstrap实例
     */
    @Bean(value = "extensionBootstrap")
    @ConditionalOnMissingBean(GXExtensionBootstrap.class)
    public GXExtensionBootstrap extPointBootstrap() {
        return new GXExtensionBootstrap();
    }

    /**
     * 创建扩展点仓库Bean
     * <p>
     * 该Bean负责存储所有扩展点实现的注册信息，是扩展点框架的核心存储组件。
     * 它使用{@code ConcurrentHashMap}存储扩展点信息，确保在多线程环境下的并发安全。
     * <p>
     * 线程安全性：该Bean使用{@code ConcurrentHashMap}存储扩展点信息，保证了线程安全。
     *
     * @return GXExtensionRepository实例
     */
    @Bean("extensionRepository")
    @ConditionalOnMissingBean(GXExtensionRepository.class)
    public GXExtensionRepository extPointRepository() {
        return new GXExtensionRepository();
    }

    /**
     * 创建扩展点执行器Bean
     * <p>
     * 该Bean负责根据业务场景定位并执行扩展点，是扩展点框架的核心执行组件。
     * 它实现了{@link cn.maple.extension.register.GXAbstractComponentExecutor}抽象类，
     * 提供了扩展点的查找和执行逻辑。
     * <p>
     * 线程安全性：该Bean不存储状态，主要依赖{@link GXExtensionRepository}的线程安全性，因此是线程安全的。
     *
     * @return GXExtensionExecutor实例
     */
    @Bean(value = "extensionExecutor")
    @ConditionalOnMissingBean(GXExtensionExecutor.class)
    public GXExtensionExecutor extensionExecutor() {
        return new GXExtensionExecutor();
    }

    /**
     * 创建扩展点注册器Bean
     * <p>
     * 该Bean负责将标记了{@link cn.maple.extension.GXExtension}或{@link cn.maple.extension.GXExtensions}注解的
     * 扩展点实现类注册到扩展仓库中。注册过程会处理AOP代理对象，确保能够正确获取原始类的注解信息。
     * <p>
     * 线程安全性：该Bean不存储状态，主要依赖{@link GXExtensionRepository}的线程安全性，因此是线程安全的。
     *
     * @return GXExtensionRegister实例
     */
    @Bean(value = "extensionRegister")
    @ConditionalOnMissingBean(GXExtensionRegister.class)
    public GXExtensionRegister extensionRegister() {
        return new GXExtensionRegister();
    }
}
