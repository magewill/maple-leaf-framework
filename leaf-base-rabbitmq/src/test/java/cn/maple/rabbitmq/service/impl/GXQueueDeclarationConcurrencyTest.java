package cn.maple.rabbitmq.service.impl;

import cn.maple.rabbitmq.exception.GXRabbitMQOperationException;
import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import cn.maple.rabbitmq.support.GXAmqpManagementException;
import cn.maple.rabbitmq.support.GXAmqpMessagePublisher;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Queue;

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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GXQueueDeclarationConcurrencyTest {
    @Mock
    private GXAmqpMessagePublisher publisher;

    @Mock
    private GXAmqpManagementClient managementClient;

    private GXSendRabbitMQServiceImpl service;

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(publisher, managementClient);
    }

    @AfterEach
    void tearDown() {
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldDeclareSameQueueOnceUnderConcurrency() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(200L);
            return null;
        }).when(managementClient).declareQueue(anyString(), anyBoolean(), anyBoolean(), anyMap());

        List<Queue> results = runConcurrently(32, () -> service.createQueue("order.queue", true, false, false, null));

        assertThat(results).hasSize(32).allSatisfy(queue -> assertThat(queue.getName()).isEqualTo("order.queue"));
        verify(managementClient, times(1)).declareQueue(anyString(), anyBoolean(), anyBoolean(), anyMap());
    }

    @Test
    void shouldDeclareEachQueueOnceForDifferentQueues() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(100L);
            return null;
        }).when(managementClient).declareQueue(anyString(), anyBoolean(), anyBoolean(), anyMap());

        List<Callable<Queue>> tasks = new ArrayList<>();
        for (int queueIndex = 0; queueIndex < 10; queueIndex++) {
            String queueName = "queue-" + queueIndex;
            for (int threadIndex = 0; threadIndex < 20; threadIndex++) {
                tasks.add(() -> service.createQueue(queueName, true, false, false, null));
            }
        }

        awaitAll(tasks);

        verify(managementClient, times(10)).declareQueue(anyString(), anyBoolean(), anyBoolean(), anyMap());
    }

    @Test
    void shouldNotSerializeDifferentQueues() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(200L);
            return null;
        }).when(managementClient).declareQueue(anyString(), anyBoolean(), anyBoolean(), anyMap());

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
            throw new GXAmqpManagementException("PRECONDITION_FAILED", 400);
        }).when(managementClient).declareQueue(anyString(), anyBoolean(), anyBoolean(), anyMap());

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
        // 固定大小线程池 + 双重闩：先让全部线程就位，再同时放行，
        // 避免 cachedThreadPool 懒创建线程把「并发窗口」拉长到首个声明已完成之后（高负载下会偶发重复声明）。
        ExecutorService callers = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Queue>> futures = new ArrayList<>();
            for (Callable<Queue> task : tasks) {
                futures.add(callers.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Queue> results = new ArrayList<>();
            for (Future<Queue> future : futures) {
                try {
                    results.add(future.get(10, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof GXAmqpManagementException)
                            && !(e.getCause() instanceof GXRabbitMQOperationException)) {
                        throw e;
                    }
                }
            }
            return results;
        } finally {
            callers.shutdownNow();
        }
    }
}
