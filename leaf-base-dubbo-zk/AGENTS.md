# AGENTS.md - leaf-base-dubbo-zk

本文档用于指导 `leaf-base-dubbo-zk` Maven 模块的后续迭代开发。修改本模块时，优先保证 Dubbo Provider 侧异常处理语义、Spring Boot Web 场景下 Dubbo 调用异常兜底行为，以及 Dubbo SPI 资源注册的一致性。

本模块已有代码已经在生产环境使用。任何修改都必须以保持现有功能正确性、对外接口兼容性、运行性能和线程安全性为前提。

## 通用硬性规则

1. 每次修改只能触碰当前 Maven 模块；确需跨模块时，先说明触发原因、受影响模块、替代方案和最小改动集合。
2. 现有代码已经在生产环境使用，不得随意更改对外接口规范、配置语义、默认行为、异常语义或序列化契约；必须变更时，同步补充兼容说明、迁移路径和回归测试。
3. 在确保原有功能正确的前提下完善实现；修改既有逻辑前，先用测试锁定当前成功、失败、空值、边界和回归路径。
4. 基础框架代码必须优先审查性能、线程安全、资源释放和并发可见性；线程池与虚拟线程都能满足需求时，优先使用虚拟线程。
5. 空值处理必须支持 JSpecify 规范：新增或修改 API、DTO、配置属性、回调、集合元素和异步结果时，明确 `@NullMarked`、`@Nullable` 或等价约束，让空值语义可被 IDE、编译检查和测试验证。
6. 严格遵循测试驱动最佳实践：先写或调整能暴露问题的测试，再实现最小代码变更，最后运行模块级验证命令。
7. 涉及改动的点都要检查测试用例是否完整；覆盖不足时补齐成功、失败、异常、空值、边界和回归场景。
8. 功能只有在 Spring Boot 应用运行后才能验证时，使用 `@SpringBootTest` 或等价方式模拟真实启动后的 Bean 装配、条件配置、AOP、监听器、异步流程和配置绑定。
9. 修改线程安全或性能相关代码时，必须补充严格测试；覆盖并发访问、重复执行、超时、取消、资源释放和关键指标，无法自动化时在提交说明写清手工压测命令、样本规模和观察指标。
10. 所有 `log` 日志内容必须使用 ASCII 字符，避免不同运行环境、终端编码或日志采集链路出现乱码。
11. 不要随便把已存在代码抽取成单独方法或类；只有抽取后的逻辑至少被两个位置复用，或能显著降低复杂度且不破坏语义时，才允许抽取。
12. 删除方法、类、变量上的冗余注释；清理 Javadoc 中重复、空泛或仅复述签名的信息，只保留语义说明、边界条件、异常约定等必要内容。
13. 复杂方法、复杂类、公共扩展点或使用方式不直观的能力，应补充必要 Javadoc；使用门槛较高时，给出简短使用示例。
14. 精准命令胜过陈词滥调：直接给出 `mvn -pl 当前模块 test`、`rg "类名|方法名" 当前模块/src/test` 这类具体步骤，避免“确保代码质量”“提升项目可维护性”等抽象套话。
15. 控制 `AGENTS.md` 长度与结构，建议保持在 200 行以内；规范继续增多时，在子目录创建领域指南，本文件只保留入口链接和必须遵守的红线。
16. 持续优化迭代：`AGENTS.md` 是动态文档；当 Agent 的实际输出与预期目标出现偏差时，把偏差改写成下一次可执行、可检查的规则。

## 1. 模块定位

`leaf-base-dubbo-zk` 是 Maple Leaf Framework 中基于 Dubbo 和 Zookeeper 注册中心的 RPC 基础支持模块。按照当前代码实现，本模块提供以下能力：

