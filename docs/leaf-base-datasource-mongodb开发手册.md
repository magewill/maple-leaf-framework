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

配置项说明：

- `uri`: MongoDB 连接 URI，支持标准 MongoDB 连接字符串格式
- `database`: 要连接的数据库名称
- `authenticationDatabase`: 认证数据库，通常为 "admin"
- `gridFsDatabase`: GridFS 数据库名称，用于存储大型文件
- `replicaSetName`: 副本集名称，用于副本集连接
- `autoIndexCreation`: 是否自动创建索引
- `username`: 连接用户名
- `password`: 连接密码
- `beanName`: 注册到 Spring 容器中的 Bean 名称，用于注入时指定
- `primary`: 是否为主数据源，系统中只能有一个主数据源

### 2.3 安全配置

为了提高安全性，建议对敏感信息（如用户名、密码）进行加密处理。模块支持通过 `GXCommonUtils.decodeConnectStr()` 方法自动解密配置中的敏感信息。

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

该类是 MongoDB 多数据源配置的核心类，负责动态注册多个 MongoDB 数据源，支持从本地配置文件或 Nacos 配置中心读取配置信息。

主要功能：

- 实现 BeanDefinitionRegistryPostProcessor 接口，在 Spring 容器启动时动态注册 MongoDB 相关的 Bean
- 支持配置主从数据源，确保系统中有且仅有一个主数据源
- 安全处理凭证信息，避免敏感信息泄露
- 使用原子操作确保线程安全计数
- 对所有外部输入进行严格验证和解码
- 合理管理资源，避免内存泄漏

### 3.2 GXMongoModel

基础模型类，所有 MongoDB 实体类都应该继承此类。

```java
public class GXMongoModel extends GXBaseModel {
}
```

### 3.3 GXMongoDao

MongoDB 数据访问对象接口，继承自 Spring Data MongoDB 的 MongoRepository 接口，提供基础的 CRUD 操作。

```java
public interface GXMongoDao<T extends GXMongoModel, ID extends Serializable> extends MongoRepository<T, ID> {
}
```

### 3.4 GXMongoRepository

MongoDB 仓储层实现类，实现了 GXBaseRepository 接口，提供了丰富的数据操作方法。

主要功能：

- 保存数据（updateOrCreate）：将实体对象保存到 MongoDB 中，支持新增和更新操作
- 条件查询（findByCondition）：根据条件获取所有数据
- 单条记录查询（findOneByCondition）：根据条件获取单条记录
- ID 查询（findOneById）：通过 ID 获取一条记录
- 分页查询（paginate）：根据条件获取分页数据
- 软删除（deleteSoftCondition）：根据条件执行软删除操作
- 物理删除（deleteCondition）：根据条件执行物理删除操作
- 数据验证（validateExists）：检查记录是否存在
- 条件更新（updateFieldByCondition）：根据条件更新字段

## 4. 使用示例

### 4.1 定义实体类

```java
import cn.maple.mongodb.datasource.model.GXMongoModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.annotation.Id;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "user")
public class UserModel extends GXMongoModel {
    @Id
    private String id;
    
    @Field("user_name")
    private String username;
    
    private String email;
    
    private Integer age;
    
    private Boolean active;
}
```

### 4.2 定义 DAO 接口

```java
import cn.maple.mongodb.datasource.dao.GXMongoDao;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDao extends GXMongoDao<UserModel, String> {
    // 可以添加自定义查询方法
    List<UserModel> findByAgeGreaterThan(Integer age);
    
    UserModel findByUsername(String username);
}
```

### 4.3 定义 Repository 实现

```java
import cn.maple.mongodb.datasource.repository.GXMongoRepository;
import org.springframework.stereotype.Component;

@Component
public class UserRepository extends GXMongoRepository<UserModel, UserDao, String> {
    // 可以添加自定义业务方法
    public List<Dict> findActiveUsers() {
        List<GXCondition<?>> conditions = new ArrayList<>();
        conditions.add(new GXConditionEQ("active", true));
        return findByCondition("user", conditions);
    }
}
```

