package cn.maple.dubbo.zk.config;

import cn.hutool.core.text.CharSequenceUtil;
import org.jspecify.annotations.Nullable;
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
 * {@code dubbo.provider.filter=dubboServerTraceIdFilter,dubboExceptionFilter,-exception}.
 *
 * <p>The check is advisory: it logs an ERROR but never blocks startup, so existing deployments are
 * not broken by an upgrade.
 */
@Component
public class GXDubboProviderFilterValidator implements SmartInitializingSingleton {
    private static final Logger LOG = LoggerFactory.getLogger(GXDubboProviderFilterValidator.class);

    static final String PROVIDER_FILTER_KEY = "dubbo.provider.filter";
    static final String REQUIRED_FILTER = "dubboServerTraceIdFilter,dubboExceptionFilter,-exception";

    private final Environment environment;

    public GXDubboProviderFilterValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String filter = environment.getProperty(PROVIDER_FILTER_KEY, "");
        String warning = buildWarningMessage(filter);
        if (warning != null) {
            LOG.error(warning);
        }
    }

    static boolean isBuiltInExceptionFilterExcluded(String filter) {
        return CharSequenceUtil.isNotBlank(filter) && CharSequenceUtil.contains(filter, "-exception");
    }

    /**
     * Builds the advisory message, or returns {@code null} when the wiring is correct. Package-private
     * so the decision can be verified without capturing logs.
     */
    static @Nullable String buildWarningMessage(String filter) {
        if (isBuiltInExceptionFilterExcluded(filter)) {
            return null;
        }
        return "DUBBO FILTER: built-in ExceptionFilter is active; GXBusinessException code/msg/data may be lost. "
                + "Configure " + PROVIDER_FILTER_KEY + "=" + REQUIRED_FILTER + " (current=" + filter + ")";
    }
}
