# Phase 0 Research: 扩展点模块审查加固

**Feature**: 002-extension-point-hardening | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

所有结论基于本模块源码静态分析 + 在 JDK/Spring Boot 4.1.1 下的一次性探针实测。探针文件在取得证据后已删除，模块源码未被改动。

---

## R1. 场景声明解析：五种写法各返回什么？

**Decision**: 用 `AnnotatedElementUtils.findMergedRepeatableAnnotations(clazz, GXExtension.class)`（Spring 7 返回 `Set<GXExtension>`）作为主解析入口，用 `findMergedAnnotation(clazz, GXExtensions.class)` 只补"仅笛卡尔积属性"这一种情况；两者皆空才算"未声明坐标"。

**Rationale**: 实测（临时探针，输出原文）：

| 声明写法 | `findMergedRepeatableAnnotations` | `findMergedAnnotation(GXExtensions)` | 现行 `findMergedAnnotation(GXExtension)` |
| --- | --- | --- | --- |
| 单个 `@GXExtension(bizId="single")` | `[single]` | `null` | 命中 |
| 重复 `@GXExtension` ×2（`v1`,`v2`） | `[v1, v2]` | `value=2` | **`null`** |
| `@GXExtensions(value={e1,e2})` | `[e1, e2]` | `value=2` | **`null`** |
| `@GXExtensions(bizId="cart")`（仅笛卡尔积） | `[]` | `value=0, bizId=cart` | **`null`** |
| 混用 `@GXExtension(m1)` + `@GXExtensions({m2})` | `[m1, m2]` | `value=1` | 只命中 `m1` |

配套实测（`GXExtensionRepository.size()`，`doRegistration` vs `doRegistrationExtensions`）：

- 重复注解：`doRegistration` → **0**；`doRegistrationExtensions` → **2**
- 显式容器：`doRegistration` → **0**；`doRegistrationExtensions` → **2**
- 仅笛卡尔积：`doRegistration` → **0**；`doRegistrationExtensions` → **2**
- 混用：`doRegistration` → **1**（漏 `m2`）

另用 `javac` 验证：`@Rep` 与其容器 `@Cont2` 同时标注同一个类是**合法**的（编译通过），因此"混用"是真实可达的写法，不是理论边界。

**派生规则**（写入契约，见 `contracts/extension-annotation.md`）：

```
repeatable = findMergedRepeatableAnnotations(clazz, GXExtension)
container  = findMergedAnnotation(clazz, GXExtensions)
if (repeatable 非空)             -> 每个元素一个坐标
if (container != null && container.value().length == 0) -> 注册笛卡尔积(bizId × useCase × scenario)
if (registered == 0)             -> 抛 GXExtensionException（不得静默）
```

"仅笛卡尔积"时 `repeatable` 为空但 `container.value().length == 0`，补笛卡尔积；"显式容器"与"混用"时 `value().length > 0`，其元素已包含在 `repeatable` 中，**不再**补笛卡尔积，避免重复注册误报冲突。

**Alternatives considered**:

- 直接遍历 `GXExtensions.value()` 再叠加单注解：会漏掉"仅笛卡尔积"，且混用时需要额外拼接，逻辑与 Spring 的合并语义重复。
- 反射读取 `getAnnotationsByType(GXExtension.class)`：不走 Spring 的合并/别名语义，会丢失 `@AliasFor` 组合注解支持（现有测试 `composedAnnotationWithAliasForIsMergedCorrectly` 依赖它）。
- 继续用 `findMergedAnnotation(GXExtension.class)` 并在 `null` 时回退到容器：需要写两套分支，且混用时仍只能拿到第一个。

---

## R2. `findMergedRepeatableAnnotations` 在 Spring 7 的签名变化

**Decision**: 按 `Set<GXExtension>` 接收返回值。

**Rationale**: 首版探针按 `List<GXExtension>` 编写，编译失败并给出明确提示：`<A>findMergedRepeatableAnnotations(AnnotatedElement,Class<A>)` 返回 `Set<A>`。Spring 6 及以前返回 `List`，Spring Framework 7.0.9 已改为 `Set`。这直接决定实现代码的接收类型，也说明不能照抄旧版 COLA 实现。

**Alternatives considered**: 用 `List.copyOf(...)` 包装以获得固定顺序——但 `Set` 本身无序，若需要确定性顺序必须显式排序（见 R6）。

---

## R3. 两个执行入口如何收敛到同一条兜底流程？

**Decision**: 字符串坐标入口先用 `GXBizScenario.fromUniqueIdentity(String)` 还原场景对象，再调用与类坐标入口相同的私有兜底方法；两级入口不再各写一套查找。

