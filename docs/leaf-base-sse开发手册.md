# Leaf-Base-SSE 模块开发手册

## 1. 模块概述

Leaf-Base-SSE 是 Maple-Leaf-Framework 框架中提供的 Server-Sent Events (SSE) 服务模块，旨在为应用程序提供一个高效、可靠的服务器向客户端单向实时消息推送解决方案。它基于 Spring Boot 实现，并充分利用了 Java 17+ 的新特性，专注于轻量级、高性能和易用性。

### 1.1 模块特点

- **轻量级与高效**：相比 WebSocket，SSE 实现更简单，协议开销更小，专注于服务器到客户端的单向通信，资源消耗更少。
- **线程安全设计**：
    - 使用 `ConcurrentHashMap` 存储客户端连接，确保高并发环境下的线程安全。
    - 所有服务方法设计为无状态或使用线程安全的数据结构。
    - 采用不可变对象和局部变量捕获，避免线程间共享可变状态。
- **内存优化**：
    - 避免不必要的对象创建，例如重用消息构建器。
    - 利用 Java 17+ 的紧凑字符串表示。
    - 消息拆分策略优化，减少大消息对内存的占用。
    - 及时清理不再使用的连接资源，防止内存泄漏。
- **自动重连与连接管理**：
    - 客户端在连接断开后，可根据服务器指定的 `reconnectTimeMillis` 自动尝试重新连接。
    - 服务器端通过 `SseEmitter` 的回调函数（`onCompletion`, `onTimeout`, `onError`）管理连接生命周期。
- **定时清理机制**：内置 `ScheduledExecutorService` 定期执行 `cleanupConnections` 方法，自动检测并移除已关闭或超时的无效连接，保持连接池的健康状态。
- **心跳机制**：提供 `sendHeartbeat` 方法，可用于定期向客户端发送心跳消息，配合 `cleanupConnections` 机制判断连接活跃性。
- **指数退避重试**：在消息发送失败时，采用指数退避算法（`retryWithBackoff` 方法）进行重试，最多重试3次，提高消息送达的可靠性。
- **消息拆分**：支持将长消息（`splitMessage` 方法）按随机长度拆分为多个小段发送，提高传输效率和稳定性，避免单一大消息阻塞通道。
- **原生浏览器支持**：基于 HTTP 协议，现代浏览器原生支持 EventSource API，客户端集成简单。
- **自定义事件类型**：支持通过 `eventName` 定义不同的事件类型，客户端可以针对性地监听和处理。
- **易于集成**：作为 `leaf-base-framework` 的一部分，可以方便地集成到现有 Spring Boot 应用中。

### 1.2 应用场景

- 实时通知推送（如系统公告、新邮件提醒、用户消息提醒）
- 实时数据更新（如股票行情、体育比分、在线用户列表、订单状态变更）
- 进度反馈（如文件上传/下载进度、后台任务执行状态）
- 监控与日志（如应用健康状况监控、实时日志查看）
- 社交媒体动态更新

### 1.3 依赖关系

本模块主要依赖于 `leaf-base-framework` 基础框架模块，并使用了 `cn.hutool` 工具库。

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-framework</artifactId>
    <version>${project.parent.version}</version>
</dependency>
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
</dependency>
```

## 2. 核心组件

### 2.1 数据传输对象

#### 2.1.1 GXSseMessageInnerReqDto

`GXSseMessageInnerReqDto` 是 SSE 消息在服务内部流转时使用的数据传输对象。它继承自 `GXBaseReqDto`，用于构建和封装待发送给客户端的 SSE 消息的详细内容和元数据。

**主要属性：**

| 属性名              | 类型   | 默认值                                  | 说明                                                                                                |
|---------------------|--------|-----------------------------------------|-----------------------------------------------------------------------------------------------------|
| `clientId`          | String | `null`                                  | 客户端标识 ID。用于唯一标识连接的客户端，在发送消息和管理连接时使用。                                 |
| `msgId`             | String | `IdUtil.fastUUID()` (Hutool生成)        | 消息 ID。每条消息的唯一标识，默认使用 Hutool 的 `fastUUID` 生成，用于消息追踪和去重。                   |
| `data`              | Dict   | `null`                                  | 传输数据，即 SSE 事件的 `data` 字段。使用 Hutool 的 `Dict` 类型存储灵活的键值对消息内容。例如：`Dict.create().set("message", "内容")` |
| `eventName`         | String | `null` | 事件标识符，即 SSE 事件的 `event` 字段。经 `sendMessageToOneClient` / `sendMessageToAllClient` 发送时由实现统一置为 `Push Msg`；手工构建 DTO 直接发送时才需要自行设置。 |
| `reconnectTimeMillis` | long   | `2000L` (2秒)                           | 重新连接时间（毫秒），即 SSE 事件的 `retry` 字段。当连接断开时，客户端尝试重新连接的等待时间。可根据网络环境和业务需求调整。 |
| `comment`           | String | `""` (空字符串)                         | 事件注释。即 SSE 事件流中的注释行。用于添加事件的额外说明信息，方便调试和日志记录。该字段会在 SSE 事件中作为注释发送给客户端。 |

**使用示例：**

```java
import cn.hutool.core.lang.Dict;
import cn.maple.sse.dto.GXSseMessageInnerReqDto;

// ...

GXSseMessageInnerReqDto messageDto = GXSseMessageInnerReqDto.builder()
    .clientId("client123")
    .eventName("userNotification")
    .data(Dict.create().set("message", "您有一条新消息").set("type", "notification"))
    .reconnectTimeMillis(5000L) // 设置5秒重连
    .comment("重要通知")
    .build();
