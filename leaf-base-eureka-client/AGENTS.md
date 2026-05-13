# AGENTS.md - leaf-base-eureka-client

本文档用于指导 `leaf-base-eureka-client` 模块的后续迭代开发。修改本模块时，优先保证 Eureka Client 注册发现行为、Feign 客户端扫描、负载均衡客户端 Bean 和 Spring Boot 集成行为稳定，避免把注册中心服务端逻辑或业务调用编排混入客户端基础模块。

## 通用硬性规则

1. 每次修改只能触碰当前 Maven 模块；确需跨模块时，先说明触发原因、受影响模块、替代方案和最小改动集合。
2. 现有代码可能已在生产环境使用，不得随意改变公开类名、配置键、默认注册发现行为、自动装配入口或 Bean 名称；必须变更时，同步补充兼容说明、迁移路径和回归测试。
3. 在确保原有功能正确的前提下完善实现；修改既有逻辑前，先用测试锁定当前成功、失败、空值、边界和回归路径。
4. 基础框架代码必须优先审查启动性能、线程安全、资源释放和并发可见性；线程池与虚拟线程都能满足需求时，优先使用虚拟线程。
5. 空值处理必须支持 JSpecify 规范：新增或修改 API、配置属性、回调、集合元素和异步结果时，明确 `@NullMarked`、`@Nullable` 或等价约束。
6. 严格遵循测试驱动最佳实践：先写或调整能暴露问题的测试，再实现最小代码变更，最后运行模块级验证命令。
7. 所有 `log` 日志内容必须使用 ASCII 字符，避免运行环境、终端编码或日志采集链路出现乱码。
8. 不要随便把已有代码抽取成单独方法或类；只有抽取后的逻辑至少被两个位置复用，或能显著降低复杂度且不破坏语义时，才允许抽取。
9. 删除方法、类、变量上的冗余注释；清理 Javadoc 中重复、空泛或仅复述签名的信息，只保留语义说明、边界条件、异常约定等必要内容。
10. 控制 `AGENTS.md` 长度与结构，建议保持在 200 行以内；规范继续增加时，在子目录创建领域指南，本文件只保留入口链接和必须遵守的红线。

## 1. 模块定位

`leaf-base-eureka-client` 是 Maple Leaf Framework 中提供 Eureka 客户端接入能力的基础模块，当前职责集中在：

1. 通过 `GXEurekaClientConfig` 启用 `@EnableFeignClients`。
2. 通过 `leaf-base-webclient` 自动装配带 `@LoadBalanced` 的 `WebClient.Builder` 与 `HttpServiceProxyFactory`，用于基于服务名进行客户端负载均衡调用。
3. 引入 `spring-cloud-starter-netflix-eureka-client` 和 `spring-cloud-starter-loadbalancer`，让业务应用接入 Eureka 服务发现。
4. 依赖 `leaf-base-feign` 与 `leaf-base-webclient`，复用框架已有远程调用基础能力。
5. 维护 `application.yml` 中 Eureka Client 连接注册中心的默认示例配置。

本模块不负责：

1. 启动 Eureka Server 注册中心。
2. 定义具体业务 Feign 接口、业务 DTO、业务异常翻译或服务调用编排。
3. 实现服务治理平台、灰度路由、动态权重、实例筛选或注册中心运维能力。
4. 替代业务模块中的超时、重试、鉴权、链路追踪和降级策略。

## 3. 功能基线

开发前必须理解并保持以下行为稳定：

1. `GXEurekaClientConfig` 的核心语义是启用 Feign 客户端扫描并提供负载均衡客户端 Bean，不要加入 Eureka Server 启用逻辑。
2. `@EnableFeignClients` 的扫描范围变化会影响业务 Feign 接口可见性，调整前必须评估下游模块影响。
3. `leaf-base-webclient` 中的 `WebClient.Builder` 必须保持 `@LoadBalanced`，用于按服务名解析实例；不要把负载均衡标记放到不会被 Spring Cloud LoadBalancer 处理的 Bean 上。
4. `HttpServiceProxyFactory` 依赖已配置好的 `WebClient`，修改 Bean 名称、创建顺序或注入方式前必须确认 `leaf-base-webclient` 的契约。
5. `application.yml` 中的 `eureka.client.service-url.defaultZone` 是示例注册中心地址，修改时要检查文档、测试或部署脚本是否依赖旧地址。
6. `feign.hystrix.enabled` 属于历史兼容配置，调整前必须确认当前 Spring Cloud 版本下的实际效果和兼容影响。
7. 客户端模块不应维护业务服务列表、硬编码业务服务名或把调用目标写死在框架层。

