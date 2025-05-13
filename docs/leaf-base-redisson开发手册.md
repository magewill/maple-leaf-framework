# Leaf-Base-Redisson 模块开发手册

## 1. 模块概述

Leaf-Base-Redisson 模块是 Maple-Leaf-Framework 框架的核心组件之一，基于 Redisson 客户端进行了深度封装和扩展，旨在为开发者提供一套功能全面、易于使用且高性能的 Redis 操作解决方案。该模块不仅涵盖了常见的 Redis 应用场景，还针对特定需求进行了优化和增强。

核心功能包括：

- **分布式缓存服务**：提供灵活的分布式缓存实现，支持多种数据类型、自定义序列化、缓存预热、缓存穿透、缓存雪崩等问题的解决方案，并与 Spring Cache 深度集成。
- **分布式锁**：提供多种类型的分布式锁，包括可重入锁、公平锁、读写锁、联锁等，确保在分布式环境下的并发安全。
- **延迟队列**：实现高可靠的延迟消息处理机制，支持动态调整延迟时间，并能将延迟消息自动转发到可靠主题进行消费。
- **可靠消息队列 (Reliable Topic)**：基于 Redis Stream 实现，提供消息的可靠发布与订阅，保证消息至少被消费一次，并支持消费者组。
- **Debezium Redis Stream 适配**：特别针对 Debezium Server 输出到 Redis Stream 的消息格式进行适配，方便与 Debezium 集成进行数据变更捕获和处理。
- **限流工具**：提供基于 Redis 的分布式限流功能，支持多种限流算法，如令牌桶、漏桶等，有效防止接口过载。
- **配置动态刷新**：支持从本地配置文件和 Nacos 配置中心加载 Redisson 相关配置，并能在 Nacos 配置变更时动态刷新客户端，无需重启应用。
- **易用工具类**：封装了大量便捷的工具方法，简化了 Redis 的日常操作，如数据存取、原子计数、消息发布等。

本模块依赖于 `leaf-base-framework` 和 `leaf-base-nacos` 模块。具体的 Redisson 版本请参考项目 `pom.xml` 文件。

## 2. 快速开始

本章节将指导您如何快速地将 `leaf-base-redisson` 模块集成到您的 Spring Boot 项目中，并开始使用其核心功能。

### 2.1 添加 Maven 依赖

首先，在您的项目 `pom.xml` 文件中添加 `leaf-base-redisson` 的依赖：

```xml
<dependency>
    <groupId>cn.maple</groupId>
    <artifactId>leaf-base-redisson</artifactId>
    <version>YOUR_VERSION</version> <!-- 请替换为最新的版本号 -->
</dependency>
```

请将 `YOUR_VERSION` 替换为实际使用的 `leaf-base-redisson` 版本。您可以参考项目根目录的 `pom.xml` 或 Nexus 仓库来获取最新版本号。

### 2.2 配置 Redis 连接

在您的 Spring Boot 项目的 `application.yml` (或 `application.properties`) 文件中，配置 Redis 的连接信息。以下是一个最小化的配置示例：

```yaml
leaf:
  redisson:
    # 标准 Redisson 客户端配置
    connect:
      # 单机模式示例 (更多模式如集群、哨兵等请参考 Redisson 官方文档及后续配置说明章节)
      single-server-config:
        address: "redis://127.0.0.1:6379"
        # password: "your_redis_password" # 如果您的 Redis 需要密码
        database: 0 # 默认数据库
        # 其他更多配置项，如连接池大小、超时时间等，请参考 GXRedissonConnectProperties
    
    # (可选) 消息队列专用 Redisson 客户端配置 (如果与标准客户端配置相同，则无需单独配置)
    # mq:
      # connect: # 同上 connect 配置结构
      #   single-server-config:
      #     address: "redis://127.0.0.1:6379"
      #     database: 1 # 可以为 MQ 使用不同的数据库

    # (可选) Spring Cache Manager 相关配置
    # cache-manager:
      # default-expire-time: 3600 # 默认缓存过期时间（秒），0 表示永不过期
      # cache-names:
      #   users: 7200 # 为名为 'users' 的缓存单独设置过期时间为 7200 秒
      #   products: 0 # 'products' 缓存永不过期
```

更详细的配置选项（包括本地配置和 Nacos 配置）请参考后续的 **配置说明** 章节。

### 2.3 简单使用示例

完成上述配置后，您就可以在您的 Spring Boot Service 或 Component 中注入并使用 `leaf-base-redisson` 提供的服务了。

以下是一个使用 <mcsymbol name="GXRedissonCacheService" filename="GXRedissonCacheService.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/service/GXRedissonCacheService.java" startline="20" type="class"></mcsymbol> 进行简单缓存操作的示例：

```java
import cn.maple.redisson.service.GXRedissonCacheService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class MySimpleService {

    @Resource
    private GXRedissonCacheService redissonCacheService;

    private static final String MY_CACHE_BUCKET = "my_app_cache";

    public void cacheData(String key, String value) {
        // 缓存数据，过期时间10分钟
        redissonCacheService.setCache(MY_CACHE_BUCKET, key, value, 10, TimeUnit.MINUTES);
        System.out.println("Data cached: [" + key + " = " + value + "]");
    }

    public String getDataFromCache(String key) {
        String value = redissonCacheService.getCache(MY_CACHE_BUCKET, key, String.class);
        if (value != null) {
            System.out.println("Cache hit for key '" + key + "': " + value);
        } else {
            System.out.println("Cache miss for key '" + key + "'.");
            // 实际应用中，此处应从数据库或其他数据源加载数据，并回填缓存
        }
        return value;
    }
}
```

