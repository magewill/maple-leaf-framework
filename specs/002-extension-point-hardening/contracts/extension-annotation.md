# 契约：扩展声明注解（`@GXExtension` / `@GXExtensions`）

**契约状态**：冻结（属性名、默认值、`@Repeatable` 关系、`@Component` 元注解均不改）
**本次变更**：仅修正"声明如何被解析成坐标"，注解本身零改动

## 1. 类型

```java
@Inherited
@Documented
@Retention(RUNTIME)
@Target(TYPE)
@Repeatable(GXExtensions.class)
@Component
public @interface GXExtension {
    String bizId()    default GXBizScenario.DEFAULT_BIZ_ID;
    String useCase()  default GXBizScenario.DEFAULT_USE_CASE;
    String scenario() default GXBizScenario.DEFAULT_SCENARIO;
}

@Inherited
@Documented
@Retention(RUNTIME)
@Target(TYPE)
@Component
public @interface GXExtensions {
    String[] bizId()    default GXBizScenario.DEFAULT_BIZ_ID;
    String[] useCase()  default GXBizScenario.DEFAULT_USE_CASE;
    String[] scenario() default GXBizScenario.DEFAULT_SCENARIO;
    GXExtension[] value() default {};
}
```

## 2. 声明形态与注册结果（必须逐项成立）

| # | 声明 | 期望注册坐标 |
| --- | --- | --- |
| 1 | `@GXExtension(bizId="a")` | `a.useCase.scenario`（用例与场景取默认占位符） |
| 2 | `@GXExtension(bizId="a")` + `@GXExtension(bizId="b")` | 两个坐标 |
| 3 | `@GXExtensions(value={@GXExtension(bizId="a"), @GXExtension(bizId="b")})` | 两个坐标 |
| 4 | `@GXExtensions(bizId={"a","b"}, useCase={"c"}, scenario={"d","e"})` | 四个坐标（笛卡尔积） |
| 5 | `@GXExtension(bizId="a")` + `@GXExtensions({@GXExtension(bizId="b")})` | 两个坐标（混用合法，两处都注册） |
| 6 | 仅 `@Component`，无场景声明 | **不注册**（不属于扩展，由 Spring 正常管理） |

第 2、5 种形态在 Java 语言层均合法（`javac` 实测通过），因此必须在解析层完整支持。

## 3. 失败契约

| 情形 | 行为 |
| --- | --- |
| 有场景声明，但解析不出任何坐标（如 `@GXExtensions(bizId={})`） | 抛 `GXExtensionException`，信息含实现类全名与声明形态；**不得静默** |
| 两条声明落到同一 `(扩展点, 场景)` | 抛 `GXExtensionException`，信息含坐标、已有实现类名、冲突实现类名 |
| 实现类没有可用扩展点接口（未实现标记接口 / 名称不含约定片段） | 抛 `GXExtensionException`，信息含实现类与候选接口 |
| 实现类同时对应多个扩展点接口 | 抛 `GXExtensionException`，信息列出全部候选 |
| 实现类只实现标记接口 `GXExtensionPoint` 本身 | 视为"无扩展点接口"，失败 |

失败发生在容器刷新完成之前，等价于启动失败。

## 4. 组合注解（`@AliasFor`）

通过 `@AliasFor` 组合到 `@GXExtension` 的自定义注解，其属性覆盖必须生效（既有用例 `composedAnnotationWithAliasForIsMergedCorrectly` 锁定）。本次解析改造不得退化为 `getAnnotationsByType` 等不处理合并语义的反射调用。

## 5. 继承语义

`@Inherited` 保持：子类未声明场景注解时继承父类声明。若父子同时声明且落到同一坐标，按"冲突"处理并失败，不得静默覆盖。
