# AGENTS.md - leaf-base-dubbo-nacos

本文档用于指导 `leaf-base-dubbo-nacos` 模块的后续迭代开发。修改本模块时，优先保持 Dubbo 调用链 TraceId 透传、Provider 侧异常语义、Spring Boot 启动期 RPC 绑定行为和 Dubbo SPI 注册方式的稳定性，避免无关重构。

## 1. 模块定位

`leaf-base-dubbo-nacos` 是 Maple Leaf Framework 中基于 Dubbo 3.3.6 和 Nacos 的 RPC 基础支持模块，当前主要提供以下能力：

1. Dubbo Consumer 侧 TraceId 透传：`GXDubboClientTraceIdFilter`
2. Dubbo Provider 侧 TraceId 提取、回填与线程上下文恢复：`GXDubboServerTraceIdFilter`
3. Dubbo 调用链附件选择与回传：`GXPenetrateAttachmentSelector`
4. Provider 侧异常分类转换与敏感实现细节保护：`GXDubboExceptionFilter`
5. Spring Boot Web 层 Dubbo 调用异常统一兜底：`GXDubboCallExceptionHandler`
6. Dubbo `ServiceBean` 启动期 RPC API 与业务服务类绑定增强：`GXDubboRpcApiBeanPostProcessor`
7. Dubbo SPI 扩展声明与序列化白名单资源注册：`META-INF/dubbo/*`、`security/serialize.allowlist`

本模块不承载具体业务 RPC 接口实现、不承载业务编排、不承载 Sentinel 控制台规则管理，也不承载 Dubbo Sentinel 适配能力；后者由 `leaf-base-dubbo-nacos-sentinel` 负责。

## 2. 目录职责

1. `src/main/java/cn/maple/dubbo/nacos/filter`
负责 Dubbo Filter 扩展，包括 Consumer/Provider TraceId 处理和 Provider 侧异常过滤。
2. `src/main/java/cn/maple/dubbo/nacos/selector`
负责 `PenetrateAttachmentSelector` 扩展，决定请求和响应阶段哪些附件需要跨链路透传。
3. `src/main/java/cn/maple/dubbo/nacos/processor`
负责 Spring 容器启动后对 Dubbo `ServiceBean` 做增强绑定。
4. `src/main/java/cn/maple/dubbo/nacos/handler`
负责 Spring MVC 场景下对 Dubbo 调用异常做统一返回包装。
5. `src/main/resources/META-INF/dubbo`
负责声明 Dubbo SPI 扩展名到实现类的映射，属于运行时装配基线。
6. `src/main/resources/security`
负责 Dubbo 序列化安全白名单资源，新增跨进程传输对象时要同步评估是否需要更新。

## 3. 模块功能基线

开发前必须理解并保持以下行为稳定：

1. TraceId 获取优先级在不同阶段有明确顺序，且都以 `Invocation attachment`、`RpcContext attachment`、`GXTraceIdContextUtils` 为核心来源，不能随意改乱优先级。
2. Consumer Filter 和 Provider Filter 都会把 TraceId 写回 `Invocation`、`RpcContext.getClientAttachment()`、`RpcContext.getServerAttachment()`，这是跨服务透传的核心机制。
3. 两个 TraceId Filter 都会保存进入前的原始 TraceId，并在 `finally` 中调用 `GXTraceIdContextUtils.restoreTraceId(originalTraceId)` 恢复线程上下文，不能删除。
4. `GXPenetrateAttachmentSelector` 与两个 TraceId Filter 是互相配合的，变更任意一方时必须一起评估请求链和响应链的透传行为。
5. `GXDubboExceptionFilter` 只在 Provider 侧生效，且对 `GXBusinessException`、方法签名已声明异常、`RpcException`、JDK/Jakarta 异常、MyBatis 异常、Sentinel 限流异常的处理策略不同，不能合并成单一路径。
6. `GXDubboExceptionFilter` 对 Sentinel 限流异常的识别依赖类名和消息内容双重兜底，修改判断逻辑时必须考虑未直接引入 Sentinel 依赖的运行场景。
7. `GXDubboRpcApiBeanPostProcessor` 依赖 Dubbo `ServiceBean#getRef()`、泛型参数解析、Spring 容器按类型取 Bean，以及反射调用 `staticBindServeServiceClass` 完成绑定；这是一条约定式链路，不要轻易改协议。
8. SPI 文件中的扩展名是外部装配点，修改扩展名、实现类路径或资源位置时，需要视为兼容性变更。
9. `serialize.allowlist` 当前只包含 `com.google.common.collect.HashBasedTable`，新增 Dubbo 传输对象若触发安全校验，需要同步评估白名单是否扩展。

## 4. 强制开发规则

