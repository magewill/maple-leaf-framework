package cn.maple.core.framework.config.aware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

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
        if (applicationContext == null) {
            throw new IllegalArgumentException("ApplicationContext must not be null");
        }
        synchronized (this) {
            ApplicationContext currentContext = this.applicationContext;
            if (currentContext == applicationContext) {
                LOG.debug("ApplicationContext is already registered: id={}", applicationContext.getId());
                return;
            }
            if (currentContext != null && isActive(currentContext)) {
                LOG.debug("ApplicationContext already exists, keep current context: currentId={}, ignoredId={}",
                        currentContext.getId(), applicationContext.getId());
                return;
            }
            this.applicationContext = applicationContext;
            LOG.info("ApplicationContext registered: id={}", applicationContext.getId());
        }
    }

    public void clearApplicationContext(ApplicationContext applicationContext) {
        synchronized (this) {
            if (this.applicationContext != applicationContext) {
                return;
            }
            LOG.info("ApplicationContext cleared: id={}", applicationContext.getId());
            this.applicationContext = null;
        }
    }

    public void clearApplicationContext() {
        synchronized (this) {
            ApplicationContext currentContext = this.applicationContext;
            if (currentContext == null) {
                return;
            }
            LOG.info("ApplicationContext cleared: id={}", currentContext.getId());
            this.applicationContext = null;
        }
    }

    private boolean isActive(ApplicationContext context) {
        if (context instanceof ConfigurableApplicationContext configurableApplicationContext) {
            return configurableApplicationContext.isActive();
        }
        return true;
    }
}
