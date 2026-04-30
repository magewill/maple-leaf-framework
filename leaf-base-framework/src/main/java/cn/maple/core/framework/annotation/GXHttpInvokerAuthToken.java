package cn.maple.core.framework.annotation;

import cn.maple.core.framework.constant.GXHttpInvokerConstant;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXHttpInvokerAuthToken {
    String value() default GXHttpInvokerConstant.FEIGN_INVOKER;
}
