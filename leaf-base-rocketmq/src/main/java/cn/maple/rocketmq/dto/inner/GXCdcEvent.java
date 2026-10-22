package cn.maple.rocketmq.dto.inner;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Normalized CDC event consumed from RocketMQ.
 *
 * <p>The event is immutable: it is created once from the raw payload and shared read-only
 * with every listener, so one listener cannot change what the next one sees. The
 * before/after difference is computed exactly once per event.</p>
 */
@EqualsAndHashCode(callSuper = true)
@Getter
public class GXCdcEvent extends GXBaseReqDto {
    private final Dict before;

    private final Dict after;

    private final Dict source;

    private final String op;

    private final String tsMs;

    private final Dict transaction;

    private final String bizType;

    private final String rawMessage;

    private final Set<String> differingFields;

    private GXCdcEvent(Dict before, Dict after, Dict source, String op, String tsMs, Dict transaction,
                       String bizType, String rawMessage) {
        this.before = before;
        this.after = after;
        this.source = source;
        this.op = op;
        this.tsMs = tsMs;
        this.transaction = transaction;
        this.bizType = bizType;
        this.rawMessage = rawMessage;
        MapDifference<String, Object> difference = Maps.difference(before, after);
        this.differingFields = Set.copyOf(difference.entriesDiffering().keySet());
    }

    public static GXCdcEvent fromPayload(Dict payload, @Nullable String bizType, @Nullable String rawMessage) {
        return new GXCdcEvent(
                toDict(payload.getObj("before")),
                toDict(payload.getObj("after")),
                toDict(payload.getObj("source")),
                Convert.toStr(payload.getObj("op"), ""),
                Convert.toStr(resolveTimestamp(payload), ""),
                toDict(payload.getObj("transaction")),
                Convert.toStr(bizType, ""),
                Convert.toStr(rawMessage, ""));
    }

    public @Nullable GXCdcOperation getOperation() {
        return GXCdcOperation.fromCode(op);
    }

    /**
     * @param fieldName field to inspect, may be null
     * @return true when the only changed field between before and after is the given one
     */
    public boolean isOnlyChanged(@Nullable String fieldName) {
        return fieldName != null && differingFields.size() == 1 && differingFields.contains(fieldName);
    }

    private static Dict toDict(@Nullable Object value) {
        Dict copy = Dict.create();
        if (value instanceof Dict source) {
            copy.putAll(source);
            return copy;
        }
        Dict converted = Convert.convert(Dict.class, value);
        if (converted != null) {
            copy.putAll(converted);
        }
        return copy;
    }

    private static @Nullable Object resolveTimestamp(Dict payload) {
        if (payload.containsKey("ts_ms")) {
            return payload.getObj("ts_ms");
        }
        return payload.getObj("tsMs");
    }
}
