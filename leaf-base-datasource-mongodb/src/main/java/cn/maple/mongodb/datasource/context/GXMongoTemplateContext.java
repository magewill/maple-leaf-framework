package cn.maple.mongodb.datasource.context;

import cn.hutool.core.text.CharSequenceUtil;

import java.util.ArrayDeque;
import java.util.Deque;

public final class GXMongoTemplateContext {
    // Do not use InheritableThreadLocal here: executor threads can inherit stale datasource names when they are lazily created.
    private static final ThreadLocal<Deque<String>> MONGO_TEMPLATE_BEAN_NAMES = new ThreadLocal<>();

    private GXMongoTemplateContext() {
    }

    public static String peek() {
        Deque<String> beanNames = MONGO_TEMPLATE_BEAN_NAMES.get();
        return beanNames == null || beanNames.isEmpty() ? null : beanNames.peek();
    }

    public static void push(String beanName) {
        Deque<String> beanNames = MONGO_TEMPLATE_BEAN_NAMES.get();
        if (beanNames == null) {
            beanNames = new ArrayDeque<>();
            MONGO_TEMPLATE_BEAN_NAMES.set(beanNames);
        }
        beanNames.push(CharSequenceUtil.nullToEmpty(beanName));
    }

    public static void pop() {
        Deque<String> beanNames = MONGO_TEMPLATE_BEAN_NAMES.get();
        if (beanNames == null || beanNames.isEmpty()) {
            MONGO_TEMPLATE_BEAN_NAMES.remove();
            return;
        }
        beanNames.pop();
        if (beanNames.isEmpty()) {
            MONGO_TEMPLATE_BEAN_NAMES.remove();
        }
    }

    public static void clear() {
        MONGO_TEMPLATE_BEAN_NAMES.remove();
    }

    public static Snapshot capture() {
        Deque<String> beanNames = MONGO_TEMPLATE_BEAN_NAMES.get();
        return new Snapshot(beanNames == null ? null : new ArrayDeque<>(beanNames));
    }

    public static Snapshot replaceWith(Snapshot snapshot) {
        Snapshot previous = capture();
        if (snapshot == null || snapshot.beanNames == null || snapshot.beanNames.isEmpty()) {
            MONGO_TEMPLATE_BEAN_NAMES.remove();
        } else {
            MONGO_TEMPLATE_BEAN_NAMES.set(new ArrayDeque<>(snapshot.beanNames));
        }
        return previous;
    }

    public static final class Snapshot {
        private final Deque<String> beanNames;

        private Snapshot(Deque<String> beanNames) {
            this.beanNames = beanNames;
        }
    }
}
