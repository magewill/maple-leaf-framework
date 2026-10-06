![Security Status](https://www.murphysec.com/platform3/v31/badge/1912047649909272576.svg)

# Maple Leaf Framework

Maple Leaf Framework是一套基于 Spring Boot 的企业级应用开发脚手架。仓库整体是一个包含 26 个 Maven 项目的 Reactor 工程（1个根聚合 POM + 25个子模块），它覆盖了数据访问、动态数据源、多级缓存、消息队列、分布式事务、CDC 同步、单点登录、状态机、注册中心等企业应用的高频基础设施。

框架采用“可选引入、按需装配”的设计：核心能力集中在 `leaf-base-framework`，其余模块彼此解耦，下游项目可以只依赖真正用到的那几个。

> 当前开发分支：`dev4.3.0`，版本号 `4.3.0-SNAPSHOT`。

## 技术栈与环境要求

根 POM 统一约束了版本，构建前请确认本地环境满足以下要求。

| 项目    | 要求      | 说明                                                  |
| ----- | ------- | --------------------------------------------------- |
| JDK   | 21 及以上  | 根 POM 中 `maven.compiler.release` 固定为 `21`，低于此版本无法编译 |
| Maven | 3.8 及以上 | 依赖 `maven-compiler-plugin` 3.15.0 等较新的插件版本          |
| 操作系统  | 无限制     | 已在 Windows 10 上完成构建与测试验证                            |
| 网络    | 首次构建需联网 | 需从 Maven 中央仓库镜像拉取依赖                                 |

主要技术栈版本（统一在根 POM 的 `<properties>` 中管理）：

| 组件                   | 版本          |
| -------------------- | ----------- |
| Spring Boot          | 4.1.1       |
| Spring Framework     | 7.0.9       |
| Spring Cloud         | 2025.1.3    |
| Spring Cloud Alibaba | 2025.1.0.0  |
| MyBatis-Plus         | 3.5.17      |
| Redisson             | 4.7.0       |
| Elasticsearch        | 9.4.2       |
| Apache Dubbo         | 3.3.6       |
| RocketMQ             | 5.3.2       |
| Debezium             | 3.6.0.Final |
| Seata                | 2.6.0       |
| Hutool               | 5.8.47      |

> 本文档的所有构建与测试命令均已在 `Temurin JDK 25.0.4` + `Apache Maven 3.9.11` 环境下实际执行并通过，执行结果见本文档末尾的“构建验证”章节。

## 模块清单

根 POM 共声明 25 个子模块。其中 `leaf-framework` 是不含源码的聚合门面，一次性传递引入 19 个常用模块，适合希望“一把梭”的下游项目。

| 模块                               | 职责                                      | 主要外部依赖                                                              | Spring Boot 自动装配                            |
| -------------------------------- | --------------------------------------- | ------------------------------------------------------------------- | ------------------------------------------- |
| `leaf-base-framework`            | 核心基础设施：工具类、注解、统一响应、全局异常、事件机制、DDD 支撑     | Spring Boot Web、Caffeine、Guava、Hutool、Jackson、BC 加密库                | 是（`GXFrameworkConfig`）                      |
| `leaf-framework`                 | 聚合门面，**无源码**，传递依赖 19 个子模块               | 无                                                                   | 否                                           |
| `leaf-base-datasource`           | 动态多数据源、MyBatis-Plus 增强、数据权限与多租户         | MyBatis-Plus 3.5.17、Druid、MySQL Connector/J 9.7.0、p6spy             | 否                                           |
| `leaf-base-datasource-mongodb`   | MongoDB 多库切换与 Repository 封装             | spring-boot-starter-data-mongodb                                    | 否                                           |
| `leaf-base-elasticsearch`        | Elasticsearch DAO / Repository 封装       | spring-data-elasticsearch 6.1.0、elasticsearch-rest-client 9.4.2     | 否                                           |
| `leaf-base-redisson`             | Redisson 缓存、分布式锁、延迟队列、Stream 队列         | Redisson 4.7.0、redisson-spring-cache                                | 否                                           |
| `leaf-base-rabbitmq`             | AMQP 收发、回调、RPC                          | spring-boot-starter-amqp、tools.jackson                              | 否                                           |
| `leaf-base-rocketmq`             | RocketMQ 发送、监听、CDC 分发                   | rocketmq-spring-boot-starter 2.3.6、fastjson                         | 否                                           |
| `leaf-base-data-sync`            | Canal 数据同步监听与解析（包名为 `cn.maple.canal`）   | 依赖 framework + rabbitmq                                             | 否                                           |
| `leaf-base-debezium`             | 内嵌 Debezium CDC 引擎                      | Debezium 3.6.0.Final（MySQL 连接器、Redis 存储）                            | 否                                           |
| `leaf-base-seata`                | Seata 分布式事务与动态数据源适配                     | seata-spring-boot-starter 2.6.0                                     | 否                                           |
| `leaf-base-sso`                  | 单点登录、验证码、OAuth、登录拦截                     | 依赖 framework + nacos                                                | 否                                           |
| `leaf-base-sse`                  | Server-Sent Events 服务端推送                | 依赖 framework                                                        | 否                                           |
| `leaf-base-retry`                | 重试上下文、回调与监听                             | 依赖 framework                                                        | 否                                           |
| `leaf-base-extension`            | COLA 风格扩展点注册与路由执行                       | 依赖 framework                                                        | 是（`GXExtensionAutoConfiguration`）           |
| `leaf-base-statemachine`         | 轻量级状态机，支持 PlantUML 导出                   | 依赖 framework                                                        | 否                                           |
| `leaf-base-nacos`                | Nacos 注册中心与配置中心基础属性                     | spring-cloud-starter-alibaba-nacos-*、nacos-client 3.1.1             | 否                                           |
| `leaf-base-dubbo-nacos`          | Dubbo + Nacos 注册，TraceId 透传与异常 Filter   | dubbo-spring-boot-starter 3.3.6                                     | Dubbo SPI（`Filter`）                         |
| `leaf-base-dubbo-zk`             | Dubbo + Zookeeper 注册                    | dubbo-zookeeper-curator5-spring-boot-starter                        | Dubbo SPI（`Filter`）                         |
| `leaf-base-dubbo-nacos-sentinel` | Sentinel 限流熔断，规则持久化到 Nacos              | Sentinel 1.8.9                                                      | Sentinel SPI（`InitFunc`）                    |
| `leaf-base-feign`                | OpenFeign 拦截器、编解码、熔断                    | spring-cloud-starter-openfeign、circuitbreaker-framework-retry       | 是（`GXFeignConfig`、`GXCircuitBreakerConfig`） |
| `leaf-base-webclient`            | WebClient 封装与客户端负载均衡                    | spring-boot-starter-webflux、spring-cloud-starter-loadbalancer 5.0.3 | 是（`GXWebClientConfig`）                      |
| `leaf-base-eureka-server`        | Eureka Server 装配                        | spring-cloud-starter-netflix-eureka-server 5.0.2                    | 是（`GXEurekaServerConfig`）                   |
| `leaf-base-eureka-client`        | Eureka Client 装配                        | eureka-client、feign、webclient                                       | 是（`GXEurekaClientConfig`）                   |
| `leaf-base-manticore`            | Manticore Search 支持，**目前仅有包声明，产出空 JAR** | manticoresearch 10.2.0                                              | 否                                           |

各模块的详细开发手册见 [`docs/`](./docs) 目录。

## 安装步骤

### 方式一：从源码构建并安装到本地仓库（推荐）

适用于需要改动框架源码、或尚未发布到远端私服的场景。在项目根目录执行：

```bash
# 完整构建并安装到本地 Maven 仓库（跳过测试，约 2 分钟）
mvn clean install -DskipTests
```

构建成功后，可在本地仓库看到全部产物：

```bash
ls ~/.m2/repository/cn/maple/framework/
# 或按你 settings.xml 中 <localRepository> 指向的路径查看
```

如需生成 javadoc 或跳过源码包，可追加 `-Dmaven.source.skip=true`；若要从任意子模块开始构建，使用 `-pl` 配合 `-am`：

```bash
# 只构建 redisson 模块及其依赖的上游模块
mvn clean install -DskipTests -pl leaf-base-redisson -am
```

### 方式二：作为依赖引入下游项目

在业务工程的 `pom.xml` 中加入所需模块即可。**建议按需引入**，不要无脑引入聚合包，以免带入大量用不上的中间件依赖。

按需引入（推荐）：

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-framework</artifactId>
    <version>4.3.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-datasource</artifactId>
    <version>4.3.0-SNAPSHOT</version>
</dependency>
```

引入聚合门面（一次性拿到 19 个常用模块）：

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-framework</artifactId>
    <version>4.3.0-SNAPSHOT</version>
</dependency>
```

> **注意**：聚合包 `leaf-framework` 不包含 `leaf-base-nacos`、`leaf-base-dubbo-nacos`、`leaf-base-dubbo-zk`、`leaf-base-extension`、`leaf-base-statemachine` 这5个模块，用到时需要单独显式声明。
>
> 另外，由于 `leaf-base-dubbo-nacos` 与 `leaf-base-dubbo-zk` 都提供 Dubbo 注册能力，同一工程**只应引入其中一个**，否则会出现 Bean 冲突。

### 方式三：发布到远端私服（项目维护者）

根 POM 已配置阿里云制品仓库：

```bash
mvn clean deploy
```

发布前需在 `~/.m2/settings.xml` 中配置 `rdc-releases` 与 `rdc-snapshots` 两个 server 的认证信息。

## 使用示例

### 1. 启用框架

**这一步不能省略。** 除6个模块通过 `AutoConfiguration.imports` 自动装配外，其余绝大多数配置类、切面、全局异常处理器都需要通过组件扫描才能生效。在 Spring Boot 启动类上添加 `@GXEnableLeafFramework`：

```java
package com.example.demo;

import cn.maple.core.framework.annotation.GXEnableLeafFramework;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@GXEnableLeafFramework
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

该注解的实质是 `@ComponentScan("cn.maple")`，请务必确保它能扫描到框架所在的包。

### 2. 编写返回统一响应体的接口

框架提供 `GXResultUtils<T>` 作为统一响应体，包含 `code`、`msg`、`data` 三个字段。建议 Controller 实现 `GXBaseController` 接口以复用对象转换能力：

```java
package com.example.demo.controller;

import cn.maple.core.framework.controller.GXBaseController;
import cn.maple.core.framework.util.GXResultUtils;
import com.example.demo.dto.UserResDto;
import com.example.demo.dto.UserEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        // 复用基类提供的实体转 DTO 能力
        return GXResultUtils.ok(convertSourceToTarget(entity, UserResDto.class));
    }

    @GetMapping("/list")
    public GXResultUtils<List<UserResDto>> list() {
        return GXResultUtils.ok(userService.listAll());
    }

    @PostMapping
    public GXResultUtils<Long> create(@RequestBody UserEntity user) {
        return GXResultUtils.ok("新增成功", userService.save(user));
    }
}
```

抛出业务异常即可被全局异常处理器 `GXExceptionHandler` 捕获并转成标准响应体，无需在 Controller 里 `try-catch`：

```java
import cn.maple.core.framework.exception.GXBusinessException;

