# AGENTS.md - leaf-base-dubbo-nacos

本文档用于指导 `leaf-base-dubbo-nacos` 模块的后续迭代开发与代码审查。修改本模块时，默认优先保持现有 Dubbo/Nacos 集成方式、TraceId 透传语义、Provider 侧异常处理语义、Spring Boot 启动期 RPC 绑定行为和 Dubbo SPI 注册方式稳定。

本模块代码已经在生产环境使用。任何修改都必须在确保原有功能正确性的前提下进行，不得随意改变对外接口规范、运行时契约或兼容性行为。

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

`leaf-base-dubbo-nacos` 是 Maple Leaf Framework 中基于 Dubbo 3.3.6 和 Nacos 的 RPC 基础支持模块，当前主要提供以下能力：

1. Dubbo Consumer 侧 TraceId 透传：`GXDubboClientTraceIdFilter`
2. Dubbo Provider 侧 TraceId 提取、回填与线程上下文恢复：`GXDubboServerTraceIdFilter`
3. Dubbo 调用链附件选择与回传：`GXPenetrateAttachmentSelector`
4. Provider 侧异常分类转换与敏感实现细节保护：`GXDubboExceptionFilter`
5. Spring Boot Web 层 Dubbo 调用异常统一兜底：`GXDubboCallExceptionHandler`
6. Dubbo `ServiceBean` 启动期 RPC API 与业务服务类绑定增强：`GXDubboRpcApiBeanPostProcessor`
7. Dubbo SPI 扩展声明与序列化白名单资源注册：`META-INF/dubbo/*`、`security/serialize.allowlist`

本模块不承载具体业务 RPC 接口实现、不承载业务编排、不承载 Sentinel 控制台规则管理，也不承载 Dubbo Sentinel 适配能力；Dubbo + Nacos + Sentinel 的适配能力由 `leaf-base-dubbo-nacos-sentinel` 负责。

## 2. 目录职责

1. `src/main/java/cn/maple/dubbo/nacos/filter`
   负责 Dubbo Filter 扩展，包括 Consumer/Provider TraceId 处理和 Provider 侧异常过滤。
2. `src/main/java/cn/maple/dubbo/nacos/selector`
   负责 `PenetrateAttachmentSelector` 扩展，决定请求和响应阶段哪些附件需要跨链路透传。
3. `src/main/java/cn/maple/dubbo/nacos/processor`
   负责 Spring 容器启动后对 Dubbo `ServiceBean` 做增强绑定。
4. `src/main/java/cn/maple/dubbo/nacos/handler`
   负责 Spring MVC 场景中对 Dubbo 调用异常做统一返回包装。
5. `src/main/resources/META-INF/dubbo`
   负责声明 Dubbo SPI 扩展名到实现类的映射，属于运行时装配基线。
6. `src/main/resources/security`
   负责 Dubbo 序列化安全白名单资源。新增跨进程传输对象时，要同步评估是否需要更新。

## 3. 核心行为基线

开发前必须理解并保持以下行为稳定：

1. TraceId 获取优先级在不同阶段有明确顺序，核心来源包括 `Invocation attachment`、`RpcContext attachment` 和 `GXTraceIdContextUtils`，不得随意调整优先级。
2. Consumer Filter 和 Provider Filter 都会把 TraceId 写回 `Invocation`、`RpcContext.getClientAttachment()`、`RpcContext.getServerAttachment()`，这是跨服务透传的核心机制。
3. 两个 TraceId Filter 都会保存进入前的原始 TraceId，并在 `finally` 中调用 `GXTraceIdContextUtils.restoreTraceId(originalTraceId)` 恢复线程上下文，禁止删除或绕过。
4. `GXPenetrateAttachmentSelector` 与两个 TraceId Filter 相互配合，变更任意一方时必须一起评估请求链和响应链的透传行为。
5. `GXDubboExceptionFilter` 只在 Provider 侧生效，并且对 `GXBusinessException`、方法签名已声明异常、`RpcException`、JDK/Jakarta 异常、MyBatis 异常、Sentinel 限流异常的处理策略不同，不得合并成单一路径。
6. `GXDubboExceptionFilter` 对 Sentinel 限流异常的识别依赖类名和消息内容双重兜底，修改判断逻辑时必须考虑未直接引入 Sentinel 依赖的运行场景。
7. `GXDubboRpcApiBeanPostProcessor` 依赖 Dubbo `ServiceBean#getRef()`、泛型参数解析、Spring 容器按类型取 Bean，以及反射调用 `staticBindServeServiceClass` 完成绑定。这是一条约定式链路，不得轻易变更协议。
8. SPI 文件中的扩展名是外部装配点。修改扩展名、实现类路径或资源位置时，必须视为兼容性变更。
9. `serialize.allowlist` 当前只包含 `com.google.common.collect.HashBasedTable`。新增 Dubbo 传输对象若触发安全校验，需要同步评估白名单是否扩展。

## 4. 强制开发规则

