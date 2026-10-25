<!--
SYNC IMPACT REPORT（临时评审草稿，提交前删除）
- Version change: 1.2.0 → 1.3.0
- Modified principles: 无（未重命名任何既有原则）
- Added sections:
  - 附加约束 §9「能力完备性对齐官方特性（不欠实现）」
  - 开发流程与质量门禁 §4 提交前检查清单：新增「未缺失官方关键特性」复核项
- Removed sections: 无
- Follow-up TODOs: 无
-->

# Maple Leaf Framework 项目宪法

<!--
沉淀依据：本文件由对现有代码库（根 POM + 25 个 leaf-base-* 模块 + docs/ + 各模块 AGENTS.md）
的静态分析归纳而成，描述的是"项目已经遵循的约定"，而非新制定的规则。
凡与代码现状不一致之处，以"原则 + 已知偏差"的方式显式记录，不美化、不臆测。
-->

## Core Principles

### I. 模块自治与按需装配（Library-First）

框架的每个能力都必须是一个可独立引入的 Maven 模块，而不是散落在超级工程里的包。

- 一个中间件/能力对应一个 `leaf-base-<能力>` 模块目录，并在根 POM 的 `<modules>` 中注册；目录名与 artifactId 一致。
- 模块依赖必须单向：业务无关的公共契约集中在 `leaf-base-framework`，其余模块只能依赖框架模块或明确的上游模块，**禁止循环依赖与反向依赖**。
- 模块之间互相引用的版本统一写 `${project.parent.version}`；第三方版本只能提升到根 POM 的 `<properties>` 声明，模块内不得出现重复的版本号（模块自有的第三方依赖除外，如 `leaf-base-datasource` 的 `p6spy`/`mysql` 版本）。
- 模块必须能在缺少其它基础设施时可用：对可选能力使用 `ObjectProvider`、`@ConditionalOnClass`、`@ConditionalOnMissingClass`、`@ConditionalOnMissingBean` 做降级或跳过，禁止在核心模块里硬编码对某个下游模块的强依赖。
- `leaf-framework` 只做聚合门面，**不承载任何源码**；互斥能力（如 `leaf-base-dubbo-nacos` 与 `leaf-base-dubbo-zk` 两套注册中心）不得同时进入同一应用类路径。
- 新增模块的交付集参照现有模块：`pom.xml`（parent 指向根 POM）+ 按包组织的源码 + 每个包的 `package-info.java` + `AGENTS.md` + `docs/<模块名>开发手册.md`。

**理由**：下游项目只引入真正用到的能力，避免中间件依赖倒灌；模块边界即依赖边界，也是故障隔离边界。

### II. 稳定契约优先（NON-NEGOTIABLE）

框架被下游业务工程以依赖形式引用，任何"看起来无害"的改动都可能是跨项目破坏性变更。

- 以下内容默认视为公共契约：公开注解及其属性、DTO/协议字段与泛型约束、公共方法签名、配置键、异常语义与错误码、序列化/反序列化行为、自动装配顺序。
- 修改既有逻辑前，必须先确认旧行为的成功路径、失败路径、空值、边界输入与兼容语义；不得以"顺手重构"的方式改变默认行为。
- 配置键一经发布不得重命名或改变语义；确需调整时保留旧键并给出迁移路径。
- 历史命名例外不得"顺手纠正"：包名根为 `cn.maple`（与 Maven 坐标 `cn.maple.framework` 不一致）、类名 `GX` 前缀、个别包名/后缀不统一，均为既定约定。
- 破坏性变更必须显式声明（版本升级 + 迁移说明 + 受影响模块清单），并在 PR 描述中给出向前/向后兼容策略。

### III. 统一响应与统一异常

对外输出只有一个形状，业务失败只有一种表达方式。