if (user == null) {
    throw new GXBusinessException("用户不存在");
}
```

### 3. 动态数据源切换

引入 `leaf-base-datasource` 后，先在配置文件中声明多个数据源：

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

然后在方法或类上使用 `@GXDataSource` 指定数据源：

```java
import cn.maple.core.datasource.annotation.GXDataSource;

@Service
public class UserServiceImpl implements UserService {

    // 走 slave1 数据源，未标注时默认走 framework
    @GXDataSource("slave1")
    @Override
    public List<UserEntity> queryFromSlave() {
        return userMapper.selectList(null);
    }
}
```

底层由 `GXDataSourceAspect` 切面 + `GXDynamicContextHolder`（`ThreadLocal` 栈）实现，支持嵌套切换并在 `finally` 中恢复上下文。

> **重要**：`@GXDataSource` 与声明式事务混用时要格外小心。Spring 事务在方法进入时就会绑定数据库连接，数据源切换可能发生在连接建立之后从而导致失效。跨数据源的事务建议改用 Seata（`leaf-base-seata`），或拆成多个互不重叠的事务。

### 4. 数据权限过滤

使用 `@GXDataFilter` 为查询自动附加数据权限条件：

```java
import cn.maple.core.datasource.annotation.GXDataFilter;

@GXDataFilter(tableAlias = "t", userIdFieldNames = {"creator_id"})
@Override
public List<UserEntity> listAll() {
    return userMapper.selectList(null);
}
```

同时需要开启开关并实现 `DataPermissionHandler` 接口：

```yaml
maple:
  framework:
    enable:
      data-permission: true   # 开启数据权限处理