1. 每次修改只能发生在当前 Maven 模块 `leaf-base-dubbo-nacos` 内，不得修改其他模块、父工程或无关共享文件；确需跨模块修改时，必须先明确说明原因并获得确认。
2. 现有代码已经在线上生产环境使用，不得随意变更对外接口、异常类型、返回结构、SPI 扩展名、资源路径、配置键、序列化白名单语义和启动期绑定协议。
3. 所有 `log` 日志内容都必须使用 ASCII 字符。新增或修改日志时，禁止写入中文、全角符号或其他非 ASCII 文案。
4. 涉及任何改动点，都要先检查对应测试用例是否完整。没有测试、测试失效或覆盖不足时，应先补齐测试再修改实现。
5. 严格遵循测试驱动最佳实践。先通过测试描述目标行为和兼容边界，再实现或调整生产代码，最后运行与改动范围匹配的测试。
6. 不要随意将已存在的代码抽取成单独方法或类。只有当抽取后的方法或类可以被两个以上位置复用，并且确实降低维护成本时，才允许抽取。
7. 删除方法、类、变量上的冗余注释。命名已经表达清楚的内容，不要再用注释重复解释。
8. 对复杂方法、复杂类、扩展点、异常转换规则和 SPI 约定，适当补充 Javadoc；使用方式复杂时，可以加入最小使用示例，帮助使用者理解前置条件、典型用法和异常语义。
9. 清理 Javadoc 中的冗余信息，只保留必要文档。不要堆砌参数描述、空洞说明或与代码命名重复的信息。
10. 基础框架代码对性能和线程安全性要求很高。修改 ThreadLocal、`RpcContext`、缓存、反射、集合共享状态、并发初始化和异常处理路径时，必须显式考虑性能开销与线程安全。
11. 在线程池与虚拟线程都能满足场景需求时，优先使用虚拟线程；如果继续使用线程池，需要有明确理由，例如兼容性、生命周期控制、资源隔离或三方库限制。
12. 在确保原有功能正确性的前提下完善代码。修改已经存在的逻辑时，不得破坏现有逻辑的正确性、兼容性和线上行为。
13. 涉及 Filter、Selector、BeanPostProcessor、ExceptionHandler 的改动时，优先保持现有扩展点名称、装配入口、触发时机和上下文恢复语义不变。
14. 涉及异常转换的改动时，必须明确哪些异常继续透传，哪些异常包装成框架异常，哪些异常需要记录日志，禁止静默改变线上错误分类。
15. 涉及 ThreadLocal、`RpcContext`、`Invocation attachment` 的改动时，必须保证设置路径和恢复路径成对出现，避免上下文串用。

## 5. 测试要求

当前模块下没有现成的 `src/test` 测试目录。后续凡是修改本模块功能代码，必须同步补齐与变更点对应的测试基线。

1. 测试用例要覆盖全面，至少覆盖成功路径、失败路径、异常路径、空值路径和边界条件。
2. 如果功能需要在 Spring Boot 应用运行后才能验证，测试用例就应该模拟真实 Spring Boot 应用启动之后再测试，优先使用 `@SpringBootTest`、真实 Bean 装配、AOP/BeanPostProcessor 生效后的上下文。
3. 修改 `GXDubboClientTraceIdFilter` 或 `GXDubboServerTraceIdFilter` 时，至少覆盖：
   - `Invocation`、client attachment、server attachment、ThreadLocal 的 TraceId 优先级
   - 进入前已有 TraceId、进入前为空、调用后恢复原值
   - 异常抛出时 `finally` 仍然恢复上下文
4. 修改 `GXPenetrateAttachmentSelector` 时，至少覆盖：
   - `select` 与 `selectReverse` 的 TraceId 选择优先级
   - 响应链透传
   - 生成新 TraceId 的兜底分支
5. 修改 `GXDubboExceptionFilter` 时，至少覆盖：
   - `GXBusinessException` 直接透传
   - 方法签名已声明异常直接透传
   - Sentinel 限流异常转换为 `GXSentinelFlowException`
   - 其他运行时异常包装为 `GXBusinessException`
   - `GenericService` 调用场景跳过处理
6. 修改 `GXDubboRpcApiBeanPostProcessor` 时，至少覆盖：
   - `ServiceBean` 识别成功路径
   - `ref` 为空、泛型解析失败、目标 Spring Bean 不存在时的跳过分支
   - 绑定方法反射调用路径
7. 修改 `GXDubboCallExceptionHandler` 时，至少覆盖：
   - `RpcException` 返回的状态码和消息结构
   - `GXSentinelFlowException` 返回 `code`、`msg`、`data` 的透传行为

## 6. 变更前检查清单

1. 是否只修改了 `leaf-base-dubbo-nacos` 当前 Maven 模块。
2. 是否影响 TraceId 在请求链和响应链中的获取优先级、透传路径或恢复语义。
3. 是否影响 `GXDubboExceptionFilter` 的异常分类边界。
4. 是否影响 `GXDubboRpcApiBeanPostProcessor` 的泛型约定、Bean 查找方式或反射绑定协议。
5. 是否修改了 `META-INF/dubbo` 下的 SPI 声明，进而影响运行时装配。
6. 是否引入了非 ASCII 日志文案。
7. 是否删除了必要测试，或遗漏了与改动点对应的新增测试。
8. 是否只是为了代码看起来更整洁而做了无复用价值的方法抽取、拆类或重构。
9. 是否清理了冗余注释，并为真正复杂的逻辑补上必要 Javadoc 或使用示例。
10. 是否评估了性能、线程安全、上下文泄漏和并发初始化风险。
11. 是否改变了外部接口、返回结构、异常语义、SPI 扩展名或配置契约。

## 7. 提交说明建议

1. 涉及 TraceId 透传、异常处理、SPI 装配、启动期绑定增强的改动，PR 描述中要写清楚旧行为、新行为和兼容性影响。
2. 涉及日志调整时，提交前确认新增和修改后的日志全部为 ASCII。
3. 涉及测试补充时，提交说明中写明新增覆盖了哪些关键分支。
4. 涉及性能、线程安全或虚拟线程选择时，提交说明中写明判断依据和验证方式。
