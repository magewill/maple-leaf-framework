package cn.maple.canal.dto.inner;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.enums.GXCanalOutcomeEnums;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Structured parse result of exactly one Canal event.
 *
 * <p>Invariant: {@link #outcome()} is {@link GXCanalOutcomeEnums#SUCCESS} if and only if both
 * {@link #event()} and {@link #operation()} are present.</p>
 *
 * @param outcome       outcome of this event, never null
 * @param reason        short ASCII reason used for logging and metrics; null when the outcome is a success
 * @param event         normalized Canal event; null when parsing failed or the event was skipped
 * @param operation     lower case operation name (insert/update/delete); null when not applicable
 * @param handlerResult value returned by the table handler, kept so the legacy {@code parseMessage}
 *                      contract can still hand back the handler payload
 * @param batchIndex    zero based index inside a batch payload, zero for a single object payload
 */
public record GXCanalParseResultInnerDto(GXCanalOutcomeEnums outcome,
                                         @Nullable String reason,
                                         @Nullable GXCanalDataDto event,
                                         @Nullable String operation,
                                         @Nullable Dict handlerResult,
                                         int batchIndex) {
    public GXCanalParseResultInnerDto {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome == GXCanalOutcomeEnums.SUCCESS && (event == null || operation == null)) {
            throw new IllegalArgumentException("SUCCESS outcome requires both event and operation");
        }
    }

    /**
     * Creates a success result.
     *
     * @param event         parsed event
     * @param operation     lower case operation name
     * @param handlerResult value returned by the handler
     * @param index         batch index
     * @return success result
     */
    public static GXCanalParseResultInnerDto success(GXCanalDataDto event, String operation,
                                                     @Nullable Dict handlerResult, int index) {
        return new GXCanalParseResultInnerDto(GXCanalOutcomeEnums.SUCCESS, null, event, operation,
                handlerResult, index);
    }

    /**
     * Creates a skipped result, for example for DDL events.
     *
     * @param reason skip reason
     * @param index  batch index
     * @return skipped result
     */
    public static GXCanalParseResultInnerDto skipped(String reason, int index) {
        return new GXCanalParseResultInnerDto(GXCanalOutcomeEnums.SKIPPED, reason, null, null, null, index);
    }

    /**
     * Creates a retryable failure result.
     *
     * @param reason failure reason
     * @param index  batch index
     * @return retryable failure result
     */
    public static GXCanalParseResultInnerDto retryableFailure(String reason, int index) {
        return new GXCanalParseResultInnerDto(GXCanalOutcomeEnums.RETRYABLE_FAILURE, reason, null, null, null, index);
    }

    /**
     * Creates a non retryable failure result.
     *
     * @param reason failure reason
     * @param index  batch index
     * @return non retryable failure result
     */
    public static GXCanalParseResultInnerDto nonRetryableFailure(String reason, int index) {
        return new GXCanalParseResultInnerDto(GXCanalOutcomeEnums.NON_RETRYABLE_FAILURE, reason, null, null,
                null, index);
    }

    /**
     * Whether the outcome may be retried. Derived from {@link #outcome()} to keep the two in sync.
     *
     * @return true for {@link GXCanalOutcomeEnums#RETRYABLE_FAILURE}
     */
    public boolean retryable() {
        return outcome.isRetryable();
    }
}
