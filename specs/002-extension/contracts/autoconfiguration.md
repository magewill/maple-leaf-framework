# 契约：自动装配与注册时机

**契约状态**：Bean 名称、类型、条件全部冻结
**本次变更**：仅注册时机（`@PostConstruct` → `SmartInitializingSingleton`）与注册顺序（按 Bean 名排序）

## 1. 自动装配入口（不变）

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
cn.maple.extension.autoconfigure.GXExtensionAutoConfiguration
```

## 2. 默认 Bean（名称与类型不变）

| Bean 名称 | 类型 | 条件 |
| --- | --- | --- |
| `extensionBootstrap` | `GXExtensionBootstrap` | `@ConditionalOnMissingBean` |
| `extensionRepository` | `GXExtensionRepository` | `@ConditionalOnMissingBean` |
| `extensionExecutor` | `GXExtensionExecutor` | `@ConditionalOnMissingBean` |
| `extensionRegister` | `GXExtensionRegister` | `@ConditionalOnMissingBean` |

约束：

- 下游声明同类型 Bean 时，自动装配必须整体让位（含仓库被注册器与执行器共享）；
- 上述四个类型同时带 `@Component`，因此当应用扫描到 `cn.maple.extension` 包时，由扫描注册、自动装配让位；两种装配方式并存时**不得**出现同名或同类型重复 Bean；
- Bean 名称属于对外契约，不得改名。

## 3. 注册时机

| 阶段 | 行为 |
| --- | --- |
| 单例实例化期间 | 不做任何扩展注册 |
| 全部单例实例化完成、容器刷新完成前（`SmartInitializingSingleton`） | 扫描 `@GXExtension` 与 `@GXExtensions` 的 Bean，去重后按 Bean 名排序，逐个解析坐标并注册 |
| 刷新完成后 | 仓库只读；不提供任何注册/注销/刷新入口 |

失败语义：任一坐标冲突、声明非法、扩展点接口非法 → 抛异常，容器刷新中止（等同启动失败）。

顺序语义：按 Bean 名排序后注册，因此"同一坐标两条声明"时"已有实现 / 冲突实现"的角色在多次启动间保持一致，报错可复现。

## 4. 延迟初始化

`@Lazy` 扩展 Bean 仍需被注册：注册流程按 Bean 定义元数据取名称，再显式取实例，与现状"注册即实例化"的可见结果一致。

## 5. 覆盖方式（不得改变）

下游覆盖默认实现的唯一方式是声明同类型 Bean；本次不新增开关、不新增配置键、不引入 `@ConditionalOnProperty`，因此 `additional-spring-configuration-metadata.json` 无需变更。
