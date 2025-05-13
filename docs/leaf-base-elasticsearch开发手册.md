# leaf-base-elasticsearch 模块开发手册

## 1. 模块概述

`leaf-base-elasticsearch` 是 Maple Leaf Framework 框架中用于简化 Elasticsearch 操作的核心模块。该模块基于 Spring Data Elasticsearch 进行了二次封装，提供了更加便捷的 Elasticsearch 数据访问接口，支持多数据源动态注册与配置、灵活的配置加载方式（本地文件或 Nacos）、统一的 CRUD 操作、复杂的查询条件构建、分页查询以及高亮显示等功能。

### 1.1 主要特性

- **多数据源动态注册**：支持在应用启动时动态注册和配置多个 Elasticsearch 数据源，并允许指定一个主数据源。
- **灵活的配置加载**：支持从本地 `elasticsearch.yml` 文件或 Nacos 配置中心加载 Elasticsearch 连接信息。
- **统一数据访问接口**：提供标准化的 DAO、Repository 和 Service 层接口，封装了常见的 Elasticsearch 操作，如条件查询、分页查询、新增、更新、删除等。
- **复杂查询构建**：通过 `GXCondition` 对象，可以方便地构建各种复杂的查询条件，如等于、大于、小于、IN、模糊查询、范围查询等。
- **分页与排序**：内置分页查询和排序功能，简化了分页逻辑的实现。
- **高亮显示支持**：分页查询结果中自动处理并合并高亮字段。
- **线程安全**：模块核心组件设计为线程安全，可在多线程环境下安全调用。
- **内存安全**：通过严格的参数校验、资源管理和安全的集合操作，确保内存使用的安全性，避免常见的内存泄漏和空指针问题。
- **DDD 风格设计**：数据访问层遵循领域驱动设计（DDD）的仓储模式，服务层提供业务逻辑封装。

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

要使用 `leaf-base-elasticsearch` 模块，需要在项目的 `pom.xml` 文件中添加以下依赖：

```xml
<properties>
    <spring.data.elasticsearch.version>5.4.5</spring.data.elasticsearch.version>
    <t-digest.version>3.3</t-digest.version>
    <parsson.version>1.1.7</parsson.version>
</properties>

<dependencies>
    <!-- Maple Leaf Elasticsearch 基础模块 -->
    <dependency>
        <groupId>cn.maple.framework</groupId>
        <artifactId>leaf-base-elasticsearch</artifactId>
        <version>${project.parent.version}</version>
    </dependency>

    <!-- 如果使用 Nacos 作为配置中心，需要引入此依赖 -->
    <dependency>
        <groupId>cn.maple.framework</groupId>
        <artifactId>leaf-base-nacos</artifactId>
        <version>${project.parent.version}</version>
    </dependency>

    <!-- Spring Data Elasticsearch核心依赖 -->
    <dependency>
        <groupId>org.springframework.data</groupId>
        <artifactId>spring-data-elasticsearch</artifactId>
        <version>${spring.data.elasticsearch.version}</version>
        <exclusions>
            <!-- 排除可能与项目中其他依赖冲突的版本 -->
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
            <!-- 与 Nacos Client 中的 httpclient 版本冲突，故排除 -->
            <exclusion>
                <groupId>org.apache.httpcomponents</groupId>
                <artifactId>httpclient</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

    <!-- Spring WebFlux，Elasticsearch Java Client 可能需要 -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-webflux</artifactId>
    </dependency>

    <!-- Elasticsearch Java Client 依赖 -->
    <dependency>
        <groupId>jakarta.json</groupId>
        <artifactId>jakarta.json-api</artifactId>
    </dependency>
    <dependency>
        <groupId>com.tdunning</groupId>
        <artifactId>t-digest</artifactId>
        <version>${t-digest.version}</version> <!-- 明确指定版本以解决冲突 -->
    </dependency>
    <dependency>
        <groupId>org.eclipse.parsson</groupId>
        <artifactId>parsson</artifactId>
        <version>${parsson.version}</version> <!-- 明确指定版本以解决冲突 -->
    </dependency>
</dependencies>
```
**注意**：请根据您的项目实际情况调整 `${project.parent.version}` 和其他依赖的版本号。

### 2.2 Elasticsearch 配置

`leaf-base-elasticsearch` 模块支持从本地配置文件或 Nacos 配置中心加载 Elasticsearch 数据源配置。配置项遵循 Spring Boot 的标准格式，并进行了一定的扩展。

#### 2.2.1 本地配置文件

在项目的 `config/{profile}/elasticsearch.yml` (例如 `config/dev/elasticsearch.yml`) 文件中配置 Elasticsearch 连接信息。模块会自动加载此文件。

