# Implementation Plan: leaf-base-redisson 逻辑正确性、线程安全与性能审查

**Branch**: `004-redisson-module-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-redisson-module-audit/spec.md`

## Summary

本次交付是**审查产物**，不改生产代码。三件事，顺序不可颠倒：

1. **结论独立成文**：把 13 条证据（E-01～E-13）与 16 条发现（F-01～F-16）写入 `review-report.md`，每条附维度、级别、位置、触发条件、影响、证据、复现方式与回归测试设计，满足 SC-001/SC-002/SC-007。
2. **用依赖侧源码把"读代码推测"升级为"可核对事实"**：从本地仓库 `redisson-4.7.0-sources.jar` 核对 `RedissonReliableTopic`、`RedissonBlockingQueue`、`RedissonDelayedQueue`、`QueueTransferService` 的实际语义，修正三处容易误判的结论（详见 Phase 0）。
3. **建立不依赖真实 Redis 的审计验证基线**：沿用既有测试风格（JUnit 5 + Mockito + 反射），为高危发现各补一条"特征化用例（characterization test）"，锁住当前行为；修复落地时这些用例的断言必须翻转，因此它们同时充当"修复验收开关"。

技术路径刻意保持零运行时改动：`pom.xml` 不新增依赖，`src/main` 不新增/修改文件，仅在 `src/test/java/cn/maple/redisson/audit/` 下新增审计用例。性能结论以"往返次数 × 单轮上限 × 锁持有窗口"的可复算口径为主，压测不在范围内（见 Assumptions）。

## Technical Context

**Language/Version**: Java 21（根 POM `maven.compiler.release=21`；虚拟线程为 `Executors.newThreadPerTaskExecutor` + `Thread.ofVirtual()`）

**Primary Dependencies**: Redisson 4.7.0（`org.redisson:redisson`、`org.redisson:redisson-spring-cache`）、Spring Boot 4.1.x / Spring Framework 7.x、Hutool（`CharSequenceUtil`）、Guava（`ThreadFactoryBuilder`）、Lombok、Jackson（`JsonJacksonCodec`）；测试侧 JUnit 5（Jupiter）、Mockito、`spring-boot-starter-test`

**Storage**: Redis（审查对象，本次不改造、无 schema 变更）；涉及 `RBlockingQueue`/`RDelayedQueue`/`RReliableTopic`/`RStream`/`RMapCache`/`RLock` 六类数据结构

**Testing**: `mvn -pl leaf-base-redisson test`（可加 `-o` 离线执行，`leaf-base-framework:4.3.0-SNAPSHOT` 已安装到本地仓库）；既有 `RedissonUtilityRegressionTest` 38 个用例为行为基线

**Target Platform**: JVM 21 库，被 Spring Boot 4.x 应用以 Maven 依赖方式引入；单机与集群连接形态均需覆盖

**Project Type**: Maven 多模块库（library），无独立可执行入口

**Performance Goals**: 不设吞吐指标，设**口径指标**——三条关键路径（延迟队列转发、stream 立即消费、stream 延迟转移）各给出：单条消息 Redis 往返次数、单轮/单批处理上限、临界区持锁范围、队列与内存上界；数值必须可由代码路径逐项复算（SC-004）

**Constraints**:

- 不改生产代码：`src/main/**` 与 `pom.xml` 零改动；新增代码仅限 `src/test/**`
- 不依赖真实 Redis、不依赖外部中间件；审计用例必须能在无 Redis 环境执行（Mock）或优雅跳过
- 不修改编译参数绕过 ErrorProne/NullAway；新增测试代码同样要能编译通过
- 日志与断言消息用 ASCII；报告中文描述、代码位置用 `文件:行区间`
- `RDelayedQueue` 在 4.7.0 已被上游标记弃用（构造时打印 error 日志），审查必须把"沿用弃用 API"登记为升级风险，但**本次不改用它**
- 结论分级口径固定：缺陷（与承诺不符且可复现）/ 风险（当前可接受但条件变化即失效）/ 既定契约（已被既有测试锁定）/ 未验证假设（无法在无 Redis 环境证实）

