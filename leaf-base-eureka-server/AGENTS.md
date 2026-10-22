# AGENTS.md - leaf-base-eureka-server

本文档用于指导 `leaf-base-eureka-server` 模块的后续迭代开发。修改本模块时，优先保证 Eureka Server 的注册中心职责、集群互注册配置、Spring Boot 自动装配入口和默认运行配置稳定，避免把业务服务治理逻辑堆进注册中心模块。

## 通用硬性规则

1. 每次修改只能触碰当前 Maven 模块；确需跨模块时，先说明触发原因、受影响模块、替代方案和最小改动集合。
2. 现有代码可能已在生产环境使用，不得随意改变公开类名、配置键、默认注册中心地址、自动装配入口或启动行为；必须变更时，同步补充兼容说明、迁移路径和回归测试。
3. 在确保原有功能正确的前提下完善实现；修改既有逻辑前，先用测试锁定当前成功、失败、空值、边界和回归路径。
4. 基础框架代码必须优先审查启动性能、线程安全、资源释放和并发可见性；线程池与虚拟线程都能满足需求时，优先使用虚拟线程。
5. 空值处理必须支持 JSpecify 规范：新增或修改 API、配置属性、回调、集合元素和异步结果时，明确 `@NullMarked`、`@Nullable` 或等价约束。
6. 严格遵循测试驱动最佳实践：先写或调整能暴露问题的测试，再实现最小代码变更，最后运行模块级验证命令。
7. 所有 `log` 日志内容必须使用 ASCII 字符，避免运行环境、终端编码或日志采集链路出现乱码。
8. 不要随便把已有代码抽取成单独方法或类；只有抽取后的逻辑至少被两个位置复用，或能显著降低复杂度且不破坏语义时，才允许抽取。
9. 删除方法、类、变量上的冗余注释；清理 Javadoc 中重复、空泛或仅复述签名的信息，只保留语义说明、边界条件、异常约定等必要内容。
10. 控制 `AGENTS.md` 长度与结构，建议保持在 200 行以内；规范继续增加时，在子目录创建领域指南，本文件只保留入口链接和必须遵守的红线。

## 1. 模块定位

`leaf-base-eureka-server` 是 Maple Leaf Framework 中提供 Eureka 注册中心服务端接入能力的基础模块，当前职责集中在：

1. 通过 `GXEurekaServerConfig` 启用 `@EnableEurekaServer`。
2. 提供 Eureka Server 相关依赖边界，包括 `spring-cloud-starter-netflix-eureka-server`。
3. 维护 `src/main/resources/examples/` 下的单机与集群示例配置（`eureka-server-standalone.yml` 保留"不注册、不拉取"的单机基线，`eureka-server-peer.yml` 提供集群互注册的占位符配置）。
4. 作为注册中心启动模块，不承载具体业务服务的注册、发现、远程调用、熔断降级或业务配置逻辑。

本模块不负责：

1. 声明业务服务的 Eureka Client 注册配置。
2. 提供 Feign 客户端、WebClient 客户端或服务调用编排。
3. 维护业务实例元数据、灰度规则、路由规则或业务健康检查策略。
4. 替代运维环境中的 Eureka 集群部署、域名解析、证书、鉴权或网络隔离配置。

## 2. 启用方式与默认值

1. 本模块默认**不启用**注册中心：只有显式设置 `maple.framework.enable.eureka-server=true` 才会装配 `@EnableEurekaServer`（`matchIfMissing=false`）。
2. 仅在 Servlet 应用中生效（`@ConditionalOnWebApplication(type = SERVLET)`）；WebFlux 或非 Web 应用不会启用注册中心。
3. 只要应用不是"Servlet 注册中心"（开关关闭，或开关开启但应用为 WebFlux/非 Web），`GXEurekaServerClientDefaultsPostProcessor` 就会把 `eureka.client.enabled` 以最低优先级默认置为 `false`，避免 starter 自带的客户端自动注册在注册中心不可达时导致应用启动失败；应用显式配置 `eureka.client.enabled` 时以其值为准。
4. 应用是"Servlet 注册中心"时，同一后置处理器以最低优先级补充单机语义默认值 `eureka.client.register-with-eureka=false` 与 `eureka.client.fetch-registry=false`；应用（或集群示例）显式设为 `true` 时以显式值为准。
5. 模块 jar 内**不再提供** `classpath:/application.yml`；单机与集群示例位于 `src/main/resources/examples/`，由 `docs/leaf-base-eureka-server开发手册.md` 引用。
6. 聚合门面 `leaf-framework` 不再传递本模块，注册中心应用需显式声明依赖。

## 3. 功能基线

开发前必须理解并保持以下行为稳定：