```yaml
spring:
  autoconfigure:
    exclude:
      # 排除 Spring Boot 默认的 ElasticsearchDataAutoConfiguration，因为本模块提供了自定义的多数据源配置
      - org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration

elasticsearch:
  datasource:
    # 数据源名称，可以自定义，例如 primary, secondary, log_es 等
    # 每个数据源名称对应一个 ElasticsearchTemplate Bean，命名规则为: {dataSourceName}ElasticsearchTemplate
    primary_cluster: # 第一个数据源，命名为 primary_cluster
      uris:
        - "192.168.1.100:9200" # Elasticsearch 服务器地址列表，可以配置多个
        - "192.168.1.101:9200"
      username: "elastic"            # 用户名 (可选)
      password: "your_password"      # 密码 (可选)
      primary: true                # 标记此数据源为主数据源。必须有且仅有一个主数据源。
                                   # 主数据源会自动注册一个名为 "elasticsearchTemplate" 的别名
      connectionTimeout: 5s        # 连接超时时间 (可选, 默认5s)
      socketTimeout: 30s           # Socket 超时时间 (可选, 默认30s)
      # 其他 Spring Boot ElasticsearchProperties 支持的属性也可以在这里配置
      # 例如：pathPrefix, headers, ssl, etc.

    another_cluster: # 第二个数据源，命名为 another_cluster
      uris:
        - "192.168.2.200:9200"
      username: "user2"
      password: "pass2"
      primary: false # 非主数据源
      # ... 其他配置
```

**关键配置说明**：

- `spring.autoconfigure.exclude`: 必须排除 `ElasticsearchDataAutoConfiguration` 以启用本模块的多数据源管理功能。
- `elasticsearch.datasource`: 这是所有 Elasticsearch 数据源配置的根节点。
- `{dataSourceName}`: 您可以为每个数据源定义一个唯一的名称（如 `primary_cluster`, `secondary_es`）。这个名称将用于生成对应的 `ElasticsearchTemplate` Bean 的名称（例如 `primary_clusterElasticsearchTemplate`）。
- `uris`: Elasticsearch 节点的 URI 列表。
- `username`, `password`: 连接 Elasticsearch 的凭证。
- `primary`:布尔值，标记是否为主数据源。项目中**必须有且仅有一个**数据源被标记为 `primary: true`。主数据源的 `ElasticsearchTemplate` 会额外注册一个名为 `elasticsearchTemplate` 的别名，方便直接注入使用。
- 其他如 `connectionTimeout`, `socketTimeout` 等均可在此配置，它们会传递给底层的 `ClientConfiguration`。

#### 2.2.2 Nacos 配置中心

如果您的项目集成了 Nacos (`leaf-base-nacos` 模块)，`leaf-base-elasticsearch` 会优先尝试从 Nacos 配置中心加载 Elasticsearch 配置。

您需要在 Nacos 中创建一个名为 `elasticsearch.yml` 的配置文件，其内容格式与本地配置文件完全相同。该配置文件应放置在应用配置对应的 Group 和 Namespace 下。

**Nacos 配置加载优先级**：

- 如果检测到 Nacos Client (`com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties`) 存在于 classpath 中，并且成功连接到 Nacos 服务，则会优先使用 Nacos 中的 `elasticsearch.yml` 配置。
- 如果 Nacos Client 不存在或无法从 Nacos 获取配置，则会回退到加载本地的 `config/{profile}/elasticsearch.yml` 文件。

模块通过 `GXLocalElasticsearchProperties` 和 `GXNacosElasticsearchProperties` 两个类分别处理本地和 Nacos 的配置加载，通过 Spring 的 `@ConditionalOnClass` 和 `@ConditionalOnMissingClass` 注解实现加载逻辑的切换。

## 3. 核心组件说明

`leaf-base-elasticsearch` 模块的核心组件协同工作，为开发者提供了强大而易用的 Elasticsearch 操作能力。

### 3.1 配置加载与数据源管理

- **`GXElasticsearchProperties`**: 继承自 Spring Data Elasticsearch 的 `org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties`，并添加了 `primary` (布尔型) 属性。此属性用于在多数据源场景下标识哪个数据源是主数据源。主数据源的 `ElasticsearchRestTemplate` Bean 会额外注册一个名为 `elasticsearchTemplate` 的别名。

