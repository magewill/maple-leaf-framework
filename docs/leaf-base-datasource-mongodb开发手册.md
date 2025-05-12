# leaf-base-datasource-mongodb 模块开发手册

## 1. 模块概述

leaf-base-datasource-mongodb 是 Maple Leaf Framework 框架中用于支持 MongoDB 数据库操作的模块。该模块提供了 MongoDB 多数据源配置、数据访问对象封装以及仓储层实现，使开发人员能够方便地在项目中集成和使用 MongoDB 数据库。

### 1.1 主要功能

- MongoDB 多数据源动态配置与管理
- 支持本地配置文件和 Nacos 配置中心两种配置方式
- 提供基础的数据访问对象（DAO）和仓储层（Repository）封装
- 支持主从数据源配置，确保系统中有且仅有一个主数据源
- 支持安全的凭证管理，避免敏感信息泄露
- 提供线程安全的数据源操作机制

### 1.2 依赖关系

模块依赖以下组件：

```xml
<dependencies>
    <dependency>
        <groupId>cn.maple.framework</groupId>
        <artifactId>leaf-base-framework</artifactId>
        <version>${project.parent.version}</version>
    </dependency>
    <dependency>
        <groupId>cn.maple.framework</groupId>
        <artifactId>leaf-base-nacos</artifactId>
        <version>${project.parent.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
    </dependency>
</dependencies>
```

## 2. 配置说明

### 2.1 基础配置

在使用 leaf-base-datasource-mongodb 模块时，需要先排除 Spring Boot 自动配置的 MongoDB 相关配置，以便使用模块提供的多数据源配置功能。在 application.yml 中添加以下配置：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
      - org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
```

### 2.2 数据源配置

模块支持两种配置方式：本地配置文件和 Nacos 配置中心。

#### 2.2.1 本地配置文件方式

在项目的 `config/${spring.profiles.active}/mongodb.yml` 文件中配置 MongoDB 数据源信息，例如：

```yaml
mongodb:
  datasource:
    primary:  # 数据源名称，可自定义
      uri: mongodb://192.168.56.101:27017  # MongoDB 连接 URI
      database: demo01  # 数据库名称
      authenticationDatabase: "admin"  # 认证数据库
      gridFsDatabase: ""  # GridFS 数据库名称
      replicaSetName: mgset-3590061  # 副本集名称
      autoIndexCreation: false  # 是否自动创建索引
      username: "demo01"  # 用户名
      password: "demo01"  # 密码
      beanName: primaryMongoTemplate  # 注册的 Bean 名称
      primary: true  # 是否为主数据源
    secondary:  # 第二个数据源
      uri: mongodb://192.168.56.101:27017
      database: demo01
      authenticationDatabase: "admin"
      gridFsDatabase: ""
      replicaSetName: mgset-3590061
      autoIndexCreation: false
      username: "demo01"
      password: "demo01"
      beanName: secondaryMongoTemplate
      primary: false
```

#### 2.2.2 Nacos 配置中心方式

当项目中引入了 Nacos 相关依赖后，模块会自动从 Nacos 配置中心读取 MongoDB 配置信息。配置格式与本地配置文件相同，需要在 Nacos 中创建 `mongodb.yml` 配置文件。

### 2.3 安全配置

为了保护敏感信息，模块提供了凭证信息的安全处理机制：

1. **凭证加密**：可以使用框架提供的加密工具对用户名、密码等敏感信息进行加密，然后在配置文件中使用加密后的值

2. **安全解码**：模块会在运行时自动解码这些加密信息，确保敏感信息不会以明文形式出现在内存中

3. **密码处理**：密码使用字符数组而非字符串存储，使用后立即清除内存，防止密码信息在内存中长时间存在

示例配置（使用加密后的凭证）：

```yaml
mongodb:
  datasource:
    primary:
      username: "ENC(加密后的用户名)"
      password: "ENC(加密后的密码)"
      # 其他配置...
