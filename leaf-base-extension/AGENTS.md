# AGENTS.md (leaf-base-extension)

本文档用于指导 `leaf-base-extension` 模块的后续迭代开发。修改本模块时，优先保持扩展点注册、扩展执行、Spring Boot 自动装配和测试覆盖的稳定性，避免无关重构。

## 1. 模块定位

`leaf-base-extension` 是 Maple Leaf Framework 的扩展点基础模块，负责：

1. 扩展点模型定义：`GXExtensionPoint`、`GXExtension`、`GXExtensions`。
2. 业务场景标识：`GXBizScenario`，唯一标识为 `bizId.useCase.scenario`。
3. 扩展仓储：`GXExtensionRepository`，负责扩展实现的注册与查询。
4. 扩展注册：`GXExtensionRegister`，负责从注解与接口信息中解析扩展并注册到仓储。
5. 扩展执行：`GXExtensionExecutor`，按完整场景、默认场景、默认用例、全局默认的顺序进行兜底查找并执行。
6. Spring Boot 自动装配：`ExtensionAutoConfiguration`、`GXExtensionBootstrap`。

本模块不承载具体业务规则，也不建议引入与扩展点机制无关的抽象层。

## 2. 开发约束

1. 所有日志内容必须使用 ASCII 字符，禁止新增非 ASCII 的日志文案。
2. 涉及改动的点，都必须检查对应测试用例是否完整；不完整时必须补齐。
3. 不要随意把已有代码抽取成单独方法或类；只有当抽取后的逻辑可被两个及以上位置复用时，才允许抽取。
4. 删除方法、类、变量上的冗余注释。
5. 对复杂方法、复杂类、对外扩展点，适当补充 Javadoc，并在必要时补充简短使用示例。
6. 所有修改应优先保持现有对外行为不变；若行为变化不可避免，必须同步补充测试和说明。

## 3. 测试要求

1. 任何功能改动前，先检查现有测试是否覆盖成功路径、失败路径、异常路径和边界条件。
2. 测试用例要覆盖全面；如果功能需要在 Spring Boot 应用运行后才能验证，那么测试必须模拟真实 Spring Boot 启动后的场景，例如使用 `@SpringBootTest` 或等价方案。
3. 修改 `GXExtensionExecutor`、`GXBizScenario`、`GXExtensionRepository`、`GXExtensionRegister` 时，至少覆盖正常路径、边界输入、兜底路径和异常路径。
4. 修改自动装配或启动注册逻辑时，必须包含 Spring 上下文级别测试。
5. 已修复的缺陷必须补充回归测试，确保旧问题可以复现且新代码下通过。

## 4. AGENTS.md 自动更新时机

出现以下任一情况时，必须同步更新本文件：

1. 扩展定位或执行顺序发生变化。
2. 注解模型或扩展点命名约定发生变化。
3. 自动装配 Bean 集合或初始化时机发生变化。
4. 测试策略、测试工具链或测试分层标准发生变化。
5. 本文件中的强制规则被新增、删除或调整。

## 5. 提交前检查

1. 日志文本是否全部为 ASCII。
2. 变更点是否有完整测试覆盖。
3. 是否存在不必要的抽方法或抽类重构。
4. Spring Boot 运行态相关功能是否使用了运行态测试。
5. 冗余注释是否已清理。
6. 复杂逻辑是否补充了必要的 Javadoc 与示例。
7. 本次改动是否触发 `AGENTS.md` 更新条件。

## 6. 推荐命令

```powershell
mvn -pl leaf-base-extension -am test
```

