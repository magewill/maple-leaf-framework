# AGENTS.md - leaf-base-redisson

本文档用于指导 `leaf-base-redisson` 模块后续迭代开发。修改本模块时，优先保持 Redisson 连接配置、缓存行为、可靠主题（Reliable Topic）和延迟队列转发链路的兼容性，避免无关重构。

## 1. 模块定位

`leaf-base-redisson` 是 Maple Leaf Framework 的 Redisson 基础能力模块，负责：

1. Redisson Client 与 Spring Cache 的统一装配（单机/集群连接、线程参数、缓存管理器配置）。
2. Redisson 可靠主题（Reliable Topic）的发送、订阅、取消订阅与本地监听缓存管理。
3. Redisson 延迟队列能力（发送延迟消息、清理队列、延迟消息转发到主题）。
4. Bean 生命周期中的 MQ 监听注册与注销（`GXRedissonMQPostProcessor`、`GXRedissonDelayMQPostProcessor`）。
5. Redisson 通用工具能力（分布式锁、计数器、限流、KV 操作）与缓存服务实现。
6. 本地配置与 Nacos 配置读取（`properties/local`、`properties/nacos`）。

不在本模块内承载业务流程编排和业务域规则。

## 2. 目录职责

1. `config`：Redisson 与 Redisson MQ 的核心配置装配。
2. `properties`：配置模型定义（含 `local`、`nacos` 子目录）。
3. `util`：通用 Redisson 操作工具（缓存、锁、限流、主题、延迟队列）。
4. `services` / `services.impl`：面向业务模块的缓存服务抽象与实现。
5. `listener`：MQ 监听契约和延迟消息处理契约。
6. `processor`：监听器自动注册、转发执行、生命周期清理。
7. `annotation`：延迟队列转发注解定义。
8. `adapter`：对外兼容适配（如 Debezium 可靠主题适配器）。

## 3. 强制开发规则

1. 所有 `log` 日志内容必须使用 ASCII 字符，禁止新增或保留非 ASCII 日志文案。
2. 涉及改动的点，必须同步检查对应测试用例是否完整覆盖；覆盖不足时必须补充测试。
3. 不要随意把已存在代码抽取成单独方法或类；仅当抽取后的逻辑能被两个及以上位置复用，或显著降低复杂度时才允许抽取。
4. 删除方法、类、变量上的冗余注释，避免注释重复描述代码字面含义。
5. 在复杂方法、复杂类、对外扩展点上，适当补充 Javadoc，并在必要时加入简短使用示例。
6. 每次修改只能修改当前 Maven 模块 `leaf-base-redisson` 内的文件，不得顺手修改其它模块。
7. 现有代码已经在生产环境使用，不得随意更改对外接口规范、配置语义、注解参数或异常语义。
8. 作为基础框架模块，代码变更必须重点关注性能、资源释放、并发可见性和线程安全性。
9. 在线程池与虚拟线程都能满足需求的场景中，优先使用虚拟线程；若继续使用线程池，必须说明容量、生命周期和拒绝策略的合理性。
10. 变更连接配置解析逻辑（单机/集群判断、地址解码、线程参数默认值）前，必须评估兼容性影响。
11. 变更 MQ 监听注册与注销逻辑（本地缓存键、重复订阅控制、销毁阶段清理）前，必须评估消息重复消费和监听泄漏风险。

## 4. 测试要求

1. 任何功能改动必须先看现有测试是否覆盖：成功路径、失败路径、异常路径、边界条件。
2. 测试用例要覆盖全面；如果功能依赖 Spring 容器、自动配置、BeanPostProcessor、线程池生命周期、Redisson Bean 装配或应用启动流程，测试必须使用接近真实运行形态的 Spring Boot 启动测试（如 `@SpringBootTest`）。
3. 优先覆盖以下关键能力：
   - `GXRedissonSpringDataConfig` / `GXRedissonMQConfig` 的配置解析、默认值、非法配置分支。
   - `GXRedissonMQUtils` 的订阅去重、强制订阅、取消订阅、缓存清理行为。
   - `GXRedissonDelayMQPostProcessor` 的转发成功、超时重试、回退入队、销毁清理流程。
   - `GXRedissonCacheServiceImpl` 的 TTL 刷新、批量写入、参数校验和边界值。
   - `GXRedissonUtils` 的锁、计数器、限流与异常封装行为。
4. 当前模块尚无 `src/test` 基线；后续任何行为变更都应补充最小可回归测试集，并保证模块级测试命令可执行：`mvn -pl leaf-base-redisson test`。

## 5. 变更前检查清单

1. 是否影响 `redissonClient`、`redissonMQClient`、`RedissonSpringCacheManager` 的装配语义。
2. 是否影响可靠主题订阅键生成规则（实例维度、topic、messageClass）与幂等注册行为。
3. 是否影响延迟队列转发失败后的重试和回退策略。
4. 是否新增或修改了非 ASCII 日志文本。
5. 是否引入了不必要的方法/类抽取。
6. 是否移除了冗余注释并补齐了必要 Javadoc/示例。
7. 是否已补充或更新测试，且覆盖本次改动风险点。
8. 是否只修改了 `leaf-base-redisson` 当前 Maven 模块内的文件。
9. 是否保持生产环境已使用的对外接口规范和行为兼容。
10. 是否评估了性能、资源释放和线程安全风险。
11. 是否在适用场景优先考虑虚拟线程。

## 6. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一 PR/提交中同步更新本文件：

1. 模块职责边界变化（新增/移除核心能力）。
2. 目录结构变化（新增关键包、迁移核心类、删除关键组件）。
3. 对外契约变化（`GXRedissonCacheService`、`GXRedissonMQListener`、`GXRedissonDelayMQListener`、注解参数）。
4. 连接配置或默认值策略变化（地址解析、单机/集群判定、线程参数、鉴权字段）。
5. MQ 转发策略变化（监听注册规则、重试策略、失败回退策略、销毁清理流程）。
6. 测试策略变化（新增必须的 Spring Boot 集成测试形态、测试基线调整）。
7. 团队新增了可复用且需长期遵守的开发约束（编码、日志、测试、文档、兼容性规则）。
8. 模块级变更边界、生产兼容性、性能或线程模型约束发生变化。

## 7. 提交说明建议

1. 涉及配置装配、监听注册、延迟转发链路的变更，PR 描述必须说明行为变化和兼容性影响。
2. 涉及重试、回退、幂等订阅等可靠性策略调整时，PR 描述需包含风险评估和回滚方案。
3. 提交前确认新增/修改日志均为 ASCII。
4. 提交前确认测试已覆盖改动点，并在 PR 中说明关键验证结果。
