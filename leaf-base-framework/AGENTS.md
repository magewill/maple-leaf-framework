# AGENTS.md (leaf-base-framework)

## 1. 模块角色与边界

### 模块定位
`leaf-base-framework` 是 Maple Leaf Framework 的基础核心模块，提供跨业务复用的框架能力：
- 通用注解与 AOP（缓存、业务日志、参数校验、耗时统计）
- Web 横切能力（`RequestBodyAdvice` / `ResponseBodyAdvice` / Filter / Interceptor）
- 基础 DTO 与统一返回结构
- 缓存、事件、上下文、线程池 MDC 透传、工具类、DDD 基础接口

### 边界约束
- 允许：通用框架能力、横切能力、基础抽象、基础配置。
- 禁止：引入具体业务域规则、业务流程编排、强业务语义常量。
- 对外能力优先通过 `service` 接口、注解或工具类暴露，避免散落的隐式行为。

---

## 2. 技术栈与版本基线

- JDK: 21
- Spring Boot: 4.x（当前仓库父 POM）
- 核心依赖：Spring Web / Validation / AOP / Actuator
- 缓存：Caffeine
- 工具：Hutool、Guava、Jackson、Lombok
- 构建：Maven

新增依赖前必须先确认：
1. 本模块是否已有等价能力可复用；
2. 是否会增加上层模块耦合或启动负担；
3. 是否与当前 Spring/Jakarta 体系冲突。

---

## 3. 目录职责（按现有代码）

- `annotation`：框架注解定义（如 `@GXEnableLeafFramework`、`@GXCacheable`、`@GXBusinessLog`）。
- `aspect`：注解对应切面逻辑（缓存、日志、参数校验、StopWatch）。
- `web` / `filter` / `handler`：Web 层横切增强与请求处理辅助。
- `config`：框架配置（异步、CORS、Filter、缓存、Web 适配）。
- `service` + `service.impl`：可替换的框架服务 SPI 与默认实现。
- `dto` / `api.dto` / `code` / `exception`：协议对象、状态码、异常模型。
- `event`：事件总线与事件处理基础设施。
- `util` / `wrapper`：通用工具与 MDC 包装器。
- `ddd`：DDD 基础抽象（factory/gateway/repository/publisher/presenter）。

新增代码必须放到语义匹配的包中，不要把“工具逻辑”塞进 `config`，也不要把“配置装配”塞进 `util`。

---

## 4. 现有能力优先级（开发时优先复用）

1. 返回结果：优先复用 `GXResultUtils` 与现有 `dto.res` 结构。
2. 缓存能力：优先使用 `@GXCacheable` / `@GXCacheEvict` + 既有配置。
3. 请求/响应拦截扩展：优先实现 `GXRequestBodyAdviceService`、`GXResponseBodyAdviceService`，避免直接改 Advice 主流程。
4. 上下文访问：优先用 `GXCurrentRequestContextUtils`、`GXSpringContextUtils`。
5. 异步与链路追踪：优先用 `GXMdcThreadUtils` 和 `wrapper.mdc` 下的执行器包装。
6. 事件：优先走现有 event center 与 publisher 工具，不重复造轮子。

---

## 5. 编码规范（针对本模块）

### 5.1 兼容性与 API 稳定性
- 对外注解、DTO 字段、常量键名、公共方法签名默认视为“稳定契约”，修改需评估影响面。
- 非必要不重命名公共类/方法，不轻易改变默认行为。
- 新增行为优先“向后兼容”策略：默认关闭、配置开启，或保持旧逻辑不变。

### 5.2 Spring 与注入约定
- 与现有文件风格保持一致；修改哪个文件，就延续该文件现有注入风格与注解体系。
- 配置类保持“可读、可覆盖、可观测”，避免硬编码环境值。
- 读取配置优先通过已有 `properties` 类或 `GXCommonUtils.getEnvironmentValue` 一致入口。

### 5.3 AOP / Advice / Filter 约束
- 切面与拦截逻辑必须保证失败可诊断（必要日志）且不吞核心异常语义。
- `RequestBodyAdvice` / `ResponseBodyAdvice` 变更时，优先保留现有扩展点委派逻辑。
- 涉及请求上下文的逻辑必须考虑空上下文场景（异步线程、非 Web 调用）。

### 5.4 并发与上下文
- 涉及线程池任务时，必须明确 MDC 透传与清理，防止上下文污染。
- ThreadLocal 相关代码必须有清理路径（`finally` 或统一收口）。

### 5.5 安全与数据处理
- 涉及 Token、Cookie、签名、敏感字段时，优先复用现有工具与服务接口。
- 禁止在代码中写死真实密钥、真实环境凭证。

---

## 6. 测试策略（按风险分层）

### 必做
- 修改 `aspect`、`web.advice`、`filter`、`wrapper.mdc` 任何逻辑时，补充或更新对应单测。
- 修改公共 DTO/协议对象时，至少验证序列化/反序列化或核心字段兼容性。

### 优先参考现有测试目录
- `src/test/java/cn/maple/core/framework/wrapper/mdc`
- `src/test/java/cn/maple/core/framework/event`
- `src/test/java/cn/maple/core/framework/filter`
- `src/test/java/cn/maple/core/framework/converter`

### 建议执行
- 最小验证：`mvn -pl leaf-base-framework test`
- 若改动影响跨模块契约，再执行根工程相关联模块测试。

---

## 7. 提交流程与变更说明

每次提交建议在描述中写清：
1. 改了什么能力；
2. 为什么这样改（兼容性/性能/可维护性）；
3. 风险点与回滚点；
4. 测试覆盖范围。

若改动公共注解、公共 DTO、拦截链路，请额外给出“对接方是否需要调整”的结论。

---

## 8. 明确禁止（红线）

1. 在本模块引入业务特定逻辑（例如订单、用户域规则）。
2. 未评估影响直接修改公共注解语义或返回协议字段含义。
3. 在并发代码中引入未清理的 ThreadLocal/MDC 上下文。
4. 在配置或代码中提交明文密钥、令牌、生产地址密码。
5. 为了局部需求绕过现有扩展点，直接硬改全局主流程。

