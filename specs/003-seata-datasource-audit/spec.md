# Feature Specification: Seata 与动态数据源协作正确性审查加固（leaf-base-seata × leaf-base-datasource）

**Feature Branch**: `003-seata-datasource-audit`（本次未通过 hook 创建 git 分支）

**Created**: 2026-10-21

**Status**: Draft

**Input**: User description: "审查 leaf-base-seata 模块，当 leaf-base-datasource 结合它用时，它的行为是不是正确的？会不会影响到 leaf-base-datasource 模块的正确性。"

## 背景与审查结论摘要

本规格把"`leaf-base-seata` 与 `leaf-base-datasource` 组合使用时是否正确、是否会反向影响数据源模块"这一审查问题，转化为可验收的需求。

审查范围与证据来源：

1. `leaf-base-seata` 全部 4 个生产类与 2 个测试类；
2. `leaf-base-datasource` 中全部 Seata 引用点（1 处生产代码、4 个测试引用）；
3. 依赖侧事实：本地仓库 `org.apache.seata:seata-all:2.6.0` / `seata-spring-autoconfigure-client:2.6.0` 的类清单与官方源码；
4. 仓库内 `config/<profile>/seata.yml` 实际配置。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| `leaf-base-seata` 自身行为是否正确 | 条件装配、本地/Nacos 双配置入口、MyBatis-Plus 数据源注入三条链路均可工作；但存在 3 处装配脆弱点（无 bean 存在性条件、无自动装配注册、配置入口资源路径脆弱） | 基本正确，存在风险 |
| 与数据源模块协作是否真正生效 | 物理数据源确实被 Seata 代理（旧命名空间兼容类委托到新实现，已核对 jar 与源码），AT 资源注册发生在代理构造期；但两侧使用的类名分属新旧两个命名空间 | 当前可用，升级即断 |
| 是否影响数据源模块正确性 | 路由键栈、上下文恢复、事务边界、Seata XID 传播未被破坏（已有测试覆盖）；但数据源暴露的类型契约被改变、启动期新增硬依赖、代理策略依赖外部配置 | 有边界风险，可约束 |

关键证据（均可复现）：

- E-01：`leaf-base-datasource` 的 `GXDynamicDataSourceConfig.wrapSeataDataSource` 以反射方式实例化 `io.seata.rm.datasource.DataSourceProxy`（第 79–92 行）。
- E-02：`leaf-base-seata` 的 `GXSeataDynamicDataSourceConfig` 以 `org.apache.seata.rm.datasource.SeataDataSourceProxy` 作为装配条件（第 24–27 行）；两侧契约判定口径不一致。
- E-03：核对 `seata-all-2.6.0.jar`，同时存在 `io/seata/rm/datasource/DataSourceProxy.class` 与 `org/apache/seata/rm/datasource/DataSourceProxy.class`；前者标注 `@Deprecated`，内部持有一个新命名空间代理实例并逐方法委托，且实现同一 `SeataDataSourceProxy` 接口。故当前版本代理确实生效，而非失效。
- E-04：`org/apache/seata/spring/boot/autoconfigure/properties/SeataProperties#enableAutoDataSourceProxy` 默认值为 `true`；`SeataAutoDataSourceProxyCreator.wrapIfNecessary` 会跳过 `AbstractRoutingDataSource`，因此路由数据源不会被自动代理二次包装。
- E-05：`org/apache/seata/rm/datasource/DataSourceProxy` 在构造链路中调用 `DefaultResourceManager.get().registerResource(this)`，手工 `new` 代理同样完成资源注册。
- E-06：仓库 `config/dev|test|pre|prod/seata.yml` 均显式设置 `enable-auto-data-source-proxy: false`。
- E-07：`leaf-base-seata` 与 `leaf-base-datasource` 均未提供 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，装配依赖使用方的包扫描或显式导入。
- E-08：`docs/leaf-base-seata开发手册.md` 描述的仍是旧实现（自定义 `MybatisSqlSessionFactoryBean`、`@AutoConfigureBefore`、`io.seata` 条件、Nacos 判定条件写错），与现有代码不一致。

发现清单（分级）：

