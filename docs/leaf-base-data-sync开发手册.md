# leaf-base-data-sync 模块开发手册

## 1. 模块概述

`leaf-base-data-sync` 模块是 Maple Leaf Framework 中用于实现数据库变更数据实时同步的核心组件。它基于阿里巴巴开源的 Canal 组件，通过监听 MySQL 的 binlog 获取数据变更事件，然后借助 RabbitMQ 消息队列将这些事件分发给下游应用进行处理。该模块旨在提供一个可扩展、高可用的数据同步解决方案。

主要功能包括：

*   通过 Canal 实时捕获数据库的 INSERT, UPDATE, DELETE 操作。
*   利用 RabbitMQ 作为消息中间件，实现变更事件的异步分发和解耦。
*   提供灵活的配置方式，支持本地配置文件和 Nacos 配置中心。
*   支持动态路由，可以根据数据库名和表名将变更事件分发到特定的处理服务。
*   提供默认的数据处理逻辑，并允许用户自定义特定表的处理逻辑。

## 2. 核心组件

### 2.1. 配置属性

模块的配置属性统一由 `canal` 前缀绑定到 `GXCanalProperties` 及其子类，绑定入口固定为 Spring Boot 标准 `@ConfigurationProperties`，因此外置 `config/<profile>/canal.yml`（经配置导入）与 Nacos 导入的配置都能真实生效。

*   **`GXCanalProperties`**: 基础配置类，既有配置项：
    *   `concurrencyCount`: RabbitMQ 消费 Canal 消息的线程数量 (默认: "10")。
    *   `canalQueueName`: Canal 消息队列的名称 (默认: "canalQueue")。
    *   `exchangeName`: RabbitMQ 交换机的名称 (默认: "exchange.fanout.canal")。
    *   `routingKey`: RabbitMQ 路由键 (默认: "canal.example.exchange.routingkey")。
    *   新增：`mqType` (默认 `RABBITMQ`)、`consumerEnabled` (默认 `true`)、`configSource`、`handlerCacheSize`、`handlerMissLogLevel`，以及 `retry.maxAttempts`、`deadLetter.*`、`rocketmq.*` 三组嵌套配置（见 §6）。
*   **`GXLocalCanalProperties` / `GXNacosCanalProperties`**: 两种配置来源的载体类，均由 `GXCanalPropertiesConfig` 按条件创建，二者互斥，同一时刻只有一个 `GXCanalProperties` Bean。
    *   配置来源由 `canal.config-source=local|nacos` 显式选择；未配置时保持既有行为（类路径存在 Nacos 配置注解则用 Nacos 变体，否则用本地变体）。
    *   启动日志会打印实际生效来源、MQ 类型与关键容量参数。

### 2.2. RabbitMQ 配置 (`GXCanalConfig`)

`GXCanalConfig` 类负责自动配置与 Canal 数据同步相关的 RabbitMQ 组件。它会根据 `GXCanalProperties` 中的配置信息，在 Spring 容器中创建并注册以下 Bean：

*   **FanoutExchange**: 创建一个持久化的扇出交换机，用于广播 Canal 捕获的数据变更消息。
*   **Queue**: 创建一个持久化的队列，用于存储待处理的 Canal 数据变更消息；队列带死信参数（`x-dead-letter-exchange` / `x-dead-letter-routing-key`）。
*   **Binding**: 创建队列与交换机之间的绑定关系，使用配置的路由键。
*   **canalDeadLetterExchange / canalDeadLetterQueue / canalDeadLetterBinding**: 死信直连交换机与死信队列，默认名称 `canal.dlx` / `canal.dlq`。

该配置类保证消息被拒绝时进入死信队列而不是被丢弃；`canal.consumer-enabled=false` 或 `canal.mq-type=rocketmq` 时上述 Bean 全部不声明。所有 Bean 均标注 `@ConditionalOnMissingBean`，下游可自行覆盖。

