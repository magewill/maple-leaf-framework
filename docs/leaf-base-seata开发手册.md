# leaf-base-seata 开发手册

## 1. 引言

`leaf-base-seata` 模块是 `maple-leaf-framework` 框架中用于集成 Seata 分布式事务解决方案的核心模块。它旨在为开发者提供一种简便的方式，在微服务架构下实现高效、可靠的分布式事务管理，并特别增强了对动态数据源的支持。

### 1.1 功能概览

- **无缝集成 Seata**：自动配置 Seata 客户端，简化与 Seata Server 的交互。
- **集成动态数据源**：通过 `GXSeataDynamicDataSourceConfig` 实现 Seata 与框架内动态数据源 (`cn.maple.core.datasource.config.GXDynamicDataSource`) 的无缝整合，允许在分布式事务环境中使用多数据源。
- **支持多种事务模式**：根据业务需求选择 AT、TCC、Saga 或 XA 事务模式（具体支持情况依赖于 Seata 版本和配置）。
- **灵活的配置加载**：支持从本地 `seata.yml` 文件或 Nacos 配置中心加载 Seata 相关配置。
- **与 Spring Cloud 整合**：良好支持 Spring Cloud Alibaba 生态，易于在微服务环境中使用。
- **集成 MyBatis-Plus**：优化了与 MyBatis-Plus 的集成，确保在 Seata 代理数据源的情况下，MyBatis-Plus 的各项功能（如分页插件、自动填充）依然可用。

### 1.2 依赖关系

本模块依赖于以下组件：

- leaf-base-datasource：提供动态数据源支持
- seata-spring-boot-starter：提供 Seata 分布式事务支持

## 2. 配置说明

### 2.1 Maven 依赖

在项目中使用 leaf-base-seata 模块，需要添加以下依赖：

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-seata</artifactId>
    <version>${maple.leaf.framework.version}</version>
</dependency>
```

### 2.2 Seata 配置文件

模块通过 `GXLocalSeataProperties` 和 `GXNacosSeataProperties` 支持两种方式加载 Seata 配置，并会根据 Classpath 中是否存在 Nacos 客户端 (`com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties`) 自动选择加载策略。

**配置加载优先级**：如果 Nacos 客户端存在，则优先尝试从 Nacos 加载配置；否则，从本地文件加载。

#### 2.2.1 本地配置文件加载 (通过 `GXLocalSeataProperties`)

当项目中**未**引入 Nacos 客户端依赖时，会通过 `GXLocalSeataProperties` 从本地文件系统加载 `seata.yml`。

- **文件路径**: `classpath:/${spring.profiles.active}/seata.yml`
  - `${spring.profiles.active}` 指的是当前 Spring Boot 激活的配置文件环境，例如 `dev`, `test`, `prod`。
  - 你需要在 `src/main/resources` 目录下创建对应环境的子目录，并将 `seata.yml` 放入其中。
- **加载工厂**: `cn.maple.core.framework.factory.GXYamlPropertySourceFactory` 用于解析 YAML 文件。

**本地 `seata.yml` 示例内容：**

```yaml
seata:
  enabled: true
  # Seata 应用编号，建议结合应用名和环境
  application-id: ${spring.application.name}-${spring.profiles.active}
  # Seata 事务服务组，需要与 Seata Server 端配置一致，建议结合应用名和环境
  tx-service-group: ${spring.application.name}-${spring.profiles.active}-group
  # 禁用 Seata 默认的自动数据源代理，因为本框架有自己的动态数据源代理机制 GXSeataDynamicDataSourceConfig
  enable-auto-data-source-proxy: false
  # Seata 服务端配置
  service:
    # 虚拟事务组到物理 Seata Server 集群的映射
    # Key (vgroup) 通常与 tx-service-group 一致或按约定规则映射
    vgroup-mapping:
      ${seata.tx-service-group}: default # "default" 是Seata Server集群名
    # Seata Server 集群列表
    grouplist:
      default: ${SEATA_ADDR:127.0.0.1:8091} # Seata Server 地址，可通过环境变量 SEATA_ADDR 覆盖
  # 访问 Seata Server 的 accessKey 和 secretKey (如果Seata Server开启了鉴权)
  access-key: ${SEATA_ACCESS_KEY:}
  secret-key: ${SEATA_SECRET_KEY:}
  # 客户端配置 (更多详细配置请参考Seata官方文档)
  client:
    rm:
      # 分支事务异步提交的缓冲队列大小
      async-commit-buffer-limit: 10000
      # 分支事务注册/提交/回滚失败时的重试次数
      report-retry-count: 5
      # 是否检查表元数据，生产环境建议关闭以提升性能
      table-meta-check-enable: false
      # 分支事务成功后是否上报TC
      report-success-enable: true
    tm:
      # 全局事务提交失败时的重试次数
      commit-retry-count: 5
      # 全局事务回滚失败时的重试次数
      rollback-retry-count: 5
    undo:
      # 是否进行数据校验
      data-validation: true
      # undo log 序列化方式，可选: jackson, fastjson, protobuf 等
      log-serialization: jackson
      # undo_log 表名
      log-table: undo_log
  # 传输层配置
  transport:
    # 网络通信协议类型，可选: TCP, UNIX_DOMAIN_SOCKET
    type: TCP
    # 服务端网络IO模型，可选: NIO, NATIVE (epoll for Linux)
    server: NIO
    # 是否开启心跳检测
    heartbeat: true
    # 序列化方式，需要与Seata Server端一致
    serialization: seata
    # 压缩方式，可选: none, gzip, zip, bzip2, lz4
    compressor: none
    # 是否批量发送客户端请求
    enable-client-batch-send-request: true
