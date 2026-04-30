package cn.maple.core.framework.annotation;

import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.res.GXBaseResDto;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface GXCacheable {
    Class<? extends GXBaseResDto> retType() default GXBaseResDto.class;

    String methodName() default GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;

    String cacheKey() default "";
}
