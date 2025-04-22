package cn.maple.sse.service;

import cn.maple.core.framework.service.GXBusinessService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 基于Spring Boot实现的Server-sent events（服务器发送事件）服务
 * <p>
 * 该接口提供了SSE（Server-Sent Events）的核心功能实现，用于在服务器和客户端之间建立单向通信通道，
 * 使服务器能够主动向客户端推送消息。相比WebSocket，SSE更轻量且专注于服务器到客户端的单向通信。
 * </p>
 * 
 * <p>
 * 安全说明：
 * 1. 使用线程安全的ConcurrentHashMap存储客户端连接，防止并发问题
 * 2. 支持消息拆分功能，避免大消息阻塞传输通道
 * 3. 实现了完善的异常处理和资源释放机制
 * 4. 提供超时控制，防止资源泄露
 * 5. 支持客户端唯一标识，避免连接混淆
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 注入服务
 * @Autowired
 * private GXSseEmitterService sseEmitterService;
 * 
 * // 创建SSE连接（在控制器中）
 * @GetMapping("/connect")
 * public SseEmitter connect(@RequestParam(required = false) String clientId) {
 *     // 创建连接，超时时间设置为5分钟
 *     return sseEmitterService.createSseConnect(clientId, 300);
 * }
 * 
 * // 向特定客户端发送消息
 * @PostMapping("/send/{clientId}")
 * public void sendMessage(@PathVariable String clientId, @RequestBody String message) {
 *     // 发送消息，启用消息拆分
 *     sseEmitterService.sendMessageToOneClient(clientId, message, true);
 * }
 * 
 * // 向所有客户端广播消息
 * @PostMapping("/broadcast")
 * public void broadcast(@RequestBody String message) {
 *     sseEmitterService.sendMessageToAllClient(message);
 * }
 * </pre>
 * </p>
 */
public interface GXSseEmitterService extends GXBusinessService {
    /**
     * 创建SSE连接
     * <p>
     * 建立客户端与服务器的SSE长连接，并注册相关回调函数。
     * 该方法会创建一个新的SseEmitter实例，并将其与指定的客户端ID关联。
     * 如果客户端ID为空，将自动生成一个唯一标识符。
     * </p>
     *
     * @param clientId 客户端ID，用于标识连接的客户端，可为空
     * @param timeout  连接超时时间（秒），建议设置合理的超时时间以防止资源泄露
     * @return 创建的SseEmitter对象，用于后续的消息推送
     */
    SseEmitter createSseConnect(String clientId, long timeout);

    /**
     * 根据客户端ID获取SseEmitter对象
     * <p>
     * 通过客户端ID查找对应的SSE连接对象，用于后续操作或状态检查。
     * 如果指定客户端ID的连接不存在，将返回null。
     * </p>
     *
     * @param clientId 客户端ID
     * @return 对应客户端的SseEmitter对象，如果不存在则返回null
     */
    SseEmitter getSseEmitterByClientId(String clientId);

    /**
     * 发送消息给所有客户端
     * <p>
     * 将指定消息广播给所有已连接的客户端。
     * 该方法会遍历所有活跃的SSE连接，并向每个连接发送相同的消息。
     * 如果没有活跃连接，该方法不会执行任何操作。
     * </p>
     *
     * @param msg 要广播的消息内容
     */
    void sendMessageToAllClient(String msg);

    /**
     * 给指定客户端发送消息
     * <p>
     * 向特定客户端推送消息，支持消息拆分功能。
     * 当消息较长时，可以启用拆分功能将消息分成多个小块发送，
     * 避免单个大消息可能导致的传输问题。
     * </p>
     *
     * @param clientId 目标客户端ID
     * @param msg      要发送的消息内容
     * @param splitMsg 是否需要拆分消息，true表示将长消息拆分成多段发送
     */
    void sendMessageToOneClient(String clientId, String msg, boolean splitMsg);

    /**
     * 关闭SSE连接
     * <p>
     * 主动关闭与指定客户端的SSE连接并释放相关资源。
     * 该方法会调用SseEmitter的complete方法结束连接，
     * 并从连接缓存中移除该客户端的记录。
     * </p>
     *
     * @param clientId 要关闭连接的客户端ID
     */
    void closeConnect(String clientId);

    /**
     * 消息拆分
     * <p>
     * 将长消息拆分成多个小消息，以提高传输效率和稳定性。
     * 拆分策略采用随机长度拆分，避免固定长度可能导致的问题。
     * 该方法主要用于处理大型消息，防止单个大消息阻塞传输通道。
     * </p>
     *
     * @param msg    待拆分的消息内容
     * @param length 拆分的基准长度参数，实际拆分长度会在此基础上随机化
     * @return 拆分后的消息列表
     */
    List<String> splitMessage(String msg, int length);
}
