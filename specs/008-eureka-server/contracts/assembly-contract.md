# 契约：自动装配边界

**模块**: `leaf-base-eureka-server` | **入口**: `cn.maple.eureka.server.config.GXEurekaServerConfig`（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）| **版本**: 4.3.0

## 1. 当前契约（审查基线，2026-10-22）

| 条件 | 是否装配注册中心 | 说明 |
| --- | --- | --- |
| 模块在类路径（任意应用形态） | 是 | 只有 `@ConditionalOnClass(EnableEurekaServer.class)`，该条件恒定成立 |
| WebFlux / REACTIVE 应用 | 是（随后启动失败或行为不确定） | 无 `@ConditionalOnWebApplication` 守卫 |
| 使用者希望关闭 | **不可关闭** | 无任何开关属性 |
| 聚合门面 `leaf-framework` 消费者 | 是 | 门面无条件依赖本模块 |

> 该状态即 `spec.md` 的 F-01，判定为高风险缺陷。

## 2. 目标契约（修复后）

| # | 前置条件 | 期望结果 | 断言方式 |
| --- | --- | --- | --- |
| A-01 | 模块在类路径，`maple.framework.enable.eureka-server` 缺省 | 不创建注册中心 Bean；**Eureka 客户端同时被默认禁用（`eureka.client.enabled=false`，最低优先级）**；应用按自身 Web 栈正常启动；无告警日志 | `ApplicationContextRunner` 断言无 `EurekaServerAutoConfiguration`/`Marker` Bean；`GXEurekaServerClientDefaultsPostProcessorTest` 断言客户端默认值 |
| A-02 | 同上，开关显式 `false` | 同 A-01 | 同上 |
| A-03 | 开关显式 `true`，Servlet 应用 | 创建注册中心 Bean，监听 `server.port`，`/eureka/apps` 可用，输出一条 ASCII INFO 启用日志；客户端保持启用（允许对等注册），并以最低优先级给出 `register-with-eureka=false` / `fetch-registry=false` 单机默认（集群成员显式设 `true` 覆盖） | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `GET /eureka/apps` = 200；`GXEurekaServerSwitchSpringBootTest#switchOnlySingleMachineDefaultsToNoRegistration` 断言 `EurekaClientConfigBean` 不注册不拉取 |
| A-04 | 开关 `true`，但类路径缺 `EnableEurekaServer`（starter 被排除） | 上下文正常启动，不创建注册中心 Bean，不抛异常 | `FilteredClassLoader(EnableEurekaServer.class)` |
| A-05 | 开关 `true`，非 Servlet 应用（REACTIVE / 非 Web） | 不装配注册中心；**客户端同样被默认禁用**；不出现"缺 ReactiveWebServerFactory"或 `Failed to start bean 'eurekaAutoServiceRegistration'` 等间接失败 | 仓库外 WebFlux 冒烟：开关缺省与 `=true` 均 `PING=200`、`/eureka/apps=404`；单测 `disablesClientWhenSwitchIsEnabledButApplicationIsReactive` |
| A-06 | 只引入聚合门面 `leaf-framework` | 依赖树中不含本模块，因此上述开关语义不适用（默认完全无注册中心能力） | `mvn -pl leaf-framework dependency:tree` 不含 `leaf-base-eureka-server` |
| A-07 | 开关 `true` 且用户自定义了同类型 Bean | 不覆盖用户 Bean；框架仅通过 `@EnableEurekaServer` 提供装配 | 本模块**不声明任何 `@Bean`**（`GXEurekaServerConfig` 仅含构造器与启用日志），因此不存在覆盖用户 Bean 的路径；校验方式：`GXEurekaServerConfigTest#eurekaServerConfigIsAutoConfigurationAndEnablesEurekaServer` 与 `contracts/assembly-contract.md` §3 第 5 条共同锁定"入口只有 `@AutoConfiguration` + `@EnableEurekaServer`、无业务 Bean 聚合" |
| A-08 | 应用显式配置 `eureka.client.enabled=true`（任意 Web 类型） | 模块不覆盖该值，客户端行为完全由应用决定 | `GXEurekaServerClientDefaultsPostProcessorTest#keepsExplicitClientSetting` |

## 3. 契约不变量

1. 自动装配入口类名、包名、`AutoConfiguration.imports` 内容不因开关引入而变化。
2. `@EnableEurekaServer` 语义不变（仍是 Eureka Server 的启用注解），只是被条件包裹。
3. 关闭状态必须"静默不生效"：不打印 WARN/ERROR，避免门面消费者被无意义日志打扰。
4. 启用状态必须可观测：至少一条 INFO 说明"由哪个开关启用"并输出 `server.port` 配置值（未设置时输出 `server.port=<unset>`；`0` 明确表示随机端口，日志文本带 `(0 = random port)` 说明），日志文本为 ASCII。
5. 本模块不得因开关引入而新增对 Feign / WebClient / 业务 Bean 的依赖或装配（`AGENTS.md` 第 43 条）。

## 4. 反例（必须被判为不通过）

- 开关 `=false` 时仍出现 `/eureka/apps` 端点或 `EurekaServerAutoConfiguration` Bean。
- 开关缺省时 WebFlux 应用启动失败且错误信息指向缺失的 Servlet/Jersey 组件。
- 启用日志包含中文或全角符号（违反 `AGENTS.md` 第 7 条）。
- 为"关闭注册中心"而要求下游排除 starter 或使用 `@SpringBootApplication(exclude=...)`（说明开关未生效）。
