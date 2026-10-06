[![Security Status](https://www.murphysec.com/platform3/v31/badge/1912047649909272576.svg)](https://www.murphysec.com/console/report/1912039536918654976/1912047649909272576)

# Maple Leaf Framework

基于 Spring Boot 的企业级应用开发脚手架，用于沉淀企业系统中反复出现的基础设施能力：动态数据源与数据权限、多级缓存、消息队列、分布式事务、CDC 数据同步、单点登录、状态机、服务注册与治理等。目标是让业务工程只关注领域逻辑。

三条贯穿全框架的设计取向：

- **按需引入**。除核心模块外，各模块之间不存在强制传递依赖，引用哪个模块就启用哪项能力。
- **约定优于配置**。统一响应体、全局异常处理、对象转换、参数校验、链路追踪等默认就绪，不需要额外装配。
- **显式启用**。核心组件由 `@GXEnableLeafFramework` 触发，避免引入依赖后被隐式改变应用行为。

> 当前版本 `4.3.0-SNAPSHOT`，开发分支 `dev4.3.0`。完整文档见 [`docs/`](./docs)。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| JDK | 21 及以上（根 POM 固定 `maven.compiler.release=21`） |
| Maven | 3.8 及以上 |

框架基线为 Spring Boot 4.x / Spring Framework 7.x / Spring Cloud 2025.x，具体版本以根 POM 的 `<properties>` 为准。

## 引入依赖

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-framework</artifactId>
    <version>4.3.0-SNAPSHOT</version>
</dependency>
```

按需追加其他能力模块，`artifactId` 见「模块」一节。

框架产物发布到私有制品仓库而非 Maven 中央仓库。团队内使用需在 `pom.xml` 或 `settings.xml` 中声明对应仓库与凭据（地址见根 POM 的 `distributionManagement`），或先按下一节从源码安装到本地仓库。

## 快速开始

**1. 启用框架**

这一步不可省略。除少数模块提供 Spring Boot 自动装配外，绝大多数配置类、切面与全局异常处理器依赖组件扫描：

```java
@SpringBootApplication
@GXEnableLeafFramework   // 等价于 @ComponentScan("cn.maple")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

**2. 编写接口**

统一响应体为 `GXResultUtils<T>`，含 `code`、`msg`、`data` 三个字段。Controller 实现 `GXBaseController` 可直接复用对象转换等默认方法：

```java
@RestController
@RequestMapping("/user")
public class UserController implements GXBaseController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public GXResultUtils<UserResDto> detail(@PathVariable Long id) {
        UserEntity entity = userService.getById(id);
        return GXResultUtils.ok(convertSourceToTarget(entity, UserResDto.class));
    }

    @PostMapping
    public GXResultUtils<Long> create(@RequestBody UserEntity user) {
        return GXResultUtils.ok("新增成功", userService.save(user));
    }
}
```

业务分支直接抛出 `GXBusinessException`，由 `GXExceptionHandler` 转成标准响应体，无需在 Controller 内捕获。

**3. 按需要叠加能力**

引入 `leaf-base-datasource` 后，声明多个数据源，即可用注解在方法或类粒度切换：

```yaml
dynamic:
  datasource:
    framework:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://127.0.0.1:3306/framework?useSSL=false&characterEncoding=utf8
      username: your_user
      password: your_password
      filters: stat,wall,slf4j
    slave1:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://127.0.0.1:3306/framework_slave?useSSL=false&characterEncoding=utf8
      username: your_user
      password: your_password
      filters: stat,wall,slf4j
```

```java
@GXDataSource("slave1")
@Override
public List<UserEntity> queryFromSlave() {
    return userMapper.selectList(null);
}
```

数据源切换基于切面与 `ThreadLocal` 栈实现，支持嵌套并在 `finally` 中恢复。与声明式事务混用时需谨慎：Spring 在事务方法进入时即绑定数据库连接，晚于此的切换会失效；跨数据源事务请改用 Seata。

更多能力（数据权限、缓存、消息、单点登录、扩展点等）的用法见 [`docs/`](./docs) 下各模块的开发手册。

## 从源码构建

```bash
mvn clean install -DskipTests
```

增量构建单个模块及其上游依赖：

```bash
mvn clean install -DskipTests -pl leaf-base-redisson -am
```

跳过源码包可追加 `-Dmaven.source.skip=true`；发布到私有仓库执行 `mvn clean deploy`。

部分模块的测试用例依赖 MySQL、Redis、RabbitMQ、Elasticsearch 等实例，本地无环境时请用 `-DskipTests`，或针对单个模块运行。

## 模块

脚手架按能力域划分为一组相互独立的模块，引用哪个就启用哪项能力，不建议整体引入。

| 能力域 | 覆盖内容 | 相关模块 |
| --- | --- | --- |
| 核心 | 统一响应体、全局异常、对象转换、参数校验、缓存键管理、事件总线、链路追踪、DDD 基础设施 | `leaf-base-framework` |
| 数据访问 | 动态多数据源、数据权限、多租户、自动填充 | `leaf-base-datasource` |
| 存储扩展 | MongoDB、Elasticsearch | `leaf-base-datasource-mongodb`、`leaf-base-elasticsearch` |
| 缓存 | 本地 Caffeine 缓存、Redis 缓存/分布式锁/延迟队列 | `leaf-base-framework`、`leaf-base-redisson` |
| 消息 | RabbitMQ、RocketMQ 收发与分发 | `leaf-base-rabbitmq`、`leaf-base-rocketmq` |
| 数据同步 | Canal 监听解析、Debezium CDC | `leaf-base-data-sync`、`leaf-base-debezium` |
| 分布式事务 | Seata 与动态数据源适配 | `leaf-base-seata` |
| 服务治理 | RPC 注册发现、声明式调用、限流熔断 | `leaf-base-dubbo-*`、`leaf-base-feign`、`leaf-base-webclient`、`leaf-base-*-eureka-*`、`leaf-base-dubbo-nacos-sentinel` |
| 通用能力 | 单点登录、服务端推送、重试、扩展点、状态机 | `leaf-base-sso`、`leaf-base-sse`、`leaf-base-retry`、`leaf-base-extension`、`leaf-base-statemachine` |
| 聚合门面 | 一次性传递引入常用模块 | `leaf-framework` |

聚合门面只覆盖常用模块，并不意味着包含全部能力，使用前请确认目标模块是否在其中；未被覆盖的需要单独声明。

`leaf-base-dubbo-nacos` 与 `leaf-base-dubbo-zk` 导出同名 Dubbo SPI key 但实现类不同，同一工程只能二选一。

> 权威且始终最新的模块清单请直接参考根 `pom.xml` 的 `<modules>`，各模块的详细设计见对应开发手册。

## 配置

根目录 [`config/`](./config) 是参考配置模板，**框架不会读取它**。其中的 `@leaf.framework.version@` 为根 POM 过滤占位符，另有外部包名与示例地址，取用时需替换。

框架自有开关统一位于 `maple.framework` 前缀下：

```yaml
maple:
  framework:
    enable:
      file-upload: true           # 文件删除服务
      sql-illegal: true           # SQL 性能规范插件
      data-change-recorder: true  # 数据变更记录插件
      data-permission: true       # 数据权限，需实现 DataPermissionHandler
      block-attack: true          # 阻止全表更新/删除
      tenant: true                # 多租户，表需含 tenant_id 字段
      debezium: false             # Debezium CDC
    web:
      client:
        token: your_token
        secret: your_secret
```

敏感值请使用 `${ENV_VAR:}` 从环境变量注入，不要提供真实默认值。`application-local.yml` 与 `config/local/` 已被 `.gitignore` 忽略，属开发者私有配置。

## 贡献

提交 PR 前请注意以下几项本项目特有的约束。

**分支**。采用 `dev` + 目标版本号的命名（`dev4.2.0`、`dev4.3.0`……），PR 请指向当前版本分支而非 `master`。

**命名**。

- 包根统一为 `cn.maple`，与 Maven 坐标 `cn.maple.framework` 不一致属历史约定，请勿修改。
- 类名以 `GX` 前缀为主，新增类请保持一致。
- 模块目录到包名的映射存在历史例外（如数据同步模块对应 `cn.maple.canal`、Sentinel 模块对应 `cn.maple.sentinel`），新增模块前先对齐现有约定。
- 每个包提供 `package-info.java`，用于承载 NullAway 的包级注解。

**静态检查**。根 POM 启用了 ErrorProne + NullAway，扫描范围为 `cn.maple` 包下的 `src/main`，且级别为 ERROR——空指针不安全的写法会直接让构建失败而非产生警告。推送前请在本地完成全量构建。

**依赖管理**。根 POM `<dependencyManagement>` 中存在大量用于消解特定组件间版本冲突的显式声明，均带注释说明针对哪两个组件。新增或升级依赖时，版本统一提到根 POM `<properties>`，冲突覆盖声明必须置于 `spring-boot-dependencies` 之前才能生效，并补注释说明原因。

**测试**。统一使用 JUnit 5，Bug 修复需附带能在旧代码上失败的回归测试。

## 已知问题

记录在 [`docs/已知问题清单.md`](./docs/已知问题清单.md)，均为待人工审查的遗留项，尚未修改任何代码。

## 许可证

[GPL v2](./LICENSE)。
