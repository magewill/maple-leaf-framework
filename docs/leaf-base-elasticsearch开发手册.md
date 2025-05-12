# leaf-base-elasticsearch 模块开发手册

## 1. 模块概述

leaf-base-elasticsearch 是 Maple Leaf Framework 框架中用于简化 Elasticsearch 操作的核心模块。该模块基于 Spring Data Elasticsearch 进行了二次封装，提供了更加便捷的 Elasticsearch 数据访问接口，支持多数据源配置、复杂查询条件构建、分页查询等功能。

### 1.1 主要特性

- **多数据源支持**：支持配置多个 Elasticsearch 数据源，并可指定主数据源
- **配置中心集成**：支持从本地配置文件或 Nacos 配置中心读取配置信息
- **统一数据访问接口**：提供标准化的数据访问方法，简化 Elasticsearch 操作
- **复杂查询支持**：支持条件组合、排序、分页等复杂查询操作
- **线程安全**：所有方法都是线程安全的，可以在多线程环境下安全调用
- **内存安全**：严格的参数验证和资源管理，避免内存泄漏和空指针异常

### 1.2 模块依赖

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-elasticsearch</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

## 2. 配置说明

### 2.1 Maven 依赖配置

leaf-base-elasticsearch 模块的 Maven 依赖配置如下：

```xml
<dependencies>
    <dependency>
        <groupId>cn.maple.framework</groupId>
        <artifactId>leaf-base-nacos</artifactId>
        <version>${project.parent.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.data</groupId>
        <artifactId>spring-data-elasticsearch</artifactId>
        <version>${spring.data.elasticsearch.version}</version>
        <exclusions>
            <!-- 排除冲突依赖 -->
            <exclusion>
                <artifactId>HdrHistogram</artifactId>
                <groupId>org.hdrhistogram</groupId>
            </exclusion>
            <exclusion>
                <groupId>com.tdunning</groupId>
                <artifactId>t-digest</artifactId>
            </exclusion>
            <exclusion>
                <groupId>org.eclipse.parsson</groupId>
                <artifactId>parsson</artifactId>
            </exclusion>
            <!--与nacos client发生了冲突 故将其排除掉-->
            <exclusion>
                <groupId>org.apache.httpcomponents</groupId>
                <artifactId>httpclient</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-webflux</artifactId>
    </dependency>
    <!-- ElasticSearch 相关依赖 -->
    <dependency>
        <groupId>jakarta.json</groupId>
        <artifactId>jakarta.json-api</artifactId>
    </dependency>
    <dependency>
        <groupId>com.tdunning</groupId>
        <artifactId>t-digest</artifactId>
        <version>${t-digest.version}</version>
    </dependency>
    <dependency>
        <groupId>org.eclipse.parsson</groupId>
        <artifactId>parsson</artifactId>
        <version>${parsson.version}</version>
    </dependency>
</dependencies>
```

### 2.2 Elasticsearch 配置

#### 2.2.1 本地配置文件

在项目的 `config/{profile}/elasticsearch.yml` 文件中配置 Elasticsearch 连接信息：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration
elasticsearch:
  datasource:
    primary:  # 主数据源名称
      uris:
        - 192.168.7.213:9200  # Elasticsearch 服务器地址
      username:  # 用户名（如果有）
      password:  # 密码（如果有）
      primary: true  # 标记为主数据源
    secondary:  # 从数据源名称
      uris:
        - 192.168.7.213:9200  # Elasticsearch 服务器地址
      username:  # 用户名（如果有）
      password:  # 密码（如果有）
