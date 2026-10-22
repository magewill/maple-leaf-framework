# AGENTS.md - leaf-base-eureka-client

本文档用于指导 `leaf-base-eureka-client` 模块的后续迭代开发。修改本模块时，优先保证 Eureka 客户端注册发现行为、装配开关、示例配置和 Spring Boot 集成行为稳定，避免把注册中心服务端逻辑或业务调用编排混入客户端基础模块。

## 通用硬性规则

1. 每次修改只能触碰当前 Maven 模块；确需跨模块时，先说明触发原因、受影响模块、替代方案和最小改动集合。
2. 现有代码已在生产环境使用，不得随意改变公开类名、配置键、默认注册发现行为、自动装配入口或 Bean 名称；必须变更时，同步补充兼容说明、迁移路径和回归测试。
3. 在确保原有功能正确的前提下完善实现；修改既有逻辑前，先用测试锁定当前成功、失败、空值、边界和回归路径。
4. 基础框架代码必须优先审查启动性能、线程安全、资源释放和并发可见性；线程池与虚拟线程都能满足需求时，优先使用虚拟线程。
5. 空值处理必须支持 JSpecify 规范：新增或修改 API、配置属性、回调、集合元素和异步结果时，明确 `@NullMarked`、`@Nullable` 或等价约束。
6. 严格遵循测试驱动最佳实践：先写或调整能暴露问题的测试，再实现最小代码变更，最后运行模块级验证命令。
7. 所有 `log` 日志内容必须使用 ASCII 字符，避免运行环境、终端编码或日志采集链路出现乱码。
8. 不要随便把已有代码抽取成单独方法或类；只有抽取后的逻辑至少被两个位置复用，或能显著降低复杂度且不破坏语义时，才允许抽取。
9. 删除方法、类、变量上的冗余注释；清理 Javadoc 中重复、空泛或仅复述签名的信息，只保留语义说明、边界条件、异常约定等必要内容。
10. 控制 `AGENTS.md` 长度与结构，建议保持在 200 行以内；规范继续增加时，在子目录创建领域指南，本文件只保留入口链接和必须遵守的红线。

## 1. 模块定位

`leaf-base-eureka-client` 是 Maple Leaf Framework 中提供 Eureka 客户端接入能力的模块，当前职责集中在：

1. 引入 `spring-cloud-starter-netflix-eureka-client` 与 `leaf-base-webclient`（后者带来 `spring-cloud-starter-loadbalancer`），使业务应用具备服务注册与服务名负载均衡调用的基础能力。
2. 通过 `GXEurekaClientConfig` 声明本模块的自动装配入口，并保证它排在 `GXFeignConfig`、`GXWebClientConfig`、`EurekaClientAutoConfiguration` 之后；该类**不定义任何 Bean**。
3. 通过 `GXEurekaClientDefaultsPostProcessor` 执行 `maple.framework.enable.eureka-client` 开关的降级语义（见第 2 节）。
4. 以 `src/main/resources/examples/` 下的示例文件交付单注册中心与多注册中心配置样例（**不在** `classpath:/` 根，不会被自动加载）。
5. 交付 `META-INF/additional-spring-configuration-metadata.json`，使开关在 IDE 中可发现。

本模块**不负责**：

1. 启动 Eureka Server 注册中心（属 `leaf-base-eureka-server`）。
2. 自动开启 `@EnableFeignClients`：Feign 客户端扫描范围必须由业务应用显式声明，模块只提供 Feign 基础能力。
3. 装配 `@LoadBalanced WebClient.Builder` 或 `HttpServiceProxyFactory`：两者由 `leaf-base-webclient` 的 `GXWebClientConfig` 提供，本模块只引用其装配顺序。
4. 提供注册中心地址、注册开关的框架级默认值；也不再提供任何 `spring.cloud.openfeign.*` 默认键（属 `config/{dev,test,pre,prod}/app-environment.yml`）。
5. 定义具体业务 Feign 接口、业务 DTO、服务治理、灰度路由或实例筛选。

