package cn.maple.core.framework.config.aware;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXApplicationContextSingletonTest {
    private ApplicationContext originalApplicationContext;

    @BeforeEach
    void setUp() {
        originalApplicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
    }

    @AfterEach
    void tearDown() {
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
        if (originalApplicationContext != null) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(originalApplicationContext);
        }
    }

    @Test
    void setApplicationContextRejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> GXApplicationContextSingleton.INSTANCE.setApplicationContext(null));
    }

    @Test
    void setApplicationContextKeepsActiveCurrentContext() {
        GenericApplicationContext firstContext = newContext("first");
        GenericApplicationContext secondContext = newContext("second");
        try {
            firstContext.refresh();
            secondContext.refresh();

            GXApplicationContextSingleton.INSTANCE.setApplicationContext(firstContext);
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(secondContext);

            assertSame(firstContext, GXApplicationContextSingleton.INSTANCE.getApplicationContext());
        } finally {
            secondContext.close();
            firstContext.close();
        }
    }

    @Test
    void setApplicationContextReplacesInactiveCurrentContext() {
        GenericApplicationContext firstContext = newContext("first");
        GenericApplicationContext secondContext = newContext("second");
        try {
            firstContext.refresh();
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(firstContext);
            firstContext.close();

            secondContext.refresh();
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(secondContext);

            assertSame(secondContext, GXApplicationContextSingleton.INSTANCE.getApplicationContext());
        } finally {
            secondContext.close();
        }
    }

    @Test
    void clearApplicationContextOnlyClearsMatchingContext() {
        GenericApplicationContext firstContext = newContext("first");
        GenericApplicationContext secondContext = newContext("second");
        try {
            firstContext.refresh();
            secondContext.refresh();
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(firstContext);

            GXApplicationContextSingleton.INSTANCE.clearApplicationContext(secondContext);
            assertSame(firstContext, GXApplicationContextSingleton.INSTANCE.getApplicationContext());

            GXApplicationContextSingleton.INSTANCE.clearApplicationContext(firstContext);
            assertNull(GXApplicationContextSingleton.INSTANCE.getApplicationContext());
        } finally {
            secondContext.close();
            firstContext.close();
        }
    }

    @Test
    void awareRegistersAndClearsContextOnClosedEvent() {
        GXApplicationContextAware aware = new GXApplicationContextAware();
        GenericApplicationContext context = newContext("aware");
        try {
            context.refresh();
            aware.setApplicationContext(context);

            assertSame(context, GXApplicationContextSingleton.INSTANCE.getApplicationContext());

            aware.onApplicationEvent(new ContextClosedEvent(context));
            assertNull(GXApplicationContextSingleton.INSTANCE.getApplicationContext());
        } finally {
            context.close();
        }
    }

    private GenericApplicationContext newContext(String id) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setId(id);
        return context;
    }
}
