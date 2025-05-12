# Leaf-Base-Redisson 模块开发手册

## 1. 模块概述

Leaf-Base-Redisson 模块是 Maple-Leaf-Framework 框架的核心组件之一，基于 Redisson 客户端封装，提供了丰富的 Redis 操作功能。该模块主要包括以下核心功能：

- **分布式缓存**：提供基于 Redis 的分布式缓存实现，支持数据的存取、过期策略等
- **分布式锁**：提供基于 Redis 的分布式锁实现，支持可重入锁、公平锁等多种锁类型
- **延迟队列**：提供基于 Redis 的延迟队列实现，支持消息的延迟处理
- **可靠消息队列**：提供基于 Redis Stream 的可靠消息队列实现，支持消息的发布订阅
- **限流工具**：提供基于 Redis 的限流工具，支持接口访问频率控制

本模块依赖于 `leaf-base-framework` 和 `leaf-base-nacos` 模块，使用 Redisson 3.46.0 版本。

## 2. 配置说明

### 2.1 Redisson 配置类

模块提供了 `GXRedissonSpringDataConfig` 配置类，负责创建和配置 Redisson 客户端实例：

- **标准 Redisson 客户端**：用于常规 Redis 操作
- **消息队列专用客户端**：专用于消息队列操作，与标准客户端隔离
- **Spring 缓存管理器**：集成 Spring Cache 抽象，提供基于 Redis 的缓存实现

### 2.2 配置属性类

模块提供了以下配置属性类：

- **GXRedissonProperties**：标准 Redisson 配置属性
- **GXRedissonMQProperties**：Redisson 消息队列配置属性
- **GXRedissonCacheManagerProperties**：Redisson 缓存管理器配置属性
- **GXRedissonConnectProperties**：Redisson 连接配置属性

这些配置属性类支持从本地配置文件或 Nacos 配置中心加载配置。

## 3. 缓存服务

### 3.1 缓存服务接口

模块提供了 `GXRedissonCacheService` 接口及其实现类 `GXRedissonCacheServiceImpl`，提供了丰富的缓存操作方法：

- **设置缓存**：支持设置带过期时间的缓存和永久缓存
- **获取缓存**：支持获取各种类型的缓存数据
- **删除缓存**：支持删除单个或批量缓存
- **缓存操作**：支持缓存的原子增减、批量操作等

### 3.2 主要方法

```java
// 设置带过期时间的缓存
Object setCache(String bucketName, String key, Object value, int expired, TimeUnit timeUnit);

// 设置永久缓存
Object setCache(String bucketName, String key, Object value);

// 获取缓存
<R> R getCache(String bucketName, String key, Class<R> clazz);

// 删除缓存
boolean deleteCache(String bucketName, String key);

// 批量删除缓存
boolean deleteCaches(String bucketName, Collection<String> keys);

// 缓存原子增加
long incrCache(String bucketName, String key, long delta);

// 缓存原子减少
long decrCache(String bucketName, String key, long delta);
```

## 4. Redisson 工具类

### 4.1 GXRedissonUtils

`GXRedissonUtils` 提供了基于 Redisson 的常用 Redis 操作，包括：

- **数据存取**：支持 Redis 数据的存取和删除
- **分布式计数器**：支持原子计数器操作
- **分布式锁**：支持获取各种类型的分布式锁
- **限流工具**：支持基于令牌桶算法的限流功能

#### 4.1.1 数据存取方法

```java
// 设置数据到Redis
public static Object set(String key, String value, int expire, TimeUnit timeUnit);

// 获取Redis中存储的数据
public static <R> R get(String key, Class<R> clazz);

// 删除Redis中的数据
public static boolean delete(String key);
```

#### 4.1.2 分布式锁方法

```java
// 获取可重入锁
public static RLock getLock(String lockName);

// 获取公平锁
public static RLock getFairLock(String lockName);

// 获取读写锁
public static RReadWriteLock getReadWriteLock(String lockName);

// 尝试获取锁
public static boolean tryLock(String lockName, long waitTime, long leaseTime, TimeUnit unit);

// 释放锁
public static void unlock(String lockName);
```

