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

模块的配置属性主要通过 `GXCanalProperties` 类及其子类进行管理。

*   **`GXCanalProperties`**: 基础配置类，定义了 RabbitMQ 相关的核心配置项：
    *   `concurrencyCount`: RabbitMQ 消费 Canal 消息的线程数量 (默认: "10")。
    *   `canalQueueName`: Canal 消息队列的名称 (默认: "canalQueue")。
    *   `exchangeName`: RabbitMQ 交换机的名称 (默认: "exchange.fanout.canal")。
    *   `routingKey`: RabbitMQ 路由键 (默认: "canal.example.exchange.routingkey")。
*   **`GXLocalCanalProperties`**: 本地配置文件加载实现。通过 `@PropertySource` 注解加载 `classpath:/${spring.profiles.active}/canal.yml` 文件中的配置。当项目中未引入 Nacos 相关依赖时，此配置类生效。
    *   配置文件路径示例: `config/dev/canal.yml`
*   **`GXNacosCanalProperties`**: Nacos 配置中心加载实现。通过 `@NacosConfigurationProperties` 注解从 Nacos 加载配置。当项目中引入 Nacos 相关依赖时，此配置类生效。
    *   Nacos Data ID: `canal.yml` (可配置)
    *   Nacos Group ID: `${nacos.config.group:DEFAULT_GROUP}` (可配置)

### 2.2. RabbitMQ 配置 (`GXCanalConfig`)

`GXCanalConfig` 类负责自动配置与 Canal 数据同步相关的 RabbitMQ 组件。它会根据 `GXCanalProperties` 中的配置信息，在 Spring 容器中创建并注册以下 Bean：

*   **FanoutExchange**: 创建一个持久化的扇出交换机，用于广播 Canal 捕获的数据变更消息。
*   **Queue**: 创建一个持久化的队列，用于存储待处理的 Canal 数据变更消息。
*   **Binding**: 创建队列与交换机之间的绑定关系，使用配置的路由键。

该配置类确保了消息在 RabbitMQ 重启后不会丢失，并通过属性配置文件管理连接参数，提高了配置的灵活性和安全性。

### 2.3. 常量 (`CanalConstant`)

`CanalConstant` 类定义了模块中使用的 RabbitMQ 相关配置参数的默认值。这些常量主要用于在代码中引用配置项的默认值，确保与 Canal 服务端配置的一致性。**注意：实际运行时，模块会优先使用 `GXCanalProperties` 中加载的配置值。**

*   `RABBITMQ_CANAL_CONCURRENCY_COUNT`: 默认并发消费者数量 ("10")。
*   `RABBITMQ_CANAL_QUEUE_NAME`: 默认队列名称 ("example")。
*   `RABBITMQ_CANAL_EXCHANGE_NAME`: 默认交换机名称 ("exchange.fanout.canal")。
*   `RABBITMQ_CANAL_ROUTING_KEY`: 默认路由键 ("canal.example.exchange.routingkey")。

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

*   **动态配置**: 监听器的队列名、交换机名、路由键和并发消费者数量均通过 SpEL 表达式从 `GXCanalProperties` 动态获取。这意味着可以在不重启应用的情况下，通过修改配置（如 Nacos 中的配置）来调整这些参数。
*   **消息处理**: 监听到消息后，会调用 `GXCanalMessageParseService` 的 `parseMessage` 方法对原始消息字符串进行解析和处理。
*   **延迟加载**: 使用 `@Lazy` 注解，实现按需加载，有助于减少应用启动时间。
*   **异常处理**: 对消息处理过程中可能发生的异常进行了捕获和日志记录。

### 2.6. 消息解析服务

