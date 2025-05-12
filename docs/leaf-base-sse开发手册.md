# Leaf-Base-SSE 模块开发手册

## 1. 模块概述

Leaf-Base-SSE 是 Maple-Leaf-Framework 框架中提供的 Server-Sent Events (SSE) 服务模块，用于实现服务器向客户端的单向实时消息推送。相比 WebSocket，SSE 更加轻量且专注于服务器到客户端的单向通信，适用于通知、实时数据更新等场景。

### 1.1 模块特点

- **轻量级**：相比 WebSocket，SSE 实现更简单，资源消耗更少
- **单向通信**：专注于服务器向客户端推送消息的场景
- **自动重连**：客户端断开连接后会自动尝试重新连接
- **原生支持**：基于 HTTP 协议，浏览器原生支持，无需额外库
- **消息类型**：支持自定义事件类型，客户端可针对不同事件类型注册不同处理函数

### 1.2 应用场景

- 实时通知推送（如系统公告、用户消息提醒）
- 实时数据更新（如股票行情、订单状态变更）
- 进度反馈（如文件上传/下载进度、长时间任务执行状态）
- 日志实时查看
- 社交媒体动态更新

### 1.3 依赖关系

本模块依赖于 `leaf-base-framework` 基础框架模块，提供了与框架无缝集成的 SSE 服务实现。

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-framework</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

## 2. 核心组件

### 2.1 数据传输对象

#### 2.1.1 GXSseMessageInnerReqDto

`GXSseMessageInnerReqDto` 是 SSE 消息内部传输对象，用于在 SSE 服务中构建和传输消息，支持自定义事件名称、数据内容和重连时间等属性。

**主要属性：**

| 属性名 | 类型 | 说明 |
| --- | --- | --- |
| clientId | String | 客户端标识 ID，用于唯一标识连接的客户端 |
| msgId | String | 消息 ID，每条消息的唯一标识，默认使用 UUID 生成 |
| data | Dict | 传输数据，使用 Dict 类型存储灵活的消息内容 |
| eventName | String | 事件标识符，用于客户端区分不同类型的事件 |
| reconnectTimeMillis | long | 重新连接时间（毫秒），默认为 2000 毫秒 |
| comment | String | 事件注释，用于添加事件的额外说明信息 |

### 2.2 服务接口

#### 2.2.1 GXSseEmitterService

`GXSseEmitterService` 是 SSE 服务的核心接口，提供了创建 SSE 连接、发送消息、关闭连接等功能。

**主要方法：**

| 方法名 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| createSseConnect | String clientId, long timeout | SseEmitter | 创建 SSE 连接，建立客户端与服务器的 SSE 长连接 |
| getSseEmitterByClientId | String clientId | SseEmitter | 根据客户端 ID 获取 SseEmitter 对象 |
| sendMessageToAllClient | String msg | void | 发送消息给所有客户端 |
| sendMessageToOneClient | String clientId, String msg, boolean splitMsg | void | 给指定客户端发送消息，支持消息拆分功能 |
| closeConnect | String clientId | void | 关闭 SSE 连接 |
| splitMessage | String msg, int length | List<String> | 消息拆分，将长消息拆分成多个小消息 |

### 2.3 服务实现

#### 2.3.1 GXSseEmitterServiceImpl

`GXSseEmitterServiceImpl` 是 `GXSseEmitterService` 接口的实现类，提供了 SSE 服务的具体实现。

**主要特性：**

- 使用 `ConcurrentHashMap` 存储客户端连接，保证线程安全
- 支持连接超时控制，防止资源泄露
- 实现了完善的异常处理和资源释放机制
- 支持消息拆分功能，避免大消息阻塞传输通道
- 提供了连接生命周期的回调处理（完成、超时、错误）

## 3. 使用指南

### 3.1 创建 SSE 连接

在控制器中创建一个方法，用于建立 SSE 连接：

```java
@Autowired
private GXSseEmitterService sseEmitterService;

@GetMapping("/connect")
public SseEmitter connect(@RequestParam(required = false) String clientId) {
    // 创建连接，超时时间设置为 5 分钟（300 秒）
    return sseEmitterService.createSseConnect(clientId, 300);
}
```

### 3.2 向特定客户端发送消息

```java
@PostMapping("/send/{clientId}")
public void sendMessage(@PathVariable String clientId, @RequestBody String message) {
    // 发送消息，启用消息拆分
    sseEmitterService.sendMessageToOneClient(clientId, message, true);
}
```

### 3.3 向所有客户端广播消息

```java
@PostMapping("/broadcast")
public void broadcast(@RequestBody String message) {
    sseEmitterService.sendMessageToAllClient(message);
}
```

### 3.4 关闭 SSE 连接

```java
@DeleteMapping("/close/{clientId}")
public void closeConnection(@PathVariable String clientId) {
    sseEmitterService.closeConnect(clientId);
}
```

### 3.5 使用消息传输对象发送自定义事件

```java
// 构建消息对象
GXSseMessageInnerReqDto messageDto = GXSseMessageInnerReqDto.builder()
    .clientId("client123")
    .eventName("userNotification")
    .data(Dict.create().set("message", "您有一条新消息").set("type", "notification"))
    .build();

// 获取客户端的 SseEmitter 对象
SseEmitter emitter = sseEmitterService.getSseEmitterByClientId("client123");

// 发送消息
// 这里需要自行实现发送逻辑，可参考 GXSseEmitterServiceImpl 中的 sendMsgToClientByClientId 方法
```

## 4. 前端接收示例

### 4.1 JavaScript 原生实现