## 2. 装配开关与装配契约

开关：`maple.framework.enable.eureka-client`（`Boolean`，**默认 `true`**，`matchIfMissing = true`）。

| 开关值 | 应用显式 `eureka.client.enabled` | 本模块配置类 | Eureka 客户端 Bean | 日志 |
| --- | --- | --- | --- | --- |
| 未设置 / `true` | 未设置 | 生效 | 存在（上游默认行为） | 未配置 `defaultZone` 时 1 条 ASCII WARN |
| 未设置 / `true` | `false` | 生效 | 不存在 | 无 |
| `false` | 未设置 | 不生效 | 不存在 | 1 条 ASCII INFO `Eureka client disabled via maple.framework.enable.eureka-client=false` |
| `false` | `true` | 不生效 | 存在（应用自行负责） | 1 条 ASCII INFO |

红线：

1. 开关为 `false` 时，模块只能写入 `eureka.client.enabled=false`（最低优先级、应用显式值优先），**不得**写入其它 `eureka.*` 键。
2. 开关为 `true` 时，模块**不得**写入任何 `eureka.*` 键，尤其不得提供 `register-with-eureka` 与 `service-url.defaultZone`。
3. 禁止在 `src/main/resources/` 根新增 `application.yml`：它与宿主同名配置位置冲突，最终生效值取决于类路径顺序（见 `specs/009-eureka-client-module-audit/audit-report.md` §5 实测）。
4. 日志通过 `DeferredLogFactory` 输出（环境后处理早于日志系统初始化），内容保持纯 ASCII，且每个上下文只输出一次。

## 3. 功能基线

开发前必须理解并保持以下行为稳定：

1. `GXEurekaClientConfig` 是**空壳配置类**：无 `@Bean`、无 `@EnableFeignClients`、无 `@EnableEurekaServer`；其价值是自动装配入口、排序约束与 `defaultZone` 缺失告警。
2. `@LoadBalanced WebClient.Builder`、`HttpServiceProxyFactory`、Feign 请求拦截器与错误解码器分别由 `leaf-base-webclient`、`leaf-base-feign` 提供；修改本模块不得改变这些 Bean 的名称、作用域或注入契约。
3. `@ConditionalOnClass({FeignClient, WebClient, EurekaClientAutoConfiguration})` 保持现状：缺少任一依赖时本模块配置类跳过，但示例文件与开关元数据仍然存在。
4. 注册中心不可达时应用**正常启动**，注册与注册表拉取在后台按 30s 周期重试，并产生 `TransportException` 级别的日志噪音；这是既定运行形态，不得改成"注册失败即启动失败"。
5. `examples/` 下的示例是**交付物而非运行时配置**：不得放进 `classpath:/` 根，不得被 `spring.config.import` 自动引用。
6. 客户端模块不应维护业务服务列表、硬编码业务服务名或把调用目标写死在框架层。
7. 历史上出现过的 `feign.hystrix.enabled` 已不再由本模块提供，不得复活该键。

## 4. 强制开发规则

