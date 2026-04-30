package cn.maple.core.framework.service;

import cn.hutool.core.collection.CollUtil;
import cn.maple.core.framework.event.GXExceptionNotifyEvent;
import cn.maple.core.framework.event.dto.GXExceptionNotifyEventDto;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

public interface GXBotNotificationExceptionService {
    @SuppressWarnings("unchecked")
    default void botNotificationException(Throwable throwable) {
        List<String> botNotificationException = GXCommonUtils.getEnvironmentValue("bot.notification.exception", List.class, CollUtil.newArrayList());
        String throwableClassCanonicalName = throwable.getClass().getCanonicalName();
        if (CollUtil.contains(botNotificationException, throwableClassCanonicalName)) {
            GXExceptionNotifyEventDto exceptionNotifyEventDto = new GXExceptionNotifyEventDto();
            ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            RequestContextHolder.setRequestAttributes(servletRequestAttributes, true);
            exceptionNotifyEventDto.setThrowable(throwable);
            exceptionNotifyEventDto.setHttpServletRequest(GXCurrentRequestContextUtils.getHttpServletRequest());
            GXExceptionNotifyEvent exceptionNotifyEvent = new GXExceptionNotifyEvent(exceptionNotifyEventDto);
            GXEventPublisherUtils.publishEvent(exceptionNotifyEvent);
        }
    }
}
