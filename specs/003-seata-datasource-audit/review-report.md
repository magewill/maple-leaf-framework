# Seata × 动态数据源协作审查报告

**Feature**: `003-seata-datasource-audit` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

## 1. 审查范围与方法

**范围**：

1. `leaf-base-seata` 全部 5 个生产文件（`config/GXSeataDynamicDataSourceConfig`、`properties/GXLocalSeataProperties`、`properties/GXNacosSeataProperties` 及 2 个 `package-info`）与 4 个测试类；
2. `leaf-base-datasource` 中全部 Seata 引用点：生产代码 1 处（`GXDynamicDataSourceConfig#wrapSeataDataSource`），测试引用 4 处（`GXDataSourceAspectTest`、`MultiDataSourceTestService`，以及 3 个 IT 的排除配置）；
3. 依赖侧事实：本地仓库 `org.apache.seata:seata-all:2.6.0`、`seata-spring-autoconfigure-client:2.6.0` 的类清单与源码；
4. 仓库内 `config/<profile>/seata.yml` 的实际配置。

**方法**：

1. 静态阅读 + `javap` 核对类签名（Spring JDBC 7.0.9 的 `AbstractRoutingDataSource`、Seata 的 `DataSourceProxy`）；
2. 用 `System.IO.Compression.ZipFile` 读取 `seata-all-2.6.0-sources.jar`，核对构造期是否建立连接；
3. 隔离上下文测试（`ApplicationContextRunner`、`FilteredClassLoader`）验证装配与降级路径；
4. 分别在两个模块执行 `mvn test`，作为回归基线。

**交付检查（quickstart 第 6 节）**：

- [x] `mvn -pl leaf-base-datasource -am test` 通过（199 用例，5 skipped）
- [x] `mvn -pl leaf-base-seata -am test` 通过（16 用例）
- [x] 报告中每条 `BLOCKER` 状态为 `FIXED`，复现索引可执行
- [x] `docs/leaf-base-seata开发手册.md` 与 `contracts/seata-assembly-contracts.md` 描述一致
- [x] 日志全部为 ASCII，无连接串/口令泄漏

## 2. 结论总览

| 编号 | 级别 | 标题 | 状态 |
| --- | --- | --- | --- |
| F-01 | BLOCKER | 代理未建立时静默降级 | FIXED |
| F-02 | BLOCKER | 装配判定与实例化使用不同命名空间 | FIXED |
| F-03 | RISK | 对 `GXDynamicDataSource` bean 的硬依赖 | FIXED |
| F-04 | RISK | 本地配置入口在 profile/资源缺失时脆弱 | ACCEPTED（行为保留，测试与文档固定） |
| F-05 | RISK | 双配置源判定口径与文档不一致 | ACCEPTED（文档已修订，装配条件不变） |
| F-06 | RISK | 手工代理策略依赖外部开关 | ACCEPTED（仅文档说明，不承诺运行时检测） |
| F-07 | RISK | 模块手册与实现漂移 | FIXED |

## 3. 证据

- **E-01**：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/config/GXDynamicDataSourceConfig.java:83-103`，`wrapSeataDataSource` 以反射实例化代理类，类名来自常量 `SEATA_DATASOURCE_PROXY_CLASS`（第 32 行）。
- **E-02**：`leaf-base-seata/src/main/java/cn/maple/seata/config/GXSeataDynamicDataSourceConfig.java`，装配条件 `name = SEATA_DATASOURCE_PROXY_INTERFACE`（常量值为 `org.apache.seata.rm.datasource.SeataDataSourceProxy`）。
- **E-03**：`seata-all-2.6.0.jar` 同时存在 `io/seata/rm/datasource/DataSourceProxy.class` 与 `org/apache/seata/rm/datasource/DataSourceProxy.class`。`javap` 显示前者 `@Deprecated`，实现 `org.apache.seata.rm.datasource.SeataDataSourceProxy` 接口并内部持有一个新命名空间代理实例逐方法委托。当前版本代理确实生效。
- **E-04**：`SeataProperties#enableAutoDataSourceProxy` 默认 `true`；`SeataAutoDataSourceProxyCreator.wrapIfNecessary` 第 73 行对 `AbstractRoutingDataSource` 直接返回原 bean，因此路由数据源不会被自动代理二次包装。
- **E-05**：`org/apache/seata/rm/datasource/DataSourceProxy.java:93-126`，构造器调用 `init()`，其中 `try (Connection connection = dataSource.getConnection())`、读取元数据 URL、`checkUndoLogTableExist(connection)`，随后 `DefaultResourceManager.get().registerResource(this)`。
- **E-06**：仓库 `config/dev|test|pre|prod/seata.yml` 均设置 `enable-auto-data-source-proxy: false`。
- **E-07**：`leaf-base-seata` 与 `leaf-base-datasource` 均未提供 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，装配依赖使用方包扫描或显式 `@Import`。
- **E-08**（审查时点）：`docs/leaf-base-seata开发手册.md` 描述的自定义 `MybatisSqlSessionFactoryBean`、`@AutoConfigureBefore`、`io.seata` 条件均已过时，已在本次修订。
- **E-09（真实环境验证）**：MySQL 8.4.11（`192.168.56.105:3306`，schema `test` / `test1`，两库均含 `undo_log`）与 Seata TC（`127.0.0.1:8091`）可用时，执行
  `mvn -o -pl leaf-base-datasource test -Dtest=MultiDataSourceIT -Dmaple.it.enabled=true ...` 得 5/5 通过；
  `-Dtest=SeataGlobalTransactionIT -Dmaple.it.seata.enabled=true` 得 2/2 通过。前者覆盖"双目标数据源均被 Seata 代理"，后者覆盖真实 AT 全局提交/回滚与 `undo_log` 清理，见 §7。