- **`GXElasticsearchSourceProperties`**: 这是一个抽象类，定义了获取 Elasticsearch 数据源配置的统一接口 `getDatasource()`，该方法返回一个 `Map<String, GXElasticsearchProperties>`，其中键是数据源的名称，值是对应数据源的配置属性。
    - **`GXLocalElasticsearchProperties`**: `GXElasticsearchSourceProperties` 的实现类，负责从本地 `elasticsearch.yml` 文件加载数据源配置。它使用 `@ConfigurationProperties("elasticsearch")` 注解绑定 `elasticsearch.datasource` 下的配置。
    - **`GXNacosElasticsearchProperties`**: `GXElasticsearchSourceProperties` 的另一个实现类，负责从 Nacos 配置中心加载数据源配置。它使用 `@NacosConfigurationProperties(dataId = "elasticsearch.yml", groupId = "${nacos.config.group:DEFAULT_GROUP}", autoRefreshed = true, type = ConfigType.YAML)` 注解，从 Nacos 的 `elasticsearch.yml` 文件中读取 `elasticsearch.datasource` 下的配置，并支持动态刷新。

- **`GXElasticsearchBeanDefinitionRegistryPostProcessor`**: 实现 Spring 的 `BeanDefinitionRegistryPostProcessor` 接口。这是模块实现多数据源动态注册的核心。它在 Spring 容器初始化过程中：
    1. 根据 classpath 中是否存在 Nacos Client 相关类，决定使用 `GXLocalElasticsearchProperties` 还是 `GXNacosElasticsearchProperties` 来获取数据源配置。
    2. 遍历获取到的所有数据源配置。
    3. 为每一个数据源动态创建一个 `ElasticsearchRestTemplate` Bean。Bean 的名称遵循 `{dataSourceName}ElasticsearchTemplate` 的格式 (例如，名为 `primary_cluster` 的数据源会生成 `primary_clusterElasticsearchTemplate` Bean)。
    4. 如果某个数据源被标记为 `primary: true`，则其对应的 `ElasticsearchRestTemplate` Bean 还会被注册一个别名 `elasticsearchTemplate`。
    5. 确保有且仅有一个主数据源，否则抛出异常。

### 3.2 数据访问层 (DAO & Repository)

- **`GXElasticsearchDao<T extends GXElasticsearchModel, ID extends Serializable>`**: 数据访问对象 (DAO) 的泛型类，封装了对 Elasticsearch 的底层操作。它直接使用 `ElasticsearchRestTemplate` 执行查询、索引、更新和删除等操作。主要方法包括：
    - `save(T entity)`: 保存或更新单个文档。
    - `saveBatch(Collection<T> entities)`: 批量保存或更新文档。
    - `deleteById(ID id, Class<T> clazz)`: 根据 ID 删除文档。
    - `deleteByCondition(GXCondition condition, Class<T> clazz)`: 根据条件删除文档。
    - `findById(ID id, Class<T> clazz)`: 根据 ID 查询文档。
    - `findOne(GXCondition condition, Class<T> clazz)`: 根据条件查询单个文档。
    - `findAll(GXCondition condition, Class<T> clazz)`: 根据条件查询所有文档。
    - `findPage(GXPage<T> page, GXCondition condition, Class<T> clazz)`: 分页条件查询，并处理高亮。
    - `count(GXCondition condition, Class<T> clazz)`: 根据条件统计文档数量。
    该类设计为线程安全，并对输入参数进行校验，以保证操作的健壮性。

- **`GXElasticsearchRepository<T extends GXElasticsearchModel, ID extends Serializable>`**: 仓储接口，继承自 `cn.maple.base.repository.GXBaseRepository<T, ID>`。它定义了标准的 CRUD 和查询方法，是业务代码与数据访问层交互的主要入口。其默认实现类 `cn.maple.elasticsearch.repository.impl.GXElasticsearchRepositoryImpl` 通过依赖注入 `GXElasticsearchDao` 来完成实际的数据操作，遵循了仓储模式的最佳实践。

### 3.3 服务层 (Service)

- **`GXElasticsearchService<T extends GXElasticsearchModel, ID extends Serializable>`**: 服务层接口，继承自 `cn.maple.base.service.GXBaseService<T, ID>`。它定义了更高层次的业务操作，通常直接由 Controller 或其他业务逻辑组件调用。
- **`cn.maple.elasticsearch.service.impl.GXElasticsearchServiceImpl<M extends GXElasticsearchDao<T, ID>, T extends GXElasticsearchModel, ID extends Serializable>`**: `GXElasticsearchService` 的泛型实现类。它依赖于对应的 `GXElasticsearchRepository` (通过泛型 `M` 间接关联到 `GXElasticsearchDao`)，并封装了服务层的通用逻辑，如事务管理（如果适用）、更复杂的查询组合、数据校验和转换等。

### 3.4 数据模型与常量