### 2.3. 常量 (`GXCanalConstant`)

`GXCanalConstant` 类定义了默认值、消息头名、消费结局相关常量。**注意：实际运行时，模块会优先使用 `GXCanalProperties` 中加载的配置值。**

*   `RABBITMQ_CANAL_CONCURRENCY_COUNT`: 默认并发消费者数量 ("10")。
*   `RABBITMQ_CANAL_QUEUE_NAME`: 默认队列名称 ("canalQueue")。
*   `RABBITMQ_CANAL_EXCHANGE_NAME`: 默认交换机名称 ("exchange.fanout.canal")。
*   `RABBITMQ_CANAL_ROUTING_KEY`: 默认路由键 ("canal.example.exchange.routingkey")。
*   `HEADER_RETRY_COUNT` / `HEADER_TRACE_ID`: 重投计数与链路追踪头名 (`x-canal-retry-count` / `traceId`)。
*   `DEFAULT_DEAD_LETTER_*`: 死信交换机、队列与路由键默认值。

### 2.4. 数据传输对象 (`GXCanalDataDto`)

`GXCanalDataDto` 是一个核心的 DTO (Data Transfer Object)，用于封装从 Canal 接收到的数据库变更事件的完整信息。其主要字段包括：

*   `data`: `List<Dict>`，对于 INSERT 操作，包含新插入的数据；对于 UPDATE 操作，包含更新后的数据；对于 DELETE 操作，包含被删除的数据。
*   `old`: `List<Dict>`，仅在 UPDATE 操作时包含变更前的数据。
*   `id`: `Long`，当前操作记录的主键 ID 值 (可能为 null，具体主键信息参考 `pkNames`)。
*   `database`: `String`，发生变更的数据库名称。
*   `table`: `String`，发生变更的数据表名称。
*   `type`: `String`，操作类型 ("INSERT", "UPDATE", "DELETE")。
*   `es`: `Long`，事件时间戳 (毫秒)。
*   `ts`: `Long`，事务提交时间戳 (毫秒)。
*   `isDdl`: `Boolean`，是否为 DDL 修改。
*   `mysqlType`: `Dict`，数据库字段类型映射 (如: `{"id": "int(11)"}` )。
*   `sqlType`: `Dict`，SQL 字段的 JDBC 数据类型 (如: `{"id": 4}` )。
*   `pkNames`: `List<String>`，数据表的主键字段名列表 (如: `["id"]` )。
*   `sql`: `String`，触发当前变更事件的原始 SQL 语句。

### 2.5. 消息监听器 (`GXCanalRabbitMQListener`)

`GXCanalRabbitMQListener` 类是 RabbitMQ 消息的消费者。它使用 `@RabbitListener` 注解监听在 `GXCanalConfig` 中配置的队列。

*   **配置来源**: 队列名、交换机名、路由键与并发数通过属性占位符从 `Environment` 解析（`${canal.canal-queue-name:canalQueue}` 等）。这些属于**装配期配置**，运行期修改需重启才生效。
*   **监听器必须保持 eager**: 该类不得加 `@Lazy`。懒加载的监听器 Bean 不会被实例化，`@RabbitListener` 端点因此不会被注册（模块内已有回归用例锁定该行为）。
*   **消息处理**: 监听到消息后调用 `GXCanalMessageParseService#parse`，按事件逐个判定结局，再决定 ack / 有界重投 / 拒绝进死信。
*   **异常处理**: 处理器抛出的异常不再被吞掉；`GXCanalFailureHandler` 负责传输级动作（重投或死信）。

### 2.6. 消息解析服务