```

### 5. 本地缓存（Caffeine）

引入核心模块即可使用。先在多文件配置中定义命名缓存：

```yaml
caffeine:
  config:
    userCache:
      initialCapacity: 100
      maximumSize: 1000
      expireAfterWrite: 600
```

再通过工具类或 Spring 原生 `@Cacheable` 使用：

```java
import cn.maple.core.framework.util.GXCaffeineCacheUtils;

GXCaffeineCacheUtils.put("userCache", userId, userEntity);
UserEntity cached = GXCaffeineCacheUtils.get("userCache", userId, UserEntity.class);
```

## 配置说明

仓库根目录下的 [`config/`](./config) 目录存放的是**参考配置模板**，不是框架自身的运行时配置（框架不会读取它）。

这些内容存在依赖具体环境的占位符，例如根 POM 过滤占位符 `@leaf.framework.version@`、外部业务包名 `cn.gaple.*`、示例地址 `http://api.maple.com`。使用时请挑选需要的片段复制到自己工程的 `resources` 目录下，并替换为真实值。

常用的框架自有配置项：

```yaml
maple:
  framework:
    enable:
      file-upload: true           # 文件删除服务
      sql-illegal: true           # SQL 性能规范插件
      data-change-recorder: true  # 数据变更记录插件
      data-permission: true       # 数据权限（需实现 DataPermissionHandler）
      block-attack: true          # 阻止全表更新/删除
      tenant: true                # 多租户（表中需有 tenant_id 字段）
      debezium: false             # Debezium CDC
    web:
      client:
        token: your_token
        secret: your_secret
```


