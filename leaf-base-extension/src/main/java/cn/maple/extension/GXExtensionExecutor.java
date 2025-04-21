package cn.maple.extension;

import cn.hutool.http.HttpStatus;
import cn.maple.extension.exception.GXExtensionException;
import cn.maple.extension.register.GXAbstractComponentExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * GXExtensionExecutor 扩展执行器
 * <p>
 * 该类是扩展点执行框架的核心实现类，负责根据业务场景定位并执行扩展点。
 * 它实现了{@link GXAbstractComponentExecutor}抽象类，提供了扩展点的查找和执行逻辑。
 * <p>
 * 扩展点查找策略：
 * 1. 首先尝试使用完整的业务场景标识（bizId.useCase.scenario）查找
 * 2. 如果找不到，尝试使用默认场景（bizId.useCase.#defaultScenario#）查找
 * 3. 如果仍找不到，尝试使用默认用例和默认场景（bizId.#defaultUseCase#.#defaultScenario#）查找
 * 4. 如果所有尝试都失败，抛出异常
 * <p>
 * 线程安全性：该类依赖{@link GXExtensionRepository}的线程安全性，而GXExtensionRepository使用ConcurrentHashMap保证了线程安全。
 * <p>
 * 使用示例：
 * <pre>
 * // 在业务代码中使用
 * @Component
 * public class OrderService {
 *     @Resource
 *     private GXExtensionExecutor extensionExecutor;
 *     
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
 * @see GXAbstractComponentExecutor 抽象组件执行器
 * @see GXExtensionRepository 扩展仓库
 * @see GXBizScenario 业务场景
 */
@Component
@Slf4j
public class GXExtensionExecutor extends GXAbstractComponentExecutor {
    @Resource
    private GXExtensionRepository extensionRepository;

    /**
     * 定位组件（扩展点实现）
     * <p>
     * 该方法实现了{@link GXAbstractComponentExecutor#locateComponent}抽象方法，
     * 用于根据扩展点接口类型和业务场景定位到对应的扩展点实现。
     *
     * @param targetClz   扩展点接口类型，不能为null
     * @param bizScenario 业务场景，不能为null
     * @param <C>         扩展点接口类型
     * @return 扩展点实现实例
     * @throws NullPointerException 如果任何参数为null
     * @throws cn.maple.extension.exception.GXExtensionException 如果找不到对应的扩展点实现
     */
    @Override
    protected <C> C locateComponent(Class<C> targetClz, GXBizScenario bizScenario) {
        if (targetClz == null) {
            throw new NullPointerException("Target class cannot be null");
        }
        C extension = locateExtension(targetClz, bizScenario);
        log.debug("[Located Extension]: {}", extension.getClass().getSimpleName());
        return extension;
    }

        /**
     * 定位扩展点实现
     * <p>
     * 该方法根据扩展点接口类型和业务场景，按照以下策略查找扩展点实现：
     * 1. 首先尝试使用完整的业务场景标识（bizId.useCase.scenario）查找
     * 2. 如果找不到，尝试使用默认场景（bizId.useCase.#defaultScenario#）查找
     * 3. 如果仍找不到，尝试使用默认用例和默认场景（bizId.#defaultUseCase#.#defaultScenario#）查找
     * 4. 如果所有尝试都失败，抛出异常
     * <p>
     * 例如，如果业务场景标识是"mall.payment.alipay"，查找路径如下：
     * 1. 首先尝试使用"mall.payment.alipay"查找
     * 2. 如果找不到，尝试使用"mall.payment.#defaultScenario#"查找
     * 3. 如果仍找不到，尝试使用"mall.#defaultUseCase#.#defaultScenario#"查找
     * 4. 如果所有尝试都失败，抛出异常
     * <p>
     * 线程安全性：该方法不修改共享状态，依赖于线程安全的locate方法，因此是线程安全的
     *
     * @param targetClz   扩展点接口类型
     * @param bizScenario 业务场景
     * @param <E>         扩展点接口类型
     * @return 扩展点实现实例，如果找不到返回null
     * @throws NullPointerException 如果bizScenario为null
     * @throws cn.maple.extension.exception.GXExtensionException 如果找不到对应的扩展点实现
     */
    protected <E> E locateExtension(Class<E> targetClz, GXBizScenario bizScenario) {
        checkNull(bizScenario);

        E extension;

        log.debug("BizScenario in locateExtension is : {}", bizScenario.getUniqueIdentity());

        // 1、 first try with full namespace
        extension = firstTry(targetClz, bizScenario);
        if (extension != null) {
            return extension;
        }

        // 2、 second try with default scenario
        extension = secondTry(targetClz, bizScenario);
        if (extension != null) {
            return extension;
        }

        // 3、 third try with default use case + default scenario
        extension = defaultUseCaseTry(targetClz, bizScenario);
        if (extension != null) {
            return extension;
        }

        String errMessage = "Can not find extension with ExtensionPoint: " + targetClz + " BizScenario:" + bizScenario.getUniqueIdentity();
        throw new GXExtensionException(errMessage, HttpStatus.HTTP_NOT_FOUND);
    }

    /**
     * 第一次尝试：使用完整的业务场景标识查找
     * <p>
     * 例如：mall.payment.alipay
     * <p>
     * 线程安全性：该方法调用线程安全的locate方法，因此是线程安全的
     *
     * @param targetClz   扩展点接口类型
     * @param bizScenario 业务场景
     * @param <E>         扩展点接口类型
     * @return 扩展点实现实例，如果找不到返回null
     */
    private <E> E firstTry(Class<E> targetClz, GXBizScenario bizScenario) {
        log.debug("First trying with {}", bizScenario.getUniqueIdentity());
        return locate(targetClz.getName(), bizScenario.getUniqueIdentity());
    }

    /**
     * 第二次尝试：使用默认场景查找
     * <p>
     * 例如：mall.payment.#defaultScenario#
     * <p>
     * 线程安全性：该方法调用线程安全的locate方法，因此是线程安全的
     *
     * @param targetClz   扩展点接口类型
     * @param bizScenario 业务场景
     * @param <E>         扩展点接口类型
     * @return 扩展点实现实例，如果找不到返回null
     */
    private <E> E secondTry(Class<E> targetClz, GXBizScenario bizScenario) {
        log.debug("Second trying with {}", bizScenario.getIdentityWithDefaultScenario());
        return locate(targetClz.getName(), bizScenario.getIdentityWithDefaultScenario());
    }

    /**
     * 第三次尝试：使用默认用例和默认场景查找
     * <p>
     * 例如：mall.#defaultUseCase#.#defaultScenario#
     * <p>
     * 线程安全性：该方法调用线程安全的locate方法，因此是线程安全的
     *
     * @param targetClz   扩展点接口类型
     * @param bizScenario 业务场景
     * @param <E>         扩展点接口类型
     * @return 扩展点实现实例，如果找不到返回null
     */
    private <E> E defaultUseCaseTry(Class<E> targetClz, GXBizScenario bizScenario) {
        log.debug("Third trying with {}", bizScenario.getIdentityWithDefaultUseCase());
        return locate(targetClz.getName(), bizScenario.getIdentityWithDefaultUseCase());
    }

    /**
     * 从扩展仓库中查找扩展点实现
     * <p>
     * 该方法根据扩展点接口名称和业务场景唯一标识，从扩展仓库中查找对应的扩展点实现。
     * <p>
     * 线程安全性：该方法使用线程安全的ConcurrentHashMap进行查找，因此是线程安全的
     *
     * @param name           扩展点接口全限定名
     * @param uniqueIdentity 业务场景唯一标识
     * @param <E>            扩展点接口类型
     * @return 扩展点实现实例，如果找不到返回null
     */
    @SuppressWarnings("all")
    private <E> E locate(String name, String uniqueIdentity) {
        GXExtensionCoordinate coordinate = new GXExtensionCoordinate(name, uniqueIdentity);
        return (E) extensionRepository.getExtensionRepo().get(coordinate);
    }

    /**
     * 检查业务场景对象是否为null
     * <p>
     * 该方法用于检查业务场景对象是否为null，如果为null则抛出异常。
     * <p>
     * 线程安全性：该方法不修改任何状态，只进行参数检查，因此是线程安全的
     *
     * @param bizScenario 业务场景对象
     * @throws IllegalArgumentException 如果bizScenario为null
     */
    private void checkNull(GXBizScenario bizScenario) {
        if (bizScenario == null) {
            throw new IllegalArgumentException("BizScenario cannot be null for extension");
        }
    }
}