- **F-01（高）失效路径静默降级**：代理类缺失时只输出 debug 日志并返回原始数据源，应用照常启动。此时全局事务"看起来成功"但分支未纳入 AT 管理，属于最难排查的一类缺陷，且与项目宪法"禁止静默降级"冲突。
- **F-02（高）跨模块类契约不一致**：装配条件判定新命名空间、实际实例化旧命名空间。兼容层一旦被上游移除（或裁剪），条件仍成立而代理消失，且当前无任何测试锁定"要么代理成功、要么显式失败"。
- **F-03（中）装配期硬依赖**：`seataDynamicDataSourceCustomizer` 以 `GXDynamicDataSource` 作为方法参数，但条件注解只校验类型存在。若使用方未装配动态数据源（自定义数据源、裁剪模块），会以 `NoSuchBeanDefinitionException` 启动失败，而不是按条件跳过。
- **F-04（中）本地配置入口脆弱**：`classpath:/${spring.profiles.active}/seata.yml` 且 `ignoreResourceNotFound = false`。profile 未设置、或多环境配置放在外部 `config/<profile>/` 目录（仓库现状）而不在类路径时，启动直接失败。
- **F-05（中）双配置源判定口径与文档不一致**：本地入口以 `com.alibaba.cloud.nacos.NacosConfigManager` 缺席为条件，Nacos 入口额外要求 `com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties`；文档描述的是旧条件，存在"只装 Nacos 客户端、未接入 Spring Cloud Alibaba 配置"的中间态。
- **F-06（中）代理策略依赖外部配置**：手工代理方案的正确性依赖 `seata.enable-auto-data-source-proxy=false` 这一外部键。Seata 会跳过路由数据源，但同一应用中其它 `DataSource` bean 仍会被自动代理，形成"手工 + 自动"两套策略并存的混合状态。本次仅以文档说明该边界，不承诺运行时检测（见 FR-007）。
- **F-07（低）文档漂移**：模块手册与 `AGENTS.md` 的描述（旧的工厂定制方式、`DataSourceProxy` 条件类名）已与实现脱节，会把维护者引向错误的排障方向。
- **已确认无缺陷项**：数据源路由键栈语义、"`push`/`poll` 成对恢复"、跨数据源与 `REQUIRES_NEW` 下 Seata XID 与全局锁标记的保留，均有测试覆盖；重复代理风险被 Seata 自身跳过 `AbstractRoutingDataSource` 规避。

## Clarifications

### Session 2026-10-21

- Q: 本次交付是"只给审查结论和测试基线、不改生产代码"，还是"结论加直接修复已确认的缺陷"？ → A: 结论 + 最小修复：审查结论与测试基线必交付；F-01/F-02/F-03 等已确认缺陷做最小改动修复，优先局限在 `leaf-base-seata` 内，跨模块须单独说明影响面。
- Q: 本次改造是否只锚定当前基线版本（Seata 2.6.0），统一改用新命名空间的那套 Seata 数据源代理类型？ → A: 仅锚定 Seata 2.6.0：装配条件与实例化统一使用新命名空间类型，移除对旧命名空间兼容类的依赖；旧命名空间缺失或未来被移除时按"代理缺失"显式告警或严格失败。
- Q: 当 Seata 代理无法建立时，默认应该"告警后继续用未代理的数据源"，还是"直接启动失败"？ → A: 默认告警继续：由 Seata 集成侧输出含数据源名称的 warn 日志并沿用原始数据源；通过 `maple.framework.enable.*` 严格开关切换为启动失败，默认值不改变下游现有启动表现。
- Q: 当 profile 未设置或对应 profile 下找不到 `seata.yml` 时，期望的行为是"继续启动失败但信息更明确"，还是"自动跳过 Seata 集成"？ → A: 保持失败：仍启动失败，但错误信息明确给出当前 profile、期望的资源路径与配置来源；文档同步修订路径约定，不引入"缺配置即跳过"的降级分支。
- Q: 审查结论以什么形式交付——是在特性目录下单独产出一份可逐条复核的审查报告，还是只保留在 `spec.md` 的结论摘要里？ → A: 独立审查报告：在特性目录下产出审查报告（逐条 Finding、证据、复现命令、现场片段），`spec.md` 只保留摘要与需求引用。
- Q: 对于"自动数据源代理与手工代理并存"，本次规格是要求运行时检测并告警，还是只承诺"路由层不会被二次代理"这一可测试的事实？ → A: 收窄承诺：路由数据源在任何配置下都不被 Seata 自动代理；每个物理数据源最多一层代理；由类型断言测试固定，不承诺检测应用内其它数据源 bean 的自动代理（属使用方配置范畴，仅文档说明）。
- Q: 当应用只引入 `leaf-base-datasource`、类路径里根本没有 Seata 时，"代理未建立"应该记成告警还是普通调试信息？ → A: 分级归属：类路径无 Seata 属正常形态，数据源侧只记 debug；warn 只由 seata 模块在"Seata 集成已装配但目标源未被代理"时发出。
- Q: SC-005 要求 4 种配置组合的行为与文档一致，是否要求这 4 种组合各自都有独立的自动化用例？ → A: 4 种组合各 1 个自动化用例（本地生效 / Nacos 生效 / profile 缺失失败 / 资源缺失失败），全部无外部依赖；缺任一组合用例视为不满足。
- Q: "启用 Seata 后行为零差异"的验收范围，是否要覆盖规格里列出的全部六类既有行为（路由、上下文、事务传播、数据权限与租户、分页、防全表更新与自动填充）？ → A: 路由与上下文做逐项对照；另加一条"分页/租户/防全表更新拦截器仍在执行链路"的存在性冒烟；其余行为由数据源模块既有测试保证，不纳入本次新增对照。
- Q: US1 里那条"依赖 jar 中新旧两个命名空间类都存在且旧类委托新类"的验收场景，应保留为历史证据还是改写成目标状态？ → A: 保留并限定用途：仅用于解释 F-02 成因、不构成目标状态，断言中不要求任何代码使用旧类。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 审查结论可复核、可定位 (Priority: P1)