```


```yaml
# Seata 配置项
seata:
  # Seata 应用编号，默认为 ${spring.application.name}
  application-id: ${spring.application.name}-${spring.profiles.active}
  # Seata 事务组编号，用于 TC 集群名
  tx-service-group: ${spring.application.name}-${spring.profiles.active}-group
  # 禁用自动数据源代理，由框架统一管理
  enable-auto-data-source-proxy: false
  # 服务配置项
  service:
    # 虚拟组和分组的映射
    vgroup-mapping:
      # 格式是 tx-service-group: xxxx  xxxx一般为default
      ${seata.tx-service-group}: default
    # 分组和 Seata 服务的映射
    grouplist:
      default: ${SEATA_ADDR:127.0.0.1:8091}
  access-key: ${SEATA_ACCESS_KEY:}
```

#### 2.2.2 Nacos 配置中心加载 (通过 `GXNacosSeataProperties`)

当项目中**已**引入 Nacos 客户端依赖 (即 `com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties` 类存在于 classpath 中)，会通过 `GXNacosSeataProperties` 从 Nacos 配置中心加载 Seata 配置。

- **Nacos 配置详情**:
    - `dataId`: 固定为 `seata.yml`。
    - `groupId`: 从 Spring Boot 配置属性 `spring.cloud.nacos.config.group` 或 `nacos.config.group` 获取。如果两者都未配置，Nacos 客户端通常有其默认值 (如 `DEFAULT_GROUP`)。
    - `serverAddr`: Nacos 服务器地址，从 `spring.cloud.nacos.config.server-addr` 或 `nacos.config.server-addr` 获取。
    - `namespace`: Nacos 命名空间 ID，从 `spring.cloud.nacos.config.namespace` 或 `nacos.config.namespace` 获取。
    - `username` / `password`: (如果 Nacos 启用了权限控制) 分别从 `spring.cloud.nacos.username` / `spring.cloud.nacos.password` 或 `nacos.config.username` / `nacos.config.password` 获取。
- **自动刷新**: 配置了 `autoRefreshed = true`，当 Nacos 中的 `seata.yml` 内容发生变更时，客户端的 Seata 配置会自动更新。

**Nacos 中的 `seata.yml` 文件内容示例与本地配置章节中的示例相同。**

## 3. 核心组件

本章节将详细介绍 `leaf-base-seata` 模块中的核心组件及其作用。

### 3.1 GXSeataDynamicDataSourceConfig

`cn.maple.seata.config.GXSeataDynamicDataSourceConfig` 是本模块的核心配置类，主要负责整合 Seata 分布式事务与框架的动态数据源功能。其主要职责和特性包括：

- **条件化加载**：通过 `@ConditionalOnClass(value = {DruidDataSource.class, GXDynamicDataSourceConfig.class}, name = {"io.seata.rm.datasource.DataSourceProxy"})` 注解，确保只有在项目中同时存在 Druid 数据源、框架动态数据源配置以及 Seata 数据源代理类时，此配置才会生效。
- **优先加载**：使用 `@AutoConfigureBefore(DataSourceAutoConfiguration.class)` 注解，确保本配置在 Spring Boot 默认的数据源自动配置之前加载，从而接管数据源的创建和配置。
- **MyBatis-Plus 集成**：
    - 创建 `MybatisSqlSessionFactoryBean` 实例替代标准的 `SqlSessionFactoryBean`，以确保 MyBatis-Plus 的特性（如 Mapper 扫描、插件等）在 Seata 环境下能够正确工作。
    - 将框架提供的动态数据源 `GXDynamicDataSource` 注入到 `MybatisSqlSessionFactoryBean` 中。
    - 配置 `SpringManagedTransactionFactory` 作为事务工厂。
    - 应用 `MybatisPlusProperties` 中的配置，并集成 `MybatisPlusInterceptor`（例如，用于分页）。
    - 集成 `GXAutoFillMetaObjectHandler`，实现实体类中如创建时间、更新时间等字段的自动填充。

通过这个配置类，开发者可以在使用 Seata 进行分布式事务管理的同时，继续享受动态数据源带来的灵活性。

### 3.2 Seata 属性配置类

为了灵活加载 Seata 的配置参数，模块提供了两个属性配置类：

- **`cn.maple.seata.properties.GXLocalSeataProperties`**：
    - 通过 `@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})` 注解，当项目中不存在 Nacos 配置相关类时，此类生效。
    - 使用 `@PropertySource(value = {"classpath:/${spring.profiles.active}/seata.yml"}, factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = false)` 从类路径下当前激活环境对应的 `seata.yml` 文件加载配置。
    - 这是一个标记类，实际的 Seata 配置属性由 Seata 自身的 `@ConfigurationProperties` 机制处理。

- **`cn.maple.seata.properties.GXNacosSeataProperties`**：
    - 通过 `@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})` 注解，当项目中存在 Nacos 配置相关类时，此类生效。
    - 使用 `@NacosConfigurationProperties` 注解，从 Nacos 配置中心拉取 `seata.yml` 的配置内容。
        - `dataId`: 默认为 `seata.yml`。
        - `groupId`: 默认为 `${spring.cloud.nacos.config.group:${nacos.config.group:}}`。
        - `serverAddr`, `namespace`, `username`, `password` 等 Nacos 连接信息会从 Spring Cloud Nacos Config 或标准 Nacos 客户端配置中获取。
        - `autoRefreshed`: 设置为 `true`，支持配置的动态刷新。
    - 同样，这是一个标记类，用于触发 Seata 从 Nacos 加载配置。

这两个类的设计使得 Seata 的配置可以根据项目的部署环境（是否使用 Nacos）自动切换加载方式。

### 3.3 Seata 核心组件（由 Seata 自身提供和管理）

`leaf-base-seata` 模块主要关注于 Seata 与框架其他部分的整合，Seata 自身的核心组件如 `GlobalTransactionScanner`、`DataSourceProxy` 等由 Seata Starter 自动配置和管理。

- **`DataSourceProxy`**：Seata 会自动将应用程序中配置的（动态）数据源包装成 `io.seata.rm.datasource.DataSourceProxy`。这样，所有通过该数据源执行的 SQL 操作都会被 Seata 拦截和管理，以实现分布式事务的 AT 模式。
- **`GlobalTransactionScanner`**：用于扫描带有 `@GlobalTransactional`（全局事务发起者）和 `@Transactional`（分支事务参与者，在 Seata 环境下通常与 `@GlobalTransactional` 配合使用，或用于本地事务）注解的方法，并为这些方法创建事务代理，从而启动或加入全局事务。

## 4. 使用指南

### 4.1 启用分布式事务 (AT 模式示例)

在正确配置了 Seata 客户端并确保 Seata Server 可用之后，可以通过 `@GlobalTransactional` 注解来启用分布式事务。

```java
package com.example.service;