1. `GXEurekaServerConfig` 的核心语义是启用 Eureka 注册中心，不要加入业务服务注册、客户端扫描或远程调用配置。
2. Eureka Server 默认不向自身注册：`eureka.client.register-with-eureka` 在 Servlet 注册中心场景由 `GXEurekaServerClientDefaultsPostProcessor` 以最低优先级默认置为 `false`；集群互注册场景必须显式设为 `true` 并说明影响。
3. Eureka Server 默认不拉取注册表：`eureka.client.fetch-registry` 同上以最低优先级默认 `false`；集群成员必须显式设为 `true`。
4. `service-url.defaultZone` 中的地址属于示例集群配置，修改时要同时检查 README、部署脚本或测试是否依赖旧地址。
5. 自动装配入口必须加载 `cn.maple.eureka.server.config.GXEurekaServerConfig`，不能指向 Feign 或 Client 模块配置。
6. 注册中心模块不应主动启用 `@EnableFeignClients`、`@LoadBalanced`、业务 `RestTemplate`、业务 `WebClient` 或客户端负载均衡 Bean。
7. 新增配置属性时，优先使用 Spring Boot 原生 `eureka.*` 语义，不要发明与 Eureka 原生配置重复的自定义键。
8. 注册中心默认不启用，开关为 `maple.framework.enable.eureka-server`（默认 `false`）；不得改回"引入即启用"。
9. `src/main/resources` 根目录不得再出现 `application.yml`；示例配置只能放在 `examples/` 子目录，避免与宿主应用同名配置竞争。
10. `jersey-apache-connector`（Jersey 3）是 `eureka-core-jersey3` 复制客户端的硬依赖，不得移除或替换为 `jersey-apache5-connector`（会导致 `NoClassDefFoundError: ApacheConnectorProvider`）。

## 4. 强制开发规则

1. 所有日志文本必须使用 ASCII 字符；禁止新增或保留中文、全角符号或其他非 ASCII 日志文本。
2. 只在本模块内修改注册中心服务端能力；不要为了“顺手修复”去改 `leaf-base-eureka-client`、`leaf-base-feign` 或根模块配置。
3. 修改自动装配文件时，必须同时检查配置类包名、类名和 Maven 产物是否一致。
4. 修改 `src/main/resources/examples/` 下的示例配置时，必须明确这是示例值而非运行时契约；示例中只允许出现占位符（`${ENV:default}`），禁止固化本地机器域名、私有地址或固定端口到默认配置。
5. 不要把注册中心配置类演变成业务 Bean 聚合入口。业务调用、鉴权、过滤、熔断和链路追踪能力应下沉到对应业务模块或客户端模块。
6. 依赖升级必须视为兼容性变更处理，提交说明中写清 Spring Cloud Netflix 版本、Spring Boot 版本兼容性和已执行的验证命令。
7. 删除冗余注释，保留能解释兼容性约束、自动装配入口和示例配置意图的说明。

## 5. 测试要求

模块现有 23 个用例，分六类：条件装配矩阵（`GXEurekaServerConfigTest`，6 个）、示例资源配置契约（`GXEurekaServerExamplesTest`，3 个）、开关真实启动（`GXEurekaServerSwitchSpringBootTest`，4 个：开关缺省 404、开关 `true` 200、开关缺省且未配置任何 `eureka.client.*` 时应用正常启动且客户端默认禁用、只设开关时单机默认不注册不拉取）、真实注册中心启动与注册往返（`GXEurekaServerSpringBootTest`，2 个）、双节点复制探针（`GXEurekaServerPeerClusterSpringBootTest`，2 个）、客户端默认值策略（`GXEurekaServerClientDefaultsPostProcessorTest`，6 个：开关缺省禁用客户端、Servlet 注册中心保留客户端、reactive + 开关仍禁用、显式客户端配置优先、Servlet 注册中心补单机默认、显式注册配置优先）。后续凡是改动功能代码、自动装配入口、依赖或默认配置，必须补齐或调整对应测试。

1. 配置类变更至少覆盖 Spring Boot 上下文能加载 `GXEurekaServerConfig`。
2. 自动装配入口变更至少覆盖 `AutoConfiguration.imports` 中的类存在且可加载。
3. `src/main/resources/examples/` 下的示例配置变更至少覆盖关键配置值：`register-with-eureka`、`fetch-registry`、`service-url.defaultZone` 和 `instance.hostname`（由 `GXEurekaServerExamplesTest` 断言，同时校验 classpath 根不存在 `application.yml`）。
4. 依赖升级后至少运行 `mvn -pl leaf-base-eureka-server test`；如果测试会触发集成环境限制，提交说明中写明未执行原因和补验建议。
5. 涉及真实 Eureka Server 启动行为时，优先使用接近真实容器行为的 `@SpringBootTest`，而不是只做字符串或反射断言。

## 6. 变更前检查清单

1. 是否确认本次改动仍然只服务于 Eureka Server 注册中心职责？
2. 是否检查了 `AutoConfiguration.imports` 没有误指向其他模块配置？
3. 是否改变了 `register-with-eureka`、`fetch-registry`、`defaultZone` 或 `hostname` 的默认语义？
4. 是否引入了业务调用、Feign、WebClient、鉴权、熔断或路由逻辑？
5. 是否新增了非 ASCII 日志内容？
6. 是否补齐了与改动点对应的测试，或在提交说明中明确未能执行的原因？
7. 是否只修改了 `leaf-base-eureka-server` 当前 Maven 模块内的文件？

## 7. 提交说明建议

1. 涉及注册中心启动、自动装配或默认集群地址调整时，PR 描述要写清旧行为、新行为和影响范围。
2. 涉及依赖升级时，写明 Spring Cloud Netflix 版本兼容性、执行的 Maven 命令和关键验证结果。
3. 如果未能执行测试，写明未执行原因、受影响范围和后续补验建议。
