# AGENTS.md - leaf-base-dubbo-zk

本文档用于指导 `leaf-base-dubbo-zk` Maven 模块的后续迭代开发。修改本模块时，优先保证 Dubbo Provider 侧异常处理语义、Spring Boot Web 场景下 Dubbo 调用异常兜底行为，以及 Dubbo SPI 资源注册的一致性。

本模块已有代码已经在生产环境使用。任何修改都必须以保持现有功能正确性、对外接口兼容性、运行性能和线程安全性为前提。

## 1. 模块定位

`leaf-base-dubbo-zk` 是 Maple Leaf Framework 中基于 Dubbo 和 Zookeeper 注册中心的 RPC 基础支持模块。按照当前代码实现，本模块主要提供以下能力：

1. Dubbo Provider 侧异常过滤与分类处理：`GXDubboExceptionFilter`
2. Spring Boot Web 层中 Dubbo 调用异常的统一返回兜底：`GXDubboCallExceptionHandler`
3. Dubbo Filter SPI 扩展声明：`src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Filter`

本模块当前不承载以下职责：

1. Dubbo Consumer 侧 TraceId 透传
2. Provider 侧 TraceId 恢复与跨线程上下文管理
3. `PenetrateAttachmentSelector` 之类的附件透传策略
4. `ServiceBean` 启动期绑定增强
5. Sentinel 控制台规则管理

以上能力如果后续引入，必须同步更新本文档。

## 2. 修改边界

1. 每次修改只能修改当前 Maven 模块：`leaf-base-dubbo-zk`。
2. 不得修改其他 Maven 模块的源码、测试、资源或构建配置，除非用户明确要求并重新确认范围。
3. 不得随意更改本模块对外接口规范，包括公开类名、方法签名、异常类型、SPI 扩展名、资源路径、返回结构和错误语义。
4. 修改已经存在的逻辑时，必须先理解现有行为，再在确保原有功能正确性的条件下完善；不得为了简化实现而改变线上依赖的正确行为。
5. 由于本模块属于基础框架，性能、线程安全性和运行时兼容性必须作为一等约束处理。

## 3. 目录职责

1. `src/main/java/cn/maple/dubbo/zk/filter`
   负责 Dubbo Provider 侧 Filter 扩展，当前核心类为 `GXDubboExceptionFilter`。
2. `src/main/java/cn/maple/dubbo/zk/handler`
   负责 Spring MVC 场景中的异常统一处理，当前核心类为 `GXDubboCallExceptionHandler`。
3. `src/main/resources/META-INF/dubbo`
   负责 Dubbo SPI 资源声明，属于运行时装配基线。类名、扩展名、资源路径发生变化时必须同步调整并验证。

## 4. 功能基线

开发前必须理解并保持以下行为稳定：

1. `GXDubboExceptionFilter` 只在 Dubbo Provider 侧生效，并以 `ExceptionFilter` 扩展方式接入。
2. `GXDubboExceptionFilter` 当前会区分处理 checked exception、`GXBusinessException`、`RpcException`、JDK/Jakarta 异常、MyBatis 异常以及特定 Sentinel 限流异常文案。
3. `GXDubboExceptionFilter` 当前通过异常消息 `"SentinelBlockException: FlowException"` 识别限流场景，并转换成 `GXSentinelFlowException`，同时附带 `methodName`、`arguments`、`interfaceName` 数据。
4. `GXDubboExceptionFilter` 当前会跳过 `GenericService` 调用场景，不应轻易改变这个兼容边界。
5. `GXDubboExceptionFilter` 当前会在未声明的运行时异常场景记录错误日志，并根据服务接口与异常类是否位于同一代码来源决定是否继续透传。
6. `GXDubboCallExceptionHandler` 当前只显式处理 `RpcException`，并返回既有兜底响应语义。
7. `GXDubboCallExceptionHandler` 继承 `GXExceptionHandler`，因此本模块的 Web 异常返回行为同时受父类通用异常处理链影响；调整时必须一并评估兼容性。
8. Dubbo SPI 资源文件是模块运行时注册入口。任何包名、类名、扩展名调整，都必须把资源文件同步视为一等变更点。

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

当前模块如果缺少完整 `src/test` 覆盖，后续凡是修改本模块功能代码，都必须同步补齐与变更点对应的测试基线。

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
5. 如果未来新增 TraceId 透传、selector、bean post processor、Sentinel 细化处理等能力，对应测试基线也必须同步写入本文档。

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

## 8. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一个 PR 或提交中同步更新本文档：

1. 模块职责边界发生变化，例如新增 Consumer 侧能力、TraceId 透传、selector、bean post processor 或其他 RPC 基础设施。
2. 目录结构发生变化，例如新增核心 `package`，或调整 `filter`、`handler`、`resources` 的职责划分。
3. 异常分类、包装、透传、日志记录策略发生变化。
4. `GXDubboCallExceptionHandler` 的返回结构、状态码语义或父类协同方式发生变化。
5. Dubbo SPI 扩展名、资源路径、实现类路径、装配方式发生变化。
6. 测试策略发生变化，例如新增必须依赖 Spring Boot 集成测试的场景，或为模块建立了新的最低测试门槛。
7. 团队新增需要长期遵守且会影响后续迭代的开发规则。
8. 对外接口规范、性能约束、线程模型或并发策略发生变化。

如果本次改动不触发以上条件，可以不更新本文档。

## 9. 提交说明建议

1. 涉及异常转换、返回语义、SPI 资源映射的改动，提交说明中要写清楚旧行为、新行为和兼容性影响。
2. 涉及日志调整时，提交前确认新增和修改后的日志全部为 ASCII。
3. 涉及测试补充时，提交说明中写明新增覆盖了哪些关键分支。
4. 涉及线程模型调整时，提交说明中写明选择虚拟线程或线程池的原因。