```

#### 2.2.2 Nacos 配置中心

如果项目集成了 Nacos 配置中心，模块会自动从 Nacos 读取配置信息。配置格式与本地配置文件相同，需要在 Nacos 中创建 `elasticsearch.yml` 配置文件。

## 3. 核心组件

### 3.1 配置类

#### 3.1.1 GXElasticsearchBeanDefinitionRegistryPostProcessor

该类负责动态注册多个 Elasticsearch 数据源，支持从本地配置文件或 Nacos 配置中心读取配置信息。实现了 `BeanDefinitionRegistryPostProcessor` 接口，在 Spring 容器启动时动态注册 Elasticsearch 相关的 Bean。

主要功能：
- 动态创建和注册 Elasticsearch 数据源相关的 Bean
- 支持配置主从数据源，确保系统中有且仅有一个主数据源
- 安全处理凭证信息，避免敏感信息泄露

### 3.2 数据访问层

#### 3.2.1 GXElasticsearchDao

基于 Spring Data Elasticsearch 的 `ElasticsearchRepository` 封装的统一基本操作接口，提供了对 Elasticsearch 数据的统一访问方法。

主要方法：
- `findByCondition`：根据条件查询所有满足条件的数据
- `findOneByCondition`：根据指定条件查询一条数据
- `paginate`：分页查询数据

#### 3.2.2 GXElasticsearchRepository

Elasticsearch 仓库实现类，提供对 Elasticsearch 数据的统一访问接口，遵循 DDD 中的仓储模式设计。

主要方法：
- `updateOrCreate`：保存或更新数据
- `findByCondition`：根据条件获取所有数据
- `paginate`：分页查询数据
- `getTableName`：获取表名（索引名）

### 3.3 服务层

#### 3.3.1 GXElasticsearchService

Elasticsearch 服务接口，定义了对 Elasticsearch 数据的业务操作方法。

主要方法：
- `checkRecordIsExists`：检测给定条件的记录是否存在
- `updateFieldByCondition`：根据条件更新字段
- `paginate`：分页查询数据
- `findByCondition`：根据条件查询数据列表

#### 3.3.2 GXElasticsearchServiceImpl

Elasticsearch 服务实现类，实现了 `GXElasticsearchService` 接口，为 Elasticsearch 数据操作提供了一套标准化的方法。

## 4. 模型类

### 4.1 GXElasticsearchModel

所有 Elasticsearch 文档实体类的基类，继承自 `GXBaseModel`。自定义的 Elasticsearch 文档实体类需要继承此类。

### 4.2 GXElasticsearchProperties

Elasticsearch 连接属性类，继承自 Spring Boot 的 `ElasticsearchProperties`，增加了 `primary` 属性用于标识是否为主数据源。

## 5. 常量类

### 5.1 GXEsCriteriaMethodMappingConstant

定义了 SQL 操作符与 Elasticsearch 查询方法的映射关系，用于构建 Elasticsearch 查询条件。

```java
METHOD_MAPPING.put("=", "is");
METHOD_MAPPING.put("in", "in");
METHOD_MAPPING.put(">", "greaterThan");
METHOD_MAPPING.put("<", "lessThan");
METHOD_MAPPING.put(">=", "greaterThanEqual");
METHOD_MAPPING.put("<=", "lessThanEqual");
METHOD_MAPPING.put("!=", "");
METHOD_MAPPING.put("not in", "notIn");
METHOD_MAPPING.put("like", "fuzzy");
METHOD_MAPPING.put("between", "between");
```

## 6. 使用示例

### 6.1 定义文档实体类

```java
import cn.maple.elasticsearch.model.GXElasticsearchModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(indexName = "products")
public class ProductEntity extends GXElasticsearchModel {
    @Id
    private String id;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String name;
    
    @Field(type = FieldType.Keyword)
    private String category;
    
    @Field(type = FieldType.Double)
    private Double price;
    
    @Field(type = FieldType.Integer)
    private Integer stock;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String description;
}
```

### 6.2 定义 DAO 接口

```java
import cn.maple.elasticsearch.dao.GXElasticsearchDao;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductDao extends GXElasticsearchDao<ProductEntity, NativeSearchQuery, NativeSearchQueryBuilder, String> {
}
```

### 6.3 定义 Repository 接口

```java
import cn.maple.elasticsearch.repository.GXElasticsearchRepository;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class ProductRepository extends GXElasticsearchRepository<ProductEntity, ProductDao, NativeSearchQuery, NativeSearchQueryBuilder, String> {
    @Override
    public String getTableName() {
        return "products";
    }
}
```

### 6.4 定义 Service 接口

```java
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.elasticsearch.service.GXElasticsearchService;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