您可以在您的应用主类或测试类中调用上述服务：

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class MyApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(MyApplication.class, args);
        MySimpleService simpleService = context.getBean(MySimpleService.class);

        simpleService.cacheData("greeting", "Hello, Redisson!");
        String cachedGreeting = simpleService.getDataFromCache("greeting");
        // 尝试获取一个不存在的缓存
        simpleService.getDataFromCache("non_existent_key");
    }
}
```

运行 `MyApplication`，您应该能看到相应的缓存操作日志。

至此，您已经成功将 `leaf-base-redisson` 集成到项目中并执行了简单的缓存操作。更多高级功能和详细用法，请继续阅读后续章节。

## 3. 配置说明

`leaf-base-redisson` 模块提供了强大且灵活的配置机制，支持从本地配置文件（`application.yml` 或 `application.properties`）和 Nacos 配置中心加载 Redisson 相关配置。当使用 Nacos 时，配置信息会优先从 Nacos 获取，并且支持动态刷新，无需重启应用即可应用新的配置。

### 3.1 核心配置类：`GXRedissonSpringDataConfig`

<mcsymbol name="GXRedissonSpringDataConfig" filename="GXRedissonSpringDataConfig.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/config/GXRedissonSpringDataConfig.java" startline="46" type="class"></mcsymbol> 是模块的中央配置类，负责根据加载到的配置属性来创建和管理 Redisson 的核心组件。主要包括：

- **标准 Redisson 客户端 (`redissonClient`)**：通过 <mcsymbol name="redissonClient" filename="GXRedissonSpringDataConfig.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/config/GXRedissonSpringDataConfig.java" startline="79" type="function"></mcsymbol> 方法创建。这个客户端实例用于执行常规的 Redis 操作，如数据存取、分布式锁等。它会根据 `GXRedissonProperties` 的配置进行初始化。
- **消息队列专用 Redisson 客户端 (`redissonMQClient`)**：通过 <mcsymbol name="redissonMQClient" filename="GXRedissonSpringDataConfig.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/config/GXRedissonSpringDataConfig.java" startline="109" type="function"></mcsymbol> 方法创建。为了隔离不同业务场景下的 Redis 连接资源和配置，模块为消息队列（如延迟队列、可靠主题）提供了独立的客户端实例。它根据 `GXRedissonMQProperties` 进行配置。
- **Spring 缓存管理器 (`cacheManager`)**：通过 <mcsymbol name="cacheManager" filename="GXRedissonSpringDataConfig.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/config/GXRedissonSpringDataConfig.java" startline="138" type="function"></mcsymbol> 方法创建。该缓存管理器集成了 Spring Cache 抽象，允许开发者通过标准的 Spring Cache 注解（如 `@Cacheable`, `@CachePut`, `@CacheEvict`）来使用 Redisson 作为底层缓存实现。缓存的行为（如默认过期时间、空值缓存等）由 `GXRedissonCacheManagerProperties` 控制。

默认情况下，所有 Redisson 客户端都使用 `JsonJacksonCodec` 作为编解码器，这意味着存入 Redis 的对象会被序列化为 JSON 字符串，取出时再反序列化为 Java 对象。

### 3.2 配置属性类详解

模块定义了一系列 `@ConfigurationProperties` 注解的类，用于映射配置文件中的属性。每种属性类都有本地版本和 Nacos 版本，Nacos 版本会覆盖本地版本。

#### 3.2.1 连接配置：`GXRedissonConnectProperties`

<mcsymbol name="GXRedissonConnectProperties" filename="GXRedissonConnectProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/GXRedissonConnectProperties.java" startline="25" type="class"></mcsymbol> 定义了连接 Redis 服务器所需的基本参数，被 `GXRedissonProperties` 和 `GXRedissonMQProperties` 组合使用。

主要属性：

- `address` (String): Redis 服务器地址，格式为 `redis://host:port`。对于哨兵模式或集群模式，可以配置多个地址，用逗号分隔。
- `password` (String): Redis 密码（如果配置了）。
- `database` (int): 使用的 Redis 数据库索引，默认为 0。
- `username` (String): Redis 用户名（适用于 Redis 6.0+ ACL）。
- `clientName` (String): 客户端名称，方便在 Redis 服务端识别连接。
- `sslEnableEndpointIdentification` (boolean): 是否启用 SSL 端点识别，默认为 `true`。
- `sslProvider` (String): SSL 提供者，可选值为 `JDK` 或 `OPENSSL`，默认为 `JDK`。
- `sslTruststore` (String): SSL 信任库路径。
- `sslTruststorePassword` (String): SSL 信任库密码。
- `sslKeystore` (String): SSL 密钥库路径。
- `sslKeystorePassword` (String): SSL 密钥库密码。
- `scanInterval` (int): 集群模式下，节点扫描间隔时间（毫秒），默认为 1000。
- `pingConnectionInterval` (int): Ping 连接间隔时间（毫秒），用于检查连接是否存活，0 表示禁用，默认为 30000。
- `keepAlive` (boolean): 是否启用 TCP KeepAlive，默认为 `false`。
- `tcpNoDelay` (boolean): 是否启用 TCP NoDelay，默认为 `false`。
- `threads` (int): 工作线程池大小，默认为 16。
- `nettyThreads` (int): Netty 线程池大小，默认为 32。
- `transportMode` (String): 传输模式，可选值为 `NIO`, `EPOLL`, `KQUEUE`，默认为 `NIO`。
- `connectTimeout` (int): 连接超时时间（毫秒），默认为 10000。
- `timeout` (int): 命令执行超时时间（毫秒），默认为 3000。
- `retryAttempts` (int): 命令执行失败时的重试次数，默认为 3。
- `retryInterval` (int): 命令执行失败时的重试间隔（毫秒），默认为 1500。
- `reconnectionTimeout` (int): 重新连接超时时间（毫秒），默认为 3000。
- `failedAttempts` (int): 连接失败尝试次数，默认为 3。
- `subscriptionsPerConnection` (int): 每个连接的最大订阅数，默认为 5。
- `subscriptionConnectionMinimumIdleSize` (int): 订阅连接池最小空闲连接数，默认为 1。
- `subscriptionConnectionPoolSize` (int): 订阅连接池大小，默认为 50。
- `slaveConnectionMinimumIdleSize` (int): 从节点连接池最小空闲连接数，默认为 24。
- `slaveConnectionPoolSize` (int): 从节点连接池大小，默认为 64。
- `masterConnectionMinimumIdleSize` (int): 主节点连接池最小空闲连接数，默认为 24。
- `masterConnectionPoolSize` (int): 主节点连接池大小，默认为 64。
- `idleConnectionTimeout` (int): 连接空闲超时时间（毫秒），默认为 10000。
- `connectionMinimumIdleSize` (int): 连接池最小空闲连接数（单机模式），默认为 24。
- `connectionPoolSize` (int): 连接池大小（单机模式），默认为 64。

#### 3.2.2 标准 Redisson 配置：`GXRedissonProperties`

<mcsymbol name="GXRedissonProperties" filename="GXRedissonProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/GXRedissonProperties.java" startline="21" type="class"></mcsymbol> (及其本地实现 <mcsymbol name="GXLocalRedissonProperties" filename="GXLocalRedissonProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/local/GXLocalRedissonProperties.java" startline="14" type="class"></mcsymbol> 和 Nacos 实现 <mcsymbol name="GXNacosRedissonProperties" filename="GXNacosRedissonProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/nacos/GXNacosRedissonProperties.java" startline="20" type="class"></mcsymbol>) 用于配置 `redissonClient`。

- `prefix` (String): 配置属性的前缀，本地为 `spring.leaf.redisson.standard`，Nacos 为 `leaf.redisson.standard`。
- `connect` (<mcsymbol name="GXRedissonConnectProperties" filename="GXRedissonConnectProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/GXRedissonConnectProperties.java" startline="25" type="class"></mcsymbol>): 嵌套的连接配置。

#### 3.2.3 消息队列 Redisson 配置：`GXRedissonMQProperties`

<mcsymbol name="GXRedissonMQProperties" filename="GXRedissonMQProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/GXRedissonMQProperties.java" startline="20" type="class"></mcsymbol> (及其本地实现 <mcsymbol name="GXLocalRedissonMQProperties" filename="GXLocalRedissonMQProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/local/GXLocalRedissonMQProperties.java" startline="14" type="class"></mcsymbol> 和 Nacos 实现 <mcsymbol name="GXNacosRedissonMQProperties" filename="GXNacosRedissonMQProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/nacos/GXNacosRedissonMQProperties.java" startline="20" type="class"></mcsymbol>) 用于配置 `redissonMQClient`。

- `prefix` (String): 配置属性的前缀，本地为 `spring.leaf.redisson.mq`，Nacos 为 `leaf.redisson.mq`。
- `connect` (<mcsymbol name="GXRedissonConnectProperties" filename="GXRedissonConnectProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/GXRedissonConnectProperties.java" startline="25" type="class"></mcsymbol>): 嵌套的连接配置。

#### 3.2.4 缓存管理器配置：`GXRedissonCacheManagerProperties`

<mcsymbol name="GXRedissonCacheManagerProperties" filename="GXRedissonCacheManagerProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/GXRedissonCacheManagerProperties.java" startline="20" type="class"></mcsymbol> (及其本地实现 <mcsymbol name="GXLocalRedissonCacheManagerProperties" filename="GXLocalRedissonCacheManagerProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/local/GXLocalRedissonCacheManagerProperties.java" startline="14" type="class"></mcsymbol> 和 Nacos 实现 <mcsymbol name="GXNacosRedissonCacheManagerProperties" filename="GXNacosRedissonCacheManagerProperties.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/properties/nacos/GXNacosRedissonCacheManagerProperties.java" startline="20" type="class"></mcsymbol>) 用于配置 Spring Cache 管理器。

- `prefix` (String): 配置属性的前缀，本地为 `spring.leaf.redisson.cache-manager`，Nacos 为 `leaf.redisson.cache-manager`。
- `defaultExpiration` (long): 默认缓存过期时间（毫秒），0 表示永不过期。默认为 0。
- `defaultMaxIdleTime` (long): 默认缓存最大空闲时间（毫秒），0 表示不限制。默认为 0。
- `cacheNames` (Map<String, Long>): 特定缓存名称的过期时间配置，键为缓存名，值为过期时间（毫秒）。
- `cacheNullValues` (boolean): 是否缓存空值，防止缓存穿透，默认为 `true`。

### 3.3 Nacos 配置示例

在 Nacos 配置中心，你可以按以下结构配置 `leaf-base-redisson` 模块 (Data ID 通常为 `应用名.yaml` 或 `应用名-环境.yaml`)：

```yaml
leaf:
  redisson:
    standard: # 标准 Redisson 客户端配置
      connect:
        address: redis://your-redis-host:6379
        password: your-password
        database: 0
        # ... 其他 GXRedissonConnectProperties 配置
    mq:       # 消息队列专用 Redisson 客户端配置
      connect:
        address: redis://your-mq-redis-host:6379
        password: your-mq-password
        database: 1
        # ... 其他 GXRedissonConnectProperties 配置
    cache-manager: # Spring Cache 管理器配置
      defaultExpiration: 3600000 # 默认1小时过期
      cacheNames:
        userCache: 7200000    # userCache 2小时过期
        productCache: 600000 # productCache 10分钟过期
      cacheNullValues: true
```

