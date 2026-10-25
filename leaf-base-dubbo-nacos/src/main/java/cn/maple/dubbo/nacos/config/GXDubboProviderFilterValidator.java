package cn.maple.dubbo.nacos.config;

import cn.hutool.core.text.CharSequenceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Startup check for the provider filter wiring.
 *
 * <p>Dubbo's built-in {@code ExceptionFilter} wraps undeclared runtime exceptions (including
 * {@code GXBusinessException}) into a plain {@code RuntimeException}, which drops the business
 * {@code code/msg/data}. Deployments must exclude it:
 * {@code dubbo.provider.filter=dubboExceptionFilter,-exception}.
 *
 * <p>The check is advisory: it logs an ERROR but never blocks startup, so existing deployments are
 * not broken by an upgrade.
 */
@Component
public class GXDubboProviderFilterValidator implements SmartInitializingSingleton {
    private static final Logger LOG = LoggerFactory.getLogger(GXDubboProviderFilterValidator.class);

    static final String PROVIDER_FILTER_KEY = "dubbo.provider.filter";
    static final String REQUIRED_FILTER = "dubboExceptionFilter,-exception";

    private final Environment environment;

    public GXDubboProviderFilterValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String filter = environment.getProperty(PROVIDER_FILTER_KEY, "");
        if (!isBuiltInExceptionFilterExcluded(filter)) {
            LOG.error("DUBBO FILTER: built-in ExceptionFilter is active; GXBusinessException code/msg/data may be lost. "
                    + "Configure {}={} (current={})", PROVIDER_FILTER_KEY, REQUIRED_FILTER, filter);
        }
    }

    static boolean isBuiltInExceptionFilterExcluded(String filter) {
        return CharSequenceUtil.isNotBlank(filter) && CharSequenceUtil.contains(filter, "-exception");
    }
}
