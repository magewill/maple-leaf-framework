package cn.maple.extension;

import java.io.Serializable;

/**
 * 扩展坐标(Extension Coordinate)用于唯一定位一个扩展点实现
 * <p>
 * 扩展坐标由扩展点类型和业务场景两部分组成，用于在扩展点框架中唯一标识一个扩展点实现。
 * 当需要查找扩展点实现时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。
 * <p>
 * 线程安全性：该类是不可变的，所有字段都是final的，因此是线程安全的。
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个扩展坐标
 * Class&lt;PaymentExtPoint&gt; extPointClass = PaymentExtPoint.class;
 * GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");
 * GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);
 * 
 * // 使用扩展坐标查找扩展点实现
 * PaymentExtPoint extension = extensionExecutor.findExtension(coordinate);
 * 
 * // 执行扩展点方法
 * extension.pay(order);
 * </pre>
 *
 * @author britton
 */
public class GXExtensionCoordinate implements Serializable {
    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * 扩展点名字
     * <p>
     * 扩展点的完全限定类名，用于唯一标识一个扩展点接口
     */
    private final String extensionPointName;

    /**
     * 业务场景唯一标识
     * <p>
     * 业务场景的唯一标识，格式为：bizId.useCase.scenario
     */
    private final String bizScenarioUniqueIdentity;

    /**
     * 扩展点的类型
     * <p>
     * 扩展点接口的Class对象，用于在运行时获取扩展点的类型信息
     */
    private final Class<?> extensionPointClass;

    /**
     * 业务场景
     * <p>
     * 业务场景对象，包含业务ID、用例和场景三个维度
     */
    private final GXBizScenario bizScenario;

    /**
     * 创建扩展坐标对象
     * <p>
     * 该构造方法用于创建一个完整的扩展坐标对象，包含扩展点类型和业务场景两个维度
     * <p>
     * 线程安全性：该构造方法初始化不可变对象，因此是线程安全的
     *
     * @param extPtClass  扩展点类型，不能为null
     * @param bizScenario 业务场景，不能为null
     * @throws NullPointerException 如果extPtClass或bizScenario为null
     */
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario) {
        if (extPtClass == null) {
            throw new NullPointerException("扩展点类型不能为空");
        }
        if (bizScenario == null) {
            throw new NullPointerException("业务场景不能为空");
        }
        this.extensionPointClass = extPtClass;
        this.extensionPointName = extPtClass.getName();
        this.bizScenario = bizScenario;
        this.bizScenarioUniqueIdentity = bizScenario.getUniqueIdentity();
    }

    /**
     * 创建扩展坐标对象（使用字符串标识）
     * <p>
     * 该构造方法用于创建一个基于字符串标识的扩展坐标对象，适用于不需要扩展点类型信息的场景
     * <p>
     * 线程安全性：该构造方法初始化不可变对象，因此是线程安全的
     *
     * @param extensionPoint 扩展点名字，不能为null或空字符串
     * @param bizScenario    业务场景唯一标识，不能为null或空字符串
     * @throws IllegalArgumentException 如果extensionPoint或bizScenario为null或空字符串
     */
    public GXExtensionCoordinate(String extensionPoint, String bizScenario) {
        if (extensionPoint == null || extensionPoint.isEmpty()) {
            throw new IllegalArgumentException("扩展点名字不能为空");
        }
        if (bizScenario == null || bizScenario.isEmpty()) {
            throw new IllegalArgumentException("业务场景唯一标识不能为空");
        }
        this.extensionPointName = extensionPoint;
        this.bizScenarioUniqueIdentity = bizScenario;
        this.extensionPointClass = null;
        this.bizScenario = null;
    }

    /**
     * 创建扩展坐标对象的工厂方法
     * <p>
     * 该方法是创建扩展坐标对象的推荐方式，提供了更好的可读性
     * <p>
     * 线程安全性：该方法创建并返回一个新的不可变对象，因此是线程安全的
     *
     * @param extPtClass  扩展点的类型，不能为null
     * @param bizScenario 业务场景值，不能为null
     * @return 扩展坐标对象
     * @throws NullPointerException 如果extPtClass或bizScenario为null
     */
    public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario) {
        return new GXExtensionCoordinate(extPtClass, bizScenario);
    }

    /**
     * 获取扩展点的类型
     * <p>
     * 该方法返回扩展点接口的Class对象，用于在运行时获取扩展点的类型信息
     * <p>
     * 线程安全性：该方法不修改对象状态，只读取字段值，因此是线程安全的
     *
     * @param <T> 扩展点类型参数
     * @return 扩展点类型的Class对象，如果使用字符串构造方法创建的对象，可能返回null
     */
    @SuppressWarnings("all")
    public <T> Class<T> getExtensionPointClass() {
        return (Class<T>) extensionPointClass;
    }

    /**
     * 获取业务场景对象
     * <p>
     * 该方法返回业务场景对象，包含业务ID、用例和场景三个维度
     * <p>
     * 线程安全性：该方法不修改对象状态，只读取字段值，因此是线程安全的
     *
     * @return 业务场景对象，如果使用字符串构造方法创建的对象，可能返回null
     */
    public GXBizScenario getBizScenario() {
        return bizScenario;
    }
    
    /**
     * 获取扩展点名字
     * <p>
     * 该方法返回扩展点的完全限定类名，用于唯一标识一个扩展点接口
     * <p>
     * 线程安全性：该方法不修改对象状态，只读取字段值，因此是线程安全的
     *
     * @return 扩展点名字
     */
    public String getExtensionPointName() {
        return extensionPointName;
    }
    
    /**
     * 获取业务场景唯一标识
     * <p>
     * 该方法返回业务场景的唯一标识，格式为：bizId.useCase.scenario
     * <p>
     * 线程安全性：该方法不修改对象状态，只读取字段值，因此是线程安全的
     *
     * @return 业务场景唯一标识
     */
    public String getBizScenarioUniqueIdentity() {
        return bizScenarioUniqueIdentity;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((bizScenarioUniqueIdentity == null) ? 0 : bizScenarioUniqueIdentity.hashCode());
        result = prime * result + ((extensionPointName == null) ? 0 : extensionPointName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        GXExtensionCoordinate other = (GXExtensionCoordinate) obj;
        if (bizScenarioUniqueIdentity == null) {
            if (other.bizScenarioUniqueIdentity != null) return false;
        } else if (!bizScenarioUniqueIdentity.equals(other.bizScenarioUniqueIdentity)) {
            return false;
        }
        if (extensionPointName == null) {
            return other.extensionPointName == null;
        } else {
            return extensionPointName.equals(other.extensionPointName);
        }
    }

    @Override
    public String toString() {
        return "GXExtensionCoordinate [extensionPointName=" + extensionPointName + ", bizScenarioUniqueIdentity=" + bizScenarioUniqueIdentity + "]";
    }
}