```

### 2.2 服务接口

#### 2.2.1 GXSseEmitterService

`GXSseEmitterService` 是 SSE 服务的核心接口，定义了 SSE 服务应提供的基本功能。它继承自 `GXBusinessService`。

**主要方法：**

| 方法名                      | 参数                                         | 返回值        | 说明                                                                                                                               |
|-----------------------------|----------------------------------------------|---------------|------------------------------------------------------------------------------------------------------------------------------------|
| `createSseConnect`          | `String clientId`, `long timeout`            | `SseEmitter`  | 创建 SSE 连接。`clientId` 可选，若为空则自动生成。`timeout` 为连接超时时间（秒），实际超时时间会与最小超时时间（`DEFAULT_MIN_TIMEOUT_SECONDS`）比较取最大值。 |
| `getSseEmitterByClientId`   | `String clientId`                            | `SseEmitter`  | 根据客户端 ID 获取 `SseEmitter` 对象。如果连接不存在，返回 `null`。                                                                     |
| `sendMessageToAllClient`    | `String msg`                                 | `void`        | 向所有当前连接的客户端广播消息。如果消息为空或没有活跃连接，则不执行任何操作。                                                                 |
| `sendMessageToOneClient`    | `String clientId`, `String msg`, `boolean splitMsg` | `void`        | 向指定的客户端发送消息。`splitMsg` 为 `true` 时，会调用 `splitMessage` 方法对长消息进行拆分。                                                  |
| `closeConnect`              | `String clientId`                            | `void`        | 主动关闭与指定客户端的 SSE 连接，并从缓存中移除。会尝试向客户端发送一个关闭通知事件。                                                              |
| `splitMessage`              | `String msg`, `int length`                   | `List<String>` | 将长消息拆分成多个小段。`length` 是拆分的基准长度参数（建议1-100），实际拆分长度会在此基础上随机化，以避免固定长度可能导致的问题。返回拆分后的消息列表。   |

### 2.3 服务实现

#### 2.3.1 GXSseEmitterServiceImpl

`GXSseEmitterServiceImpl` 是 `GXSseEmitterService` 接口的具体实现类，负责管理 SSE 连接、处理消息发送、以及维护连接的生命周期。

**核心特性与机制：**

- **连接缓存**：
    - 使用实例级 `ConcurrentHashMap<String, SseEmitter>` 存储客户端 ID 与 `SseEmitter` 实例的映射。
    - 只允许"条件删除"（`remove(key, value)`）：旧连接的 `onCompletion`/`onTimeout`/`onError` 回调不会误删重连后的新连接。
- **超时管理**：
    - `createSseConnect` 方法中，传入的 `timeout`（秒）会与内部定义的 `DEFAULT_MIN_TIMEOUT_SECONDS`（默认为60秒）比较，取较大者作为实际超时时间（毫秒级传递给 `SseEmitter` 构造函数）。
    - `SseEmitter` 本身具有超时机制，超时后会触发 `onTimeout` 回调。
- **回调处理**：
    - `onCompletionCallBack`: 连接正常完成（客户端关闭或服务器调用 `emitter.complete()`）时触发，条件删除连接。
    - `onTimeoutCallBack`: 连接超时触发，条件删除连接（容器已完成该连接，不重复 `complete()`）。
    - `onErrorCallBack`: 连接被容器判定失败时触发，记录 WARN 日志并条件删除连接；**不再尝试发送"恢复提示"消息**（此时连接已不可用）。
- **心跳与清理 (`triggerHeartbeat` / `cleanupConnections`)**：
    - `scheduler` 是单线程 `ScheduledExecutorService`，**只做触发**：每分钟把连接快照按 `256` 条一批提交给 `taskExecutor`（`Executors.newVirtualThreadPerTaskExecutor()`），批内串行写、批间并行，单个慢连接不会拖住其它连接。
    - 每批对每个连接发送空注释 `comment("heartbeat")`；发送失败即判定连接失效，**移除并关闭**该连接。
    - 心跳只用于**探活与清理**，不会延长连接寿命（见 2.4 节）。
- **消息投递 (`dispatchMessage` / `trySend`)**：
    - `dispatchMessage` 负责空负载拦截、错误分类与"同步直发 / 有序队列"分流；`trySend` 负责单次写并根据异常区分 `SENT` / `IO_FAILED` / `DEAD`。
    - 成功路径在调用线程同步写；**首次写失败后**该连接转入有序发送管线（`GXSseClientSendPipeline`），此后同一连接的消息按 FIFO 异步投递。
- **指数退避重试 (`deliverWithRetry`)**：
    - 仅对 `IOException` 重试，最多 3 次，间隔 100 / 200 / 400 毫秒，在发送管线的虚拟线程上执行（**不在调用线程 sleep**）。
    - `IllegalStateException`（连接已 `complete`）不重试，直接丢弃并计入 `drops`。
    - 3 次重试仍失败：丢弃该消息、`failures` 计数 +1、**关闭连接并从缓存移除**。
- **消息拆分 (`splitMessage`)**：
    - `null` 或空白消息返回空列表。
    - `length` 会被钳制到 `1-100`（单位：字符，不是 KB）；每片长度在该区间随机，避免固定模式。
    - 分片共享同一个 `msgId`，并在 SSE 注释中携带 `chunk=<序号>/<总片数>`，便于客户端重组与去重。
- **心跳机制 (`sendHeartbeat`)**：
    - 向指定客户端发送空注释作为心跳包；失败时关闭并移除该连接。
- **辅助方法**：
    - `buildMessageDto`: 根据客户端 ID、消息内容、共享 `msgId` 与分片序号构建 `GXSseMessageInnerReqDto`（默认事件名 `Push Msg`）。
    - `closeAndRemove`: **移除即关闭**——条件删除成功后调用 `complete()`，所有主动清理路径统一走它。
    - `removeEmitter`: 仅供容器回调使用，只做条件删除。
    - `getActiveConnectionCount` / `getMetrics`: 返回当前活跃连接数与六项投递指标。
    - `closeConnections(Function<String, Boolean> predicate)`: 按谓词批量关闭连接；谓词异常会直接抛给调用方（批量关闭不是原子操作）。

**重要常量：**

- `DEFAULT_MIN_TIMEOUT_SECONDS`: 默认最小超时时间（秒），值为 `60L`。
- `DEFAULT_EVENT_NAME`: 默认 SSE 事件名称，值为 `"Push Msg"`。
- `MAX_RETRIES`: 重试次数上限，值为 `3`（总尝试次数 = 1 + 3）。
- `DEFAULT_SPLIT_LENGTH`: 拆分基准长度，值为 `100`（字符）。
- `MAX_PENDING_MESSAGES`: 单连接有序队列容量，值为 `256`。
- `HEARTBEAT_BATCH_SIZE`: 心跳分批大小，值为 `256`。
- `scheduler`: 单线程 `ScheduledExecutorService`，仅用于触发心跳批次。
- `taskExecutor`: `Executors.newVirtualThreadPerTaskExecutor()`，承载心跳批次与有序投递任务。

### 2.4 连接生命周期、投递顺序与可观测性

#### 2.4.1 连接生命周期真相

SSE 的异步超时是**绝对超时**：`ResponseBodyEmitterReturnValueHandler` 用 `new DeferredResult<>(emitter.getTimeout())` 建立异步处理，后续任何写入（包括心跳）都不会重置它。因此：

| 事件 | 缓存变化 | 是否 `complete()` | 客户端表现 |
| --- | --- | --- | --- |
| 正常完成 / 超时 / 错误（容器回调） | 条件删除 | 容器已完成，不重复调用 | 流结束，浏览器按 `retry` 重连 |
| 服务端 `closeConnect` | 删除 | 是 | 收到 `event: close`，随后**仍会自动重连** |
| 心跳失败 / `sendHeartbeat` 失败 | 删除 | 是 | 连接被关闭后自动重连 |
| 重试耗尽 | 删除 | 是 | 同上 |
| 同 clientId 新连接替换旧连接 | 覆盖 | 旧连接被 `complete()` | 旧浏览器连接断开并重连 |

由此可得两条必须在业务侧接受的限制：

1. **心跳不保活**：连接存活上限就是 `timeout` 秒（最小 60 秒），心跳只用于发现失效连接；
2. **服务端无法阻止重连**：`closeConnect` 不会返回 HTTP 204，浏览器会按 `retry`（默认 2000ms）重连；如需"下线即不再连接"，必须在业务层拒绝再次握手。

#### 2.4.2 投递顺序与重试

| 场景 | 行为 |
| --- | --- |
| 全部发送成功 | 调用线程同步写，顺序即调用顺序 |
| 某条消息首次写入失败 | 该消息进入连接专属 FIFO 队列，后续消息排在其后，由单个 drain 任务串行投递 → **顺序保持** |
| 重试 | 仅 `IOException` 重试 3 次（100/200/400ms），在虚拟线程上 sleep，不阻塞调用方 |
| 连接已 `complete`（`IllegalStateException`） | 不重试，丢弃并计入 `drops` |
| 3 次重试仍失败 | 丢弃消息、`failures`+1、关闭连接 |
| 队列超过 256 条 | 拒绝新消息（`drops`+1），已排队消息不受影响 |

> 一旦某连接发生过写入失败，该连接之后的投递都会经由有序队列异步完成；其它连接仍保持同步直发。

#### 2.4.3 可观测性

`GXSseEmitterService#getMetrics()` 返回不可变快照（`GXSseMetrics`）：