> 配置文件中的 `application-local.yml` 与 `config/local/` 已被 `.gitignore` 忽略，属于开发者私有配置，**请勿将真实密钥、数据库密码提交到仓库**。涉及敏感配置请使用 `${ENV_VAR:default}` 形式从环境变量注入。

## 构建与测试

| 目的             | 命令                                     |
| -------------- | -------------------------------------- |
| 全量构建并安装到本地仓库   | `mvn clean install -DskipTests`        |
| 仅编译打包，不安装      | `mvn clean package -DskipTests`        |
| 运行指定模块及其上游的测试  | `mvn test -pl leaf-base-framework -am` |
| 全模块跑测试（需外部中间件） | `mvn test`                             |
| 查看依赖树排查冲突      | `mvn dependency:tree -Dverbose`        |

**关于是否需要外部中间件**：`leaf-base-framework`、`leaf-base-extension`、`leaf-base-statemachine` 等模块主要是纯单元测试，无需外部依赖即可运行。而 `leaf-base-datasource`、`leaf-base-redisson`、`leaf-base-rabbitmq`、`leaf-base-elasticsearch`、`leaf-base-sso` 等模块的测试依赖 MySQL、Redis、RabbitMQ、Elasticsearch 等外部服务，需要先准备对应的环境。

## 贡献指南

