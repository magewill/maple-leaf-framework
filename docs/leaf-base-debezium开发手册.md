# Leaf-Base-Debezium 开发手册

## 1. 模块介绍

Leaf-Base-Debezium 是基于 Debezium 的变更数据捕获（CDC）模块，用于监听数据库变更并进行实时处理。该模块封装了 Debezium 的核心功能，提供了简单易用的接口，使开发人员能够方便地实现数据库变更事件的监听和处理。

### 1.1 主要功能

- **实时数据捕获**：监听数据库的 `INSERT`、`UPDATE`、`DELETE` 操作，实时捕获数据变更。
- **分布式单实例运行**：通过集成 Redisson 分布式锁，确保在分布式环境下只有一个服务实例运行 Debezium 引擎，避免重复处理事件。
- **灵活配置**：支持从本地 `debezium.yml` 文件或 Nacos 配置中心加载 Debezium 相关配置。
- **简易事件处理接口**：提供 `GXDebeziumService` 接口，开发者只需实现该接口即可处理捕获到的数据变更事件。
- **虚拟线程执行**：Debezium 引擎在 Java 虚拟线程 (Virtual Thread) 中执行，提高并发处理能力并减少资源消耗，特别适合 I/O 密集型的 CDC 任务。
- **优雅停机**：实现 `DisposableBean` 接口，确保在应用关闭时能够优雅地关闭 Debezium 引擎和释放相关资源。

### 1.2 依赖说明

模块主要依赖以下组件：

- Debezium 3.1.0.Final：提供核心的 CDC 功能
- Leaf-Base-Framework：框架基础功能
- Leaf-Base-Nacos：Nacos 配置中心支持
- Leaf-Base-Redisson：分布式锁支持

## 2. 快速开始

### 2.1 添加依赖

在项目的 `pom.xml` 文件中添加以下依赖：

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-debezium</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

### 2.2 实现 GXDebeziumService 接口

创建一个服务类实现 `GXDebeziumService` 接口，用于处理捕获到的数据变更事件：

```java
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.maple.debezium.services.GXDebeziumService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class MyDebeziumServiceImpl implements GXDebeziumService {
    @Override
    public void processCaptureDataChange(Dict data) {
        // 获取操作类型 (c:新增, u:更新, d:删除)
        String op = data.getStr("op");
        // 获取变更前的数据
        Dict before = Convert.convert(Dict.class, data.getObj("before"));
        // 获取变更后的数据
        Dict after = Convert.convert(Dict.class, data.getObj("after"));
        
        // 根据操作类型处理数据
        switch (op) {
            case "c" -> handleInsert(after);
            case "u" -> handleUpdate(before, after);
            case "d" -> handleDelete(before);
            default -> log.warn("未知的操作类型: {}", op);
        }
    }
    
    private void handleInsert(Dict data) {
        // 处理插入操作
        log.info("处理插入操作: {}", data);
    }
    
    private void handleUpdate(Dict before, Dict after) {
        // 处理更新操作
        log.info("处理更新操作, 更新前: {}, 更新后: {}", before, after);
    }
    
    private void handleDelete(Dict data) {
        // 处理删除操作
        log.info("处理删除操作: {}", data);
    }
}
```

### 2.3 配置 Debezium

#### 2.3.1 本地配置方式

在 `src/main/resources/{profile}/debezium.yml` 文件中添加配置（例如 `src/main/resources/dev/debezium.yml`）：

```yaml
debezium:
  config:
    name: ${spring.application.name}-engine
    snapshot.mode: no_data
    connector.class: io.debezium.connector.mysql.MySqlConnector
    offset.storage: io.debezium.storage.redis.offset.RedisOffsetBackingStore
    offset.storage.redis.address: ${DEBEZIUM_REDIS_ADDRESS:192.168.56.101:6379}
    offset.storage.redis.key: debezium:${spring.application.name}:${spring.profiles.active}:offsets
    offset.flush.interval.ms: 60000
    database.hostname: ${DEBEZIUM_MYSQL_ADDR}
    database.port: ${DEBEZIUM_MYSQL_PORT}
    database.user: ${DEBEZIUM_MYSQL_USERNAME}
    database.password: ${DEBEZIUM_MYSQL_PASSWORD}
    database.server.id: ${DEBEZIUM_MYSQL_SERVER_ID}
    topic.prefix: debezium-${DEBEZIUM_TOPIC_PREFIX}
    transforms.unwrap.drop.tombstones: false
    schema.history.internal.store.only.captured.tables.ddl: true
    schema.history.internal: io.debezium.storage.redis.history.RedisSchemaHistory
    schema.history.internal.redis.address: ${DEBEZIUM_REDIS_ADDRESS:192.168.56.101:6379}
    schema.history.internal.redis.key: debezium:${spring.application.name}:${spring.profiles.active}:schema-history
    include.schema.changes: false
    database.include.list: your_database_name
    table.include.list: your_database_name.your_table_name
```

#### 2.3.2 Nacos 配置方式

如果项目中集成了 Nacos 配置中心，可以在 Nacos 中创建 `debezium.yml` 配置文件，配置内容与本地配置相同。

## 3. 核心组件说明

### 3.1 GXDebeziumEngineConfig

`GXDebeziumEngineConfig` 是 Debezium 引擎的核心配置与管理类，负责 Debezium 引擎的生命周期管理，包括初始化、启动、事件处理和销毁。

**核心职责与特性：**

