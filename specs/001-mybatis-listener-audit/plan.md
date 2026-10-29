# Implementation Plan: MyBatis-Plus 操作监听切面的正确性、线程安全与性能加固

**Branch**: `001-mybatis-listener-audit` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-mybatis-listener-audit/spec.md`

**Note**: 本计划只覆盖 `leaf-base-datasource` 模块内的写操作监听链路；不改变数据源切换、数据权限与 SQL 构建的既有行为。

## Summary

现有监听链路（6 个写操作切面 + 同步/异步两个分发器 + 异步执行器）整体可用，但审查发现三类问题：**同一写入可能重复投递**、**同步回调在事务未提交时执行且异常会波及已成功写入**、**监听器之间无故障隔离**；此外无监听声明时仍有事件对象分配与注解反射开销，执行器在虚拟线程模式下容量语义不一致，且缺少投递计数与整体开关。

技术方案：在不改变注解与回调接口契约的前提下，把"配置解析 / 事件构造 / 投递去重 / 计数"收敛到一个内部发布组件，把监听声明解析结果做有界缓存，用"空声明快速短路"消除无监听时的开销，用逐监听器隔离消除异常扩散，并把异步执行器配置迁移到类型安全属性类、补齐虚拟线程与开关语义。

## Technical Context

**Language/Version**: Java 21（根 POM `maven.compiler.release=21`，启用 ErrorProne + NullAway）

**Primary Dependencies**: Spring Boot 4.1.1、Spring Framework 7.0.9、MyBatis-Plus 3.5.17、Hutool 5.8.47、Caffeine 3.3.0、JSqlParser（经 `mybatis-plus-jsqlparser`）、Guava（Table 条件表达式）；测试：JUnit 5、Mockito、AssertJ

**Storage**: N/A（纯内存事件投递，不引入持久化、消息队列或发件箱）

**Testing**: JUnit 5（Jupiter）+ Mockito + `ApplicationContextRunner`；模块级命令 `mvn -pl leaf-base-datasource -am test`；微基准以手写预热 + 多次采样的测试夹具实现，不引入 JMH

**Target Platform**: JVM 21 服务端类库（被下游 Spring Boot 应用以依赖形式引入）

**Project Type**: library（Maven Reactor 中的 `leaf-base-datasource` 模块）

**Performance Goals**: 无监听声明的写操作额外耗时 ≤ 2%（P95）且事件对象分配为 0；有监听声明的单条写操作监听引入开销 ≤ 1 ms（P95，不含业务回调耗时）；1 万条批量载荷峰值内存 ≤ 实体数据大小的 2 倍

**Constraints**: 保持 `@GXMyBatisListener` 与 `GXMybatisListenerService` 的二进制兼容（只增不改）；不新增运行时第三方依赖；不得破坏既有同步/异步默认语义；所有变更须通过 `mvn -pl leaf-base-datasource -am test` 与全量编译；单模块内完成，跨模块改动需显式说明

**Scale/Scope**: 6 个写操作切面、2 个分发器、14 个事件类型、8 个事件载体、1 个执行器配置；目标并发验证强度 200 线程 × 7 类写操作

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 本计划的约束动作 | 结论 |
| --- | --- | --- |
| I. 模块自治与按需装配 | 改动限定在 `leaf-base-datasource`；不新增模块、不改变依赖方向；新增类型均放在本模块既有包内 | PASS |
| II. 稳定契约优先 | 注解只新增带默认值的属性；回调接口只新增 `default` 方法；配置键只新增不重命名；`publishEvent`/`publishEventAfterCommit` 语义保持不变 | PASS（附带迁移说明，见 Complexity Tracking） |
| III. 统一响应与统一异常 | 本能力无 HTTP 输出；内部失败继续使用 `GXBusinessException` 语义，不新增异常层级；错误码沿用现有状态码枚举 | PASS |
| IV. 显式配置与双配置源 | 执行器由 `@Value` 迁移为 `@ConfigurationProperties`（键名不变）；新增开关与容量项写入 `additional-spring-configuration-metadata.json`；不引入 Nacos 专属配置（本能力非外部化敏感配置） | PASS |
| V. 空值安全与上下文纪律 | 新增包的 `package-info.java` 标注与模块一致；缓存使用有界 Caffeine；异步上下文包装保留 `finally` 恢复 | PASS |
| VI. 测试与静态检查门槛 | 每个缺陷配可复现的回归测试；并发场景补 Spring Boot 集成测试；性能断言以可重复夹具验证；不使用 JUnit 4 | PASS |
| VII. 命名与包结构一致性 | 新增类型遵循 `GX<能力><角色>`：`GXMyBatisListenerConfigResolver`、`GXMyBatisListenerEventPublisher`、`GXMyBatisListenerMetrics`、`GXMyBatisListenerProperties` | PASS |

**初判**：无违反项。唯一需要显式声明的行为变化是"同步回调异常默认不再向业务调用方传播"——这属于缺陷修复（FR-006），将通过开关保留旧行为，记入 Complexity Tracking 并在迁移说明中公示。

## Project Structure

### Documentation (this feature)

```text
specs/001-mybatis-listener-audit/
├── plan.md              # 本文件
├── research.md          # Phase 0：12 项技术决策与备选方案
├── data-model.md        # Phase 1：事件、声明、载荷、投递结果、指标快照
├── quickstart.md        # Phase 1：可执行的验证场景与预期结果
├── contracts/           # Phase 1：公共 API 与配置契约
│   ├── listener-annotation.md
│   ├── listener-callback.md
│   ├── event-payload.md
│   └── configuration.md
└── tasks.md             # Phase 2（/speckit.tasks 生成，本命令不产出）
```

### Source Code (repository root)

```text
leaf-base-datasource/src/main/java/cn/maple/core/datasource/
├── annotation/
│   └── GXMyBatisListener.java                     # 新增可选属性（默认值保持旧行为）
├── aspect/mybatis/
│   ├── GXMyBatisPlusSaveEntityAspect.java         # 改为委托统一发布组件
│   ├── GXMyBatisPlusUpdateEntityAspect.java       # 保留条件解析与缓存，补降级原因
│   ├── GXMyBatisPlusUpdateFieldAspect.java
│   ├── GXMyBatisPlusDeleteSoftAspect.java         # 修正泛型转换签名
│   ├── GXMyBatisPlusDeleteAspect.java
│   ├── GXMyBatisPlusSaveBatchEntityAspect.java
│   └── support/                                   # 新增：内部支撑（package-info 同步补齐）
│       ├── GXMyBatisListenerConfigResolver.java   # 声明解析 + 有界缓存 + 空声明短路
│       ├── GXMyBatisListenerEventPublisher.java    # 去重、分模式投递、计数、日志
│       ├── GXReadOnlyPayloadUtils.java            # 多监听方共享只读载荷（同一集合只复制一次）
│       └── GXMyBatisListenerPayloadUtils.java     # 条件/更新字段渲染与快照探针（三切面单一口径）
├── listener/
│   ├── GXMyBatisBaseListener.java                 # 逐监听器隔离与降级记录
│   ├── GXMyBatisSyncListener.java
│   └── GXMyBatisAsyncListener.java
├── config/
│   ├── GXMyBatisListenerProperties.java           # 新增：类型安全配置
│   └── GXMyBatisAsyncListenerExecutorConfig.java   # 迁移配置读取，统一池化/虚拟线程语义
├── enums/
│   └── GXMyBatisListenerDegradeReasonEnums.java   # 新增：降级/拒绝原因
├── service/
│   └── GXMyBatisListenerMetricsService.java       # 新增：只读计数快照
└── util/
    └── GXMyBatisListenerAnnotationUtils.java      # 复用，不改签名