public interface ProductService extends GXElasticsearchService<ProductRepository, ProductEntity, ProductDao, NativeSearchQuery, NativeSearchQueryBuilder, ProductResDto, String> {
}
```

### 6.5 定义 Service 实现类

```java
import cn.maple.elasticsearch.service.impl.GXElasticsearchServiceImpl;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends GXElasticsearchServiceImpl<ProductRepository, ProductEntity, ProductDao, NativeSearchQuery, NativeSearchQueryBuilder, ProductResDto, String> implements ProductService {
}
```

### 6.6 使用示例

```java
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {
    @Autowired
    private ProductService productService;
    
    /**
     * 分页查询产品列表
     */
    @GetMapping("/list")
    public GXPaginationResDto<ProductResDto> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String keyword) {
        // 构建查询条件
        GXBaseQueryParamInnerDto queryParam = new GXBaseQueryParamInnerDto();
        queryParam.setPage(page - 1);  // Elasticsearch 分页从0开始
        queryParam.setPageSize(pageSize);
        
        List<GXCondition<?>> conditions = new ArrayList<>();
        
        // 如果有关键字，添加模糊查询条件
        if (keyword != null && !keyword.isEmpty()) {
            conditions.add(GXCondition.fuzzyCondition("name", keyword));
        }
        
        queryParam.setConditions(conditions);
        
        // 执行分页查询
        return productService.paginate(queryParam);
    }
    
    /**
     * 根据ID查询产品
     */
    @GetMapping("/{id}")
    public ProductResDto getById(@PathVariable String id) {
        GXBaseQueryParamInnerDto queryParam = new GXBaseQueryParamInnerDto();
        List<GXCondition<?>> conditions = new ArrayList<>();
        conditions.add(GXCondition.eq("id", id));
        queryParam.setConditions(conditions);
        
        List<ProductResDto> products = productService.findByCondition(queryParam);
        return products.isEmpty() ? null : products.get(0);
    }
    
    /**
     * 创建产品
     */
    @PostMapping
    public String create(@RequestBody ProductEntity product) {
        return productService.updateOrCreate(product);
    }
    
    /**
     * 更新产品
     */
    @PutMapping("/{id}")
    public void update(@PathVariable String id, @RequestBody Dict updateData) {
        List<GXCondition<?>> conditions = new ArrayList<>();
        conditions.add(GXCondition.eq("id", id));
        
        List<GXUpdateField<?>> updateFields = new ArrayList<>();
        updateData.forEach((key, value) -> {
            updateFields.add(GXUpdateField.field(key, value));
        });
        
        productService.updateFieldByCondition(updateFields, conditions);
    }
    
    /**
     * 删除产品
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        productService.delete(product);
    }
}
```

## 7. 注意事项

1. **配置多数据源**：确保只有一个数据源被标记为主数据源（`primary: true`）
2. **索引名称**：在实体类上使用 `@Document(indexName = "xxx")` 注解指定索引名称
3. **字段映射**：使用 `@Field` 注解定义字段类型和分析器
4. **ID 字段**：使用 `@Id` 注解标记主键字段
5. **查询条件**：使用 `GXCondition` 构建查询条件，支持等于、大于、小于、模糊查询等多种条件
6. **分页查询**：Elasticsearch 的分页从 0 开始，使用 `paginate` 方法时需要注意页码转换

## 8. 常见问题

### 8.1 如何处理复杂查询？

对于复杂查询，可以使用 `GXCondition` 组合多个条件，或者直接使用原生的 Elasticsearch 查询 DSL。

### 8.2 如何优化查询性能？

- 合理设计索引和映射
- 使用过滤器代替查询（filter vs query）
- 避免使用通配符开头的模糊查询
- 使用聚合缓存
- 设置合理的分页大小

### 8.3 如何处理大数据量的索引？

- 使用索引别名和索引滚动更新
- 实现数据分片
- 优化批量操作
- 使用 Scroll API 处理大量数据的遍历

## 9. 参考文档

- [Spring Data Elasticsearch 官方文档](https://docs.spring.io/spring-data/elasticsearch/reference/index.html)
- [Elasticsearch 官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- [Elasticsearch Java API 文档](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html)