**注意**：当同时存在本地配置和 Nacos 配置时，Nacos 中的配置项会覆盖本地配置文件中的相应配置项。如果 Nacos 中未配置某个属性，则会使用本地配置的默认值或指定值。

## 4. 缓存服务

`leaf-base-redisson` 模块提供了 <mcsymbol name="GXRedissonCacheService" filename="GXRedissonCacheService.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/services/GXRedissonCacheService.java" startline="26" type="class"></mcsymbol> 接口及其默认实现 <mcsymbol name="GXRedissonCacheServiceImpl" filename="GXRedissonCacheServiceImpl.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/services/impl/GXRedissonCacheServiceImpl.java" startline="36" type="class"></mcsymbol>，封装了对 Redisson 各种数据结构的操作，简化了分布式缓存的使用。该服务利用 `redissonClient`（标准 Redisson 客户端）进行操作。

### 4.1 核心设计与特性

- **统一接口**：通过 <mcsymbol name="GXRedissonCacheService" filename="GXRedissonCacheService.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/services/GXRedissonCacheService.java" startline="26" type="class"></mcsymbol> 提供一致的缓存操作方法，屏蔽底层 Redisson API 的复杂性。
- **多样化数据结构支持**：
    - **RBucket**: 用于存储单个对象（字符串、数字、序列化对象等）。对应 `setCache`, `getCache` 等方法中的 `bucketName` 参数，实际上 Redisson 的 `RBucket` 名称就是缓存的 Key。文档中的 `bucketName` 更像是一个命名空间或业务分类的概念，实际实现中会与 `key` 拼接或组合形成最终的 Redis Key。
    - **RMap**: 用于存储哈希结构，适合存储对象的多个字段。对应 `setMapCache`, `getMapCacheValue`, `deleteMapCacheKeys` 等方法。
    - **RList**: 用于存储列表结构。对应 `setListCache`, `getListCache`, `removeListCacheValue` 等方法。
    - **RSet**: 用于存储集合结构。对应 `setSetCache`, `getSetCache`, `removeSetCacheValue` 等方法。
    - **RScoredSortedSet**: 用于存储有序集合。对应 `setSortedSetCache`, `getSortedSetCacheByScore`, `removeSortedSetCacheValue` 等方法。
- **过期策略**：所有设置缓存的方法都支持指定过期时间 (`ttl`) 和时间单位 (`timeUnit`)。如果 `ttl` 小于等于 0，则表示缓存永不过期。
- **原子操作**：提供了如 `incrCache` (原子增) 和 `decrCache` (原子减) 等原子操作方法，确保并发环境下的数据一致性。
- **批量操作**：支持批量删除缓存 (`deleteCaches`)、批量获取 RBucket (`getBuckets`) 等，提高操作效率。
- **键名管理**：内部使用 `CacheKeyPrefix` 枚举（如果存在，或者开发者自定义的键前缀策略）来规范和管理缓存键的前缀，避免键冲突，方便按业务模块管理缓存。
- **空值处理**：获取缓存时，如果 Redis 中不存在对应的键，会返回 `null`。开发者可以结合 `@Cacheable(cacheNullValues = true)` (Spring Cache层面) 或在业务代码中处理空值，以防止缓存穿透。
- **序列化**：默认使用 `JsonJacksonCodec`，对象会被序列化为 JSON 存储。可以通过配置 RedissonClient 的 Codec 来更改序列化方式。

### 4.2 主要方法概览

以下列出 <mcsymbol name="GXRedissonCacheService" filename="GXRedissonCacheService.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/services/GXRedissonCacheService.java" startline="26" type="class"></mcsymbol> 中的部分核心方法及其说明。方法中的 `bucketName` 参数通常作为缓存键的一部分或前缀。

#### 4.2.1 RBucket (通用对象/字符串缓存)

```java
// 设置带过期时间的缓存 (RBucket)
// bucketName: 业务分类或命名空间
// key: 缓存的唯一标识
// value: 要缓存的对象
// ttl: 过期时间数值
// timeUnit: 过期时间单位
void setCache(String bucketName, String key, Object value, long ttl, TimeUnit timeUnit);

// 设置永久缓存 (RBucket)
void setCache(String bucketName, String key, Object value);

// 获取缓存 (RBucket)
<R> R getCache(String bucketName, String key, Class<R> clazz);

// 尝试设置缓存，如果键已存在则不操作 (SETNX)
boolean trySetCache(String bucketName, String key, Object value, long ttl, TimeUnit timeUnit);

// 删除缓存 (RBucket)
boolean deleteCache(String bucketName, String key);

// 批量删除多个 RBucket 缓存
long deleteCaches(String bucketName, Collection<String> keys);

// 原子增加 (适用于数值类型的 RBucket)
long incrCache(String bucketName, String key, long delta);

// 原子减少 (适用于数值类型的 RBucket)
long decrCache(String bucketName, String key, long delta);
```

#### 4.2.2 RMap (哈希缓存)

```java
// 向 RMap 中设置一个键值对
// mapName: RMap 的名称 (相当于缓存的大类)
// key: RMap 中的字段名
// value: 字段值
void setMapCache(String mapName, String key, Object value, long ttl, TimeUnit timeUnit);

// 从 RMap 中获取一个字段的值
<R> R getMapCacheValue(String mapName, String key, Class<R> clazz);

// 获取 RMap 中的所有键值对
<K, V> Map<K, V> getAllMapCache(String mapName, Class<K> keyClazz, Class<V> valueClazz);

// 删除 RMap 中的一个或多个字段
long deleteMapCacheKeys(String mapName, Collection<String> keys);

// 获取 RMap 对象本身
RMap<String, Object> getMap(String mapName);
```

#### 4.2.3 RList (列表缓存)

```java
// 向 RList 头部添加元素
void addListCacheFirst(String listName, Object value, long ttl, TimeUnit timeUnit);

// 向 RList 尾部添加元素
void addListCacheLast(String listName, Object value, long ttl, TimeUnit timeUnit);

// 获取 RList 中的部分或全部元素
<R> List<R> getListCache(String listName, Class<R> clazz, int startIndex, int endIndex);

// 从 RList 中移除指定元素
boolean removeListCacheValue(String listName, Object value, int count); // count: 0移除所有, >0从头移除N个, <0从尾移除N个

// 获取 RList 对象本身
<V> RList<V> getList(String listName, Class<V> valueClazz);
```

更多关于 RSet, RScoredSortedSet, RLock, RAtomicLong 等数据结构的操作方法，请直接查阅 <mcsymbol name="GXRedissonCacheService" filename="GXRedissonCacheService.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/services/GXRedissonCacheService.java" startline="26" type="class"></mcsymbol> 接口定义及其实现 <mcsymbol name="GXRedissonCacheServiceImpl" filename="GXRedissonCacheServiceImpl.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/services/impl/GXRedissonCacheServiceImpl.java" startline="36" type="class"></mcsymbol>。

### 4.3 使用 Spring Cache 集成

除了直接使用 <mcsymbol name="GXRedissonCacheService" filename="GXRedissonCacheService.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/services/GXRedissonCacheService.java" startline="26" type="class"></mcsymbol>，模块还通过 <mcsymbol name="GXRedissonSpringDataConfig" filename="GXRedissonSpringDataConfig.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/config/GXRedissonSpringDataConfig.java" startline="46" type="class"></mcsymbol> 配置了 Spring `CacheManager`。这意味着你可以方便地在你的 Service 或 Component 中使用 Spring Cache 的注解来进行缓存管理，例如：

```java
@Service
public class MyBusinessService {

    @Cacheable(value = "userCache", key = "#userId")
    public User getUserById(String userId) {
        // 方法体：如果缓存中没有，则执行此方法，并将结果存入缓存
        return userRepository.findById(userId);
    }

    @CachePut(value = "userCache", key = "#user.id")
    public User updateUser(User user) {
        // 方法体：执行此方法，并将结果更新到缓存
        return userRepository.save(user);
    }

    @CacheEvict(value = "userCache", key = "#userId")
    public void deleteUser(String userId) {
        // 方法体：执行此方法，并从缓存中移除
        userRepository.deleteById(userId);
    }
}
```

缓存的名称（如 `userCache`）和过期策略等可以通过 `GXRedissonCacheManagerProperties` 进行配置（详见 2.2.4 节）。

## 5. Redisson 工具类

