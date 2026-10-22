package cn.maple.redisson.audit;

import cn.maple.redisson.properties.GXRedissonStreamMQProperties;
import cn.maple.redisson.stream.dto.req.GXRedissonStreamMessageDto;
import cn.maple.redisson.stream.queue.GXRedissonStreamDelayedMQ;
import cn.maple.redisson.stream.queue.GXRedissonStreamImmediateMQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.api.stream.StreamMessageId;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-04: delayed stream transfer performs six serial round trips per message while
 * holding one global distributed lock across all topics.
 */
class GXRedissonStreamTransferAuditTest {

    private static final String TOPIC = "audit-delay-stream-topic";
    private static final String MESSAGE_ID = "audit-message-1";

    private RedissonClient client;
    private GXRedissonStreamImmediateMQ immediateQueue;
    private GXRedissonStreamDelayedMQ delayedQueue;
    private RScoredSortedSet<String> zset;
    private RScoredSortedSet<String> transferSet;
    private RMap<String, String> hash;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(RedissonClient.class);
        immediateQueue = mock(GXRedissonStreamImmediateMQ.class);
        objectMapper = new ObjectMapper();
        GXRedissonStreamMQProperties props = GXRedissonAuditTestSupport.streamProperties();
        delayedQueue = new GXRedissonStreamDelayedMQ(client, props, immediateQueue, new ObjectMapper());

        zset = mock(RScoredSortedSet.class);
        transferSet = mock(RScoredSortedSet.class);
        hash = mock(RMap.class);
        when(client.<String>getScoredSortedSet(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key.contains("transfer") ? transferSet : zset;
        });
        when(client.<String, String>getMap(anyString())).thenReturn(hash);
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": one due message costs six serial round trips")
    void oneDueMessageCostsSixRoundTrips() {
        long now = System.currentTimeMillis();
        Collection<ScoredEntry<String>> due = List.of(new ScoredEntry<>((double) now, MESSAGE_ID));
        when(zset.entryRange(anyDouble(), anyBoolean(), anyDouble(), anyBoolean(), anyInt(), anyInt())).thenReturn(due);
        when(transferSet.entryRange(anyDouble(), anyBoolean(), anyDouble(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(hash.get(MESSAGE_ID)).thenReturn(messageJson());
        when(zset.remove(MESSAGE_ID)).thenReturn(true);
        when(zset.isEmpty()).thenReturn(false);

        GXRedissonAuditTestSupport.invoke(delayedQueue, "transferTopicDueMessages",
                new Class<?>[]{String.class}, TOPIC);

        // CURRENT BEHAVIOR: hash.get -> transferSet.add -> zset.remove -> publish ->
        // hash.remove -> transferSet.remove, all serial, no pipeline.
        verify(hash, times(1)).get(MESSAGE_ID);
        verify(transferSet, times(1)).add(anyDouble(), eq(MESSAGE_ID));
        verify(zset, times(1)).remove(MESSAGE_ID);
        verify(immediateQueue, times(1)).publish(any(GXRedissonStreamMessageDto.class));
        verify(hash, times(1)).remove(MESSAGE_ID);
        verify(transferSet, times(1)).remove(MESSAGE_ID);
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": the transfer round is capped at 200 messages per topic")
    void transferRoundIsCapped() {
        when(zset.entryRange(anyDouble(), anyBoolean(), anyDouble(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(transferSet.entryRange(anyDouble(), anyBoolean(), anyDouble(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(zset.isEmpty()).thenReturn(false);

        GXRedissonAuditTestSupport.invoke(delayedQueue, "transferTopicDueMessages",
                new Class<?>[]{String.class}, TOPIC);

        // CURRENT BEHAVIOR: the scan is bounded by MAX_TRANSFER_PER_ROUND = 200, so the
        // lock hold time is bounded by 200 * 6 round trips per topic.
        verify(zset, times(1)).entryRange(anyDouble(), anyBoolean(), anyDouble(), anyBoolean(), eq(0), eq(200));
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": one global lock serialises every topic")
    @SuppressWarnings("unchecked")
    void oneGlobalLockSerialisesEveryTopic() throws InterruptedException {
        org.redisson.api.RLock lock = mock(org.redisson.api.RLock.class);
        when(client.getLock("mq:delay:lock")).thenReturn(lock);
        org.mockito.Mockito.doReturn(true).when(lock).tryLock(0L, java.util.concurrent.TimeUnit.MILLISECONDS);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(client.<String>getSet(anyString())).thenReturn(mock(org.redisson.api.RSet.class));
        GXRedissonAuditTestSupport.setField(delayedQueue, "running", true);

        GXRedissonAuditTestSupport.invoke(delayedQueue, "transferDueMessages", new Class[]{});

        // CURRENT BEHAVIOR: the lock key is a fixed constant shared by every
        // application instance and every topic, so all transfers are serialised.
        verify(client, times(1)).getLock("mq:delay:lock");
        verify(lock, times(1)).unlock();
    }

    private String messageJson() {
        GXRedissonStreamMessageDto message = GXRedissonStreamMessageDto.delayed(
                TOPIC, "payload", String.class.getName(), 1_000L);
        message.setMessageId(MESSAGE_ID);
        message.setStreamMessageId(new StreamMessageId(1, 1).toString());
        return objectMapper.writeValueAsString(message);
    }
}
