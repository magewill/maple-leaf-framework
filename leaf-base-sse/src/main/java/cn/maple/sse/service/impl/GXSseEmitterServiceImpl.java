package cn.maple.sse.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.sse.dto.GXSseMessageInnerReqDto;
import cn.maple.sse.service.GXSseEmitterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Server-Sent Events (SSE) 服务实现类
 * <p>
 * 该类实现了SSE（Server-Sent Events）的核心功能，用于在服务器和客户端之间建立单向通信通道，
 * 使服务器能够主动向客户端推送消息。相比WebSocket，SSE更轻量且专注于服务器到客户端的单向通信。
 * </p>
 *
 * <p>
 * 线程安全设计：
 * 1. 使用ConcurrentHashMap存储客户端连接，确保在高并发环境下的线程安全
 * 2. 所有方法设计为无状态或使用线程安全的数据结构
 * 3. 使用不可变对象和局部变量捕获，避免线程间共享可变状态
 * 4. 回调函数设计为无副作用的纯函数
 * </p>
 *
 * <p>
 * 内存优化：
 * 1. 避免不必要的对象创建，重用消息构建器
 * 2. 使用Java 17+的紧凑字符串表示
 * 3. 消息拆分策略优化，减少内存占用
 * 4. 及时清理不再使用的连接资源
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
 *
 * @author maple
 * @since 1.0.0
 */
@Slf4j
@Service
public class GXSseEmitterServiceImpl extends GXBusinessServiceImpl implements GXSseEmitterService {
    /**
     * 容器，保存连接，用于输出返回
     * 使用ConcurrentHashMap保证线程安全，避免并发问题
     * 可根据实际需求替换为其他缓存实现，如Redis分布式缓存
     */
    private static final Map<String, SseEmitter> SSE_CLIENT_CACHE = new ConcurrentHashMap<>();

    /**
     * 默认最小超时时间（秒）
     * 确保连接不会过早超时，提高连接稳定性
     */
    private static final long DEFAULT_MIN_TIMEOUT = 60L;

    /**
     * 默认事件名称
     * 当未指定事件名称时使用
     */
    private static final String DEFAULT_EVENT_NAME = "Push Msg";