- **`GXElasticsearchModel`**: 所有需要映射到 Elasticsearch 文档的实体类都应继承此类。它继承自 `cn.maple.base.model.GXBaseModel`，可以包含通用的字段如 `id` 等。建议在此基础上为每个索引定义具体的实体类，并使用 `@Document(indexName = "your_index_name")` 注解指定索引名称，使用 `@Field` 注解标记字段。

- **`GXEsCriteriaMethodMappingConstant`**: 包含一个静态 `Map<String, String> METHOD_MAPPING`。这个映射表定义了 `GXCondition` 中使用的操作符 (如 `EQ` 代表等于, `LIKE` 代表模糊匹配, `GT` 代表大于等) 与 Spring Data Elasticsearch `Criteria` API 中相应方法名之间的映射关系 (如 `EQ` -> `is`, `LIKE` -> `contains`, `GT` -> `greaterThan`)。这使得 `GXElasticsearchDao` 能够动态地将 `GXCondition` 对象转换为 Elasticsearch 的查询语句。
    ```java
    // 部分映射示例
    METHOD_MAPPING.put("=", "is");
    METHOD_MAPPING.put("IN", "in"); // 注意：之前文档为小写 "in"
    METHOD_MAPPING.put(">", "greaterThan");
    METHOD_MAPPING.put("<", "lessThan");
    METHOD_MAPPING.put(">=", "greaterThanEqual");
    METHOD_MAPPING.put("<=", "lessThanEqual");
    METHOD_MAPPING.put("!=", "not"); // 之前为空字符串
    METHOD_MAPPING.put("NOT_IN", "notIn"); // 注意：之前为 "not in"
    METHOD_MAPPING.put("LIKE", "contains"); // 之前为 "fuzzy", Spring Data ES Criteria API 中更常用 contains 或 matches
    METHOD_MAPPING.put("FUZZY", "fuzzy"); // 保留 fuzzy 映射
    METHOD_MAPPING.put("BETWEEN", "between");
    // ... 其他更多映射
    ```

## 4. 使用示例

以下示例将展示如何在项目中使用 `leaf-base-elasticsearch` 模块进行常见的 Elasticsearch 操作。

### 4.1 实体类定义

首先，定义一个需要存储到 Elasticsearch 的实体类，继承自 `GXElasticsearchModel`，并使用相关注解。

```java
package com.example.model;

import cn.maple.elasticsearch.model.GXElasticsearchModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id; // 确保引入正确的 @Id 注解
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(indexName = "products") // 指定索引名称
public class Product extends GXElasticsearchModel {

    @Id // 标记主键字段
    private String id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
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

### 4.2 Repository 定义与使用

创建一个继承自 `GXElasticsearchRepository` 的 Repository 接口。

```java
package com.example.repository;

import cn.maple.elasticsearch.repository.GXElasticsearchRepository;
import com.example.model.Product;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends GXElasticsearchRepository<Product, String> {
    // 可以添加自定义的查询方法，如果需要的话
    // 例如：List<Product> findByCategory(String category);
}
```

在 Service 或 Controller 中注入并使用 `ProductRepository`：

```java
package com.example.service;

import cn.maple.base.model.GXPage;
import cn.maple.base.model.condition.GXCondition;
import cn.maple.base.model.condition.GXLambdaCondition;
import com.example.model.Product;
import com.example.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class ProductSearchService {

    @Autowired
    private ProductRepository productRepository;

    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }

    public Optional<Product> getProductById(String id) {
        return productRepository.findById(id);
    }

    public List<Product> findProductsByName(String name) {
        GXCondition condition = new GXCondition();
        condition.eq("name", name); // 精确匹配 name 字段
        return productRepository.findAll(condition);
    }

    public List<Product> findProductsByDescription(String keyword) {
        // 使用 Lambda 条件
        GXLambdaCondition<Product> lambdaCondition = new GXLambdaCondition<>();
        lambdaCondition.like(Product::getDescription, keyword); // 模糊匹配 description 字段
        return productRepository.findAll(lambdaCondition);
    }

    public GXPage<Product> searchProductsPage(String keyword, int pageNum, int pageSize) {
        GXPage<Product> page = new GXPage<>(pageNum, pageSize);
        // 高亮显示 name 和 description 字段
        page.setHighlightFields(Arrays.asList("name", "description"));

        GXCondition condition = new GXCondition();
        // 构建 OR 查询：name 包含 keyword 或者 description 包含 keyword
        condition.or(
            new GXCondition().like("name", keyword),
            new GXCondition().like("description", keyword)
        );
        // 按价格降序排序
        condition.orderByDesc("price");

        return productRepository.findPage(page, condition);
    }

    public void deleteProductById(String id) {
        productRepository.deleteById(id);
    }
    
    public long countProductsByCategory(String category) {
        GXCondition condition = new GXCondition();
        condition.eq("category", category);
        return productRepository.count(condition);
    }
}
```

### 4.3 Service 定义与使用 (可选, 针对更复杂业务)

如果业务逻辑非常复杂，或者需要组合多个 Repository 操作，可以创建继承自 `GXElasticsearchService` 的 Service 接口及其实现。

```java
package com.example.service;