- **引擎初始化与启动**：
    - 在 Spring 容器启动后，通过 `@PostConstruct` 注解的 `initDebeziumEngine` 方法自动初始化并启动 Debezium 引擎。
    - 使用 `Thread.Builder.OfVirtual` 创建名为 `debezium-virtual-thread#` 的虚拟线程池 (`EXECUTOR_SERVICE`) 来异步执行 Debezium 引擎，充分利用虚拟线程轻量级的特性，提高 I/O 密集型任务的效率。
- **分布式锁机制**：
    - 在 `initDebeziumEngine` 方法中，调用 `GXDebeziumEngineLockConfig` 获取分布式锁（锁名格式为 `initial-engine-lock:{spring.application.name}:{spring.profiles.active}`）。
    - 此机制确保在分布式部署的多实例环境中，只有一个实例能够成功初始化并运行 Debezium 引擎，防止数据被重复消费。
    - 初始化失败或引擎退出时，通过 `GXDebeziumEngineLockConfig` 校验 owner token 后释放锁。
- **事件捕获与处理**：
    - 配置 Debezium 引擎使用 `io.debezium.engine.format.Json` 格式处理变更事件。
    - 通过 `debeziumEngine.notifying(record -> { ... })` 方法注册回调函数，处理从 Debezium 引擎接收到的 `ChangeEvent<String, String>` 记录。
    - 在回调中，将 `record.value()` (JSON 字符串) 解析为 `Dict` 对象，并提取 `payload` 部分，然后调用 `GXDebeziumService` 的 `processCaptureDataChange(payload)` 方法，将数据变更事件交由业务逻辑处理。
- **配置加载**：
    - 通过注入 `GXDebeziumProperties` 获取 Debezium 的相关配置，并将其转换为 `java.util.Properties` 对象用于初始化 Debezium 引擎。
- **优雅停机**：
    - 实现 `DisposableBean` 接口，在 Spring 容器关闭时，其 `destroy` 方法会被调用。
    - `destroy` 方法负责关闭 `debeziumEngine`，并关闭 `EXECUTOR_SERVICE` 线程池，确保资源得到正确释放。

**关键代码片段解析：**

```java
// 虚拟线程池初始化
static {
    Thread.Builder.OfVirtual ofVirtual = Thread.ofVirtual().name("debezium-virtual-thread#", 1);
    ThreadFactory factory = ofVirtual.factory();
    EXECUTOR_SERVICE = Executors.newThreadPerTaskExecutor(factory);
}

// Debezium引擎配置与事件通知
debeziumEngine = DebeziumEngine.create(Json.class)
        .using(properties) // properties 从 GXDebeziumProperties 加载
        .notifying(record -> {
            // ... 解析 record ...
            Dict payload = Convert.convert(Dict.class, dbChangeData.getObj("payload"));
            debeziumService.processCaptureDataChange(payload);
        }).build();

// 启动引擎
EXECUTOR_SERVICE.execute(debeziumEngine);
```

### 3.2 GXDebeziumService

`GXDebeziumService` 是一个核心接口，开发者需要实现此接口来定义如何处理从 Debezium 捕获到的数据库变更事件。分布式锁由模块内置的 `GXDebeziumEngineLockConfig` 管理，业务实现不需要处理锁生命周期。

**接口方法详解：**

1.  **`void processCaptureDataChange(Dict data)`**
    *   **功能**：此方法是业务逻辑处理的核心。当 Debezium 捕获到数据库变更时，`GXDebeziumEngineConfig` 会调用此方法，并将解析后的数据变更内容作为 `Dict`类型的参数传入。
    *   **参数 `data` (Dict)**：该字典对象通常包含以下关键信息 (具体结构取决于 Debezium 连接器和配置)：
        *   `op` (String): 操作类型。常见值有：
            *   `c`: Create (插入操作)
            *   `u`: Update (更新操作)
            *   `d`: Delete (删除操作)
            *   `r`: Read (快照读取操作，通常在初始快照期间)
        *   `before` (Dict): 数据变更前的值。对于 `UPDATE` 和 `DELETE` 操作，此字段非空。
        *   `after` (Dict): 数据变更后的值。对于 `CREATE` 和 `UPDATE` 操作，此字段非空。
        *   `source` (Dict): 事件的源信息，包含如连接器名称、版本、数据库名、表名、事务ID (如果可用) 等元数据。
        *   `ts_ms` (Long): 事件发生的时间戳 (毫秒级)。
        *   其他可能的字段：根据具体配置和数据库类型，可能还包含事务相关的元数据等。
    *   **实现建议**：在实现此方法时，应根据 `op` 字段判断操作类型，并从 `before` 和 `after` 字段获取具体的数据进行业务处理。务必进行充分的错误处理，避免因单个事件处理失败影响整个 CDC 流程。

2.  **兼容锁方法**
    *   `tryInitialEngineLock`、`initialEngineLock`、`renewInitialEngineLock`、`initialEngineUnLock`、`isEngineInitialized` 仅作为历史兼容入口保留，内部委托给 `GXDebeziumEngineLockConfig`。
    *   新代码不应在业务实现中调用或覆盖这些方法。

**示例代码（接口实现）：**

```java
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.maple.debezium.services.GXDebeziumService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class MyDebeziumServiceImpl implements GXDebeziumService {
    @Override
    public void processCaptureDataChange(Dict data) {
        log.info("接收到数据变更事件: {}", data);
        String op = data.getStr("op");
        Dict before = data.get("before") != null ? Convert.convert(Dict.class, data.getObj("before")) : null;
        Dict after = data.get("after") != null ? Convert.convert(Dict.class, data.getObj("after")) : null;
        Dict source = Convert.convert(Dict.class, data.getObj("source"));
        String tableName = source != null ? source.getStr("table") : "unknown_table";

        log.info("操作类型: {}, 表名: {}", op, tableName);

        switch (op) {
            case "c":
                log.info("处理插入操作 (表: {}): {}", tableName, after);
                // 添加插入操作的业务逻辑
                break;
            case "u":
                log.info("处理更新操作 (表: {}), 更新前: {}, 更新后: {}", tableName, before, after);
                // 添加更新操作的业务逻辑
                break;
            case "d":
                log.info("处理删除操作 (表: {}): {}", tableName, before);
                // 添加删除操作的业务逻辑
                break;
            default:
                log.warn("未知的操作类型 (表: {}): {}", tableName, op);
                break;
        }
    }
}
```

