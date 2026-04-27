package cn.maple.dubbo.nacos.processor;

import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.dubbo.config.spring.ServiceBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Dubbo RPC API Bean后处理器，用于处理标注了@DubboService的Bean
 * <p>
 * 该处理器的主要职责：
 * 1. 识别Dubbo框架中的ServiceBean实例
 * 2. 从ServiceBean中获取实际的RPC API实现类
 * 3. 通过泛型参数获取该API应该绑定的具体业务服务类
 * 4. 将业务服务类绑定到RPC API实现类，实现自动委派调用
 * </p>
 * <p>
 * 工作原理：
 * 框架提供统一的通用RPC接口，这些接口需要有具体的业务服务类来处理实际逻辑。
 * 本处理器通过反射和泛型分析，自动将RPC接口与对应的业务服务类进行绑定，
 * 当外部系统调用RPC接口时，请求会自动委派给绑定的业务服务类处理。
 * </p>
 * <p>
 * 线程安全性说明：
 * - 本处理器仅在Spring容器初始化Bean时执行，属于容器启动阶段的单线程操作
 * - 处理完成后不再持有任何状态，不存在并发访问的线程安全问题
 * - 反射调用过程中使用的是线程安全的Spring容器方法
 * </p>
 * <p>
 * 内存安全性说明：
 * - 不会创建长期存活的对象引用，不会导致内存泄漏
 * - 所有对象引用都是临时的，会随着方法调用结束而被释放
 * - 通过Spring容器管理的Bean生命周期，确保资源的正确释放
 * </p>
 * 
 * @author gapleaf@163.com
 */
@Component
@Log4j2
public class GXDubboRpcApiBeanPostProcessor implements BeanPostProcessor {
    /**
     * Bean初始化前的处理方法
     * <p>
     * 本实现直接返回原始Bean，不做任何处理
     * </p>
     *
     * @param bean     待处理的Bean实例
     * @param beanName Bean的名称
     * @return 原始Bean实例
     * @throws BeansException Spring Bean处理异常
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * Bean初始化后的处理方法，用于识别和处理Dubbo的ServiceBean
     * <p>
     * 该方法的主要职责：
     * 1. 识别Dubbo的ServiceBean类型的Bean
     * 2. 获取ServiceBean中的实际RPC API实现类
     * 3. 通过泛型分析获取该API应该绑定的业务服务类
     * 4. 从Spring容器中获取业务服务类的实例
     * 5. 通过反射调用API实现类的绑定方法，将业务服务类与API关联
     * </p>
     * <p>
     * 线程安全性说明：
     * - 该方法在Spring容器初始化阶段被调用，属于单线程操作
     * - 反射调用过程中使用的是线程安全的工具类方法
     * - 不会修改共享状态，不存在并发安全问题
     * </p>
     * <p>
     * 异常处理说明：
     * - 对于泛型分析失败的情况，会直接返回原始Bean，不进行绑定
     * - 对于业务服务类不存在的情况，会跳过绑定过程
     * - 反射调用异常由GXCommonUtils.reflectCallObjectMethod方法内部处理
     * </p>
     *
     * @param bean     待处理的Bean实例
     * @param beanName Bean的名称
     * @return 处理后的Bean实例（本实现中返回原始Bean）
     * @throws BeansException Spring Bean处理异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 1. 检查Bean是否为Dubbo的ServiceBean类型
        if (bean instanceof ServiceBean<?> serviceBean) {
            // 2. 获取ServiceBean中的实际RPC API实现类
            Object realRpcApiTarget = serviceBean.getRef();
            if (ObjectUtil.isNull(realRpcApiTarget)) {
                log.warn("DUBBO RPC API: ServiceBean [{}] ref为空,跳过服务绑定", beanName);
                return bean;
            }
            
            // 3. 通过泛型分析获取该API应该绑定的业务服务类
            // 约定：RPC API实现类的第一个泛型参数为需要绑定的业务服务类
            Class<?> targetService;
            try {
                targetService = GXCommonUtils.getGenericClassType(realRpcApiTarget.getClass(), 0);
            } catch (IllegalArgumentException e) {
                log.warn("DUBBO RPC API: [{}] 泛型服务类型解析失败,跳过服务绑定", realRpcApiTarget.getClass().getName(), e);
                return bean;
            }
            if (ObjectUtil.isNull(targetService)) {
                // 如果无法获取泛型类型，直接返回原始Bean
                log.warn("DUBBO RPC API: [{}] 未声明可绑定的泛型服务类型,跳过服务绑定", realRpcApiTarget.getClass().getName());
                return bean;
            }
            
            // 4. 从Spring容器中获取业务服务类的实例
            Object targetServiceObject;
            try {
                targetServiceObject = GXSpringContextUtils.getBean(targetService);
            } catch (BeansException e) {
                log.warn("DUBBO RPC API: 未找到 [{}] 对应的Spring Bean,跳过服务绑定", targetService.getName(), e);
                return bean;
            }
            if (ObjectUtil.isNotNull(targetServiceObject)) {
                // 5. 通过反射调用API实现类的绑定方法，将业务服务类与API关联
                // 约定：RPC API实现类必须有一个名为staticBindServeServiceClass的方法，用于接收业务服务类
                GXCommonUtils.reflectCallObjectMethod(realRpcApiTarget, "staticBindServeServiceClass", targetService);
                log.info("DUBBO RPC API: 《{}》使用的底层服务是《{}》", realRpcApiTarget.getClass().getSimpleName(), targetService.getSimpleName());
            }
        }
        return bean;
    }
}