## 4. 发现

### F-01 代理未建立时静默降级

- **级别**: BLOCKER
- **位置**: `leaf-base-datasource/.../GXDynamicDataSourceConfig.java:83-103`；`leaf-base-seata/.../GXSeataDynamicDataSourceConfig.java`
- **触发条件**: 类路径存在 Seata 代理类型，但物理数据源未被包装（例如使用方自建 `GXDynamicDataSource`）；或装配判定与实例化类名不一致
- **影响**: 应用正常启动，`@GlobalTransactional` 下的分支不会注册到 TC，事务静默退化为本地事务
- **证据**: E-01、E-02、E-05；`GXSeataProxyContractTest#fallbackPathStaysAtDebugLevel`
- **复现**: `mvn -pl leaf-base-datasource test -Dtest=GXSeataProxyContractTest`；`mvn -pl leaf-base-seata test -Dtest=GXSeataProxyAssemblyTest`
- **建议**: 未启用 Seata（类路径无代理类型）保持 DEBUG；"已装配但未代理"由 Seata 侧 WARN，严格开关下启动失败
- **状态**: FIXED（`GXSeataDynamicDataSourceConfig#verifyDataSourceProxies` + `maple.framework.enable.seata-strict`）

### F-02 装配判定与实例化使用不同命名空间

- **级别**: BLOCKER
- **位置**: 同上两处
- **触发条件**: 上游移除/裁剪 `io.seata` 兼容包，或装配侧改用新命名空间而实例化侧未同步
- **影响**: 装配条件成立但代理类找不到，回到 F-01 的静默降级
- **证据**: E-01、E-02、E-03
- **复现**: `mvn -pl leaf-base-datasource test -Dtest=GXSeataProxyContractTest`（断言常量等于新命名空间）
- **建议**: 装配判定与实例化统一到 `org.apache.seata.*`，不保留回退分支
- **状态**: FIXED（`SEATA_DATASOURCE_PROXY_CLASS` / `SEATA_DATASOURCE_PROXY_INTERFACE`）

### F-03 对 `GXDynamicDataSource` bean 的硬依赖

- **级别**: RISK
- **位置**: `GXSeataDynamicDataSourceConfig#seataDynamicDataSourceCustomizer`（改造前）
- **触发条件**: 使用方引入 `leaf-base-seata` 但未装配动态数据源 bean
- **影响**: `NoSuchBeanDefinitionException`，应用启动失败
- **证据**: `GXSeataDataSourceAbsenceTest`
- **复现**: `mvn -pl leaf-base-seata test -Dtest=GXSeataDataSourceAbsenceTest`
- **建议**: 改为 `ObjectProvider` 惰性解析，缺失时 WARN 并跳过
- **状态**: FIXED

### F-04 本地配置入口在 profile/资源缺失时脆弱

- **级别**: RISK
- **位置**: `GXLocalSeataProperties`
- **触发条件**: `spring.profiles.active` 未设置；或 `classpath:/<profile>/seata.yml` 不存在（含把文件放在仓库 `config/<profile>/` 且未入类路径）
- **影响**: 启动失败，错误信息可能只给出通用资源异常
- **证据**: E-06；`GXSeataPropertiesSpringBootTest#localSeataPropertiesFailsWhenProfileIsMissing/…ResourceIsMissing`
- **复现**: `mvn -pl leaf-base-seata test -Dtest=GXSeataPropertiesSpringBootTest`
- **建议**: 保留"缺失即失败"的显式语义（澄清 4），补测试断言与文档路径约定
- **状态**: ACCEPTED（行为未改，已由测试与手册固定）

