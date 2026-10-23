package cn.maple.retry.config;

import cn.maple.core.framework.config.aware.GXApplicationContextAware;
import cn.maple.retry.listener.GXRetryListener;
import cn.maple.retry.util.GXRetryUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.resilience.annotation.Retryable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = GXRetrySpringBootTest.TestApp.class)
class GXRetrySpringBootTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RetryableService retryableService;

    @Autowired
    @Qualifier("retryTemplate")
    private RetryTemplate qualifiedRetryTemplate;

    @Test
    void retryTemplateBeanIsRegisteredExactlyOnce() {
        assertEquals(1, applicationContext.getBeansOfType(RetryTemplate.class).size());
        assertNotNull(applicationContext.getBean(GXRetryConfig.class));
        assertNotNull(applicationContext.getBean(GXRetryListener.class));
    }

    @Test
    void retryTemplateBeanUsesInjectedListener() {
        RetryTemplate retryTemplate = applicationContext.getBean(RetryTemplate.class);
        GXRetryListener listener = applicationContext.getBean(GXRetryListener.class);

        assertEquals(listener, retryTemplate.getRetryListener());
    }

    @Test
    void retryTemplateCanBeInjectedByQualifierName() {
        assertNotNull(qualifiedRetryTemplate);
        assertEquals(applicationContext.getBean("retryTemplate"), qualifiedRetryTemplate);
    }

    @Test
    void retryableAnnotationUsesItsOwnMaxRetriesInsteadOfModuleDefaults() {
        retryableService.reset();

        assertEquals("ok", retryableService.run());
        assertEquals(2, retryableService.getInvocations(),
                "@Retryable(maxRetries=1) must produce 2 attempts, independent of GXRetryConfig defaults");
    }

    @Test
    void configCanBeImportedWithoutListenerBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXRetryConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RetryTemplate.class);
                });
    }

    @Test
    void customRetryTemplateBeanCanCoexistAndIsSelectableByPrimary() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXRetryConfig.class, GXRetryListener.class, CustomTemplateConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    Map<String, RetryTemplate> templates = context.getBeansOfType(RetryTemplate.class);
                    assertThat(templates).containsKey("customRetryTemplate");
                    assertThat(context.getBean(RetryTemplate.class)).isSameAs(templates.get("customRetryTemplate"));
                });
    }

    @Test
    void gxRetryUtilDoesNotUseContainerRetryTemplateBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXApplicationContextAware.class, GXRetryConfig.class,
                        GXRetryListener.class, CustomTemplateConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AtomicInteger attempts = new AtomicInteger(0);

                    assertThrows(IllegalStateException.class, () -> GXRetryUtil.retryOperation(retryContext -> {
                        attempts.incrementAndGet();
                        throw new IllegalStateException("always fail");
                    }, 3, 100L));

                    assertEquals(3, attempts.get(),
                            "GXRetryUtil builds its own template (3 attempts) and ignores the custom bean (maxRetries=1)");
                });
    }

    @SpringBootConfiguration
    @Import({GXRetryConfig.class, GXRetryListener.class})
    static class TestApp {

        @Bean
        RetryableService retryableService() {
            return new RetryableService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTemplateConfig {

        @Bean
        @Primary
        RetryTemplate customRetryTemplate() {
            return new RetryTemplate(RetryPolicy.withMaxRetries(1));
        }
    }

    static class RetryableService {
        private final AtomicInteger invocations = new AtomicInteger();

        @Retryable(maxRetries = 1)
        public String run() {
            if (invocations.incrementAndGet() < 2) {
                throw new IllegalStateException("temporary failure");
            }
            return "ok";
        }

        int getInvocations() {
            return invocations.get();
        }

        void reset() {
            invocations.set(0);
        }
    }
}
