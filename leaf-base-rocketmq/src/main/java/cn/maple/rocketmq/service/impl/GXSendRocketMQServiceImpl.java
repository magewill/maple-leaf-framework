package cn.maple.rocketmq.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * RocketMQ消息发送服务实现类
 * <p>
 * 提供多种消息发送模式：普通消息、延迟消息、同步消息、异步消息和单向(Oneway)消息
 * 每种发送模式都有不同的可靠性和性能特点，适用于不同的业务场景
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 发送普通消息示例
 * GXRocketMQMessageReqDto normalMsg = new GXRocketMQMessageReqDto();
 * normalMsg.setTopic("topic_normal");
 * normalMsg.setTag("tag_normal");
 * normalMsg.setBody("{\"key\":\"value\"}");
 * normalMsg.setMessageKey("unique_key_001");
 * sendRocketMQService.sendNormalMessage(normalMsg);
 *
 * // 2. 发送延迟消息示例（10秒后投递）
 * GXRocketMQMessageReqDto delayMsg = new GXRocketMQMessageReqDto();
 * delayMsg.setTopic("topic_delay");
 * delayMsg.setTag("tag_delay");
 * delayMsg.setBody("{\"order_id\":\"12345\",\"status\":\"pending\"}");
 * delayMsg.setDeliverTime(10); // 10秒后投递
 * delayMsg.setMessageKey("order_timeout_12345");
 * String msgId = sendRocketMQService.sendDelayMessage(delayMsg);
 *
 * // 3. 发送同步消息示例
 * GXRocketMQMessageReqDto syncMsg = new GXRocketMQMessageReqDto();
 * syncMsg.setTopic("topic_sync");
 * syncMsg.setTag("tag_sync");
 * syncMsg.setBody("{\"notification\":\"important_update\"}");
 * boolean syncResult = sendRocketMQService.syncSend(syncMsg);
 *
 * // 4. 发送异步消息示例
 * GXRocketMQMessageReqDto asyncMsg = new GXRocketMQMessageReqDto();
 * asyncMsg.setTopic("topic_async");
 * asyncMsg.setTag("tag_async");
 * asyncMsg.setBody("{\"log\":\"system_operation_log\"}");
 * boolean asyncResult = sendRocketMQService.sendAsync(asyncMsg);
 *
 * // 5. 发送单向消息示例
 * GXRocketMQMessageReqDto onewayMsg = new GXRocketMQMessageReqDto();
 * onewayMsg.setTopic("topic_oneway");
 * onewayMsg.setTag("tag_oneway");
 * onewayMsg.setBody("{\"metric\":\"system_status\"}");
 * boolean onewayResult = sendRocketMQService.sendOneway(onewayMsg);
 * </pre>
 * </p>
 *
 * <p>
 * 注意事项：
 * 1. 消息发送前必须设置Topic，否则会抛出异常
 * 2. 延迟消息的延迟时间单位为秒，且必须大于0
 * 3. 消息体(body)不能为空，建议使用JSON格式字符串
 * 4. 消息Key(messageKey)可选，但建议设置，便于消息追踪和排查问题
 * 5. 异步消息和单向消息虽然返回值为boolean，但仅表示发送请求是否成功提交，不代表消息一定发送成功
 * </p>
 *
 * <p>
 * 各消息类型适用场景：
 * - 普通消息：适用于一般的业务场景，如用户注册后发送欢迎邮件
 * - 延迟消息：适用于定时任务、订单超时取消等场景
 * - 同步消息：适用于重要的通知消息，如重要通知邮件、营销短信等
 * - 异步消息：适用于可靠性要求不高，但要求高吞吐量的场景，如日志收集
 * - 单向消息：适用于不关心发送结果的场景，如监控数据上报，性能最高
 * </p>
 *
 * @author maple
 * @see cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto 消息请求DTO
 * @see org.apache.rocketmq.spring.core.RocketMQTemplate RocketMQ模板
 */
