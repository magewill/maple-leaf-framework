package cn.maple.rabbitmq.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 模块内共享的 Jackson 3 {@link JsonMapper}，供消息转换器与类型化转换使用。
 *
 * <p>出站载荷仍由 Hutool 序列化（见 {@code GXSendRabbitMQServiceImpl}），以保持既有
 * wire format 不变（{@code java.util.Date}/{@code LocalDateTime} 均写毫秒时间戳）；
 * 本类只负责入站类型化转换与 {@code __TypeId__} 契约常量。</p>
 */
public final class GXRabbitMQJsonSupport {
    /**
     * Spring AMQP 类型映射头，消费端 {@code DefaultClassMapper} 依赖此头还原类型。
     */
    public static final String TYPE_ID_HEADER = "__TypeId__";

    /**
     * 发送载荷统一为 Hutool {@code Dict}，与公开 DTO 字段类型一致。
     */
    public static final String DICT_TYPE_ID = "cn.hutool.core.lang.Dict";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .changeDefaultPropertyInclusion(inclusion ->
                    JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();

    private GXRabbitMQJsonSupport() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    /**
     * 共享实例（线程安全）。
     */
    public static JsonMapper jsonMapper() {
        return JSON_MAPPER;
    }
}
