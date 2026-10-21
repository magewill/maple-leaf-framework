# Contract: 审查报告条目结构

**Feature**: `003-seata-datasource-audit` | **产物**: `specs/003-seata-datasource-audit/review-report.md`

本契约约束审查报告的写法，保证"每条结论都能被第三方复核"。报告由实现阶段产出，`spec.md` 只保留摘要与引用（FR-016）。

---

## 1. 文件结构（固定顺序）

```text
# Seata × 动态数据源协作审查报告
## 1. 审查范围与方法
## 2. 结论总览（表格：编号 / 级别 / 标题 / 状态）
## 3. 证据（E-NN）
## 4. 发现（F-NN，每条形如 4.x）
## 5. 已确认无缺陷项
## 6. 复现索引（Finding → 命令/测试）
## 7. 环境与版本事实
```

---

## 2. 单条 Finding 模板（必须逐字段填写）

```markdown
### F-01 代理缺失时静默降级

- **级别**: BLOCKER
- **位置**: `leaf-base-datasource/src/main/java/cn/maple/core/datasource/config/GXDynamicDataSourceConfig.java:79-92`
- **触发条件**: 类路径不存在可反射到的 Seata 代理类，且 `seata.enable-auto-data-source-proxy=false`
- **影响**: 应用正常启动，但物理数据源未被包装；`@GlobalTransactional` 下属分支不会注册到 TC，事务退化为本地事务且不报错
- **证据**: 代码片段（引 3–8 行）；`seata-all-2.6.0.jar` 类清单输出
- **复现**: `mvn -pl leaf-base-datasource test -Dtest=GXSeataProxyContractTest#reportsOriginalDataSourceWhenProxyClassMissing`
- **建议**: 代理缺失改为 warn（含数据源名）；严格开关下启动失败
- **状态**: FIXED
```

---

## 3. 字段校验规则

| 规则 | 判定 |
| --- | --- |
| `级别` 只能是 `BLOCKER` / `RISK` / `CLEAN` | 其他值视为不合格 |
| `位置` 必须可定位 | 文件 + 行号区间，或依赖坐标 + 类全名；"某模块"这类模糊描述不合格 |
| `触发条件` 必须是可构造的前提 | 至少包含一个布尔条件（类路径 / 配置 / bean 形态） |
| `影响` 必须说明后果落到哪里 | 事务正确性 / 启动行为 / 类型契约 / 可观测性，四者之一 |
| `证据` 至少一条可独立核查 | 代码片段需带行号；jar 类清单需给出坐标与类名 |
| `复现` 必须可直接执行 | 测试全名或 shell 命令；不接受"阅读代码可知" |
| `状态` | `BLOCKER` 交付时必须 `FIXED`；`RISK` 可为 `ACCEPTED` 但需写明理由 |

---

## 4. 复现索引要求

`## 6. 复现索引` 必须是一张表，`Finding` 与命令/测试一一对应，且满足：

1. 每条 `BLOCKER` 至少 1 条命令；
2. 命令在**无网络、无 Seata Server、无 Nacos、无 MySQL** 的环境下可执行；
3. 命令若依赖模块构建，写明 `-am` 以免使用本地陈旧产物。

---

## 5. 与 spec/plan 的一致性要求

| 要求 | 检查方式 |
| --- | --- |
| 报告中的 `F-NN` 与 `spec.md` 摘要中的编号、级别一致 | 逐条对照 |
| 报告新增的 Finding 必须同步回 `spec.md` 摘要或明确标注"实现阶段新增" | 人工评审 |
| 报告中"已验证/已修复"的结论必须在 `quickstart.md` 中有对应验证步骤 | 双向引用 |