- HTTP 接口统一返回 `GXResultUtils<T>`，结构固定为 `code` / `msg` / `data`；分页统一使用 `GXPaginationResDto<T>` 或 `GXPaginationResProtocol<T>`（`total` / `pages` / `pageSize` / `currentPage` / `records`）。禁止裸返回 `Map`、`String`、`Boolean` 或自定义包装。
- 只允许通过静态工厂构造响应：`GXResultUtils.ok(...)` / `GXResultUtils.error(...)`，禁止 `new GXResultUtils<>()` 后手工 set。
- 业务失败必须抛异常，不得用返回 `null`、`false` 或空集合表达失败；Controller 中禁止 `try-catch` 业务异常后自行拼装响应。
- 业务异常使用 `GXBusinessException`（携带 `code` 与 `Dict data` 扩展信息）；对不需要完整堆栈的高频校验失败使用 `GXConciseBusinessException`；参数绑定/校验失败使用 `GXBeanValidateException`。
- 业务错误码必须来自实现 `GXResultStatusCode` 的枚举（如 `GXDefaultResultStatusCode`、`GXBusinessResultStatusCode`），禁止在业务代码里散落 magic number；`code` 默认复用标准 HTTP 状态码（框架内部可直接使用 `HttpStatus` 常量），业务语义码使用 `0x5xx` / `0x6xx` / `0x1000` 段位。
- 全局异常处理集中在 `GXExceptionHandler`（`@RestControllerAdvice`），每个处理器统一执行"记录日志 → 触发异常通知 → 返回标准响应"；兜底 `Exception` 只对外暴露"系统内部错误"，**不得回显堆栈、SQL、内部路径或密钥**。
- 模块自定义异常放在模块的 `exception` 包中，命名 `GX<领域>Exception`，并继承 `GXBusinessException` 以复用统一处理链路。

### IV. 显式配置与双配置源

配置必须类型安全、可默认、可观测、可开关，并且同时支持本地与 Nacos。

- 新增配置项必须使用 `@ConfigurationProperties(prefix = "...")` 绑定到 `GX<能力>Properties` 类，字段给出合理默认值；裸 `@Value` 仅用于单点开关，不得用于成组配置。
- 需要外部化的能力提供两套实现并共享抽象基类：
  - `properties/local/GXLocal<能力>Properties`：`@PropertySource("classpath:/${spring.profiles.active}/<能力>.yml")`；
  - `properties/nacos/GXNacos<能力>Properties`：`@NacosConfigurationProperties(dataId = "<能力>.yml", ...)`。
  业务代码只面向基类编程。
- 两套实现的互斥方式二选一，但**不允许出现"文档说支持两套、实际只有一套"**：
  - **类路径条件（默认）**：local 用 `@ConditionalOnMissingClass("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties")`，nacos 用 `@ConditionalOnClass(...)`；
  - **显式开关（仅当类路径条件不可达时）**：例如模块以 compile 依赖引入 `leaf-base-nacos`，导致 `@ConditionalOnMissingClass` 恒为假、local 变体永不生效。此时用 `@ConditionalOnProperty(name = "<模块>.config.source", havingValue = "local" | "nacos", matchIfMissing = true)` 互斥装配，并同时满足四条护栏：① 默认值（`matchIfMissing`）保持已发布的既有行为；② 启动时以 INFO 打印实际生效源与关键默认值（不打印密钥）；③ 必须有互斥装配与默认值的测试；④ 新增键登记 `additional-spring-configuration-metadata.json` 并在模块手册的配置键表中标注。
- 跨模块的框架能力开关统一置于 `maple.framework.enable.<feature>`，默认关闭，通过 `@ConditionalOnProperty` / `@ConditionalOnExpression` 显式开启；**单模块内部开关沿用该模块既有配置前缀**（如 `sso.config.*`、`redisson.*`），不强制迁移到 `maple.framework.enable.*`。新增开关同步写入 `META-INF/additional-spring-configuration-metadata.json`，保证 IDE 提示。
- 敏感值只允许 `${ENV_VAR:default}` 形式注入，禁止把真实密码、令牌、生产地址写入源码、测试或 `config/` 模板。
- 配置类必须保持可覆盖、可观测：装配过程输出明确日志（如"Nacos 配置 / 本地配置"），禁止静默降级。

### V. 空值安全与上下文纪律

框架大量使用 `ThreadLocal`、MDC、异步线程池与反射，上下文正确性优先于代码简洁。

- 每个包应提供 `package-info.java`，用于声明包级空值策略（现有 `cn.maple` 各子包普遍标注 `@org.jspecify.annotations.NullUnmarked`，缺失的包在触碰时补齐）；公共 API 中可能为空的返回值/参数使用 `org.jspecify.annotations.Nullable` 显式标注。
- `ThreadLocal` 上下文（动态数据源 `GXDynamicContextHolder`、数据权限、动态服务绑定等）必须成对使用 `push`/`poll`，切面在 `finally` 中恢复，避免上下文泄漏；嵌套调用必须支持栈式恢复。
- 异步执行、线程池、事件分发必须使用既有的 MDC/TraceId 包装能力（`GXMdcWrapper*`），禁止直接 `new Thread` 绕过上下文传播。
- 事件载荷在发布时快照化（`GXBaseEvent` 仅支持 Map/Collection/Array/Date/Calendar 的防御性拷贝），不得依赖 Java 序列化或对任意对象做反射克隆；自定义可变对象须先转为不可变值。
- 任何持有资源的组件（Engine、监听器、连接、锁、线程池）必须有明确的释放路径（`DisposableBean`/关闭钩子/`finally`），并在失败路径上同样释放。