#### 4.1.3 限流方法

```java
// 尝试获取令牌（限流）
public static boolean tryAcquire(String rateLimiterName, int permits, int rate);

// 尝试获取令牌（带超时时间）
public static boolean tryAcquire(String rateLimiterName, int permits, int rate, long timeout, TimeUnit timeUnit);
```

### 4.2 GXRedissonQueueUtils

`GXRedissonQueueUtils` 提供了基于 Redisson 的延迟队列操作，包括：

- **发送延迟消息**：支持同步和异步发送延迟消息
- **获取延迟队列**：支持获取延迟队列实例

#### 4.2.1 主要方法

```java
// 发送同步延迟消息
public static void sendDelayMessage(String queueName, Object message, int delayTime, TimeUnit timeUnit);

// 发送异步延迟消息
public static void sendDelayMessageAsync(String queueName, Object message, int delayTime, TimeUnit timeUnit);

// 获取延迟队列实例
public static RDelayedQueue<String> getDelayedQueue(String queueName);
```

### 4.3 GXRedissonMQUtils

`GXRedissonMQUtils` 提供了基于 Redisson 的可靠主题（ReliableTopic）操作，包括：

- **发布消息**：支持同步和异步发布消息
- **获取主题实例**：支持获取特定编码的主题实例

#### 4.3.1 主要方法

```java
// 同步发布消息
public static long publish(String topicName, Object message);

// 异步发布消息
public static long publishAsync(String topicName, Object message);

// 获取主题实例
public static RReliableTopic getTopic(String topicName);

// 获取特定编码的主题实例
public static RReliableTopic getTopic(String topicName, Codec codec);
```

## 5. 消息队列监听

### 5.1 消息队列监听接口

模块提供了 `GXRedissonMQListener` 接口，用于监听 Redisson 的可靠主题消息：

```java
public interface GXRedissonMQListener {
    /**
     * 在这个方法这中添加监听redis发布的主题
     * 进行自己的业务逻辑处理
     */
    void registerRedissonListener();
}
```

实现该接口的类会被 `GXRedissonMQListenerServicePostProcessor` 自动检测并注册。

### 5.2 延迟队列转发到主题

模块提供了 `@GXConvertRedissonDelayQueueToTopic` 注解和 `GXRedissonDelayQueueListener` 接口，用于将延迟队列中的消息转发到可靠主题：

```java
@GXConvertRedissonDelayQueueToTopic(delayQueueName = "my_delay_queue", topicName = "my_topic")
public class MyDelayQueueListener implements GXRedissonDelayQueueListener {
    @Override
    public void onMessage(String message) {
        // 处理从延迟队列转发到主题的消息
    }
}
```

`GXConvertRedissonDelayQueueToTopicPostProcessor` 会自动检测并处理标注了该注解的类。

## 6. 使用示例

### 6.1 缓存服务使用示例

```java
@Service
public class UserService {
    @Resource
    private GXRedissonCacheService redissonCacheService;
    
    public void saveUser(User user) {
        // 业务逻辑...
        
        // 将用户信息缓存到Redis，过期时间1小时
        redissonCacheService.setCache("user_bucket", "user:" + user.getId(), user, 3600, TimeUnit.SECONDS);
    }
    
    public User getUser(Long userId) {
        // 从Redis缓存获取用户信息
        User user = redissonCacheService.getCache("user_bucket", "user:" + userId, User.class);
        if (user == null) {
            // 缓存未命中，从数据库加载
            // ...
            
            // 加载后放入缓存
            redissonCacheService.setCache("user_bucket", "user:" + userId, user, 3600, TimeUnit.SECONDS);
        }
        return user;
    }
}
```

### 6.2 分布式锁使用示例