import cn.maple.elasticsearch.service.GXElasticsearchService;
import com.example.model.Product;

public interface ProductManagementService extends GXElasticsearchService<Product, String> {
    // 自定义服务层方法，例如：
    // void complexBusinessOperation(String productId, UpdateDto updateDto);
    long countActiveProductsByCategory(String category);
}
```

```java
package com.example.service.impl;

import cn.maple.base.model.condition.GXCondition;
import cn.maple.elasticsearch.service.impl.GXElasticsearchServiceImpl;
import com.example.model.Product;
import com.example.repository.ProductRepository; // 注入具体的 Repository
import com.example.service.ProductManagementService;
import org.springframework.stereotype.Service;

@Service("productManagementService")
public class ProductManagementServiceImpl extends GXElasticsearchServiceImpl<ProductRepository, Product, String> implements ProductManagementService {

    // 构造函数注入或 @Autowired 注入 Repository
    // public ProductManagementServiceImpl(ProductRepository productRepository) {
    //     this.repository = productRepository; // GXElasticsearchServiceImpl 中有 repository 字段
    // }

    @Override
    public long countActiveProductsByCategory(String category) {
        GXCondition condition = new GXCondition();
        condition.eq("category", category);
        // 假设 Product 有一个 boolean 类型的 active 字段
        // condition.eq("active", true); 
        return this.repository.count(condition);
    }
}
```

### 4.4 直接使用 ElasticsearchRestTemplate (多数据源场景)

如果配置了多个数据源，可以通过 `@Qualifier` 注解注入指定数据源的 `ElasticsearchRestTemplate`。

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;
// 假设有另一个实体类 OtherEntity 和索引 other_index
// import com.example.model.OtherEntity;

@Component
public class MultiEsHandler {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate; // 注入主数据源 (默认别名)

    @Autowired
    @Qualifier("another_clusterElasticsearchTemplate") // 假设配置文件中定义了名为 another_cluster 的数据源
    private ElasticsearchRestTemplate secondaryEsTemplate;

    public void queryFromPrimaryProductIndex() {
        Query query = new NativeSearchQueryBuilder().withQuery(org.elasticsearch.index.query.QueryBuilders.matchAllQuery()).build();
        SearchHits<Product> searchHits = elasticsearchRestTemplate.search(query, Product.class, IndexCoordinates.of("products"));
        System.out.println("Products from primary: " + searchHits.getTotalHits());
    }

    public void queryFromSecondaryOtherIndex() {
        // Query query = new NativeSearchQueryBuilder().withQuery(org.elasticsearch.index.query.QueryBuilders.matchAllQuery()).build();
        // SearchHits<OtherEntity> searchHits = secondaryEsTemplate.search(query, OtherEntity.class, IndexCoordinates.of("other_index"));
        // System.out.println("OtherEntities from secondary: " + searchHits.getTotalHits());
    }
}
```

### 4.5 构建复杂查询条件 (`GXCondition` & `GXLambdaCondition`)

`GXCondition` 和 `GXLambdaCondition` 提供了流式 API 来构建复杂的查询条件。

