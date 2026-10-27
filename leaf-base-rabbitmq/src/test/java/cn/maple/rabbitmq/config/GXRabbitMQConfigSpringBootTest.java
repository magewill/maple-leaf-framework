package cn.maple.rabbitmq.config;

import cn.maple.rabbitmq.GXRabbitMQTestApplication;
import cn.maple.rabbitmq.listener.impl.GXDefaultRabbitMQQueueListenerImpl;
import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GXRabbitMQTestApplication.class,
        properties = {
                "spring.rabbitmq.host=localhost",
                "spring.rabbitmq.port=5672",
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "maple.framework.mq.rabbitmq.enable=true",
                "maple.framework.mq.rabbitmq.default-queue-name=test.default.queue",
                "spring.rabbitmq.default-queue-name=test.default.queue"
        })
class GXRabbitMQConfigSpringBootTest {
    @Autowired
    private ApplicationContext context;

    @Autowired
    private GXRabbitMQProperties rabbitProperties;

    @Test
    void shouldExposeCoreBeans() {
        assertThat(context.getBean(RabbitTemplate.class)).isNotNull();
        assertThat(context.getBean(RabbitAdmin.class)).isNotNull();
        assertThat(context.getBean(AsyncRabbitTemplate.class)).isNotNull();
        assertThat(context.getBean(RabbitMessagingTemplate.class)).isNotNull();
    }

    @Test
    void shouldUseJacksonConverter() {
        assertThat(context.getBean(RabbitTemplate.class).getMessageConverter())
                .isInstanceOf(JacksonJsonMessageConverter.class);
    }

    @Test
    void shouldInstallAllCallbacks() {
        RabbitTemplate rabbitTemplate = context.getBean(RabbitTemplate.class);

        assertThat(ReflectionTestUtils.getField(rabbitTemplate, "confirmCallback")).isNotNull();
        assertThat(ReflectionTestUtils.getField(rabbitTemplate, "returnsCallback")).isNotNull();
        assertThat(ReflectionTestUtils.getField(rabbitTemplate, "recoveryCallback")).isNotNull();
    }

    @Test
    void shouldExposeSchedulerByDefault() {
        assertThat(context.containsBean("rabbitTaskScheduler")).isTrue();
        assertThat(context.getBean("rabbitTaskScheduler", TaskScheduler.class)).isNotNull();
    }

    @Test
    void shouldRunScheduledTasksConcurrently() throws Exception {
        TaskScheduler scheduler = context.getBean("rabbitTaskScheduler", TaskScheduler.class);
        int taskCount = 4;
        CountDownLatch latch = new CountDownLatch(taskCount);
        long start = System.nanoTime();

        for (int index = 0; index < taskCount; index++) {
            scheduler.schedule(() -> {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }, Instant.now());
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThat(elapsedMillis).isLessThan(700L);
    }

    @Test
    void shouldRegisterDefaultListenerWhenEnabled() {
        assertThat(context.getBean(GXDefaultRabbitMQQueueListenerImpl.class)).isNotNull();
    }

    @Test
    void shouldExposeSinglePropertiesVariant() {
        assertThat(context.getBeansOfType(GXRabbitMQProperties.class)).hasSize(1);
    }

    @Test
    void shouldBindSpringRabbitProperties() {
        assertThat(rabbitProperties.getHost()).isEqualTo("localhost");
        assertThat(rabbitProperties.getPort()).isEqualTo(5672);
        assertThat(rabbitProperties.getDefaultQueueName()).isEqualTo("test.default.queue");
    }
}
