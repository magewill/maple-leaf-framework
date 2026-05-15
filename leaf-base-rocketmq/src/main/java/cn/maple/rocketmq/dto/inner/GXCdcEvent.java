package cn.maple.rocketmq.dto.inner;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Normalized CDC event consumed from RocketMQ.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GXCdcEvent extends GXBaseReqDto {
    private Dict before = Dict.create();

    private Dict after = Dict.create();

    private Dict source = Dict.create();

    private String op = "";

    private String tsMs = "";

    private Dict transaction = Dict.create();

    private String bizType = "";

    private String rawMessage = "";

    public static GXCdcEvent fromPayload(Dict payload, @Nullable String bizType, @Nullable String rawMessage) {
        GXCdcEvent event = new GXCdcEvent();
        event.setBefore(toDict(payload.getObj("before")));
        event.setAfter(toDict(payload.getObj("after")));
        event.setSource(toDict(payload.getObj("source")));
        event.setOp(Convert.toStr(payload.getObj("op"), ""));
        event.setTsMs(Convert.toStr(resolveTimestamp(payload), ""));
        event.setTransaction(toDict(payload.getObj("transaction")));
        event.setBizType(Convert.toStr(bizType, ""));
        event.setRawMessage(Convert.toStr(rawMessage, ""));
        return event;
    }

    public @Nullable GXCdcOperation getOperation() {
        return GXCdcOperation.fromCode(op);
    }

    public boolean isOnlyChanged(String fieldName) {
        MapDifference<String, Object> difference = Maps.difference(before, after);
        Map<String, MapDifference.ValueDifference<Object>> entriesDiffering = difference.entriesDiffering();
        return entriesDiffering.size() == 1 && entriesDiffering.containsKey(fieldName);
    }

    private static Dict toDict(@Nullable Object value) {
        Dict dict = Convert.convert(Dict.class, value);
        return dict == null ? Dict.create() : dict;
    }

    private static @Nullable Object resolveTimestamp(Dict payload) {
        if (payload.containsKey("ts_ms")) {
            return payload.getObj("ts_ms");
        }
        return payload.getObj("tsMs");
    }
}
