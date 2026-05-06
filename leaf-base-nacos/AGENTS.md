# AGENTS.md - leaf-base-nacos

本文档用于指导 `leaf-base-nacos` 模块的后续迭代开发。修改本模块时，优先保持 Nacos 配置接入行为、Spring Boot 启动期装配行为，以及配置加载进入 `Spring Environment` 的方式稳定，避免为了形式统一做无收益重构。

## 1. 模块定位

`leaf-base-nacos` 是 Maple Leaf Framework 中提供 Nacos 配置中心基础接入能力的模块，当前职责聚焦为：

1. 通过 `GXNacosAppEnvironmentProperties` 声明一个启动期装配的配置接入点。
2. 使用 `@NacosConfigurationProperties` 从 Nacos 拉取 `app-environment.yml`。
3. 将 Nacos 中的配置直接绑定到 Spring Boot 的 `Environment`，而不是在本模块内维护业务字段。
4. 兼容 `spring.cloud.nacos.*` 与 `nacos.*` 两套属性来源，用于解析 `serverAddr`、`namespace`、`group`、`username`、`password`。
5. 通过 `@ConditionalOnClass` 保证只有在 Nacos Config 注解存在时才启用该能力。

本模块当前不是业务配置定义模块，不负责：

1. 声明业务领域专属的 `@ConfigurationProperties` 字段。
2. 提供 Nacos 配置发布、灰度、监听器编排或动态变更管理平台能力。
3. 替代具体业务模块中的类型安全配置 Bean。

## 2. 目录职责

1. `src/main/java/cn/maple/nacos/properties`
负责 Nacos 配置接入相关的属性类与启动期声明。

2. `pom.xml`
负责本模块依赖边界声明，当前核心依赖包括：
`leaf-base-framework`、`spring-cloud-starter-alibaba-nacos-discovery`、`spring-cloud-starter-alibaba-nacos-config`、`nacos-client`。

当前模块下没有 `src/test` 目录。后续只要改动功能代码，就要同步建立并维护测试基线。

## 3. 功能基线

开发前必须理解并保持以下行为稳定：

1. `dataId` 固定为 `app-environment.yml`，除非需求明确要求调整配置载体。
2. `groupId` 的解析优先保持 `${spring.cloud.nacos.config.group:${nacos.config.group:DEFAULT_GROUP}}` 这条回退链路。
3. `serverAddr`、`namespace`、`username`、`password` 的属性占位符回退顺序属于兼容性约定，调整前必须评估现有接入方影响。
4. `@ConfigurationProperties(prefix = "")` 的语义是将 YAML 根路径直接接入 `Environment`，不要随意改成具名前缀。
5. `GXNacosAppEnvironmentProperties` 当前保持空类是有意设计，表示“配置发现与装配入口”而不是“业务字段容器”。
6. 如果要新增业务配置字段，优先在对应业务模块新增专属 `@ConfigurationProperties` 类型，不要先把字段堆进本模块。
7. `@ConditionalOnClass`、`@Component` 与 `@NacosConfigurationProperties` 组合后的装配方式属于启动契约，除非有明确收益与验证，否则不要替换为不同注册机制。

## 4. 强制开发规则

1. 所有 `log` 日志中的内容都必须使用 ASCII 字符。禁止新增或保留中文、全角符号或其他非 ASCII 日志文本。
2. 涉及到改动的点，都要同步查看测试用例是否完整覆盖；缺失、失效或覆盖不足时，先补测试再提交。
3. 不要随便将已存在的代码抽取成单独的方法或者类，除非抽取出来的逻辑能够被两个以上的地方复用，且明显降低维护成本。
4. 将方法、类、变量上的冗余注释去掉。能通过命名、类型和局部代码直接表达清楚的内容，不保留重复性注释。
5. 在复杂的方法、类或扩展点上适当补充 Javadoc；必要时加入最小使用示例，帮助后续使用者理解前置条件、装配方式和典型用法。
6. 不要把本模块演变成业务配置大杂烩。业务字段绑定、业务默认值和业务解释逻辑应下沉到具体业务模块。
7. 变更依赖版本、注解模型、属性回退顺序或装配条件时，必须视为兼容性变更处理，提交说明中写清旧行为、新行为和影响范围。

