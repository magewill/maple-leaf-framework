package cn.maple.canal.service;

import cn.hutool.core.lang.Dict;

/**
 * Canal消息解析服务接口
 * <p>
 * 该接口负责解析从Canal服务接收到的数据库变更消息，将原始消息转换为应用程序可处理的数据结构。
 * 通常与RabbitMQ等消息队列结合使用，接收Canal推送的变更事件。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * @Service
 * public class CustomCanalMessageParseServiceImpl implements GXCanalMessageParseService {
 *     @Override
 *     public Dict parseMessage(String message) {
 *         // 1. 验证消息格式
 *         if (!JSONUtil.isTypeJSON(message)) {
 *             return Dict.create();
 *         }
 *         
 *         // 2. 解析消息内容
 *         GXCanalDataDto canalDataDto = JSONUtil.toBean(message, GXCanalDataDto.class);
 *         
 *         // 3. 根据消息类型分发处理
 *         String type = canalDataDto.getType();
 *         // 执行相应的业务逻辑...
 *         
 *         return Dict.create().set("status", "success");
 *     }
 * }
 * </pre>
 * </p>
 */
public interface GXCanalMessageParseService {
    /**
     * 解析RabbitMQ中canal的消息信息
     * <p>
     * 该方法接收从RabbitMQ队列中获取的原始Canal消息，进行解析和处理。
     * 解析过程通常包括：JSON反序列化、数据验证、业务处理分发等步骤。
     * 处理结果通过Dict返回，可包含处理状态、处理结果等信息。
     * </p>
     * <p>
     * 安全性考虑：
     * 1. 应对输入消息进行严格验证，防止非法JSON导致解析异常
     * 2. 对空值和异常情况进行妥善处理，确保服务稳定性
     * 3. 敏感操作应进行日志记录，便于问题追踪
     * </p>
     *
     * @param message 从RabbitMQ接收的原始消息字符串，通常为JSON格式
     * @return Dict 处理结果，包含处理状态和相关业务数据
     */
    Dict parseMessage(String message);
}