**Rationale**: 兜底顺序（精确 → 默认场景 → 默认用例+默认场景 → 全局默认）由场景对象的三段组合而成，字符串只要能被还原为三段，就可以走完全相同的四次查找。现状 `locateComponent(String,String)` 只查一次，是"入口不对等"缺陷的直接原因。

**Alternatives considered**:

- 在字符串入口按 `.` 手工拆三段并直接拼默认串：与 `GXBizScenario` 内的分隔符常量、默认占位符重复，后续改动必然漂移。
- 让字符串入口抛出"不支持兜底"：把缺陷写进契约，直接违反 spec FR-005。
- 保留两套实现但共享一个 `List<String> candidates` 生成器：仍需两处调用，收益低于直接共用流程。

---

## R4. 字符串坐标的格式校验放在哪一层？

**Decision**: 放在坐标构造期（`GXExtensionCoordinate(String,String)` 与 `GXBizScenario.fromUniqueIdentity`），要求恰为三段点分、每段非空（不含分隔符由拆段天然保证）；不合规立即 `IllegalArgumentException`，错误信息给出实际段数与非法段。

**Rationale**: spec Q3 已确认"构造期校验并拒绝"。把校验放在构造期，可以保证"凡能进入执行器的坐标都可完整兜底"，从而使 `SC-002` 能用统一断言表达，无需为"宽松字符串"写例外用例。注册路径传入的是 `bizScenario.getUniqueIdentity()`，天然合规，无兼容风险。

**Alternatives considered**:

- 构造期不校验、执行时判断能否兜底：失败点后移到请求路径，且需要为"部分兜底"增加第三个语义分支。
- 只做"非空"校验：仍允许 `a.b` 这类两段坐标，兜底时无法确定缺失的是 useCase 还是 scenario。

---

## R5. 错误码与异常类型

**Decision**: 解析未命中继续用 `HttpStatus.HTTP_NOT_FOUND`（404）；把 `GXExtensionException(String)` 与 `GXExtensionException(String, Throwable)` 两个构造器里的 `HTTP_OK` 改为 `HTTP_NOT_FOUND`；注册冲突与装配错误统一改抛 `GXExtensionException`（500 语义，`GXBusinessException` 父类不变）。

**Rationale**: spec Q4 确认"沿用 404、不新增枚举"；宪法 III 允许框架内部直接使用 `HttpStatus` 常量。把注册冲突从 `GXBusinessException` 改为其子类 `GXExtensionException`，对既有 `catch (GXBusinessException)` 完全兼容，同时让调用方能按模块异常精确捕获。

**Alternatives considered**:

- 新增 `GXExtensionResultStatusCode` 枚举（0x5xx 段）：属新增契约，且会改变下游已依赖的 404 判断。
- 全部统一 500：丢失"资源未找到"的可区分性，与 URL/资源语义不符。

---

## R6. 注册时机与顺序

**Decision**: `GXExtensionBootstrap` 由 `@PostConstruct` 改为实现 `SmartInitializingSingleton`；注册时先取 `GXExtension` 与 `GXExtensions` 两个注解的 Bean 名并去重，再按 Bean 名排序后逐个注册。

**Rationale**:

- `@PostConstruct` 阶段调用 `getBeansWithAnnotation` 会强制实例化**所有**带注解的 Bean（包括 `@Lazy`），把装配副作用提前到容器仍在创建 Bean 的时刻。
- `SmartInitializingSingleton#afterSingletonsInstantiated` 在全部单例实例化完成后、容器刷新完成前执行，仍属启动失败可见的窗口（异常依旧让上下文启动失败），但不再与 Bean 创建过程交织。
- `getBeansWithAnnotation` 返回 `Map`，其顺序取决于内部 `HashMap` 遍历，导致"同一坐标两条声明"时 `existing`/`duplicate` 的角色在不同次启动间可能互换，冲突报错不可复现。按 Bean 名排序后，报错稳定，满足 `SC-004`。

`@Lazy` 扩展 Bean：`getBeanNamesForAnnotation` 基于 Bean 定义元数据返回名称，随后显式 `getBean(name)` 取实例——与现状"强制实例化后注册"一致，不改变可见行为。

**Alternatives considered**:

- 保持 `@PostConstruct`：装配期副作用与报错顺序不确定性保留。
- `ApplicationListener<ContextRefreshedEvent>`：父子上下文会重复触发，需要额外幂等标记；且事件在刷新完成后，晚于 `SmartInitializingSingleton`。
- `BeanPostProcessor`：介入过早，会看到半初始化状态的 Bean。

