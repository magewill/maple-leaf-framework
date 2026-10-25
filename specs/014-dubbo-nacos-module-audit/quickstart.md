# Quickstart：leaf-base-dubbo-nacos 审查结论的复现与验收

**Feature**: 014-dubbo-nacos-module-audit | **Date**: 2026-10-25
**用途**：让维护者在 15 分钟内复现任一审查结论（SC-001），并在修复后验证目标语义（SC-009）。
**前置条件**：Windows PowerShell / JDK 21+ / Maven 离线仓库可用（本机 `settings.localRepository=F:\repository`）；在仓库根目录执行所有命令。

---

## 1. 基线（修复前必须先跑）

```powershell
mvn -o -pl leaf-base-dubbo-nacos test
```

- 修复前期望：`Tests run: 31, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`（8 个测试类）。
- 修复后期望：用例数 **≥ 31 且单调增长**，0 失败。
- 测试日志中的 `ERROR Got unchecked and undeclared exception ...` 是 `GXDubboExceptionFilterTest` 的**预期输出**，不是失败；以 `Tests run` 汇总为准。

若数量少于 31，说明用例被跳过或环境不一致，先解决再继续（FR-012）。

按类运行（排查用）：

```powershell
mvn -o -pl leaf-base-dubbo-nacos test "-Dtest=GXDubboExceptionFilterTest,GXDubboTraceIdFilterTest"
mvn -o -pl leaf-base-dubbo-nacos test "-Dtest=GXDubboCallExceptionHandlerTest,GXDubboCallExceptionHandlerSpringBootTest"
mvn -o -pl leaf-base-dubbo-nacos test "-Dtest=GXDubboRpcApiBeanPostProcessorTest,GXDubboRpcApiBeanPostProcessorSpringBootTest"
mvn -o -pl leaf-base-dubbo-nacos test "-Dtest=GXPenetrateAttachmentSelectorTest,AsciiSourceGuardTest"
```

## 2. 依赖与装配真相（一条命令看清）

```powershell
mvn -o -pl leaf-base-dubbo-nacos dependency:tree "-Dincludes=com.alibaba.nacos:*,org.apache.dubbo:*"
```

期望：`leaf-base-nacos:compile → nacos-client:3.1.1:compile`；`dubbo-spring-boot-starter:3.3.6 → dubbo:3.3.6`；`dubbo-nacos-spring-boot-starter:3.3.6`。

```powershell
Select-String -Path "config\online-nacos-dubbo-demo.yml" -Pattern "filter"
```

期望：`filter: dubboExceptionFilter,-exception` → 证明"排除内置 `exception`"是**未文档化的强制装配契约**（F-01 / R-03）。

## 3. 逐条复现高危结论

| 结论 | 复现方式 | 判定标准（修复前） | 修复后判定 |
| --- | --- | --- | --- |
| **F-01** 内置 filter 包装丢业务码 | 在真实 Dubbo `injvm`/直连链中**不配置** `provider.filter`，provider 抛 `GXBusinessException(code=4001, msg, data)`，观察 consumer 收到的异常 | consumer 收到 `RuntimeException`，`code/msg/data` 不可读 | 配置 `dubboExceptionFilter,-exception` 后，consumer 收到原 `GXBusinessException`（code=4001 保真）；未配置时启动 ERROR 告警 |
| **F-02** advice 被兜底遮蔽 | 新增真实 `@SpringBootTest` + `@ComponentScan("cn.maple")`，controller 抛 `RpcException`，断言响应 `data.errorType` | 若基础 advice 先匹配 → `msg="系统内部错误"`、无 `errorType` | `data.errorType` 存在（`unknown`），`msg="Dubbo provider error..."` |
| **F-03** Sentinel 覆盖不全 | 构造类名为 `com.alibaba.csp.sentinel.slots.block.degrade.DegradeException` 的异常（测试替身，不引依赖），经 filter | 不被转换，仍为原异常 | 转为 `GXSentinelFlowException`（code=406） |
| **F-03** 消息误判 | 普通异常 `new RuntimeException("my FlowException happened")` 经 filter | 被误判为限流 | 不被转换，按未声明运行时异常包装 |
| **F-04** 反射失败 fail-open | 构造 `invocation.getParameterTypes()` 与实际方法不匹配的调用 + 未声明运行时异常 | 原异常直接透传（未包装） | 包装为 `GXBusinessException(500)` |
| **F-05** 静默吞异常 | 让 filter 内部抛异常（如 `invoker.getInterface()` 返回触发 NPE 的替身） | 仅 WARN，无计数 | 计数递增，原始异常语义不变 |
| **F-08** 静态 appName 串用 | 两个线程并发 `getAppName()`；两个不同 `spring.application.name` 的上下文 | 第二个上下文可能读到第一个名字 | `resetAppNameCache()` 后重新解析 |
| **F-10** 每请求 INFO | 用 Logback `ListAppender` 捕获 INFO | TraceId Filter 每请求 1 条 INFO | INFO 为 0，DEBUG 有 1 条 |
| **F-14** `@Log4j2` | `Select-String -Path "leaf-base-dubbo-nacos\src\main\java" -Pattern "@Log4j2"` | 命中 `GXDubboRpcApiBeanPostProcessor` | 无输出（改 `@Slf4j`） |

## 4. 离线测试策略（默认）

- `@SpringBootTest` 必须关闭 Nacos 自动配置：
  ```properties
  spring.cloud.nacos.config.import-check.enabled=false
  spring.cloud.nacos.discovery.enabled=false
  ```
- Dubbo 真实链用 `injvm://` 或直连 `dubbo://127.0.0.1:<port>`，**不需要注册中心**。
- 异常/上下文现场用 `AppResponse`、`RpcInvocation`、mock `Invoker`/`Invocation` 构造（FR-023，不反射私有成员）。

## 5. 需要真实 Nacos 时（FR-025）

仅当以下场景必须端到端验证时才申请：

- 注册中心服务发现下 Filter/Selector 的实际激活顺序；
- `dubbo-nacos-spring-boot-starter` 的真实配置中心下发。

**流程**：先在任务说明中写明"需要的 Nacos 能力（注册/配置）、期望验证点、预计用例"，**向用户申请**；由用户提供真实 Nacos 地址/namespace/group/凭据后再执行。**禁止**臆造连接参数；缺环境时用例须 `assumeTrue` 优雅跳过，不得让流水线失败。

## 6. 修复后验收清单

1. `mvn -o -pl leaf-base-dubbo-nacos clean test` 全绿，用例数 ≥ 31（只增不减）。
2. 每个修复项都有"旧代码失败"的用例证据（FR-021），并在 `audit-report.md` 记录。
3. `mvn -o -pl leaf-base-dubbo-nacos clean install -DskipTests` 通过（ErrorProne + NullAway 零错误）。
4. `Select-String -Path "leaf-base-dubbo-nacos\src\main\java" -Pattern "@Log4j2"` 无输出。
5. `docs/leaf-base-dubbo-nacos开发手册.md` 存在且包含：SPI 扩展名、TraceId 优先级表、异常分类矩阵、**强制装配配置**、`serialize.allowlist` 说明、不支持范围。
6. `AGENTS.md` 第 5 节与"9 个测试类 / ≥31 条用例"一致。