`leaf-base-redisson` 模块提供了一系列静态工具类，封装了 Redisson 的常用操作，使得在业务代码中调用 Redisson 功能更加便捷。这些工具类通常通过 `GXSpringContextUtils.getBean()` 获取预配置的 `RedissonClient` 或 `RedissonReactiveClient` 实例进行操作。

### 5.1 通用工具：`GXRedissonUtils`

<mcsymbol name="GXRedissonUtils" filename="GXRedissonUtils.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/util/GXRedissonUtils.java" startline="30" type="class"></mcsymbol> 提供了对 Redisson 多种功能的便捷访问，主要使用 `redissonClient`（标准 Redisson 客户端）。

#### 5.1.1 RBucket 操作 (字符串/对象)

这些方法简化了对单个值（如字符串、序列化对象）的存取。

```java
// 设置带过期时间的值
public static void set(String key, Object value, long ttl, TimeUnit timeUnit);

// 设置永久有效的值
public static void set(String key, Object value);

// 获取值
public static <R> R get(String key, Class<R> clazz);

// 删除值
public static boolean delete(String key);

// 批量删除
public static long deleteByPattern(String pattern);

// 尝试设置值 (SETNX)
public static boolean trySet(String key, Object value, long ttl, TimeUnit timeUnit);
```

#### 5.1.2 分布式锁 (RLock)

提供了获取和操作各种 Redisson 分布式锁的方法。

```java
// 获取可重入锁 (Reentrant Lock)
public static RLock getLock(String lockName);

// 获取公平锁 (Fair Lock)
public static RLock getFairLock(String lockName);

// 获取读写锁 (ReadWriteLock)
public static RReadWriteLock getReadWriteLock(String lockName);

// 获取红锁 (RedLock) - 由多个独立的RLock构成
public static RedissonRedLock getRedLock(String... lockNames);

// 获取联锁 (MultiLock) - 由多个RLock构成，所有锁都成功才算成功
public static RedissonMultiLock getMultiLock(String... lockNames);

// 尝试获取锁 (非阻塞或带等待超时)
// lockName: 锁的名称
// waitTime: 获取锁的等待时间，0表示不等待立即返回
// leaseTime: 锁的持有时间（自动释放时间），-1表示不自动释放（需要手动释放，并依赖看门狗机制）
// unit: 时间单位
public static boolean tryLock(String lockName, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

// 异步尝试获取锁
public static RFuture<Boolean> tryLockAsync(String lockName, long waitTime, long leaseTime, TimeUnit unit);

// 锁定 (阻塞直到获取锁)
public static void lock(String lockName, long leaseTime, TimeUnit unit);
public static void lock(String lockName); // 默认leaseTime，依赖看门狗

// 释放锁
public static void unlock(String lockName);
public static void unlock(RLock lock);
```
**注意**：使用分布式锁时，务必在 `finally` 块中调用 `unlock` 方法，确保锁一定会被释放，防止死锁。

#### 5.1.3 分布式计数器 (RAtomicLong)

```java
// 获取原子长整型对象
public static RAtomicLong getAtomicLong(String name);

// 原子增加并获取结果
public static long incrementAndGet(String name);

// 原子减少并获取结果
public static long decrementAndGet(String name);

// 原子增加指定值并获取结果
public static long addAndGet(String name, long delta);
```

#### 5.1.4 限流器 (RRateLimiter)

基于令牌桶算法实现分布式限流。

```java
// 获取限流器实例
public static RRateLimiter getRateLimiter(String name);

// 尝试设置速率 (每秒产生多少令牌)
// rateType: Overall (所有客户端共享) 或 PerClient (每个客户端独立)
// rate: 速率值
// rateInterval: 速率间隔
// rateIntervalUnit: 速率间隔单位
public static boolean trySetRate(String name, RateType rateType, long rate, long rateInterval, RateIntervalUnit rateIntervalUnit);

// 尝试获取1个许可 (非阻塞)
public static boolean tryAcquire(String name);

// 尝试获取指定数量的许可 (非阻塞)
public static boolean tryAcquire(String name, int permits);

// 尝试获取指定数量的许可 (带等待超时)
public static boolean tryAcquire(String name, int permits, long timeout, TimeUnit unit);

// 获取1个许可 (阻塞)
public static void acquire(String name);

// 获取指定数量的许可 (阻塞)
public static void acquire(String name, int permits);
```

### 5.2 延迟队列工具：`GXRedissonQueueUtils`

<mcsymbol name="GXRedissonQueueUtils" filename="GXRedissonQueueUtils.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/util/GXRedissonQueueUtils.java" startline="25" type="class"></mcsymbol> 封装了对 Redisson 延迟队列 (`RDelayedQueue`) 的操作，主要使用 `redissonMQClient`（消息队列专用客户端）。延迟队列可以将元素在指定的延迟时间后，自动从源队列转移到目标队列（通常是一个 `RBlockingQueue`）。

#### 5.2.1 主要方法

```java
// 获取 RBlockingQueue 实例 (延迟消息最终会进入这里)
public static <M> RBlockingQueue<M> getBlockingQueue(String queueName, Codec codec);
public static <M> RBlockingQueue<M> getBlockingQueue(String queueName); // 使用默认JsonJacksonCodec

// 获取 RDelayedQueue 实例 (用于发送延迟消息)
public static <M> RDelayedQueue<M> getDelayedQueue(RBlockingQueue<M> destinationQueue);

// 同步发送延迟消息
// queueName: 目标 RBlockingQueue 的名称
// message: 要发送的消息体
// delay: 延迟时间数值
// timeUnit: 延迟时间单位
public static <M> void sendDelayMessage(String queueName, M message, long delay, TimeUnit timeUnit);
public static <M> void sendDelayMessage(String queueName, M message, Codec codec, long delay, TimeUnit timeUnit);

// 异步发送延迟消息
public static <M> RFuture<Void> sendDelayMessageAsync(String queueName, M message, long delay, TimeUnit timeUnit);
public static <M> RFuture<Void> sendDelayMessageAsync(String queueName, M message, Codec codec, long delay, TimeUnit timeUnit);
```

**使用场景**：订单超时未支付自动取消、定时任务触发等。

### 5.3 可靠消息队列工具：`GXRedissonMQUtils`

<mcsymbol name="GXRedissonMQUtils" filename="GXRedissonMQUtils.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/util/GXRedissonMQUtils.java" startline="25" type="class"></mcsymbol> 提供了对 Redisson 可靠主题 (`RReliableTopic`) 的操作，同样主要使用 `redissonMQClient`。`RReliableTopic` 基于 Redis Stream 实现，保证消息至少被成功处理一次。

#### 5.3.1 主要方法

```java
// 获取 RReliableTopic 实例
public static RReliableTopic getTopic(String topicName);
public static RReliableTopic getTopic(String topicName, Codec codec); // 指定编解码器

// 同步发布消息到主题
// 返回发布的消息数量 (通常为1，如果主题不存在或配置问题可能为0)
public static <M> long publish(String topicName, M message);
public static <M> long publish(String topicName, M message, Codec codec);

// 异步发布消息到主题
public static <M> RFuture<Long> publishAsync(String topicName, M message);
public static <M> RFuture<Long> publishAsync(String topicName, M message, Codec codec);
```

**使用场景**：需要可靠消息传递的业务场景，如分布式事务的最终一致性通知、重要事件广播等。
监听 `RReliableTopic` 的消息通常通过实现 <mcsymbol name="GXRedissonMQListener" filename="GXRedissonMQListener.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/listener/GXRedissonMQListener.java" startline="3" type="class"></mcsymbol> 接口并由 <mcsymbol name="GXRedissonMQListenerServicePostProcessor" filename="GXRedissonMQListenerServicePostProcessor.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonMQListenerServicePostProcessor.java" startline="29" type="class"></mcsymbol> 自动注册，或者使用 <mcsymbol name="GXRedissonDebeziumReliableTopic" filename="GXRedissonDebeziumReliableTopic.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/adapter/GXRedissonDebeziumReliableTopic.java" startline="24" type="class"></mcsymbol> 进行特定格式消息的处理。

## 6. 消息队列监听

`leaf-base-redisson` 提供了强大的消息队列监听和处理机制，支持对 Redisson 可靠主题 (`RReliableTopic`) 和延迟队列 (`RDelayedQueue`) 中的消息进行消费。核心组件包括监听器接口、注解以及后置处理器，简化了消息驱动应用的开发。

### 6.1 可靠主题监听：`GXRedissonMQListener`