框架维护者在评估"是否可以在生产启用 Seata"时，需要拿到分级结论，而不是笼统的"看起来没问题"。每条结论都要能直接跳到代码位置，并有一键可复现的验证方式（执行某条命令或某个测试即可重现该判断）。

**Why this priority**: 审查类工作的唯一价值就是"结论可信"。结论若不能复核，后续 `/speckit.plan` 与修复都会建立在猜测之上。

**Independent Test**: 逐条抽取审查结论，按给定位置打开代码、执行给定命令，断言"看到的现象与结论描述一致"；对 F-01/F-02 这类风险项，必须能构造出"代理失效但应用仍启动"的现场。

**Acceptance Scenarios**:

1. **Given** 一条"代理当前确实生效"的结论，**When** 按给出的类清单核对命令检查依赖 jar，**Then** 可确认新旧命名空间两个类同时存在且旧类委托新类——该结论**仅用于解释 F-02 的成因**，不构成目标状态，也不要求任何代码依赖旧类。
2. **Given** 一条"代理失效时静默降级"的结论，**When** 屏蔽代理类后启动隔离上下文，**Then** 应用正常启动、仅出现 debug 日志、数据源未被包装，现象与结论一致。
3. **Given** 一条"配置入口在 profile 缺失时启动失败"的结论，**When** 在隔离上下文中去掉 profile 配置，**Then** 复现启动失败并给出定位信息。

---

### User Story 2 - 代理不生效时必须显式暴露 (Priority: P1)

运维与开发在启用 Seata 后，必须能在启动阶段确认"哪些物理数据源被代理、用的是哪个配置来源"。当 Seata 已启用、而某个物理数据源没有被代理时，宁可看到明确告警（或按开关直接失败），也不能拿到一个静默未代理的数据源继续跑业务。

**Why this priority**: 分布式事务的失效是"功能正确性"级别的失败，且不会在单机路径上暴露。静默降级等于把故障推迟到资金/库存对账环节。

**Independent Test**: 构造"Seata 已装配但目标源未代理"与"目标源已代理"两种隔离上下文，断言前者产生含数据源名称的告警或按严格模式失败、后者输出被代理的数据源清单与配置来源。

**Acceptance Scenarios**:

1. **Given** Seata 集成已装配（代理类型在类路径上）但某个目标数据源未被代理，**When** 应用启动，**Then** 输出一条 warn 级日志，明确指出未代理的数据源名称与原因，且不改变原有"继续使用原始数据源"的可用行为。
2. **Given** 开启"代理缺失即失败"的严格开关，**When** 存在未被代理的目标数据源，**Then** 启动失败，错误信息包含数据源名称与缺失的契约类型。
3. **Given** Seata 可用且配置来源为本地文件，**When** 启动完成，**Then** 日志可检索到"数据源已代理"的清单与"配置来源 = 本地"。
4. **Given** Seata 可用且配置来源为 Nacos，**When** 启动完成，**Then** 日志可检索到"配置来源 = Nacos"，且不存在本地配置同时生效的迹象。

---

### User Story 3 - 装配边界不牵连数据源模块 (Priority: P2)