```

## 3. 核心组件

### 3.1 GXMongoBeanDefinitionRegistryPostProcessor

`GXMongoBeanDefinitionRegistryPostProcessor` 是模块的核心组件，负责动态注册 MongoDB 数据源相关的 Bean。

主要功能：

- 从配置中读取 MongoDB 数据源信息
- 动态创建和注册 MongoDB 数据源相关的 Bean
- 确保系统中有且仅有一个主数据源
- 安全处理凭证信息，避免敏感信息泄露

内存安全特性：

- 安全处理凭证信息，避免敏感信息泄露
- 使用原子操作确保线程安全计数
- 对所有外部输入进行严格验证和解码
- 合理管理资源，避免内存泄漏

线程安全特性：

- 使用 AtomicInteger 确保计数操作的原子性
- 避免共享可变状态，确保方法执行的线程安全
- 在 Spring 容器初始化阶段执行，不存在并发访问问题

### 3.2 GXMongoDao

`GXMongoDao` 是数据访问对象的基类，提供了基本的 MongoDB 数据操作功能。

主要功能：

- 提供基本的 CRUD 操作
- 支持复杂查询条件构建
- 支持分页查询
- 支持聚合操作

### 3.3 GXMongoRepository

`GXMongoRepository` 是仓储层的基类，封装了更高级的数据操作功能。

主要功能：

- 提供更高级的数据操作接口
- 支持条件查询和分页
- 支持数据验证和错误处理
- 支持实体对象的更新或创建

### 3.4 GXMongoModel

`GXMongoModel` 是实体类的基类，定义了 MongoDB 文档的基本结构。

主要功能：

- 提供基本的文档属性
- 支持 ID 生成和管理
- 支持文档序列化和反序列化

## 4. 使用示例

### 4.1 定义实体类

```java
import cn.maple.mongodb.datasource.model.GXMongoModel;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "user")
public class User extends GXMongoModel {
    @Id
    private String id;
    
    @Field("user_name")
    private String userName;
    
    @Field("age")
    private Integer age;
    
    // getter 和 setter 方法
}
```

### 4.2 定义 DAO 接口

```java
import cn.maple.mongodb.datasource.dao.GXMongoDao;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserDao extends GXMongoDao<User, String> {
    // 自定义查询方法
    @Query("{userName: ?0}")
    List<User> findByUserName(String userName);
    
    // 更多自定义方法...
}
```

### 4.3 定义 Repository 实现

```java
import cn.maple.mongodb.datasource.repository.GXMongoRepository;
import org.springframework.stereotype.Component;

@Component
public class UserRepository extends GXMongoRepository<User, UserDao, String> {
    // 可以添加特定的业务逻辑方法
    public List<User> findUsersByAgeRange(int minAge, int maxAge) {
        return baseDao.find(
            Query.query(Criteria.where("age").gte(minAge).lte(maxAge)),
            User.class
        );
    }
}
```

### 4.4 在 Service 中使用

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    
    public User saveUser(User user) {
        String id = userRepository.updateOrCreate(user);
        return userRepository.baseDao.findById(id).orElse(null);
    }
    
    public List<User> findUsersByName(String userName) {
        return userRepository.baseDao.findByUserName(userName);
    }
    
    public Page<User> findUsersByPage(int page, int size) {
        return userRepository.baseDao.findAll(PageRequest.of(page, size));
    }
}
```

### 4.5 多数据源配置示例

```java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class MultiDataSourceService {
    private final MongoTemplate primaryMongoTemplate;
    private final MongoTemplate secondaryMongoTemplate;
    
    public MultiDataSourceService(
            @Qualifier("primaryMongoTemplate") MongoTemplate primaryMongoTemplate,
            @Qualifier("secondaryMongoTemplate") MongoTemplate secondaryMongoTemplate) {
        this.primaryMongoTemplate = primaryMongoTemplate;
        this.secondaryMongoTemplate = secondaryMongoTemplate;
    }
    
    public void saveToMultipleDataSources(User user) {
        // 保存到主数据源
        primaryMongoTemplate.save(user);
        
        // 保存到从数据源
        secondaryMongoTemplate.save(user);
    }
}
```

## 5. 性能优化

### 5.1 索引优化

在 MongoDB 中，合理的索引设计对查询性能至关重要：

1. **创建适当的索引**：在实体类中使用 `@Indexed` 注解创建索引

```java
import org.springframework.data.mongodb.core.index.Indexed;

@Document(collection = "user")
public class User extends GXMongoModel {
    @Id
    private String id;
    
    @Indexed  // 创建单字段索引
    private String userName;
    
    @Indexed(background = true)  // 后台创建索引，不阻塞其他操作
    private Integer age;
    
    // 其他字段...
}
```

2. **复合索引**：对于经常一起查询的多个字段，创建复合索引

```java
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document(collection = "user")
@CompoundIndex(name = "user_age_idx", def = "{userName: 1, age: 1}")
public class User extends GXMongoModel {
    // 字段定义...
}
```

### 5.2 查询优化

1. **投影查询**：只返回需要的字段，减少数据传输量

```java
Query query = new Query(Criteria.where("age").gt(20));
query.fields().include("userName").include("age");
List<User> users = mongoTemplate.find(query, User.class);
```

2. **分页查询**：避免一次性返回大量数据

```java
Query query = new Query();
PageRequest pageRequest = PageRequest.of(0, 10);
query.with(pageRequest);
List<User> users = mongoTemplate.find(query, User.class);
```

### 5.3 连接池配置