1. Dubbo 异常过滤与分类处理（Provider/Consumer 双侧均可装配）：`GXDubboExceptionFilter`
2. Consumer 侧 TraceId 透传与响应回填：`GXDubboClientTraceIdFilter`
3. Provider 侧 TraceId 恢复与响应回填：`GXDubboServerTraceIdFilter`
4. TraceId 跨边界附件选择：`GXPenetrateAttachmentSelector`
5. Web 层 Dubbo 异常统一返回兜底：`GXDubboCallExceptionHandler`
6. `ServiceBean` 启动期绑定增强：`GXDubboRpcApiBeanPostProcessor`
7. Provider filter 装配自检：`GXDubboProviderFilterValidator`
8. Dubbo SPI 扩展声明：`src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Filter`、`org.apache.dubbo.rpc.PenetrateAttachmentSelector`

本模块当前不承载以下职责：

1. Sentinel 控制台规则管理、原生指标上报（只做异常语义转换）
2. Zookeeper 注册中心端到端服务发现（由 `dubbo-zookeeper-curator5-spring-boot-starter` 提供）
3. Dubbo 鉴权 filter、消费者路由策略

以上能力如果后续引入，必须同步更新本文档。

## 2. 修改边界

1. 每次修改只能修改当前 Maven 模块：`leaf-base-dubbo-zk`。
2. 不得修改其他 Maven 模块的源码、测试、资源或构建配置，除非用户明确要求并重新确认范围。
3. 不得随意更改本模块对外接口规范，包括公开类名、方法签名、异常类型、SPI 扩展名、资源路径、返回结构和错误语义。
4. 修改已经存在的逻辑时，必须先理解现有行为，再在确保原有功能正确性的条件下完善；不得为了简化实现而改变线上依赖的正确行为。
5. 由于本模块属于基础框架，性能、线程安全性和运行时兼容性必须作为一等约束处理。

## 3. 目录职责

1. `src/main/java/cn/maple/dubbo/zk/filter`
   负责 Dubbo Filter 扩展：`GXDubboExceptionFilter`、`GXDubboClientTraceIdFilter`、`GXDubboServerTraceIdFilter`。
2. `src/main/java/cn/maple/dubbo/zk/handler`
   负责 Spring MVC 场景中的异常统一处理，当前核心类为 `GXDubboCallExceptionHandler`。
3. `src/main/java/cn/maple/dubbo/zk/selector`
   负责跨边界附件选择，当前核心类为 `GXPenetrateAttachmentSelector`（同时持有 `appName` 静态缓存）。
4. `src/main/java/cn/maple/dubbo/zk/processor`
   负责 `ServiceBean` 启动期绑定增强，当前核心类为 `GXDubboRpcApiBeanPostProcessor`。
5. `src/main/java/cn/maple/dubbo/zk/config`
   负责启动期装配自检，当前核心类为 `GXDubboProviderFilterValidator`。
6. `src/main/resources/META-INF/dubbo`
   负责 Dubbo SPI 资源声明，属于运行时装配基线。类名、扩展名、资源路径发生变化时必须同步调整并验证。

## 4. 功能基线

开发前必须理解并保持以下行为稳定：