使用方可能在只引入 `leaf-base-seata` 的情况下自行提供数据源，或裁剪掉动态数据源能力。此时 Seata 集成应安静地跳过，而不是让整个应用启动失败。

**Why this priority**: 基础框架模块的装配条件必须"缺少前置能力就不参与"，任何隐式硬依赖都会在下游项目以启动崩溃的形式暴露。

**Independent Test**: 在只有 Seata 依赖、没有动态数据源 bean 的隔离上下文中启动，断言上下文启动成功且 Seata 相关 bean 不存在；再补齐动态数据源 bean，断言注入生效。

**Acceptance Scenarios**:

1. **Given** 类路径存在 Seata 与数据源模块，但容器中没有动态数据源 bean，**When** 启动，**Then** 启动成功，Seata 模块的相关 bean 不装配，且输出一条可检索的跳过原因。
2. **Given** 动态数据源 bean 存在，**When** 启动，**Then** MyBatis-Plus 最终使用的数据源就是该动态数据源。
3. **Given** 使用方自定义了 `SqlSessionFactory`，**When** 启动，**Then** 框架不覆盖使用方的定制，并在日志中说明未接管的原因。

---

### User Story 4 - 启用 Seata 不改变数据源模块既有行为 (Priority: P2)

启用 Seata 后，数据源路由、数据权限、租户、分页、防全表更新、自动填充、上下文恢复、连接释放这些既有能力必须保持原样；本次验收以"路由与上下文逐项对照 + 拦截器链与自动填充的存在性冒烟"为准，其余能力由既有测试保证。包装带来的类型差异必须以正向契约书面声明（如何获取底层连接池），而不是"用到了才发现"。

**Why this priority**: 这两件事同时发生才构成用户真正担心的"seata 会不会把 datasource 搞坏"。

**Independent Test**: 同一组数据源装配在"有 Seata 代理"与"无 Seata 代理"两种形态下分别执行路由与上下文用例，断言结果一致；断言分页/租户/防全表更新拦截器与自动填充仍在执行链路中；并断言包装后数据源在销毁路径上仍释放底层连接池。

**Acceptance Scenarios**:

1. **Given** 同一份多数据源配置，**When** 分别以"包装"与"未包装"形态运行路由与上下文恢复用例，**Then** 访问到的物理连接序列完全一致。
2. **Given** 数据源已被 Seata 包装，**When** 容器关闭，**Then** 底层连接池被关闭且未出现重复关闭或资源泄漏告警。
3. **Given** 数据源已被 Seata 包装，**When** 上游需要连接池实例，**Then** 可通过 `getTargetDataSource()` 取得原始实例，且行为与 `contracts/seata-assembly-contracts.md` 的类型契约一致，并有测试固定该结论。
4. **Given** 处于全局事务上下文中并发生跨数据源切换，**When** 执行嵌套调用，**Then** 全局事务标识与全局锁标记在每次切换后仍然可见。
5. **Given** 数据源已被 Seata 包装，**When** 执行一次分页查询与一次租户过滤写入，**Then** 分页/租户/防全表更新拦截器仍被调用，自动填充字段仍按原规则写入。

---

### User Story 5 - 配置入口与文档一致 (Priority: P3)

维护者依赖文档判断"配置该放哪里、缺了会怎样"。本地文件与 Nacos 两条入口的启用条件、资源路径、必需键、失败语义必须与文档描述一致，不允许"文档说 A、运行时是 B"。

**Why this priority**: 配置漂移造成的启动失败排查成本高，但不影响事务正确性，故优先级低于前四项。

**Independent Test**: 按文档构造 4 种配置组合（本地存在 / Nacos 存在 / profile 缺失 / 资源缺失），逐一启动隔离上下文，断言实际行为与文档一致。

**Acceptance Scenarios**:

1. **Given** 使用方未接入 Nacos 配置中心，**When** 提供对应 profile 的 `seata.yml`，**Then** 配置被加载且只有本地入口生效。
2. **Given** 使用方已接入 Nacos 配置中心，**When** 启动，**Then** 只有 Nacos 入口生效，本地入口不装配。
3. **Given** 当前 profile 下缺少 `seata.yml`，**When** 启动，**Then** 失败信息明确指出缺失的资源路径与 profile，而非仅抛出通用资源异常。
4. **Given** 文档中描述的条件、类名、路径与实现不一致，**When** 交付本次变更，**Then** 文档已按实现修订，且修订点可在评审中逐条对照。

