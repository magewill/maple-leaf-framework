# Phase 0 Research: Seata 与动态数据源协作正确性审查加固

**Feature**: `003-seata-datasource-audit` | **Date**: 2026-10-21 | **Plan**: [plan.md](./plan.md)

技术上下文中的每一项未知均已在本阶段闭环，无遗留 `NEEDS CLARIFICATION`。

---

## R-01 数据源侧代理实例化方式

**Decision**: 保留反射实例化，但把类名常量统一为新命名空间 `org.apache.seata.rm.datasource.DataSourceProxy`；"是否具备代理能力"的判定标识统一为 `org.apache.seata.rm.datasource.SeataDataSourceProxy`。

**Rationale**:
- `leaf-base-datasource` 的 `pom.xml` 中 Seata 只有 `test` scope，运行期不传递 Seata 依赖。改为编译期引用会让所有使用方被动获得 Seata（及其 antlr/netty 冲突面），违反宪法 I"模块必须能在缺少其它基础设施时可用"。
- 反射已存在且经过生产验证，本次只需把字符串常量换到基线命名空间，改动面最小（FR-013/FR-015）。
- `seata-all:2.6.0` 中 `io.seata.rm.datasource.DataSourceProxy` 仅为 `@Deprecated` 委托壳；直接使用新类是消除"升级即断"的唯一方式（澄清 2）。

**Alternatives considered**:
| 方案 | 否决原因 |
| --- | --- |
| 新增 `optional` 依赖并直接 `new DataSourceProxy(...)` | 仍需 `@ConditionalOnClass` 兜底；把 Seata 拉进编译类路径，增加下游版本冲突与 NullAway 分析面 |
| 把代理创建下沉到 `leaf-base-seata`，datasource 暴露扩展点 | 新增跨模块抽象与生命周期契约，属"代理体系重构"，超出 FR-015 范围 |
| 保留旧命名空间 + 双版本回退 | 被澄清 2 明确否决；双路径的告警口径与测试成本翻倍 |

---

## R-02 代理是否真正生效的权威校验位置

**Decision**: 在 `leaf-base-seata` 的 `GXSeataDynamicDataSourceConfig` 中新增一个 `SmartInitializingSingleton` bean，遍历 `GXDynamicDataSource#getResolvedDataSources()`，对每个目标数据源做 `SeataDataSourceProxy` 实例判定。校验范围仅限两条可断言事实：目标源是否被 Seata 代理、路由源是否被二次代理；不检测应用中其它数据源 bean 的自动代理（FR-007 澄清结论）。

**Rationale**:
- `AbstractRoutingDataSource#getResolvedDataSources()` 在 Spring Framework 7.0.9 中为 `public`（已用 `javap` 核对），无需反射、无需新增访问器。
- 校验需要读取"严格模式"开关与"配置来源"，这两件事只属于 seata 模块；放在 datasource 会把 Seata 语义倒灌进通用数据源模块。
- `SmartInitializingSingleton#afterSingletonsInstantiated` 在所有单例创建完成后执行，能覆盖"使用方手工构造 `GXDynamicDataSource` 或覆盖了默认 bean"的情形，且异常发生在 context refresh 内，严格模式可直接中断启动。

**Alternatives considered**:
| 方案 | 否决原因 |
| --- | --- |
| 在 `GXDynamicDataSourceConfig.wrapSeataDataSource` 内直接抛错 | datasource 无法读取 seata 开关；且"是否要求严格"属于 Seata 集成语义 |
| `ApplicationRunner` / `CommandLineRunner` | 时机更晚、异常栈更远离装配点；Web 应用之外形态（非 Web 的批处理）执行顺序更难预期 |
| `@PostConstruct` 于配置类 | 配置类可能在 `GXDynamicDataSource` 之前初始化，读不到已解析集合 |

---

## R-03 装配条件如何避免"缺 bean 即启动失败"