| 字段 | 含义 |
| --- | --- |
| `attempts` | 发送尝试次数（含重试，不含握手） |
| `successes` | 成功写入次数 |
| `failures` | 重试耗尽被丢弃的消息数 |
| `retries` | 实际执行的重试次数 |
| `drops` | 丢弃消息数（连接失效 / 队列满 / 负载为空） |
| `connections` | 当前活跃连接数 |

日志级别约定：连接**创建/关闭**为 INFO（每个连接每次生命周期最多 2 条），**移除/清理/丢弃/重试中**为 DEBUG，**重试耗尽**为 ERROR（每条消息最多 1 条）。生产环境排障建议优先看指标，而不是打开 DEBUG。

## 3. 使用指南

### 3.1 引入依赖

确保项目中已引入 `leaf-base-sse` 模块的依赖（通常通过父模块或BOM管理）。

### 3.2 注入服务

在你的 Spring Boot Service 或 Controller 中注入 `GXSseEmitterService`：

```java
import cn.maple.sse.service.GXSseEmitterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller; // 或者 @Service
// ...

@Controller // 或者 @Service
public class MySseController {

    private final GXSseEmitterService sseEmitterService;

    @Autowired
    public MySseController(GXSseEmitterService sseEmitterService) {
        this.sseEmitterService = sseEmitterService;
    }

    // ... Sse 相关方法
}
```

### 3.3 创建 SSE 连接

在控制器中创建一个端点，用于客户端发起 SSE 连接请求。`createSseConnect` 方法会返回一个 `SseEmitter` 对象。连接成功后，服务器会首先自动发送一条事件，其 `id` 为 `201` (HttpStatus.HTTP_CREATED)，`data` 为实际使用的客户端ID（如果请求时未提供 `clientId`，则为服务器生成的ID）。

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SseController {

    @Autowired
    private GXSseEmitterService sseEmitterService;

    @GetMapping("/sse/connect")
    public SseEmitter handleSseConnection(@RequestParam(required = false) String clientId) {
        // 创建连接，超时时间设置为 5 分钟（300 秒）
        // 实际超时时间会是 Math.max(300, DEFAULT_MIN_TIMEOUT_SECONDS) 秒
        long timeoutInSeconds = 300L;
        SseEmitter emitter = sseEmitterService.createSseConnect(clientId, timeoutInSeconds);
        // 连接建立后，服务器会首先发送一个包含 clientId 的消息，客户端可以此作为连接成功的凭证和 clientId
        return emitter;
    }
}
```

**客户端注意事项：** 客户端在收到第一个事件后，应解析其 `data` 字段以获取 `clientId`，特别是当客户端未在连接请求中指定 `clientId` 时。这个 `clientId` 后续可用于服务器端点名或消息体中，以便服务器识别客户端。

### 3.4 发送消息

#### 3.4.1 向特定客户端发送简单文本消息

```java
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
// ...

@PostMapping("/sse/send/{clientId}")
public void sendMessageToClient(@PathVariable String clientId, @RequestBody String messageContent) {
    // 发送简单文本消息，默认不拆分消息
    // 消息会以默认事件名 "Push Msg" 发送
    sseEmitterService.sendMessageToOneClient(clientId, messageContent, false);
}

@PostMapping("/sse/send/split/{clientId}")
public void sendSplitMessageToClient(@PathVariable String clientId, @RequestBody String longMessageContent) {
    // 发送长文本消息，启用消息拆分
    sseEmitterService.sendMessageToOneClient(clientId, longMessageContent, true);
}
```

#### 3.4.2 向所有客户端广播简单文本消息

```java
@PostMapping("/sse/broadcast")
public void broadcastMessage(@RequestBody String messageContent) {
    sseEmitterService.sendMessageToAllClient(messageContent);
}
```

#### 3.4.3 使用 `GXSseMessageInnerReqDto` 发送自定义SSE事件

要发送包含自定义事件名称、ID、数据、重连时间或注释的SSE事件，你需要构建一个 `GXSseMessageInnerReqDto` 对象，并直接操作 `SseEmitter` 实例来发送。

```java
import cn.hutool.core.lang.Dict;
import cn.maple.sse.dto.GXSseMessageInnerReqDto;
import org.springframework.http.MediaType;
import java.io.IOException;
// ...