import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
// 假设存在 OrderMapper, InventoryServiceFeignClient, AccountServiceFeignClient
// OrderMapper: 操作本地订单数据库的MyBatis Mapper接口
// InventoryServiceFeignClient: 调用远程库存服务的Feign客户端
// AccountServiceFeignClient: 调用远程账户服务的Feign客户端

@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private InventoryServiceFeignClient inventoryService;

    @Autowired
    private AccountServiceFeignClient accountService;

    /**
     * 创建订单的业务方法，作为全局事务的发起方。
     *
     * @param orderRequest 包含订单信息的请求对象
     * @GlobalTransactional 参数说明:
     *   - name: 全局事务的名称，建议具有业务含义且唯一，便于追踪和监控。
     *           默认为该方法的方法签名。
     *   - timeoutMills: 全局事务的超时时间（毫秒）。如果事务在此时间内未完成，
     *                   Seata会尝试回滚。默认60000ms (1分钟)。
     *   - rollbackFor: 指定哪些类型的异常会触发事务回滚。默认为 RuntimeException 和 Error。
     *   - noRollbackFor: 指定哪些类型的异常不会触发事务回滚。
     *   - propagation: 事务传播行为（Seata的GlobalTransactional主要关注发起新事务，
     *                  对于参与者，通常由RPC框架的Seata拦截器处理）。
     */
    @GlobalTransactional(name = "create-order-tx", timeoutMills = 300000)
    public void createOrder(OrderRequest orderRequest) {
        System.out.println("开始创建订单流程，全局事务XID: " + io.seata.core.context.RootContext.getXID());

        // 1. 本地数据库操作：创建订单记录 (分支事务1)
        Order newOrder = new Order();
        // ... 从 orderRequest 填充 newOrder 的属性 ...
        orderMapper.insert(newOrder); // 此操作会被GXSeataDynamicDataSourceConfig代理的数据源拦截
        System.out.println("订单记录已插入，订单ID: " + newOrder.getId());

        // 2. 远程RPC调用：扣减库存 (分支事务2)
        // 假设 InventoryService 的对应方法也正确处理了Seata事务上下文（通常由Feign/Dubbo的Seata拦截器完成）
        inventoryService.decreaseStock(orderRequest.getProductId(), orderRequest.getQuantity());
        System.out.println("库存服务调用完成: 产品ID " + orderRequest.getProductId() + ", 数量 " + orderRequest.getQuantity());

        // 3. 远程RPC调用：扣减账户余额 (分支事务3)
        accountService.debit(orderRequest.getUserId(), orderRequest.getAmount());
        System.out.println("账户服务调用完成: 用户ID " + orderRequest.getUserId() + ", 金额 " + orderRequest.getAmount());

        // 模拟业务异常，测试回滚
        // if (orderRequest.getAmount() > 1000) {
        //     throw new RuntimeException("订单金额过大，触发回滚测试!");
        // }

        System.out.println("订单创建流程成功完成。");
    }
}
```

**关键点**：
- **`@GlobalTransactional`**: 标记事务发起点。
- **本地事务**: `orderMapper.insert(newOrder)` 会被 `GXSeataDynamicDataSourceConfig` 中配置的、由 Seata 代理的数据源执行，自动成为一个分支事务。
- **远程事务**: 对 `inventoryService` 和 `accountService` 的调用，如果这些远程服务也集成了 Seata 客户端，并且其 RPC 框架（如 Feign、Dubbo）配置了 Seata 拦截器，则事务上下文会通过 RPC 调用传递，远程服务中的数据库操作也会作为分支事务加入到当前全局事务中。
- **回滚**: 如果任何一个分支事务失败，或者 `createOrder` 方法自身抛出需要回滚的异常，Seata TC（Transaction Coordinator）会通知所有 RM（Resource Manager）回滚各自的分支事务。

### 4.2 动态数据源与 Seata 结合使用

`leaf-base-seata` 模块的核心优势之一就是无缝整合了 Seata 与框架的动态数据源能力。这意味着在 `@GlobalTransactional` 方法内部及其调用链中，你仍然可以使用 `@DS` (或其他框架动态数据源切换注解，如示例中的 `@GXDataSource`) 来切换数据源，所有操作都会被纳入当前的 Seata 全局事务。

```java
package com.example.service;