欢迎提交 Issue 与 Pull Request。参与前请先花几分钟阅读本节，可以显著减少返工。

### 分支约定

本项目采用按版本号独立的开发分支，由 Git 历史可知分支命名格式如下：

| 分支                                            | 用途                              |
| --------------------------------------------- | ------------------------------- |
| `master`                                      | 主干                              |
| `dev`                                         | 早期开发分支，GitHub Actions 的 CI 触发分支 |
| `dev4.1.5`、`dev4.1.6`、`dev4.2.0`、`dev4.3.0` … | 各版本的开发分支，命名格式为 `dev` + 目标版本号    |

提交 PR 时请将目标分支指向**当前正在开发的版本号分支**（当前为 `dev4.3.0`），而不是 `master`。

### 编码规范

这是本项目最强的几条硬约束，请务必遵守：

1. **包名统一以 `cn.maple` 开头**。根包与该 Maven 坐标 `cn.maple.framework` 并不一致，这是历史约定，不要"顺手纠正"。
2. **类名以 `GX` 前缀为主**。现有代码中约70%的类遵循该约定。新增类请保持这一风格，便于检索与识别。
3. **模块目录名与包名存在映射关系**，但有历史例外，新增模块时请先对齐下表：
   | 模块目录                             | 包名前缀                          |
   | -------------------------------- | ----------------------------- |
   | `leaf-base-framework`            | `cn.maple.core.framework`     |
   | `leaf-base-datasource`           | `cn.maple.core.datasource`    |
   | `leaf-base-data-sync`            | `cn.maple.canal`（例外）          |
   | `leaf-base-dubbo-nacos-sentinel` | `cn.maple.sentinel`（例外）       |
   | `leaf-base-datasource-mongodb`   | `cn.maple.mongodb.datasource` |
   | 其余 `leaf-base-X`                 | 通常对应 `cn.maple.X`             |
4. **每个包建议提供 `package-info.java`**，现有模块普遍包含该文件，用于承载 NullAway 的 `@NullMarked` / `@NullUnmarked` 等包级注解。
5. **新增配置属性请使用 `@ConfigurationProperties` 类型安全绑定**，并在 `additional-spring-configuration-metadata.json` 中补充 IDE 提示元数据。
6. **敏感信息不得硬编码**，禁止把真实密码、密钥写入源码、测试快照或 `config/` 模板。
7. **最小必要改动**：不要在生产修复中夹带无关重构、格式化或依赖升级。发现相邻问题时请另开 Issue 讨论。

### 静态检查（提交前必读）

项目在编译期启用了 ErrorProne + NullAway，配置位于根 POM 的 `maven-compiler-plugin`：

- `-Xplugin:ErrorProne -XepDisableAllChecks -Xep:NullAway:ERROR`
- `-XepOpt:NullAway:AnnotatedPackages=cn.maple`
- `-XepOpt:NullAway:JSpecifyMode=true`

这意味着**任何 `cn.maple` 包下的空指针不安全写法都会导致构建失败**，而不是仅仅产生警告。请务必在本地执行完整构建确认通过后再推送。

> 注意：检查排除了测试源码与 `target/generated-sources`，但 `src/main` 下的所有代码都在扫描范围内。

### 新增依赖的原则

根 POM 的 `<dependencyManagement>` 中已经积累了大量用于**解决版本冲突**的显式声明，每个都带有中文注释说明解决的是哪两个组件之间的冲突，例如：