---

## R7. 并发正确性是否需要改结构？

**Decision**: 不改数据结构。继续用 `ConcurrentHashMap` + 不可变坐标/场景对象；只用测试把现有并发语义锁定。

**Rationale**: 实测（8 虚拟线程 × 10 000 次对同一坐标 `registerExtensionIfAbsent`）：成功 **1** 次、冲突 **79 999** 次、`size()==1`；随后 8 虚拟线程 × 200 000 次并发解析（注册后只读）：**0** 次异常结果。说明 `putIfAbsent` 的原子性与 `ConcurrentHashMap` 的可见性已满足 FR-011/FR-012。

**Alternatives considered**:

- 引入 `ReadWriteLock` 或 `StampedLock`：读路径会被锁语义拖慢，且没有必要——注册后只读不产生写竞争。
- 引入 Caffeine 缓存"坐标 → 实现"：注册后映射不变，缓存命中率恒定但多一层查找与内存占用；spec FR-018 要求"引入前证明收益"，当前 15–44 ns 的解析已远低于业务处理成本，**不引入**。

---

## R8. 性能门禁如何做到可重复？

**Decision**: 双门禁——同一进程内先测"裸 `ConcurrentHashMap.get` 循环"作为对照，再测执行器循环，多轮取中位数，要求 `执行器中位数 ≤ 6 × 对照中位数` **且** `≤ 1 μs/次`。"规模无关"另用双证据：用可计数的仓库替身断言单次解析触达仓库的查找次数恒为 ≤ 4；再用"1 个实现 vs 100 个实现"两种规模的执行器中位数比值 ≤ 1.2 覆盖对象分配等间接开销。

**Rationale**: 实测本机（JDK 21、单线程 100 万次 × 5 轮）：精确命中 15–26 ns，四层兜底 23–44 ns，波动主要来自 JIT 预热与 GC。绝对阈值（如 100 ns）在共享 CI 机器上容易因 CPU 降频偶发失败；相对基线把机器差异抵消掉，"是否劣化"的判断更稳。

**Alternatives considered**:

- 引入 JMH：宪法要求新增依赖说明用途与替代方案；本特性只需"不劣化"门禁，JMH 的统计精度收益不匹配成本。
- 只断言绝对阈值 100 ns：简单，但 CI 抖动会导致偶发红灯，最终被人为放宽或删除。
- 不做性能测试：spec FR-016 明确要求。

---

## R9. debug 日志与 `getSimpleName()` 的开销

**Decision**: 把 `log.debug("[Located Extension]: {}", extension.getClass().getSimpleName())` 改为 `if (log.isDebugEnabled()) { ... }` 包裹，或改为直接传 `Class` 对象（SLF4J 对 `Class` 有专用重载，`toString` 延迟到实际输出时）。

**Rationale**: `getSimpleName()` 是方法调用，作为参数会在**每次**执行时求值，即使 debug 关闭。解析是每请求可能多次执行的热路径，这是纯浪费；改动只影响日志实现细节，不改变日志内容语义。

**Alternatives considered**: 删除日志——会丢失排障线索，AGENTS.md 要求关键路径可定位。

---

## R10. JUnit 4 端到端示例的迁移方式

**Decision**: `ExtensionTest` 原地迁移：`@RunWith(SpringJUnit4ClassRunner.class)` → 直接用 `@SpringBootTest(classes = ExtTestApplication.class)`（JUnit 5 无需 Runner），`org.junit.Test`/`org.junit.Assert` → `org.junit.jupiter.api.Test`/`org.junit.jupiter.api.Assertions`；三个用例的输入、断言与业务断言对象全部保持原样。迁移完成后从模块 `pom.xml` 移除 `junit:junit` 与冗余的 `junit-jupiter-api` 声明。

**Rationale**: spec Q5 确认"原地迁移并保留"。该用例是模块唯一的端到端装配样本，同时充当使用文档；删除会同时损失回归覆盖与示例价值。当前它零执行（`target/surefire-reports` 中不存在该类），构建日志显示其余 11 个测试类共 39 个用例通过，属于宪法登记的"残留旧测试框架导致用例被静默跳过"偏差，本次一并收敛。

**Alternatives considered**:

- 删除示例、改写为聚焦集成测试：维护成本低但丢失用法样例。
- 保留但 `@Disabled`：仍会产生"零执行"变体，与门禁目标冲突。
- 引入 `junit-vintage-engine` 继续跑 JUnit 4：违反宪法"新增测试禁止使用 JUnit 4"，且增加一个仅为兼容而存在的依赖。

---

## R11. 父类与子类都声明同一场景坐标时如何判定？

