package cn.maple.core.framework.service;

import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXBotNotificationExceptionServiceTest {
    private final GXBotNotificationExceptionService service = new GXBotNotificationExceptionService() {
    };

    @Test
    void shouldNotifyWhenExactExceptionClassConfigured() {
        boolean notify = service.shouldNotify(new IllegalArgumentException("bad request"),
                List.of(IllegalArgumentException.class.getCanonicalName()));

        assertTrue(notify);
    }

    @Test
    void shouldNotifyWhenSuperclassConfigured() {
        boolean notify = service.shouldNotify(new GXBusinessException("biz error"),
                List.of(RuntimeException.class.getCanonicalName()));

        assertTrue(notify);
    }

    @Test
    void shouldNotifyWhenCauseClassConfigured() {
        boolean notify = service.shouldNotify(new IllegalStateException("wrapper", new GXBusinessException("biz error")),
                List.of(GXBusinessException.class.getCanonicalName()));

        assertTrue(notify);
    }

    @Test
    void shouldNotNotifyWhenConfigurationIsEmptyOrUnmatched() {
        assertFalse(service.shouldNotify(new IllegalArgumentException("bad request"), List.of()));
        assertFalse(service.shouldNotify(new IllegalArgumentException("bad request"),
                List.of(IllegalStateException.class.getCanonicalName())));
    }
}