---

### Edge Cases

- 未设置 `spring.profiles.active` 时，本地配置入口必须启动失败，且错误信息指出"profile 未设置"，不得以未解析的占位符路径继续尝试。
- `seata.yml` 放在仓库 `config/<profile>/` 外部目录而未进入类路径时，必须启动失败，且错误信息给出期望的类路径位置与当前 profile。
- 同时存在 Nacos 客户端与本地 `seata.yml`：必须只有一条入口生效，另一条不装配。
- 应用定义了自定义 `SqlSessionFactory` 或多个 `DataSource` bean 时，框架的定制器是否仍强行接管。
- 物理数据源被第三方再包一层（连接池代理、日志代理）时的代理顺序与资源标识稳定性。
- Seata Server 不可用、或业务库缺少回滚日志表时，第一阶段与第二阶段的失败表现，以及是否会被误判为"数据源模块故障"。
- 上游版本移除（或使用方裁剪）新命名空间代理类型后，装配条件不再成立时的降级路径与日志表现。
- 多个逻辑数据源指向同一物理库时，资源标识是否冲突并导致分支注册互相覆盖。
- 全局事务中通过数据源注解切换数据源，分支注册数量与预期是否一致。
- 容器关闭时，包装后的数据源与底层连接池的关闭顺序与重复关闭保护。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖 `leaf-base-seata` 全部生产代码文件，以及 `leaf-base-datasource` 中全部 Seata 相关引用点；每条结论必须标注代码位置与判定级别（无缺陷 / 风险 / 缺陷）。
- **FR-002**: 每条结论 MUST 附带可复现的验证方式（测试、隔离上下文启动、依赖类清单核对、日志片段），不得只凭阅读下结论。
- **FR-003**: 判定"是否具备代理能力"所用的类型与实例化代理所用的类型 MUST 属于同一命名空间与同一生命周期承诺，并 MUST 统一使用当前基线版本（Seata 2.6.0）的新命名空间类型；不得一侧依赖已弃用兼容类、另一侧依赖新类型，也不得为旧命名空间保留回退分支。
- **FR-004**: 当 Seata 集成已装配、但存在未被代理的物理数据源时，系统 MUST 产生 warn 及以上级别的可检索日志，包含数据源名称与原因。类路径不存在 Seata 代理类型属正常形态（未启用 Seata），数据源侧 MUST 仅记录 debug，不得产生告警；禁止把"已启用但未代理"降级为 debug。
- **FR-005**: 系统 MUST 提供"代理缺失即启动失败"的严格开关；默认值 MUST 保持现有可用行为，不得改变既有下游启动结果。
- **FR-006**: Seata 集成 MUST 以"动态数据源 bean 实际存在"为装配前提；bean 缺失时 MUST 跳过装配且不影响应用启动。
- **FR-007**: 任一物理数据源在任一时刻 MUST 最多被一层 Seata 代理包裹；路由数据源 MUST NOT 被 Seata 自动代理二次包裹。上述两条 MUST 由类型断言测试固定。框架 MUST NOT 承诺检测"应用内其它数据源 bean 被自动代理"的并存情形——该情形属使用方配置范畴，只需在模块文档中说明。
- **FR-008**: 数据源被包装后对外暴露的类型契约 MUST 书面化（哪些类型断言不再成立、应改用何种方式获取底层连接池），并 MUST 有测试固定该契约。
- **FR-009**: 启用 Seata MUST NOT 改变数据源路由键语义与上下文栈恢复，二者 MUST 有逐项对照用例；同时 MUST 保持 MyBatis-Plus 拦截器链（数据权限/租户、分页、防全表更新）与自动填充处于执行链路中，MUST 有至少一条存在性冒烟用例。其余既有行为由数据源模块既有测试保证，不纳入本次新增对照范围。
- **FR-010**: 本地与 Nacos 两条配置入口 MUST 保持互斥；资源路径、必需键、失败语义 MUST 与文档一致，且失败信息 MUST 指出缺失的具体路径与环境。profile 未设置或对应配置资源缺失时 MUST 保持启动失败，不得自动跳过 Seata 集成。
- **FR-011**: 装配阶段 MUST 输出可检索的 ASCII 日志，至少包含：是否启用 Seata、被代理的数据源清单、生效的配置来源。
- **FR-012**: 上述行为 MUST 有不依赖真实 Seata Server 与 Nacos 的自动化测试基线，覆盖装配、代理形态、上下文传播、失败与边界路径。
- **FR-013**: 修复 MUST 优先限制在 `leaf-base-seata` 模块内；确需改动 `leaf-base-datasource` 时，MUST 给出最小影响面说明与回归证明，并保持既有成功路径行为不变。
- **FR-014**: 模块手册与 `AGENTS.md` 中关于条件装配、契约类名、配置键与资源路径的描述 MUST 与实现一致。
- **FR-015**: 本次交付 MUST 包含审查结论、测试基线与已确认缺陷（F-01/F-02/F-03）的最小修复；代理体系重构、模块职责重划与大范围装配重写 MUST 排除在本次范围之外，另立规格。
- **FR-016**: 审查证据 MUST 独立成文于特性目录（审查报告），逐条包含 Finding 编号、级别、代码位置、触发条件、影响、证据、复现命令与现场片段；`spec.md` 只保留摘要与对该报告的引用，且两者在交付时必须相互一致。