### VI. 测试与静态检查门槛

提交前必须能通过编译期静态检查，并有与风险匹配的测试。

- 统一使用 **JUnit 5（Jupiter）**；新增测试禁止使用 JUnit 4。类名 `GX<被测类型>Test`，需要 Spring 容器的集成测试命名为 `GX<能力>SpringBootTest`；测试方法使用描述性驼峰命名表达"行为 + 条件"（历史代码中 `testXxx` 与 `should<行为>When<条件>` 并存，新增用例优先使用后者）。
- Bug 修复必须附带**能在旧代码上失败、在新代码上通过**的回归测试。
- 涉及并发、`ThreadLocal`/MDC/TraceId、缓存、事件、AOP、反射、SpEL、Web advice/filter/interceptor、配置装配、SQL 拦截的改动，必须补充接近真实运行形态的 Spring Boot 测试或可执行冒烟验证。
- 依赖外部中间件（MySQL/Redis/RabbitMQ/Elasticsearch 等）的测试必须在缺少环境时可优雅跳过，不得让整条流水线因环境缺失而失败。
- 编译期已启用 ErrorProne + NullAway（`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`），**构建失败即视为不合格**；不得为绕过检查而删除 `maven-compiler-plugin` 中的编译参数或注解处理器。
- 涉及公共 DTO/协议对象变更时，至少验证序列化、反序列化与核心字段兼容性。

### VII. 命名与包结构一致性

命名是检索与识别的基础，必须保持可预测。

- 包根为 `cn.maple`。模块目录与包前缀的映射：`leaf-base-framework → cn.maple.core.framework`、`leaf-base-datasource → cn.maple.core.datasource`、`leaf-base-datasource-mongodb → cn.maple.mongodb.datasource`、`leaf-base-data-sync → cn.maple.canal`（历史例外）、`leaf-base-dubbo-nacos-sentinel → cn.maple.sentinel`（历史例外），其余 `leaf-base-X` 通常对应 `cn.maple.X`。
- 类型统一 `GX` 前缀（当前约七成类遵循）；新增类型必须沿用，历史无前缀类型不强制重命名。
- 后缀表（新增代码必须遵守）：

| 角色 | 命名规则 | 示例 |
| --- | --- | --- |
| 抽象基类 / 公共契约 | `GXBase<概念>` | `GXBaseController`、`GXBaseDao`、`GXBaseRepository` |
| 接口实现 | `GX<概念>ServiceImpl` / `GX<概念>Impl` | `GXFileUploadServiceImpl` |
| 异常 | `GX<领域>Exception` | `GXBusinessException`、`GXSqlInjectionException` |
| 状态码 | `GX<范围>StatusCode`（枚举，实现 `GXResultStatusCode`） | `GXDefaultResultStatusCode` |
| 枚举 | `GX<概念>Enums` / `GX<概念>Enum` | `GXUnionTypeEnums` |
| 常量 | `GX<范围>Constant`（私有构造、全大写字段）；顺序、上限、标记类常量并入模块既有的 `GX<范围>Constant`，不为个别常量新建类 | `GXCommonConstant`、`GXSSOConstant.AUTHORIZATION_ORDER` |
| 配置属性 | `GX<能力>Properties`（外层 + `local`/`nacos` 子包） | `GXRedissonProperties` |
| 配置类 | `GX<能力>Config` | `GXFrameworkConfig`、`GXRetryConfig` |
| 注解与切面 | 注解 `GX<动作>`，切面同名 + `Aspect` | `GXStopWatch` / `GXStopWatchAspect` |
| 过滤器 / 拦截器 | `GX<用途>Filter` / `GX<用途>Interceptor` | `GXXssFilter`、`GXTraceIdInterceptor` |
| 工具类 | `GX<用途>Utils`（历史 `*Util` 不再新增） | `GXCommonUtils`、`GXResultUtils` |
| 校验器 | `GX<概念>Validator`（无状态、可静态调用、不持有可变字段） | `GXRocketMQConfigSourceValidator`、`GXValidateDBExistsValidator` |
| 请求 DTO / 响应 DTO | `GX<业务>ReqDto` / `GX<业务>ResDto` | `GXUserReqDto`、`GXPaginationResDto` |
| 对外协议对象 | `GX<业务>ReqProtocol` / `GX<业务>ResProtocol` | `GXQueryParamReqProtocol` |
| 内部传输对象 | `GX<业务>InnerDto` | `GXBaseQueryParamInnerDto` |
| 数据库条件对象 | `GXCondition<操作符>` | `GXConditionEQ`、`GXConditionIsNULL` |