```xml

<dependency>
    <groupId>com.github.luben</groupId>
    <artifactId>zstd-jni</artifactId>
    <version>${zstd-jni.version}</version>
</dependency>
```

新增或升级依赖时请遵循：**版本号统一提到根 POM 的 `<properties>`；冲突覆盖声明放在 `spring-boot-dependencies` 之前**（它在 `<dependencyManagement>` 中靠后才生效）；并补上注释说明原因。**不要为了方便而删除已有的冲突覆盖声明**，那通常是踩过坑才加上的。

### 测试要求

- 新增功能请配套单元测试，纯业务逻辑优先（当前 `leaf-base-framework` 有518个测试用例可作为参考范本）。
- Bug 修复请提供**能在旧代码上失败**的回归测试。
- 请统一使用 **JUnit 5（Jupiter）**。项目中残留的 JUnit 4 写法属于历史遗留，新代码请勿再沿用（原因见"已知问题"章节）。
- 需要外部中间件的测试，请确保在没有该环境时可以优雅跳过，而不是直接让整条流水线失败。

### 提交信息与 PR

- 提交信息建议采用 `类型: 简述` 格式，例如 `feat: 动态数据源支持嵌套切换`、`fix: 修复 ThreadLocal 上下文泄漏`、`docs: 补充 Redisson 模块手册`。
- PR 描述中请说明改动动机、影响范围、是否涉及接口或配置项变更，以及本地执行的验证命令与结果。
- 若改动涉及数据库字段、消息格式、对外接口或错误码，请显式说明向前/向后兼容策略。

## 4.x 版本计划

以下是项目既定的演进方向：