### 4.4 在服务中使用

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    
    // 创建用户
    public String createUser(UserModel user) {
        return userRepository.updateOrCreate(user);
    }
    
    // 根据ID查询用户
    public Dict getUserById(String id) {
        return userRepository.findOneById("user", id);
    }
    
    // 条件查询用户
    public List<Dict> findUsers(List<GXCondition<?>> conditions) {
        return userRepository.findByCondition("user", conditions);
    }
    
    // 更新用户信息
    public Integer updateUser(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> conditions) {
        return userRepository.updateFieldByCondition("user", updateFields, conditions);
    }
    
    // 删除用户
    public Integer deleteUser(List<GXCondition<?>> conditions) {
        return userRepository.deleteCondition("user", conditions);
    }
    
    // 分页查询
    public GXPaginationResDto<Dict> getUsersByPage(Integer page, Integer pageSize, List<GXCondition<?>> conditions) {
        return userRepository.paginate("user", page, pageSize, conditions);
    }
}
```

### 4.5 使用多数据源

如果配置了多个数据源，可以通过指定 Bean 名称来注入不同的 MongoTemplate：

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class MultiDataSourceService {
    @Autowired
    @Qualifier("primaryMongoTemplate")
    private MongoTemplate primaryMongoTemplate;
    
    @Autowired
    @Qualifier("secondaryMongoTemplate")
    private MongoTemplate secondaryMongoTemplate;
    
    // 使用主数据源操作数据
    public void saveWithPrimary(Object document) {
        primaryMongoTemplate.save(document);
    }
    
    // 使用从数据源操作数据
    public void saveWithSecondary(Object document) {
        secondaryMongoTemplate.save(document);
    }
    
    // 使用MongoTemplate进行复杂查询
    public List<UserModel> findUsersByAgeRange(int minAge, int maxAge) {
        Query query = new Query();
        query.addCriteria(Criteria.where("age").gte(minAge).lte(maxAge));
        return primaryMongoTemplate.find(query, UserModel.class);
    }
}
```

## 5. 性能优化

### 5.1 索引优化

在 MongoDB 中，合理使用索引可以显著提高查询性能。可以通过以下方式在实体类中定义索引：

```java
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Document(collection = "user")
@CompoundIndexes({
    @CompoundIndex(name = "email_age_idx", def = "{email: 1, age: -1}")
})
public class UserModel extends GXMongoModel {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String username;
    
    @Indexed
    private String email;
    
    private Integer age;
    
    private Boolean active;
}
```

### 5.2 查询优化

- 使用投影（Projection）减少返回的字段数量

```java
public List<Dict> findUserBasicInfo() {
    List<GXCondition<?>> conditions = new ArrayList<>();
    Set<String> columns = CollUtil.newHashSet("username", "email");
    return findByCondition("user", conditions, columns);
}
```

- 使用分页查询避免一次性返回大量数据

```java
public GXPaginationResDto<Dict> getUsersByPage(Integer page, Integer pageSize) {
    List<GXCondition<?>> conditions = new ArrayList<>();
    return paginate("user", page, pageSize, conditions);
}
```

### 5.3 连接池配置

可以通过自定义 MongoClientSettings 来优化连接池配置：

```java
// 在GXMongoBeanDefinitionRegistryPostProcessor中配置
MongoClientSettings.Builder builder = MongoClientSettings.builder()
    .applyConnectionString(new ConnectionString(uri))
    .applyToConnectionPoolSettings(builder -> {
        builder.maxSize(100);  // 最大连接数
        builder.minSize(10);   // 最小连接数
        builder.maxWaitTime(5000, TimeUnit.MILLISECONDS);  // 最大等待时间
        builder.maxConnectionLifeTime(30, TimeUnit.MINUTES);  // 连接最大生命周期
    });
```

## 6. 注意事项

1. 确保系统中只有一个 MongoDB 数据源的 `primary` 属性设置为 `true`
2. 敏感信息（如用户名、密码）建议使用加密方式存储，模块会自动解密
3. 在使用 Nacos 配置中心时，确保相关配置项正确设置
4. 模块默认禁用了 Spring Boot 的 MongoDB 自动配置，如需使用请调整配置
5. 在进行复杂查询时，可能需要直接使用 MongoTemplate 提供的更丰富的 API
6. 对于大型文档或大量数据的操作，注意内存使用和性能影响
7. 使用 GridFS 存储大文件时，需要配置 `gridFsDatabase` 属性