### 3.3 GXDebeziumProperties

`GXDebeziumProperties` 是一个抽象类，定义了获取 Debezium 配置的基础接口。它的主要作用是为不同的配置来源（如本地文件、Nacos）提供统一的配置获取方式。

**核心方法：**

-   **`public Map<String, String> getConfig()`**
    *   **功能**：获取 Debezium 引擎所需的配置项。
    *   **返回值**：一个 `Map<String, String>`，其中键是 Debezium 的配置属性名 (例如 `connector.class`, `database.hostname` 等)，值是对应的配置值。
    *   **默认实现**：在抽象类 `GXDebeziumProperties` 中，此方法返回一个空的 `HashMap`。具体的配置加载逻辑由其子类实现。

#### 3.3.1 GXLocalDebeziumProperties

`GXLocalDebeziumProperties` 是 `GXDebeziumProperties` 的一个具体实现，负责从**本地配置文件**加载 Debezium 的配置。

**特性与机制：**

-   **条件化加载**：使用 `@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})` 注解。这意味着只有当项目中**不存在** Nacos 配置相关的类时 (即没有集成 Nacos SDK)，这个本地配置加载类才会被激活并注册到 Spring 容器中。
-   **配置文件路径**：通过 `@PropertySource(value = {"classpath:/${spring.profiles.active}/debezium.yml"}, factory = GXYamlPropertySourceFactory.class, encoding = "utf-8", ignoreResourceNotFound = true)` 注解指定配置文件的位置。
    *   它会根据当前激活的 Spring Profile (通过 `${spring.profiles.active}` 获取，例如 `dev`, `test`, `prod`)，在 `classpath` 下对应的 Profile 目录中查找 `debezium.yml` 文件 (例如 `classpath:/dev/debezium.yml`)。
    *   使用自定义的 `GXYamlPropertySourceFactory` 来解析 YAML 格式的配置文件。
    *   `ignoreResourceNotFound = true` 表示如果配置文件不存在，不会抛出异常，允许模块在没有特定 `debezium.yml` 的情况下也能运行 (此时配置为空)。
-   **配置属性绑定**：使用 `@ConfigurationProperties(prefix = "debezium")` 注解，将 `debezium.yml` 文件中以 `debezium` 为前缀的配置项绑定到该类的 `config` 字段 (一个 `Map<String, String>`)。
    *   例如，配置文件中的 `debezium.config.name=my-engine` 会被加载到 `config` Map 中，键为 `name`，值为 `my-engine`。
-   **日志记录**：构造函数中会打印日志 `Debezium配置使用的是本地配置`，方便开发者确认当前生效的配置来源。

**示例 `debezium.yml` (本地配置):**

```yaml
dabezium:
  config:
    name: my-app-connector
    connector.class: io.debezium.connector.mysql.MySqlConnector
    database.hostname: localhost
    # ... 其他 Debezium 配置项
```

#### 3.3.2 GXNacosDebeziumProperties

`GXNacosDebeziumProperties` 是 `GXDebeziumProperties` 的另一个具体实现，负责从 **Nacos 配置中心**加载 Debezium 的配置。

**特性与机制：**

-   **条件化加载**：使用 `@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})` 注解。这意味着只有当项目中**存在** Nacos 配置相关的类时 (即集成了 Nacos SDK)，这个 Nacos 配置加载类才会被激活。
-   **Nacos 配置源**：通过 `@NacosConfigurationProperties` 注解指定从 Nacos 获取配置的详细信息：
    *   `dataId = "debezium.yml"`：指定在 Nacos 中配置文件的 Data ID。
    *   `groupId = "${spring.cloud.nacos.config.group:${nacos.config.group:}}"`：指定配置文件的 Group ID。它会优先使用 `spring.cloud.nacos.config.group` 的值，如果不存在则使用 `nacos.config.group`，如果两者都为空，则使用 Nacos 的默认 Group。
    *   `properties = @NacosProperties(...)`：用于配置连接 Nacos 服务器的地址、命名空间、用户名和密码。这些值通常从 Spring Boot 的 Nacos 配置属性 (如 `spring.cloud.nacos.config.server-addr`) 中获取。
-   **配置属性绑定**：同样使用 `@ConfigurationProperties(prefix = "debezium")` 注解，将从 Nacos 获取到的 `debezium.yml` 内容中以 `debezium` 为前缀的配置项绑定到该类的 `config` 字段。
-   **日志记录**：构造函数中会打印日志 `Debezium配置使用的是NACOS配置`。

**在 Nacos 中的配置示例 (`debezium.yml`):**

内容与本地 `debezium.yml` 格式相同，例如：

```yaml
dabezium:
  config:
    name: my-app-connector-nacos
    connector.class: io.debezium.connector.postgresql.PostgresConnector
    database.hostname: pg.example.com
    # ... 其他 Debezium 配置项
```

### 3.4 GXDebeziumInitialException

`GXDebeziumInitialException` 是一个自定义业务异常类，继承自框架基础的 `GXBusinessException`。它专门用于表示在 Debezium 引擎初始化过程中发生的特定错误。

