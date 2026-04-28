package cn.maple.sse.dto;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.IdUtil;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
    private String clientId;

    /**
     * Message id. Lombok builders do not keep field initializers unless the field
     * is marked with Builder.Default.
     */
    @Builder.Default
    private String msgId = IdUtil.fastUUID();

    /**
     * Event payload.
     */
    private Dict data;

    /**
     * SSE event name.
     */
    private String eventName;

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