```java
import cn.maple.base.model.condition.GXCondition;
import cn.maple.base.model.condition.GXLambdaCondition;
import com.example.model.Product;
import com.example.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component
class ComplexQueryExamples {

    @Autowired
    private ProductRepository productRepository;

    public void runQueries() {
        // 示例1: 价格在 100 到 200 (包含边界) 之间，并且分类为 "electronics"
        GXCondition condition1 = new GXCondition();
        condition1.between("price", 100.0, 200.0);
        condition1.eq("category", "electronics");
        List<Product> results1 = productRepository.findAll(condition1);
        System.out.println("Condition 1 results: " + results1.size());

        // 示例2: 使用 Lambda 条件，名称包含 "Pro" (模糊) 并且价格大于 500
        GXLambdaCondition<Product> condition2 = new GXLambdaCondition<>();
        condition2.like(Product::getName, "Pro").gt(Product::getPrice, 500.0);
        List<Product> results2 = productRepository.findAll(condition2);
        System.out.println("Condition 2 results: " + results2.size());

        // 示例3: OR 条件 - 分类为 "books" 或价格小于 50
        GXCondition condition3 = new GXCondition();
        condition3.or(
            new GXCondition().eq("category", "books"),
            new GXCondition().lt("price", 50.0)
        );
        List<Product> results3 = productRepository.findAll(condition3);
        System.out.println("Condition 3 results: " + results3.size());

        // 示例4: IN 条件 - ID 在指定列表中
        GXCondition condition4 = new GXCondition();
        condition4.in("id", Arrays.asList("prod_123", "prod_456", "prod_789"));
        List<Product> results4 = productRepository.findAll(condition4);
        System.out.println("Condition 4 results: " + results4.size());

        // 示例5: 排序 - 按价格降序，再按名称升序 (如果价格相同)
        GXCondition condition5 = new GXCondition();
        condition5.eq("category", "appliances"); // 假设先筛选一个分类
        condition5.orderByDesc("price").orderByAsc("name");
        List<Product> results5 = productRepository.findAll(condition5);
        System.out.println("Condition 5 results: " + results5.size());
        
        // 示例6: NOT IN 条件
        GXCondition condition6 = new GXCondition();
        condition6.notIn("category", Arrays.asList("toys", "games"));
        List<Product> results6 = productRepository.findAll(condition6);
        System.out.println("Condition 6 results (not in toys or games): " + results6.size());

        // 示例7: FUZZY 查询 (更底层的模糊查询，可能需要特定字段类型支持)
        GXCondition condition7 = new GXCondition();
        condition7.fuzzy("description", "technologies"); // 查找与 'technologies'相似的词
        List<Product> results7 = productRepository.findAll(condition7);
        System.out.println("Condition 7 results (fuzzy on description): " + results7.size());
    }
}
```

这些示例覆盖了 `leaf-base-elasticsearch` 模块的主要用法，开发者可以根据实际需求灵活组合使用。

## 5. 注意事项

在使用 `leaf-base-elasticsearch` 模块时，请注意以下几点：

- **版本兼容性**: 确保项目中使用的 Spring Boot 版本、Spring Data Elasticsearch 版本与 `leaf-base-elasticsearch` 模块及其依赖（如 Elasticsearch Java Client）兼容。版本不匹配可能导致启动失败或运行时错误。
- **主数据源配置**: 在多数据源场景下，必须有且仅有一个数据源被标记为 `primary: true`。如果缺少主数据源或配置了多个主数据源，`GXElasticsearchBeanDefinitionRegistryPostProcessor` 会在启动时抛出异常。
- **Nacos 配置**: 
    - 使用 Nacos 作为配置中心时，确保 Nacos Server 可访问，并且 `elasticsearch.yml` (或自定义的 dataId) 已正确配置在指定的 `groupId` 下。
    - 模块会根据 `com.alibaba.cloud.nacos.NacosConfigManager` 类是否存在于 classpath 来自动判断是否启用 Nacos 配置。如果期望使用 Nacos 但相关依赖缺失，模块将回退到本地配置加载。
- **索引生命周期管理 (ILM)**: 对于生产环境，强烈建议结合 Elasticsearch 的索引生命周期管理 (ILM) 策略来管理索引的创建、翻转、归档和删除，以优化存储和查询性能。
- **字段映射 (Mapping)**: 
    - 首次向 Elasticsearch 写入数据时，如果索引不存在，Elasticsearch 会尝试根据第一条数据自动创建映射。这种动态映射可能不总是最优的。建议显式定义实体类的 `@Field` 注解，或者通过 Elasticsearch API 预先创建索引并定义好映射，特别是对于需要特定分词器、数据类型或索引选项的字段。
    - 一旦字段的映射被创建，通常不能修改其核心类型（例如，从 `text` 改为 `keyword`）。如果需要更改，通常需要重建索引并迁移数据。
- **查询性能**: 
    - 避免在 `text` 类型的字段上进行精确匹配、排序或聚合操作，除非该字段也配置了 `keyword` 类型的子字段。`text` 字段主要用于全文搜索。
    - 复杂的布尔查询、嵌套查询或脚本查询可能会影响性能。尽量简化查询逻辑，并利用 Elasticsearch 的查询缓存。
    - 对于大结果集的分页，避免使用深分页 (from + size)，考虑使用 `search_after` 来提高效率。