### Key Entities *(include if data involved)*

- **审查结论（Finding）**: 编号、严重级别、代码位置、触发条件、影响面、证据、复现方式、修复建议；用于驱动后续计划与任务拆分。
- **协作场景矩阵**: 场景（代理生效 / 代理缺失 / 装配缺失 / 多数据源路由与行为对照 / 配置互斥）× 责任模块 × 预期行为 × 守护测试。
- **数据源包装契约**: 包装后类型、资源标识来源、关闭责任归属、可用的类型探测方式；供上游模块与使用方共同遵守。
- **配置入口契约**: 来源（本地 / Nacos）、启用条件、资源路径、必需键、缺失与非法时的失败语义。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查结论覆盖 100% 的 `leaf-base-seata` 生产类与 100% 的 `leaf-base-datasource` Seata 引用点；每条结论都记录在独立审查报告中，可在 1 次阅读内定位到代码位置与复现方式。
- **SC-002**: 至少 8 个可自动执行的验证用例覆盖 5 类关键协作场景（代理生效、代理缺失、装配缺失、包装后行为对照、配置互斥），在无外网、无 Seata Server、无 Nacos 的环境下 100% 通过。
- **SC-003**: "Seata 已装配但目标源未代理"路径 100% 产生包含数据源名称的 warn 及以上日志；严格模式下 100% 启动失败并给出缺失契约；未装配 Seata 的路径 0 告警。
- **SC-004**: 启用 Seata 后，数据源模块既有测试 100% 通过；包装前后对照用例除书面声明的类型契约差异外 0 行为差异（对照范围：路由与上下文逐项，加拦截器链与自动填充的存在性冒烟）。
- **SC-005**: 4 种配置组合（本地存在 / Nacos 存在 / profile 缺失 / 资源缺失）的实际行为与文档描述 100% 一致；4 种组合 MUST 各自有独立的自动化用例，任一组合缺少用例即视为不满足。
- **SC-006**: 新维护者可在 15 分钟内按审查报告中的命令复现任一审查结论，无需额外口头说明。

## Assumptions

- 目标运行形态为：业务应用同时引入 `leaf-base-seata` 与 `leaf-base-datasource`，Seata 客户端版本 2.6.0，多环境配置由外部 `config/<profile>/` 目录提供。
- 本次验收不引入真实 Seata Server 与 Nacos 做端到端验证；AT 第二阶段（回滚日志写入与回滚）不作为本次验收项，只覆盖"代理是否生效、资源是否注册、上下文是否保留"的可验证部分。
- 默认保持"代理缺失时继续运行"的现有行为不变，仅在显式开启严格开关时才失败，以满足向下兼容要求。
- 允许对 `leaf-base-seata` 做最小行为修复（静默降级改为显式告警、补齐装配条件、修订文档）；若修复必须落在 `leaf-base-datasource`，按宪法"稳定契约优先"给出最小影响面说明与回归证明，不另立规格，但需保持既有成功路径不变。
- 不改变 `seata.yml` 已有配置键语义；新增开关遵循 `maple.framework.enable.*` 约定并补充 IDE 配置元数据。
- 审查以静态证据与隔离测试为准，不承诺生产链路压测与故障注入结论。
- 兼容类 `io.seata.rm.datasource.DataSourceProxy` 在当前版本仍可用，因此本次审查将其定性为"升级即断的契约风险"而非"当前功能失效"；但交付目标状态是**只依赖新命名空间类型**，兼容类只作为缺陷成因被记录，不再作为可接受的技术依赖。
