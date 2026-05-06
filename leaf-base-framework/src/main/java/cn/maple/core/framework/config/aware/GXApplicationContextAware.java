package cn.maple.core.framework.config.aware;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GXApplicationContextAware implements ApplicationContextAware, ApplicationListener<ContextClosedEvent> {
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
    }

    @Override
    public void onApplicationEvent(@NonNull ContextClosedEvent event) {
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext(event.getApplicationContext());
    }
}