<mcsymbol name="GXRedissonMQListener" filename="GXRedissonMQListener.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/listener/GXRedissonMQListener.java" startline="3" type="class"></mcsymbol> 接口用于定义可靠主题 (`RReliableTopic`) 的消息监听器。开发者通过实现此接口来处理特定主题接收到的消息。

当前的 `GXRedissonMQListener` 接口定义如下：
```java
public interface GXRedissonMQListener<T> {
    // 返回需要监听的 ReliableTopic 名称
    String getTopicName();

    // 返回期望接收的消息类型，用于反序列化
    Class<T> getMessageType();

    // 核心消息处理逻辑
    void onMessage(T message);

    // 可选：指定消息的编解码器，默认为 JsonJacksonCodec
    default Codec getCodec() {
        return new JsonJacksonCodec();
    }

    // 可选：指定消费者组名称。如果提供，则启用消费者组模式
    // 多个监听器实例使用相同的组名和主题名，可以实现消息在组内负载均衡消费
    default String getConsumerGroupName() {
        return null; // 默认为独立消费者
    }

    // 可选：指定消费者名称，在消费者组模式下有用，用于标识组内特定消费者
    // 默认为随机UUID
    default String getConsumerName() {
        return UUID.randomUUID().toString();
    }
}
```
*注意：根据您提供的上下文，`GXRedissonMQListener` 接口定义可能与上述不同。上述定义是基于对 `leaf-base-redisson` 模块中常见消息监听器模式的理解。请参考您项目中的实际 `GXRedissonMQListener.java` 文件为准。如果接口仅包含 `void registerRedissonListener();` 方法，则表示监听器的注册和消息处理逻辑需要在此方法内手动完成，而不是通过返回主题名、消息类型等信息由框架自动处理。*

**自动注册**：实现了 <mcsymbol name="GXRedissonMQListener" filename="GXRedissonMQListener.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/listener/GXRedissonMQListener.java" startline="3" type="class"></mcsymbol> 接口并被 Spring 容器管理的 Bean，会被 <mcsymbol name="GXRedissonMQListenerServicePostProcessor" filename="GXRedissonMQListenerServicePostProcessor.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonMQListenerServicePostProcessor.java" startline="29" type="class"></mcsymbol> 自动扫描并注册为对应 `RReliableTopic` 的监听器。该处理器负责启动监听逻辑，并在应用关闭时优雅停止监听。

**使用示例 (基于详细接口定义)**：

```java
@Service
public class MyTopicListener implements GXRedissonMQListener<String> {
    private static final Logger logger = LoggerFactory.getLogger(MyTopicListener.class);

    @Override
    public String getTopicName() {
        return "myAppTopic"; // 监听名为 myAppTopic 的主题
    }

    @Override
    public Class<String> getMessageType() {
        return String.class; // 期望接收字符串类型的消息
    }

    @Override
    public void onMessage(String message) {
        logger.info("Received message from myAppTopic: {}", message);
        // 在这里实现业务处理逻辑
    }
}
```

### 6.2 延迟队列消息转可靠主题：`@GXConvertRedissonDelayQueueToTopic`

<mcsymbol name="GXConvertRedissonDelayQueueToTopic" filename="GXConvertRedissonDelayQueueToTopic.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/annotation/GXConvertRedissonDelayQueueToTopic.java" startline="12" type="class"></mcsymbol> 注解用于将 Redisson 延迟队列 (`RDelayedQueue`) 中的消息在到达预设的延迟时间后，自动从其关联的阻塞队列 (`RBlockingQueue`) 中取出，并转发到指定的可靠主题 (`RReliableTopic`)。

```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXConvertRedissonDelayQueueToTopic {
    // 源延迟队列所关联的 RBlockingQueue 的名称
    String delayQueueName();

    // 目标 RReliableTopic 的名称
    String topicName();

    // 从 RBlockingQueue 中轮询消息的超时时间（秒），默认为 5 秒
    // 如果设置为0或负数，则表示阻塞式获取 (take())
    long timeout() default 5L;

    // 期望从 RBlockingQueue 中取出的消息类型，用于反序列化
    Class<?> messageType() default String.class;

    // 可选：指定源 RBlockingQueue 使用的编解码器，默认为 JsonJacksonCodec
    Class<? extends Codec> codec() default JsonJacksonCodec.class;

    // 可选：指定目标 RReliableTopic 使用的编解码器，默认为 JsonJacksonCodec
    Class<? extends Codec> topicCodec() default JsonJacksonCodec.class;
}
```

**工作机制**：
1. 开发者创建一个实现了 <mcsymbol name="GXRedissonDelayQueueListener" filename="GXRedissonDelayQueueListener.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/listener/GXRedissonDelayQueueListener.java" startline="8" type="class"></mcsymbol> 接口的 Bean，并使用 `@GXConvertRedissonDelayQueueToTopic` 注解配置源队列和目标主题。
2. <mcsymbol name="GXConvertRedissonDelayQueueToTopicPostProcessor" filename="GXConvertRedissonDelayQueueToTopicPostProcessor.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXConvertRedissonDelayQueueToTopicPostProcessor.java" startline="35" type="class"></mcsymbol> 会在 Spring Bean 初始化后，为每个被此注解标记的 Bean 启动一个后台线程。
3. 该后台线程会持续轮询（或阻塞等待）指定的 `RBlockingQueue` (`delayQueueName`)。
4. 一旦从 `RBlockingQueue` 中获取到消息，它会使用 <mcsymbol name="GXRedissonMQUtils" filename="GXRedissonMQUtils.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/util/GXRedissonMQUtils.java" startline="25" type="class"></mcsymbol> 将消息发布到配置的 `RReliableTopic` (`topicName`)。
5. 实现了 <mcsymbol name="GXRedissonDelayQueueListener" filename="GXRedissonDelayQueueListener.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/listener/GXRedissonDelayQueueListener.java" startline="8" type="class"></mcsymbol> 接口的 `onMessage` 方法会在消息成功转发到 Topic **之后**被回调，允许开发者执行一些额外的本地逻辑（例如记录日志、更新状态等），但**主要的消息处理应由订阅了目标 Topic 的监听器完成**。

### 6.3 延迟队列监听回调：`GXRedissonDelayQueueListener`

<mcsymbol name="GXRedissonDelayQueueListener" filename="GXRedissonDelayQueueListener.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/listener/GXRedissonDelayQueueListener.java" startline="8" type="class"></mcsymbol> 接口主要与 `@GXConvertRedissonDelayQueueToTopic` 注解配合使用。

```java
public interface GXRedissonDelayQueueListener<T> {
    /**
     * 当消息从延迟队列成功转发到目标 Topic 后调用此方法。
     * 注意：此方法主要用于通知和辅助处理，核心业务逻辑应由 Topic 的监听器处理。
     *
     * @param message 从 RBlockingQueue 中获取并已转发到 Topic 的消息。
     */
    void onMessage(T message);
}
```

**使用示例**：

```java
@Service
@GXConvertRedissonDelayQueueToTopic(
        delayQueueName = "myDelayedTasksQueue", // 监控这个阻塞队列
        topicName = "processedTasksTopic",     // 消息转发到这个主题
        messageType = TaskData.class,
        timeout = 10 // 轮询超时10秒
)
public class MyDelayedTaskForwarder implements GXRedissonDelayQueueListener<TaskData> {
    private static final Logger logger = LoggerFactory.getLogger(MyDelayedTaskForwarder.class);

    @Override
    public void onMessage(TaskData taskData) {
        // 此处 taskData 已经从 'myDelayedTasksQueue' 取出并发送到了 'processedTasksTopic'
        logger.info("Task {} forwarded from delay queue to topic.", taskData.getId());
        // 可以进行一些轻量级的操作，如记录日志或更新本地状态
        // 复杂的业务处理应由订阅了 'processedTasksTopic' 的 GXRedissonMQListener 完成
    }
}

// 假设 TaskData 类
class TaskData {
    private String id;
    // ...其他属性和方法
    public String getId() { return id; }
}
```

### 6.4 Debezium Redis Stream 消息适配与监听：`GXRedissonDebeziumReliableTopic`

<mcsymbol name="GXRedissonDebeziumReliableTopic" filename="GXRedissonDebeziumReliableTopic.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/adapter/GXRedissonDebeziumReliableTopic.java" startline="24" type="class"></mcsymbol> 是一个特殊的 `RReliableTopic` 适配器，专门用于消费由 Debezium Server 通过 Redis Stream 输出的数据变更事件。

