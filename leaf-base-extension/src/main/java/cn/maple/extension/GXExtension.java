package cn.maple.extension;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks a {@link GXExtensionPoint} implementation for one business scenario.
 * <p>
 * The annotation is repeatable; use {@link GXExtensions} when declaring many scenarios.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(GXExtensions.class)
@Component
public @interface GXExtension {
    /**
     * Business domain id.
     */
    String bizId() default GXBizScenario.DEFAULT_BIZ_ID;

    /**
     * Use case under the business domain.
     */
    String useCase() default GXBizScenario.DEFAULT_USE_CASE;

    /**
     * Scenario under the use case.
     */
    String scenario() default GXBizScenario.DEFAULT_SCENARIO;
}