public void sendCustomEventToClient(String clientId, String eventName, Dict data, String comment, Long reconnectTime) {
    SseEmitter emitter = sseEmitterService.getSseEmitterByClientId(clientId);
    if (emitter != null) {
        GXSseMessageInnerReqDto messageDto = GXSseMessageInnerReqDto.builder()
                .clientId(clientId) // 可选，主要用于构建对象，实际发送时 emitter 已绑定 clientId
                .eventName(eventName)
                .data(data)
                .comment(comment)
                .reconnectTimeMillis(reconnectTime != null ? reconnectTime : 2000L) // 默认2秒
                // msgId 会自动生成
                .build();

        try {
            SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event()
                    .id(messageDto.getMsgId()) // 使用 DTO 中的 msgId
                    .name(messageDto.getEventName()) // 自定义事件名
                    .data(messageDto.getData(), MediaType.APPLICATION_JSON) // 发送JSON数据
                    .reconnectTime(messageDto.getReconnectTimeMillis()) // 自定义重连时间
                    .comment(messageDto.getComment()); // 添加注释

            emitter.send(eventBuilder);
        } catch (IOException e) {
            // 处理发送异常，例如记录日志，或调用 onErrorCallBack 中的逻辑
            // GXSseEmitterServiceImpl 内部的 send 方法有重试机制，但直接调用 emitter.send() 没有
            // 可以考虑调用 sseEmitterService.closeConnect(clientId) 或让其自然超时/错误处理
            System.err.println("Failed to send custom SSE event to client " + clientId + ": " + e.getMessage());
            // sseEmitterService.closeConnect(clientId); // 或者根据策略处理
        }
    } else {
        System.err.println("Client " + clientId + " not connected.");
    }
}

// 调用示例
// sendCustomEventToClient("client123", "stockUpdate", Dict.create().set("ticker", "AAPL").set("price", 150.00), "Apple stock price update", 5000L);
```
**注意**：直接使用 `emitter.send()` 时，`GXSseEmitterServiceImpl` 内部的发送重试机制（`retryWithBackoff`）不会被触发。如果需要重试，需要自行实现或改造 `GXSseEmitterServiceImpl` 以暴露更灵活的发送接口。

### 3.5 关闭 SSE 连接

可以由服务器主动关闭某个客户端的连接。

```java
import org.springframework.web.bind.annotation.DeleteMapping;
// ...

@DeleteMapping("/sse/close/{clientId}")
public void closeClientConnection(@PathVariable String clientId) {
    sseEmitterService.closeConnect(clientId);
}
```

### 3.6 其他实用方法

- **获取连接实例**：`sseEmitterService.getSseEmitterByClientId(clientId)`（可能返回 `null`；直接向返回的 emitter 写会绕过重试、顺序与指标统计）
- **获取活跃连接数**：`sseEmitterService.getMetrics().connections()`（接口方法，推荐）；实现类另有 `getActiveConnectionCount()`
- **发送心跳**：`sseEmitterService.sendHeartbeat(clientId)`（仅实现类可见，需要注入 `GXSseEmitterServiceImpl`）
- **批量关闭连接**：`sseEmitterService.closeConnections(predicate)`（仅实现类可见；谓词异常直接抛给调用方，批量关闭非原子）
- **投递指标**：`sseEmitterService.getMetrics()`（接口方法，六项计数）

## 4. 前端接收示例

前端可以通过 JavaScript 的 `EventSource` API 来接收 SSE 事件。

### 4.1 原生 JavaScript 实现

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>SSE Client Demo</title>
    <style>
        body { font-family: sans-serif; margin: 20px; }
        #messages { list-style-type: none; padding: 0; }
        #messages li { background-color: #f0f0f0; margin-bottom: 5px; padding: 10px; border-radius: 3px; }
        .event-id { font-size: 0.8em; color: #666; }
        .event-name { font-weight: bold; color: #333; }
    </style>
</head>
<body>
    <h1>SSE Messages:</h1>
    <div id="connectionStatus">Status: Connecting...</div>
    <ul id="messages"></ul>

    <script>
        let clientId = null; // 将由服务器首次消息提供
        let eventSource = null;

        function connectSse() {
            // 如果用户有固定的clientId，可以在这里传入，否则由服务器生成
            // const userDefinedClientId = "myUniqueId"; 
            // eventSource = new EventSource(`/sse/connect?clientId=${userDefinedClientId}`);
            eventSource = new EventSource('/sse/connect'); // 让服务器生成clientId

            const messagesElement = document.getElementById("messages");
            const connectionStatusElement = document.getElementById("connectionStatus");

            eventSource.onopen = function() {
                connectionStatusElement.textContent = "Status: Connected";
                console.log("SSE Connection opened.");
            };

            // 监听默认消息 (服务器未使用 eventName 或 eventName 为 "message")
            eventSource.onmessage = function(event) {
                console.log("Received generic message:", event);
                const newMessage = document.createElement("li");
                let content = `Data: ${event.data}`;
                if (event.id) content += ` <span class="event-id">(ID: ${event.id})</span>`;
                newMessage.innerHTML = content;
                messagesElement.appendChild(newMessage);

                // 特别处理服务器首次发送的 clientId 信息
                if (event.id === '201' && !clientId) { // '201' 是 GXSseEmitterServiceImpl 中定义的成功连接事件ID
                    try {
                        const dataFromServer = JSON.parse(event.data); // 假设服务器以JSON格式发送clientId
                        if(dataFromServer && dataFromServer.clientId) {
                           clientId = dataFromServer.clientId;
                        } else {
                           clientId = event.data; // 如果不是JSON，直接使用data作为clientId
                        }
                        console.log("Received clientId from server: ", clientId);
                        connectionStatusElement.textContent = `Status: Connected (Client ID: ${clientId})`;
                        // 你可以在这里将 clientId 保存到 localStorage 或用于后续请求
                    } catch (e) {
                        // 如果解析失败，直接将 event.data 作为 clientId
                        clientId = event.data;
                        console.warn("Could not parse clientId from initial message, using raw data: ", clientId, e);
                        connectionStatusElement.textContent = `Status: Connected (Client ID: ${clientId})`;
                    }
                }
            };

            // 监听自定义事件 (例如：服务器发送 eventName: "userNotification")
            eventSource.addEventListener('userNotification', function(event) {
                console.log("Received 'userNotification' event:", event);
                const newMessage = document.createElement("li");
                let dataParsed = event.data;
                try {
                    dataParsed = JSON.stringify(JSON.parse(event.data), null, 2);
                } catch (e) { /* Not JSON, use as is */ }
                newMessage.innerHTML = `<span class="event-name">[UserNotification]</span> Data: <pre>${dataParsed}</pre>`;
                if (event.id) newMessage.innerHTML += ` <span class="event-id">(ID: ${event.id})</span>`;
                messagesElement.appendChild(newMessage);
            });
            
            // 监听另一个自定义事件 (例如：服务器发送 eventName: "Push Msg"，这是默认的事件名)
            eventSource.addEventListener('Push Msg', function(event) {
                console.log("Received 'Push Msg' event:", event);
                const newMessage = document.createElement("li");
                newMessage.innerHTML = `<span class="event-name">[Push Msg]</span> Data: ${event.data}`;
                if (event.id) newMessage.innerHTML += ` <span class="event-id">(ID: ${event.id})</span>`;
                messagesElement.appendChild(newMessage);
            });

            eventSource.onerror = function(err) {
                connectionStatusElement.textContent = "Status: Error (Connection closed)";
                console.error("EventSource failed:", err);
                if (eventSource.readyState === EventSource.CLOSED) {
                    console.log("SSE Connection was closed. Attempting to reconnect in 5 seconds...");
                    // 简单的重连逻辑，实际应用中可能需要更复杂的策略（例如指数退避）
                    setTimeout(connectSse, 5000);
                } else {
                    // 如果错误不是关闭连接，可能需要其他处理
                }
                // eventSource.close(); // 通常浏览器会自动处理，但特定错误下可能需要手动关闭
            };
        }

        // 初始连接
        connectSse();

        // 页面卸载时关闭连接
        window.addEventListener('beforeunload', function() {
            if (eventSource) {
                eventSource.close();
                console.log("SSE Connection closed on page unload.");
            }
        });
    </script>
</body>
</html>
```

