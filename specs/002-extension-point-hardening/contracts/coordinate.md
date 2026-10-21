# 契约：场景与坐标（`GXBizScenario` / `GXExtensionCoordinate`）

**契约状态**：公开方法签名冻结
**本次变更**：新增 `GXBizScenario.fromUniqueIdentity(String)`；字符串坐标构造新增三段格式校验；补充空值标注

## 1. 场景标识格式（不变）

```
uniqueIdentity            = bizId "." useCase "." scenario
identityWithDefaultScenario = bizId "." useCase "." "#defaultScenario#"
identityWithDefaultUseCase  = bizId "." "#defaultUseCase#" "." "#defaultScenario#"
globalDefault               = "#defaultBizId#" "." "#defaultUseCase#" "." "#defaultScenario#"
```

- 分隔符固定为 `.`；
- 三段任一不得为空/空白，且不得包含 `.`（否则唯一标识产生歧义）；
- 上述三个默认占位符字符串为对外契约常量，不得改名。

## 2. `GXBizScenario` 工厂

| 方法 | 语义 | 失败 |
| --- | --- | --- |
| `valueOf(bizId, useCase, scenario)` | 完整三段 | `IllegalArgumentException`（空/空白/含 `.`） |
| `valueOf(bizId, useCase)` | scenario 取默认占位符 | 同上 |
| `valueOf(bizId)` | useCase、scenario 取默认占位符 | 同上 |
| `newDefault()` | 全局默认场景 | 不失败 |
| `fromUniqueIdentity(identity)`（新增） | 由 `uniqueIdentity` 还原场景 | `IllegalArgumentException`（段数 ≠ 3、存在空白段） |

`fromUniqueIdentity` 的存在意义：让"字符串坐标"与"类型坐标"进入同一条兜底流程，而不是各自为政。

## 3. `GXExtensionCoordinate` 构造

### 3.1 类型坐标

```java
public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
```
- `extPtClass` 与 `bizScenario` 非空；
- `extPtClass` 必须是 `GXExtensionPoint` 的子接口，且不能是标记接口本身；
- 结果对象的 `getExtensionPointClass()`、`getBizScenario()` 非空。

### 3.2 字符串坐标

```java
public GXExtensionCoordinate(String extensionPoint, String bizScenario)
```
- 两者非空非空白；
- `bizScenario` 必须恰为三段点分、每段非空（**本次新增校验**）；不合规立即 `IllegalArgumentException`，信息含实际段数与非法段；
- 结果对象的 `getExtensionPointClass()`、`getBizScenario()` 为 `null`，并标注 `@Nullable`。

### 3.3 相等性

`equals`/`hashCode` 只使用 `extensionPointName + bizScenarioUniqueIdentity`，因此：

- 类型坐标与字符串坐标只要描述同一接口与场景，即相等；
- 二者`hashCode` 相同，可混用作映射键；
- `extensionPointClass` / `bizScenario` 是否为 `null` **不得**影响解析结果（修复后，字符串入口同样执行四层兜底）。

## 4. 序列化

- 两个类型均实现 `Serializable`；
- 序列化契约只包含 `bizId/useCase/scenario` 三段语义与坐标两个字符串字段；
- 不允许因本次改造改变字段语义（可新增 `serialVersionUID`，不得删除既有字段）。