**主要特点与用途：**

-   **继承体系**：作为 `GXBusinessException` 的子类，它能够融入框架统一的异常处理机制。
-   **专用性**：其命名清晰地表明了异常发生的场景——Debezium 初始化阶段。这有助于开发者快速定位问题来源。
-   **构造函数**：提供了多种构造函数，与 `GXBusinessException` 保持一致，允许在抛出异常时指定：
    *   `GXResultStatusCode`: 框架定义的结果状态码，用于标准化错误响应。
    *   `msg` (String): 详细的错误描述信息。
    *   `data` (Dict): 附带的额外数据，可能包含导致异常的上下文信息。
    *   `e` (Throwable): 原始的سبب异常 (cause)。
    *   `code` (int): 自定义的错误码 (如果未使用 `GXResultStatusCode`)。
-   **使用场景**：当 `GXDebeziumEngineConfig` 在初始化 Debezium 引擎（例如，加载配置、连接数据库、启动引擎组件等环节）遇到无法恢复的错误时，可以抛出此类型的异常。例如，如果配置文件缺失关键参数，或者无法连接到指定的数据库，都可以封装成 `GXDebeziumInitialException` 抛出。

**示例（可能的抛出点）：**

```java
// 在 GXDebeziumEngineConfig.java 的某个初始化步骤中
if (config == null || config.isEmpty()) {
    log.error("Debezium配置为空，请检查配置信息");
    // 可以选择抛出 GXDebeziumInitialException
    throw new GXDebeziumInitialException(GXResultStatusCode.PARAM_VALID_ERROR, "Debezium配置为空，无法初始化引擎");
    // return; // 原有逻辑是直接返回，也可以改为抛出异常
}
```

通过使用专门的异常类，可以更精确地捕获和处理与 Debezium 初始化相关的故障，例如在全局异常处理器中针对 `GXDebeziumInitialException` 进行特定的日志记录或告警操作。

## 4. 配置参数说明

以下配置参数均位于 `debezium.yml` 文件中的 `debezium.config` 路径下。例如：

```yaml
dabezium:
  config:
    name: "my-connector"
    database.hostname: "localhost"
    # ...其他参数
```

### 4.1 核心配置 (Core Configuration)

| 参数名                      | 说明                                                                                                                               | 示例值                                        |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| `name`                      | Debezium 连接器的唯一名称。在分布式环境中，此名称也可能用于协调。                                                                         | `${spring.application.name}-engine`           |
| `connector.class`           | 指定要使用的 Debezium 连接器类。根据源数据库类型选择。                                                                                   | `io.debezium.connector.mysql.MySqlConnector` (MySQL), `io.debezium.connector.postgresql.PostgresConnector` (PostgreSQL), `io.debezium.connector.sqlserver.SqlServerConnector` (SQL Server), `io.debezium.connector.oracle.OracleConnector` (Oracle), `io.debezium.connector.mongodb.MongoDbConnector` (MongoDB) |
| `offset.storage`            | 指定用于存储 Debezium 偏移量 (offset) 的类。集群部署应使用 Redis 等共享存储，避免 failover 后从错误 binlog 位置继续读取。                                                               | `io.debezium.storage.redis.offset.RedisOffsetBackingStore` (Redis存储), `io.debezium.storage.jdbc.offset.JdbcOffsetBackingStore` (JDBC存储) |
| `offset.flush.interval.ms`  | Debezium 将偏移量刷新到 `offset.storage` 的时间间隔（毫秒）。                                                                               | `60000` (60秒)                                |
| `snapshot.mode`             | 连接器启动时的快照模式。决定了连接器如何处理现有数据。常用的值有：                                                                                     | `initial` (全量快照后增量), `schema_only` (仅快照schema，然后增量，常用于生产避免锁表), `never` (从不快照，仅增量，需确保binlog/WAL包含历史), `no_data` (同`schema_only`) |
| `topic.prefix`              | Debezium 为其内部主题和数据变更事件主题生成的主题名称的前缀。                                                                                 | `dbz-events-${spring.application.name}`       |
| `include.schema.changes`    | 是否在输出的数据变更事件中包含 DDL 语句（如 `CREATE TABLE`, `ALTER TABLE`）。                                                                 | `false` (通常不包含以减少事件体积), `true`         |
| `database.include.list`     | (可选) 要捕获变更的数据库名列表，逗号分隔。如果未指定，则可能捕获所有非系统数据库 (取决于连接器)。                                                              | `mydb1,mydb2`                                 |
| `table.include.list`        | (可选) 要捕获变更的表名列表，逗号分隔，格式为 `databaseName.tableName`。如果指定了 `database.include.list`，则这里的数据库名应在其范围内。                      | `mydb1.orders,mydb1.customers,mydb2.products` |
| `table.exclude.list`        | (可选) 要排除捕获变更的表名列表，格式同 `table.include.list`。                                                                               | `mydb1.temp_table`                            |
| `column.include.list`       | (可选) 要包含在变更事件中的列名列表，格式为 `databaseName.tableName.columnName`。                                                              | `mydb1.orders.order_id,mydb1.orders.order_date` |
| `column.exclude.list`       | (可选) 要从变更事件中排除的列名列表，格式同 `column.include.list`。可用于排除敏感数据或大字段。                                                        | `mydb1.users.password_hash`                   |
| `decimal.handling.mode`     | 如何处理 `DECIMAL` 和 `NUMERIC` 类型的数据。可选值：`precise` (使用 `java.math.BigDecimal`), `double` (使用 `double`), `string` (使用字符串表示)。 | `precise`                                     |
| `tombstones.on.delete`      | 对于 `DELETE` 操作，是否在发送数据删除事件后再发送一个 tombstone 事件 (value为null的事件)。Kafka等消息队列常用此机制来处理日志压缩。                     | `true` (推荐用于Kafka), `false`                 |
| `transforms`                | (可选) 定义一个或多个单消息转换 (Single Message Transforms, SMTs) 的别名列表，逗号分隔。SMTs 用于在事件发布前修改事件内容。                        | `unwrap,filterByField`                        |
| `transforms.<alias>.type`   | (条件性必需) 如果定义了 `transforms`，则需要为每个别名指定SMT的完全限定类名。例如 `transforms.unwrap.type=io.debezium.transforms.ExtractNewRecordState`。 | `io.debezium.transforms.ExtractNewRecordState`  |
| `transforms.<alias>.*`      | (可选) 为特定别名的 SMT 配置其特定参数。例如 `transforms.unwrap.drop.tombstones=false`。                                                     | (具体参数取决于SMT类型)                       |