**原生 JavaScript 示例说明：**

1.  **连接建立**：`new EventSource('/sse/connect')` 发起连接。可以附加 `clientId` 查询参数，如果省略，服务器会生成一个并在第一条消息中返回。
2.  **`onopen`**：连接成功打开时触发。
3.  **`onmessage`**：当服务器发送没有特定 `event` 字段（或 `event: message`）的消息时触发。示例中特别处理了 `event.id === '201'` 的情况，这是 `GXSseEmitterServiceImpl` 成功建立连接后发送的初始消息，其 `data` 包含 `clientId`。
4.  **`addEventListener('eventName', callback)`**：用于监听服务器通过 `event: eventName` 指定的自定义事件。
5.  **`onerror`**：发生错误时触发。`EventSource` API 具有自动重连机制（除非服务器返回 HTTP 204 或客户端调用 `eventSource.close()`）。示例中添加了一个简单的延时重连逻辑，用于演示。
6.  **获取 `clientId`**：示例代码演示了如何从服务器发送的第一条消息中（ID 为 `201`）解析出 `clientId`。这对于后续需要此 ID 的操作非常重要。
7.  **关闭连接**：在页面卸载 (`beforeunload`) 时主动关闭 `EventSource` 连接，以释放资源。

### 4.2 Vue.js (Vue 3 Composition API) 实现

```html
<template>
  <div>
    <h1>SSE Messages (Vue 3)</h1>
    <div>Status: {{ connectionStatus }}</div>
    <div v-if="clientId">Client ID: {{ clientId }}</div>
    <ul>
      <li v-for="(msg, index) in messages" :key="index" :class="msg.type">
        <span v-if="msg.eventName" class="event-name">[{{ msg.eventName }}]</span>
        Data: <pre>{{ msg.data }}</pre>
        <span v-if="msg.id" class="event-id">(ID: {{ msg.id }})</span>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';

const messages = ref([]);
const eventSource = ref(null);
const connectionStatus = ref('Connecting...');
const clientId = ref(null);

let reconnectTimeout = null;

const connect = () => {
  // 清除之前的重连定时器（如果有）
  if (reconnectTimeout) clearTimeout(reconnectTimeout);

  // const userDefinedClientId = "vueUser3";
  // eventSource.value = new EventSource(`/sse/connect?clientId=${userDefinedClientId}`);
  eventSource.value = new EventSource('/sse/connect');

  eventSource.value.onopen = () => {
    connectionStatus.value = 'Connected';
    console.log('SSE Connection opened (Vue).');
  };

  eventSource.value.onmessage = (event) => {
    console.log('Received generic message (Vue):', event);
    messages.value.push({ 
      type: 'generic', 
      data: event.data, 
      id: event.id 
    });

    if (event.id === '201' && !clientId.value) {
        try {
            const dataFromServer = JSON.parse(event.data);
            clientId.value = dataFromServer && dataFromServer.clientId ? dataFromServer.clientId : event.data;
        } catch (e) {
            clientId.value = event.data;
        }
        console.log("Received clientId from server (Vue): ", clientId.value);
        connectionStatus.value = `Connected (Client ID: ${clientId.value})`;
    }
  };

  eventSource.value.addEventListener('userNotification', (event) => {
    console.log("Received 'userNotification' event (Vue):", event);
    let dataParsed = event.data;
    try {
        dataParsed = JSON.parse(event.data); // 尝试解析为JSON
    } catch (e) { /* Not JSON, use as is */ }
    messages.value.push({ 
      type: 'custom', 
      eventName: 'userNotification', 
      data: dataParsed, 
      id: event.id 
    });
  });
  
  eventSource.value.addEventListener('Push Msg', (event) => {
    console.log("Received 'Push Msg' event (Vue):", event);
    messages.value.push({ 
      type: 'custom', 
      eventName: 'Push Msg', 
      data: event.data, 
      id: event.id 
    });
  });

  eventSource.value.onerror = (err) => {
    connectionStatus.value = 'Error (Connection closed)';
    console.error('EventSource failed (Vue):', err);
    if (eventSource.value) {
        eventSource.value.close(); // 确保关闭旧的实例
    }
    // 尝试在5秒后重连
    console.log("Attempting to reconnect in 5 seconds (Vue)...");
    reconnectTimeout = setTimeout(connect, 5000);
  };
};

onMounted(() => {
  connect();
});

onBeforeUnmount(() => {
  if (reconnectTimeout) clearTimeout(reconnectTimeout);
  if (eventSource.value) {
    eventSource.value.close();
    console.log('SSE Connection closed on component unmount (Vue).');
  }
});

</script>

<style scoped>
  ul { list-style-type: none; padding: 0; }
  li { background-color: #e9e9e9; margin-bottom: 8px; padding: 12px; border-radius: 4px; }
  .event-id { font-size: 0.8em; color: #555; margin-left: 10px; }
  .event-name { font-weight: bold; color: #007bff; margin-right: 5px; }
  pre { white-space: pre-wrap; word-break: break-all; margin: 0; font-size: 0.9em; }
</style>
```

**Vue.js (Vue 3 Composition API) 示例说明：**

1.  **响应式数据**：使用 `ref` 创建响应式变量 `messages`, `eventSource`, `connectionStatus`, 和 `clientId`。
2.  **生命周期钩子**：
    *   `onMounted`：组件挂载后调用 `connect` 方法建立 SSE 连接。
    *   `onBeforeUnmount`：组件卸载前关闭 SSE 连接并清除重连定时器，防止内存泄漏。