## 7. 常见问题

### 7.1 连接失败

检查以下几点：

- MongoDB 服务是否正常运行
- 连接 URI 是否正确
- 用户名密码是否正确
- 网络环境是否允许连接
- 防火墙设置是否阻止了连接
- 认证数据库（authenticationDatabase）是否正确

### 7.2 无法注入 MongoTemplate

可能原因：

- Bean 名称不匹配，检查配置中的 `beanName` 属性
- 配置文件路径不正确，检查配置文件位置
- 排除了自动配置但没有正确配置数据源
- Spring 上下文中存在多个同名的 Bean

### 7.3 数据查询返回为空

可能原因：

- 集合名称不匹配，检查实体类上的 @Document 注解
- 查询条件不正确
- 数据库中没有符合条件的数据
- 字段名称与实体类属性映射不一致，检查 @Field 注解

### 7.4 性能问题

可能原因：

- 缺少必要的索引
- 查询条件不够优化
- 返回了过多不必要的字段
- 连接池配置不合理
- 网络延迟较高

## 8. 高级用法

### 8.1 使用 MongoDB 聚合操作

```java
public List<Document> getAgeStatistics() {
    Aggregation aggregation = Aggregation.newAggregation(
        Aggregation.match(Criteria.where("active").is(true)),
        Aggregation.group("age").count().as("count"),
        Aggregation.sort(Sort.Direction.DESC, "count")
    );
    
    AggregationResults<Document> results = primaryMongoTemplate.aggregate(
        aggregation, "user", Document.class);
    
    return results.getMappedResults();
}
```

### 8.2 使用 GridFS 存储大文件

```java
@Service
public class FileService {
    @Autowired
    @Qualifier("primaryMongoTemplate")
    private MongoTemplate mongoTemplate;
    
    private GridFsTemplate getGridFsTemplate() {
        return new GridFsTemplate(
            new SimpleMongoClientDatabaseFactory(mongoTemplate.getDb().getClient(), "gridfs"),
            mongoTemplate.getConverter()
        );
    }
    
    public String storeFile(String filename, InputStream content) {
        GridFsTemplate gridFsTemplate = getGridFsTemplate();
        ObjectId fileId = gridFsTemplate.store(content, filename);
        return fileId.toString();
    }
    
    public GridFSFile getFile(String id) {
        GridFsTemplate gridFsTemplate = getGridFsTemplate();
        return gridFsTemplate.findOne(new Query(Criteria.where("_id").is(new ObjectId(id))));
    }
    
    public void deleteFile(String id) {
        GridFsTemplate gridFsTemplate = getGridFsTemplate();
        gridFsTemplate.delete(new Query(Criteria.where("_id").is(new ObjectId(id))));
    }
}
```

### 8.3 事务支持

对于支持事务的 MongoDB 部署（如副本集），可以使用 Spring 的事务管理：

```java
@Service
public class TransactionalService {
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Transactional
    public void performTransactionalOperation() {
        // 在一个事务中执行多个操作
        mongoTemplate.save(new UserModel("user1", "user1@example.com"));
        mongoTemplate.save(new UserModel("user2", "user2@example.com"));
        // 如果发生异常，所有操作都会回滚
    }
}
```

## 9. 总结

leaf-base-datasource-mongodb 模块提供了丰富的 MongoDB 数据库操作功能，支持多数据源配置和灵活的数据访问方式。通过继承模块提供的基础类和接口，开发人员可以快速集成 MongoDB 到项目中，实现数据的存储、查询和管理。

模块的主要优势包括：

- 简化了 MongoDB 的配置和使用
- 支持多数据源动态配置
- 提供了安全的凭证管理机制
- 封装了常用的数据操作方法
- 与 Spring Boot 和 Spring Data MongoDB 无缝集成

通过合理使用本模块，可以显著提高开发效率，降低 MongoDB 数据库操作的复杂度。