package cn.maple.core.framework.annotation;

import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@ComponentScan(
        value = {"cn.maple"},
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.CUSTOM,
                        classes = AutoConfigurationExcludeFilter.class
                )
        }
)
//@Import({GXFrameworkConfig.class})
//@Order(Ordered.HIGHEST_PRECEDENCE)
public @interface GXEnableLeafFramework {
}