```java
@Service
public class OrderService {
    public boolean createOrder(Order order) {
        // 获取分布式锁，防止同一用户并发下单
        String lockKey = "order_lock:" + order.getUserId();
        
        // 尝试获取锁，最多等待5秒，锁持有10秒自动释放
        boolean locked = GXRedissonUtils.tryLock(lockKey, 5, 10, TimeUnit.SECONDS);
        if (!locked) {
            // 获取锁失败，说明有并发请求，返回失败
            return false;
        }
        
        try {
            // 执行创建订单的业务逻辑
            // ...
            return true;
        } finally {
            // 释放锁
            GXRedissonUtils.unlock(lockKey);
        }
    }
}
```

### 6.3 延迟队列使用示例

```java
@Service
public class OrderTimeoutService {
    // 发送订单超时检查的延迟消息
    public void scheduleOrderTimeoutCheck(Long orderId, int delayMinutes) {
        // 将订单ID发送到延迟队列，延迟指定分钟后处理
        GXRedissonQueueUtils.sendDelayMessage(
            "order_timeout_queue", 
            orderId, 
            delayMinutes, 
            TimeUnit.MINUTES
        );
    }
}

// 实现延迟队列监听器，将延迟队列消息转发到可靠主题
@Component
@GXConvertRedissonDelayQueueToTopic(delayQueueName = "order_timeout_queue", topicName = "order_timeout_topic")
public class OrderTimeoutQueueListener implements GXRedissonDelayQueueListener {
    @Override
    public void onMessage(String message) {
        // 消息已自动转发到order_timeout_topic主题
        // 可以在这里添加额外的处理逻辑
    }
}

// 实现主题监听器，处理订单超时消息
@Component
public class OrderTimeoutTopicListener implements GXRedissonMQListener {
    @Resource
    private RedissonClient redissonMQClient;
    
    @Resource
    private OrderService orderService;
    
    @Override
    public void registerRedissonListener() {
        RReliableTopic topic = redissonMQClient.getReliableTopic("order_timeout_topic");
        topic.addListener(Long.class, (channel, msg) -> {
            // 处理订单超时逻辑
            Long orderId = msg;
            orderService.handleOrderTimeout(orderId);
        });
    }
}
```

### 6.4 限流工具使用示例

```java
@RestController
@RequestMapping("/api")
public class ApiController {
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

## 7. 注意事项

1. **配置隔离**：标准操作和消息队列操作使用不同的 RedissonClient 实例，避免相互影响。

2. **异常处理**：所有工具类方法都进行了异常捕获和处理，但在业务代码中仍应进行适当的异常处理。

3. **资源释放**：使用分布式锁时，务必在 finally 块中释放锁，避免死锁。

4. **性能考虑**：
   - 缓存操作应设置合理的过期时间
   - 避免缓存大对象，可能导致序列化和网络传输开销增大
   - 合理使用批量操作方法，减少网络往返次数

5. **序列化**：默认使用 JsonJacksonCodec 作为编解码器，确保对象可以正确序列化。

6. **监控**：生产环境应对 Redis 服务器和连接池状态进行监控，及时发现问题。

## 8. 常见问题

### 8.1 连接超时

**问题**：Redis 连接超时或连接失败

**解决方案**：
- 检查 Redis 服务器地址和端口是否正确
- 检查网络连接是否正常
- 适当增加连接超时时间
- 检查 Redis 服务器是否启用了密码认证

### 8.2 序列化异常

**问题**：存储对象时出现序列化异常

**解决方案**：
- 确保对象实现了 Serializable 接口
- 对于复杂对象，考虑使用自定义序列化器
- 避免存储包含循环引用的对象

### 8.3 分布式锁释放失败

**问题**：分布式锁未正确释放，导致死锁

**解决方案**：
- 始终在 try-finally 块中释放锁
- 设置合理的锁超时时间，避免因程序崩溃导致锁无法释放
- 考虑使用 Redisson 的可重入锁，它支持自动续期

### 8.4 消息丢失

**问题**：延迟队列或主题消息丢失

**解决方案**：
- 使用可靠主题（ReliableTopic）而非普通主题
- 确保消费者异常时不会导致消息确认但未处理
- 考虑实现消息重试机制
- 监控队列长度和消费延迟