*   **`GXCanalMessageParseService` (接口)**: 提供两个入口：
    *   `List<GXCanalParseResultInnerDto> parse(String rawPayload)`：结构化结局入口，单个 JSON 对象返回 1 条结果，JSON 数组按元素逐条返回。处理器异常的处置随载荷形态而变：**单个对象**载荷按历史契约向上抛出（`parseMessage` 语义不变）；**JSON 数组**载荷按元素捕获，失败元素以 `RETRYABLE_FAILURE` 返回，其余元素继续处理（见 §6.3 批量语义）。
    *   `Dict parseMessage(String message)`：历史入口，保留原签名与语义，内部走 `parse` 并把首个结局映射回 `status`/`message`。
*   **`GXCanalMessageParseServiceImpl` (实现类)**:
    1.  **单次解析**: 用 `JSONUtil.parse` 解析一次，再按 `JSONObject` / `JSONArray` 分支处理，避免校验与反序列化各解析一遍。
    2.  **消息验证**: 空白载荷、非 JSON、标量 JSON 均判定为不可重试失败。
    3.  **DDL 跳过**: `isDdl=true` 或 `type` 为 `CREATE`/`ALTER`/`ERASE`/`DROP`/`TRUNCATE` 时判定为「跳过」，不再记为错误。
    4.  **服务定位**: 通过 `GXCanalHandlerResolver` 在启动期快照中按 `数据库名_表名_Service` 驼峰名查找（不再每条消息调用 `getBean`），结果写入有界缓存（含负结果）。
    5.  **默认服务**: 未命中时回退到 `defaultProcessCanalDataService`；两者都不存在则判定为不可重试失败。
    6.  **操作分发**: 按 `type` 分发到 `processInsert` / `processUpdate` / `processDelete`。
    7.  **结局判定**: 处理器返回 `null` 判定为不可重试失败（不再以 warning 方式 ack 掉）。

### 2.7. 数据处理服务

*   **`GXProcessCanalDataService` (接口)**: 定义了处理具体数据库变更事件的接口，包含三个核心方法：
    *   `processUpdate(GXCanalDataDto canalData, Dict param)`: 处理数据库更新操作。
    *   `processInsert(GXCanalDataDto canalData, Dict param)`: 处理数据库插入操作。
    *   `processDelete(GXCanalDataDto canalData, Dict param)`: 处理数据库删除操作。
    每个方法都接收 `GXCanalDataDto` (包含完整的变更数据) 和一个额外的 `Dict param` (可用于传递上下文信息)，并返回一个 `Dict` 作为处理结果。
*   **`GXDefaultProcessCanalDataServiceImpl` (默认实现类)**: 提供了 `GXProcessCanalDataService` 接口的默认实现，Bean 名称为 `defaultProcessCanalDataService`。
    *   该默认实现主要进行日志记录，打印出接收到的操作类型、数据库名、表名和影响的记录数。
    *   它不执行任何实际的业务逻辑，主要用作没有配置特定表处理服务时的兜底处理，或作为自定义实现的模板。

## 3. 工作流程

1.  **数据库变更**: 当 MySQL 数据库中的数据发生 INSERT, UPDATE, 或 DELETE 操作时，MySQL 的 binlog 会记录这些变更。
2.  **Canal 捕获**: Canal 服务端配置为监听目标数据库实例的 binlog。当检测到变更时，Canal 会解析 binlog，并将变更事件转换为其标准格式。
3.  **消息发送到 RabbitMQ**: Canal 服务端将格式化后的变更事件消息发送到配置的 RabbitMQ 交换机 (`exchangeName`)。
4.  **RabbitMQ 路由**: RabbitMQ 交换机（通常是 Fanout 类型）将消息广播到所有绑定到它的队列。`leaf-base-data-sync` 模块中的 `GXCanalConfig` 会自动创建一个队列 (`canalQueueName`) 并将其绑定到该交换机。
5.  **消息监听与接收**: `GXCanalRabbitMQListener` 监听指定的 RabbitMQ 队列。当队列中有新消息时，监听器会接收到原始的消息字符串。
6.  **消息解析**: `GXCanalRabbitMQListener` 将接收到的消息字符串传递给 `GXCanalMessageParseServiceImpl#parse`。
7.  **服务分发**: `GXCanalMessageParseServiceImpl` 解析消息：
    *   将 JSON 字符串转换为 `GXCanalDataDto` 对象（数组载荷逐元素处理）。
    *   通过 `GXCanalHandlerResolver` 查找对应的 `GXProcessCanalDataService` 实现类 Bean。
    *   如果找不到特定实现，则使用 `GXDefaultProcessCanalDataServiceImpl`。
