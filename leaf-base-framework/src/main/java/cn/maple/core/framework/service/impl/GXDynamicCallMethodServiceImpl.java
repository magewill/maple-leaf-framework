package cn.maple.core.framework.service.impl;

import cn.maple.core.framework.service.GXDynamicCallMethodService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 动态方法调用服务实现类
 * <p>
 * 该服务提供了通过反射机制动态调用Spring容器中Bean的方法的功能。
 * 可以通过类名和方法名，或者直接通过目标对象和方法名进行调用。
 * 主要用于需要在运行时根据配置或条件动态决定调用哪个服务的哪个方法的场景。
 * </p>
 *
 * @author britton
 */
@Service
@Slf4j
public class GXDynamicCallMethodServiceImpl implements GXDynamicCallMethodService {
    /**
     * 通过类名和方法名动态调用服务方法
     * <p>
     * 首先通过类名获取Class对象，然后从Spring容器中获取对应的Bean实例，
     * 最后调用指定的方法。如果类不存在或调用过程中发生异常，将记录错误日志并返回null。
     * </p>
     *
     * @param serviceClassName 服务类的全限定名
     * @param methodName       要调用的方法名
     * @param parameters       方法参数列表
     * @return 方法调用结果，如果调用失败则返回null
     */
    @Override
    public Object call(String serviceClassName, String methodName, Object... parameters) {
        if (Objects.isNull(serviceClassName) || Objects.isNull(methodName)) {
            log.error("动态调用方法失败：类名或方法名不能为空");
            return null;
        }

        try {
            final Class<?> aClass = Class.forName(serviceClassName);
            final Object bean = GXSpringContextUtils.getBean(aClass);
            if (Objects.isNull(bean)) {
                log.error("动态调用方法失败：无法从Spring容器获取类型为{}的Bean", serviceClassName);
                return null;
            }
            return call(bean, methodName, parameters);
        } catch (ClassNotFoundException e) {
            log.error("动态调用方法失败：{}类不存在，异常信息：{}", serviceClassName, e.getMessage());
        } catch (Exception e) {
            log.error("动态调用方法失败：调用{}类的{}方法时发生异常，异常信息：{}", serviceClassName, methodName, e.getMessage());
        }
        return null;
    }

    /**
     * 通过目标对象和方法名动态调用方法
     * <p>
     * 直接在给定的目标对象上调用指定的方法。这个方法是上面方法的底层实现，
     * 当已经有目标对象实例时可以直接使用此方法。
     * </p>
     *
     * @param target     目标对象实例
     * @param methodName 要调用的方法名
     * @param parameters 方法参数列表
     * @return 方法调用结果，如果调用失败则返回null
     */
    @Override
    public Object call(Object target, String methodName, Object... parameters) {
        if (Objects.isNull(target) || Objects.isNull(methodName)) {
            log.error("动态调用方法失败：目标对象或方法名不能为空");
            return null;
        }

        try {
            return GXCommonUtils.reflectCallObjectMethod(target, methodName, parameters);
        } catch (Exception e) {
            log.error("动态调用方法失败：调用{}对象的{}方法时发生异常，异常信息：{}",
                    target.getClass().getName(), methodName, e.getMessage());
            return null;
        }
    }
}
