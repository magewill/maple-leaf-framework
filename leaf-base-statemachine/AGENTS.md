# AGENTS.md - leaf-base-statemachine

本文档用于指导 `leaf-base-statemachine` 模块后续的迭代开发。修改本模块时，优先保持状态机构建 DSL、状态转换路由、条件判定、动作执行、工厂注册、并发复用能力以及 PlantUML 可视化能力的稳定，避免与当前模块职责无关的重构。

## 1. 模块定位

`leaf-base-statemachine` 是 Maple Leaf Framework 的轻量级状态机基础模块，当前代码职责主要包括：

1. 提供泛型状态机核心抽象：`GXStateMachine`、`GXState`、`GXTransition`、`GXCondition`、`GXAction`。
2. 提供流式 DSL 构建能力：支持 `externalTransition()`、`externalTransitions()`、`internalTransition()` 进行状态机定义。
3. 提供状态机运行时实现：根据 `sourceState + event + context` 路由转换，并执行条件判断与动作回调。
4. 提供状态机工厂注册能力：构建完成后自动注册到 `GXStateMachineFactory`，支持按 `machineId` 获取。
5. 提供调试与可视化能力：支持 `showStateMachine()` 输出结构，支持 `generatePlantUML()` 生成 PlantUML 文本。
6. 保持状态机实例无状态，使单个状态机定义在运行期可被多线程并发复用。

本模块当前不负责：

1. Spring Boot 自动装配。
2. 持久化状态存储。
3. 分布式状态同步。
4. 状态机实例生命周期托管。
5. 业务领域专属状态模型设计。

## 2. 目录职责

1. `src/main/java/cn/maple/statemachine`
   定义对外核心接口、工厂与访问者相关抽象。
2. `src/main/java/cn/maple/statemachine/builder`
   定义并实现状态机 DSL 构建器，包括单源外部转换、多源外部转换、内部转换三类构建路径。
3. `src/main/java/cn/maple/statemachine/impl`
   提供状态机运行时实现、状态对象、转换对象、事件到转换列表的映射、调试器、状态辅助类、访问者实现。
4. `src/test/java/cn/maple/statemachine`
   覆盖正常路径、异常路径、分支选择、PlantUML 生成等模块级测试。

## 3. 功能基线

开发前必须理解并保持以下行为基线稳定：

1. 状态机是无状态的。`GXStateMachineImpl` 不保存“当前状态”，调用方负责持有和传入当前状态。
2. 构建完成后状态机会被标记为 `ready=true`，未构建完成时调用 `fireEvent()` 必须抛出异常。
3. `build(machineId)` 会自动向 `GXStateMachineFactory` 注册状态机；相同 `machineId` 不允许重复注册。
4. `externalTransition()` 表示源状态与目标状态不同的状态迁移。
5. `internalTransition()` 表示状态不变，仅在同一状态内响应事件并执行动作。
6. `externalTransitions().fromAmong(...)` 支持多个源状态共享同一目标状态、同一事件、同一条件和动作定义。
7. 同一源状态下，同一事件可以定义多个转换，但不能出现完全相同的源状态、目标状态、事件组合；重复定义必须抛出异常。
8. 转换选择遵循当前实现的顺序语义：
   先遍历候选转换；命中第一个满足条件的转换即执行；若没有命中条件转换，则回退到首个无条件转换；若都没有则保持原状态。
9. `fireEvent()` 在找不到可执行转换时，返回源状态，而不是抛出异常。
10. 找不到源状态定义时，必须抛出 `GXStateMachineException`，并先输出当前状态机结构用于排查。
11. 内部转换校验要求源状态和目标状态必须相同，否则必须抛出异常。
12. 动作执行发生在转换真正命中之后；动作内部异常不会被状态机吞掉，应继续向上传递。
13. `showStateMachine()` 和 `generatePlantUML()` 依赖 Visitor 模式，修改相关结构时必须同步保证访问者输出仍正确。
14. `generatePlantUML()` 当前输出的是纯文本状态转移关系，不维护初始状态箭头等额外语义。
15. 运行期并发复用依赖“构建期一次性定义、运行期只读使用”的模式，不能让运行期逻辑反向修改状态机结构。

## 4. 强制开发规则

1. 所有新增或修改的 `log` 日志内容必须只使用 ASCII 字符。异常消息、调试输出、日志模板也遵循同样规则。
2. 任何改动都必须同步检查对应测试用例是否完整覆盖；覆盖不足时必须补齐测试后再提交。
3. 不要随意把现有代码抽取成单独的方法或类；只有在抽取后的逻辑能被两个及以上位置复用，或能显著降低重复复杂度时，才允许抽取。
4. 删除方法、类、变量上的冗余注释，避免注释重复描述代码表面含义。
5. 对复杂方法、复杂类和关键扩展点，应补充必要的 Javadoc；对外使用成本较高的 API，应适当提供简短使用示例。
6. 修改公共接口、构建 DSL、路由规则、异常语义、Visitor 输出格式时，优先考虑向后兼容；如必须变更行为，需在提交说明中明确写出。
7. 不允许把运行期问题简单转化为“加反射、加全局状态、加隐式缓存”处理，除非有明确收益、风险分析和测试支撑。
8. 新增行为如果依赖顺序，必须在代码、Javadoc 或测试中明确体现该顺序语义。