## 4. 强制开发规则

1. 所有日志文本必须使用 ASCII 字符；禁止新增或保留中文、全角符号或其他非 ASCII 日志文本。
2. 只在本模块内修改 Eureka Client 接入能力；不要为了“顺手修复”去改 `leaf-base-eureka-server`、`leaf-base-feign`、`leaf-base-webclient` 或根模块配置。
3. 修改 `GXEurekaClientConfig` 时，必须确认自动装配入口、OpenFeign 客户端扫描和 `leaf-base-webclient` 的负载均衡 WebClient 启动行为没有被破坏。
4. 修改 `HttpServiceProxyFactory` 或 WebClient 创建方式时，必须评估 Bean 名称、代理创建方式和客户端负载均衡能力。
5. 修改 `application.yml` 时，必须明确这是示例默认值还是运行时契约；不要把本地机器域名、临时端口或私有环境地址固化为框架默认。
6. 不要在本模块新增业务 Feign 接口、业务服务名常量、业务鉴权拦截器或业务降级处理。
7. 依赖升级必须视为兼容性变更处理，提交说明中写清 Spring Cloud Netflix、LoadBalancer、Feign 和 Spring Boot 版本兼容性。
8. 删除冗余注释，保留能解释兼容性约束、Bean 注入契约和负载均衡行为的说明。

## 5. 测试要求

当前模块没有现成测试。后续凡是改动功能代码、依赖或默认配置，必须补齐对应测试。

1. 配置类变更至少覆盖 Spring Boot 上下文能加载 `GXEurekaClientConfig`。
2. WebClient 相关变更至少覆盖 Bean 存在、`WebClient.Builder` 带 `@LoadBalanced` 能力，以及 `httpServiceProxyFactory` 注入契约未破坏。
3. `@EnableFeignClients` 相关变更至少覆盖 Feign 客户端扫描行为符合预期。
4. `application.yml` 变更至少覆盖关键配置值：`feign.hystrix.enabled`、`eureka.client.register-with-eureka` 和 `service-url.defaultZone`。
5. 依赖升级后至少运行 `mvn -pl leaf-base-eureka-client test`；如果测试会触发集成环境限制，提交说明中写明未执行原因和补验建议。
6. 涉及真实注册发现或负载均衡行为时，优先使用接近真实容器行为的 `@SpringBootTest`，而不是只做字符串或反射断言。

## 6. 变更前检查清单

1. 是否确认本次改动仍然只服务于 Eureka Client 注册发现和客户端调用基础能力？
2. 是否误引入了 `@EnableEurekaServer` 或注册中心服务端职责？
3. 是否改变了 `@EnableFeignClients` 的扫描语义？
4. 是否破坏了 `@LoadBalanced WebClient.Builder` 或 `httpServiceProxyFactory` 注入契约？
5. 是否改变了 `defaultZone`、`register-with-eureka` 或 `feign.hystrix.enabled` 的默认语义？
6. 是否引入了业务服务名、业务 Feign 接口、业务鉴权或业务降级逻辑？
7. 是否新增了非 ASCII 日志内容？
8. 是否补齐了与改动点对应的测试，或在提交说明中明确未能执行的原因？
9. 是否只修改了 `leaf-base-eureka-client` 当前 Maven 模块内的文件？

## 7. 提交说明建议

1. 涉及客户端注册发现、Feign 扫描、负载均衡 Bean 或默认注册中心地址调整时，PR 描述要写清旧行为、新行为和影响范围。
2. 涉及依赖升级时，写明 Spring Cloud Netflix、LoadBalancer、Feign 版本兼容性、执行的 Maven 命令和关键验证结果。
3. 如果未能执行测试，写明未执行原因、受影响范围和后续补验建议。
