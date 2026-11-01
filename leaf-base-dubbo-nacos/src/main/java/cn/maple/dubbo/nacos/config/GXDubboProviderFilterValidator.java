package cn.maple.dubbo.nacos.config;

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
 * {@code code/msg/data}; deployments must exclude it and enable the module filter:
 * {@code dubbo.provider.filter=dubboExceptionFilter,-exception}.
 *
 * <p>The check is advisory: it logs an ERROR but never blocks startup, so existing deployments are
 * not broken by an upgrade.
 */
@Component
public class GXDubboProviderFilterValidator implements SmartInitializingSingleton {
    private static final Logger LOG = LoggerFactory.getLogger(GXDubboProviderFilterValidator.class);

    static final String PROVIDER_FILTER_KEY = "dubbo.provider.filter";
    private static final String CUSTOM_FILTER = "dubboExceptionFilter";
    private static final String BUILT_IN_EXCLUSION = "-exception";
    private static final String REQUIRED_FILTER = CUSTOM_FILTER + "," + BUILT_IN_EXCLUSION;

    private final Environment environment;

    public GXDubboProviderFilterValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String filter = environment.getProperty(PROVIDER_FILTER_KEY, "");
        boolean excluded = isBuiltInExceptionFilterExcluded(filter);
        boolean customEnabled = isCustomExceptionFilterEnabled(filter);
        if (excluded && customEnabled) {
            return;
        }
        LOG.error("DUBBO FILTER: provider filter wiring is incomplete; GXBusinessException code/msg/data may be lost "
                        + "or stay un-normalized. Configure {}={} (current={}, builtInExceptionFilterExcluded={}, customExceptionFilterEnabled={})",
                PROVIDER_FILTER_KEY, REQUIRED_FILTER, filter, excluded, customEnabled);
    }

    /**
     * {@code -exception} must be a comma-separated token of its own: a filter merely named like
     * {@code my-exception-filter} does not remove the built-in filter.
     */
    static boolean isBuiltInExceptionFilterExcluded(@Nullable String filter) {
        return containsToken(filter, BUILT_IN_EXCLUSION);
    }

    /**
     * Without the module filter the provider throws raw exceptions to the consumer: nothing converts
     * Sentinel blocks into {@code GXSentinelFlowException} and nothing hides provider internals.
     */
    static boolean isCustomExceptionFilterEnabled(@Nullable String filter) {
        return containsToken(filter, CUSTOM_FILTER);
    }

    /**
     * Blank/null input yields {@code false}; {@link CharSequenceUtil#splitTrim} is null safe, so an
     * absent property never throws here.
     */
    private static boolean containsToken(@Nullable String filter, String token) {
        return CharSequenceUtil.splitTrim(filter, ',').contains(token);
    }
}
