package cn.maple.core.framework.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.event.GXExceptionNotifyEvent;
import cn.maple.core.framework.event.dto.GXExceptionNotifyEventDto;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Objects;

public interface GXBotNotificationExceptionService {
    @SuppressWarnings("unchecked")
    default void botNotificationException(Throwable throwable) {
        List<String> botNotificationException = getConfiguredExceptionNames();
        if (shouldNotify(throwable, botNotificationException)) {
            GXExceptionNotifyEventDto exceptionNotifyEventDto = new GXExceptionNotifyEventDto();
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
                RequestContextHolder.setRequestAttributes(servletRequestAttributes, true);
                exceptionNotifyEventDto.setHttpServletRequest(servletRequestAttributes.getRequest());
            } else {
                exceptionNotifyEventDto.setHttpServletRequest(GXCurrentRequestContextUtils.getHttpServletRequest());
            }
            exceptionNotifyEventDto.setThrowable(throwable);
            GXExceptionNotifyEvent exceptionNotifyEvent = new GXExceptionNotifyEvent(exceptionNotifyEventDto);
            GXEventPublisherUtils.publishEvent(exceptionNotifyEvent);
        }
    }

    @SuppressWarnings("unchecked")
    default List<String> getConfiguredExceptionNames() {
        String configuredValue = GXCommonUtils.getEnvironmentValue("bot.notification.exception", String.class, "");
        if (CharSequenceUtil.isNotBlank(configuredValue)) {
            return CharSequenceUtil.splitTrim(configuredValue, ",");
        }
        return GXCommonUtils.getEnvironmentValue("bot.notification.exception", List.class, CollUtil.newArrayList());
    }

    default boolean shouldNotify(Throwable throwable, List<String> configuredExceptionNames) {
        if (throwable == null || CollUtil.isEmpty(configuredExceptionNames)) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (matches(current.getClass(), configuredExceptionNames)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    default boolean matches(Class<?> exceptionClass, List<String> configuredExceptionNames) {
        Class<?> current = exceptionClass;
        while (current != null) {
            String canonicalName = current.getCanonicalName();
            if (Objects.nonNull(canonicalName) && CollUtil.contains(configuredExceptionNames, canonicalName)) {
                return true;
            }
            current = current.getSuperclass();
        }

        return matchesInterfaces(exceptionClass, configuredExceptionNames);
    }

    default boolean matchesInterfaces(Class<?> exceptionClass, List<String> configuredExceptionNames) {
        Class<?> current = exceptionClass;
        while (current != null) {
            for (Class<?> interfaceClass : current.getInterfaces()) {
                if (matchesInterface(interfaceClass, configuredExceptionNames)) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    default boolean matchesInterface(Class<?> interfaceClass, List<String> configuredExceptionNames) {
        String canonicalName = interfaceClass.getCanonicalName();
        if (Objects.nonNull(canonicalName) && CollUtil.contains(configuredExceptionNames, canonicalName)) {
            return true;
        }
        for (Class<?> parentInterface : interfaceClass.getInterfaces()) {
            if (matchesInterface(parentInterface, configuredExceptionNames)) {
                return true;
            }
        }
        return false;
    }
}