**Scale/Scope**: 51 个生产源码文件 + 1 个 `application.yml` + 1 个测试类（38 用例）；交付 1 份审查报告 + 5 份设计产物；生产代码改动 0，新增审计用例 ≤ 8 个文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据 |
| --- | --- | --- |
| I. 模块自治与按需装配 | PASS | 不改依赖、不改装配条件；审查把"`@ConditionalOnExpression` 默认关闭 + `@ConditionalOnBean(name="redissonMQClient")`"的现状记为事实，不提出跨模块重构 |
| II. 稳定契约优先 | PASS（需登记） | 本次零生产改动，公开 API/配置键/异常语义不变。F-01/F-02/F-03 指向的"承诺与实现不符"属**已存在**的契约风险，报告单列并标注修复需另行审批，不在本规格内改 |
| III. 统一响应与统一异常 | PASS | 审查 F-10/F-11 的异常吞噬与返回值歧义（`executeWithLock` 超时返回 `null`、`getCounterValue` 异常返回 `0`），只出结论不改实现 |
| IV. 显式配置与双配置源 | PASS | F-13/F-14 涉及连接地址解析与 `blockingTimeoutMillis=0`，报告需指出"配置缺省即失效"违反"禁止静默降级"；本次不新增配置键、不新增元数据 |
| V. 空值安全与上下文纪律 | PASS | 审查范围含静态缓存跨容器持有旧客户端（F-09）与线程/调度器释放路径（F-08/F-14）；审计用例需覆盖关闭路径，不新增 `ThreadLocal` |
| VI. 测试与静态检查门槛 | PASS（需登记） | 新增审计用例为 JUnit 5、无外部中间件、可跳过；不触碰编译参数。**已知偏差登记**：当前 51 个生产文件仅由 1 个测试类覆盖，报告须给出"未被任何用例触及的类型清单"，作为后续测试补齐的输入 |
| VII. 命名与包结构 | PASS | 新增测试放 `cn.maple.redisson.audit`，命名 `GX<被测能力>AuditTest`；沿用 `GX` 前缀与 SLF4J 门面 |

**Gate 结论**：通过，0 违规项，无需豁免。跨模块改动 0 处。

## Project Structure

### Documentation (this feature)

```text
specs/004-redisson-module-audit/
├── spec.md              # 需求（已完成，含 2 条会话澄清）
├── plan.md              # 本文件
├── research.md          # Phase 0：技术决策、依赖侧事实核对、口径定义
├── data-model.md        # Phase 1：Finding / 证据 / 审查矩阵 / 性能口径 / 覆盖率台账
├── quickstart.md        # Phase 1：可执行验证指南（命令 + 预期输出）
├── contracts/
│   ├── review-report-schema.md       # 审查报告条目结构、级别定义、证据等级、校验规则
│   └── audit-coverage-contract.md    # 覆盖矩阵、链路可靠性判定口径、性能口径、缺陷 vs 契约判定
├── review-report.md     # 实现阶段交付：逐条 Finding + 证据 + 复现命令
└── tasks.md             # Phase 2（/speckit.tasks 生成，本命令不创建）
```

### Source Code (repository root)

```text
leaf-base-redisson/
├── pom.xml                                     # 不改
├── src/main/java/cn/maple/redisson/            # 不改（审查对象，51 个文件）
│   ├── adapter/ annotation/ config/ listener/ processor/
│   ├── properties/{,local,nacos}/
│   ├── services/{,impl}/
│   ├── stream/{,dto/req,handler,queue}/
│   └── util/
├── src/main/resources/application.yml          # 不改（审查对象）
└── src/test/java/cn/maple/redisson/
    ├── RedissonUtilityRegressionTest.java      # 不改（38 用例基线）
    └── audit/                                  # 新增：审计特征化用例
        ├── GXRedissonTopicSubscriptionAuditTest.java   # F-01/F-09/F-10
        ├── GXRedissonDelayForwardingAuditTest.java     # F-02/F-05/F-06/F-08
        ├── GXRedissonConsumerConcurrencyAuditTest.java # F-07/F-14
        ├── GXRedissonStreamReliabilityAuditTest.java   # F-03/F-12
        ├── GXRedissonStreamTransferAuditTest.java      # F-04
        ├── GXRedissonLockSemanticsAuditTest.java       # F-11
        └── GXRedissonConnectConfigAuditTest.java       # F-13
```

**Structure Decision**: 沿用仓库既有多模块布局，不新增模块、不新增生产包。审查对象保持只读；审计用例独立放在 `cn.maple.redisson.audit` 包，避免污染既有回归测试文件的职责边界。

## Phase 0: Research Summary

详见 [research.md](./research.md)。全部未知项已解决，无 `NEEDS CLARIFICATION` 残留。核心决策：