**Decision**: `seataDynamicDataSourceCustomizer` 的方法参数改为 `ObjectProvider<GXDynamicDataSource>`，在 `SqlSessionFactoryBeanCustomizer#customize` 回调里解析；取不到时输出 warn 并跳过注入。不使用 `@ConditionalOnBean`。

**Rationale**:
- 两个配置类依靠包扫描装配（无 `AutoConfiguration.imports`），Spring 官方明确 `@ConditionalOnBean` 只在自动配置类中顺序可靠；在扫描配置上使用会出现"取决于解析顺序"的隐性缺陷。
- `ObjectProvider` 惰性解析天然容忍缺失，且把失败从"启动崩溃"降级为"可观测告警"，符合 FR-006 与用户故事 3。
- 若使用方自定义了 `SqlSessionFactory`，MyBatis-Plus 的定制器本就不会被调用，日志可说明未接管。

**Alternatives considered**:
| 方案 | 否决原因 |
| --- | --- |
| `@ConditionalOnBean(GXDynamicDataSource.class)` | 扫描装配下顺序不可靠，行为随配置类声明顺序漂移 |
| 方法参数直接注入（现状） | 无 bean 时 `NoSuchBeanDefinitionException`，即 F-03 |
| 返回 `null` 的 `@Bean` 条件工厂 | Spring 会注册 NullBean，语义含糊，后续注入易踩坑 |

---

## R-04 严格开关的键名、默认值与读取点

**Decision**: `maple.framework.enable.seata-strict`，`Boolean`，默认 `false`；只在 `leaf-base-seata` 内以 `@Value` 读取，并写入该模块的 `META-INF/additional-spring-configuration-metadata.json`。

**Rationale**:
- 宪法 IV 规定单点开关可用裸 `@Value`，成组配置才要求 `@ConfigurationProperties`；这里只有一个布尔值，新建 Properties 类属过度设计。
- 前缀遵循 `maple.framework.enable.*` 且默认关闭，语义是"额外收紧"，与既有开关习惯一致。
- 默认 `false` 保证下游升级后启动表现不变（澄清 3）。

**Alternatives considered**: 复用 `seata.yml` 内的自定义键（会与 Seata 官方命名空间混淆）；默认 `true`（等于改变下游启动结果，违反宪法 II）。

---

## R-05 配置来源（本地 / Nacos）的可观测方式

**Decision**: 在 `GXSeataDynamicDataSourceConfig` 注入 `ObjectProvider<GXLocalSeataProperties>` 与 `ObjectProvider<GXNacosSeataProperties>`，校验器执行时输出 `config-source=local|nacos|none`。

**Rationale**: 这两个标记类本身就是"二选一"装配的载体，其存在性即事实来源；不需要新增配置键，也不需要解析注解元数据。两者同时存在属于装配异常，应输出 warn 并按 `nacos` 优先记录（与 `@ConditionalOnMissingClass` 的互斥意图一致）。输出点唯一：由装配校验 bean 在同一条 INFO 汇总行中输出，不另建第二个日志点，避免出现两处各自打印 `config-source` 的局面。

**Alternatives considered**: 反射读取 `@PropertySource`/`@NacosConfigurationProperties`（脆弱、对代理对象不成立）；推断 `spring.cloud.nacos.config.*` 是否存在（推断而非事实）。

---

## R-06 启动期日志契约（ASCII）

**Decision**: 日志按"是否启用 Seata"分级，统一前缀 `[Seata]`：

