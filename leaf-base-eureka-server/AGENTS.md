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
3. 维护 `application.yml` 中注册中心自身不注册、不拉取注册表以及集群 `defaultZone` 的默认示例配置。
4. 作为注册中心启动模块，不承载具体业务服务的注册、发现、远程调用、熔断降级或业务配置逻辑。

本模块不负责：

1. 声明业务服务的 Eureka Client 注册配置。
2. 提供 Feign 客户端、WebClient 客户端或服务调用编排。
3. 维护业务实例元数据、灰度规则、路由规则或业务健康检查策略。
4. 替代运维环境中的 Eureka 集群部署、域名解析、证书、鉴权或网络隔离配置。

## 3. 功能基线

开发前必须理解并保持以下行为稳定：

1. `GXEurekaServerConfig` 的核心语义是启用 Eureka 注册中心，不要加入业务服务注册、客户端扫描或远程调用配置。
2. Eureka Server 默认不向自身注册：`eureka.client.register-with-eureka` 应保持为 `false`，除非明确改为集群互注册场景并说明影响。
3. Eureka Server 默认不拉取注册表：`eureka.client.fetch-registry` 应保持为 `false`，除非明确需要作为集群成员拉取注册信息。
4. `service-url.defaultZone` 中的地址属于示例集群配置，修改时要同时检查 README、部署脚本或测试是否依赖旧地址。
5. 自动装配入口必须加载 `cn.maple.eureka.server.config.GXEurekaServerConfig`，不能指向 Feign 或 Client 模块配置。
6. 注册中心模块不应主动启用 `@EnableFeignClients`、`@LoadBalanced`、业务 `RestTemplate`、业务 `WebClient` 或客户端负载均衡 Bean。
7. 新增配置属性时，优先使用 Spring Boot 原生 `eureka.*` 语义，不要发明与 Eureka 原生配置重复的自定义键。

## 4. 强制开发规则

1. 所有日志文本必须使用 ASCII 字符；禁止新增或保留中文、全角符号或其他非 ASCII 日志文本。
2. 只在本模块内修改注册中心服务端能力；不要为了“顺手修复”去改 `leaf-base-eureka-client`、`leaf-base-feign` 或根模块配置。
3. 修改自动装配文件时，必须同时检查配置类包名、类名和 Maven 产物是否一致。
4. 修改 `application.yml` 时，必须明确这是示例默认值还是运行时契约；不要把本地机器域名、临时端口或私有环境地址固化为框架默认。
5. 不要把注册中心配置类演变成业务 Bean 聚合入口。业务调用、鉴权、过滤、熔断和链路追踪能力应下沉到对应业务模块或客户端模块。
6. 依赖升级必须视为兼容性变更处理，提交说明中写清 Spring Cloud Netflix 版本、Spring Boot 版本兼容性和已执行的验证命令。
7. 删除冗余注释，保留能解释兼容性约束、自动装配入口和示例配置意图的说明。

## 5. 测试要求

当前模块没有现成测试。后续凡是改动功能代码、自动装配入口、依赖或默认配置，必须补齐对应测试。

1. 配置类变更至少覆盖 Spring Boot 上下文能加载 `GXEurekaServerConfig`。
2. 自动装配入口变更至少覆盖 `AutoConfiguration.imports` 中的类存在且可加载。
3. `application.yml` 变更至少覆盖关键配置值：`register-with-eureka`、`fetch-registry`、`service-url.defaultZone` 和 `instance.hostname`。
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
