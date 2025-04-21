package cn.maple.sse.dto;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.IdUtil;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SSE消息内部传输对象
 * <p>
 * 该类用于在SSE服务中构建和传输消息，支持自定义事件名称、数据内容和重连时间等属性
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * GXSseMessageInnerReqDto messageDto = GXSseMessageInnerReqDto.builder()
 *     .clientId("client123")
 *     .eventName("userNotification")
 *     .data(Dict.create().set("message", "您有一条新消息").set("type", "notification"))
 *     .build();
 * </pre>
 * </p>
 * 
 * @author maple
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
public class GXSseMessageInnerReqDto extends GXBaseReqDto {
    /**
     * 客户端标识ID
     * <p>用于唯一标识连接的客户端，在发送消息和管理连接时使用</p>
     */
    private String clientId;

    /**
     * 消息ID
     * <p>每条消息的唯一标识，默认使用UUID生成，用于消息追踪和去重</p>
     */
    private String msgId = IdUtil.fastUUID();

    /**
     * 传输数据，事件数据
     * <p>使用Dict类型存储灵活的消息内容，可以包含任意键值对数据</p>
     * <p>例如：Dict.create().set("message", "消息内容").set("timestamp", System.currentTimeMillis())</p>
     */
    private Dict data;

    /**
     * 事件标识符，事件名称
     * <p>用于客户端区分不同类型的事件，客户端可以通过事件名称注册不同的处理函数</p>
     * <p>例如："userLogin"、"newMessage"、"systemNotification"等</p>
     */
    private String eventName;

    /**
     * 重新连接时间
     * <p>当连接断开时，客户端尝试重新连接的等待时间（毫秒）</p>
     * <p>默认为2秒(2000毫秒)，可根据网络环境和业务需求调整</p>
     */
    private long reconnectTimeMillis = 2_000L;

    /**
     * 事件注释
     * <p>用于添加事件的额外说明信息，方便调试和日志记录</p>
     * <p>该字段会在SSE事件中作为注释发送给客户端</p>
     */
    private String comment = "";
}