1. 所有日志文本必须使用 ASCII 字符；禁止新增或保留中文、全角符号或其他非 ASCII 日志文本。
2. 只在本模块内修改客户端接入能力；不要为了"顺手修复"去改 `leaf-base-eureka-server`、`leaf-base-feign`、`leaf-base-webclient`、`leaf-framework` 或根模块配置。
3. 修改开关语义时，必须同时更新 `META-INF/additional-spring-configuration-metadata.json`、`docs/leaf-base-eureka-client开发手册.md` 与本文件第 2 节。
4. 不得引入新的第三方依赖；测试使用 JUnit 5、AssertJ、`ApplicationContextRunner`、`@SpringBootTest` 与 JDK 自带 `com.sun.net.httpserver.HttpServer`。
5. 依赖变更必须视为兼容性变更：模块内不得钉 `spring-cloud-starter-netflix-eureka-client` 版本（由 BOM 管理），`<exclusion>` 与同坐标重声明必须成对出现并带中文注释说明意图，不得删除根 POM 的冲突覆盖。
6. 不得新增 `spring-boot-devtools`、`spring-boot-starter-web`/Tomcat、注册中心服务端（jersey/hk2）等越界依赖。
7. 测试断言不得依赖换行符、缩进或文件字符集：读取文件必须显式 `StandardCharsets.UTF_8`，配置值必须绑定 `Environment` 后断言。
8. 删除冗余注释，保留能解释兼容性约束、开关语义与降级路径的说明。

## 5. 测试要求

当前模块有 23 个用例（`mvn -o -pl leaf-base-eureka-client test`，2026-10-23 全绿，CRLF 与 LF 双平台一致），覆盖：自动装配入口、不自动开启 Feign 扫描、Feign 上下文隔离、`classpath:/application.yml` 不存在、示例文件存在、开关两向、`eureka.client.enabled` 优先级、`defaultZone` 告警与抑制、注册请求真实落地（JDK HttpServer）、宿主配置文件优先、"引入但不注册"三种组合、配置元数据与文档契约、自动装配条件键位。

新增改动必须满足：

1. 配置类或开关变更：补 `@SpringBootTest` 用例覆盖开关两向，断言 Bean 缺失/存在与 `Environment` 取值。
2. 注册行为变更：至少一个用例让真实 HTTP 请求打到本地 `GXTestRegistrationServer`，只断言配置值不算通过。
3. 资源布局变更：补断言证明 `application.yml` 不在 `classpath:/` 根、`examples/` 可被读取。
4. 依赖升级：运行 `mvn -o -pl leaf-base-eureka-client dependency:tree "-Dverbose=true"`，与改动前逐行对比，除被声明修改的那一行外无差异。
5. 提交前至少运行 `mvn -o -pl leaf-base-eureka-client test`；涉及构建期检查时运行 `mvn -o -pl leaf-base-eureka-client clean install -DskipTests`（ErrorProne + NullAway 必须零错误）。
6. 涉及真实注册发现或负载均衡行为时，优先使用接近真实运行形态的 `@SpringBootTest`，而不是反射或字符串断言。

## 6. 变更前检查清单

1. 是否确认本次改动仍然只服务于 Eureka Client 注册发现与客户端调用基础能力？
2. 是否误引入了注册中心服务端职责（`@EnableEurekaServer`、jersey、hk2）？
3. 是否改变了 `@EnableFeignClients` 的默认状态（本模块必须始终不开启）？
4. 是否在 `classpath:/` 根新增了 `application.yml`，或往 jar 里塞了固定域名、IP、端口？
5. 是否让框架重新提供了 `register-with-eureka` 或 `service-url.defaultZone` 默认值？
6. 是否在本模块 jar 内新增了 `spring.cloud.openfeign.*` 或其它越界的默认键？
7. 开关为 `false` 时是否仍然残留 `eureka.*` 值或 Eureka 线程？
8. 新增/修改的测试是否既不依赖换行符也能在 Linux 与 Windows 通过，且未使用 `@Disabled`？
9. 是否只修改了 `leaf-base-eureka-client` 当前 Maven 模块内的文件？

## 7. 提交说明建议

1. 涉及开关、默认值、示例配置调整时，PR 描述要写清旧行为、新行为和受影响应用（尤其"依赖框架默认注册地址"的应用）。
2. 涉及依赖升级时，写明 Spring Cloud Netflix、OpenFeign、LoadBalancer 版本兼容性、执行的 Maven 命令和 `dependency:tree` 对比结论。
3. 如果未能执行测试，写明未执行原因、受影响范围和后续补验建议。