## 5. 测试要求

1. 任何功能改动都必须至少检查成功路径、失败路径、异常路径和边界条件是否被覆盖。
2. 当前模块是纯 Java 状态机内核，现有核心能力以模块级单元测试为主即可；但如果后续引入 Spring Boot 自动装配、容器集成、Bean 注册、Starter 封装等能力，测试必须模拟真实 Spring Boot 应用启动后的运行形态，不能只写脱离容器的伪单测。
3. 修改 `GXStateMachineImpl`、`GXTransitionImpl`、`GXEventTransitions`、`GXStateImpl` 时，至少覆盖：
   `fireEvent()` 正常迁移、无匹配迁移返回原状态、条件不满足返回原状态、源状态不存在异常、内部转换非法配置异常。
4. 修改 `builder` 包时，至少覆盖：
   `externalTransition()`、`externalTransitions()`、`internalTransition()` 的正常构建与组合使用；重复转换定义异常；构建完成后工厂注册行为。
5. 修改 `GXStateMachineFactory` 时，至少覆盖：
   注册成功、重复 `machineId` 异常、按 ID 获取成功、获取不存在实例异常。
6. 修改 Visitor 相关实现时，至少覆盖：
   `showStateMachine()` 不报错、`generatePlantUML()` 产出的关键状态转移文本正确。
7. 修复缺陷时，必须新增一个先复现旧问题、再验证修复结果的回归测试。
8. 提交前至少保证模块级命令可执行：
   `mvn -pl leaf-base-statemachine -am test`

## 6. 注释与文档约定

1. 冗余注释要删除，例如“给字段赋值”“返回字段值”这类机械描述不应继续新增。
2. 复杂逻辑说明优先放在类级或方法级 Javadoc 中，不要把大量解释拆成零散行内注释。
3. 示例代码要尽量贴近模块真实使用方式，优先展示 `builder -> build -> fireEvent` 的完整最短路径。
4. 若新增注释中包含日志、异常文案示例，示例文本也建议保持 ASCII，以降低后续复制进日志的风险。

## 7. 变更前检查清单

1. 是否影响状态机“无状态、运行期只读”的核心设计。
2. 是否影响转换路由顺序语义，尤其是“优先命中满足条件的转换，否则回退无条件转换”。
3. 是否影响 `internalTransition()` 的源目标同态约束。
4. 是否影响 `GXStateMachineFactory` 的自动注册、唯一性和查找行为。
5. 是否影响 Visitor 输出格式或 PlantUML 文本结构。
6. 是否新增或保留了非 ASCII 的日志或异常消息。
7. 是否引入了不必要的方法/类抽取。
8. 是否清理了冗余注释，并为复杂逻辑补足了必要 Javadoc。
9. 是否补充并运行了与改动风险匹配的测试。

## 8. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一个 PR 或提交中同步更新本文件：

1. 模块职责边界发生变化，例如新增 Spring Boot 集成、持久化能力、分布式能力、Starter 能力。
2. 目录结构发生变化，尤其是新增或重组 `builder`、`impl`、测试目录、对外 API 包。
3. 状态机构建 DSL 发生变化，例如新增新的 transition 类型、调整调用顺序、修改构建约束。
4. 状态迁移路由规则发生变化，例如条件优先级、回退规则、无匹配行为、动作执行时机改变。
5. `GXStateMachineFactory` 的注册策略、唯一性规则、查找方式发生变化。
6. Visitor 输出语义变化，例如 PlantUML 输出格式、状态展示格式、初始化节点表达方式改变。
7. 测试策略发生变化，例如要求从单元测试升级为 Spring Boot 集成测试，或引入新的最低测试门槛。
8. 团队新增、删除或调整了需要长期遵守的开发规则，例如日志、测试、注释、Javadoc、重构约束。

如果本次改动不触发以上条件，则可以不更新本文件。

## 9. 提交说明建议

1. 涉及状态路由、条件选择、异常语义、工厂注册、Visitor 输出的变更，PR 描述中应明确旧行为、新行为和兼容性影响。
2. 涉及日志改动时，提交前确认新增日志文本为 ASCII。
3. 涉及测试补充时，PR 描述中应说明新增覆盖的风险点。

## 10. 推荐执行命令

```powershell
mvn -pl leaf-base-statemachine -am test
```