8.  **数据处理**: 根据 `GXCanalDataDto` 中的 `type` (INSERT, UPDATE, DELETE)，调用选定的 `GXProcessCanalDataService` 实现类的相应方法，传入 `GXCanalDataDto` 和带 `operator` 的 `Dict` 参数。
9.  **业务逻辑执行**: 在 `GXProcessCanalDataService` 的实现方法中，开发者可以根据具体的业务需求处理这些数据变更，例如：
    *   更新缓存。
    *   同步数据到其他系统 (如 Elasticsearch, Redis)。
    *   发送通知。
    *   记录审计日志。
10. **结局与确认**: 每个事件得到 `成功 / 跳过 / 可重试失败 / 不可重试失败` 之一；可重试失败按 `canal.retry.max-attempts` 有界重投，超限或不可重试失败进入死信（RabbitMQ 为 `canal.dlq`，RocketMQ 为 `%DLQ%<group>` 或 `canal.rocketmq.dead-letter-topic`）。

## 4. 使用指南

### 4.1. 引入依赖

在你的 Maven 项目的 `pom.xml` 文件中添加 `leaf-base-data-sync` 模块的依赖：

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-data-sync</artifactId>
    <version>${project.parent.version}</version> <!-- 请替换为实际的版本号 -->
</dependency>
```

同时，确保项目中已包含 `leaf-base-framework` 和 `leaf-base-rabbitmq` (或 `leaf-base-nacos` 如果使用 Nacos 配置) 的依赖，因为 `leaf-base-data-sync` 依赖于它们。

### 4.2. 配置

你需要根据部署环境选择配置方式：本地配置文件或 Nacos。

**本地配置文件 (`canal.yml`)**

在 `src/main/resources` 目录下，根据当前激活的 Spring Profile (例如 `dev`, `test`, `prod`) 创建对应的 `canal.yml` 文件。例如，对于 `dev` 环境，文件路径为 `src/main/resources/dev/canal.yml`。

一个典型的 `canal.yml` 配置示例如下：

```yaml
canal:
  concurrency-count: "10"  # RabbitMQ消费者并发数
  canal-queue-name: "my_canal_queue" # 自定义Canal消息队列名称
  exchange-name: "my_canal_exchange" # 自定义RabbitMQ交换机名称
  routing-key: "my_canal_routing_key" # 自定义RabbitMQ路由键
```

**Nacos 配置**

如果项目中集成了 Nacos，`leaf-base-data-sync` 会自动从 Nacos 配置中心加载配置。

1.  确保 Nacos 服务可用，并且应用已正确连接到 Nacos。
2.  在 Nacos 中创建或修改配置：
    *   **Data ID**: `canal.yml` (默认，可以通过 `GXNacosCanalProperties` 的注解属性修改)
    *   **Group**: 应用配置的 Nacos Group (例如 `DEFAULT_GROUP`，可以通过 `GXNacosCanalProperties` 的注解属性修改)
    *   **Configuration Format**: `YAML`
3.  配置内容与本地 `canal.yml` 文件格式相同，例如：

```yaml
canal:
  concurrency-count: "15"
  canal-queue-name: "shared_canal_queue"
  exchange-name: "global_canal_exchange"
  routing-key: "global_canal_routing_key"
