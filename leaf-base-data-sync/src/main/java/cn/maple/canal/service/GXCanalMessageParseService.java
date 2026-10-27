package cn.maple.canal.service;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;

import java.util.List;

/**
 * Parses raw Canal payloads and dispatches events to table handlers.
 */
public interface GXCanalMessageParseService {
    /**
     * Parses a payload and returns one result per contained event.
     *
     * <p>A single JSON object yields one result, a JSON array yields one result per element.</p>
     *
     * <p>Handler failures are handled per payload shape: for a <em>single object</em> payload the
     * exception is rethrown unchanged, which keeps the legacy {@link #parseMessage(String)} contract;
     * for a <em>JSON array</em> the exception is captured per element and returned as
     * {@link cn.maple.canal.enums.GXCanalOutcomeEnums#RETRYABLE_FAILURE} for that element, so the
     * remaining elements are still processed.</p>
     *
     * <p>This method was added after the interface had been published. It is declared {@code default}
     * so that existing implementations still compile; the default throws, because a lossy re-mapping
     * from the legacy {@code Dict} contract would hide outcomes. Implementations that participate in
     * consumption must override it (the module's own implementation does).</p>
     *
     * @param rawPayload raw Canal payload, may be blank
     * @return one result per event, empty never returned
     * @throws RuntimeException              the handler exception, for a single object payload
     * @throws UnsupportedOperationException when an implementation only supports the legacy contract
     */
    default List<GXCanalParseResultInnerDto> parse(String rawPayload) {
        throw new UnsupportedOperationException(
                "This GXCanalMessageParseService implementation only supports the legacy parseMessage contract; "
                        + "override parse() to take part in Canal consumption");
    }

    /**
     * Legacy entry point that returns the handler payload as a {@link Dict}.
     *
     * <p>Kept for backward compatibility. New consumer code should use {@link #parse(String)}.</p>
     *
     * @param message raw Canal payload
     * @return handler result for a successful event, otherwise a status map
     */
    Dict parseMessage(String message);
}
