# Quickstart：扩展点模块加固的验证指南

**Feature**: 002-extension-point-hardening | **Date**: 2026-10-21

本文件只描述"怎么跑、看什么、期望什么"。契约细节见 `contracts/`，实体与不变量见 `data-model.md`。

## 0. 前置条件

- JDK 21+（本项目实测在 JDK 21/25 上均可编译）
- Maven 可用，本地仓库已具备 `leaf-base-framework` 依赖
- 在仓库根目录执行命令

基础命令：

```powershell
# 模块级全量验证（推荐，提交前必跑）
mvn -pl leaf-base-extension -am test

# 只跑本模块（依赖已安装时）
mvn -pl leaf-base-extension test

# 只跑某个测试类
mvn -pl leaf-base-extension test -Dtest=GXExtensionRegisterTest
```

通过判据：`BUILD SUCCESS`，且 `leaf-base-extension/target/surefire-reports` 中**每个**测试类都有 `Tests run: N, Failures: 0, Errors: 0`，`N > 0`。

---

## 场景 1：五种声明写法全部注册（对应 FR-001、SC-001）

```powershell
mvn -pl leaf-base-extension test -Dtest=GXExtensionRegisterTest
```

期望：
- 单场景、重复注解 ×2、显式容器、笛卡尔积、混用 —— 每种的注册条数等于声明坐标数；
- 混用写法（`@GXExtension` + `@GXExtensions`）得到两处坐标，不是一处；
- 父类与子类各自声明同一坐标 —— 启动期冲突失败，信息同时含父子双方类名（不得子类静默覆盖）。

反证：改造前同一组用例只会在 `doRegistrationExtensions` 路径通过，`doRegistration` 路径注册数为 0。

## 场景 2：解析不出坐标必须失败（对应 FR-002）

```powershell
mvn -pl leaf-base-extension test -Dtest=GXExtensionRegisterTest
```

期望：声明了容器但坐标为空的用例抛出 `GXExtensionException`，断言内容包含实现类全名与声明形态；不存在"注册 0 条且无异常"的用例通过。

## 场景 3：字符串入口四层兜底（对应 FR-005、SC-002）

```powershell
mvn -pl leaf-base-extension test -Dtest=GXExtensionExecutorTest
```

期望：把同一份已注册数据分别用"类型 + 场景对象"和"扩展点名称 + 场景标识"解析，四条兜底路径（精确 / 默认场景 / 默认用例 / 全局默认）与"全部未命中"共 5 类用例，两个入口返回同一实现；未命中时两个入口抛同类型异常。

## 场景 4：非法坐标在构造期被拒（对应 FR-020、SC-009）

```powershell
mvn -pl leaf-base-extension test -Dtest=GXExtensionCoordinateTest,GXBizScenarioTest
```

期望：
- `a.b`（两段）、`a.b.c.d`（四段）、`a..c`（空白段）在构造期抛 `IllegalArgumentException`，信息含实际段数与非法段；
- 合法三段坐标构造成功，且与等价类型坐标 `equals` 为真、`hashCode` 相同。

## 场景 5：错误码语义（对应 FR-008）

```powershell
mvn -pl leaf-base-extension test -Dtest=GXExtensionExceptionTest,GXExtensionExecutorTest
```

期望：解析未命中的异常错误码为 `404`；`new GXExtensionException("x")` 与其带 `Throwable` 的构造器不再返回 `200`；异常仍是 `GXBusinessException` 的子类；信息要素按类型分层（未命中=扩展点 + 场景；冲突=坐标 + 双方实现类；接口非法=实现类 + 候选接口）。

## 场景 6：并发注册与并发解析（对应 FR-011、FR-012、FR-016）

```powershell
mvn -pl leaf-base-extension test -Dtest=GXExtensionConcurrencyTest
```

期望：
- 8 个虚拟线程同时对同一坐标注册 10 000 次 → 成功次数恰为 1，其余全部观察到冲突，仓库该坐标只有一个实现；
- 注册期间并发解析 200 000 次 → 不出现空实现、不出现与坐标不符的实现、不抛非预期异常。

## 场景 7：解析开销与规模无关（对应 FR-014、SC-006、SC-008、FR-016）

```powershell
mvn -pl leaf-base-extension test -Dtest=GXExtensionExecutorBenchmarkTest
```

期望（三层证据，缺一不可）：

1. **绝对 + 相对双门禁**：预热后多轮采样，执行器单次解析中位数 ≤ 6 × 同进程"裸映射查找"中位数，且 ≤ 1 μs。参照基线（本机 JDK 21，单线程 100 万次）：精确命中 15–26 ns，四层兜底 23–44 ns。
2. **查找次数恒定**：用可计数的仓库替身统计，精确命中触达仓库 1 次、四层兜底最多 4 次。
3. **规模无关**：注册 1 个实现与注册 100 个实现两种规模下，执行器耗时中位数比值 ≤ 1.2。

## 场景 8：装配、注册时机与延迟初始化（对应 FR-015、FR-019、SC-005）

```powershell
mvn -pl leaf-base-extension test -Dtest=GXExtensionBootstrapTest,GXExtensionAutoConfigurationTest
```

期望：
- 混用注解的 Bean 两处坐标都注册；
- 仅笛卡尔积的 Bean 被注册；
- `@Lazy` 扩展 Bean 仍被注册；
- 组件扫描与自动装配并存时，四个默认 Bean 各只有一个；
- 冲突声明的上下文启动失败，且错误信息中的"已有实现 / 冲突实现"在重复启动间保持一致。

## 场景 9：端到端示例真实执行（对应 FR-015、SC-005）

```powershell
mvn -pl leaf-base-extension test -Dtest=GXExtensionCustomerSpringBootTest
Get-Content leaf-base-extension/target/surefire-reports/cn.maple.extension.GXExtensionCustomerSpringBootTest.txt
```

期望：报告存在且 `Tests run: 3, Failures: 0, Errors: 0`（改造前该文件不存在，用例零执行）。三个用例覆盖"精确场景""默认场景""默认用例"三层兜底在真实 Spring 上下文中的选实现结果。

---

## 回归自检清单

| 检查 | 命令 / 观察点 |
| --- | --- |
| 全模块测试 | `mvn -pl leaf-base-extension -am test` 全绿 |
| 零执行测试类 | `target/surefire-reports` 中每个类 `Tests run` 均 > 0 |
| 编译期检查 | 构建日志无 ErrorProne / NullAway 报错 |
| 契约未变 | `git diff` 中注解属性、`execute*` / `doRegistration*` / `registerExtension*` 签名、Bean 名称均无改动 |
| 消融验证 | 手动把兜底顺序或注册解析改回旧逻辑，场景 1/3/9 必须变红 |

## 依赖的契约与模型

- 声明形态矩阵与失败契约：`contracts/extension-annotation.md`
- 执行入口、兜底顺序与异常：`contracts/executor-api.md`
- 坐标格式与构造期校验：`contracts/coordinate.md`
- Bean 名称、注册时机与覆盖方式：`contracts/autoconfiguration.md`
- 实体、不变量与状态流转：`data-model.md`
- 决策依据与实测证据：`research.md`
