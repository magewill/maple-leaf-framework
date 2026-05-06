# AGENTS.md - leaf-base-dubbo-nacos-sentinel

本文件用于指导 `leaf-base-dubbo-nacos-sentinel` 模块的后续迭代开发与代码审查。修改本模块时，默认优先保持现有 Sentinel + Dubbo + Nacos 集成方式稳定，避免不必要的重构和行为漂移。

## 1. 模块定位

`leaf-base-dubbo-nacos-sentinel` 是框架中 Dubbo/Nacos/Sentinel 的适配模块，当前职责集中在以下三类能力：

1. 为 `@SentinelResource` 提供 Spring Bean 装配能力。
2. 通过 `GXNacosDataSourceInitFunc` 从 Nacos 读取并注册 Sentinel 动态规则。
3. 解析集群分组配置，完成 Sentinel 集群客户端、服务端与模式状态的初始化。

本模块不负责业务接口实现，不负责具体限流规则内容的编排，也不负责业务侧的配置生成逻辑。

## 2. 现有代码结构

1. `src/main/java/cn/maple/sentinel/config`
   - Sentinel 注解切面配置。
2. `src/main/java/cn/maple/sentinel/init`
   - Sentinel Nacos 数据源初始化、集群模式识别、规则注册。
3. `src/main/java/cn/maple/sentinel/vo`
   - Sentinel 集群分组配置对象。
4. `src/main/resources/META-INF/services`
   - Sentinel `InitFunc` SPI 注册文件。

## 3. 当前核心行为

1. `GXSentinelResourceAspectConfig` 负责注册 `SentinelResourceAspect`，使 `@SentinelResource` 可用。
2. `GXNacosDataSourceInitFunc` 在 Sentinel 启动时初始化：
   - 流控规则
   - 参数流控规则
   - 集群客户端分配配置
   - 集群服务端传输配置
   - 集群状态
   - 集群规则提供者
3. `ClusterGroupVO` 作为 Nacos 集群映射配置的载体，描述 `machineId`、`ip`、`port`、`clientSet`。

## 4. 开发规则

1. 所有 `log` 日志内容必须使用 ASCII 字符。
2. 涉及任何改动点时，必须同步检查测试用例是否完整，不能只改实现不补测试。
3. 不要随便把已有代码抽成单独方法或类，除非抽取后的代码能被两个以上位置复用，且确实降低维护成本。
4. 删除方法、类、变量上的冗余注释，保留真正有信息量的说明。
5. 对复杂的方法、类、扩展点或关键流程，适当补充 Javadoc，并提供最小可读的使用示例。
6. 涉及 `Filter`、`Aspect`、`InitFunc`、`Bean` 注册、SPI、Nacos 数据源、Sentinel 集群模式等代码时，优先保持现有扩展点名称、注册方式和运行时语义不变。
7. 修改异常转换、状态识别、规则加载顺序、集群映射解析时，必须明确兼容旧配置和旧行为。

## 5. 测试要求

1. 任何功能修改都要补齐或更新测试，不允许留下明显空洞。
2. 测试用例要覆盖成功、失败、边界、空值、异常路径。
3. 如果功能只能在 Spring Boot 应用运行后验证，测试就要模拟真实 Spring Boot 启动后的场景，优先使用 `@SpringBootTest`、真实 Bean 装配、AOP 生效后的上下文和 `BeanPostProcessor`/SPI 生效后的结果。
4. 涉及 Sentinel 初始化、Nacos 规则加载、集群状态识别的改动，要验证规则注册结果、状态切换结果和配置解析结果。
5. 当前模块如果还没有对应测试目录，新增功能时应先补测试骨架，再做实现调整。

## 6. 自动更新 AGENTS.md 的时机

出现以下任一情况时，必须同步更新本文件：

1. 模块职责边界变化，例如新增或移除 Sentinel、Dubbo、Nacos 相关能力。
2. 新增目录、包结构或核心扩展点，例如新增 `config`、`init`、`vo` 之外的重要职责区。
3. Sentinel 规则加载、集群模式识别、SPI 注册方式、Nacos DataId 约定发生变化。
4. 测试策略变化，例如新增必须依赖 Spring Boot 启动的测试场景，或新增集成测试基线。
5. 团队新增需要长期遵守的开发规范，且会影响后续迭代。

## 7. 提交前检查

1. 新增或修改日志时，确认没有非 ASCII 内容。
2. 检查这次变更是否影响测试覆盖面。
3. 确认没有为了“更整洁”而做无必要抽取、重构或拆分。
4. 检查是否需要补充 Javadoc 或示例。
5. 确认 SPI、Nacos 配置键、集群配置格式没有被破坏。