### 4.2 数据库连接配置 (Database Connection - 以MySQL为例)

以下参数特定于 `io.debezium.connector.mysql.MySqlConnector`。其他连接器会有类似的但可能不同的参数。

| 参数名                      | 说明                                                                 | 示例值                               |
| --------------------------- | -------------------------------------------------------------------- | ------------------------------------ |
| `database.hostname`         | 数据库服务器的主机名或 IP 地址。                                           | `localhost`, `${DEBEZIUM_MYSQL_ADDR}`  |
| `database.port`             | 数据库服务器的端口号。                                                   | `3306`, `${DEBEZIUM_MYSQL_PORT}`     |
| `database.user`             | 连接数据库的用户名。该用户需要有复制权限 (如 `REPLICATION SLAVE`, `REPLICATION CLIENT`)。 | `debezium_user`, `${DEBEZIUM_MYSQL_USERNAME}` |
| `database.password`         | 连接数据库的密码。                                                       | `dbz_password`, `${DEBEZIUM_MYSQL_PASSWORD}` |
| `database.server.id`        | MySQL 服务器的唯一 ID (整数)。在 MySQL 复制集群中必须唯一。Debezium 连接器会表现为一个复制从库。 | `184054`, `${DEBEZIUM_MYSQL_SERVER_ID}` |
| `database.server.name`      | (已废弃, 使用 `topic.prefix` 代替) 逻辑服务器名称，曾用于构成 Kafka 主题名。 | `my-mysql-server`                    |
| `database.ssl.mode`         | (可选) 控制与数据库的 SSL 连接。可选值：`disabled`, `preferred`, `required`, `verify_ca`, `verify_identity`。 | `preferred`                            |
| `gtid.source.filter.dml.events` | (高级) 是否基于GTID源过滤DML事件。                                       | `false`                              |
| `binlog.buffer.size`        | (高级) Debezium 读取 MySQL binlog 时内部缓冲区的大小（字节）。             | `0` (默认，自动调整)                   |

### 4.3 历史记录存储配置 (Schema History Storage - 以文件为例)

Debezium 需要存储数据库的 schema 历史，以便正确解析 binlog/WAL 中的数据。集群部署应使用 Redis 等共享存储，避免 failover 后新实例无法解析历史 binlog。

| 参数名                                  | 说明                                                                                             | 示例值                                          |
| --------------------------------------- | ------------------------------------------------------------------------------------------------ | ----------------------------------------------- |
| `schema.history.internal`               | 指定用于存储 schema 历史的类。                                                                       | `io.debezium.storage.redis.history.RedisSchemaHistory` (Redis存储), `io.debezium.storage.kafka.history.KafkaSchemaHistory` (Kafka存储), `io.debezium.storage.jdbc.history.JdbcSchemaHistory` (JDBC存储) |
| `schema.history.internal.redis.address` | Redis schema history 存储的连接地址。 | `${DEBEZIUM_REDIS_ADDRESS:192.168.56.101:6379}` |
| `schema.history.internal.redis.key` | Redis schema history 存储的 key，应按应用和 profile 隔离。 | `debezium:${spring.application.name}:${spring.profiles.active}:schema-history` |
| `schema.history.internal.store.only.captured.tables.ddl` | (可选) 是否只存储被 `table.include.list` 捕获的表的 DDL 变更。`true` 可以减小历史文件大小。        | `true`                                          |

**注意：**
- 很多参数支持使用 `${ENV_VAR}` 或 `${system_property}` 的形式来引用环境变量或系统属性。
- 不同数据库连接器 (MySQL, PostgreSQL, MongoDB 等) 会有其特定的配置参数，请参考 Debezium 官方文档中对应连接器的说明。
- 上述列表并非详尽无遗，Debezium 提供了大量高级配置选项以适应各种复杂场景。请务必查阅 <mcurl name="Debezium 官方文档" url="https://debezium.io/documentation/reference/stable/connectors/"></mcurl> 获取最准确和最全面的配置信息。



## 5. 最佳实践

### 5.1 精确指定监听范围

为了减少不必要的资源消耗和数据处理量，应尽可能精确地指定需要监听的数据库和表。

-   使用 `database.include.list` 参数指定要监控的数据库实例（Schema）。
-   使用 `table.include.list` 参数指定具体的表名，格式为 `databaseName.tableName`。

**示例：**

```yaml
dabezium:
  config:
    database.include.list: my_app_db
    table.include.list: my_app_db.orders,my_app_db.order_items
    # 如果只需要监听特定数据库下的所有表，可以省略 table.include.list
    # 或者只指定 database.include.list
```

如果需要排除某些表，可以使用 `table.exclude.list`。

### 5.2 细化列的捕获

