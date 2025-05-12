# Leaf-Base-Debezium 开发手册

## 1. 模块介绍

Leaf-Base-Debezium 是基于 Debezium 的变更数据捕获（CDC）模块，用于监听数据库变更并进行实时处理。该模块封装了 Debezium 的核心功能，提供了简单易用的接口，使开发人员能够方便地实现数据库变更事件的监听和处理。

### 1.1 主要功能

- 监听数据库变更事件（插入、更新、删除）
- 支持分布式环境下的单实例处理机制
- 支持本地配置和 Nacos 配置中心两种配置方式
- 提供简单的接口用于处理捕获到的数据变更

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
    offset.storage: org.apache.kafka.connect.storage.FileOffsetBackingStore
    offset.storage.file.filename: /data/offsets.dat
    offset.flush.interval.ms: 60000
    database.hostname: ${DEBEZIUM_MYSQL_ADDR}
    database.port: ${DEBEZIUM_MYSQL_PORT}
    database.user: ${DEBEZIUM_MYSQL_USERNAME}
    database.password: ${DEBEZIUM_MYSQL_PASSWORD}
    database.server.id: ${DEBEZIUM_MYSQL_SERVER_ID}
    topic.prefix: debezium-${DEBEZIUM_TOPIC_PREFIX}
    transforms.unwrap.drop.tombstones: false
    schema.history.internal.store.only.captured.tables.ddl: true
    schema.history.internal: io.debezium.storage.file.history.FileSchemaHistory
    schema.history.internal.file.filename: /data/schema/history.dat
    include.schema.changes: false
    database.include.list: your_database_name
    table.include.list: your_database_name.your_table_name
```

#### 2.3.2 Nacos 配置方式

如果项目中集成了 Nacos 配置中心，可以在 Nacos 中创建 `debezium.yml` 配置文件，配置内容与本地配置相同。

## 3. 核心组件说明

### 3.1 GXDebeziumEngineConfig

`GXDebeziumEngineConfig` 是 Debezium 引擎的配置类，负责初始化和管理 Debezium 引擎。该类使用 Java 虚拟线程执行 Debezium 引擎，并在应用关闭时负责优雅地关闭引擎和释放资源。

主要功能：

- 初始化 Debezium 引擎
- 通过分布式锁确保只有一个服务实例初始化并运行 Debezium 引擎
- 处理数据库变更事件并调用自定义处理逻辑
- 在应用关闭时优雅地关闭引擎和释放资源

### 3.2 GXDebeziumService

`GXDebeziumService` 是处理 Debezium 捕获的数据库变更事件的接口，定义了以下方法：

- `processCaptureDataChange(Dict data)`：处理捕获到的数据库变更事件
- `initialEngineLock(String key)`：获取分布式锁，确保只有一个服务实例初始化引擎
- `initialEngineUnLock(String key)`：释放分布式锁

### 3.3 GXDebeziumProperties

`GXDebeziumProperties` 是 Debezium 配置属性的抽象类，提供了获取配置的方法。该类有两个实现：

- `GXLocalDebeziumProperties`：从本地配置文件加载配置
- `GXNacosDebeziumProperties`：从 Nacos 配置中心加载配置

### 3.4 GXDebeziumInitialException

Debezium 初始化异常类，用于处理 Debezium 初始化过程中可能发生的异常。

## 4. 配置参数说明

### 4.1 基本配置

| 参数名 | 说明 | 示例值 |
| --- | --- | --- |
| name | 引擎名称 | ${spring.application.name}-engine |
| connector.class | 连接器类 | io.debezium.connector.mysql.MySqlConnector |
| snapshot.mode | 快照模式 | no_data |

### 4.2 数据库连接配置

| 参数名 | 说明 | 示例值 |
| --- | --- | --- |
| database.hostname | 数据库主机名 | localhost |
| database.port | 数据库端口 | 3306 |
| database.user | 数据库用户名 | root |
| database.password | 数据库密码 | password |
| database.server.id | 数据库服务器 ID | 1 |
| database.include.list | 要监听的数据库列表 | orders |
| table.include.list | 要监听的表列表 | orders.mgo_order |

### 4.3 存储配置

| 参数名 | 说明 | 示例值 |
| --- | --- | --- |
| offset.storage | 偏移量存储类 | org.apache.kafka.connect.storage.FileOffsetBackingStore |
| offset.storage.file.filename | 偏移量存储文件路径 | /data/offsets.dat |
| offset.flush.interval.ms | 偏移量刷新间隔（毫秒） | 60000 |
| schema.history.internal | Schema 历史存储类 | io.debezium.storage.file.history.FileSchemaHistory |
| schema.history.internal.file.filename | Schema 历史存储文件路径 | /data/schema/history.dat |

## 5. 最佳实践

### 5.1 监听特定表的变更

通过配置 `database.include.list` 和 `table.include.list` 参数，可以指定要监听的数据库和表：

```yaml
database.include.list: orders
table.include.list: orders.mgo_order,orders.mgo_order_item
```

### 5.2 处理不同类型的变更事件

在 `processCaptureDataChange` 方法中，可以根据操作类型（op 字段）处理不同类型的变更事件：

- `c`：创建（插入）操作
- `u`：更新操作
- `d`：删除操作

### 5.3 分布式环境下的部署

在分布式环境下（同一个服务部署了多个实例的情况下），只需要有一个服务实例处理 CDC 事件即可。模块通过 Redisson 分布式锁确保只有一个服务实例初始化并启动 Debezium 引擎。

### 5.4 异常处理

在处理数据库变更事件时，应当捕获并处理可能发生的异常，避免影响 Debezium 引擎的正常运行：

```java
@Override
public void processCaptureDataChange(Dict data) {
    try {
        // 处理数据库变更事件
    } catch (Exception e) {
        log.error("处理数据库变更事件时发生异常", e);
    }
}
```

## 6. 常见问题

### 6.1 Debezium 引擎无法启动

可能的原因：

- 配置错误：检查数据库连接信息是否正确
- 权限不足：确保数据库用户具有读取 binlog 的权限
- 文件路径不存在：确保偏移量存储文件和 Schema 历史存储文件的目录存在且可写

### 6.2 无法捕获数据库变更事件

可能的原因：

- 表未配置：检查 `table.include.list` 配置是否包含要监听的表
- binlog 格式不正确：确保 MySQL binlog 格式为 ROW
- binlog 已被清理：确保 binlog 保留时间足够长

### 6.3 处理变更事件时出现异常

可能的原因：

- 数据格式不匹配：检查数据类型转换是否正确
- 业务逻辑错误：检查处理逻辑是否有问题
- 资源不足：检查系统资源是否充足

## 7. 参考资料

- [Debezium 官方文档](https://debezium.io/documentation/)
- [MySQL Connector 配置参数](https://debezium.io/documentation/reference/connectors/mysql.html#mysql-connector-properties)
- [Debezium Engine API](https://debezium.io/documentation/reference/development/engine.html)