1. **强制装配**：`dubbo.provider.filter` 必须包含 `dubboServerTraceIdFilter,dubboExceptionFilter,-exception`；缺失 `-exception` 时内置 `ExceptionFilter` 会把 `GXBusinessException` 包装成普通 `RuntimeException` 并丢失 `code/msg/data`。`GXDubboProviderFilterValidator` 会在启动期输出 ERROR 告警（不阻断启动）。consumer 侧推荐 `dubboClientTraceIdFilter,dubboExceptionFilter,-exception`。
2. `GXDubboExceptionFilter` 的判定顺序即对外契约：`GXSentinelFlowException` 透传 → Sentinel 阻断异常转换 → checked 异常透传 → MyBatis 透传 → `GXBusinessException` 透传 → 声明异常透传 → `java.`/`jakarta.` 与 `RpcException` 透传 → 其余运行时异常包装为 `GXBusinessException(500)`。
3. Sentinel 识别条件为三选一：类名以 `com.alibaba.csp.sentinel.slots.block.` 前缀、可赋值给 `BlockException`、消息包含完整常量 `SentinelBlockException: FlowException`；判定会**遍历 `cause` 链**，因此 `GXBusinessException` 的 cause 链含 `BlockException` 子类时会被转换为限流异常并丢弃业务码（既有顺序，用例 `convertsBusinessExceptionWhoseCauseIsBlockException` 锁定）。**禁止**使用裸 `"FlowException"` 子串判定（会误判业务异常）。转换结果附带 `X-B3-TraceId`、`errorType`、`interfaceName`、`methodName`、`argumentCount`、`parameterTypes`，**不得**携带参数值。
4. 方法签名无法反射解析时不得提前返回（fail-closed）：未声明运行时异常仍需包装并记录 ERROR；filter 自身异常只允许 WARN + 内部计数。
5. 两个 TraceId filter 必须在 `finally` 中恢复 MDC，异步结果通过 `whenCompleteWithContext` 二次捕获/恢复；异常 filter 处于 Dubbo `Filter.Listener` 阶段，traceId 必须优先从 invocation 附件读取（此时 MDC 已恢复）。
6. `GXPenetrateAttachmentSelector` 的 `appName` 是共享静态缓存（`volatile` + `resetAppNameCache()`），filter 与 selector 必须共用 `getCachedAppName()`，核心链路不得输出 INFO 日志。
7. `GXDubboCallExceptionHandler` 显式处理 `RpcException` 与 `GXSentinelFlowException`，并通过 `@Order(HIGHEST_PRECEDENCE)` 优先于继承自 `GXExceptionHandler` 的兜底 `Exception` handler；`data` 只返回副本，不得修改异常自身。
8. `GXPenetrateAttachmentSelector` 只透传 `X-B3-TraceId` 一项；consumer 与 provider 两侧均写 `clientAttachment`/`serverAttachment` 属既定契约，禁止以“语义更干净”为由删除。
9. Dubbo SPI 资源文件与 `security/serialize.allowlist` 是模块运行时注册入口。任何包名、类名、扩展名调整，都必须同步视为一等变更点。

## 5. 强制开发规则

1. 所有 `log` 日志中的内容都必须使用 ASCII 字符。新增或修改日志时，禁止写入中文、全角符号或其他非 ASCII 文案。
2. 涉及到改动的点，都必须检查测试用例是否完整。没有测试、测试失效或覆盖不足时，必须先补齐对应测试。
3. 严格遵循测试驱动最佳实践：先明确待保护行为和失败场景，再补测试，再实现或调整代码，最后运行相关测试验证。
4. 不要随便将已存在的代码抽取成单独的方法或者类，除非抽取出来的方法或类可以被两个以上的地方复用，并且确实降低理解或维护成本。
5. 将方法、类、变量上的冗余注释去掉。命名已经表达清楚的内容，不要再重复解释。
6. 清理 Javadoc 中的冗余信息，只保留必要文档。复杂方法、复杂类、异常转换规则、SPI 扩展点应适当添加 Javadoc；如果使用方式较复杂，可以补充最小使用实例，便于使用者参考。
7. 修改 `ExceptionFilter`、`RestControllerAdvice`、SPI 资源映射时，优先保持既有扩展入口、触发时机和返回契约不变。
8. 修改异常转换逻辑时，必须明确哪些异常继续透传，哪些异常需要包装，哪些异常需要记录日志，禁止静默改变线上错误分类。
9. 修改 Dubbo 运行时装配相关内容时，必须同时检查 Java 实现、资源文件、`pom.xml` 依赖是否仍然一致。
10. 在线程池与虚拟线程都能满足需求的场景中，优先使用虚拟线程；如果仍选择线程池，必须有明确理由，例如兼容性、生命周期管理或第三方组件限制。
11. 不得引入会破坏线程安全的共享可变状态。必须共享状态时，应明确并发访问边界和同步策略。
12. 不得为了局部风格统一做无业务价值的重构，尤其不得在没有测试保护的情况下调整生产路径上的核心逻辑。