- 分层职责固定：`controller`（协议适配、参数校验、DTO 转换）→ `service`（业务编排，接口与 `impl` 分包）→ `dao`/`repository`（数据访问）；`api` 层面向 RPC/远程调用，独立使用 `api/dto/req|res`，不得反向依赖 Web 层。
- 数据访问方法命名保持动词一致：`findByCondition`、`findOneByCondition`、`findById`、`updateOrCreate`、`updateFieldByCondition`、`deleteCondition`、`deleteSoftCondition`、`paginate`、`checkRecordIsExists`、`count`。
- 日志统一走 SLF4J 门面（`@Slf4j`，历史文件中的 `@Log4j2` 允许保留但不再新增）；禁止 `System.out`/`printStackTrace`。日志与断言消息优先使用 ASCII 文本，面向用户的异常消息使用中文。

## 附加约束（技术栈与依赖治理）

1. 基线版本：JDK 21、Spring Boot 4.1.x、Spring Framework 7.x、Spring Cloud 2025.1.x、Spring Cloud Alibaba 2025.1.x；不得自行降级主版本。
2. 依赖版本只允许在根 POM `<properties>` 中声明；冲突覆盖声明必须位于 `spring-boot-dependencies` 之前，并附中文注释说明"解决哪两个组件的冲突"。**不得删除已有的冲突覆盖**，它们通常是踩坑后的沉淀。
3. 新增第三方依赖必须说明：用途、是否与现有依赖功能重叠、体积/传递依赖影响、是否可用现有封装替代。能用 JDK/Spring Boot/Spring Framework/本模块既有工具完成的，不引入新库。
4. 禁止重复造轮子：不要为已有能力再包一层抽象；只有在逻辑被两处以上复用或能显著降低复杂度时才抽取新类型。
5. 单一职责：一个类型不得同时承担 Web、缓存、并发、反射、转换、事件、上下文传播等多类职责。
6. Lombok 使用保持克制且一致：DTO/Properties 用 `@Data`，继承类必须 `@EqualsAndHashCode(callSuper = true)`；构造器注入优先于字段注入。
7. 安全底线：SQL 拼接必须走既有 `GXCondition`/参数化能力；批量更新/删除必须保留条件非空保护；不得关闭 Druid `wall` 过滤器与防全表更新拦截器等既有防护。
8. 反对过度设计与过度实现（YAGNI）：实现代码只覆盖当前明确需求，不得为"未来可能"预留投机性抽象、扩展点、配置开关或未被任何调用方使用的公共 API。评审必须重点复核实现是否超出需求范围——如仅一处使用的抽象基类/接口、可被 JDK/Spring Boot/Spring Framework/本模块既有工具替代的自研封装、重复的参数化与分支、冗余的中间层；一经发现必须删除，或由作者在 PR 中给出必要性证明（必要性、替代方案、可回滚方式），否则不得合入。
9. 能力完备性对齐官方特性（不欠实现）：对中间件/组件的封装必须覆盖其官方核心能力，不得因遗漏而缺失官方提供的关键特性；确不支持的必须显式声明并说明原因。评审必须重点复核各 `leaf-base-<组件>` 模块是否缺失官方特性——例如 `leaf-base-nacos` 是否缺失 Nacos 官方特性、`leaf-base-debezium` 是否缺失 Debezium 官方特性——一经发现缺失，必须补齐，或在模块 `AGENTS.md`/手册中登记"不支持范围 + 原因 + 替代方案"。

## 开发流程与质量门禁

1. **分支**：按版本开发，分支名 `dev<版本号>`（当前 `dev4.3.0`）；PR 目标分支指向当前版本分支，而非 `master`。
2. **提交信息**：`类型(范围): 简述`，类型取 `feat` / `fix` / `docs` / `refactor` / `test` / `chore`，例如 `feat(fix): 完善数据源模块的数据过滤`。
3. **PR 必须说明**：改动动机、影响范围（哪些模块/公共契约受影响）、是否涉及接口或配置键变更、本地执行的验证命令与结果、兼容性策略。
4. **提交前检查清单**：
   - `mvn clean install -DskipTests` 通过（ErrorProne + NullAway 无错误）；
   - 受影响模块的测试通过（`mvn test -pl <module> -am`）；
   - 仅修改目标模块；确需跨模块时说明原因与最小影响面；
   - 公共 API/配置键/异常语义未被无意改变；
   - 新增配置项已补 `additional-spring-configuration-metadata.json`；
   - 无敏感信息、无调试残留、无 `System.out`；
   - 已复核无过度设计/过度实现（无投机性抽象、无未被使用的公共 API/扩展点、无冗余中间层）；
   - 已复核所封装组件未缺失官方关键特性（不支持的范围已在 `AGENTS.md`/手册显式登记）；
   - 测试覆盖与风险等级匹配（单元 / Spring Boot 集成 / 冒烟）。