### F-05 双配置源判定口径与文档不一致

- **级别**: RISK
- **位置**: `GXLocalSeataProperties`（`NacosConfigManager` 缺席）、`GXNacosSeataProperties`（`NacosConfigManager` + `NacosConfigurationProperties`）
- **触发条件**: 仅引入 Nacos 客户端但未接入 Spring Cloud Alibaba 配置中心
- **影响**: 文档若按"存在 Nacos 客户端即走 Nacos"理解，会误判配置来源
- **证据**: `GXSeataPropertiesSpringBootTest` 的互斥用例
- **复现**: `mvn -pl leaf-base-seata test -Dtest=GXSeataPropertiesSpringBootTest`
- **建议**: 保持装配条件不变（兼容），修订文档说明两个条件类
- **状态**: ACCEPTED（手册已修订）

### F-06 手工代理策略依赖外部开关

- **级别**: RISK
- **位置**: `config/<profile>/seata.yml` 的 `enable-auto-data-source-proxy: false`
- **触发条件**: 使用方漏配该键，且应用中存在其它 `DataSource` bean
- **影响**: 其它数据源被 Seata 自动代理，形成"手工 + 自动"两套策略并存
- **证据**: E-04、E-06
- **复现**: 阅读 `SeataAutoDataSourceProxyCreator#wrapIfNecessary`（第 73 行跳过路由数据源）
- **建议**: 仅文档说明；不实现运行时检测（澄清 6）
- **状态**: ACCEPTED（手册已说明）

### F-07 模块手册与实现漂移

- **级别**: RISK
- **位置**: `docs/leaf-base-seata开发手册.md` 第 3 章
- **触发条件**: 阅读文档排障
- **影响**: 误导维护者按旧机制（自定义 `SqlSessionFactoryBean`、`@AutoConfigureBefore`）排查
- **证据**: E-08
- **复现**: 对照手册第 3 章与 `GXSeataDynamicDataSourceConfig`
- **建议**: 按现实现修订
- **状态**: FIXED（手册第 2.3/3.1/3.2/3.3 节已重写）

## 5. 已确认无缺陷项

| 结论 | 守护测试 | 断言要点 |
| --- | --- | --- |
| 路由键栈语义与上下文恢复未被 Seata 改变 | `GXSeataDataSourceParityTest` | 包装/未包装两形态的访问序列一致 |
| 跨数据源与 `REQUIRES_NEW` 下 Seata XID 与全局锁标记保留 | `GXDataSourceAspectTest`（15 用例） | 每次记录到的 XID 与锁标记一致 |
| 路由数据源不会被二次代理 | `GXSeataWrapperContractTest` | 路由源 `isNotInstanceOf(SeataDataSourceProxy)`，目标源 `isInstanceOf` |
| 连接池关闭责任未变 | `GXSeataDataSourceLifecycleTest` | `destroy()` 后每个池关闭 1 次，重复调用不再关闭 |
| MyBatis-Plus 拦截器链与自动填充仍在链路 | `GXSeataDataSourceParityTest#mybatisPlusInterceptorChainStillContainsPaginationTenantAndBlockAttack` | 分页/防全表更新/数据过滤拦截器存在 |

## 6. 复现索引

| Finding | 命令 | 预期 |
| --- | --- | --- |
| F-01 | `mvn -pl leaf-base-seata test -Dtest=GXSeataProxyAssemblyTest` | 三态用例通过；严格模式启动失败 |
| F-01 | `mvn -pl leaf-base-datasource test -Dtest=GXSeataProxyContractTest` | 降级返回同一实例，日志级别断言为 debug |
| F-02 | `mvn -pl leaf-base-datasource test -Dtest=GXSeataProxyContractTest` | 常量等于 `org.apache.seata.rm.datasource.DataSourceProxy` |
| F-03 | `mvn -pl leaf-base-seata test -Dtest=GXSeataDataSourceAbsenceTest` | 无数据源 bean 时启动成功且不注入 |
| F-04 | `mvn -pl leaf-base-seata test -Dtest=GXSeataPropertiesSpringBootTest` | profile/资源缺失时失败信息含 profile 或路径 |
| F-05 | 同上 | 本地/Nacos 两个入口互斥 |
| F-06 | `mvn -o -q -pl leaf-base-seata dependency:build-classpath -Dmdep.outputFile=seata-cp.txt`，随后 `& "$env:JAVA_HOME\bin\javap.exe" -classpath (Get-Content seata-cp.txt) org.apache.seata.spring.annotation.datasource.SeataAutoDataSourceProxyCreator` | 类存在；源码第 73 行跳过 `AbstractRoutingDataSource` |
| F-07 | `Select-String -Path docs/leaf-base-seata开发手册.md -Pattern 'SeataDataSourceProxy|seata-strict'` | 能检索到新命名空间契约与严格开关说明 |

