# leaf-base-seata 开发手册

## 1. 模块概述

leaf-base-seata 模块是 maple-leaf-framework 框架中用于整合 Seata 分布式事务与动态数据源的组件。该模块提供了在分布式事务环境下实现多数据源切换的能力，使开发人员能够在微服务架构中轻松实现跨服务、跨数据库的事务一致性。

### 1.1 主要功能

- 整合 Seata 分布式事务与动态数据源
- 支持在分布式事务环境下进行多数据源切换
- 提供本地配置和 Nacos 配置两种方式加载 Seata 配置
- 自动配置 Seata 相关的数据源代理和事务管理

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

模块支持两种方式加载 Seata 配置：

1. **本地配置文件**：通过 `classpath:/${spring.profiles.active}/seata.yml` 加载
2. **Nacos 配置中心**：从 Nacos 配置中心加载 `seata.yml` 配置

#### 2.2.1 本地配置示例 (seata.yml)

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

#### 2.2.2 Nacos 配置中心

当项目中引入 Nacos 相关依赖时，模块会自动从 Nacos 配置中心加载 `seata.yml` 配置。相关配置项如下：

- dataId: seata.yml
- groupId: 从 `spring.cloud.nacos.config.group` 或 `nacos.config.group` 获取
- 服务地址: 从 `spring.cloud.nacos.config.server-addr` 或 `nacos.config.server-addr` 获取
- 命名空间: 从 `spring.cloud.nacos.config.namespace` 或 `nacos.config.namespace` 获取
- 认证信息: 从 Spring Cloud Nacos 或 Nacos 配置中获取用户名和密码

## 3. 核心组件

### 3.1 GXSeataDynamicDataSourceConfig

`GXSeataDynamicDataSourceConfig` 是整合 Seata 分布式事务与动态数据源的核心配置类。该类通过条件注解确保只有在存在 Seata 和动态数据源相关类时才会启用。

主要功能：

- 创建支持 Seata 分布式事务的 SqlSessionFactory
- 配置 MyBatis-Plus 相关功能，如分页插件、字段自动填充等
- 整合 Spring 事务管理器，确保事务正常工作

### 3.2 配置属性类

#### 3.2.1 GXLocalSeataProperties

`GXLocalSeataProperties` 用于从本地配置文件加载 Seata 配置。当项目中不存在 Nacos 相关依赖时，会使用该类加载配置。

#### 3.2.2 GXNacosSeataProperties

`GXNacosSeataProperties` 用于从 Nacos 配置中心加载 Seata 配置。当项目中存在 Nacos 相关依赖时，会使用该类加载配置。

## 4. 使用示例

### 4.1 基本使用

1. 在项目中引入 leaf-base-seata 依赖
2. 配置 Seata 相关参数（本地配置文件或 Nacos 配置中心）
3. 在需要分布式事务的方法上添加 `@GlobalTransactional` 注解

```java
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    
    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    public void createOrder(OrderDTO orderDTO) {
        // 创建订单
        orderMapper.insert(order);
        
        // 调用库存服务扣减库存
        inventoryClient.deduct(orderDTO.getProductId(), orderDTO.getCount());
        
        // 调用账户服务扣减余额
        accountClient.deduct(orderDTO.getUserId(), orderDTO.getAmount());
    }
}
```

### 4.2 多数据源切换

在使用 Seata 分布式事务的同时，依然可以使用动态数据源切换功能：

```java
import cn.maple.core.datasource.annotation.GXDataSource;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    
    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    public void createOrder(OrderDTO orderDTO) {
        // 默认数据源操作
        orderMapper.insert(order);
        
        // 切换到其他数据源
        this.updateOrderStatus(order.getId(), OrderStatus.PROCESSING);
    }
    
    @GXDataSource("slave")
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        // 在从库更新订单状态
        orderMapper.updateStatus(orderId, status);
    }
}
```

## 5. 注意事项

1. 使用 Seata 时，所有参与分布式事务的数据源都会被 Seata 代理
2. 动态数据源切换功能依然可用，但所有操作都会被纳入到 Seata 的事务管理中
3. 确保所有参与事务的数据库都已注册到 Seata 服务端
4. 在配置文件中设置 `seata.enable-auto-data-source-proxy=false`，由框架统一管理数据源代理
5. 分布式事务适用于对数据一致性要求较高的场景，但会影响系统性能，请根据业务需求合理使用

## 6. 常见问题

### 6.1 Seata 服务端未启动

**问题描述**：应用启动时报错，无法连接到 Seata 服务端

**解决方案**：
- 确认 Seata 服务端是否已启动
- 检查配置文件中的 Seata 服务地址是否正确
- 检查网络连接是否正常

### 6.2 分布式事务未生效

**问题描述**：使用 `@GlobalTransactional` 注解，但分布式事务未生效

**解决方案**：
- 确认 `@GlobalTransactional` 注解是否添加在公共方法上
- 确认方法是否通过 Spring 代理调用（避免类内部直接调用）
- 检查是否存在事务传播行为配置不当的情况

### 6.3 数据源代理冲突

**问题描述**：启动时报错，提示数据源已被代理

**解决方案**：
- 确认配置文件中已设置 `seata.enable-auto-data-source-proxy=false`
- 避免在项目中手动创建 Seata 数据源代理

## 7. 参考资料

- [Seata 官方文档](https://seata.io/zh-cn/docs/overview/what-is-seata.html)
- [Spring Boot 整合 Seata](https://seata.io/zh-cn/docs/user/quickstart.html)
- [Seata 分布式事务模式](https://seata.io/zh-cn/docs/dev/mode/at-mode.html)