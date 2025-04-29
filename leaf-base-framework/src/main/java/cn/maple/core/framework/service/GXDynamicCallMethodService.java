package cn.maple.core.framework.service;

/**
 * 动态方法调用服务接口
 * <p>
 * 该接口提供了在运行时动态调用对象方法的能力，支持通过类名和方法名，或者直接通过目标对象和方法名进行调用。
 * 主要用于需要在运行时根据配置或条件动态决定调用哪个服务的哪个方法的场景，增强了系统的灵活性和可扩展性。
 * </p>
 * <p>
 * 使用该服务时应注意方法调用的安全性，避免调用不安全的方法或传入不合适的参数，以防止安全风险。
 * </p>
 */
public interface GXDynamicCallMethodService {
    /**
     * 通过类名和方法名动态调用服务方法
     * <p>
     * 根据提供的服务类名获取对应的Bean实例，然后调用指定的方法。
     * 该方法适用于需要在运行时根据配置决定调用哪个服务的场景。
     * </p>
     *
     * @param serviceClassName 服务类的全限定名，不能为null
     * @param methodName       要调用的方法名，不能为null
     * @param parameters       方法参数列表，可变参数，可以为空
     * @return 方法调用结果，如果调用失败则返回null
     */
    Object call(String serviceClassName, String methodName, Object... parameters);

    /**
     * 通过目标对象和方法名动态调用方法
     * <p>
     * 直接在给定的目标对象上调用指定的方法。
     * 该方法适用于已经有目标对象实例的场景，比第一个方法更直接高效。
     * </p>
     *
     * @param target     目标对象实例，不能为null
     * @param methodName 要调用的方法名，不能为null
     * @param parameters 方法参数列表，可变参数，可以为空
     * @return 方法调用结果，如果调用失败则返回null
     */
    Object call(Object target, String methodName, Object... parameters);
}