对于包含敏感信息或非常大的字段（如LOB类型）的表，可以考虑使用 `column.exclude.list` 来排除这些列，以保护数据安全并减少网络传输和处理开销。

**示例：**

```yaml
dabezium:
  config:
    table.include.list: my_app_db.users
    column.exclude.list: my_app_db.users.password_hash,my_app_db.users.personal_details
```

反之，如果只需要捕获特定列，可以使用 `column.include.list`。

### 5.3 处理不同类型的变更事件

在 `GXDebeziumService` 的 `processCaptureDataChange(Dict data)` 方法中，Debezium 传递的 `data` 对象包含了变更事件的详细信息。其中，`op` 字段（Operation Code）标示了数据变更的类型：

-   **`c`**：创建 (Create) - 对应数据库的 `INSERT` 操作。
    *   `after` 字段包含了新插入行的完整数据。
-   **`u`**：更新 (Update) - 对应数据库的 `UPDATE` 操作。
    *   `before` 字段包含了更新前行的部分或完整数据（取决于数据库和配置）。
    *   `after` 字段包含了更新后行的完整数据。
-   **`d`**：删除 (Delete) - 对应数据库的 `DELETE` 操作。
    *   `before` 字段包含了被删除行的完整数据。
    *   `after` 字段通常为 `null`。
-   **`r`**：读取 (Read) - 对应快照 (snapshot) 过程中的数据读取。其结构与 `c` 类似。
-   **`t`**：截断 (Truncate) - 对应数据库的 `TRUNCATE` 操作 (并非所有连接器都支持)。

**示例代码片段：**

```java
@Override
public void processCaptureDataChange(Dict data) {
    String opType = data.getStr("op");
    Dict source = data.get("source", Dict.class); // 获取源信息，如表名、数据库名
    String tableName = source.getStr("table");

    if ("c".equals(opType)) {
        Dict afterData = data.get("after", Dict.class);
        log.info("捕获到表 '{}' 的插入操作: {}", tableName, afterData);
        // 处理插入逻辑
    } else if ("u".equals(opType)) {
        Dict beforeData = data.get("before", Dict.class);
        Dict afterData = data.get("after", Dict.class);
        log.info("捕获到表 '{}' 的更新操作: Before={}, After={}", tableName, beforeData, afterData);
        // 处理更新逻辑
    } else if ("d".equals(opType)) {
        Dict beforeData = data.get("before", Dict.class);
        log.info("捕获到表 '{}' 的删除操作: {}", tableName, beforeData);
        // 处理删除逻辑
    } else {
        log.warn("捕获到未知操作类型 '{}' 的事件: {}", opType, data);
    }
}
```

### 5.4 分布式环境下的单实例运行

本模块通过 `GXDebeziumEngineConfig` 和 `GXDebeziumEngineLockConfig` 中的 Redisson 分布式锁机制，确保在分布式部署（多个服务实例）的场景下，只有一个实例会成功获取锁并初始化和运行 Debezium 引擎。这避免了多个实例同时消费数据库变更日志导致的数据重复处理或冲突。

**关键点：**

-   **依赖 Redisson**：确保项目中已正确配置并引入 Redisson 客户端。
-   **锁的唯一性**：锁名格式为 `initial-engine-lock:{spring.application.name}:{spring.profiles.active}`，同一应用与 profile 下的实例会竞争同一个 Redis key。
-   **容错性**：锁使用带 TTL 的 Redis string key。运行期间每 1 分钟续期一次；连续续期异常或 owner 校验失败时，当前实例会主动关闭 Debezium 引擎。释放锁时会校验 owner token，避免误删其它实例新获得的锁。

开发者无需在业务层面额外处理分布式锁，模块已内置此逻辑。

### 5.5 健壮的事件处理与异常管理

在 `GXDebeziumService` 的 `processCaptureDataChange` 方法实现中，务必进行充分的异常处理。

-   **捕获具体异常**：尽量捕获可预期的具体业务异常或数据处理异常，而不是宽泛的 `Exception`。
-   **日志记录**：详细记录异常信息，包括导致异常的原始数据 (可以考虑脱敏后记录部分关键信息)，便于问题排查。
-   **错误处理策略**：
    *   **重试机制**：对于临时性错误 (如网络抖动导致调用下游服务失败)，可以考虑引入重试逻辑 (例如使用 Spring Retry)。
    *   **死信队列 (DLQ)**：如果事件处理失败且无法立即恢复，可以将原始事件发送到死信队列，后续进行人工干预或异步补偿处理，避免阻塞 Debezium 引擎的正常消费。
    *   **告警通知**：对于严重错误，应触发告警通知相关人员。
-   **避免阻塞**：`processCaptureDataChange` 方法的执行不应过于耗时或包含可能长时间阻塞的操作，以免影响 Debezium 引擎的吞吐量和偏移量提交。对于耗时操作，可以考虑将其异步化。

**示例：**