- **高亮处理**: `GXPage` 对象中的 `highlightFields` 属性用于指定需要高亮的字段。确保这些字段在 Elasticsearch 中是可高亮的（通常是 `text` 类型字段）。高亮结果会包含在返回的实体对象中（如果实体类中有对应 `Map<String, List<String>> highlight` 字段）或 `GXPage` 的 `highlightMap` 中。
- **实体类设计**: 
    - 实体类中的 `@Id` 注解应使用 `org.springframework.data.annotation.Id`。
    - 确保实体类有无参构造函数，以便 Spring Data Elasticsearch 进行实例化。
- **异常处理**: 模块中的 DAO 和 Repository 层会捕获并处理一些常见的 Elasticsearch 操作异常，但上层业务代码仍需妥善处理可能发生的异常，例如网络问题、查询语法错误等。
- **线程安全**: `ElasticsearchRestTemplate` 是线程安全的。`GXElasticsearchDao` 和 `GXElasticsearchRepositoryImpl` 的设计也是线程安全的，可以在多线程环境中共享使用。
- **资源管理**: 虽然 `ElasticsearchRestTemplate` 会管理其底层的连接池，但在应用关闭时，Spring Boot 会负责优雅地关闭相关的 Elasticsearch客户端资源。
- **日志**: 模块使用 SLF4J 进行日志记录。可以通过调整项目的日志配置（如 Logback, Log4j2）来控制 `cn.maple.elasticsearch` 包下的日志级别，以便于调试和监控。

## 6. 常见问题与故障排查

### 6.1 连接 Elasticsearch 失败

- **症状**: 应用启动时报错，提示无法连接到 Elasticsearch 集群，如 `Connection refused`、`NoNodeAvailableException` 等。
- **排查步骤**:
    1. **检查 Elasticsearch 服务状态**: 确保 Elasticsearch 集群正在运行并且健康。可以通过浏览器访问 `http://<your_es_host>:9200` 或使用 `curl` 命令查看集群状态。
    2. **检查配置文件**: 
        - **本地配置 (`elasticsearch.yml`)**: 验证 `spring.elasticsearch.uris` (或旧版的 `spring.data.elasticsearch.cluster-nodes`) 配置的 Elasticsearch 节点地址和端口是否正确。
        - **Nacos 配置**: 确认 Nacos 中 `elasticsearch.yml` 的 `elasticsearch.datasource.<your_datasource_name>.uris` 配置是否正确，并且应用已成功从 Nacos 拉取到配置。
    3. **网络连通性**: 从应用服务器 `ping` Elasticsearch 服务器 IP，或使用 `telnet <your_es_host> 9200` 测试端口连通性。检查防火墙规则是否阻止了连接。
    4. **认证与授权**: 如果 Elasticsearch 集群启用了安全特性 (如 X-Pack Security)，确保配置文件中已正确配置用户名、密码或 API Key (`spring.elasticsearch.username`, `spring.elasticsearch.password`, 或其他认证头信息)。
    5. **SSL/TLS 配置**: 如果 Elasticsearch 使用 HTTPS，确保客户端配置了正确的 SSL/TLS 证书和信任存储。相关配置项如 `spring.elasticsearch.socket-timeout` (应为 `spring.elasticsearch.connection-timeout` 和 `spring.elasticsearch.socket-timeout`)，以及更底层的 SSL 配置。
    6. **驱动版本兼容性**: 确认 `spring-boot-starter-data-elasticsearch` 的版本与 Elasticsearch 服务器版本兼容。

### 6.2 无法找到 `elasticsearchTemplate` Bean 或特定数据源的 `ElasticsearchRestTemplate` Bean

- **症状**: 应用启动时报 `NoSuchBeanDefinitionException`，提示找不到 `elasticsearchTemplate` 或 `{dataSourceName}ElasticsearchTemplate`。
- **排查步骤**:
    1. **检查主数据源配置**: 确保至少有一个数据源在配置中标记了 `primary: true`。只有主数据源才会注册名为 `elasticsearchTemplate` 的别名。
    2. **检查数据源名称**: 如果是查找特定数据源的 Bean (如 `myClusterElasticsearchTemplate`)，请确认配置文件中的数据源名称 (`elasticsearch.datasource.<dataSourceName>`) 与代码中 `@Qualifier` 指定的 Bean 名称 (`{dataSourceName}ElasticsearchTemplate`) 一致。
    3. **检查 `GXElasticsearchBeanDefinitionRegistryPostProcessor` 是否执行**: 确认该后置处理器是否被 Spring 容器扫描并执行。通常情况下，只要模块被正确引入，它会自动执行。
    4. **依赖冲突或缺失**: 检查是否有依赖冲突导致 Spring Data Elasticsearch 或 `leaf-base-elasticsearch` 模块未能正确初始化。

### 6.3 查询没有返回预期的结果