## 5. 测试要求

当前模块还没有现成测试用例。后续凡是改动本模块功能代码，必须补齐对应测试。

1. 测试用例要覆盖全面，至少覆盖成功路径、异常路径、空值路径、默认值路径和属性回退路径。
2. 如果功能需要在 Spring Boot 应用运行的情况下才能验证，测试必须模拟真实 Spring Boot 应用启动后的场景，优先使用 `@SpringBootTest`、真实自动装配与 `Environment` 校验。
3. 涉及 `GXNacosAppEnvironmentProperties` 的改动时，至少覆盖：
   - Bean 在满足 `ConditionalOnClass` 条件时能够被装配。
   - Nacos 注解上的 `dataId`、`groupId`、`autoRefreshed` 等关键声明未被破坏。
   - `spring.cloud.nacos.*` 与 `nacos.*` 两套属性来源的回退关系符合预期。
   - `prefix = ""` 的绑定语义没有被改坏。
4. 若本次改动影响真实配置加载行为，优先补充接近真实启动方式的集成测试，而不是只做纯反射或纯注解字符串断言。
5. 提交前至少执行与本模块相关的 Maven 测试命令；如果因环境限制未执行，必须在提交说明中明确写出原因和风险。

## 6. 变更前检查清单

1. 是否改变了 `app-environment.yml` 这一默认配置入口？
2. 是否改变了 `spring.cloud.nacos.*` 与 `nacos.*` 的属性回退顺序？
3. 是否把“配置接入入口”错误演变成了“业务字段承载类”？
4. 是否引入了非 ASCII 的日志内容？
5. 是否已经检查并补齐与改动点对应的测试？
6. 是否只是为了“代码更好看”而做了没有复用价值的方法抽取或类拆分？
7. 是否删除了冗余注释，并为真正复杂的地方补上了必要的 Javadoc 和示例？

## 7. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一个 PR 或提交中同步更新本文档：

1. 模块职责发生变化，例如从“配置接入入口”扩展为“配置监听”“动态刷新编排”或其他新能力。
2. 目录结构发生变化，例如新增新的核心 package、测试目录、自动装配入口或资源目录。
3. `GXNacosAppEnvironmentProperties` 的装配方式发生变化，例如替换注解模型、注册机制或条件装配策略。
4. `dataId`、`groupId`、`serverAddr`、`namespace`、`username`、`password` 的默认值或属性回退顺序发生变化。
5. 模块新增了稳定的开发约束、测试门槛或日志规范，且这些规则需要被后续迭代长期遵守。
6. 模块新增或删除关键依赖，导致运行时接入方式、兼容边界或测试策略发生变化。
7. 真实问题修复沉淀出了应长期遵守的新规则，例如新的测试场景、新的注释规范或新的兼容性边界。

如果本次改动没有触发以上条件，可以不更新本文档。

## 8. 注释与文档规则

1. 删除乱码注释、重复注释和仅复述代码表面的注释。
2. 对复杂装配逻辑、兼容性约定、属性回退链路和空类设计意图，优先使用高质量 Javadoc 说明。
3. Javadoc 应尽量说明“为什么这样设计”，而不只是“代码做了什么”。
4. 当使用方式不直观时，在 Javadoc 中提供最小示例，示例内容保持简洁且可直接帮助调用方理解。

## 9. 提交说明建议

1. 如果改动影响 Nacos 配置接入、属性回退、启动装配或兼容性边界，提交说明中要明确写清旧行为、新行为和影响范围。
2. 如果补充了测试，提交说明中写明新增覆盖了哪些关键路径。
3. 如果未能执行测试，提交说明中必须写明未执行原因、受影响范围和后续补验建议。
