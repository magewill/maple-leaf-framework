package cn.maple.sse.dto;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.IdUtil;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;

/**
 * Internal DTO used to build SSE messages.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
public class GXSseMessageInnerReqDto extends GXBaseReqDto {
    /**
     * Client connection id.
     */
    private @Nullable String clientId;

    /**
     * Message id. Lombok builders do not keep field initializers unless the field
     * is marked with Builder.Default.
     */
    @Builder.Default
    private String msgId = IdUtil.fastUUID();

    /**
     * Event payload. A {@code null} payload is never sent: callers that build the DTO
     * manually must set it, otherwise the message is dropped and counted.
     */
    private @Nullable Dict data;

    /**
     * SSE event name.
     */
    private @Nullable String eventName;

    /**
     * Browser reconnect interval in milliseconds.
     */
    @Builder.Default
    private long reconnectTimeMillis = 2_000L;

    /**
     * SSE comment.
     */
    @Builder.Default
    private String comment = "";
}