3.  **连接与事件处理**：逻辑与原生 JavaScript 类似，但更新的是 Vue 的响应式数据，从而自动更新视图。
4.  **错误处理与重连**：`onerror` 处理器中，关闭当前 `EventSource` 实例，并使用 `setTimeout` 安排重连。在 `connect` 函数开始时清除任何现有的重连定时器，以避免多个重连进程。
5.  **`clientId` 处理**：同样从初始消息中提取 `clientId` 并存储在响应式变量中。
6.  **样式**：添加了一些简单的 scoped CSS 来美化列表项。

这些示例提供了基本的前端集成方法。在实际应用中，你可能需要根据具体需求调整错误处理、重连策略、用户界面和状态管理。例如，使用更健壮的重连库（如 `event-source-polyfill` 的某些特性或自定义指数退避逻辑），或将 SSE 逻辑封装到 Vuex/Pinia store 或专门的 Service 类中。

## 5. 最佳实践与注意事项

### 5.1 服务器端 (基于 `GXSseEmitterServiceImpl`)

- **合理设置超时时间**：
    - 通过 `createSseConnect(clientId, timeoutInSeconds)` 方法设置连接的超时时间。实际超时时间会是 `Math.max(timeoutInSeconds, DEFAULT_MIN_TIMEOUT_SECONDS)`，其中 `DEFAULT_MIN_TIMEOUT_SECONDS` 默认为 60 秒。这意味着即使你设置一个非常短的超时，至少也会有 60 秒的有效期。
    - 长时间运行的连接应配合心跳机制来维持其活跃状态，并及时清理无效连接。
- **客户端标识 (`clientId`)**：
    - 强烈建议为每个客户端连接使用唯一的 `clientId`。这可以是用户ID、设备ID或会话ID。
    - 如果在 `createSseConnect` 时未提供 `clientId`，服务器会自动生成一个 UUID 作为 `clientId`，并通过初始消息（事件ID `201`）发送给客户端。
    - `clientId` 是管理连接、定向推送消息和关闭特定连接的关键。
- **连接管理与监控**：
    - `GXSseEmitterServiceImpl` 使用实例级 `ConcurrentHashMap` 存储 `SseEmitter`，并以"条件删除 + 引用相等"保证并发安全。
    - 通过 `getMetrics().connections()`（推荐）或实现类的 `getActiveConnectionCount()` 获取活跃连接数。
    - `onCompletion` / `onTimeout` / `onError` 回调只做条件删除；**主动清理路径（心跳失败、重试耗尽、销毁）统一走 `closeAndRemove`，保证"移除即关闭"**。
- **消息发送**：
    - **消息拆分**：使用 `sendMessageToOneClient(clientId, message, true)`。分片按 `1-100` 个**字符**随机切分（不是 KB），所有分片共享同一 `msgId`，注释中带 `chunk=i/n`；客户端应按 `msgId` 去重、按 `chunk` 校验完整性。`sendMessageToAllClient` **没有**拆分重载。
    - **自定义事件**：使用 `SseEmitter.event().name("customEventName").data(...)` 构造事件；直接调用 `emitter.send()` 会绕过本模块的重试、顺序与清理逻辑，仅建议在能自行处理异常时使用。
    - **错误处理与重试**：失败计入指标并可观测；重试只在连接仍为当前连接且未完成时进行，3 次耗尽即关闭连接。**不要**依赖重试保证送达——`IOException` 后容器通常已判定连接失败。
- **心跳机制**：
    - 只有 `sendHeartbeat(clientId)`（单个）与内部每分钟一次的批量心跳，**没有** `sendHeartbeatToAll()` 公共方法。
    - 心跳内容是空注释 `:heartbeat`（不是事件、也不是时间戳），作用是探活与触发清理，**不能延长连接寿命**（见 2.4.1）。连接存活上限就是 `timeout` 秒。
    - 心跳发送失败即判定连接失效，关闭并移除该连接。
- **回调处理**：
    - `createSseConnect` 方法返回的 `SseEmitter` 实例注册了 `onCompletion`、`onTimeout` 和 `onError` 回调。
    - `onCompletionCallBack`：连接正常完成时调用；`onTimeoutCallBack`：绝对超时终止时调用；`onErrorCallBack`：容器判定连接出错时调用。
    - 三者均只从缓存**条件删除**，避免误删重连后的新连接。
- **线程安全**：依赖 `ConcurrentHashMap`、引用相等、Spring 内部 `ReentrantLock` 与单连接 FIFO 管线；没有全局锁、没有单线程串行写。
- **日志记录**：SLF4J + 全 ASCII 文本；连接生命周期 INFO，清理与丢弃 DEBUG，重试耗尽 ERROR。

### 5.2 客户端 (JavaScript `EventSource`)

- **获取并使用 `clientId`**：如前端示例所示，客户端应从服务器发送的初始消息（事件ID `201`）中获取 `clientId`，特别是当连接时未指定 `clientId` 的情况。此 `clientId` 可用于后续的交互或调试。
- **监听特定事件**：使用 `eventSource.addEventListener('eventName', callback)` 来处理服务器发送的自定义事件，而不是仅依赖 `onmessage`。
- **错误处理与自动重连**：
    - `EventSource` API 本身具有自动重连机制。当连接断开（非正常关闭，如网络问题），它会尝试重新连接。
    - 服务器可以通过发送特定的 HTTP 状态码（如 204 No Content）来指示客户端不要重连。
    - 客户端的 `onerror` 回调可以用于记录错误或执行自定义的重连逻辑（例如，在多次重连失败后放弃或提示用户）。
- **关闭连接**：当不再需要 SSE 连接时（例如用户导航离开页面、组件卸载），客户端应调用 `eventSource.close()` 来显式关闭连接，以释放客户端和服务器资源。
- **处理 `reconnectTime`**：如果服务器在 SSE 事件中发送了 `retry: <milliseconds>` 字段，`EventSource` 会使用这个值作为重连间隔。

### 5.3 安全性

- **认证与授权**：SSE 端点应像其他 API 端点一样受到保护。确保只有经过身份验证和授权的客户端才能建立 SSE 连接。
    - 可以使用 Spring Security 等框架进行保护。
    - `clientId` 不应被视为安全凭证。它主要用于识别连接，而不是验证用户身份。
- **数据加密 (HTTPS)**：始终通过 HTTPS 提供 SSE 服务，以加密服务器和客户端之间传输的数据，防止窃听和篡改。
- **输入校验**：对客户端传入的任何参数（如 `clientId`）进行校验。
- **访问控制**：确保客户端只能接收到其有权访问的数据。

### 5.4 性能与伸缩性

