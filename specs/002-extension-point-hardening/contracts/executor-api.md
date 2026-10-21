# 契约：执行入口（`GXAbstractComponentExecutor` / `GXExtensionExecutor`）

**契约状态**：方法签名冻结，仅补齐行为一致性
**本次变更**：字符串坐标入口获得与类型入口完全一致的兜底能力

## 1. 公开入口

```java
public <R, T> R execute(Class<T> targetClz, GXBizScenario bizScenario, Function<T, R> exeFunction);
public <R, T> R execute(GXExtensionCoordinate extensionCoordinate, Function<T, R> exeFunction);
public <T> void executeVoid(Class<T> targetClz, GXBizScenario context, Consumer<T> exeFunction);
public <T> void executeVoid(GXExtensionCoordinate extensionCoordinate, Consumer<T> exeFunction);

protected abstract <C> C locateComponent(Class<C> targetClz, GXBizScenario context);
protected <C> C locateComponent(String extensionPointName, String bizScenarioUniqueIdentity);  // 默认实现保持
```

四个公开入口、两个 `protected` 钩子的签名与可见性均不变；`GXExtensionExecutor` 同时覆盖两个 `locateComponent`。

## 2. 兜底顺序（两个入口必须完全一致）

| 步骤 | 查找键 |
| --- | --- |
| 1 | `bizId.useCase.scenario` |
| 2 | `bizId.useCase.#defaultScenario#` |
| 3 | `bizId.#defaultUseCase#.#defaultScenario#` |
| 4 | `#defaultBizId#.#defaultUseCase#.#defaultScenario#` |

规则：

- 命中即返回，不再继续后续步骤；
- 字符串坐标入口**先**把坐标标识还原为场景对象，再走同一流程；不存在"字符串入口只做精确匹配"的例外；
- 四次查找全部未命中 → 抛 `GXExtensionException`，错误码 `404`，信息含扩展点标识（类名或名称）与请求场景标识；
- 查找次数恒 ≤ 4，与已注册扩展数量无关。

## 3. 入参契约

| 参数 | 非空 | 校验时机 | 失败类型 |
| --- | --- | --- | --- |
| `targetClz` | 是 | 调用前 | `NullPointerException` |
| `bizScenario` | 是 | 调用前 | `NullPointerException` |
| `exeFunction` / `Consumer` | 是 | 调用前 | `NullPointerException` |
| `extensionCoordinate` | 是 | 调用前 | `NullPointerException` |

坐标对象本身的合法性在**构造期**校验（见 `coordinate.md`）；执行器不重复校验，也不接受"半合法"坐标。

## 4. 异常契约

| 情形 | 异常 | 错误码 |
| --- | --- | --- |
| 四层兜底均未命中 | `GXExtensionException` | `404`（`HttpStatus.HTTP_NOT_FOUND` 常量） |
| 入参为 `null` | `NullPointerException` | 不适用 |
| 坐标构造非法 | `IllegalArgumentException` | 不适用 |
| 业务回调自身抛出的异常 | 原样向上抛出，不包装、不吞掉 | 由业务决定 |

`GXExtensionException` 继续继承 `GXBusinessException`，全局异常处理链路与对外响应结构不变。

### 4.1 信息要素（按失败类型分层）

| 失败类型 | 必须出现的要素 |
| --- | --- |
| 解析未命中 | 扩展点标识（类全名或名称）+ 请求场景标识 |
| 注册冲突 | 冲突坐标 + 已有实现类名 + 冲突实现类名 |
| 扩展点接口非法 | 实现类全名 + 候选接口列表（或"无候选"说明） |
| 坐标构造非法 | 字段名 + 实际段数 + 非法段 |

所有信息必须是 ASCII 文本。未命中路径**不要求**出现实现类名（该路径不存在实现类）。

## 5. 线程与性能契约

- 执行器与仓库均为无状态/只读共享对象，可被任意线程并发调用；
- 解析不修改任何共享状态，不持有锁；
- 解析路径不做反射、不做字符串拼接、不做全量拷贝；
- debug 日志关闭时不得产生额外求值开销（不在参数位置调用 `getSimpleName()` 等方法）。