```

**确保 Canal 服务端配置一致**

非常重要的一点是，`leaf-base-data-sync` 模块中配置的 `exchange-name` 和 `routing-key` (如果Canal服务端配置了基于路由键的投递) 必须与 Canal 服务端实例的 `instance.properties` 文件中关于 RabbitMQ 的配置（如 `canal.mq.exchange` 和 `canal.mq.topic` 或 `canal.mq.routingKey`）保持一致，否则消息无法正确投递和接收。

### 4.3. 自定义处理逻辑

要处理特定数据库表的变更数据，你需要创建一个实现了 `GXProcessCanalDataService` 接口的 Spring Bean。

1.  **创建实现类**: 创建一个 Java 类并实现 `GXProcessCanalDataService` 接口。
2.  **实现处理方法**: 根据业务需求，实现 `processInsert`, `processUpdate`, 和 `processDelete` 方法。
3.  **注册为 Spring Bean**: 使用 `@Service` 或其他方式将该类注册为 Spring Bean。
4.  **命名规范 (重要)**: 为了让 `GXCanalMessageParseServiceImpl` 能够自动发现并调用你的处理服务，**Bean 的名称必须遵循特定的命名规范**：
    *   格式: `数据库名_表名_Service` (下划线连接，然后转换为驼峰命名)。
    *   例如，如果要处理 `order_db` 数据库中的 `order_info` 表，那么你的 Service Bean 名称应该定义为 `orderDb_orderInfo_Service`。
        ```java
        package com.example.canal.handler;

        import cn.hutool.core.lang.Dict;
        import cn.maple.canal.dto.GXCanalDataDto;
        import cn.maple.canal.service.GXProcessCanalDataService;
        import lombok.extern.slf4j.Slf4j;
        import org.springframework.stereotype.Service;

        import java.util.List;

        @Slf4j
        @Service("orderDb_orderInfo_Service") // Bean 名称遵循 数据库名_表名_Service 的驼峰形式
        public class OrderInfoCanalHandler implements GXProcessCanalDataService {

            @Override
            public Dict processUpdate(GXCanalDataDto canalData, Dict param) {
                log.info("Processing UPDATE for {}.{}:", canalData.getDatabase(), canalData.getTable());
                List<Dict> newDataList = canalData.getData();
                List<Dict> oldDataList = canalData.getOld();
                // 示例：打印更新前后的数据
                for (int i = 0; i < newDataList.size(); i++) {
                    log.info("  Record PKs: {}", canalData.getPkNames());
                    log.info("  Old data: {}", oldDataList.get(i));
                    log.info("  New data: {}", newDataList.get(i));
                }
                // 在这里添加你的业务逻辑，例如更新缓存、同步到ES等
                return Dict.create().set("status", "success").set("processed_updates", newDataList.size());
            }

            @Override
            public Dict processInsert(GXCanalDataDto canalData, Dict param) {
                log.info("Processing INSERT for {}.{}:", canalData.getDatabase(), canalData.getTable());
                List<Dict> dataList = canalData.getData();
                for (Dict data : dataList) {
                    log.info("  Inserted data: {}", data);
                }
                // 在这里添加你的业务逻辑
                return Dict.create().set("status", "success").set("processed_inserts", dataList.size());
            }

            @Override
            public Dict processDelete(GXCanalDataDto canalData, Dict param) {
                log.info("Processing DELETE for {}.{}:", canalData.getDatabase(), canalData.getTable());
                List<Dict> dataList = canalData.getData();
                for (Dict data : dataList) {
                    log.info("  Deleted data: {}", data);
                }
                // 在这里添加你的业务逻辑
                return Dict.create().set("status", "success").set("processed_deletes", dataList.size());
            }
        }
        ```

如果 `GXCanalMessageParseServiceImpl` 找不到特定表的处理服务，它会回退到使用 `GXDefaultProcessCanalDataServiceImpl`，该默认实现仅打印日志。

## 5. 注意事项与最佳实践

*   **幂等性处理**: 消息队列本身存在重复投递的可能（至少一次语义）。设计数据处理逻辑时必须保证幂等，重复处理同一消息不得产生副作用。
*   **异常处理与重试**: 框架已负责结局判定与有界重投，业务处理器**不要**自行吞异常或自行重试；处理器抛出异常即表示「可重试失败」，返回 `null` 表示「不可重试失败」。
*   **事务管理**: 如果数据处理逻辑涉及到数据库操作或其他事务性资源，确保在适当的事务边界内执行，以保证数据一致性。
*   **性能调优**: RabbitMQ 侧由 `canal.concurrency-count` 与 `spring.rabbitmq.listener.simple.prefetch` 共同决定在途上限（`并发数 × prefetch`）；RocketMQ 侧消费线程数由 `@RocketMQMessageListener` 注解常量固定为 20（不可配置，见 §6.2 注）。两种 MQ 的启动日志都会打印实际容量。
*   **日志记录**: 框架日志为 ASCII，并携带 `traceId`、`database`、`table`、`batchIndex`；自定义处理逻辑也应保持可检索。
*   **Canal 服务端配置**: 务必确保 Canal 服务端的 `instance.properties` 中关于 MQ 的配置（如 `canal.mq.topic`, `canal.mq.exchange` 等）与 `leaf-base-data-sync` 模块的配置（`exchangeName`, `routingKey`；RocketMQ 侧为 `canal.rocketmq.topic`）匹配。
*   **安全性**: 原始 SQL (`GXCanalDataDto.sql`) 字段可能包含敏感信息，在记录或使用时需谨慎。
*   **资源清理**: 消费容器由 Spring AMQP / rocketmq-spring 托管，模块自身不持有线程池；自定义逻辑中如需资源请自行释放。
*   **版本兼容性**: 关注 Canal、RabbitMQ、RocketMQ 与 Spring AMQP 等组件的版本兼容性问题。

## 6. 多消息中间件与失败处置（v4.3.0）

### 6.1. 切换消息中间件

```yaml
canal:
  mq-type: rabbitmq          # rabbitmq（默认） | rocketmq
  consumer-enabled: true     # false 时完全不装配消费侧