@Log4j2
@Service
public class GXSendRocketMQServiceImpl extends GXBusinessServiceImpl implements GXSendRocketMQService {
    /**
     * RocketMQ模板，用于发送各类消息
     * 由Spring自动注入，线程安全
     */
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 发送普通消息
     * <p>
     * 普通消息是最基础的消息类型，不保证消息顺序，适用于一般的业务场景
     * 该方法会同步发送消息，如果发送失败会抛出异常
     * </p>
     * <p>
     * 安全性考虑：
     * 1. 使用Objects.requireNonNull进行参数校验，避免NPE
     * 2. 对消息体进行非空检查，确保消息内容有效
     * 3. 使用try-catch捕获所有可能的异常，确保异常信息被正确记录
     * 4. 日志记录关键操作节点，便于问题排查
     * 5. 避免在日志中记录完整的敏感消息内容，防止信息泄露
     * </p>
     * <p>
     * 内存安全：
     * 1. 使用MessageBuilder构建消息，避免直接操作底层消息结构
     * 2. 不保存消息的引用，避免内存泄漏
     * 3. 所有资源在方法结束时自动释放，无需手动清理
     * </p>
     *
     * @param messageReqDto 待发送的消息对象，包含消息内容、主题、标签等信息
     * @throws NullPointerException 当messageReqDto或消息体为null时抛出
     * @throws GXBusinessException  当消息发送失败或参数无效时抛出异常
     */
    @Override
    public void sendNormalMessage(GXRocketMQMessageReqDto messageReqDto) {
        // 参数校验，防止空指针异常
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        Objects.requireNonNull(messageReqDto.getBody(), "消息内容不能为空");

        log.info("发送普通消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());
        try {
            // 获取消息唯一标识Key
            String messageKey = messageReqDto.getMessageKey();
            // 使用Builder模式构建消息，更安全且易于扩展
            MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
            // 设置消息Key，便于消息追踪
            if (CharSequenceUtil.isNotEmpty(messageKey)) {
                messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey);
            }
            Message<String> message = messageBuilder.build();
            // 发送消息到指定目标
            rocketMQTemplate.send(getDestination(messageReqDto), message);
            // 避免在日志中记录完整消息内容，可能包含敏感信息
            log.info("普通消息发送成功，消息ID: {}", messageKey);
        } catch (Exception e) {
            // 捕获并记录所有可能的异常
            log.error("普通消息发送失败: {}", e.getMessage(), e);
            // 包装异常并向上抛出，保留原始异常信息
            throw new GXBusinessException("发送普通消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送延迟消息
     * <p>
     * 延迟消息是指消息发送后，不会立即投递，而是在指定的时间后才投递到消费者进行消费
     * 适用于定时任务、订单超时取消等场景
     * </p>
     * <p>
     * 安全性考虑：
     * 1. 使用Objects.requireNonNull进行参数校验，避免NPE
     * 2. 对消息体进行非空检查，确保消息内容有效
     * 3. 对延迟时间进行有效性验证，防止无效的延迟设置
     * 4. 使用try-catch捕获所有可能的异常，确保异常信息被正确记录
     * 5. 日志记录关键操作节点，便于问题排查
     * 6. 避免在日志中记录完整的敏感消息内容，防止信息泄露
     * </p>
     * <p>
     * 内存安全：
     * 1. 使用MessageBuilder构建消息，避免直接操作底层消息结构
     * 2. 计算延迟时间时注意使用long类型避免整数溢出
     * 3. 不保存消息的引用，避免内存泄漏
     * 4. 所有资源在方法结束时自动释放，无需手动清理
     * </p>
     *
     * @param messageReqDto 待发送的消息对象，包含消息内容、主题、标签和延迟时间等信息
     * @return 消息ID，可用于后续跟踪消息
     * @throws NullPointerException 当messageReqDto或消息体为null时抛出
     * @throws GXBusinessException  当消息发送失败、延迟时间无效或参数无效时抛出异常
     */
    @Override
    public String sendDelayMessage(GXRocketMQMessageReqDto messageReqDto) {
        // 参数校验，防止空指针异常
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        Objects.requireNonNull(messageReqDto.getBody(), "消息内容不能为空");

        // 验证延迟时间的有效性
        if (messageReqDto.getDeliverTime() <= 0) {
            throw new GXBusinessException("延迟时间必须大于0秒");
        }

        log.info("发送延时消息开始，主题: {}, 标签: {}, 延迟: {}秒",
                messageReqDto.getTopic(), messageReqDto.getTag(), messageReqDto.getDeliverTime());
        try {
            // 处理消息到时的绝对时间（毫秒），使用long避免整数溢出
            long deliveryTimeMills = System.currentTimeMillis() + messageReqDto.getDeliverTime() * 1000L;

            // 获取消息唯一标识Key
            String messageKey = messageReqDto.getMessageKey();
            // 使用Builder模式构建消息，更安全且易于扩展
            MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
            // 设置消息Key，便于消息追踪
            if (CharSequenceUtil.isNotEmpty(messageKey)) {
                messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey);
            }
            Message<String> message = messageBuilder.build();

            // 发送延迟消息，指定投递时间
            SendResult sendResult = rocketMQTemplate.syncSendDeliverTimeMills(
                    getDestination(messageReqDto), message, deliveryTimeMills);

            // 记录成功信息，但不记录完整消息内容
            log.info("延迟消息发送完毕，消息ID: {}, 投递时间: {}", sendResult.getMsgId(),
                    deliveryTimeMills);
            return sendResult.getMsgId();
        } catch (Exception e) {
            // 捕获并记录所有可能的异常
            log.error("延迟消息发送失败: {}", e.getMessage(), e);
            // 包装异常并向上抛出，保留原始异常信息
            throw new GXBusinessException("发送延迟消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送同步消息
     * <p>
     * 同步消息是指消息发送方发出数据后，会在收到接收方发回响应之后才发下一个数据包
     * 适用于重要的通知消息，如重要通知邮件、营销短信等
     * </p>
     * <p>
     * 安全性考虑：
     * 1. 使用Objects.requireNonNull进行参数校验，避免NPE
     * 2. 对消息体进行非空检查，确保消息内容有效
     * 3. 使用try-catch捕获所有可能的异常，确保异常信息被正确记录
     * 4. 日志记录关键操作节点，便于问题排查
     * 5. 避免在日志中记录完整的敏感消息内容，防止信息泄露
     * </p>
     * <p>
     * 内存安全：
     * 1. 使用MessageBuilder构建消息，避免直接操作底层消息结构
     * 2. 不保存消息的引用，避免内存泄漏
     * 3. 所有资源在方法结束时自动释放，无需手动清理
     * </p>
     * <p>
     * 同步发送的特点：
     * 1. 可靠性高，确保消息被服务器接收
     * 2. 有阻塞等待，会影响发送方的性能
     * 3. 适合对可靠性要求高的业务场景
     * </p>
     *
     * @param messageReqDto 待发送的消息对象
     * @return 发送是否成功
     * @throws NullPointerException 当messageReqDto或消息体为null时抛出
     * @throws GXBusinessException  当消息发送失败或参数无效时抛出异常
     */
    @Override
    public boolean syncSend(GXRocketMQMessageReqDto messageReqDto) {
        // 参数校验，防止空指针异常
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        Objects.requireNonNull(messageReqDto.getBody(), "消息内容不能为空");

        log.info("同步发送消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());
        try {
            // 获取目标地址
            String destination = getDestination(messageReqDto);

            // 构建消息
            String messageKey = messageReqDto.getMessageKey();
            MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
            if (CharSequenceUtil.isNotEmpty(messageKey)) {
                messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey);
            }
            Message<String> message = messageBuilder.build();

            // 同步发送消息，会阻塞等待服务器响应
            SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
            // 记录成功信息，但不记录完整消息内容
            log.info("同步发送消息成功，消息ID: {}", sendResult.getMsgId());
            return true;
        } catch (Exception e) {
            // 捕获并记录所有可能的异常
            log.error("同步发送消息失败: {}", e.getMessage(), e);
            // 包装异常并向上抛出，保留原始异常信息
            throw new GXBusinessException("同步发送消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送异步消息
     * <p>
     * 异步消息是指发送方发出数据后，不等接收方发回响应，接着发送下个数据包
     * 适用于可靠性要求不高，但要求高吞吐量的场景，如日志收集
     * </p>
     * <p>
     * 安全性考虑：
     * 1. 使用Objects.requireNonNull进行参数校验，避免NPE
     * 2. 对消息体进行非空检查，确保消息内容有效
     * 3. 使用try-catch捕获所有可能的异常，确保异常信息被正确记录
     * 4. 日志记录关键操作节点，便于问题排查
     * 5. 避免在日志中记录完整的敏感消息内容，防止信息泄露
     * 6. 在回调函数中妥善处理异常，防止回调执行失败导致问题
     * </p>
     * <p>
     * 内存安全：
     * 1. 使用MessageBuilder构建消息，避免直接操作底层消息结构
     * 2. 不保存消息的引用，避免内存泄漏
     * 3. 使用匿名内部类实现回调，注意避免引用外部对象导致内存泄漏
     * 4. 所有资源在方法结束时自动释放，无需手动清理
     * </p>
     * <p>
     * 异步发送的特点：
     * 1. 不阻塞发送线程，提高系统吞吐量
     * 2. 通过回调函数获知消息发送结果
     * 3. 适合对性能要求高，但对可靠性要求不是特别高的场景
     * </p>
     *
     * @param messageReqDto 待发送的消息对象
     * @return 发送请求是否成功提交
     * @throws NullPointerException 当messageReqDto或消息体为null时抛出
     * @throws GXBusinessException  当消息参数无效时抛出异常
     */
    @Override
    public boolean sendAsync(GXRocketMQMessageReqDto messageReqDto) {
        // 参数校验，防止空指针异常
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        Objects.requireNonNull(messageReqDto.getBody(), "消息内容不能为空");

        log.info("异步发送消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());
        try {
            // 构建消息
            String messageKey = messageReqDto.getMessageKey();
            MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
            if (CharSequenceUtil.isNotEmpty(messageKey)) {
                messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey);
            }
            Message<String> message = messageBuilder.build();

            // 异步发送消息，通过回调函数处理发送结果
            final String topic = messageReqDto.getTopic(); // 捕获外部变量，避免在回调中直接引用messageReqDto
            final String tag = messageReqDto.getTag();
            rocketMQTemplate.asyncSend(getDestination(messageReqDto), message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    // 记录成功信息，但不记录完整消息内容
                    log.info("异步消息发送成功，主题: {}, 标签: {}, 消息ID: {}",
                            topic, tag, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable throwable) {
                    // 记录失败信息，包含详细的异常堆栈
                    log.error("异步消息发送失败，主题: {}, 标签: {}, 错误: {}",
                            topic, tag, throwable.getMessage(), throwable);
                }
            });

            log.info("异步消息发送请求已提交");
            return true;
        } catch (Exception e) {
            // 捕获并记录所有可能的异常
            log.error("异步消息发送请求提交失败: {}", e.getMessage(), e);
            // 包装异常并向上抛出，保留原始异常信息
            throw new GXBusinessException("异步消息发送请求提交失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送单向(Oneway)消息
     * <p>
     * 单向消息是指只负责发送消息，不等待服务器回应且没有回调函数触发
     * 适用于不关心发送结果的场景，如日志收集
     * 相比于异步消息，单向消息发送方式耗时非常短，性能最高
     * </p>
     * <p>
     * 安全性考虑：
     * 1. 使用Objects.requireNonNull进行参数校验，避免NPE
     * 2. 对消息体进行非空检查，确保消息内容有效
     * 3. 使用try-catch捕获所有可能的异常，确保异常信息被正确记录
     * 4. 日志记录关键操作节点，便于问题排查
     * 5. 避免在日志中记录完整的敏感消息内容，防止信息泄露
     * </p>
     * <p>
     * 内存安全：
     * 1. 使用MessageBuilder构建消息，避免直接操作底层消息结构
     * 2. 不保存消息的引用，避免内存泄漏
     * 3. 所有资源在方法结束时自动释放，无需手动清理
     * </p>
     * <p>
     * 单向发送的特点：
     * 1. 性能最高，不关心消息是否成功发送到服务端
     * 2. 没有发送结果反馈，也没有重试机制
     * 3. 适合对可靠性要求极低，但对性能要求极高的场景，如监控数据上报
     * 4. 注意：由于不关心发送结果，存在消息丢失的风险，不适用于业务核心流程
     * </p>
     *
     * @param messageReqDto 待发送的消息对象
     * @return 发送请求是否成功提交
     * @throws NullPointerException 当messageReqDto或消息体为null时抛出
     * @throws GXBusinessException  当消息参数无效时抛出异常
     */
    @Override
    public boolean sendOneway(GXRocketMQMessageReqDto messageReqDto) {
        // 参数校验，防止空指针异常
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");
        Objects.requireNonNull(messageReqDto.getBody(), "消息内容不能为空");

        log.info("发送单向消息开始，主题: {}, 标签: {}", messageReqDto.getTopic(), messageReqDto.getTag());
        try {
            // 构建消息
            String messageKey = messageReqDto.getMessageKey();
            MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(messageReqDto.getBody());
            if (CharSequenceUtil.isNotEmpty(messageKey)) {
                messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey);
            }
            Message<String> message = messageBuilder.build();

            // 发送单向消息，不关心发送结果
            rocketMQTemplate.sendOneWay(getDestination(messageReqDto), message);

            // 记录发送操作，但不能确认是否真正发送成功
            log.info("单向消息发送操作完成，主题: {}, 标签: {}",
                    messageReqDto.getTopic(), messageReqDto.getTag());
            return true;
        } catch (Exception e) {
            // 捕获并记录所有可能的异常
            log.error("单向消息发送失败: {}", e.getMessage(), e);
            // 包装异常并向上抛出，保留原始异常信息
            throw new GXBusinessException("单向消息发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 组装消息发送的目标地址
     * <p>
     * 根据消息的主题(Topic)和标签(Tag)组装完整的目标地址
     * 格式为：topic:tag，如果没有tag则只返回topic
     * </p>
     * <p>
     * 安全性考虑：
     * 1. 使用Objects.requireNonNull进行空指针检查，避免NPE
     * 2. 对Topic进行非空检查，确保消息有正确的路由信息
     * 3. 使用CharSequenceUtil工具类处理字符串，避免可能的字符串操作异常
     * </p>
     *
     * @param messageReqDto 消息信息对象，包含主题和标签信息
     * @return 组装后的目标地址字符串
     * @throws NullPointerException 当messageReqDto为null时抛出
     * @throws GXBusinessException  当主题为空时抛出异常
     */
    private String getDestination(GXRocketMQMessageReqDto messageReqDto) {
        // 参数非空检查，避免NPE
        Objects.requireNonNull(messageReqDto, "消息对象不能为空");

        // Topic是必须的，没有Topic无法正确路由消息
        if (CharSequenceUtil.isEmpty(messageReqDto.getTopic())) {
            throw new GXBusinessException("消息主题(Topic)不能为空");
        }

        // Tag是可选的，如果没有Tag则直接返回Topic
        if (CharSequenceUtil.isEmpty(messageReqDto.getTag())) {
            return messageReqDto.getTopic();
        }

        // 组装完整的目标地址：topic:tag
        return CharSequenceUtil.format("{}:{}", messageReqDto.getTopic(), messageReqDto.getTag());
    }
}