```java
@Service
public class MyDebeziumServiceImpl implements GXDebeziumService {

    private static final Logger log = LoggerFactory.getLogger(MyDebeziumServiceImpl.class);

    @Override
    public void processCaptureDataChange(Dict data) {
        try {
            // 1. 解析数据 (示例)
            String tableName = data.getDeepStr("source.table");
            Dict payload = data.get("after", Dict.class); // 假设处理插入和更新的 after 状态
            if (payload == null && "d".equals(data.getStr("op"))) {
                payload = data.get("before", Dict.class); // 删除操作的 before 状态
            }

            if (payload == null) {
                log.warn("无法获取有效的数据负载: {}", data);
                return; // 或其他处理
            }

            log.info("开始处理表 '{}' 的变更数据: {}", tableName, payload);

            // 2. 具体的业务逻辑
            // processBusinessLogic(tableName, payload);

            log.info("表 '{}' 的变更数据处理完成.", tableName);

        } catch (GXBusinessException be) {
            // 可预期的业务异常
            log.error("处理数据库变更事件时发生业务异常: {}, 事件数据: {}", be.getMsg(), data, be);
            // 根据异常类型决定是否发送到DLQ或告警
        } catch (JsonProcessingException jpe) {
            log.error("解析Debezium事件数据失败: {}, 事件数据: {}", jpe.getMessage(), data, jpe);
            // 可能需要发送到DLQ
        } catch (Exception e) {
            // 其他未预期的运行时异常
            log.error("处理数据库变更事件时发生未知异常, 事件数据: {}", data, e);
            // 考虑发送到DLQ并触发告警
            // 注意：不建议在此处重新抛出异常，除非有明确的全局异常处理机制能妥善处理并防止引擎终止
        }
    }
    // ... 省略锁相关方法
}
```

### 5.6 配置项的动态调整与监控

-   **Nacos 配置**：如果使用 `GXNacosDebeziumProperties`，可以利用 Nacos 的动态配置能力，在不重启服务的情况下调整部分 Debezium 配置 (需要 Debezium Engine 或相关组件支持动态刷新，或通过重启引擎实例使新配置生效)。
-   **监控 Debezium 状态**：Debezium 通过 JMX 暴露了大量的监控指标 (metrics)，可以集成到监控系统 (如 Prometheus + Grafana) 中，实时了解连接器状态、吞吐量、延迟、错误等信息，及时发现和处理问题。

### 5.7 资源规划

-   **数据库负载**：Debezium 读取数据库的事务日志 (binlog, WAL) 会对数据库产生一定的负载。需要评估并确保数据库有足够的资源处理这些额外的读取请求。
-   **Debezium 实例资源**：运行 Debezium 引擎的 Java 进程需要分配足够的内存和 CPU 资源，特别是在处理大量数据变更或复杂转换时。
-   **偏移量和历史存储**：确保为偏移量 (offset) 和 schema 历史记录分配足够的存储空间，并考虑其备份和恢复策略。

## 6. 常见问题与故障排查

### 6.1 Debezium 引擎无法启动或初始化失败

**症状：**

-   应用启动日志中出现 `GXDebeziumInitialException` 或其他与 Debezium 相关的错误。
-   `GXDebeziumEngineConfig` 中的 `afterSingletonsInstantiated` 方法未能成功执行或抛出异常。
-   未能获取到分布式锁 (如果其他实例已持有)。

**可能的原因与排查步骤：**

1.  **配置错误 (`debezium.yml` 或 Nacos 配置)：**
    *   **检查核心参数**：`name`, `connector.class`, `offset.storage`, `schema.history.internal` 是否正确配置。
    *   **数据库连接参数**：`database.hostname`, `database.port`, `database.user`, `database.password` 是否准确无误。对于特定数据库，如 MySQL 的 `database.server.id` 是否配置且在集群中唯一。
    *   **Redis 配置**：集群部署时确认 `offset.storage.redis.address`、`offset.storage.redis.key`、`schema.history.internal.redis.address` 和 `schema.history.internal.redis.key` 指向共享 Redis，并按应用和 profile 隔离。
    *   **Nacos 配置检查**：如果使用 Nacos，确认 `dataId`, `groupId` 是否正确，Nacos 服务是否可达，应用是否有权限读取该配置。
2.  **数据库权限不足：**
    *   Debezium 连接数据库的用户通常需要特定的权限来读取事务日志。例如，MySQL 用户需要 `REPLICATION SLAVE`, `REPLICATION CLIENT`, `SELECT` (对要监控的表) 等权限。PostgreSQL 用户需要复制权限和对 `pg_replication_slots` 的访问权限。请查阅对应数据库连接器的 Debezium 文档。
3.  **依赖问题：**
    *   确保 `pom.xml` 中引入了正确的 Debezium 连接器依赖 (如 `debezium-connector-mysql`) 以及可能的存储依赖 (如 `debezium-storage-redis` 如果使用 Redis 存储偏移量)。
    *   版本兼容性：检查 Debezium 版本与数据库版本、Kafka Connect (如果嵌入模式) 版本等的兼容性。
4.  **网络问题：**
    *   Debezium 实例无法连接到数据库服务器。
    *   如果使用 Nacos，无法连接到 Nacos 服务器。
    *   如果使用 Redisson 分布式锁，无法连接到 Redis 服务器。
5.  **资源问题：**
    *   数据库服务器资源不足 (CPU, 内存, IO)。
    *   Debezium 实例所在机器资源不足。
6.  **数据库端配置：**
    *   **MySQL**：确保 `binlog_format` 设置为 `ROW`，`binlog_row_image` 设置为 `FULL` (推荐)。确保 binlog 已开启。
    *   **PostgreSQL**：确保 `wal_level` 设置为 `logical`。确保已创建复制槽 (replication slot)，或者 Debezium 有权限创建。
    *   **SQL Server**：确保已启用变更数据捕获 (CDC) 功能。
7.  **分布式锁问题 (Redisson)：**
    *   检查 Redisson 配置是否正确，Redis 服务是否可用。
    *   查看日志，确认是否因为无法获取锁而未启动引擎。可能是其他实例已经持有锁并且正常运行。
8.  **类加载或版本冲突：**
    *   检查是否有传递性依赖导致的版本冲突，特别是与 Kafka Connect 相关的库 (如 Jackson, Jersey 等)。

### 6.2 引擎已启动，但无法捕获或处理数据库变更事件

