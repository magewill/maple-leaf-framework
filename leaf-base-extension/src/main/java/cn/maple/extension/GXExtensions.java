package cn.maple.extension;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Declares multiple {@link GXExtension} scenarios.
 * <p>
 * Use {@link #value()} for explicit coordinates, or {@link #bizId()}, {@link #useCase()} and
 * {@link #scenario()} to register their Cartesian product.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Component
public @interface GXExtensions {
    /**
     * Business domain ids used in Cartesian product registration.
     */
    String[] bizId() default GXBizScenario.DEFAULT_BIZ_ID;

    /**
     * Use cases used in Cartesian product registration.
     */
    String[] useCase() default GXBizScenario.DEFAULT_USE_CASE;

    /**
     * Scenarios used in Cartesian product registration.
     */
    String[] scenario() default GXBizScenario.DEFAULT_SCENARIO;

    /**
     * Explicit extension coordinates. When set, Cartesian product attributes are ignored.
     */
    GXExtension[] value() default {};
}
