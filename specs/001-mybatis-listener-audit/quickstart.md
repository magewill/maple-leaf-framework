# 快速验证指南：MyBatis-Plus 操作监听加固

本指南用于人工或 CI 验证 `spec.md` 的验收结果。所有命令在仓库根目录执行，Windows PowerShell 与 bash 均适用。

## 前置条件

| 项目 | 要求 |
| --- | --- |
| JDK | 21 及以上（根 POM `release=21`） |
| Maven | 3.8 及以上 |
| 依赖 | 需能拉取 Maven 中央仓库依赖；首次构建约 2 分钟 |
| 外部中间件 | **不需要**。监听链路验证全部基于单元测试、Mock 与 `ApplicationContextRunner` |

先确认基线可编译：

```bash
mvn -q -pl leaf-base-datasource -am -DskipTests install
```

## 一键验证

```bash
mvn -pl leaf-base-datasource -am test
```

预期：`BUILD SUCCESS`，无跳过（除显式标注的性能夹具）。

## 场景清单

| 编号 | 验证目标 | 建议执行方式 | 预期结果 | 对应验收 |
| --- | --- | --- | --- | --- |
| V1 | 生效判定：0 行不投递、异常不投递 | 定向运行回归测试类 | 事件发布次数为 0；异常按原样抛出 | SC-001 |
| V2 | 事务语义：回滚不投递"提交后"事件、提交后投递一次 | 回归测试 + 事务同步模拟 | 回滚场景发布 0 次；提交场景发布 1 次 | SC-001 |
| V3 | 去重：双接口继承同一声明只投递一次；不同监听器各一次 | 新增去重测试类 | 同一监听器 1 次、不同监听器各 1 次 | SC-002 |
| V4 | 条件还原：`OR`/函数/重复字段/参数错配 | 回归测试的条件解析用例 | `conditionLossless=false` 且 `rawWhereSql` 非空，字段值不错配 | SC-008 |
| V5 | 并发隔离：200 线程 × 7 类写操作 | 新增并发测试类 | 错配 0、重复 0、丢失 0、上下文串扰 0 | SC-002 |
| V6 | 异常隔离：一个监听器抛异常不影响其他监听器与业务结果 | 并发/回归测试 | 其他监听器仍被调用，写操作仍成功 | SC-001、SC-002 |
| V7 | 容量与拒绝：队列饱和、停机等待 | 执行器配置测试类 | 饱和时拒绝可观测、不在调用方执行、业务不失败 | SC-006、SC-007 |
| V8 | 开关回退：关闭整体开关 / 异步开关 | `ApplicationContextRunner` 属性驱动 | 关闭后排队的异步任务为 0，行为等同无监听基线 | SC-007 |
| V9 | 开销门禁：无声明零分配、有监听开销上限、批量内存 | 性能夹具测试类 | 无声明路径 0 次事件分配；比值在阈值内 | SC-003、SC-004、SC-005 |

## 定向执行示例

```bash
# 仅跑监听相关测试
mvn -pl leaf-base-datasource -am test -Dtest='GXMyBatis*Test'

# 只看并发用例
mvn -pl leaf-base-datasource -am test -Dtest='GXMyBatisListenerConcurrencyTest'

# 只看性能夹具（默认可能被跳过，需显式开启）
mvn -pl leaf-base-datasource -am test -Dtest='GXMyBatisListenerOverheadTest' -Dmaple.perf.enabled=true
```

## 开关验证方法

在测试或本地应用中设置属性后观察行为，例如：

```text
maple.framework.enable.mybatis-listener=false
maple.framework.mybatis.listener.propagate-exception=true
maple.framework.mybatis.listener.deduplicate=false
maple.framework.mybatis.async.listener.virtual.enabled=true
maple.framework.mybatis.async.listener.virtual.concurrency-limit=-1
```

预期：

- 关闭整体开关后，写操作不产生事件，也不触发声明解析。
- 恢复 `propagate-exception=true` 后，回调异常重新向调用方传播（用于对比旧行为）。
- 关闭去重后，多接口声明恢复多次投递（仅用于兼容验证，不建议生产使用）。
- 虚拟线程并发上限设为 `-1` 时不再有并发限流，队列与拒绝策略相应失效，日志中应有明确提示。

## 性能夹具口径

- 预热 ≥ 10,000 次，采样 ≥ 5 轮，取 P95。
- 门禁以"相对无监听基线的比值"判定，避免不同机器绝对耗时差异。
- 性能夹具默认不参与常规构建，需显式开启；不建议在共享 CI 上作为阻塞门禁，可作为趋势观察。
- 记录并归档：机器 CPU 核数、JVM 版本、`-Xmx`、JDK 虚拟线程开关状态。

## 常见问题

| 现象 | 排查方向 |
| --- | --- |
| 事件根本不触发 | 是否标注监听声明；是否命中写操作；返回值是否为 0 行；整体开关是否关闭 |
| 同一操作收到两次回调 | 是否多个接口都声明了监听；确认去重开关状态 |
| 异步任务未执行 | 队列是否饱和（查拒绝日志与计数）；应用是否已进入停机 |
| 条件字段为空但原始条件有值 | 属预期降级，查看 `degradeReasons` 与 `rawConditionSql` |
| 监听器改了载荷但业务对象也变了 | 检查 `snapshotDegraded`；该值类型不可克隆 |
| 回调里读到旧数据 | 同步模式在事务提交前执行，属既定语义；可开启 `sync-after-commit` |

## 验证记录要求

在 PR 中附：执行的命令、模块测试总数与结果、并发用例参数（线程数、事件数）、性能夹具的比值与机器信息。缺少其中任一项视为验证不完整。
