package cn.maple.dubbo.zk.processor;

import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.api.GXBaseServeApi;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.ServiceBean;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;

/**
 * Binds a Dubbo RPC API implementation to its generic service class after ServiceBean initialization.
 *
 * <p>Requires component scanning of {@code cn.maple} (no auto-configuration is shipped by this
 * module), and depends on {@code GXBaseServeApi#staticBindServeServiceClass(Class)} being present on
 * the ref implementation. Binding failures are logged but never block startup.
 */
@Component
@Slf4j
public class GXDubboRpcApiBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ServiceBean<?> serviceBean) {
            Object realRpcApiTarget = serviceBean.getRef();
            if (ObjectUtil.isNull(realRpcApiTarget)) {
                log.warn("DUBBO RPC API: ServiceBean [{}] ref is null, skip binding", beanName);
                return bean;
            }

            Class<?> targetService;
            try {
                targetService = GXCommonUtils.getGenericClassType(realRpcApiTarget.getClass(), 0);
            } catch (IllegalArgumentException e) {
                log.warn("DUBBO RPC API: [{}] failed to resolve generic service type, skip binding", realRpcApiTarget.getClass().getName(), e);
                return bean;
            }
            if (ObjectUtil.isNull(targetService)) {
                log.warn("DUBBO RPC API: [{}] has no generic service type, skip binding", realRpcApiTarget.getClass().getName());
                return bean;
            }

            String[] beanNames = getBeanNamesForType(targetService);
            if (ObjectUtil.isEmpty(beanNames)) {
                log.warn("DUBBO RPC API: Spring Bean [{}] was not found, skip binding", targetService.getName());
                return bean;
            }
            if (beanNames.length > 1) {
                log.info("DUBBO RPC API: Spring Bean [{}] has multiple candidates {}, bind by service class only",
                        targetService.getName(), Arrays.toString(beanNames));
            }

            GXCommonUtils.reflectCallObjectMethod(realRpcApiTarget, "staticBindServeServiceClass", targetService);
            logBindingResult(realRpcApiTarget, targetService);
        }
        return bean;
    }

    /**
     * Verifies the binding actually landed. {@code staticBindServeServiceClass} returns {@code void}
     * and the reflective helper swallows failures, so the binding is re-read from the API instead.
     * When the API does not expose the getter the binding cannot be verified and only an INFO is logged.
     */
    private void logBindingResult(Object realRpcApiTarget, Class<?> targetService) {
        Class<?> boundService = resolveBoundService(realRpcApiTarget);
        if (Objects.isNull(boundService)) {
            log.info("DUBBO RPC API: [{}] uses service [{}]",
                    realRpcApiTarget.getClass().getSimpleName(), targetService.getSimpleName());
            return;
        }
        if (Objects.equals(targetService, boundService)) {
            log.info("DUBBO RPC API: [{}] uses service [{}]",
                    realRpcApiTarget.getClass().getSimpleName(), targetService.getSimpleName());
            return;
        }
        log.warn("DUBBO RPC API: binding not effective for [{}], expected [{}] but got [{}]",
                realRpcApiTarget.getClass().getSimpleName(), targetService.getName(), boundService.getName());
    }

    private @Nullable Class<?> resolveBoundService(Object realRpcApiTarget) {
        if (realRpcApiTarget instanceof GXBaseServeApi serveApi) {
            return serveApi.getServeServiceClass();
        }
        Object bound = GXCommonUtils.reflectCallObjectMethod(realRpcApiTarget, "getServeServiceClass");
        return bound instanceof Class<?> clazz ? clazz : null;
    }

    private String[] getBeanNamesForType(Class<?> targetService) {
        ApplicationContext applicationContext = GXSpringContextUtils.getApplicationContext();
        if (Objects.isNull(applicationContext)) {
            return new String[0];
        }
        try {
            return applicationContext.getBeanNamesForType(targetService, false, false);
        } catch (BeansException e) {
            log.warn("DUBBO RPC API: failed to find Spring Bean names for [{}], skip binding", targetService.getName(), e);
            return new String[0];
        }
    }
}
