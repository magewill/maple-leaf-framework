package cn.maple.core.framework.util.cglib;

import cn.hutool.core.map.WeakConcurrentMap;
import cn.hutool.core.util.StrUtil;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.core.Converter;

public enum GXBeanCopierCache {
    INSTANCE;

    private final WeakConcurrentMap<String, BeanCopier> cache = new WeakConcurrentMap<>();

    public BeanCopier get(final Class<?> srcClass, final Class<?> targetClass, final Converter converter) {
        if (srcClass == null || targetClass == null) {
            throw new NullPointerException("源类或目标类不能为null");
        }
        return get(srcClass, targetClass, null != converter);
    }

    public BeanCopier get(final Class<?> srcClass, final Class<?> targetClass, final boolean useConverter) {
        if (srcClass == null || targetClass == null) {
            throw new NullPointerException("源类或目标类不能为null");
        }
        final String key = genKey(srcClass, targetClass, useConverter);
        return cache.computeIfAbsent(key, (k) -> BeanCopier.create(srcClass, targetClass, useConverter));
    }
    
    private String genKey(Class<?> srcClass, Class<?> targetClass, boolean useConverter) {
        final StringBuilder key = StrUtil.builder()
                .append(srcClass.getName())
                .append('#').append(targetClass.getName())
                .append('#').append(useConverter ? 1 : 0);
        return key.toString();
    }
}
