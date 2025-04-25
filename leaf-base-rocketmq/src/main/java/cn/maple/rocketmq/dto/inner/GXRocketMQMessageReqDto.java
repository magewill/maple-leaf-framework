package cn.maple.rocketmq.dto.inner;

import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * RocketMQ消息请求数据传输对象
 * <p>
 * 此类用于封装发送到RocketMQ的消息数据，提供了多种构造方法以适应不同的消息发送场景。
 * 继承自GXBaseReqDto，可以利用基类提供的通用功能。
 * </p>
 * <p>
 * 安全性考虑：
 * <ul>
 *   <li>消息体使用JSON格式序列化，确保数据的可读性和兼容性</li>
 *   <li>提供多种构造方法，避免直接暴露内部属性</li>
 *   <li>使用Lombok注解简化代码，减少出错可能性</li>
 *   <li>对于敏感信息，建议在转换为JSON前进行脱敏处理</li>
 * </ul>
 * </p>
 * <p>
 * 性能优化：
 * <ul>
 *   <li>使用高效的JSON工具进行序列化，减少CPU和内存开销</li>
 *   <li>消息KEY的合理设置可以提高消息路由和查询效率</li>
 *   <li>延时消息的精确控制可以优化系统资源使用</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建简单消息（只有消息体）
 * Order order = new Order("ORD123456", 100.00);
 * GXRocketMQMessageReqDto messageDto = new GXRocketMQMessageReqDto(order);
 * 
 * // 2. 创建带标签的消息
 * GXRocketMQMessageReqDto taggedMessage = new GXRocketMQMessageReqDto("order_created", order);
 * 
 * // 3. 创建完整消息（主题、标签、消息体、延时时间、消息KEY）
 * GXRocketMQMessageReqDto fullMessage = new GXRocketMQMessageReqDto(
 *     "order_topic",           // 主题
 *     "order_paid",            // 标签
 *     order,                    // 消息体
 *     60,                       // 延时发送时间（秒）
 *     "ORD123456"              // 消息KEY
 * );
 * 
 * // 4. 使用setter方法逐个设置属性
 * GXRocketMQMessageReqDto customMessage = new GXRocketMQMessageReqDto();
 * customMessage.setTopic("order_topic");
 * customMessage.setTag("order_refunded");
 * customMessage.setBody(JSONUtil.toJsonStr(order));
 * customMessage.setDeliverTime(3600);  // 1小时后发送
 * customMessage.setMessageKey("ORD123456_REFUND");
 * </pre>
 * </p>
 */
@Data
@ToString
@EqualsAndHashCode(callSuper = true)
public class GXRocketMQMessageReqDto extends GXBaseReqDto {
    /**
     * 消息主题
     * <p>
     * 消息主题是消息的第一级分类，RocketMQ的消息必须有主题。
     * 主题最长不超过255个字符，由字母、数字、中划线和下划线构成。
     * </p>
     * <p>
     * <strong>注意：一条合法的RocketMQ消息，主题不能为空</strong>
     * </p>
     * <p>
     * 主题命名建议：
     * <ul>
     *   <li>使用有意义的业务名称，如order_topic, user_topic等</li>
     *   <li>使用下划线分隔多个单词</li>
     *   <li>避免使用特殊字符和中文</li>
     * </ul>
     * </p>
     */
    private String topic;

    /**
     * 消息标签
     * <p>
     * 标签是消息的第二级分类，用于同一主题下的消息过滤。
     * 消费者可以根据标签进行消息过滤，只消费感兴趣的消息。
     * </p>
     * <p>
     * 默认为空字符串，表示不使用标签过滤。
     * </p>
     * <p>
     * 标签使用建议：
     * <ul>
     *   <li>标签应简洁明了，表达消息的具体类型或操作</li>
     *   <li>同一主题下的不同业务操作可使用不同标签</li>
     *   <li>例如：订单主题下可设置created, paid, shipped等标签</li>
     * </ul>
     * </p>
     */
    private String tag = "";

    /**
     * 消息内容
     * <p>
     * 消息的具体内容，通常是JSON格式的字符串，包含业务数据。
     * 在构造方法中，会自动将对象转换为JSON字符串。
     * </p>
     * <p>
     * 安全建议：
     * <ul>
     *   <li>避免在消息中包含敏感信息，如密码、密钥等</li>
     *   <li>必要时对敏感字段进行脱敏处理</li>
     *   <li>控制消息大小，过大的消息会影响性能</li>
     * </ul>
     * </p>
     */
    private String body;

    /**
     * 延时消息发送时间
     * <p>
     * 指定消息延迟投递的时间，单位为秒。
     * 设置后，消息将在指定的时间后才被消费者接收到。
     * </p>
     * <p>
     * 使用场景：
     * <ul>
     *   <li>订单超时未支付自动取消</li>
     *   <li>预约提醒</li>
     *   <li>定时任务触发</li>
     * </ul>
     * </p>
     * <p>
     * 注意：RocketMQ的延时等级是固定的，如果需要精确的延时，需要在应用层面进行处理。
     * </p>
     */
    private long deliverTime;

    /**
     * 消息KEY
     * <p>
     * 消息的唯一标识，用于消息的查询和跟踪。
     * 建议使用业务唯一标识作为消息KEY，如订单号、用户ID等。
     * </p>
     * <p>
     * 设置消息KEY的好处：
     * <ul>
     *   <li>方便在RocketMQ控制台查询和跟踪消息</li>
     *   <li>便于问题排查和业务分析</li>
     *   <li>可用于实现消息幂等性处理</li>
     * </ul>
     * </p>
     */
    private String messageKey;

    /**
     * 默认构造函数
     * <p>
     * 创建一个空的消息DTO对象，需要后续设置各个属性。
     * </p>
     */
    public GXRocketMQMessageReqDto() {

    }

    /**
     * 构造函数 - 仅指定消息体
     * <p>
     * 创建一个只包含消息体的DTO对象，消息体会被自动转换为JSON字符串。
     * 使用此构造方法时，需要后续设置topic属性，因为topic是必须的。
     * </p>
     *
     * @param body 消息体对象，将被转换为JSON字符串
     */
    public GXRocketMQMessageReqDto(Object body) {
        this.body = JSONUtil.toJsonStr(body);
    }

    /**
     * 构造函数 - 指定标签和消息体
     * <p>
     * 创建一个包含标签和消息体的DTO对象，消息体会被自动转换为JSON字符串。
     * 使用此构造方法时，需要后续设置topic属性，因为topic是必须的。
     * </p>
     *
     * @param tag 消息标签，用于消息过滤
     * @param body 消息体对象，将被转换为JSON字符串
     */
    public GXRocketMQMessageReqDto(String tag, Object body) {
        this.tag = tag;
        this.body = JSONUtil.toJsonStr(body);
    }

    /**
     * 构造函数 - 指定所有属性
     * <p>
     * 创建一个完整的消息DTO对象，包含主题、标签、消息体、延时时间和消息KEY。
     * 消息体会被自动转换为JSON字符串。
     * </p>
     *
     * @param topic 消息主题，不能为空
     * @param tag 消息标签，用于消息过滤
     * @param body 消息体对象，将被转换为JSON字符串
     * @param deliverTime 延时发送时间，单位为秒
     * @param messageKey 消息KEY，用于消息查询和跟踪
     */
    public GXRocketMQMessageReqDto(String topic, String tag, Object body, int deliverTime, String messageKey) {
        this.topic = topic;
        this.tag = tag;
        this.deliverTime = deliverTime;
        this.messageKey = messageKey;
        this.body = JSONUtil.toJsonStr(body);
    }
}