## 6. 测试要求

模块当前已有 **14 个测试类 / 83 条用例**（`mvn -o -pl leaf-base-dubbo-zk test`，0 失败 0 跳过）。后续凡是修改本模块功能代码，都必须在既有基线上补齐与变更点对应的测试，**不得删除或禁用既有用例**。

1. 测试用例要覆盖全面，至少覆盖成功路径、失败路径、异常路径、空值路径和边界条件。
2. 如果功能需要在 Spring Boot 应用运行的情况下才能测试，那么测试用例应模拟真实 Spring Boot 应用启动之后再测试，优先使用 `@SpringBootTest` 或等价的真实容器启动方式。
3. 修改 `GXDubboExceptionFilter` 时，至少要覆盖：
   - `GXBusinessException` 直接透传
   - 方法签名已声明异常直接透传
   - `GenericService` 场景跳过处理
   - Sentinel 限流异常文案转换为 `GXSentinelFlowException`
   - MyBatis 异常、`RpcException`、JDK/Jakarta 异常的原样透传
   - 未声明运行时异常的日志记录与最终异常设置
4. 修改 `GXDubboCallExceptionHandler` 时，至少要覆盖：
   - `RpcException` 的响应结果
   - 与 `GXExceptionHandler` 继承链的协同行为
   - 真实 Spring Boot Web 容器启动后的异常拦截效果
5. 修改 TraceId 链路、selector 或 `RpcContext` 写入行为时，至少要覆盖：来源优先级、MDC 恢复（正常/异常/异步）、同一次调用单一 traceId、附件写入位置。
6. 修改启动期装配或 `GXDubboRpcApiBeanPostProcessor` 时，至少要覆盖：`-exception` 缺失告警、`ref` 为空、泛型解析失败、目标 Bean 缺失、多候选、绑定未生效。
7. 现有测试类职责：`GXDubboExceptionFilterTest`（23）、`GXDubboTraceIdFilterTest`（10）、`GXPenetrateAttachmentSelectorTest`（6）、`GXDubboCallExceptionHandlerTest`（13）、`GXDubboCallExceptionHandlerSpringBootTest`（3）、`GXDubboRpcApiBeanPostProcessorTest`（6）、`GXDubboRpcApiBeanPostProcessorSpringBootTest`（2）、`GXDubboProviderFilterValidatorTest`（3）、`GXDubboProviderFilterValidatorSpringBootTest`（2）、`GXDubboFilterChainBehaviorTest`（3）、`GXDubboSharedStateContractTest`（4）、`GXDubboPerformanceContractTest`（5）、`GXDubboExceptionDataContractTest`（2）、`AsciiSourceGuardTest`（1）。

## 7. 变更前检查清单

1. 是否只修改了 `leaf-base-dubbo-zk` 当前 Maven 模块。
2. 是否影响 Provider 侧异常分类、包装和透传边界。
3. 是否影响 `RpcException` 在 Web 层的返回结构或消息语义。
4. 是否引入了非 ASCII 日志文案。
5. 是否修改了 Dubbo SPI 资源映射但没有同步验证类路径和扩展名。
6. 是否删除冗余注释的同时，仍保留了复杂逻辑所需的 Javadoc 或使用示例。
7. 是否只是为了代码“更好看”而做了无复用价值的方法抽取或类拆分。
8. 是否已经检查并补充了与改动点对应的测试。
9. 是否评估了性能、线程安全性和对外接口兼容性。
10. 是否在修改既有逻辑前后确认原有行为仍然正确。

## 8. 提交说明建议

1. 涉及异常转换、返回语义、SPI 资源映射的改动，提交说明中要写清楚旧行为、新行为和兼容性影响。
2. 涉及日志调整时，提交前确认新增和修改后的日志全部为 ASCII。
3. 涉及测试补充时，提交说明中写明新增覆盖了哪些关键分支。
4. 涉及线程模型调整时，提交说明中写明选择虚拟线程或线程池的原因。