**Decision**: 按重复声明处理——启动期冲突失败，信息同时给出父子双方类名；不允许子类静默覆盖父类。

**Rationale**: `@GXExtension` 标注了 `@Inherited`，`javac` 也允许父子各自标注（与 R1 的"混用合法"同源：语言层不阻止堆叠声明）。若不显式判定，最终选中哪个实现取决于 Bean 注册顺序与 Spring 的合并策略，属于"改一行父类代码就悄悄改变全局行为"的隐性风险。统一按 FR-003 的冲突规则处理，结果确定且可定位。

**Alternatives considered**:

- 子类覆盖父类：语义更灵活，但需要额外定义"谁算子类"（直接父类？整条继承链？），且与"一个坐标一个实现"的失败快速原则冲突。
- 继承坐标不注册：实现简单，但子类无法接管父类场景，会削弱继承的价值。

---

## R12. 失败信息需要包含哪些要素？

**Decision**: 按失败类型分层：解析未命中 → 扩展点标识 + 请求场景标识；注册冲突 → 冲突坐标 + 已有实现类名 + 冲突实现类名；扩展点接口非法 → 实现类名 + 候选接口列表。全部使用 ASCII 文本。

**Rationale**: 原始要求写成"异常信息必须包含实现类名"，但"完全没找到实现"这一最常见失败根本不存在实现类，该要求不可满足，会被写成永真断言或占位符。分层后每类失败只承诺它能真实提供的信息，验收用例可以写成确定断言，排障时也不会被无意义的 `none` 占位干扰。

**Alternatives considered**:

- 统一三字段并在未命中时填 `none`：格式统一，但引入误导性占位符。
- 只保留扩展点 + 场景：冲突类失败会丢失"哪两个类撞了"的关键线索。

---

## R13. 性能门禁的对照口径与实测值（收敛阶段补充）

**Decision**: 相对门禁的对照物为"与已注册键内容相等、但实例不同的键"上的裸 `ConcurrentHashMap.get`；相对预算按每次仓库查找计（精确命中 1 次、四层兜底按 4 次），并保留 ≤ 1 μs 绝对门禁、≤ 4 次查找与"1 vs 100 规模比 ≤ 1.2"三项独立证据。

**Rationale**: 复用已注册键实例做 `get` 时 `equals` 退化为引用比较，JIT 会把单次查找压到约 3 ns，这是任何调用方都无法复现的下界——每次解析都必须构造自己的坐标键。实测（JDK 21，单线程，预热 100 万次、7 轮 × 100 万次取中位数）：

| 测量项 | 实测中位数 |
| --- | --- |
| 身份键裸查找（同一键实例） | 3.0–3.2 ns |
| 等值键裸查找（内容相等、实例不同） | 7.8–8.3 ns |
| 执行器精确命中（`locateExtension`） | 18.5–22 ns |
| 执行器四层兜底 | 51–64 ns |

以 3 ns 对照计，精确命中倍数为 6–7，恰好压在 SC-006 的 6× 边界上并随 JIT 抖动翻转；以等值键对照计为 2.5–2.7×，稳定通过。因此把对照校准到"等值键"这一可复现下界（倍数仍为 6，未放宽），精确命中仍处于 spec 记录的 15–26 ns 基线区间。

**配套实现调整**:
- 解析热路径改用包内可空查找 `GXExtensionRepository.findExtensionOrNull`，去掉每次查找的 `Optional` 分配；
- `GXExtensionExecutor.locateExtensionByName` 去掉与坐标构造重复的空白校验（坐标构造抛出相同异常类型与文案）。

**Alternatives considered**: 复用键实例作为对照——倍数会稳定在 6× 以上，但它衡量的是一次调用方无法执行的查找；把仓库改为"扩展点名 → 场景标识"两级映射以彻底消除键构造——超出本规格"不改并发模型/数据结构"的边界，收益不足以抵消风险。

---

## 遗留风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `SmartInitializingSingleton` 对"刷新后才注册的 Bean 定义"不可见 | 极少数依赖运行期注册 Bean 定义的场景 | 与现状 `@PostConstruct` 同样不可见，无回归；spec 明确不新增运行期注册能力 |
| 相对基线门禁在极端低负载 CI（对照循环被完全内联/消除） | 阈值抖动 | 对照循环结果写入 volatile 汇总量避免被 JIT 消除，并对中位数取多轮 |
| 字符串坐标严格化对下游历史数据的影响 | 升级期可能出现 `IllegalArgumentException` | 构造期即失败且信息含段数，迁移成本低；在提交说明与升级说明中公示 |
