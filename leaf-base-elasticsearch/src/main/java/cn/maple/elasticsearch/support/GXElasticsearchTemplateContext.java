package cn.maple.elasticsearch.support;

import cn.hutool.core.text.CharSequenceUtil;

import java.util.function.Supplier;

/**
 * Holds the current ElasticsearchTemplate bean name for the executing thread.
 */
public final class GXElasticsearchTemplateContext {
    private static final ThreadLocal<String> TEMPLATE_NAME_CONTEXT = new ThreadLocal<>();

    private GXElasticsearchTemplateContext() {
    }

    public static String getTemplateName() {
        return TEMPLATE_NAME_CONTEXT.get();
    }

    public static void setTemplateName(String templateName) {
        if (CharSequenceUtil.isBlank(templateName)) {
            TEMPLATE_NAME_CONTEXT.remove();
        } else {
            TEMPLATE_NAME_CONTEXT.set(templateName);
        }
    }

    public static void clear() {
        TEMPLATE_NAME_CONTEXT.remove();
    }

    public static <R> R withTemplateName(String templateName, Supplier<R> supplier) {
        String previousTemplateName = getTemplateName();
        setTemplateName(templateName);
        try {
            return supplier.get();
        } finally {
            setTemplateName(previousTemplateName);
        }
    }

    public static <R> Supplier<R> wrap(Supplier<R> supplier) {
        String capturedTemplateName = getTemplateName();
        return () -> withTemplateName(capturedTemplateName, supplier);
    }

    public static Runnable wrap(Runnable runnable) {
        String capturedTemplateName = getTemplateName();
        return () -> withTemplateName(capturedTemplateName, () -> {
            runnable.run();
            return null;
        });
    }
}