- **连接数限制**：监控并可能限制单个用户或IP地址可以建立的并发 SSE 连接数，以防止滥用。
- **消息压缩**：虽然 SSE 本身不直接支持 HTTP 级别的压缩协商，但如果代理服务器（如 Nginx）配置了 GZip/Brotli 压缩，文本类型的 SSE 数据可以被压缩。
- **负载均衡**：
    - 在多实例部署时，需要确保客户端的 SSE 连接能够路由到正确的服务器实例（如果消息是针对特定会话的）。这通常需要“粘性会话”(sticky sessions) 或一个共享的后端（如 Redis Pub/Sub）来分发消息到持有连接的实例。
    - `leaf-base-sse` 本身是单实例设计，若要水平扩展，需要额外的消息总线集成。
- **阻塞与异步**：成功路径的写入发生在调用线程（同步），因此广播到大量慢连接会占用调用线程的写时间；一旦某连接出现写失败，其后续消息改为经有序队列异步投递。
- **资源清理**：确保所有连接在不再需要或出错时都能被正确关闭和清理，防止资源泄漏。`GXSseEmitterServiceImpl` 中的回调机制对此至关重要。

## 6. 常见问题与解决方案 (FAQ)

- **问题 1：SSE 连接意外断开**
    - **可能原因**：
        1.  **网络波动或中断**：客户端与服务器之间的网络连接不稳定。
        2.  **服务器/客户端超时**：`SseEmitter` 的超时是**绝对超时**（`timeoutInSeconds`，最小 `DEFAULT_MIN_TIMEOUT_SECONDS`），从连接建立开始计时，发送消息或心跳都不会重置它。客户端 `EventSource` 也可能有自己的超时或不活动检测。
        3.  **中间代理/防火墙**：网络中的防火墙或负载均衡器可能因空闲超时而关闭长连接。
        4.  **服务器重启或部署**：导致所有活动连接丢失。
        5.  **客户端主动关闭**：用户关闭浏览器标签页或应用，或代码调用 `eventSource.close()`。
        6.  **服务器端错误**：发送消息时发生 `IOException` 或其他错误，触发 `onErrorCallBack`，导致连接关闭。
    - **解决方案**：
        1.  **心跳机制与超时口径**：
            -   服务器端：模块内置每分钟一次的批量心跳（空注释 `:heartbeat`），并可调用 `sendHeartbeat(clientId)` 手动探活；失败即关闭连接。
            -   **心跳不能延长连接寿命**：`SseEmitter` 的超时是绝对超时（见 2.4.1），连接到期必然断开，因此必须依赖客户端 `EventSource` 自动重连，而不要把"心跳间隔小于超时"当成保活手段。
        2.  **客户端自动重连**：
            -   JavaScript `EventSource` API 默认支持自动重连（除非服务器响应 HTTP 204 或客户端调用 `close()`）。
            -   在 `eventSource.onerror` 回调中可以实现更精细的重连策略（如增加延迟、限制重连次数）。
        3.  **合理配置超时**：`createSseConnect(clientId, timeoutSeconds)` 的 `timeout` 就是连接存活上限（最小 60 秒），与心跳无关；需要"长时间在线"时调大 `timeout` 并接受到期重连，或在业务层实现"到期前主动重连"。
        4.  **服务器端日志**：检查 `GXSseEmitterServiceImpl` 的日志，特别是 `onTimeoutCallBack` 和 `onErrorCallBack` 的触发情况，以定位断开原因。
        5.  **优雅停机**：在服务器关闭或重新部署时，尝试优雅地通知客户端并关闭连接，但这对于 SSE 比较困难，通常客户端的重连机制更为关键。

- **问题 2：消息丢失或未送达**
    - **可能原因**：
        1.  **连接在消息发送前断开**：客户端已离线，但服务器尝试发送。
        2.  **发送过程中发生错误**：网络问题导致 `emitter.send()` 抛出 `IOException`。
        3.  **消息被拆分，但客户端未完整接收或处理**：如果启用了消息拆分，客户端需要能正确组合被拆分的消息（尽管 `leaf-base-sse` 的拆分是在服务器端透明进行的，客户端收到的仍然是完整的独立事件）。
        4.  **客户端处理逻辑错误**：客户端 `onmessage` 或 `addEventListener` 中的代码有 bug。
        5.  **服务器端逻辑错误**：未正确调用发送方法，或 `clientId` 不匹配。
    - **解决方案**：
        1.  **服务器端重试与指标**：`GXSseEmitterServiceImpl` 对 `IOException` 做 3 次指数退避重试（100/200/400ms），3 次失败即丢弃消息并关闭连接。通过 `getMetrics()` 的 `attempts` / `successes` / `failures` / `retries` / `drops` 判断是否在丢消息；重试只覆盖"尚未被容器判定失败"的窗口，不能替代客户端去重与业务幂等。
        2.  **消息持久化与确认 (高级)**：对于非常关键的消息，SSE 本身不提供应用级的消息确认。如果需要严格保证送达：
            -   可以考虑将消息先存入持久化队列（如 Kafka, RabbitMQ）。
            -   客户端收到消息后，向服务器发送一个确认回执（通过另一个 HTTP 请求）。
            -   服务器根据回执更新消息状态。这已超出 `leaf-base-sse` 的基本功能范围，需要额外实现。
        3.  **日志与监控**：
            -   服务器端：详细记录消息发送尝试、成功、失败和重试的日志。
            -   客户端：记录接收到的消息和任何处理错误。
        4.  **幂等性设计**：如果客户端可能因重连收到重复消息（例如，服务器重试了某条消息，而客户端在第一次尝试时已收到但未成功处理），客户端应能处理重复消息。

- **问题 3：服务器性能问题 (高并发、高内存占用)**
    - **可能原因**：
        1.  **大量并发连接**：每个 `SseEmitter` 都会占用服务器资源（内存、线程）。
        2.  **频繁或大量数据推送**：CPU 和网络带宽消耗。
        3.  **连接未及时清理**：已断开或超时的连接未从 `emitterMap` 中移除，导致内存泄漏。
        4.  **消息拆分逻辑开销**：虽然通常是优化，但在极端情况下，频繁的小块消息也可能增加开销。
    - **解决方案**：
        1.  **监控连接数**：使用 `sseEmitterService.getActiveConnectionCount()` 监控活跃连接，设置合理的上限。
        2.  **优化数据大小与频率**：仅推送必要的数据，避免过于频繁的更新。考虑增量更新而非全量数据。
        3.  **确保回调正确执行**：验证 `onCompletionCallBack`, `onTimeoutCallBack`, `onErrorCallBack` 是否总能被触发并成功从 `emitterMap` 中移除 `SseEmitter`。检查日志中是否有相关错误。
        4.  **水平扩展 (需要额外架构)**：`leaf-base-sse` 本身是为单体应用或单个服务实例设计的。要在分布式环境中有效使用 SSE，通常需要结合消息队列（如 Redis Pub/Sub, Kafka, RabbitMQ）来实现：
            -   客户端连接到任意一个应用实例。
            -   当需要向特定 `clientId` 或所有客户端发送消息时，消息发布到消息队列。
            -   所有应用实例都订阅消息队列，如果某个实例持有目标 `clientId` 的连接，则由该实例通过其 `SseEmitter` 推送消息。
        5.  **调整JVM参数**：根据负载情况调整堆大小、GC策略等。
        6.  **前端优化**：确保前端能高效处理接收到的数据，避免因前端性能瓶颈导致感知上的延迟。