import cn.maple.core.datasource.annotation.DS; // 假设 @DS 是框架的动态数据源切换注解
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
// 假设有 MasterDbMapper 和 SlaveDbMapper

@Service
public class MultiDataSourceService {

    @Autowired
    private MasterDbMapper masterMapper;

    @Autowired
    private SlaveDbMapper slaveMapper;

    @GlobalTransactional(name = "multi-ds-operation-tx")
    public void processWithMultipleDataSources(BusinessObject data) {
        System.out.println("开始多数据源操作，全局事务XID: " + io.seata.core.context.RootContext.getXID());

        // 1. 在主数据源 (master) 上执行写操作
        performWriteOnMaster(data);

        // 2. 切换到从数据源 (slave) 执行读操作 (或写操作，如果slave也配置了undo_log)
        // 注意：如果slave只读，通常不建议将其纳入写事务，但技术上可行
        performOperationOnSlave(data.getId());

        // 3. 再次切换回主数据源 (master) 执行更新操作
        updateDataOnMaster(data);

        System.out.println("多数据源操作成功完成。");
    }

    @DS("master") // 切换到名为 "master" 的数据源 (此数据源也应被Seata代理)
    public void performWriteOnMaster(BusinessObject data) {
        // masterMapper.insert(data); ...
        System.out.println("在 master 数据源上执行写入操作: " + data.getContent());
    }