| 场景 | 级别 | 位置 | 内容 |
| --- | --- | --- | --- |
| 类路径无 Seata 代理类型（未启用 Seata） | DEBUG | datasource 侧 | `Seata DataSourceProxy not found, using original DataSource`（保持现状，不产生告警） |
| Seata 已装配、目标源全部被代理 | INFO | seata 侧 | `[Seata] enabled=true, config-source=local, datasources=[ds1,ds2], proxied=[ds1,ds2]` |
| Seata 已装配、存在未代理目标源 | WARN | seata 侧 | `[Seata] datasource 'ds2' is NOT wrapped by Seata; branches on it will not join global transactions` |
| 严格模式命中 | ERROR（异常链路） | seata 侧 | 抛 `GXBusinessException`，消息含数据源名称与缺失契约 `org.apache.seata.rm.datasource.SeataDataSourceProxy` |
| 无 `GXDynamicDataSource` bean | WARN | seata 侧 | `[Seata] GXDynamicDataSource is absent, skip MyBatis-Plus datasource customization` |

**Rationale**: 满足 FR-004/FR-011 与 `AGENTS.md` 的 ASCII 约束；分级避免让"只用数据源模块"的用户在每个数据源上收到无意义告警（澄清 7）；固定键值对便于检索与断言；不引入新日志框架或 MDC 字段。

**Alternatives considered**: datasource 侧直接 warn（纯数据源用户产生 N 条噪声，被澄清 7 否决）；依赖 Seata 自身日志（不覆盖"框架内装配是否成功"）；仅打印总数（无法定位具体数据源，违反 SC-003）。

---

## R-07 测试策略（零外部中间件）

**Decision**:

| 测试目标 | 手段 | 旧代码上必须失败的点 |
| --- | --- | --- |
| 代理类契约（新命名空间、实现 `SeataDataSourceProxy`、存在 `(DataSource)` 构造器） | 反射断言 + `Class.forName`，不实例化真实代理（避免构造期连库） | 断言 `GXDynamicDataSourceConfig` **实际使用的类名常量**等于新命名空间；旧实现为 `io.seata...`，断言失败 |
| 代理类缺失时的降级 | `FilteredClassLoader` 隐藏新命名空间类，断言返回同一实例且**日志级别为 debug** | 日志级别断言（改为 warn 的实现会失败） |
| 装配缺失（无 `GXDynamicDataSource` bean） | `ApplicationContextRunner` + 定制器调用路径断言不抛错且输出跳过告警 | 旧实现直接抛 `NoSuchBeanDefinitionException` |
| 代理校验三态 | 实现 `SeataDataSourceProxy` 的桩数据源（不做 SQL）与普通桩数据源对照，覆盖 VERIFIED / DEGRADED / FAILED | 旧实现无该校验 bean，`DEGRADED`/`FAILED` 用例失败 |
| 包装后的行为（接口实现、`getTargetDataSource()` 返回原实例、`destroy` 关闭） | 用实现 `SeataDataSourceProxy` 的桩对象作为"已包装目标源"，不构造真实代理 | 旧实现不产生包装，桩不会被使用 |
| 严格模式 | `withPropertyValues("maple.framework.enable.seata-strict=true")` 断言启动失败与消息内容 | 同上 |
| 配置组合（4 种） | 各 1 个 `ApplicationContextRunner` 用例：本地生效 / Nacos 生效（`FilteredClassLoader`）/ profile 缺失失败 / 资源缺失失败，断言失败消息含 profile 与路径 | profile/资源缺失的消息断言在旧实现上失败（消息不含 profile 与期望路径） |

**Rationale**: 宪法 VI 要求"依赖外部中间件的测试可优雅跳过"，且 Bug 修复的回归测试必须"旧代码失败、新代码通过"；因此每条用例都标注了旧代码上的失败点，避免写出在新旧实现上都通过的烟雾测试。

已核对 `seata-all-2.6.0-sources.jar` 中 `org/apache/seata/rm/datasource/DataSourceProxy.java`（第 93–126 行）：构造器调用 `init()`，其中 `try (Connection connection = dataSource.getConnection())` 读取元数据 URL，并执行 `checkUndoLogTableExist(connection)`，随后 `DefaultResourceManager.get().registerResource(this)`。结论：**任何测试都无法在无库环境构造真实代理**，因此代理类只做"类名/接口/构造器"反射契约测试，包装行为一律用桩对象验证；"生产环境实际类型为新命名空间代理类"作为书面契约，不在单测中断言 `getClass()`。