Debezium 是一个开源的分布式平台，用于捕获数据库的变更数据（CDC - Change Data Capture）。当 Debezium Server 配置为使用 Redis Stream 作为 Sink 时，它会将数据库的 `INSERT`, `UPDATE`, `DELETE` 操作以特定格式写入 Redis Stream。

<mcsymbol name="GXRedissonDebeziumReliableTopic" filename="GXRedissonDebeziumReliableTopic.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/adapter/GXRedissonDebeziumReliableTopic.java" startline="24" type="class"></mcsymbol> 继承自 `RedissonReliableTopic`，并重写了消息处理逻辑，以正确解析 Debezium Redis Stream 消息的格式。Debezium 通常会将消息体（payload）进行 Base64 编码，并且键名可能也是 Base64 编码的数字。此适配器会自动处理这些解码操作。

**核心功能**：
- **自动解码**：能够自动检测并解码 Base64 编码的消息键和值。
- **格式转换**：将 Debezium Stream 中的原始消息（通常是 `Map<String, String>` 或 `Map<Object, Object>`）转换为更易于业务处理的格式，通常是 `Map<String, Object>`，其中键为解码后的列名，值为解码后的列值。
- **与 `GXRedissonMQListener` 集成**：可以像普通 `RReliableTopic` 一样，通过实现 <mcsymbol name="GXRedissonMQListener" filename="GXRedissonMQListener.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/listener/GXRedissonMQListener.java" startline="3" type="class"></mcsymbol> 接口来监听和处理这些经过适配和解码的 Debezium 消息。

**如何使用**：
1. 在配置 <mcsymbol name="GXRedissonSpringDataConfig" filename="GXRedissonSpringDataConfig.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/config/GXRedissonSpringDataConfig.java" startline="52" type="class"></mcsymbol> 或自定义 RedissonClient Bean 时，确保 `redissonMQClient` 被正确配置。
2. 创建一个实现了 <mcsymbol name="GXRedissonMQListener" filename="GXRedissonMQListener.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/listener/GXRedissonMQListener.java" startline="3" type="class"></mcsymbol> 接口的 Bean。
3. 在 `getTopicName()` 方法中返回 Debezium Server 输出数据到 Redis Stream 的 Stream 名称。
4. 在 `getCodec()` 方法中，关键是返回一个能够正确处理 Debezium 消息格式的 `Codec`。通常，`GXRedissonDebeziumReliableTopic` 内部会处理大部分解码，但你可能仍需指定一个基础的编解码器，如 `StringCodec` 或 `JsonJacksonCodec`，取决于 Debezium 输出的原始数据结构。
5. **重要的是**，在 <mcsymbol name="GXRedissonMQListenerServicePostProcessor" filename="GXRedissonMQListenerServicePostProcessor.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonMQListenerServicePostProcessor.java" startline="29" type="class"></mcsymbol> 中，当它为 <mcsymbol name="GXRedissonMQListener" filename="GXRedissonMQListener.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/listener/GXRedissonMQListener.java" startline="3" type="class"></mcsymbol> 创建 `RReliableTopic` 实例时，需要确保它创建的是 <mcsymbol name="GXRedissonDebeziumReliableTopic" filename="GXRedissonDebeziumReliableTopic.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/adapter/GXRedissonDebeziumReliableTopic.java" startline="24" type="class"></mcsymbol> 的实例，而不是标准的 `RedissonReliableTopic`。这通常通过在 `GXRedissonMQListenerServicePostProcessor` 中修改获取 Topic 的逻辑，或者提供一种方式让开发者指定使用此适配器。

   经查阅 <mcsymbol name="GXRedissonMQListenerServicePostProcessor" filename="GXRedissonMQListenerServicePostProcessor.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonMQListenerServicePostProcessor.java" startline="29" type="class"></mcsymbol>，它目前是直接通过 `redissonMQClient.getReliableTopic(topicName, codec)` 获取标准 `RReliableTopic`。为了使用 <mcsymbol name="GXRedissonDebeziumReliableTopic" filename="GXRedissonDebeziumReliableTopic.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/adapter/GXRedissonDebeziumReliableTopic.java" startline="24" type="class"></mcsymbol>，开发者需要：
    a. 修改 `GXRedissonMQListenerServicePostProcessor` 使其能够根据监听器的特定标记或类型来实例化 `GXRedissonDebeziumReliableTopic`。
    b. 或者，不依赖自动注册，手动创建 `GXRedissonDebeziumReliableTopic` 实例并添加监听器。

   **推荐方案**：为了保持框架的易用性，可以考虑在 <mcsymbol name="GXRedissonMQListener" filename="GXRedissonMQListener.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/listener/GXRedissonMQListener.java" startline="3" type="class"></mcsymbol> 接口中增加一个方法，例如 `boolean useDebeziumAdapter()`，默认为 `false`。然后在 <mcsymbol name="GXRedissonMQListenerServicePostProcessor" filename="GXRedissonMQListenerServicePostProcessor.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonMQListenerServicePostProcessor.java" startline="29" type="class"></mcsymbol> 中根据此方法的返回值来决定实例化 `GXRedissonDebeziumReliableTopic` 还是 `RedissonReliableTopic`。

**示例 (假设 `GXRedissonMQListenerServicePostProcessor` 已支持适配器选择)**:

```java
@Service
public class DebeziumUserChangesListener implements GXRedissonMQListener<Map<String, Object>> {
    private static final Logger logger = LoggerFactory.getLogger(DebeziumUserChangesListener.class);

    @Override
    public String getTopicName() {
        // Debezium server.name.schema.table (e.g., myserver.public.users)
        return "myserver.public.users"; 
    }

    @Override
    public Class<Map<String, Object>> getMessageType() {
        // GXRedissonDebeziumReliableTopic 会将消息转换为 Map<String, Object>
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    @Override
    public void onMessage(Map<String, Object> message) {
        // message 中的键是列名，值是列的解码后数据
        logger.info("Debezium CDC event for users table: {}", message);
        // 例如: { "id": 1, "name": "John Doe", "email": "john.doe@example.com", "__op": "c", "__source_ts_ms": 1678886400000 }
        // __op: 'c' for create/insert, 'u' for update, 'd' for delete, 'r' for read (snapshot)
        // __source_ts_ms: source timestamp
        // 其他字段为表的列数据
    }

    // 假设 GXRedissonMQListener 增加了此方法，并且 PostProcessor 支持它
    // @Override 
    // public boolean useDebeziumAdapter() { 
    // return true; 
    // }

    @Override
    public Codec getCodec() {
        // GXRedissonDebeziumReliableTopic 内部处理大部分解码，
        // 这里可以指定一个基础的编解码器，例如 StringCodec，因为 Stream 中的原始条目是字符串映射
        return StringCodec.INSTANCE; 
    }
}
```

**当前版本的处理方式**：由于 <mcsymbol name="GXRedissonMQListenerServicePostProcessor" filename="GXRedissonMQListenerServicePostProcessor.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonMQListenerServicePostProcessor.java" startline="29" type="class"></mcsymbol> 尚未直接支持 <mcsymbol name="GXRedissonDebeziumReliableTopic" filename="GXRedissonDebeziumReliableTopic.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/adapter/GXRedissonDebeziumReliableTopic.java" startline="24" type="class"></mcsymbol> 的自动实例化，开发者如果需要使用它，需要手动在自己的配置类或服务中创建 <mcsymbol name="GXRedissonDebeziumReliableTopic" filename="GXRedissonDebeziumReliableTopic.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/adapter/GXRedissonDebeziumReliableTopic.java" startline="24" type="class"></mcsymbol> 实例，并手动为其添加监听器逻辑。例如：

```java
@Configuration
public class DebeziumListenerConfig {

    @Autowired
    private RedissonClient redissonMQClient; // 注入消息队列专用客户端

    @Bean
    public GXRedissonDebeziumReliableTopic<Map<String, Object>> userChangesTopic(
            CommandAsyncExecutor commandExecutor // 从 redissonMQClient 获取
    ) {
        String topicName = "myserver.public.users";
        Codec codec = StringCodec.INSTANCE; // 或者适合 Debezium 输出的原始格式的 Codec
        
        GXRedissonDebeziumReliableTopic<Map<String, Object>> debeziumTopic =
                new GXRedissonDebeziumReliableTopic<>(codec, commandExecutor, topicName);
        
        // 添加监听器
        debeziumTopic.addListener(Map.class, (channel, msg) -> {
            // msg 已经是 GXRedissonDebeziumReliableTopic 处理过的 Map<String, Object>
            System.out.println("Received Debezium message: " + msg);
            // 在这里实现业务逻辑
        });
        
        return debeziumTopic;
    }
}
```
这部分手动配置较为复杂，建议未来版本增强 <mcsymbol name="GXRedissonMQListenerServicePostProcessor" filename="GXRedissonMQListenerServicePostProcessor.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonMQListenerServicePostProcessor.java" startline="29" type="class"></mcsymbol> 以支持此类适配器的自动注册和管理。

