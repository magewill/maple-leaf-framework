package cn.maple.rabbitmq.config;

import cn.maple.rabbitmq.GXRabbitMQTestApplication;
import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code maple.framework.rabbitmq.scheduler.expose=false} 时不应污染容器默认调度器，
 * 但 {@link AsyncRabbitTemplate} 仍需拿到模块内部调度器。
 */
@SpringBootTest(classes = GXRabbitMQTestApplication.class,
        properties = {
                "spring.rabbitmq.host=localhost",
                "spring.rabbitmq.port=5672",
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "maple.framework.rabbitmq.scheduler.expose=false"
        })
class GXRabbitMQSchedulerExposeDisabledTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void shouldNotExposeTaskSchedulerBean() {
        assertThat(context.getBeanNamesForType(TaskScheduler.class)).isEmpty();
    }

    @Test
    void shouldStillProvideSchedulerToAsyncTemplate() {
        AsyncRabbitTemplate asyncRabbitTemplate = context.getBean(AsyncRabbitTemplate.class);

        assertThat(ReflectionTestUtils.getField(asyncRabbitTemplate, "taskScheduler")).isNotNull();
    }

    @Test
    void shouldNotRegisterRabbitTaskSchedulerBean() {
        assertThat(context.containsBean("rabbitTaskScheduler")).isFalse();
    }

    @Test
    void shouldShutDownInternalSchedulerAfterContextClose() throws Exception {
        new ApplicationContextRunner()
                .withBean(ConnectionFactory.class, () -> Mockito.mock(ConnectionFactory.class))
                .withBean(GXRabbitMQProperties.class, GXRabbitMQProperties::new)
                .withUserConfiguration(GXRabbitMQConfig.class)
                .withPropertyValues("maple.framework.rabbitmq.scheduler.expose=false")
                .run(runnerContext -> {
                    AsyncRabbitTemplate template = runnerContext.getBean(AsyncRabbitTemplate.class);
                    TaskScheduler scheduler = (TaskScheduler) ReflectionTestUtils.getField(template, "taskScheduler");
                    assertThat(scheduler).isNotNull();

                    CountDownLatch latch = new CountDownLatch(1);
                    scheduler.schedule(latch::countDown, Instant.now());
                    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                });

        assertThat(awaitNoThreadWithPrefix("maple-rabbit-scheduler-", 3000L)).isTrue();
    }

    private static boolean awaitNoThreadWithPrefix(String prefix, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            boolean alive = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.isAlive() && thread.getName().startsWith(prefix));
            if (!alive) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }
}