- **问题 4：客户端无法收到消息，但连接看似正常**
    - **可能原因**：
        1.  **事件名称不匹配**：服务器发送时指定了 `eventName`，但客户端监听的是 `onmessage` (默认事件) 或错误的事件名。
        2.  **`clientId` 不匹配**：服务器向错误的 `clientId` 发送消息。
        3.  **消息内容或格式问题**：例如，服务器发送了非文本数据，或客户端期望特定JSON结构但实际收到的不符。
        4.  **防火墙或代理问题**：某些网络设备可能会干扰长轮询或SSE流，尽管这通常表现为连接断开。
    - **解决方案**：
        1.  **检查事件名**：确保客户端 `addEventListener` 的事件名与服务器端 `SseEmitter.SseEventBuilder.name()` 设置的事件名一致。如果服务器未设置 `name`，则客户端应使用 `onmessage`。
        2.  **验证 `clientId`**：在服务器端日志中确认消息是发往预期的 `clientId`。客户端可以打印其 `clientId` (从初始消息获取) 以供比对。
        3.  **检查消息内容与类型**：在服务器端记录发送的数据，在客户端记录接收到的 `event.data`。确保数据格式符合预期。
        4.  **网络诊断**：使用浏览器开发者工具的网络(Network)面板检查SSE连接的原始数据流，查看是否有数据实际到达客户端。

## 7. SSE (leaf-base-sse) 与 WebSocket 对比

| 特性             | SSE (Server-Sent Events, e.g., `leaf-base-sse`) | WebSocket                                    |
| ---------------- | ------------------------------------------------- | -------------------------------------------- |
| **通信方式**     | 服务器 -> 客户端 (单向推送)                       | 客户端 <-> 服务器 (双向实时通信)             |
| **协议基础**     | 标准 HTTP/HTTPS                                   | 独立的 TCP 协议 (ws://, wss://)，初始握手基于 HTTP |
| **连接管理**     | 相对简单，基于 HTTP 长连接。`leaf-base-sse` 提供了连接管理、超时、心跳等机制。 | 更复杂，需要专门的连接生命周期管理。         |
| **浏览器兼容性** | 现代浏览器原生支持 (IE/Edge 旧版不支持 `EventSource`) | 现代浏览器原生支持。                         |
| **自动重连**     | `EventSource` API 原生支持自动重连。`leaf-base-sse` 的心跳和超时有助于此过程。 | 需要客户端自行实现或使用库支持。             |
| **事件类型**     | 支持自定义事件名 (`event:` 字段)。                | 消息层面，通常需要应用层协议定义事件类型。   |
| **消息格式**     | 文本 (通常 UTF-8)，`leaf-base-sse` 支持发送 JSON 等。 | 文本 (UTF-8) 或二进制数据。                  |
| **防火墙/代理**  | 通常更容易通过，因为它使用标准 HTTP 端口。        | 可能需要特定配置才能通过某些防火墙或代理。   |
| **服务器资源**   | 每个连接通常比 WebSocket 轻量一些（尤其在连接数非常多时）。 | 每个连接维护一个持久的 TCP 套接字。          |
| **`leaf-base-sse` 特点** | 连接缓存与条件删除、单连接有序投递、指数退避重试、心跳探活、消息拆分（共享 msgId + 分片序号）、六项投递指标。 | (通用 WebSocket 特点)                        |
| **适用场景**     | **`leaf-base-sse` 理想场景**：<br>- 实时通知推送 (如新邮件、新订单、系统告警)<br>- 实时数据更新 (如股票行情、仪表盘数据、体育比分)<br>- 任务进度反馈 (如文件上传/下载、后台任务处理)<br>- 日志流推送 | **WebSocket 理想场景**：<br>- 实时聊天应用<br>- 多人在线游戏<br>- 协同编辑工具<br>- 需要低延迟、高频率双向交互的场景 |

**总结**：

-   如果你的需求主要是从服务器向客户端单向推送更新，且不需要客户端频繁向服务器发送数据，SSE (特别是像 `leaf-base-sse` 这样功能完善的实现) 是一个更简单、更轻量级的选择，并且能很好地利用现有的 HTTP 基础设施。
-   如果需要真正的双向实时通信，或者需要传输二进制数据，WebSocket 是更合适的选择。

## 8. 版本历史

- **v1.0.0 (初始版本)**
    - 提供核心的 SSE 服务接口 `GXSseEmitterService` 及其实现 `GXSseEmitterServiceImpl`。
    - 支持创建连接、指定客户端ID、超时管理、回调处理（完成、超时、错误）。
    - 实现向单个客户端和所有客户端发送消息的功能。
    - 支持消息拆分 (`splitMessage`)。
    - 内置线程安全的连接缓存 (`ConcurrentHashMap`)。
    - 包含基本的日志记录。
    - 提供 `GXSseMessageInnerReqDto` 用于构建消息。
    - 实现指数退避重试机制和心跳机制。
    - 增加定时清理无效连接的调度任务。

*后续版本请在此处添加变更记录。*

## 9. 相关资源与进一步阅读

-   **MDN Web Docs - Server-Sent Events**: [Using server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
-   **Spring Framework - `SseEmitter` Javadoc**: [SseEmitter (Spring Framework API)](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html)
-   **RFC 8030 - Generic Event Delivery Using HTTP Push (SSE)**: [RFC 8030](https://tools.ietf.org/html/rfc8030) (虽然 SSE 规范更早，此 RFC 提供了相关背景)
-   **HTML Living Standard - EventSource**: [HTML Standard - EventSource](https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events)
-   **`cn.hutool.core.lang.Dict`**: [Hutool Dict documentation](https://www.hutool.cn/docs/#/core/Collection/%E5%AD%97%E5%85%B8-Dict) (如果项目中使用了 Hutool)

---