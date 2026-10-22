# Quickstart: 复现与验收 leaf-base-framework 审查结论

**Feature**: `005-framework-module-audit` | **Date**: 2026-10-22

本指南用于**验收审查交付物**：任何人按顺序执行即可确认"报告里的结论真的成立"。不包含实现代码，不包含完整用例体；用例字段与判定口径见 [data-model.md](./data-model.md) 与 [contracts/](./contracts/)。

---

## 1. 前置条件

| 项 | 要求 | 核对命令 |
| --- | --- | --- |
| JDK | ≥ 21（本机实测 25.0.4，`pom.xml` 为 `release=21`） | `java -version` |
| Maven | 3.9+，可离线执行 | `mvn -v` |
| 本地仓库 | 已安装 `leaf-base-framework:4.3.0-SNAPSHOT` 及其父 POM | `mvn -o -pl leaf-base-framework -q validate` |
| 工作目录 | 仓库根 `maple-leaf-framework` | — |

不要求：真实数据库、Redis、消息中间件、Servlet 容器端口、外部网络。

---

## 2. 建立基线（必须先跑，用于区分"既有失败"与"审查引入的失败"）

```powershell
mvn -o -q -pl leaf-base-framework test
```

**预期**：构建成功，`target/surefire-reports` 汇总为

```text
files=65 tests=518 failures=0 errors=0 skipped=0
```

聚合命令（与实测口径一致）：

```powershell
cd leaf-base-framework
$r = Get-ChildItem target/surefire-reports -Filter *.txt
$t=0;$f=0;$e=0;$s=0
foreach($x in $r){
  $c = Get-Content $x.FullName -Raw
  if($c -match 'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)'){
    $t+=[int]$Matches[1]; $f+=[int]$Matches[2]; $e+=[int]$Matches[3]; $s+=[int]$Matches[4]
  }
}
"files=$($r.Count) tests=$t failures=$f errors=$e skipped=$s"
```

**注意**：控制台会打印若干异常堆栈与中文乱码（既有用例故意触发的异常路径 + 控制台编码），**不代表失败**；以退出码与 surefire 汇总为准。`spec.md` 提到 519 个 `@Test` 注解，实际执行 518，差额解释必须出现在报告第 2 章。

---

## 3. 执行审计用例（现状固化）

```powershell
mvn -o -pl leaf-base-framework test -Dtest='GX*AuditTest'
```

**预期**：8 个审计用例类全部通过——它们断言的是**当前行为**（含缺陷行为），因此绿色即"现状成立"。若某个审计用例失败，说明现状已变化（可能已被他人修复），需按 [contracts/review-report-schema.md](./contracts/review-report-schema.md) §6 的 `REVISED` 流程更新报告。

单类执行示例：

```powershell
mvn -o -pl leaf-base-framework test -Dtest=GXContextPropagationAuditTest
```

---

## 4. 八条高危发现的单点复现

| 发现 | 用例类#方法（或命令） | 预期现象 |
| --- | --- | --- |
| F-01 异步不传播上下文 | `GXContextPropagationAuditTest#asyncExecutorDoesNotPropagateTraceId` | 任务内 `getNullableTraceId()` 与提交线程不同（为 `null` 或不一致） |
| F-01 包装器漏覆写 | `GXContextPropagationAuditTest#forkJoinSubmitDoesNotWrapTask`、`#submitListenableDoesNotWrap` | 任务内 MDC 为空；包装器调用走父类路径 |
| F-02 MDC 只恢复单键 | `GXContextPropagationAuditTest#customMdcKeyLeaksAfterFilterChain` | 过滤链结束后自定义键仍在当前线程 MDC 中 |
| F-03 解密静默降级 | `GXSecurityBoundaryAuditTest#decryptReturnsInputWhenCiphertextInvalid` | 返回入参原串，无异常、无日志 |
| F-04 请求体进属性 | `GXSecurityBoundaryAuditTest#requestBodyJsonStoredWithSensitiveFields` | 请求属性中含完整 JSON，包括口令类字段 |
| F-05 XSS 数据污染 | `GXWebBoundaryAuditTest#htmlFilterRewritesAmpersandAndLtInJsonValue` | JSON 字符串值中的 `&`/`<` 被改写；`getContentLength()` 与 `Content-Length` 头不一致 |
| F-06 动态调用无白名单 | `GXSecurityBoundaryAuditTest#dynamicCallLoadsClassOutsideModule` | 传入模块外类名时 `Class.forName` 成功（对照：解析侧同样输入被拒） |
| F-07 异常信息回显 | `GXWebBoundaryAuditTest#multipartExceptionEchoesCauseMessage` | 响应 `msg` 等于构造的 cause message |
| F-08 事件载荷不快照 | `GXEventContractAuditTest#customPayloadIsSharedAfterPublish` | 发布后修改原对象，订阅方读到修改后的值 |