**症状：**

-   Debezium 引擎看似正常运行，但 `GXDebeziumService` 的 `processCaptureDataChange` 方法没有被调用。
-   日志中没有数据变更相关的记录，或者有错误提示无法解析事件。

**可能的原因与排查步骤：**

1.  **`table.include.list` 或 `database.include.list` 配置不正确：**
    *   确保配置中准确指定了要监控的数据库和表。注意大小写敏感性 (取决于数据库和 Debezium 连接器)。
    *   检查是否有 `table.exclude.list` 意外排除了目标表。
2.  **数据库没有产生新的变更事件：**
    *   在配置的数据库和表上执行一些 DML 操作 (INSERT, UPDATE, DELETE) 来触发事件。
3.  **快照 (Snapshot) 模式问题：**
    *   如果 `snapshot.mode` 设置为 `never` 或 `schema_only` (且之前没有成功快照)，则只有新的变更才会被捕获。
    *   如果快照过程中断或失败，可能导致部分数据未被捕获。检查 Debezium 日志中关于快照执行状态的信息。
4.  **偏移量 (Offset) 问题：**
    *   如果 Debezium 之前运行过并记录了偏移量，它会从上次停止的位置继续。如果期望从头开始，可能需要清理旧的偏移量信息 (谨慎操作，可能导致数据重复或丢失)。
    *   检查偏移量存储是否正常工作 (如文件可写，Kafka 主题可访问等)。
5.  **Schema 历史问题：**
    *   如果 schema 历史记录不正确或损坏，Debezium 可能无法解析 binlog/WAL 中的数据。检查 schema 历史存储的配置和状态。
6.  **数据库事务未提交：**
    *   Debezium 通常只捕获已提交事务的变更。
7.  **数据库日志 (binlog/WAL) 问题：**
    *   **MySQL**：`binlog_format` 不是 `ROW`，或者 `binlog_row_image` 不是 `FULL`。
    *   **PostgreSQL**：`wal_level` 不是 `logical`。复制槽 (replication slot) 可能不存在、不活跃或被删除。如果复制槽的日志堆积过多，可能导致磁盘空间问题。
    *   日志文件可能已被数据库清理 (如果 Debezium 长时间未连接或处理速度跟不上)。确保数据库的日志保留策略与 Debezium 的需求相符。
8.  **过滤或转换 (SMTs) 配置错误：**
    *   如果配置了 Single Message Transforms (SMTs)，错误的 SMT 配置可能导致事件被过滤掉或转换失败。检查 SMTs 的配置和日志。
9.  **时间戳或时区问题：**
    *   某些情况下，数据库服务器、Debezium 实例和应用服务器之间的时区配置不一致可能导致问题，尤其是在处理时间相关字段时。
10. **Debezium 内部错误：**
    *   查看 Debezium 引擎的详细日志 (通常是 `DEBUG` 或 `TRACE` 级别)，查找是否有连接器内部的错误或警告信息。
11. **`GXDebeziumService` 实现问题：**
    *   确保 `processCaptureDataChange` 方法的逻辑正确，没有因为内部错误提前返回或吞掉异常导致看起来没有处理事件。

## 7. 参考资料

-   **Debezium 官方文档**: <mcurl name="Debezium Documentation" url="https://debezium.io/documentation/reference/stable/"></mcurl>
    *   这是获取关于 Debezium 所有方面（包括概念、架构、连接器配置、操作等）最权威信息的地方。
-   **各数据库连接器文档**: (在官方文档内查找)
    *   <mcurl name="MySQL Connector" url="https://debezium.io/documentation/reference/stable/connectors/mysql.html"></mcurl>
    *   <mcurl name="PostgreSQL Connector" url="https://debezium.io/documentation/reference/stable/connectors/postgresql.html"></mcurl>
    *   <mcurl name="SQL Server Connector" url="https://debezium.io/documentation/reference/stable/connectors/sqlserver.html"></mcurl>
    *   <mcurl name="Oracle Connector" url="https://debezium.io/documentation/reference/stable/connectors/oracle.html"></mcurl>
    *   <mcurl name="MongoDB Connector" url="https://debezium.io/documentation/reference/stable/connectors/mongodb.html"></mcurl>
-   **Debezium Engine (Embedded)**: <mcurl name="Debezium Engine API" url="https://debezium.io/documentation/reference/stable/development/engine.html"></mcurl>
    *   详细介绍了如何在 Java 应用中嵌入 Debezium 引擎，这正是本模块 `leaf-base-debezium` 所采用的方式。
-   **Debezium 社区**: <mcurl name="Debezium Community" url="https://debezium.io/community/"></mcurl>
    *   可以找到邮件列表、Gitter 聊天室等，用于提问和交流。
-   **Redisson (分布式锁)**: <mcurl name="Redisson Documentation" url="https://redisson.org/documentation/distributed-locks-and-synchronizers.html"></mcurl>
    *   了解本模块使用的 Redisson 分布式锁的更多细节。
-   **Spring Boot 相关配置文档** (如果使用 Spring Boot 管理配置):
    *   <mcurl name="Spring Boot Externalized Configuration" url="https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config"></mcurl>
    *   <mcurl name="Nacos Spring Boot" url="https://github.com/alibaba/nacos/blob/develop/README.md#nacos-spring-boot"></mcurl> (如果使用 Nacos 作为配置中心)

## 7. 参考资料

- [Debezium 官方文档](https://debezium.io/documentation/)
- [MySQL Connector 配置参数](https://debezium.io/documentation/reference/connectors/mysql.html#mysql-connector-properties)
- [Debezium Engine API](https://debezium.io/documentation/reference/development/engine.html)