1. 4.x版本将 MyBatis-Plus 替换为 [MyBatis Dynamic SQL](https://mybatis.org/mybatis-dynamic-sql/docs/introduction.html)
2. 适配 Spring Boot 到 3.x 版本（**已于 4.3.0-SNAPSHOT 完成并超越，当前实际已进入 Spring Boot 4.1.1**）
3. 重构 datasource 模块，增强数据源切换
4. 增强数据权限的处理
5. 注册中心、配置中心去掉 Nacos？？？

## 已知问题清单（待人工审查）

以下问题是在本次文档梳理过程中通过静态分析与实际构建验证发现的，**均未修改任何代码**，记录在此供后续决策：

| 编号 | 位置                                           | 问题描述                                                                                                                                  | 影响                                               | 建议方向                                                            |
| -- | -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ | --------------------------------------------------------------- |
| 1  | `.github/workflows/maven.yml:24-28`          | CI 固定使用 JDK 11，而根 POM 要求 `--release 21`                                                                                               | **CI 必然构建失败**，且 `Cache maven` 无意义；当前所有 CI 校验形同虚设 | 将 `java-version` 改为 `21`（或 `25`），并把触发分支 `dev` 更新为当前的 `dev4.3.0` |
| 2  | `leaf-base-statemachine/src/test/`           | 4个测试类中3个使用 JUnit 4，但 Surefire 3.2.5 使用 `JUnitPlatformProvider` 且未引入 `junit-vintage-engine`                                            | 实际构建时**只执行了1个测试用例，3个测试类被静默忽略**，无任何失败提示           | 迁移到 JUnit 5，或显式引入 `junit-vintage-engine` 依赖                     |
| 3  | `leaf-base-extension/src/test/`              | `ExtensionTest.java` 使用 JUnit 4，同样被静默忽略（该模块实际执行39个测试，来自其余11个测试类）                                                                      | 扩展点的集成场景缺少有效回归保护                                 | 同上，迁移到 JUnit 5                                                  |
| 4  | `leaf-base-manticore/`                       | 模块仅有 `package-info.java`，构建产出空 JAR（构建日志出现 `JAR will be empty` 警告）                                                                     | 下游引入后无任何可用类，易造成误解                                | 确认是否仍要支持 Manticore；若需要则补充实现，若不需要建议暂时从 `<modules>` 中摘除以免误导       |
| 5  | `README.md`（旧版）、`docs/开发手册.md`               | 文档称"适配 Spring Boot 到 3.x"，而实际已是 4.1.1；`docs/leaf-base-framework开发手册.md` 称依赖 Spring Boot `3.4.x` / Spring Framework `6.2.x`            | 与实际依赖版本不符，误导使用者                                  | 同步更新为实际版本                                                       |
| 6  | `docs/leaf-base-framework开发手册.md:47,79-80`   | 文档提到 `GXDBStringEscapeUtils`、`@GXCacheable`、`@GXCacheEvict`、`GXCacheableAspect`，但全仓库未找到对应的 Java 文件                                    | 文档描述的 API 不存在，使用者照抄会编译失败                         | 确认是已删除还是尚未实现，据此删除文档或补充代码                                        |
| 7  | 各模块 `AGENTS.md`                              | 25个模块中23个各有一份，`leaf-base-manticore` 与 `leaf-framework` 缺失，仓库根目录也没有                                                                    | 贡献者不易统一获取约定                                      | 考虑在根目录补一份，或将各模块的一致说明合并进本 README                                 |
| 8  | `config/application.yml:50-51`               | Druid 监控页账号密码存在默认值 `britton/britton` 且已提交到仓库                                                                                          | 若下游照抄上线，监控页面存在未授权访问风险                            | 改为 `${ENV_VAR:}` 形式从环境变量注入，不提供真实默认值                             |
| 9  | `leaf-framework/pom.xml`                     | 聚合门面只传递依赖了19个模块，遗漏了 `leaf-base-nacos`、`leaf-base-dubbo-nacos`、`leaf-base-dubbo-zk`、`leaf-base-extension`、`leaf-base-statemachine` 这5个 | 使用者容易误以为引入聚合包就拥有了全部能力                            | 已在本文档"安装步骤"中标注，建议后续评估是否补齐                                       |
| 10 | `leaf-base-dubbo-nacos`、`leaf-base-dubbo-zk` | 两个模块均导出同类型的 Dubbo `Filter` SPI，同时引入会产生冲突                                                                                              | 下游项目同时依赖会导致 Dubbo Filter 行为不可预期                  | 已在本文档标注二选一；建议在文档中补充更明确的互斥说明                                     |

## 构建验证

本次文档更新所使用的实际验证记录如下，**未修改任何源码**。

| 项目    | 内容                                                                                                                      |
| ----- | ----------------------------------------------------------------------------------------------------------------------- |
| 环境    | Windows 10 + Temurin JDK 25.0.4 + Apache Maven 3.9.11                                                                   |
| 构建命令  | `mvn -B clean install -DskipTests`                                                                                      |
| 构建结果  | `BUILD SUCCESS`，26个 Reactor 项目全部 SUCCESS，耗时约 2 分 5 秒                                                                    |
| 测试命令  | `mvn -B -pl leaf-base-framework,leaf-base-extension,leaf-base-statemachine test`                                        |
| 测试结果  | `BUILD SUCCESS`；`leaf-base-framework` 执行 518 个测试、`leaf-base-extension` 执行 39 个、`leaf-base-statemachine` 执行 1 个，全部通过且无跳过 |
| 工作区状态 | `git status` 干净，无未提交改动残留（本次仅新增/修改 Markdown 文档）                                                                          |

未执行全量 `mvn test` 的原因是部分模块（数据源、Redisson、RabbitMQ、Elasticsearch、SSO 等）的测试依赖 MySQL、Redis、RabbitMQ、Elasticsearch 等外部中间件，本机未部署。

## 许可证

本项目采用 [GPL v2](./LICENSE) 开源许可证。使用时请遵守 GPL v2 的相关条款。