**Alternatives considered**: Testcontainers + 真实 MySQL（超出本次范围、增加流水线成本，且需要 `undo_log` 表）；Mockito 深度桩代理（对静态初始化与 `registerResource` 无意义）；仅断言"类存在"（在旧代码上也通过，违反宪法 VI）；断言 `getClass()==DataSourceProxy`（必须先构造真实代理，不可行）。

**Alternatives considered**: Testcontainers + 真实 MySQL（超出本次范围、增加流水线成本）；Mockito 深度桩代理（对静态初始化与 `registerResource` 无意义）。

---

## R-08 是否新增自动装配注册

**Decision**: 不新增 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，维持"包扫描或显式 `@Import`"的既有约定，并在文档中写明。

**Rationale**: 现状两个模块都靠扫描装配；新增自动装配会与使用方既有的 `cn.maple` 扫描叠加，产生重复注册与顺序不确定性，属于超范围的装配重写（FR-015）。E-07 记录的是事实，不是本次必修缺陷。

**Alternatives considered**: 全面迁移到 `@AutoConfiguration`（影响所有下游装配方式，应单独立规格）。

---

## R-09 文档与配置元数据

**Decision**: 修订 `docs/leaf-base-seata开发手册.md`（条件类名、`seata.yml` 路径约定、严格开关、失败语义、类型契约）与 `leaf-base-seata/AGENTS.md`（测试与检查项）；新增 seata 模块的 `additional-spring-configuration-metadata.json`。

**Rationale**: FR-014 要求文档与实现一致；宪法 IV 要求新增配置项补 IDE 元数据；宪法"已知偏差登记"要求触碰模块时收敛文档漂移（F-07）。

**Alternatives considered**: 只改代码（F-07 未闭环，SC-005 无法达成）。

---

## R-10 审查报告的存放与命名

**Decision**: `specs/003-seata-datasource-audit/review-report.md`，由实现阶段产出，结构与校验规则见 [contracts/review-report-schema.md](./contracts/review-report-schema.md)。

**Rationale**: 澄清 5 要求独立成文；放在特性目录可与 spec/plan 同版本演进，避免污染 `docs/`（`docs/` 只放稳定的模块手册）。

**Alternatives considered**: 并入 `docs/leaf-base-seata开发手册.md`（审查过程性证据不属于用户手册）；只在 `spec.md`（信息密度过高，SC-006 难达成）。

---

## 依赖与版本事实（供实现阶段引用）

| 事实 | 值 | 来源 |
| --- | --- | --- |
| Seata 客户端 | `org.apache.seata:seata-spring-boot-starter:2.6.0` | 根 POM `<seata.spring.boot.starter.version>` |
| 新命名空间代理类 | `org.apache.seata.rm.datasource.DataSourceProxy` | `seata-all-2.6.0.jar` + sources |
| 代理接口 | `org.apache.seata.rm.datasource.SeataDataSourceProxy` | 同上 |
| 旧命名空间兼容类 | `io.seata.rm.datasource.DataSourceProxy`（`@Deprecated`，委托新实现） | 同上 |
| 自动代理默认值 | `seata.enable-auto-data-source-proxy=true`，但跳过 `AbstractRoutingDataSource` | `SeataProperties` / `SeataAutoDataSourceProxyCreator` |
| 资源注册时机 | 代理构造链路调用 `DefaultResourceManager.get().registerResource(this)` | `DataSourceProxy` 源码 |
| Spring 路由数据源 API | `getResolvedDataSources()` / `getResolvedDefaultDataSource()` 为 public | `javap` Spring JDBC 7.0.9 |

**结论**：所有技术未知已闭环，可进入 Phase 1 设计与任务拆分。
