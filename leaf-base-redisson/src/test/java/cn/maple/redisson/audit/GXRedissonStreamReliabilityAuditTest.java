package cn.maple.redisson.audit;

import cn.maple.redisson.properties.GXRedissonStreamMQProperties;
import cn.maple.redisson.stream.dto.req.GXRedissonStreamMessageDto;
import cn.maple.redisson.stream.handler.GXRedissonStreamMessageHandler;
import cn.maple.redisson.stream.queue.GXRedissonStreamImmediateMQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMapCache;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.PendingEntry;
import org.redisson.api.stream.StreamMessageId;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-03, F-12: stream consumption discards messages without a handler and keeps
 * acknowledging after the dedupe marker fails; pending recovery only looks at the
 * head of the pending list.
 */
@SuppressWarnings("deprecation")
class GXRedissonStreamReliabilityAuditTest {

    private static final String TOPIC = "audit-stream-topic";
    private static final String GROUP = "default-group";
    private static final String STREAM_KEY = "mq:stream:" + TOPIC;
    private static final String DLQ_KEY = "mq:dlq:" + TOPIC;

    private RedissonClient client;
    private GXRedissonStreamMQProperties props;
    private GXRedissonStreamImmediateMQ immediateMQ;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        client = mock(RedissonClient.class);
        props = GXRedissonAuditTestSupport.streamProperties();
        objectMapper = new ObjectMapper();
        immediateMQ = new GXRedissonStreamImmediateMQ(client, props, new ObjectMapper());
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": message without handler is acknowledged and discarded")
    @SuppressWarnings("unchecked")
    void messageWithoutHandlerIsAcknowledged() {
        RStream<String, String> stream = mock(RStream.class);
        StreamMessageId streamId = new StreamMessageId(1, 1);

        GXRedissonAuditTestSupport.invoke(immediateMQ, "processMessage",
                new Class<?>[]{RStream.class, String.class, String.class, String.class, StreamMessageId.class, String.class},
                stream, STREAM_KEY, TOPIC, GROUP, streamId, messageJson());

        // CURRENT BEHAVIOR: the entry is acknowledged without any handler and without
        // a DLQ copy, so a topic published before subscription is silently lost.
        verify(stream, times(1)).ack(GROUP, streamId);
        verify(client, never()).getStream(DLQ_KEY);
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": ack happens even when the processed marker cannot be written")
    @SuppressWarnings("unchecked")
    void ackHappensWhenDedupeMarkerFails() {
        RStream<String, String> stream = mock(RStream.class);
        RMapCache<String, String> cache = mock(RMapCache.class);
        StreamMessageId streamId = new StreamMessageId(2, 1);
        when(client.<String, String>getMapCache(anyString())).thenReturn(cache);
        when(cache.containsKey(anyString())).thenReturn(false);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(cache).fastPut(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        Map<String, GXRedissonStreamMessageHandler> handlers =
                (Map<String, GXRedissonStreamMessageHandler>) GXRedissonAuditTestSupport.field(immediateMQ, "handlers");
        handlers.put(TOPIC, message -> {
        });

        GXRedissonAuditTestSupport.invoke(immediateMQ, "processMessage",
                new Class<?>[]{RStream.class, String.class, String.class, String.class, StreamMessageId.class, String.class},
                stream, STREAM_KEY, TOPIC, GROUP, streamId, messageJson());

        // CURRENT BEHAVIOR: markProcessed failure is downgraded to warn and the entry
        // is acknowledged anyway, so the dedupe window is lost on partial failures.
        verify(cache, times(1)).fastPut(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        verify(stream, times(1)).ack(GROUP, streamId);
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": pending recovery scans only the head of the pending list")
    @SuppressWarnings("unchecked")
    void pendingRecoveryScansOnlyHeadOfPendingList() {
        RStream<String, String> stream = mock(RStream.class);
        when(client.<String, String>getStream(STREAM_KEY)).thenReturn(stream);

        PendingEntry pendingEntry = mock(PendingEntry.class);
        when(pendingEntry.getIdleTime()).thenReturn(60_000L);
        when(pendingEntry.getId()).thenReturn(new StreamMessageId(3, 1));
        when(stream.listPending(eq(GROUP), any(StreamMessageId.class), any(StreamMessageId.class), eq(20)))
                .thenReturn(List.of(pendingEntry));
        when(stream.claim(eq(GROUP), anyString(), anyLong(), any(TimeUnit.class), any(StreamMessageId.class)))
                .thenReturn(Map.of());

        immediateMQ.recoverPendingMessages(TOPIC);

        // CURRENT BEHAVIOR: a single listPending call with a fixed cap (batchSize * 2)
        // and one claim call per entry: no paging cursor, so a blocked head entry keeps
        // the rest of the pending list from ever being recovered.
        verify(stream, times(1)).listPending(eq(GROUP), any(StreamMessageId.class), any(StreamMessageId.class), eq(20));
        verify(stream, times(1)).claim(eq(GROUP), anyString(), anyLong(), any(TimeUnit.class), any(StreamMessageId.class));
    }

    private String messageJson() {
        return objectMapper.writeValueAsString(
                GXRedissonStreamMessageDto.immediate(TOPIC, "payload", String.class.getName()));
    }
}