```javascript
// 建立 SSE 连接
const clientId = 'client123'; // 可选，如不提供则服务端自动生成
const eventSource = new EventSource(`/api/sse/connect?clientId=${clientId}`);

// 监听连接打开事件
eventSource.onopen = (event) => {
    console.log('SSE 连接已建立');
};

// 监听消息事件（默认事件）
eventSource.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log('收到消息：', data);
};

// 监听自定义事件
eventSource.addEventListener('userNotification', (event) => {
    const data = JSON.parse(event.data);
    console.log('收到用户通知：', data);
});

// 监听错误事件
eventSource.onerror = (error) => {
    console.error('SSE 连接错误：', error);
};

// 关闭连接
function closeConnection() {
    eventSource.close();
    // 可选：通知服务器关闭连接
    fetch(`/api/sse/close/${clientId}`, { method: 'DELETE' });
}
```

### 4.2 Vue.js 实现示例

```javascript
export default {
    data() {
        return {
            clientId: '',
            messages: [],
            eventSource: null
        };
    },
    methods: {
        // 建立 SSE 连接
        connectSSE() {
            this.eventSource = new EventSource(`/api/sse/connect`);
            
            // 监听连接建立事件
            this.eventSource.onopen = (event) => {
                console.log('SSE 连接已建立');
            };
            
            // 监听消息事件
            this.eventSource.onmessage = (event) => {
                const data = JSON.parse(event.data);
                if (!this.clientId && event.lastEventId) {
                    // 保存服务器返回的客户端 ID
                    this.clientId = data;
                } else {
                    // 处理普通消息
                    this.messages.push(data.message);
                }
            };
            
            // 监听自定义事件
            this.eventSource.addEventListener('userNotification', (event) => {
                const data = JSON.parse(event.data);
                this.$notify({
                    title: '新通知',
                    message: data.message,
                    type: data.type
                });
            });
            
            // 监听错误事件
            this.eventSource.onerror = (error) => {
                console.error('SSE 连接错误：', error);
            };
        },
        // 关闭 SSE 连接
        disconnectSSE() {
            if (this.eventSource) {
                this.eventSource.close();
                if (this.clientId) {
                    // 通知服务器关闭连接
                    this.$http.delete(`/api/sse/close/${this.clientId}`);
                }
            }
        }
    },
    mounted() {
        this.connectSSE();
    },
    beforeDestroy() {
        this.disconnectSSE();
    }
};
```

## 5. 最佳实践

### 5.1 连接管理

- **合理设置超时时间**：根据业务需求设置合适的连接超时时间，避免资源浪费
- **客户端标识**：尽量使用有意义的客户端标识，如用户ID或会话ID，便于连接管理和问题排查
- **连接数量监控**：在生产环境中监控 SSE 连接数量，防止连接泄露

### 5.2 消息发送

- **消息拆分**：对于大型消息，启用消息拆分功能，避免单个大消息阻塞传输通道
- **批量发送**：尽量批量发送消息，减少网络开销
- **错误处理**：实现完善的错误处理机制，确保消息发送失败时能够及时处理

### 5.3 安全性

- **身份验证**：在建立 SSE 连接前进行身份验证，确保只有授权用户才能建立连接
- **数据加密**：对敏感数据进行加密处理，防止信息泄露
- **访问控制**：实现细粒度的访问控制，确保用户只能接收到有权限的消息

### 5.4 性能优化

- **连接池化**：在高并发场景下，考虑使用连接池化技术，减少连接创建和销毁的开销
- **消息压缩**：对消息进行压缩处理，减少网络传输量
- **分布式部署**：在分布式环境中，考虑使用 Redis 等分布式缓存存储连接信息，实现跨节点的消息推送

## 6. 常见问题与解决方案

### 6.1 连接断开问题

**问题**：客户端连接频繁断开

**解决方案**：
- 检查网络环境是否稳定
- 增加重连时间间隔（reconnectTimeMillis）
- 确保服务器没有主动关闭连接
- 检查是否有代理服务器或防火墙限制长连接

### 6.2 消息丢失问题

**问题**：部分消息未能成功发送到客户端

**解决方案**：
- 实现消息确认机制
- 使用消息队列存储待发送消息
- 实现消息重发机制
- 检查客户端是否正确处理接收到的消息

### 6.3 性能问题

**问题**：大量连接导致服务器性能下降

**解决方案**：
- 优化连接管理，及时关闭不活跃的连接
- 使用集群部署，分散连接压力
- 实现连接限流机制，控制单个服务器的最大连接数
- 优化消息发送逻辑，减少不必要的消息推送

## 7. 版本历史

| 版本 | 日期 | 变更内容 |
| --- | --- | --- |
| 4.1.2 | - | 当前版本 |

## 8. 附录

### 8.1 SSE 与 WebSocket 对比

| 特性 | SSE | WebSocket |
| --- | --- | --- |
| 通信方向 | 单向（服务器到客户端） | 双向 |
| 协议 | HTTP | WebSocket（基于 TCP） |
| 实现复杂度 | 简单 | 相对复杂 |
| 自动重连 | 原生支持 | 需要自行实现 |
| 数据格式 | 文本（支持事件流格式） | 文本和二进制 |
| 浏览器支持 | 大部分现代浏览器 | 几乎所有现代浏览器 |
| 适用场景 | 实时通知、数据更新 | 需要双向通信的场景（如聊天） |

### 8.2 相关资源

- [MDN Web Docs: Server-Sent Events](https://developer.mozilla.org/zh-CN/docs/Web/API/Server-sent_events)
- [Spring 官方文档: SseEmitter](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html)
- [HTML5 SSE 规范](https://html.spec.whatwg.org/multipage/server-sent-events.html)