**执行方式**：每行都可用第 3 节的 `-Dtest=<类>#<方法>` 单独运行。若某条无法执行，报告 `reproduction` 必须写明缺失条件（此时该条不得标 `DEFECT`）。

---

## 5. 性能口径复算（不依赖计时）

按 [contracts/audit-verdict-contract.md](./contracts/audit-verdict-contract.md) §3 的六条公式，逐条做"代码路径 → 数字"的复算：

1. **XSS_REWRITE**：读 `filter/GXXssHttpServletRequestWrapper.java` 的读取与二次渲染位置，代入 `body = 1MB`、并发 100，得到峰值内存量级；用 `GXWebBoundaryAuditTest#xssRewriteKeepsOriginalContentLengthHeader` 验证头不一致。
2. **BODY_CACHE**：同一请求调用 `getHttpParam` 5 次，观察底层解析次数（用例 `GXWebBoundaryAuditTest#bodyCacheParsesPerRead`）。
3. **RATE_LIMIT**：以 1000 个不同 `clientId` 打点，反射读静态 Map 的 `size()`（用例 `GXConcurrencySafetyAuditTest#rateLimiterGrowsWithDistinctClientIds`）。
4. **CONCURRENT_TOOLS**：把池容量设为 1 且提交阻塞任务，观察后续任务在**提交线程**执行（用例 `GXConcurrencySafetyAuditTest#callerRunsPolicyExecutesOnCallerThread`）。
5. **EVENT_DISPATCH**：向 k 个订阅者发布 1 个事件，Mock 执行器记录提交次数应等于 k（用例 `GXEventExecutionAuditTest#dispatchSubmitsOncePerSubscriber`）。
6. **REFLECTION_COPY**：连续获取同一类型对的 `BeanCopier` N 次，观察 `create` 调用次数（用例 `GXPerformanceBudgetAuditTest#beanCopierCacheMissesOnEveryLookup`）。

**验收标准**：报告中的公式代入结果与上述观察一致；**不接受**任何以 `... < 2000ms` 形式表达的结论。

---

## 6. 交付物一致性核对

```powershell
$dir = 'specs/005-framework-module-audit'

# 6.1 台账行数必须为 309（去掉表头与分隔行）
(Select-String -Path "$dir/coverage-ledger.md" -Pattern '^\| `[^`]+\.java`').Count

# 6.2 证据/发现编号齐全
(Select-String -Path "$dir/review-report.md" -Pattern '^### F-\d{2}').Count   # 期望 ≥ 21
(Select-String -Path "$dir/review-report.md" -Pattern '^\| `E-\d{2}`').Count   # 期望 ≥ 22

# 6.3 六条性能口径齐全
foreach ($p in 'XSS_REWRITE','BODY_CACHE','RATE_LIMIT','CONCURRENT_TOOLS','EVENT_DISPATCH','REFLECTION_COPY') {
  if (-not (Select-String -Path "$dir/review-report.md" -Pattern $p -Quiet)) { "MISSING $p" }
}

# 6.4 报告与规格编号一致（F-01…F-21 全部出现）
1..21 | ForEach-Object { $id = 'F-{0:d2}' -f $_; if (-not (Select-String -Path "$dir/review-report.md" -Pattern $id -Quiet)) { "MISSING $id" } }
```

`contracts/review-report-schema.md` §8 的 C-01…C-06 是这组核对的完整版，`/speckit.tasks` 会把它落成可执行检查。

---

## 7. 不覆盖项（避免误读验收范围）

- 不做生产环境长稳压测、不做故障注入；性能结论是**结构性推算 + 计数冒烟**。
- 不验证下游模块（`leaf-base-datasource`、`leaf-base-redisson` 等）对本模块契约的依赖后果。
- 不验证 JDK 21 与 JDK 25 的行为差异（仅登记为风险）。
- 不评估业务语义正确性（本模块不含业务规则）。