```

*   **RabbitMQ（默认）**：声明 `exchange.fanout.canal`、`canalQueue`、绑定与死信队列 `canal.dlx`/`canal.dlq`，使用 `@RabbitListener` 消费。
*   **RocketMQ**：需由下游显式引入 `cn.maple.framework:leaf-base-rocketmq`（本模块以 `optional` 依赖声明，避免仅用 RabbitMQ 的应用被迫引入 RocketMQ 客户端）；随后配置：

```yaml
canal:
  mq-type: rocketmq
  rocketmq:
    topic: canal-topic
    consumer-group: canal-consumer-group
    tag: "*"
    dead-letter-topic: canal-dead-letter   # 可选
rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}
```

*   切换只改配置，**业务处理器与 `database_table_Service` 命名约定零改动**。
*   选择 `rocketmq` 但缺少 `leaf-base-rocketmq` 时启动失败并提示解决方式；选择非法值时启动失败并列出合法取值。
*   未选中的中间件的基础设施不会被声明，也不会产生连接尝试。

### 6.2. 配置键总表

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `canal.mq-type` | `rabbitmq` | 消息中间件类型 |
| `canal.consumer-enabled` | `true` | 是否装配消费侧 |
| `canal.config-source` | 空（类路径探测） | `local` / `nacos` |
| `canal.concurrency-count` | `10` | RabbitMQ 并发消费者数 |
| `canal.canal-queue-name` | `canalQueue` | 主队列名 |
| `canal.exchange-name` | `exchange.fanout.canal` | 交换机名 |
| `canal.routing-key` | `canal.example.exchange.routingkey` | 绑定路由键 |
| `canal.retry.max-attempts` | `3` | 可重试失败的最大重投次数 |
| `canal.dead-letter.exchange` | `canal.dlx` | 死信交换机 |
| `canal.dead-letter.queue` | `canal.dlq` | 死信队列 |
| `canal.dead-letter.routing-key` | `canal.dead` | 死信路由键 |
| `canal.handler-cache-size` | `4096` | 处理器解析缓存上限 |
| `canal.handler-miss-log-level` | `warn-once` | 未命中处理器的日志级别（off/debug/warn-once/warn） |
| `canal.rocketmq.topic` | 空 | `mq-type=rocketmq` 时必填 |
| `canal.rocketmq.consumer-group` | 空 | `mq-type=rocketmq` 时必填 |
| `canal.rocketmq.tag` | `*` | 标签过滤表达式 |
| `canal.rocketmq.dead-letter-topic` | 空 | 不可重试失败的转发目标 |

> 注：RocketMQ 的 `consumeThreadMax`（固定 20）与 `maxReconsumeTimes`（固定 3）是 `@RocketMQMessageListener` 的注解属性，**不接受占位符或配置覆盖**；启动日志会打印实际生效值，因此不提供对应的配置键，避免"配了不生效"。

### 6.3. 结局与死信

| 结局 | 触发条件 | RabbitMQ 动作 | RocketMQ 动作 |
| --- | --- | --- | --- |
| 成功 | 处理器返回非空 `Dict` | ack | `CONSUME_SUCCESS` |
| 跳过 | DDL 事件 | ack + 计数 | `CONSUME_SUCCESS` + 计数 |
| 可重试失败 | 处理器抛异常 | 未达上限：带 `x-canal-retry-count` 重投后 ack；达上限：拒绝 → 死信队列 | `RECONSUME_LATER`，超过 `maxReconsumeTimes` 进入 `%DLQ%` |
| 不可重试失败 | 空载荷 / 非 JSON / 缺库表 / 未知类型 / 无处理器 / 返回 `null` | 拒绝 → 死信队列 | 转发 `dead-letter-topic`（未配置则仅记录） |

批量语义：一条载荷内的多个事件按元素逐个判定结局；**单个元素失败（含处理器抛异常）不会中止同批其他元素**，每个失败元素单独计数并留日志，而整批只执行一次传输动作（同时存在可重试与不可重试失败时，优先按可重试失败处理，交给重投机制）。

运行计数（`GXCanalConsumeStatistics`）在有 `MeterRegistry` 时以 `canal.consumer.*` Gauge 暴露，无 actuator 时仅保留内存计数，不影响功能。

### 6.4. 不支持范围

| 不支持能力 | 原因 | 替代方案 |
| --- | --- | --- |
| 顺序消费 / 分区有序 | 与既有 `concurrency=10` 无序语义对齐 | 按表维度拆分消费组，同一表只用单一消费者 |
| 事务消息 | 数据同步链路无事务边界 | 业务侧幂等 + 唯一键约束 |
| 批量确认 | 逐条判定结局是不丢消息的前提 | RabbitMQ 调大 `spring.rabbitmq.listener.simple.prefetch`；RocketMQ 注解 `consumeThreadMax` 为固定值 |
| 跨 MQ 双跑 | 重复消费与幂等责任超出模块职责 | 部署级切换 |
| 跨库 routing 过滤 | 会改变既有 fanout 交换机语义 | 下游各自建队列；RocketMQ 用 tag 过滤 |
| 运行期刷新装配期键 | 队列/交换机/主题/并发在容器启动时固定 | 改配置后重启 |
| 运行期新增处理器 Bean | 处理器集合在启动期快照 | 重启应用 |
| 复用 `GXCdcMessageDispatcher` 作为 Canal 分发主路径 | 事件模型不同，会破坏业务零改动 | 使用 `GXProcessCanalDataService` 扩展点 |

通过遵循这些指南和最佳实践，你可以有效地利用 `leaf-base-data-sync` 模块构建稳定、高效的数据库数据同步系统。