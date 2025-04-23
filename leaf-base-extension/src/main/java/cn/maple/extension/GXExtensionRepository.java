package cn.maple.extension;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展仓库(Extension Repository)用于存储和管理扩展点实现
 * <p>
 * 扩展仓库是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。
 * 当系统启动时，框架会自动扫描所有带有{@link GXExtension}注解的类，并将其实例化后存储到扩展仓库中。
 * 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。
 * <p>
 * 线程安全性：该类使用ConcurrentHashMap存储扩展点实现，因此是线程安全的。
 * 所有的读写操作都是原子的，可以安全地在多线程环境下使用。
 * <p>
 * 使用示例：
 * <pre>
 * // 注入扩展仓库
 * @Autowired
 * private GXExtensionRepository extensionRepository;
 * 
 * // 获取扩展点实现
 * GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
 * Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
 * PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);
 * 
 * // 执行扩展点方法
 * extension.pay(order);
 * </pre>
 *
 * @author britton
 * @see GXExtension 扩展点注解
 * @see GXExtensionCoordinate 扩展坐标
 * @see GXExtensionPoint 扩展点接口
 */
@Getter
@Component
public class GXExtensionRepository {
    /**
     * 扩展点缓存对象
     * <p>
     * 使用ConcurrentHashMap存储扩展点实现，key为扩展坐标，value为扩展点实现实例。
     * ConcurrentHashMap提供了线程安全的读写操作，适合在多线程环境下使用。
     * <p>
     * 线程安全性：ConcurrentHashMap是线程安全的，可以安全地在多线程环境下进行读写操作。
     * -- GETTER --
     * 获取扩展点缓存对象
     */
    private final Map<GXExtensionCoordinate, GXExtensionPoint> extensionRepo = new ConcurrentHashMap<>();
    
    /**
     * 根据扩展坐标查找扩展点实现
     * <p>
     * 该方法根据扩展坐标从扩展仓库中查找对应的扩展点实现。
     * 如果找不到对应的实现，则返回空的Optional。
     * <p>
     * 线程安全性：该方法不修改扩展仓库的状态，只读取数据，因此是线程安全的。
     *
     * @param coordinate 扩展坐标，不能为null
     * @return 扩展点实现的Optional包装，如果找不到则返回空的Optional
     * @throws NullPointerException 如果coordinate为null
     */
    public Optional<GXExtensionPoint> findExtension(GXExtensionCoordinate coordinate) {
        if (coordinate == null) {
            throw new NullPointerException("扩展坐标不能为空");
        }
        return Optional.ofNullable(extensionRepo.get(coordinate));
    }
    
    /**
     * 注册扩展点实现
     * <p>
     * 该方法将扩展点实现注册到扩展仓库中。
     * 如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。
     * <p>
     * 线程安全性：该方法使用ConcurrentHashMap的put方法，是线程安全的。
     *
     * @param coordinate 扩展坐标，不能为null
     * @param extension 扩展点实现，不能为null
     * @throws NullPointerException 如果coordinate或extension为null
     */
    public void registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension) {
        if (coordinate == null) {
            throw new NullPointerException("扩展坐标不能为空");
        }
        if (extension == null) {
            throw new NullPointerException("扩展点实现不能为空");
        }
        extensionRepo.put(coordinate, extension);
    }
}