5. **新增模块检查清单**：目录与 artifactId 一致、已注册到根 `<modules>`、包前缀符合映射表、每包 `package-info.java`、`AGENTS.md` 与 `docs/` 手册齐备、聚合门面是否纳入明确说明、依赖方向正确。
6. **已知偏差登记**：仓库中现存的历史偏差（部分模块残留 JUnit 4 导致用例被静默跳过、`leaf-base-manticore` 仅有包声明、聚合门面未覆盖全部模块、包级 `@NullUnmarked` 与构建期 NullAway 强制检查的表述尚未完全统一等）**必须在触碰相关代码时一并收敛，不得作为新代码的模仿对象**。

## Governance

- 本宪法优先于其它开发约定；`docs/` 手册与各模块 `AGENTS.md` 是宪法在具体模块上的细化，冲突时以宪法为准。
- 所有评审必须校验合规性：不满足"稳定契约优先""统一响应与统一异常""测试与静态检查门槛"的变更不得合入。
- 修订流程：提出变更动机 → 给出影响面分析（受影响模块、公共契约、下游兼容性）→ 更新本文件与相关 `AGENTS.md`/手册 → 在 PR 中记录。
- 版本语义：**MAJOR** 用于移除或重定义原则、引入不兼容约束；**MINOR** 用于新增原则/章节或实质性扩展；**PATCH** 用于措辞澄清与非语义修正。
- 复杂度必须被证明：新增抽象层、缓存、反射链路、生命周期钩子、并发模型时，需在 PR 中说明必要性、替代方案与回滚方式；评审重点复核实现是否过度设计/过度实现（见"附加约束"§8）。
- 运行时开发指导以根 `README.md`、`docs/` 各模块手册与模块级 `AGENTS.md` 为准；宪法只约束"什么不可妥协"。

## 修订记录

| 版本 | 日期 | 类型 | 变更 | 依据 |
| --- | --- | --- | --- | --- |
| 1.3.0 | 2026-10-25 | MINOR | 附加约束新增 §9「能力完备性对齐官方特性（不欠实现）」：封装组件必须覆盖官方核心能力，评审重点复核各 `leaf-base-<组件>` 模块是否缺失官方特性（如 `leaf-base-nacos`、`leaf-base-debezium`），缺失须补齐或显式登记不支持范围；开发流程与质量门禁 §4 提交前检查清单新增对应复核项 | 用户指示：审查、复核实现代码是否缺失官方提供的功能特性（如 nacos、debezium 模块） |
| 1.2.0 | 2026-10-25 | MINOR | 附加约束新增 §8「反对过度设计与过度实现（YAGNI）」：实现只覆盖当前明确需求，评审重点复核是否引入投机性抽象、未被使用的公共 API/扩展点/配置开关、可被既有工具替代的自研封装等过度设计与过度实现；开发流程与质量门禁 §4 提交前检查清单新增对应复核项；Governance「复杂度必须被证明」补充评审复核要点 | 用户指示：重点审查、复核实现代码是否存在过度设计/过度实现 |
| 1.1.0 | 2026-10-23 | MINOR | §IV：为双配置源新增"显式开关"等价互斥方式（附四条护栏），并澄清单模块内部开关沿用模块既有前缀、不强制迁移到 `maple.framework.enable.*`；§VII：后缀表新增"校验器 `GX<概念>Validator`"角色，并注明顺序/上限/标记类常量并入既有 `GX<范围>Constant` | `specs/013-sso-module-audit` T102：`leaf-base-nacos` 为 compile 依赖使 `@ConditionalOnMissingClass` 恒为假（F-06），模块改用 `sso.config.source` 显式开关并经 110 条用例验证；仓库已有 `GXRocketMQConfigSourceValidator`、`GXValidateDBExistsValidator`、`GXValidateCallRemoteServiceValidator` 三个类型在实践上使用 `Validator` 后缀但后缀表无对应角色 |
| 1.0.0 | 2026-10-21 | MAJOR | 初始版本（由现有代码库静态分析归纳） | 根 POM + 25 个 `leaf-base-*` 模块 + `docs/` + 各模块 `AGENTS.md` |

**Version**: 1.3.0 | **Ratified**: 2026-10-21 | **Last Amended**: 2026-10-25