在生产环境中，合理配置 MongoDB 连接池可以提高性能：

```yaml
mongodb:
  datasource:
    primary:
      # 基本配置...
      options:
        minConnectionsPerHost: 10  # 每个主机的最小连接数
        maxConnectionsPerHost: 100  # 每个主机的最大连接数
        threadsAllowedToBlockForConnectionMultiplier: 5  # 线程阻塞倍数
        serverSelectionTimeout: 30000  # 服务器选择超时时间（毫秒）
        maxWaitTime: 120000  # 最大等待时间（毫秒）
        maxConnectionIdleTime: 0  # 最大连接空闲时间
        maxConnectionLifeTime: 0  # 最大连接生命周期
        connectTimeout: 10000  # 连接超时时间（毫秒）
        socketTimeout: 0  # 套接字超时时间（毫秒）
```

## 6. 常见问题与解决方案

### 6.1 连接问题

**问题**：无法连接到 MongoDB 服务器

**解决方案**：
- 检查 MongoDB 服务是否正常运行
- 验证连接 URI 是否正确
- 确认网络连接是否通畅（防火墙设置）
- 检查用户名和密码是否正确

### 6.2 认证问题

**问题**：认证失败

**解决方案**：
- 确认用户名和密码是否正确
- 检查认证数据库（authenticationDatabase）是否正确设置
- 验证用户是否有权限访问目标数据库

### 6.3 性能问题

**问题**：查询性能较差

**解决方案**：
- 检查是否创建了适当的索引
- 优化查询条件，避免全表扫描
- 使用投影查询，只返回需要的字段
- 调整连接池配置，增加可用连接数

### 6.4 调试技巧

在开发过程中，可以通过以下方式进行调试：

1. **启用 MongoDB 日志**：在 application.yml 中配置日志级别

```yaml
logging:
  level:
    org.springframework.data.mongodb: DEBUG
```

2. **查看执行的查询**：通过日志查看实际执行的查询语句

3. **使用 MongoDB Compass**：使用官方的 MongoDB Compass 工具连接数据库，查看集合结构和索引情况

## 7. 高级用法

### 7.1 聚合操作

```java
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

// 按年龄分组统计用户数量
Aggregation aggregation = newAggregation(
    match(Criteria.where("age").gt(20)),
    group("age").count().as("count"),
    project("count").and("age").previousOperation(),
    sort(Sort.Direction.DESC, "count")
);

AggregationResults<Document> results = mongoTemplate.aggregate(
    aggregation, "user", Document.class
);

List<Document> mappedResults = results.getMappedResults();
```

### 7.2 GridFS 文件存储

```java
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

@Service
public class FileService {
    @Autowired
    private GridFsTemplate gridFsTemplate;
    
    public String storeFile(InputStream content, String filename, String contentType) {
        // 存储文件并返回文件ID
        ObjectId id = gridFsTemplate.store(
            content, filename, contentType, 
            new Document("metadata", "custom file metadata")
        );
        return id.toString();
    }
    
    public GridFSFile retrieveFile(String id) {
        // 根据ID检索文件
        return gridFsTemplate.findOne(
            new Query(Criteria.where("_id").is(new ObjectId(id)))
        );
    }
    
    public void deleteFile(String id) {
        // 删除文件
        gridFsTemplate.delete(new Query(Criteria.where("_id").is(new ObjectId(id))));
    }
}
```

### 7.3 事务支持

在 MongoDB 4.0 及以上版本的副本集环境中，可以使用事务功能：

```java
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class MongoConfig {
    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}

@Service
public class UserTransactionService {
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Transactional
    public void transferPoints(String fromUserId, String toUserId, int points) {
        // 在一个事务中执行多个操作
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(fromUserId)),
            new Update().inc("points", -points),
            User.class
        );
        
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(toUserId)),
            new Update().inc("points", points),
            User.class
        );
    }
}
```

## 8. 总结

leaf-base-datasource-mongodb 模块提供了强大而灵活的 MongoDB 数据库支持，具有以下优势：

1. **简化配置**：通过简单的配置即可集成 MongoDB，无需复杂的手动配置

2. **多数据源支持**：支持配置多个 MongoDB 数据源，满足复杂业务场景需求

3. **安全性**：提供凭证信息的安全处理机制，保护敏感信息

4. **线程安全**：确保在多线程环境下的安全操作

5. **易于使用**：提供简洁的 API，降低开发难度

6. **性能优化**：内置多种性能优化机制，提高系统响应速度

7. **灵活扩展**：支持自定义查询和高级操作，满足各种业务需求

通过本模块，开发人员可以快速集成 MongoDB 数据库，专注于业务逻辑的实现，而无需关注底层的数据库连接和配置细节。