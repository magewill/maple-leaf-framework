# AGENTS.md (leaf-base-framework)

## 模块定位

`leaf-base-framework` 是 Maple Leaf Framework 的基础核心模块，负责提供跨业务复用的框架能力：

- 通用注解与 AOP，如业务日志、参数校验、耗时统计。
- Web 横切能力，如 `RequestBodyAdvice`、`ResponseBodyAdvice`、Filter、Interceptor。
- 基础 DTO、统一返回结构、状态码、异常模型。
- Caffeine / Spring Cache 基础配置、事件、上下文、MDC 透传、通用工具、DDD 基础接口。

本模块不要引入具体业务域规则、业务流程编排或业务常量。

## 缓存规范

- 缓存注解统一使用 Spring Cache：`@Cacheable`、`@CacheEvict`、`@CachePut`、`@Caching`。
- 本模块只维护 Spring Cache 所需的基础设施，如 `CaffeineCacheManager`、`CachingConfigurer` 和缓存配置属性。
- 不再新增 Leaf 自定义缓存注解或对应切面，避免与 Spring Boot 缓存机制重复。
- 需要分布式缓存时，优先通过 Spring Cache 的 `CacheManager` 扩展或现有 `GXBaseCacheService` 实现接入。

## 目录职责

- `annotation`：框架通用注解定义，不包含自定义缓存注解。
- `aspect`：通用横切逻辑，如业务日志、参数校验、耗时统计。
- `config`：框架配置，如异步、CORS、Filter、缓存、Web 适配。
- `service` 和 `service.impl`：可替换的框架服务 SPI 与默认实现。
- `web`、`filter`、`handler`：Web 层横切增强与请求处理辅助。
- `dto`、`api.dto`、`code`、`exception`：协议对象、状态码、异常模型。
- `event`：事件总线与事件处理基础设施。
- `util`、`wrapper`：通用工具与 MDC 包装器。
- `ddd`：DDD 基础抽象。

## 开发约束

- 对外注解、DTO 字段、公共方法签名默认视为稳定契约，修改前先评估影响面。
- 新增能力优先复用 Spring Boot / Spring Framework 标准机制。
- 新增框架能力前，必须先判断是否与 Spring Boot 本身已经提供的功能重复；若重复，优先使用 Spring Boot 原生能力或基于其扩展点增强。
- 配置类保持可读、可覆盖、可观测，避免硬编码环境值。
- 涉及请求上下文的逻辑必须考虑异步线程和非 Web 调用。
- 涉及 ThreadLocal / MDC 的代码必须有清理路径。
- 禁止提交真实密钥、令牌、生产地址或密码。

## 测试建议

- 修改 `aspect`、`web.advice`、`filter`、`wrapper.mdc` 时，补充或更新对应单测。
- 修改公共 DTO 或协议对象时，至少验证序列化、反序列化或核心字段兼容性。
- 最小验证命令：`mvn -pl leaf-base-framework test`。

