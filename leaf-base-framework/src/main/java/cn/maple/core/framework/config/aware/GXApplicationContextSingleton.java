package cn.maple.core.framework.config.aware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Objects;

@SuppressWarnings("all")
public enum GXApplicationContextSingleton {
    INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(GXApplicationContextSingleton.class);

    private volatile ApplicationContext applicationContext;

    GXApplicationContextSingleton() {
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        LOG.info("GXApplicationContextSingleton类设置ApplicationContext对象被调用");
        if (Objects.isNull(this.applicationContext)) {
            synchronized (this) {
                if (Objects.isNull(this.applicationContext)) {
                    this.applicationContext = applicationContext;
                    LOG.info("ApplicationContext已成功设置到GXApplicationContextSingleton");
                }
            }
        }
    }
}