- **症状**: 执行查询后，返回的结果为空或与预期不符。
- **排查步骤**:
    1. **检查索引名称和实体类映射**: 确认 `@Document(indexName = "...")` 注解中的索引名称与 Elasticsearch 中实际的索引名称一致。确认实体类的字段与索引中的字段映射正确。
    2. **检查查询条件 (`GXCondition` / `GXLambdaCondition`)**: 
        - 仔细核对构建的查询条件是否符合业务逻辑。
        - 对于 `text` 类型的字段，模糊匹配 (`like`, `contains`, `fuzzy`) 通常需要配合正确的分词器。精确匹配 (`eq`, `is`) 在 `text` 字段上可能不会按预期工作，应使用 `keyword` 类型的子字段进行精确匹配。
        - 检查操作符是否使用正确，例如 `eq` 用于精确值，`in` 用于多个值，`between` 用于范围等。
    3. **直接在 Kibana Dev Tools 或通过 `curl` 执行查询**: 将 `GXCondition` 转换（或手动构建）为等效的 Elasticsearch DSL 查询，在 Kibana Dev Tools 中执行，看是否能得到预期结果。这有助于判断问题是在应用层还是 Elasticsearch 层。
    4. **数据同步问题**: 确认数据是否已成功索引到 Elasticsearch 中，并且是最新的。检查索引过程是否有错误。
    5. **分词器影响**: 全文搜索的结果受分词器影响很大。确保索引时和搜索时使用的分词器一致，或者搜索时使用的分词器能够正确处理查询字符串。可以通过 Elasticsearch 的 `_analyze` API 测试分词效果。

### 6.4 高亮不生效

- **症状**: 设置了高亮字段，但返回结果中没有高亮片段。
- **排查步骤**:
    1. **检查 `GXPage.setHighlightFields()`**: 确保已调用此方法并传入了需要高亮的字段名列表。
    2. **字段类型**: 高亮通常作用于 `text` 类型的字段。确保要高亮的字段是 `text` 类型，并且在 Elasticsearch 的映射中配置为可高亮。
    3. **查询条件**: 高亮依赖于查询条件匹配到了文本。如果查询条件没有命中任何文档或特定字段，自然不会有高亮结果。
    4. **Elasticsearch 高亮配置**: 模块内部会构建高亮请求。如果需要更细致的高亮控制（如自定义标签、分片大小等），可能需要直接使用 `ElasticsearchRestTemplate` 并手动构建 `HighlightBuilder`。

### 6.5 Nacos 配置未生效

- **症状**: 应用启动后，Elasticsearch 连接使用的是本地配置，而不是 Nacos 中的配置。
- **排查步骤**:
    1. **检查 Nacos 依赖**: 确认项目中是否引入了 `spring-cloud-starter-alibaba-nacos-config` 或类似的 Nacos Client 依赖。`GXElasticsearchBeanDefinitionRegistryPostProcessor` 通过检测 `com.alibaba.cloud.nacos.NacosConfigManager` 类来判断是否启用 Nacos。
    2. **检查 Nacos 连接配置**: 确保 `bootstrap.yml` (或 `application.yml`，取决于 Spring Cloud 版本和配置方式) 中 Nacos Server 地址 (`spring.cloud.nacos.config.server-addr`)、命名空间 (`spring.cloud.nacos.config.namespace`)、Group ID (`spring.cloud.nacos.config.group`) 和 Data ID (`spring.cloud.nacos.config.name` 或模块内硬编码的 `elasticsearch.yml`) 配置正确。
    3. **Nacos Server 状态**: 确认 Nacos Server 运行正常且可访问。
    4. **日志排查**: 查看应用启动日志，搜索 Nacos Client 连接 Nacos Server 的相关日志，以及 `GXElasticsearchBeanDefinitionRegistryPostProcessor` 加载配置的日志，判断是从本地加载还是从 Nacos 加载。

## 7. 参考资料

- **Spring Data Elasticsearch 官方文档**: [https://docs.spring.io/spring-data/elasticsearch/docs/current/reference/html/](https://docs.spring.io/spring-data/elasticsearch/docs/current/reference/html/)
- **Elasticsearch 官方文档**: [https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- **Spring Boot Elasticsearch 配置**: [https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.elasticsearch](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.elasticsearch)
- **Nacos 官方文档**: [https://nacos.io/zh-cn/docs/what-is-nacos.html](https://nacos.io/zh-cn/docs/what-is-nacos.html)
- **`leaf-base` (项目内其他基础模块)**: (如果适用，可以链接到项目内其他相关模块的文档，例如 `leaf-base-common` 中 `GXCondition` 的详细说明)

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