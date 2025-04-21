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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
        timeout = Math.max(timeout, 60);
        // 创建SseEmitter对象，超时时间单位为毫秒
        SseEmitter sseEmitter = new SseEmitter(timeout * 1000);
        // 检查客户端ID是否为空，为空则生成一个UUID作为客户端ID
        if (CharSequenceUtil.isBlank(clientId)) {
            clientId = IdUtil.fastSimpleUUID();
        }
        // 1. 注册回调->长链接完成断开后回调接口(即关闭连接时调用)
        sseEmitter.onCompletion(onCompletionCallBack(clientId));
        // 2. 注册回调->连接超时回调
        sseEmitter.onTimeout(onTimeoutCallBack(clientId));
        // 3. 注册回调->推送消息异常时回调
        sseEmitter.onError(onErrorCallBack(clientId));
        SSE_CLIENT_CACHE.put(clientId, sseEmitter);
        log.info("创建新的sse连接，当前用户：{}    累计用户:{}", clientId, SSE_CLIENT_CACHE.size());
        // 注册成功返回用户信息
        SseEmitter.SseEventBuilder sseEventBuilder = SseEmitter.event().id(String.valueOf(HttpStatus.HTTP_CREATED)).data(clientId, MediaType.APPLICATION_JSON);
        try {
            sseEmitter.send(sseEventBuilder);
        } catch (IOException ex) {
            throw new GXBusinessException("创建SSE连接失败", ex);
        }
        return sseEmitter;
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
            return;
        }
        // 判断发送的消息是否为空
        for (Map.Entry<String, SseEmitter> entry : SSE_CLIENT_CACHE.entrySet()) {
            // TODO 如果消息过长 可以将消息拆分成小段分别发送给前端
            GXSseMessageInnerReqDto messageDto = GXSseMessageInnerReqDto.builder()
                    .clientId(entry.getKey())
                    .data(Dict.create().set("message", msg))
                    .build();
            sendMsgToClientByClientId(entry.getKey(), messageDto, entry.getValue());
        }
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
        // TODO 如果消息过长 可以将消息拆分成小段分别发送给前端
        List<String> msgLst = splitMsg ? splitMessage(msg, RandomUtil.randomInt(1, 8)) : CollUtil.newArrayList(msg);
        for (String message : msgLst) {
            GXSseMessageInnerReqDto messageVo = GXSseMessageInnerReqDto.builder().
                    clientId(clientId)
                    .msgId(IdUtil.fastUUID())
                    .eventName("Push Msg")
                    .data(Dict.create().set("message", message))
                    .build();
            sendMsgToClientByClientId(clientId, messageVo, SSE_CLIENT_CACHE.get(clientId));
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
            sseEmitter.complete();
            removeUser(clientId);
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
        if (ObjectUtil.isNull(sseEmitter)) {
            log.error("推送消息失败：客户端{}未创建长链接,失败消息:{}", clientId, messageDto.toString());
            return;
        }
        SseEmitter.SseEventBuilder sendData = SseEmitter.event()
                .data(messageDto.getData(), MediaType.APPLICATION_JSON)
                .id(messageDto.getMsgId())
                .comment(messageDto.getComment() + ":" + clientId)
                .reconnectTime(messageDto.getReconnectTimeMillis())
                .name(messageDto.getEventName());
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
        return () -> {
            log.info("结束连接：{}", clientId);
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
            log.info("连接超时：{}", clientId);
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
            log.error("GXSseEmitterServiceImpl[errorCallBack]：连接异常,客户端ID:{}", clientId);
            SseEmitter sseEmitter = SSE_CLIENT_CACHE.get(clientId);
            if (ObjectUtil.isNull(sseEmitter)) {
                log.error("客户端{}不存在长连接。", clientId);
                return;
            }
            GXSseMessageInnerReqDto messageDto = GXSseMessageInnerReqDto.builder().clientId(clientId).data(Dict.create().set("message", "失败后重新推送")).build();
            SseEmitter.SseEventBuilder sendData = SseEmitter.event().id(String.valueOf(HttpStatus.HTTP_OK)).data(messageDto, MediaType.APPLICATION_JSON);
            send(clientId, sseEmitter, sendData);
        };
    }

    /**
     * 移除用户连接
     * 从连接缓存中移除指定客户端的连接信息
     *
     * @param clientId 要移除的客户端ID
     **/
    private void removeUser(String clientId) {
        SSE_CLIENT_CACHE.remove(clientId);
        log.info("SseEmitterServiceImpl[removeUser]:移除用户：{}", clientId);
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
            sseEmitter.send(sseEventBuilder);
            // 注意：这里注释掉了closeConnect调用，如果需要发送后立即关闭连接，可以取消注释
            // closeConnect(clientId);
        } catch (IOException e) {
            // 发送失败处理策略：
            // 1. 可以将发送失败的客户端信息放入消息队列(MQ)中，等待下一次重试
            // 2. 也可以直接移除该客户端连接，等待客户端重新建立连接
            // 3. 可以实现指数退避重试机制
            log.error("连接异常,向SSE客户端发送消息失败，客户端ID:{},异常信息:{}", clientId, e.getMessage());
            // 考虑在特定条件下移除失败的连接
            // removeUser(clientId);
        }
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
        if (CharSequenceUtil.isEmpty(msg)) {
            return Collections.emptyList();
        }
        int currentIndex = 0;
        int nextIndex = RandomUtil.randomInt(1, length);
        List<String> msgLst = CollUtil.newArrayList();
        while (currentIndex <= msg.length()) {
            String subStr = msg.substring(currentIndex, Math.min(nextIndex, msg.length()));
            log.info("SSE服务拆分消息日志信息 ->>>> currentIndex : {} , nextIndex : {} , 消息 : {}", currentIndex, nextIndex, subStr);
            msgLst.add(subStr);
            currentIndex = nextIndex;
            nextIndex += RandomUtil.randomInt(1, length);
        }
        return msgLst;
    }
}