1. **审查方法采用"三级证据"**：源码事实（本仓库代码位置）＞依赖事实（`redisson-4.7.0-sources.jar` 实际实现）＞可执行验证（Mock 用例）。任一新结论必须落到其中一级，否则降级为"未验证假设"。
2. **修正三处易误判的既有判断**（这是本轮的主要研究收益，直接约束报告口径）：
   - `RBlockingQueue.drainTo(c, n)` 是**单次 Lua + LTRIM**（1 次往返），抓取侧不存在 N+1 往返；因此 F-05 只主张"in-flight 无界"，不主张"抓取低效"。
   - `QueueTransferService` 对 `RDelayedQueue` 采用**引用计数 + 按队列名去重**，重复 `getDelayedQueue` 只会 `incUsage`；因此 F-08 的后果是"引用计数只增不减 → 转移任务永不停止"，而非"新增一个后台任务"。
   - `RedissonReliableTopic.removeListenerAsync` **先同步移除本地 listeners、再异步清理 Redis 消费组**；异常时本地已无监听器而 `LISTENER_REGISTRATION_CACHE` 未清 → 后续 `subscribe` 返回旧 id 并被视为成功。F-01 由此从"疑似"升级为"依赖源码可证"。
3. **登记上游弃用事实**：4.7.0 的 `Redisson.getDelayedQueue` 在构造时打印 `RDelayedQueue object is deprecated ... Use RReliableQueue object instead`，且 `RedissonDelayedQueue` 显式标注该 API 弃用。审查须将其列为"升级即断"的风险（F-16 候选），但不建议本次改造。
4. **审计用例定位为特征化测试**：断言"当前（有缺陷的）行为"而非期望行为，命名与注释显式标注 `CURRENT BEHAVIOR`；修复落地时必须翻转断言，因此用例天然充当修复验收开关。
5. **性能口径解析化**：以"每消息往返次数 × 单轮上限"计算，不引入压测工具、不要求真实 Redis；可执行冒烟只作为旁证（Mock 计数 `verify` 次数即可复算）。
6. **并发验证不启容器**：沿用 `ReflectionTestUtils` + `CountDownLatch` + Mockito 静态 mock 的既有手法；仅在需要验证 Bean 生命周期语义时使用 `ApplicationContextRunner`（`spring-boot-starter-test` 已在依赖内，无需新增依赖）。

## Phase 1: Design Summary

- **data-model.md**：六个实体——Finding、Evidence、链路可靠性矩阵、并发与生命周期矩阵、性能口径、覆盖率台账；含 Finding 状态机（初稿 → 已复核 → 已定级 → 可复现 → 已交付）与"缺陷/风险/既定契约/未验证假设"的判定规则。
- **contracts/review-report-schema.md**：每条 Finding 的必填字段（编号、维度、级别、位置、触发条件、影响、证据、复现方式、回归测试设计、修复建议、契约影响），以及"可复现"的判定标准与证据等级要求。
- **contracts/audit-coverage-contract.md**：覆盖矩阵（51 文件 × 4 判定）、五条链路的可靠性定性规则、三条性能路径的计数口径、缺陷与既定契约的分界（以"是否被既有测试锁定 + 是否与文档承诺冲突"双条件判定）。
- **quickstart.md**：模块级测试命令（含离线与单类执行）、六类高风险发现的复现步骤与预期现象、性能口径复算步骤、覆盖率台账核对方式。

两条收窄结论直接约束设计：①F-05 只主张"无背压"，不主张"抓取批次低效"（与依赖事实冲突）；②F-15 明确归为"既定契约"，报告中不得计为缺陷，避免计划阶段误改。

## Post-Design Constitution Re-check

| 原则 | 判定 | 设计落地后的变化 |
| --- | --- | --- |
| I. 模块自治 | PASS | 设计只新增测试包；`pom.xml` 零改动，不引入压测/新断言库 |
| II. 稳定契约 | PASS | 契约对照表把"文档承诺 vs 实现"逐条列出，但不产生任何生产改动；修复建议统一标注"需独立审批" |
| III. 统一异常 | PASS | F-10/F-11 只出结论；审计用例用 `assertThrows`/返回值断言描述现状，不改异常类型 |
| IV. 显式配置 | PASS | F-13/F-14 结论附带"最小配置修正建议"，明确不新增配置键 |
| V. 空值安全 | PASS | 审计用例对 `null` 返回值、空消息、空地址均有显式断言；新增代码标注 JSpecify |
| VI. 测试门槛 | PASS | 7 个审计用例文件全部无外部中间件；覆盖率台账登记 51 文件的可及性 |
| VII. 命名与包结构 | PASS | 新增包 `cn.maple.redisson.audit`，命名 `GX<能力>AuditTest`，与既有 `GX<被测类型>Test` 约定不冲突 |

**结论**：设计后仍通过，无违规项。

## Complexity Tracking

> 无 Constitution Check 违规项，无需豁免说明。

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| — | — | — |
