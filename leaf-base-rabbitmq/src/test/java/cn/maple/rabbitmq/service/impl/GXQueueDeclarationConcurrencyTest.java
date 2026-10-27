package cn.maple.rabbitmq.service.impl;

import cn.maple.rabbitmq.exception.GXRabbitMQOperationException;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GXQueueDeclarationConcurrencyTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitAdmin rabbitAdmin;

    private GXSendRabbitMQServiceImpl service;

    private ExecutorService callers;

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(rabbitTemplate, rabbitAdmin);
        callers = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        callers.shutdownNow();
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldDeclareSameQueueOnceUnderConcurrency() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(100L);
            return ((Queue) invocation.getArgument(0)).getName();
        }).when(rabbitAdmin).declareQueue(any());

        List<Queue> results = runConcurrently(32, () -> service.createQueue("order.queue", true, false, false, null));

        assertThat(results).hasSize(32).allSatisfy(queue -> assertThat(queue.getName()).isEqualTo("order.queue"));
        verify(rabbitAdmin, times(1)).declareQueue(any());
    }

    @Test
    void shouldDeclareEachQueueOnceForDifferentQueues() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(50L);
            return ((Queue) invocation.getArgument(0)).getName();
        }).when(rabbitAdmin).declareQueue(any());

        List<Callable<Queue>> tasks = new ArrayList<>();
        for (int queueIndex = 0; queueIndex < 10; queueIndex++) {
            String queueName = "queue-" + queueIndex;
            for (int threadIndex = 0; threadIndex < 20; threadIndex++) {
                tasks.add(() -> service.createQueue(queueName, true, false, false, null));
            }
        }

        awaitAll(tasks);

        verify(rabbitAdmin, times(10)).declareQueue(any());
    }

    @Test
    void shouldNotSerializeDifferentQueues() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(200L);
            return ((Queue) invocation.getArgument(0)).getName();
        }).when(rabbitAdmin).declareQueue(any());

        long start = System.nanoTime();
        List<Callable<Queue>> tasks = new ArrayList<>();
        for (int queueIndex = 0; queueIndex < 10; queueIndex++) {
            String queueName = "parallel-" + queueIndex;
            tasks.add(() -> service.createQueue(queueName, true, false, false, null));
        }
        awaitAll(tasks);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMillis).isLessThan(1500L);
    }

    @Test
    void shouldPropagateFailureToAllConcurrentCallers() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(100L);
            throw new AmqpException("PRECONDITION_FAILED");
        }).when(rabbitAdmin).declareQueue(any());

        AtomicInteger wrappedFailures = new AtomicInteger();
        List<Callable<Queue>> tasks = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            tasks.add(() -> {
                try {
                    return service.createQueue("order.queue", true, false, false, null);
                } catch (GXRabbitMQOperationException e) {
                    wrappedFailures.incrementAndGet();
                    return null;
                }
            });
        }

        awaitAll(tasks);

        assertThat(wrappedFailures).hasValue(8);
    }

    private List<Queue> runConcurrently(int threadCount, Callable<Queue> task) throws Exception {
        List<Callable<Queue>> tasks = new ArrayList<>();
        for (int index = 0; index < threadCount; index++) {
            tasks.add(task);
        }
        return awaitAll(tasks);
    }

    private List<Queue> awaitAll(List<Callable<Queue>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Queue>> futures = new ArrayList<>();
        for (Callable<Queue> task : tasks) {
            futures.add(callers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return task.call();
            }));
        }
        start.countDown();

        List<Queue> results = new ArrayList<>();
        for (Future<Queue> future : futures) {
            try {
                results.add(future.get(10, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof AmqpException) && !(e.getCause() instanceof GXRabbitMQOperationException)) {
                    throw e;
                }
            }
        }
        return results;
    }
}