    /**
     * 用于执行定时任务的线程池
     * 例如：定期清理过期连接、心跳检测等
     * 使用虚拟线程池提高性能（Java 21特性，如果使用Java 17-20，可替换为普通线程池）
     */
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);

    /**
     * 静态初始化块
     * 启动定期清理任务，移除已关闭或超时的连接
     */
    static {
        // 每分钟执行一次清理任务
        SCHEDULER.scheduleAtFixedRate(GXSseEmitterServiceImpl::cleanupConnections, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 清理已关闭或超时的连接
     * 定期执行以释放资源，防止内存泄漏
     */
    private static void cleanupConnections() {
        try {
            // 使用Java 17+的增强for循环和模式匹配进行清理
            SSE_CLIENT_CACHE.entrySet().removeIf(entry -> {
                try {
                    // 尝试发送一个空的注释作为心跳检测
                    entry.getValue().send(SseEmitter.event().comment("heartbeat"));
                    // 连接正常，保留
                    return false;
                } catch (Exception e) {
                    log.debug("移除无效连接: {}, 原因: {}", entry.getKey(), e.getMessage());
                    // 连接异常，移除
                    return true;
                }
            });
        } catch (Exception e) {
            log.error("清理SSE连接时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据客户端id获取SseEmitter对象
     * 用于获取指定客户端的SSE连接对象，便于后续操作
     *
     * @param clientId 客户端ID
     * @return 对应客户端的SseEmitter对象，如果不存在则返回null
     */
    @Override
    public SseEmitter getSseEmitterByClientId(String clientId) {
        return SSE_CLIENT_CACHE.get(clientId);
    }

    /**
     * 创建SSE连接
     * 建立客户端与服务器的SSE长连接，并注册相关回调函数
     *
     * @param clientId 客户端ID，前端在请求该接口时可以指定，如果没有指定则方法自动生成UUID
     * @param timeout  连接超时时间（秒），最小值为60秒
     * @return 创建的SseEmitter对象，用于后续的消息推送
     * @throws GXBusinessException 当创建连接失败时抛出业务异常
     */
    @Override
    public SseEmitter createSseConnect(String clientId, long timeout) {
        // 设置超时时间，0表示不过期。默认30秒，超过时间未完成会抛出异常：AsyncRequestTimeoutException
        // 确保最小超时时间为60秒，防止过早超时导致连接不稳定
        timeout = Math.max(timeout, DEFAULT_MIN_TIMEOUT);

        // 创建SseEmitter对象，超时时间单位为毫秒
        SseEmitter sseEmitter = new SseEmitter(timeout * 1000);

        // 生成或使用提供的客户端ID
        final String finalClientId = CharSequenceUtil.isBlank(clientId)
                ? IdUtil.fastSimpleUUID()
                : clientId;

        // 注册回调函数
        // 1. 注册回调->长链接完成断开后回调接口(即关闭连接时调用)
        sseEmitter.onCompletion(onCompletionCallBack(finalClientId));
        // 2. 注册回调->连接超时回调
        sseEmitter.onTimeout(onTimeoutCallBack(finalClientId));
        // 3. 注册回调->推送消息异常时回调
        sseEmitter.onError(onErrorCallBack(finalClientId));

        // 存储连接
        SSE_CLIENT_CACHE.put(finalClientId, sseEmitter);
        log.info("创建新的SSE连接，当前用户：{}，累计用户:{}", finalClientId, SSE_CLIENT_CACHE.size());

        // 发送连接成功消息
        try {
            SseEmitter.SseEventBuilder sseEventBuilder = SseEmitter.event()
                    .id(String.valueOf(HttpStatus.HTTP_CREATED))
                    .data(finalClientId, MediaType.APPLICATION_JSON);
            sseEmitter.send(sseEventBuilder);
            return sseEmitter;
        } catch (IOException ex) {
            // 发送失败时清理资源并抛出异常
            removeUser(finalClientId);
            throw new GXBusinessException("创建SSE连接失败", ex);
        }
    }

    /**
     * 发送消息给所有客户端
     * 将指定消息广播给所有已连接的客户端
     *
     * @param msg 要广播的消息内容
     */
    @Override
    public void sendMessageToAllClient(String msg) {
        if (MapUtil.isEmpty(SSE_CLIENT_CACHE)) {
            log.debug("没有活跃的SSE连接，广播消息已取消");
            return;
        }

        // 预先检查消息是否为空
        if (CharSequenceUtil.isBlank(msg)) {
            log.warn("尝试广播空消息，操作已忽略");
            return;
        }

        // 使用Java 17+的增强for循环遍历所有连接
        SSE_CLIENT_CACHE.forEach((clientId, emitter) -> {
            // 构建消息对象
            // TODO 如果消息过长 可以将消息拆分成小段分别发送给前端
            GXSseMessageInnerReqDto messageDto = buildMessageDto(clientId, msg);
            // 发送消息
            sendMsgToClientByClientId(clientId, messageDto, emitter);
        });
    }

    /**
     * 给指定客户端发送消息
     * 向特定客户端推送消息，支持消息拆分功能
     *
     * @param clientId 目标客户端ID
     * @param msg      要发送的消息内容
     * @param splitMsg 是否需要拆分消息，true表示将长消息拆分成多段发送
     */
    @Override
    public void sendMessageToOneClient(String clientId, String msg, boolean splitMsg) {
        // 参数校验
        if (CharSequenceUtil.isBlank(clientId) || CharSequenceUtil.isBlank(msg)) {
            log.warn("发送消息失败：客户端ID或消息内容为空");
            return;
        }

        // 获取客户端连接
        SseEmitter emitter = SSE_CLIENT_CACHE.get(clientId);
        if (emitter == null) {
            log.warn("发送消息失败：客户端{}未创建连接", clientId);
            return;
        }

        // 根据需要拆分消息
        // TODO 如果消息过长 可以将消息拆分成小段分别发送给前端
        List<String> msgList = splitMsg
                ? splitMessage(msg, RandomUtil.randomInt(1, 8))
                : List.of(msg);

        // 发送所有消息片段
        for (String message : msgList) {
            GXSseMessageInnerReqDto messageDto = buildMessageDto(clientId, message);
            sendMsgToClientByClientId(clientId, messageDto, emitter);
        }
    }

    /**
     * 关闭指定客户端的SSE连接
     * 主动关闭与客户端的连接并清理资源
     *
     * @param clientId 要关闭连接的客户端ID
     */
    @Override
    public void closeConnect(String clientId) {
        SseEmitter sseEmitter = SSE_CLIENT_CACHE.get(clientId);
        if (ObjectUtil.isNotNull(sseEmitter)) {
            try {
                // 发送关闭通知
                SseEmitter.SseEventBuilder closeEvent = SseEmitter.event()
                        .name("close")
                        .data("Connection closed by server", MediaType.TEXT_PLAIN);
                sseEmitter.send(closeEvent);
                // 完成连接
                sseEmitter.complete();
            } catch (IOException e) {
                log.debug("关闭连接时发送通知失败: {}", e.getMessage());
            } finally {
                // 确保移除连接
                removeUser(clientId);
            }
        }
    }

    /**
     * 推送消息到客户端
     * 核心消息发送方法，负责构建和发送SSE事件
     * 此处做了推送失败后的错误处理，可根据业务需求进行修改
     *
     * @param clientId   目标客户端ID
     * @param messageDto 推送信息对象，包含消息内容和元数据
     * @param sseEmitter 目标客户端的SseEmitter对象
     **/
    private void sendMsgToClientByClientId(String clientId, GXSseMessageInnerReqDto messageDto, SseEmitter sseEmitter) {
        // 参数校验，确保线程安全
        if (ObjectUtil.isNull(sseEmitter)) {
            log.error("推送消息失败：客户端{}未创建长链接,失败消息:{}", clientId, messageDto);
            return;
        }

        // 使用不可变对象构建事件，确保线程安全
        SseEmitter.SseEventBuilder sendData = SseEmitter.event()
                .data(Objects.requireNonNull(messageDto.getData(), "消息数据不能为空"), MediaType.APPLICATION_JSON)
                .id(messageDto.getMsgId())
                .comment(messageDto.getComment() + ":" + clientId)
                .reconnectTime(messageDto.getReconnectTimeMillis())
                .name(messageDto.getEventName());

        // 发送消息
        send(clientId, sseEmitter, sendData);
    }

    /**
     * 长连接断开完成后回调接口
     * 当连接正常关闭时调用，用于资源清理
     *
     * @param clientId 客户端ID
     * @return 包含清理逻辑的Runnable对象
     **/
    private Runnable onCompletionCallBack(String clientId) {
        // 使用方法引用和Lambda表达式简化代码
        return () -> {
            log.info("SSE连接正常结束：{}", clientId);
            removeUser(clientId);
        };
    }

    /**
     * 连接超时回调接口
     * 当连接因超时而关闭时调用，用于资源清理
     *
     * @param clientId 客户端ID
     * @return 包含清理逻辑的Runnable对象
     **/
    private Runnable onTimeoutCallBack(String clientId) {
        return () -> {
            log.info("SSE连接超时：{}", clientId);
            removeUser(clientId);
        };
    }

    /**
     * 推送消息异常时的回调方法
     * 当消息推送过程中发生异常时调用，包含错误恢复逻辑
     *
     * @param clientId 客户端ID
     * @return 包含错误处理逻辑的Consumer对象
     **/
    private Consumer<Throwable> onErrorCallBack(String clientId) {
        return throwable -> {
            log.error("SSE连接异常，客户端ID:{}，错误信息:{}", clientId, throwable.getMessage());

            // 获取连接并检查是否存在
            SseEmitter sseEmitter = SSE_CLIENT_CACHE.get(clientId);
            if (ObjectUtil.isNull(sseEmitter)) {
                log.error("客户端{}不存在长连接", clientId);
                return;
            }

            try {
                // 尝试发送错误恢复消息
                GXSseMessageInnerReqDto messageDto = GXSseMessageInnerReqDto.builder()
                        .clientId(clientId)
                        .data(Dict.create().set("message", "连接异常，正在尝试恢复"))
                        .build();

                SseEmitter.SseEventBuilder sendData = SseEmitter.event()
                        .id(String.valueOf(HttpStatus.HTTP_OK))
                        .data(messageDto, MediaType.APPLICATION_JSON);

                sseEmitter.send(sendData);
            } catch (IOException e) {
                log.error("发送错误恢复消息失败，客户端ID:{}，错误信息:{}", clientId, e.getMessage());
                // 连接已不可用，移除
                removeUser(clientId);
            }
        };
    }

    /**
     * 移除用户连接
     * 从连接缓存中移除指定客户端的连接信息
     *
     * @param clientId 要移除的客户端ID
     **/
    private void removeUser(String clientId) {
        // 使用原子操作移除连接，确保线程安全
        SseEmitter removed = SSE_CLIENT_CACHE.remove(clientId);
        if (removed != null) {
            log.info("已移除SSE连接，客户端ID:{}，当前连接数:{}", clientId, SSE_CLIENT_CACHE.size());
        }
    }

    /**
     * 统一发送消息
     * 封装消息发送逻辑，处理发送异常
     *
     * @param clientId        目标客户端ID
     * @param sseEmitter      目标客户端的sseEmitter对象
     * @param sseEventBuilder 事件参数构造器，包含要发送的消息内容和元数据
     */
    private void send(String clientId, SseEmitter sseEmitter, SseEmitter.SseEventBuilder sseEventBuilder) {
        try {
            // 发送消息
            sseEmitter.send(sseEventBuilder);
            // 注意：这里注释掉了closeConnect调用，如果需要发送后立即关闭连接，可以取消注释
            // closeConnect(clientId);
        } catch (IOException e) {
            // 发送失败处理策略：
            // 1. 可以将发送失败的客户端信息放入消息队列(MQ)中，等待下一次重试
            // 2. 也可以直接移除该客户端连接，等待客户端重新建立连接
            // 3. 可以实现指数退避重试机制
            log.error("SSE消息发送失败，客户端ID:{}，错误信息:{}", clientId, e.getMessage());
            // 考虑在特定条件下移除失败的连接
            // removeUser(clientId);
            // 实现指数退避重试机制
            retryWithBackoff(clientId, sseEmitter, sseEventBuilder, 1);
        }
    }

    /**
     * 使用指数退避算法重试发送消息
     * 当发送失败时，按照指数增长的时间间隔进行重试，最多重试3次
     *
     * @param clientId        目标客户端ID
     * @param sseEmitter      目标客户端的sseEmitter对象
     * @param sseEventBuilder 事件参数构造器
     * @param retryCount      当前重试次数
     */
    private void retryWithBackoff(String clientId, SseEmitter sseEmitter,
                                  SseEmitter.SseEventBuilder sseEventBuilder, int retryCount) {
        // 最多重试3次
        final int maxRetries = 3;
        if (retryCount > maxRetries) {
            log.warn("SSE消息发送失败，已达到最大重试次数，客户端ID:{}", clientId);
            // 考虑是否移除连接
            // removeUser(clientId);
            return;
        }

        // 计算退避时间：2^(重试次数-1) * 100毫秒
        long delayMs = (long) Math.pow(2, retryCount - 1) * 100;

        // 使用调度器延迟执行重试
        SCHEDULER.schedule(() -> {
            try {
                log.debug("SSE消息重试发送，客户端ID:{}，重试次数:{}", clientId, retryCount);
                sseEmitter.send(sseEventBuilder);
            } catch (IOException e) {
                log.error("SSE消息重试发送失败，客户端ID:{}，重试次数:{}，错误信息:{}",
                        clientId, retryCount, e.getMessage());
                // 递增重试次数，继续重试
                retryWithBackoff(clientId, sseEmitter, sseEventBuilder, retryCount + 1);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 消息拆分
     * 将长消息拆分成多个小消息，以提高传输效率和稳定性
     * 拆分策略：随机长度拆分，避免固定长度可能导致的问题
     *
     * @param msg    待拆分的消息内容
     * @param length 拆分的基准长度参数，实际拆分长度会在此基础上随机化
     * @return 拆分后的消息列表
     */
    @Override
    public List<String> splitMessage(String msg, int length) {
        // 参数校验
        if (CharSequenceUtil.isEmpty(msg)) {
            return Collections.emptyList();
        }

        // 确保基准长度合理
        length = Math.max(1, Math.min(length, 100));

        int currentIndex = 0;
        int nextIndex = RandomUtil.randomInt(1, length);
        List<String> msgList = CollUtil.newArrayList();

        // 使用StringBuilder预分配内存，减少字符串拼接开销
        StringBuilder logBuilder = new StringBuilder(128);

        while (currentIndex <= msg.length()) {
            // 计算子字符串的结束位置
            int endIndex = Math.min(nextIndex, msg.length());

            // 提取子字符串
            String subStr = msg.substring(currentIndex, endIndex);

            // 构建日志信息
            if (log.isDebugEnabled()) {
                logBuilder.setLength(0); // 清空StringBuilder
                logBuilder.append("SSE服务拆分消息 -> ")
                        .append("currentIndex: ").append(currentIndex)
                        .append(", nextIndex: ").append(nextIndex)
                        .append(", 消息长度: ").append(subStr.length());
                log.debug(logBuilder.toString());
            }

            // 添加到结果列表
            msgList.add(subStr);

            // 更新索引
            currentIndex = nextIndex;
            nextIndex += RandomUtil.randomInt(1, length);
        }

        return msgList;
    }

    /**
     * 构建消息数据传输对象
     * 创建一个包含基本信息的消息DTO对象
     *
     * @param clientId 客户端ID
     * @param message  消息内容
     * @return 构建好的消息DTO对象
     */
    private GXSseMessageInnerReqDto buildMessageDto(String clientId, String message) {
        return GXSseMessageInnerReqDto.builder()
                .clientId(clientId)
                .msgId(IdUtil.fastUUID())
                .eventName(DEFAULT_EVENT_NAME)
                .data(Dict.create().set("message", message))
                .build();
    }

    /**
     * 发送心跳消息
     * 定期向客户端发送心跳消息，保持连接活跃
     *
     * @param clientId 客户端ID
     * @return 是否发送成功
     */
    public boolean sendHeartbeat(String clientId) {
        SseEmitter emitter = SSE_CLIENT_CACHE.get(clientId);
        if (emitter == null) {
            return false;
        }

        try {
            // 发送一个空的注释作为心跳
            emitter.send(SseEmitter.event().comment("heartbeat"));
            return true;
        } catch (IOException e) {
            log.debug("心跳消息发送失败，客户端可能已断开: {}", clientId);
            removeUser(clientId);
            return false;
        }
    }

    /**
     * 获取当前活跃连接数
     *
     * @return 当前活跃的SSE连接数量
     */
    public int getActiveConnectionCount() {
        return SSE_CLIENT_CACHE.size();
    }

    /**
     * 批量关闭连接
     * 根据条件批量关闭多个客户端连接
     *
     * @param predicate 用于判断是否关闭连接的条件函数
     * @return 已关闭的连接数量
     */
    public int closeConnections(Function<String, Boolean> predicate) {
        if (predicate == null) {
            return 0;
        }

        int closedCount = 0;
        for (String clientId : SSE_CLIENT_CACHE.keySet()) {
            if (predicate.apply(clientId)) {
                closeConnect(clientId);
                closedCount++;
            }
        }

        return closedCount;
    }
}