    @DS("slave")  // 切换到名为 "slave" 的数据源 (此数据源也应被Seata代理)
    public void performOperationOnSlave(String id) {
        // slaveMapper.selectById(id); ... 或 slaveMapper.updateSomething(id); ...
        System.out.println("在 slave 数据源上执行操作，ID: " + id);
    }

    @DS("master") // 再次确保操作在 master 数据源
    public void updateDataOnMaster(BusinessObject data) {
        // masterMapper.update(data); ...
        System.out.println("在 master 数据源上执行更新操作: " + data.getContent());
    }
}
```

**重要提示**:
- **`undo_log` 表**: 所有参与 Seata AT 模式分布式事务且有数据修改操作的数据库（无论是主库、从库或其他业务库），都**必须**创建 `undo_log` 表。Seata RM 依赖此表记录 SQL 执行前后的数据镜像，用于事务回滚。
- **数据源代理**: `GXSeataDynamicDataSourceConfig` 确保了通过 `@DS` 切换的每一个数据源都会被 Seata 的 `DataSourceProxy` 包装，从而使其操作能够被 Seata 事务管理器识别和控制。
- **MyBatis-Plus 功能兼容**: 由于 `GXSeataDynamicDataSourceConfig` 中使用了 `MybatisSqlSessionFactoryBean` 并正确配置了 MyBatis-Plus 插件和处理器（如 `GXAutoFillMetaObjectHandler`），因此 MyBatis-Plus 的各项功能（如自动填充创建/更新时间、分页查询等）在 Seata 环境下依然可以正常工作。

## 5. 注意事项

1.  **`undo_log` 表的重要性**: 对于 AT 模式，所有参与分布式事务且涉及数据修改的数据库实例中，都**必须**创建 `undo_log` 表。这是 Seata 实现事务回滚的基础。请参考 Seata 官方文档获取 `undo_log` 表的创建脚本。
2.  **数据源代理**: 本模块通过 `GXSeataDynamicDataSourceConfig` 自动为所有动态数据源（包括默认数据源和通过 `@DS` 切换的数据源）应用 Seata 的 `DataSourceProxy` 包装。因此，务必在 Seata 配置文件 (`seata.yml`) 中设置 `seata.enable-auto-data-source-proxy=false`，以避免双重代理或冲突。
3.  **Seata Server 连接**: 确保应用程序能够正确连接到 Seata Server。检查 `seata.yml` 中的 `service.vgroup-mapping` 和 `service.grouplist` 配置是否正确，以及网络是否通畅。
4.  **事务传播**: `@GlobalTransactional` 注解通常用于事务的发起方。参与方（如被 RPC 调用的服务）通常不需要此注解，其事务参与由 RPC 框架的 Seata 拦截器自动处理。确保远程调用的服务也正确集成了 Seata 客户端。
5.  **性能考量**: 分布式事务（尤其是 AT 模式）会引入额外的网络开销和锁竞争，可能对系统性能产生影响。请仔细评估业务场景对数据一致性的要求，并在必要时进行性能测试和优化。对于读多写少的场景，可以考虑其他柔性事务方案。
6.  **长事务**: 避免在 `@GlobalTransactional` 方法中执行耗时过长的操作，这可能导致事务超时或长时间占用数据库锁。Seata 的 `timeoutMills` 参数可以设置全局事务的超时时间。
7.  **异常处理**: `@GlobalTransactional` 的 `rollbackFor` 和 `noRollbackFor` 属性用于控制哪些异常会触发回滚。默认情况下，`RuntimeException` 和 `Error` 会触发回滚。请根据业务需求仔细配置。
8.  **幂等性设计**: 参与分布式事务的服务接口，尤其是 RM 侧，应考虑幂等性设计，以防止因网络重试等原因导致操作被重复执行。
9.  **Seata 版本兼容性**: 确保 `leaf-base-seata` 模块依赖的 Seata 客户端版本与您部署的 Seata Server 版本兼容。
10. **MyBatis-Plus 与 Seata**: 本模块已处理 MyBatis-Plus 与 Seata 的集成，包括 `SqlSessionFactory` 的配置。通常情况下，MyBatis-Plus 的功能（如分页、自动填充）在 Seata 环境下可以正常工作。

## 6. 常见问题

### 6.1 Seata Server 连接失败或事务注册失败

**问题描述**：
- 应用启动时日志显示无法连接到 Seata TC (Transaction Coordinator)。
- 调用 `@GlobalTransactional` 方法时报错，提示注册全局事务或分支事务失败。

**可能原因与解决方案**：
- **Seata Server 未启动或不可达**: 
    - 确认 Seata Server 进程是否正在运行。
    - 检查应用配置文件 (`seata.yml`) 中的 `seata.service.vgroup-mapping.${seata.tx-service-group}` 和 `seata.service.grouplist.default` (或对应的集群名) 配置的 Seata Server 地址和端口是否正确，并且应用服务器可以访问该地址和端口（检查防火墙、网络策略）。
- **事务组配置不匹配**: 
    - 确保 `seata.tx-service-group` 的值与 Seata Server 端配置的事务组名称一致。`vgroup-mapping` 中的 key 通常就是 `tx-service-group` 的值。
- **Nacos/Consul 等注册配置中心问题** (如果 Seata Server 使用注册中心):
    - 检查 Seata Server 是否已成功注册到配置的注册中心。
    - 检查客户端应用是否能从注册中心正确拉取到 Seata Server 的地址信息。
- **客户端配置错误**: 
    - 仔细核对 `seata.yml` 中的各项配置，特别是 `application-id` 和 `tx-service-group`。
- **网络问题**: 
    - 使用 `ping` 或 `telnet` 等工具测试应用服务器到 Seata Server 的网络连通性。

### 6.2 `@GlobalTransactional` 注解未生效，分布式事务未开启

**问题描述**：
- 调用了标记 `@GlobalTransactional` 的方法，但实际上没有发起全局事务（例如，通过 `RootContext.getXID()` 获取不到 XID，或者某个分支事务失败后没有发生回滚）。

**可能原因与解决方案**：
- **Spring AOP代理问题**: 
    - `@GlobalTransactional` 依赖 Spring AOP 实现。确保该注解标记的方法是通过 Spring 代理对象调用的。最常见的问题是**类内部方法调用**，例如 `this.methodWithAnnotation()`，这种调用不会经过代理，导致注解失效。需要通过注入的 Bean 实例来调用。
    - 确保 `@GlobalTransactional` 标记的方法是 `public` 的。
    - 检查是否有其他 AOP 配置与 Seata 的 AOP 拦截器冲突。
- **Seata 自动配置未生效**: 
    - 确认 `leaf-base-seata` 依赖已正确添加到项目中。
    - 检查 Spring Boot 是否正确加载了 Seata 的自动配置类 (如 `SeataAutoConfiguration`，以及本模块的 `GXSeataAutoConfiguration` 和 `GXSeataDynamicDataSourceConfig`)。查看启动日志中是否有相关 Bean 的创建信息。
    - 确保 `seata.enabled=true` (虽然默认为 true)。
- **数据源未被 Seata 代理**: 
    - 虽然 `GXSeataDynamicDataSourceConfig` 会尝试代理所有数据源，但如果存在自定义的、未被框架管理的数据源 Bean，它们可能没有被 Seata 代理。所有参与分布式事务的数据源都必须是 `DataSourceProxy` 的实例。
    - 确认 `seata.enable-auto-data-source-proxy=false` 已配置，避免 Seata 自身的自动代理与本模块的代理机制冲突。
- **RPC 调用未传递事务上下文**: 
    - 如果问题出在远程调用未加入全局事务，检查 RPC 框架 (如 Feign, Dubbo) 是否正确集成了 Seata 拦截器，并且这些拦截器已启用，能够传递 `XID`。
- **`GlobalTransactionScanner` 未正确初始化**: 
    - `GlobalTransactionScanner` 负责扫描 `@GlobalTransactional` 注解并创建代理。检查其是否被正确初始化，`application-id` 和 `tx-service-group` 是否正确传递给它。

### 6.3 `undo_log` 表相关错误

**问题描述**：
- 事务回滚时报错，提示找不到 `undo_log` 表，或 `undo_log` 表操作失败。
- 分支事务提交时，插入 `undo_log` 失败。

**可能原因与解决方案**：
- **`undo_log` 表未创建**: 
    - 在所有参与 AT 模式事务且有数据修改的数据库实例中，都必须创建 `undo_log` 表。请使用 Seata 官方提供的对应数据库类型的 DDL 脚本创建该表。
- **`undo_log` 表名配置错误**: 
    - Seata 客户端默认的 `undo_log` 表名是 `undo_log`。如果你的表名不同，需要在 `seata.yml` 中通过 `client.undo.log-table` 配置指定正确的表名。
- **数据库用户权限不足**: 
    - 确保应用连接数据库的用户具有对 `undo_log` 表的 `SELECT`, `INSERT`, `DELETE` 权限。
- **`undo_log` 序列化问题**: 
    - `client.undo.log-serialization` 配置了 Seata 不支持或 classpath 中缺少对应依赖的序列化方式。默认通常是 `jackson` 或 `fastjson`，确保相关依赖存在。

### 6.4 数据源代理冲突或重复代理

**问题描述**：
- 应用启动时报错，提示数据源已经被代理，或者出现与数据源代理相关的奇怪行为。

**可能原因与解决方案**：
- **`seata.enable-auto-data-source-proxy` 配置错误**: 
    - `leaf-base-seata` 模块通过 `GXSeataDynamicDataSourceConfig` 统一管理数据源的 Seata 代理。因此，必须在 `seata.yml` 中设置 `seata.enable-auto-data-source-proxy=false`，以禁用 Seata 自身的自动数据源代理功能，避免冲突。
- **项目中存在其他手动代理数据源的代码**: 
    - 检查项目中是否还有其他地方手动创建了 `DataSourceProxy` 实例，这可能导致重复代理。应统一由本模块管理。

### 6.5 分支事务超时或全局事务超时

**问题描述**：
- 日志中出现分支事务超时或全局事务超时的错误。

**可能原因与解决方案**：
- **业务逻辑执行时间过长**: 
    - 某个分支事务（本地数据库操作或远程 RPC 调用）执行时间超过了 Seata 配置的超时时间。
    - 优化业务逻辑，减少单个事务内的操作耗时。
- **Seata 超时配置不合理**: 
    - `@GlobalTransactional(timeoutMills = ...)`: 全局事务超时时间。
    - Seata Server 端和客户端都有相关的超时配置，如 RM 分支注册/提交的超时。检查这些配置是否适合你的业务场景。
- **网络延迟或抖动**: 
    - 应用与 Seata Server 之间、或应用与远程服务之间的网络延迟可能导致超时。
- **数据库锁竞争**: 
    - 长时间持有数据库锁可能导致其他事务等待，最终引发超时。优化 SQL，减少锁的粒度和持有时间。

## 7. 参考资料

- [Seata 官方文档](https://seata.io/zh-cn/docs/overview/what-is-seata.html)
- [Spring Boot 整合 Seata](https://seata.io/zh-cn/docs/user/quickstart.html)
- [Seata 分布式事务模式](https://seata.io/zh-cn/docs/dev/mode/at-mode.html)