leaf-base-datasource/src/main/resources/META-INF/
└── additional-spring-configuration-metadata.json   # 新增配置项提示

leaf-base-datasource/src/test/java/cn/maple/core/datasource/
├── aspect/mybatis/
│   ├── GXMyBatisListenerAspectRegressionTest.java  # 扩展现有用例
│   ├── GXMyBatisListenerDeduplicationTest.java     # 新增：重复声明/多接口去重
│   └── GXMyBatisListenerConcurrencyTest.java       # 新增：200 并发 + 上下文隔离
├── config/
│   └── GXMyBatisAsyncListenerExecutorConfigTest.java # 补虚拟线程/开关/停机用例
└── perf/
    └── GXMyBatisListenerOverheadTest.java          # 新增：无声明短路与开销上限
```

**Structure Decision**: 采用"单模块内增量改造"。不新建模块、不移动既有类，只在 `aspect/mybatis` 下新增 `support` 子包承载去重与发布职责，避免 6 个切面继续复制同一段发布逻辑；其余改动均为既有类的内部调整。

## Phase 0 研究结论（详见 research.md）

12 项决策已全部收敛，无 NEEDS CLARIFICATION 遗留。关键取舍：

1. 去重键 = "监听器类型 + 事件种类"，按接口名排序保证确定性（替代"只取第一个声明"会丢监听器）。
2. 同步回调异常默认隔离并计数，提供开关恢复旧传播行为（替代"直接改为提交后投递"会破坏既有语义）。
3. 事务边界保持现状：同步=事务内、异步=提交后；新增可选 `sync-after-commit` 开关。
4. 声明解析结果做有界缓存并缓存"无声明"结论，实现无监听零分配。
5. 执行器配置迁移到 `@ConfigurationProperties`，虚拟线程并发上限显式化，校验执行器尊重同一开关。
6. 可观测性用模块内 `LongAdder` 计数 + 结构化日志，不引入 Micrometer 强依赖。

## Phase 1 设计产出

- **data-model.md**：6 类实体（写操作事件、监听声明、回调载荷、投递任务、投递结果、指标快照）及其字段、校验规则与状态流转。
- **contracts/**：4 份契约（注解、回调接口、事件载荷、配置），明确"可新增不可修改"的兼容边界与线程/异常语义。
- **quickstart.md**：8 个可执行验证场景，覆盖回滚、重复声明、条件降级、并发上下文、队列饱和、优雅停机、开关回退、开销门禁。

## Phase 1 后宪法复核

| 原则 | 复核结果 |
| --- | --- |
| I | 依然单模块；新增 6 个类型（2 个支撑类、1 个属性类、1 个枚举、1 个只读服务接口、1 个指标实现），均在本模块包内，无新依赖 → PASS |
| II | 注解新增属性全部带默认值；回调接口仅新增 default 方法；事件类型枚举只追加。发现一处需公示的行为修正（同步异常默认隔离）→ PASS + 迁移说明 |
| III | 未新增异常层级；降级/拒绝通过枚举原因 + 计数表达 → PASS |
| IV | 配置迁移保持键名不变；新增键全部登记元数据 → PASS |
| V | 新增包补 `package-info.java`；缓存有界；上下文恢复路径不变 → PASS |
| VI | 每个缺陷均映射到回归用例；性能断言可重复执行 → PASS |
| VII | 命名与包结构与既有约定一致 → PASS |

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| 同步回调异常默认由"传播"改为"隔离" | 现有行为会让已成功的写操作因监听方异常被连带回滚（FR-006）；隔离才能满足"监听不改变业务结果" | 保持传播：缺陷保留，业务成功率受监听质量影响；改为提交后投递：改变同步语义，下游可能依赖事务内回调 |
| 新增 6 个内部类型（解析器、发布器、配置属性、降级原因枚举、指标服务接口及其实现） | 6 个切面各自复制发布逻辑，无法统一去重、计数与降级；配置散落 `@Value` 违反宪法 IV | 只在各切面内打补丁：去重与计数会重复实现 6 份，后续必然漂移；合并进单个巨型工具类：违反单一职责 |
| 手写微基准而非引入 JMH | 宪法要求新增依赖需说明理由；本特性只需"相对基线不劣化"的门禁，不需要统计级基准 | 引入 JMH：新增测试依赖与维护成本，收益与目标不匹配 |