*   **`GXCanalMessageParseService` (接口)**: 定义了消息解析服务的契约，核心方法是 `parseMessage(String message)`，接收原始消息字符串并返回一个 `Dict` 对象作为处理结果。
*   **`GXCanalMessageParseServiceImpl` (实现类)**: 提供了 `GXCanalMessageParseService` 接口的默认实现。
    1.  **消息验证**: 首先验证输入的消息是否为空和是否为有效的 JSON 格式。
    2.  **JSON 解析**: 将 JSON 字符串转换为 `GXCanalDataDto` 对象。
    3.  **服务定位**: 根据 `GXCanalDataDto` 中的 `database` 和 `table` 字段，动态构造服务 Bean 的名称。命名规则为驼峰式的 `数据库名_表名_Service` (例如：`user_account_Service`)。通过 `GXSpringContextUtils.getBean()` 方法从 Spring 容器中获取对应的处理服务实例。
    4.  **默认服务**: 如果找不到特定表的服务 Bean，会尝试获取名为 `defaultProcessCanalDataService` 的默认处理服务。
    5.  **类型检查**: 确保获取到的 Bean 是 `GXProcessCanalDataService` 的实例。
    6.  **操作分发**: 根据 `GXCanalDataDto` 中的 `type` 字段 (INSERT, UPDATE, DELETE)，调用 `GXProcessCanalDataService` 实例对应的方法进行处理。
    7.  **异常处理与结果返回**: 对处理过程中的异常进行捕获，并确保返回一个非 null 的 `Dict` 对象。

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
6.  **消息解析**: `GXCanalRabbitMQListener` 将接收到的消息字符串传递给 `GXCanalMessageParseServiceImpl` 的 `parseMessage` 方法。
7.  **服务分发**: `GXCanalMessageParseServiceImpl` 解析消息：
    *   将 JSON 字符串转换为 `GXCanalDataDto` 对象。
    *   根据 `database` 和 `table` 名称，查找对应的 `GXProcessCanalDataService` 实现类 Bean。
    *   如果找不到特定实现，则使用 `GXDefaultProcessCanalDataServiceImpl`。
8.  **数据处理**: 根据 `GXCanalDataDto` 中的 `type` (INSERT, UPDATE, DELETE)，调用选定的 `GXProcessCanalDataService` 实现类的相应方法，传入 `GXCanalDataDto` 和一个空的 `Dict` 参数。
9.  **业务逻辑执行**: 在 `GXProcessCanalDataService` 的实现方法中，开发者可以根据具体的业务需求处理这些数据变更，例如：
    *   更新缓存。
    *   同步数据到其他系统 (如 Elasticsearch, Redis)。
    *   发送通知。
    *   记录审计日志。
10. **结果返回**: 数据处理方法返回一个 `Dict` 对象，表示处理结果。该结果目前主要用于日志记录。

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

*   **幂等性处理**: 消息队列本身可能存在消息重复的风险 (尽管 RabbitMQ 在某些配置下可以尽量避免)。在设计数据处理逻辑时，应考虑操作的幂等性，确保重复处理同一消息不会产生副作用。
*   **异常处理与重试**: `GXCanalRabbitMQListener` 中对 `canalMessageParseService.parseMessage()` 的调用有基本的异常捕获。对于关键业务，你可能需要在自定义的 `GXProcessCanalDataService` 实现中加入更完善的异常处理、重试机制或死信队列逻辑。
*   **事务管理**: 如果数据处理逻辑涉及到数据库操作或其他事务性资源，确保在适当的事务边界内执行，以保证数据一致性。
*   **性能调优**: `concurrency-count` 配置项控制了 RabbitMQ 监听器的并发消费者数量。根据消息产生的速率和处理逻辑的复杂度，合理调整此参数以达到最佳的吞吐量和资源利用率。
*   **日志记录**: 模块本身提供了较为详细的 DEBUG 和 INFO 级别日志。在自定义处理逻辑时，也应添加清晰的日志记录，方便问题排查和状态监控。
*   **Canal 服务端配置**: 务必确保 Canal 服务端的 `instance.properties` 中关于 MQ 的配置（如 `canal.mq.topic`, `canal.mq.exchange` 等）与 `leaf-base-data-sync` 模块的配置（`exchangeName`, `routingKey`）匹配。
*   **安全性**: 原始 SQL (`GXCanalDataDto.sql`) 字段可能包含敏感信息，在记录或使用时需谨慎。
*   **资源清理**: 虽然模块本身处理了 Spring Bean 的生命周期，但如果在自定义逻辑中使用了需要手动管理的资源，请确保在适当的时候进行清理。
*   **版本兼容性**: 关注 Canal、RabbitMQ 和 Spring AMQP 等组件的版本兼容性问题。

通过遵循这些指南和最佳实践，你可以有效地利用 `leaf-base-data-sync` 模块构建稳定、高效的数据库数据同步系统。