### 6.1 真实环境复现命令（MySQL `test` / `test1` + Seata TC）

前提：MySQL 可达（本次为 `192.168.56.105:3306`，账号对 `test` / `test1` 有权限且两库均有 `undo_log`），Seata TC 监听 `127.0.0.1:8091`。凭据通过参数传入，仓库内不落盘。

```powershell
cd <repo-root>

# 动态数据源行为（不需要 TC）
mvn -o -pl leaf-base-datasource test "-Dtest=MultiDataSourceIT" "-DfailIfNoTests=false" "-Dmaple.it.enabled=true" `
  "-Ddynamic.datasource.framework.url=jdbc:mysql://192.168.56.105:3306/test" `
  "-Ddynamic.datasource.framework.username=<user>" "-Ddynamic.datasource.framework.password=<password>" `
  "-Ddynamic.datasource.framework.connection-properties=useSSL=false;allowPublicKeyRetrieval=true" `
  "-Ddynamic.datasource.slave1.url=jdbc:mysql://192.168.56.105:3306/test1" `
  "-Ddynamic.datasource.slave1.username=<user>" "-Ddynamic.datasource.slave1.password=<password>" `
  "-Ddynamic.datasource.slave1.connection-properties=useSSL=false;allowPublicKeyRetrieval=true"

# Seata AT 端到端（需要 TC，参数同上）
mvn -o -pl leaf-base-datasource test "-Dtest=SeataGlobalTransactionIT" "-DfailIfNoTests=false" `
  "-Dmaple.it.enabled=true" "-Dmaple.it.seata.enabled=true" `
  "-Ddynamic.datasource.framework.url=jdbc:mysql://192.168.56.105:3306/test" `
  "-Ddynamic.datasource.framework.username=<user>" "-Ddynamic.datasource.framework.password=<password>" `
  "-Ddynamic.datasource.framework.connection-properties=useSSL=false;allowPublicKeyRetrieval=true" `
  "-Ddynamic.datasource.slave1.url=jdbc:mysql://192.168.56.105:3306/test1" `
  "-Ddynamic.datasource.slave1.username=<user>" "-Ddynamic.datasource.slave1.password=<password>" `
  "-Ddynamic.datasource.slave1.connection-properties=useSSL=false;allowPublicKeyRetrieval=true"
```

期望：前者 `Tests run: 5, Failures: 0`；后者 `Tests run: 2, Failures: 0`；执行后两库 `undo_log` 计数均为 0。

> 说明：`dev/datasource-multi.yml` 使用 `${IT_DB_*}` 占位符，而 PowerShell 下含 `&` 的 JDBC URL 会被拆分；示例改用 `-Ddynamic.datasource.*` 直接覆盖（优先级高于 `@PropertySource`），或改用环境变量设置 `IT_DB_*`。

## 7. 环境与版本事实

| 项 | 值 |
| --- | --- |
| 构建 JDK | Temurin 25.0.4（语言级别由根 POM `maven.compiler.release=21` 固定） |
| Maven | 3.9.11，本地仓库 `F:\repository` |
| Spring Boot / Spring Framework | 4.1.1 / 7.0.9 |
| MyBatis-Plus / Druid | 3.5.17 / 1.2.28 |
| Seata 客户端 | `org.apache.seata:seata-spring-boot-starter:2.6.0` |
| 回归结果 | `leaf-base-datasource`：199 用例通过（5 skipped）；`leaf-base-seata`：16 用例通过 |
| 真实环境集成结果 | `MultiDataSourceIT` 5/5 通过（真实 MySQL `test`/`test1`，含"双目标源均被 Seata 代理"）；`SeataGlobalTransactionIT` 2/2 通过（真实 TC，跨库全局提交/回滚，`undo_log` 两端均清理为 0） |
