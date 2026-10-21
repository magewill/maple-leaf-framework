# Quickstart: 验证 Seata 与动态数据源协作加固

**Feature**: `003-seata-datasource-audit` | **Date**: 2026-10-21

本指南只做验证，不含实现代码。全部步骤在**无网络、无 Seata Server、无 Nacos、无 MySQL** 的环境可执行。

---

## 1. 前置条件

| 项 | 要求 | 检查命令 |
| --- | --- | --- |
| JDK | 21+（`JAVA_HOME` 已设置） | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| 依赖 | 本地仓库已缓存 Seata 2.6.0（`org.apache.seata:seata-spring-boot-starter`） | `mvn -pl leaf-base-seata dependency:tree -Dincludes=org.apache.seata` |

期望：`dependency:tree` 输出包含 `org.apache.seata:seata-spring-boot-starter:2.6.0`，且**不出现** `io.seata:*`。

---

## 2. 模块级验证命令

```powershell
# 数据源模块：契约测试 + 既有行为回归
mvn -pl leaf-base-datasource -am test

# Seata 模块：装配 / 校验 / 严格模式 / 配置互斥
mvn -pl leaf-base-seata -am test
```

**期望**: 两个命令均 `BUILD SUCCESS`；`leaf-base-datasource` 既有用例 0 失败；新增用例全部执行（Surefire 报告中的 `Tests run` 计数不为 0）。

---

## 3. 场景验证清单

### S1 代理生效（PROXY_APPLIED）

```powershell
& "$env:JAVA_HOME\bin\javap.exe" -classpath "$env:USERPROFILE\.m2\repository\org\apache\seata\seata-all\2.6.0\seata-all-2.6.0.jar" org.apache.seata.rm.datasource.SeataDataSourceProxy
```

**期望**: 输出接口定义，包含 `getTargetDataSource()` 与 `getBranchType()`；命令不报 `class not found`。
（若本地仓库不在默认位置，用 `mvn -pl leaf-base-seata dependency:build-classpath` 得到的路径替换。）

### S2 代理缺失（PROXY_MISSING）

```powershell
mvn -pl leaf-base-datasource test -Dtest=GXSeataProxyContractTest
```

**期望**:
- 隐藏代理类的用例：返回**同一个**原始数据源实例、不抛异常；
- 该场景日志为 **DEBUG**，且**不出现** WARN/ERROR（未启用 Seata 属正常形态）；
- 断言 `GXDynamicDataSourceConfig` 实际使用的类名常量为新命名空间（该断言在旧代码上必须失败）。

### S3 代理校验与装配缺失（PROXY_MISSING / ASSEMBLY_ABSENT）

```powershell
mvn -pl leaf-base-seata test -Dtest='GXSeataProxyAssemblyTest,GXSeataDataSourceAbsenceTest'
```

**期望**:
- 全部目标源已代理：INFO 汇总行含 `proxied=[...]`；
- 存在未代理目标源且非严格模式：WARN 含数据源名称，启动继续；
- 严格模式：启动失败，消息含数据源名称与缺失契约类名；
- 无 `GXDynamicDataSource` bean：启动**成功**，出现 `[Seata] GXDynamicDataSource is absent` 告警，补齐 bean 后注入生效。

### S4 包装前后行为对照（BEHAVIOR_PARITY）

```powershell
mvn -pl leaf-base-datasource -am test -Dtest='GXSeataWrapperContractTest,GXSeataDataSourceParityTest,GXSeataDataSourceLifecycleTest,GXDataSourceAspectTest'
```

**期望**:
- 包装契约：目标源 `isInstance(SeataDataSourceProxy)` 为真、`getTargetDataSource()` 返回原始实例（**用桩代理，不构造真实代理**——真实 `DataSourceProxy` 构造会 `getConnection()` 并校验 `undo_log`）；
- 对照：包装 / 未包装两种形态下路由访问序列与上下文恢复结果一致；
- 拦截器链：分页/租户/防全表更新拦截器与自动填充仍在执行链路（存在性冒烟）；
- 生命周期：`destroy()` 关闭连接池且不重复关闭；
- 既有 12 个 `GXDataSourceAspectTest` 用例全部通过。

### S5 配置互斥（CONFIG_EXCLUSIVE）

```powershell
mvn -pl leaf-base-seata test -Dtest=GXSeataPropertiesSpringBootTest
```

**期望**（4 个独立用例，缺一即不满足 SC-005）:
1. 本地生效：隐藏 `com.alibaba.cloud.nacos.NacosConfigManager` 时只有本地入口装配；
2. Nacos 生效：存在该类型时只有 Nacos 入口装配，本地入口不装配；
3. profile 缺失：启动失败，消息含"profile 未设置/缺失"；
4. 资源缺失：启动失败，消息含当前 profile 与期望的 `classpath:/<profile>/seata.yml` 路径。

---

## 4. 启动期可观测性验证（可选，需可启动的示例应用）

1. 在引入 `leaf-base-seata` + `leaf-base-datasource` 的应用中，保持 `seata.enable-auto-data-source-proxy=false`；
2. 启动应用并检索日志：

```powershell
Select-String -Path .\logs\*.log -Pattern '\[Seata\]'
```

**期望**（按数据源数量不同，`[ ]` 内为实际名称）：

```text
[Seata] enabled=true, config-source=local, datasources=[ds1,ds2], proxied=[ds1,ds2]
```

异常态期望：

```text
[Seata] datasource 'ds2' is NOT wrapped by Seata; branches on it will not join global transactions
```

3. 追加 `-Dmaple.framework.enable.seata-strict=true` 重启，**期望**启动失败，异常消息列出未代理的数据源名称与 `org.apache.seata.rm.datasource.SeataDataSourceProxy`。

---

## 5. 失败排查

| 现象 | 优先检查 |
| --- | --- |
| 测试报 `class not found: org.apache.seata...` | 本地仓库 Seata 版本是否被降级；`seata.spring.boot.starter.version` 是否为 2.6.0 |
| 启动报资源缺失 | 当前 profile 与 `classpath:/<profile>/seata.yml` 是否匹配；外部 `config/<profile>/` 是否已加入类路径 |
| 日志没有 `[Seata]` 行 | 应用是否扫描了 `cn.maple` 包，或是否显式 `@Import` 了 seata 配置类 |
| 严格模式未生效 | 键名是否为 `maple.framework.enable.seata-strict`；是否被更高优先级配置源覆盖 |

---

## 6. 交付前检查

- [ ] `mvn -pl leaf-base-datasource -am test` 通过
- [ ] `mvn -pl leaf-base-seata -am test` 通过
- [ ] `review-report.md` 中每条 `BLOCKER` 状态为 `FIXED`，且复现索引可执行
- [ ] `docs/leaf-base-seata开发手册.md` 与 `contracts/seata-assembly-contracts.md` 描述一致
- [ ] 日志全部为 ASCII，无连接串/口令泄漏