1. 所有 `log` 日志中的内容都必须使用 ASCII 字符。新增或修改日志时，禁止写入中文、全角符号或其他非 ASCII 文案。
2. 涉及到改动的点，都要同步查看测试用例是否完整覆盖。没有测试、测试失效、或覆盖不足时，先补测试再提交。
3. 不要随便将已存在的代码抽取成单独的方法或者类，除非抽取后的方法或类可以被两个以上位置复用，并且明显降低维护成本。
4. 将方法、类、变量上的冗余注释去掉。注释不能重复解释命名已经表达清楚的内容。
5. 对复杂的方法、类、扩展点要适当补充 Javadoc；必要时加入最小使用示例，帮助调用方理解前置条件、典型用法和异常语义。
6. 涉及 Filter、Selector、BeanPostProcessor、ExceptionHandler 的改动时，优先保持现有扩展点名称、装配入口、触发时机和上下文恢复语义不变。
7. 涉及异常转换的改动时，必须明确哪些异常继续透传，哪些异常包装成框架异常，哪些异常需要记录日志，禁止静默改变线上错误分类。
8. 涉及 ThreadLocal、`RpcContext`、`Invocation attachment` 的改动时，必须保证设置路径和恢复路径成对出现，避免上下文串用。

## 5. 测试要求

当前模块下没有现成的 `src/test` 测试目录。后续凡是改动本模块功能代码，必须先补齐对应测试基线。

1. 测试用例要覆盖全面，至少覆盖成功路径、失败路径、异常路径、空值路径和边界条件。
2. 如果该功能需要在 Spring Boot 应用运行的情况下才能测试，那么测试用例就应该模拟真实 Spring Boot 应用启动之后再测试，优先使用 `@SpringBootTest`、真实 Bean 装配、AOP/BeanPostProcessor 生效后的上下文。
3. `GXDubboClientTraceIdFilter` 和 `GXDubboServerTraceIdFilter` 的改动，至少覆盖：
   - `Invocation`、`client attachment`、`server attachment`、`ThreadLocal` 的 TraceId 优先级
   - 进入前已有 TraceId、进入前为空、调用后恢复原值
   - 异常抛出时 `finally` 仍然恢复上下文
4. `GXPenetrateAttachmentSelector` 的改动，至少覆盖：
   - `select` 与 `selectReverse` 的 TraceId 选择优先级
   - 响应链透传
   - 生成新 TraceId 的兜底分支
5. `GXDubboExceptionFilter` 的改动，至少覆盖：
   - `GXBusinessException` 直接透传
   - 方法签名已声明异常直接透传
   - Sentinel 限流异常转换为 `GXSentinelFlowException`
   - 其他运行时异常包装为 `GXBusinessException`
   - `GenericService` 调用场景跳过处理
6. `GXDubboRpcApiBeanPostProcessor` 的改动，至少覆盖：
   - `ServiceBean` 识别成功路径
   - `ref` 为空、泛型解析失败、目标 Spring Bean 不存在时的跳过分支
   - 绑定方法反射调用路径
7. `GXDubboCallExceptionHandler` 的改动，至少覆盖：
   - `RpcException` 返回的状态码和消息结构
   - `GXSentinelFlowException` 返回 `code`、`msg`、`data` 的透传行为

## 6. 变更前检查清单

1. 是否影响 TraceId 在请求链和响应链中的获取优先级、透传路径或恢复语义。
2. 是否影响 `GXDubboExceptionFilter` 的异常分类边界。
3. 是否影响 `GXDubboRpcApiBeanPostProcessor` 的泛型约定、Bean 查找方式或反射绑定协议。
4. 是否修改了 `META-INF/dubbo` 下的 SPI 声明，进而影响运行时装配。
5. 是否引入了非 ASCII 日志文案。
6. 是否删除了必要测试，或遗漏了与改动点对应的新增测试。
7. 是否只是为了“代码更好看”而做了无复用价值的抽方法、拆类或重构。
8. 是否清理了冗余注释，并为真正复杂的逻辑补上了必要的 Javadoc。

## 7. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一个 PR 或提交中同步更新本文件：

1. 模块职责边界发生变化，例如新增或移除 RPC 基础能力、异常处理能力、调用链透传能力。
2. 目录结构发生变化，例如新增新的核心 `package`，或把 `filter`、`selector`、`processor`、`handler` 的职责重新拆分。
3. TraceId 透传规则、优先级、ThreadLocal 恢复策略、`RpcContext` 使用方式发生变化。
4. Provider 侧异常分类、包装策略、Sentinel 兼容策略、返回错误语义发生变化。
5. `ServiceBean` 绑定约定、泛型解析方式、反射方法名、Spring Bean 获取方式发生变化。
6. Dubbo SPI 扩展名、资源文件路径、序列化白名单策略发生变化。
7. 测试策略发生变化，例如新增必须使用 Spring Boot 集成测试验证的场景，或新增模块最低测试门槛。
8. 团队新增了需要长期遵守的开发规则，并且会影响后续所有迭代。

如果本次改动不触发以上条件，可以不更新本文件。

## 8. 提交说明建议

1. 涉及 TraceId 透传、异常处理、SPI 装配、启动期绑定增强的改动，PR 描述中要写清楚旧行为、新行为和兼容性影响。
2. 涉及日志调整时，提交前确认新增和修改后的日志全部为 ASCII。
3. 涉及测试补充时，提交说明中写明新增覆盖了哪些关键分支。