## 7. 使用示例

本章节提供 `leaf-base-redisson` 模块各项核心功能的使用示例代码。为使示例更清晰，部分业务类（如 `User`, `Order`, `Product`）会给出简单定义。

```java
// 示例中用到的简单数据对象
class User {
    private Long id;
    private String name;
    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

class Order {
    private Long id;
    private Long userId;
    private String productCode;
    private int quantity;
    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void handleTimeout() { System.out.println("Order " + id + " timed out."); }
}

class Product {
    private String code;
    private String name;
    private double price;
    // getters and setters
}
```

### 7.1 缓存服务使用示例 (`GXRedissonCacheService`)

#### 7.1.1 RBucket 示例 (对象缓存)

```java
@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Resource
    private GXRedissonCacheService redissonCacheService;

    private static final String USER_BUCKET_PREFIX = "user_info";

    public void saveUser(User user) {
        // 模拟业务逻辑：保存用户信息到数据库等
        logger.info("Saving user: {}", user.getName());

        // 将用户信息缓存到Redis，过期时间1小时
        String cacheKey = "id:" + user.getId();
        redissonCacheService.setCache(USER_BUCKET_PREFIX, cacheKey, user, 1, TimeUnit.HOURS);
        logger.info("User {} cached with key {}", user.getName(), USER_BUCKET_PREFIX + ":" + cacheKey);
    }

    public User getUserById(Long userId) {
        String cacheKey = "id:" + userId;
        User user = redissonCacheService.getCache(USER_BUCKET_PREFIX, cacheKey, User.class);

        if (user == null) {
            logger.info("Cache miss for user ID: {}. Fetching from DB.", userId);
            // 模拟从数据库加载
            user = fetchUserFromDatabase(userId);
            if (user != null) {
                // 加载后放入缓存
                redissonCacheService.setCache(USER_BUCKET_PREFIX, cacheKey, user, 1, TimeUnit.HOURS);
                logger.info("User {} fetched from DB and cached.", user.getName());
            }
        } else {
            logger.info("Cache hit for user ID: {}. User: {}", userId, user.getName());
        }
        return user;
    }

    private User fetchUserFromDatabase(Long userId) {
        // 模拟数据库查询
        User dbUser = new User();
        dbUser.setId(userId);
        dbUser.setName("UserFromDB_" + userId);
        return dbUser;
    }
}
```

#### 7.1.2 RMap 示例 (对象属性缓存)

```java
@Service
public class ProductService {
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    @Resource
    private GXRedissonCacheService redissonCacheService;

    private static final String PRODUCT_MAP_CACHE = "product_details_map";

    public void updateProductPrice(String productCode, double newPrice) {
        // 模拟更新数据库中的产品价格
        logger.info("Updating price for product {} to {}", productCode, newPrice);

        // 更新缓存中 RMap 的价格字段，假设 RMap 永不过期，但可以单独为字段设置
        // 注意：RMap 本身的过期是通过 getMap(mapName).expire() 设置的，setMapCache 通常用于单个字段
        redissonCacheService.setMapCache(PRODUCT_MAP_CACHE, productCode + ":price", newPrice, 0, null); 
        logger.info("Product {} price updated in cache map.", productCode);
    }

    public Double getProductPrice(String productCode) {
        Double price = redissonCacheService.getMapCacheValue(PRODUCT_MAP_CACHE, productCode + ":price", Double.class);
        if (price == null) {
            logger.info("Price cache miss for product {}. Fetching from DB.", productCode);
            // 模拟从数据库获取价格
            Product product = fetchProductFromDatabase(productCode);
            if (product != null) {
                price = product.getPrice();
                redissonCacheService.setMapCache(PRODUCT_MAP_CACHE, productCode + ":price", price, 0, null);
                logger.info("Product {} price fetched from DB and cached.", productCode);
            }
        }
        return price;
    }
    private Product fetchProductFromDatabase(String productCode) {
        // 模拟数据库查询
        Product dbProduct = new Product();
        dbProduct.setCode(productCode);
        dbProduct.setName("Sample Product " + productCode);
        dbProduct.setPrice(Math.random() * 100);
        return dbProduct;
    }
}
```

### 7.2 分布式锁使用示例 (`GXRedissonUtils`)

```java
@Service
public class PaymentService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    // 假设这是注入的订单服务，用于实际处理订单
    // @Autowired private ActualOrderProcessor actualOrderProcessor;

    public boolean processPayment(Order order) {
        String lockKey = "payment_lock:user:" + order.getUserId() + ":order:" + order.getId();
        boolean locked = false;
        try {
            // 尝试获取锁，等待5秒，锁自动释放时间30秒
            locked = GXRedissonUtils.tryLock(lockKey, 5, 30, TimeUnit.SECONDS);
            if (locked) {
                logger.info("Lock acquired for payment: {}", lockKey);
                // 获取锁成功，执行支付处理逻辑
                // actualOrderProcessor.process(order);
                logger.info("Payment processed for order: {}", order.getId());
                return true;
            } else {
                logger.warn("Failed to acquire lock for payment: {}. Payment might be in progress.", lockKey);
                return false; // 获取锁失败，可能已有其他线程在处理
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Lock acquisition interrupted for payment: {}", lockKey, e);
            return false;
        } finally {
            if (locked) {
                GXRedissonUtils.unlock(lockKey);
                logger.info("Lock released for payment: {}", lockKey);
            }
        }
    }
}
```

### 7.3 延迟队列与可靠主题示例

#### 7.3.1 发送延迟消息 (`GXRedissonQueueUtils`)

```java
@Service
public class OrderCreationService {
    private static final Logger logger = LoggerFactory.getLogger(OrderCreationService.class);

    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "order_timeout_delay_q";

    public void createOrderAndScheduleTimeout(Order order) {
        // 模拟创建订单逻辑
        logger.info("Creating order: {}", order.getId());

        // 订单创建成功后，发送一个延迟消息，30分钟后检查订单是否支付
        GXRedissonQueueUtils.sendDelayMessage(
            ORDER_TIMEOUT_DELAY_QUEUE, // 延迟队列名称 (实际是 RBlockingQueue 名称)
            order.getId(),             // 消息内容 (订单ID)
            30,                        // 延迟时间
            TimeUnit.MINUTES           // 时间单位
        );
        logger.info("Scheduled timeout check for order {} in {} minutes.", order.getId(), 30);
    }
}
```

#### 7.3.2 延迟队列消息转发到主题 (`@GXConvertRedissonDelayQueueToTopic`)

```java
@Service
@GXConvertRedissonDelayQueueToTopic(
        delayQueueName = OrderCreationService.ORDER_TIMEOUT_DELAY_QUEUE, // 监控此 RBlockingQueue
        topicName = "order_timeout_event_topic",                     // 消息转发到此 RReliableTopic
        messageType = Long.class,                                    // 期望从队列中取出的消息类型
        timeout = 10                                                 // 轮询 RBlockingQueue 超时10秒
)
public class OrderTimeoutDelayQueueForwarder implements GXRedissonDelayQueueListener<Long> {
    private static final Logger logger = LoggerFactory.getLogger(OrderTimeoutDelayQueueForwarder.class);

    @Override
    public void onMessage(Long orderId) {
        // 此方法在消息从 RBlockingQueue 取出并成功转发到 RReliableTopic 后被调用
        logger.info("Order ID {} successfully forwarded from delay queue to topic 'order_timeout_event_topic'.", orderId);
        // 此处可以执行一些轻量级操作，如记录日志。核心处理在 Topic 监听器中。
    }
}
```

#### 7.3.3 可靠主题消息监听 (`GXRedissonMQListener`)

```java
@Service
public class OrderTimeoutTopicProcessor implements GXRedissonMQListener<Long> {
    private static final Logger logger = LoggerFactory.getLogger(OrderTimeoutTopicProcessor.class);

    // @Autowired private OrderService orderService; // 假设用于处理订单超时的服务

    @Override
    public String getTopicName() {
        return "order_timeout_event_topic"; // 监听的主题名称，与转发器配置一致
    }

    @Override
    public Class<Long> getMessageType() {
        return Long.class; // 期望接收的消息类型
    }

    @Override
    public void onMessage(Long orderId) {
        logger.info("Received order timeout event for order ID: {} from topic.", orderId);
        // 在这里处理订单超时的核心业务逻辑
        // orderService.handleOrderTimeout(orderId);
        Order order = new Order(); // 模拟获取订单
        order.setId(orderId);
        order.handleTimeout(); // 调用订单的超时处理方法
    }

    // 可根据需要覆盖 getCodec(), getConsumerGroupName(), getConsumerName()
}
```

