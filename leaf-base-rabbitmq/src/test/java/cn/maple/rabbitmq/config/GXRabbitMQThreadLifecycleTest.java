package cn.maple.rabbitmq.config;

import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import cn.maple.rabbitmq.service.impl.GXSendRabbitMQServiceImpl;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文重复启停后模块自建线程不得累积（SC-005）。
 */
class GXRabbitMQThreadLifecycleTest {
    private static final String SCHEDULER_THREAD_PREFIX = "maple-rabbit-scheduler-";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ConnectionFactory.class, () -> Mockito.mock(ConnectionFactory.class))
            .withBean(GXRabbitMQProperties.class, GXRabbitMQProperties::new)
            .withUserConfiguration(GXRabbitMQConfig.class);

    @Test
    void shouldNotLeakSchedulerThreadsAcrossContextRestarts() throws Exception {
        for (int cycle = 0; cycle < 3; cycle++) {
            runner.run(context -> {
                TaskScheduler scheduler = context.getBean("rabbitTaskScheduler", TaskScheduler.class);
                CountDownLatch latch = new CountDownLatch(1);
                scheduler.schedule(latch::countDown, Instant.now());
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            });

            assertThat(awaitNoThreadWithPrefix(SCHEDULER_THREAD_PREFIX, 3000L))
                    .as("cycle %d should release scheduler threads", cycle)
                    .isTrue();
        }
    }

    @Test
    void shouldTerminateAsyncWorkerThreadsAfterShutdown() throws Exception {
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        RabbitAdmin rabbitAdmin = Mockito.mock(RabbitAdmin.class);
        Mockito.when(rabbitAdmin.declareQueue(Mockito.any())).thenReturn("lifecycle.queue");
        GXSendRabbitMQServiceImpl service = GXTestRabbitFixtures.newService(rabbitTemplate, rabbitAdmin);
        try {
            service.setupMessageChannelAsync("lifecycle.queue", true, false, false, null,
                    new DirectExchange("lifecycle.exchange"), "lifecycle.key").join();
        } finally {
            service.shutdownAsyncExecutor();
        }

        assertThat(awaitNoThreadWithPrefix("rabbitmq-async-worker-", 3000L)).isTrue();
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