### 7.4 限流工具使用示例 (`GXRedissonUtils`)

```java
@RestController
@RequestMapping("/api/products")
public class ProductApiController {
    private static final Logger logger = LoggerFactory.getLogger(ProductApiController.class);

    // 假设这是获取产品列表的服务
    // @Autowired private ProductQueryService productQueryService;

    @GetMapping("/list")
    public ResponseEntity<List<Product>> listProducts() {
        String limiterName = "api_list_products_limiter";
        // 获取限流器：每秒允许10个请求
        RRateLimiter rateLimiter = GXRedissonUtils.getRateLimiter(limiterName);
        rateLimiter.trySetRate(RateType.OVERALL, 10, 1, RateIntervalUnit.SECONDS);

        if (rateLimiter.tryAcquire(1)) { // 尝试获取1个许可
            logger.info("Token acquired for /api/products/list. Proceeding...");
            // 获取许可成功，执行业务逻辑
            // List<Product> products = productQueryService.getAllProducts();
            List<Product> products = Arrays.asList(new Product(), new Product()); // 模拟数据
            return ResponseEntity.ok(products);
        } else {
            logger.warn("Rate limit exceeded for /api/products/list.");
            // 获取许可失败，请求被限流
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(null);
        }
    }
}
```

### 7.5 Debezium Redis Stream 消息处理示例

此示例演示如何手动配置和使用 <mcsymbol name="GXRedissonDebeziumReliableTopic" filename="GXRedissonDebeziumReliableTopic.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/adapter/GXRedissonDebeziumReliableTopic.java" startline="24" type="class"></mcsymbol> 来监听 Debezium 通过 Redis Stream 发送的数据变更事件。这在 <mcsymbol name="GXRedissonMQListenerServicePostProcessor" filename="GXRedissonMQListenerServicePostProcessor.java" path="leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonMQListenerServicePostProcessor.java" startline="29" type="class"></mcsymbol> 未直接支持自动注册此适配器时非常有用。

```java
@Configuration
public class DebeziumEventConsumerConfig {
    private static final Logger logger = LoggerFactory.getLogger(DebeziumEventConsumerConfig.class);

    @Autowired
    private RedissonClient redissonMQClient; // 注入消息队列专用 Redisson 客户端

    @Bean(destroyMethod = "destroy") // 确保监听器在销毁时被移除
    public DebeziumUserEventsConsumer debeziumUserEventsConsumer() {
        // Debezium Stream 名称通常格式为: serverName.schemaName.tableName
        String debeziumStreamName = "myserver.public.users"; 

        // GXRedissonDebeziumReliableTopic 需要 CommandAsyncExecutor
        // 可以从 RedissonClient 实现中获取，例如 RedissonReactive (RedissonClient的父接口)
        // 或者直接使用 RedissonClient 实例，它内部会处理命令执行
        CommandAsyncExecutor commandExecutor = redissonMQClient.getCommandExecutor();

        // 使用 StringCodec 因为 Debezium 通常将 Stream 条目作为字符串映射写入
        // GXRedissonDebeziumReliableTopic 内部会处理 Base64 解码等
        GXRedissonDebeziumReliableTopic<Map<String, Object>> userEventsTopic =
                new GXRedissonDebeziumReliableTopic<>(StringCodec.INSTANCE, commandExecutor, debeziumStreamName);

        DebeziumUserEventsConsumer consumer = new DebeziumUserEventsConsumer(userEventsTopic);
        consumer.startListening();
        return consumer;
    }

    // 独立的消费者类，便于管理生命周期
    public static class DebeziumUserEventsConsumer {
        private final GXRedissonDebeziumReliableTopic<Map<String, Object>> topic;
        private int listenerId;

        public DebeziumUserEventsConsumer(GXRedissonDebeziumReliableTopic<Map<String, Object>> topic) {
            this.topic = topic;
        }

        public void startListening() {
            this.listenerId = topic.addListener(Map.class, (channel, msg) -> {
                // msg 是 GXRedissonDebeziumReliableTopic 解码和转换后的 Map<String, Object>
                logger.info("Received Debezium event from stream '{}': {}", channel, msg);
                
                // 示例：提取操作类型和数据
                String operation = (String) msg.get("__op"); // 'c', 'u', 'd', 'r'
                Long timestamp = (Long) msg.get("__source_ts_ms");
                // Map<String, Object> dataBefore = (Map<String, Object>) msg.get("before"); // for UPDATE/DELETE
                // Map<String, Object> dataAfter = (Map<String, Object>) msg.get("after");   // for CREATE/UPDATE

                if ("c".equals(operation)) {
                    logger.info("User created: {}", msg.get("after"));
                } else if ("u".equals(operation)) {
                    logger.info("User updated. Before: {}, After: {}", msg.get("before"), msg.get("after"));
                } else if ("d".equals(operation)) {
                    logger.info("User deleted: {}", msg.get("before"));
                }
                // 在此实现具体的业务逻辑，如更新缓存、发送通知等
            });
            logger.info("Started listening to Debezium stream: {} with listener ID: {}", topic.getName(), listenerId);
        }

        public void destroy() {
            if (topic != null && listenerId != 0) {
                topic.removeListener(listenerId);
                logger.info("Stopped listening to Debezium stream: {} with listener ID: {}", topic.getName(), listenerId);
            }
        }
    }
}
```

**注意**：上述 Debezium 示例中的 `myserver.public.users` 是一个示例 Stream 名称。你需要将其替换为 Debezium 配置中实际使用的 Stream 名称。同时，确保 `redissonMQClient` 配置正确，可以连接到承载 Debezium Stream 的 Redis 实例。
    @GetMapping("/data")
    public Result getData(String userId) {
        // 对接口进行限流，每个用户每分钟最多请求10次
        String rateLimiterKey = "rate_limiter:getData:" + userId;
        boolean acquired = GXRedissonUtils.tryAcquire(rateLimiterKey, 1, 10);
        
        if (!acquired) {
            // 获取令牌失败，说明请求频率过高
            return Result.error("请求过于频繁，请稍后再试");
        }
        
        // 正常处理业务逻辑
        // ...
        return Result.success(data);
    }
}
```

## 8. 注意事项

1. **配置隔离**：标准操作和消息队列操作使用不同的 RedissonClient 实例，避免相互影响。

2. **异常处理**：所有工具类方法都进行了异常捕获和处理，但在业务代码中仍应进行适当的异常处理。

3. **资源释放**：使用分布式锁时，务必在 finally 块中释放锁，避免死锁。

4. **性能考虑**：
   - 缓存操作应设置合理的过期时间
   - 避免缓存大对象，可能导致序列化和网络传输开销增大
   - 合理使用批量操作方法，减少网络往返次数

5. **序列化**：默认使用 JsonJacksonCodec 作为编解码器，确保对象可以正确序列化。

6. **监控**：生产环境应对 Redis 服务器和连接池状态进行监控，及时发现问题。

## 9. 常见问题

### 9.1 连接超时

**问题**：Redis 连接超时或连接失败

**解决方案**：
- 检查 Redis 服务器地址和端口是否正确
- 检查网络连接是否正常
- 适当增加连接超时时间
- 检查 Redis 服务器是否启用了密码认证

### 9.2 序列化异常

**问题**：存储对象时出现序列化异常

**解决方案**：
- 确保对象实现了 Serializable 接口
- 对于复杂对象，考虑使用自定义序列化器
- 避免存储包含循环引用的对象

### 9.3 分布式锁释放失败

**问题**：分布式锁未正确释放，导致死锁

**解决方案**：
- 始终在 try-finally 块中释放锁
- 设置合理的锁超时时间，避免因程序崩溃导致锁无法释放
- 考虑使用 Redisson 的可重入锁，它支持自动续期

### 9.4 消息丢失

**问题**：延迟队列或主题消息丢失

**解决方案**：
- 使用可靠主题（ReliableTopic）而非普通主题
- 确保消费者异常时不会导致消息确认但未处理
- 考虑实现消息重试机制
- 监控队列长度和消费延迟