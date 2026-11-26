# 契约：依赖与传递性

**模块**: `leaf-base-eureka-server` | **版本**: 4.3.0 | **基线**: `spring-boot 4.1.1` / `spring-cloud 2025.1.3`（`spring-cloud-netflix 5.0.2`）

## 1. 当前解析结果（审查基线，2026-10-22 实测）

| 坐标 | 解析版本 | Scope | 随模块传递下游 | 判定 |
| --- | --- | --- | --- | --- |
| `org.springframework.cloud:spring-cloud-starter-netflix-eureka-server` | 5.0.2 | compile | 是 | 冗余版本声明（与 BOM 同值） |
| `org.springframework.boot:spring-boot-devtools` | 4.1.1 | **compile** | **是** | **缺陷**：开发期工具外泄 |
| `org.glassfish.jersey.connectors:jersey-apache-connector` | 3.1.12 | compile | 是 | **上游硬约束**（非缺陷）：`eureka-core-jersey3` 复制客户端硬编码 `ApacheConnectorProvider`；该坐标**无 4.x 版本** |
| `org.glassfish.jersey.core:jersey-server` / `jersey-container-servlet` / `jersey-client` | 4.0.2 | compile | 是 | 无风险（容器侧走 Jersey 4，与复制客户端侧并存） |
| `org.glassfish.jersey.inject:jersey-hk2` | 4.0.3 | compile | 是 | 无风险 |
| `org.apache.httpcomponents:httpclient` | 4.5.14 | compile | 是 | **上游硬约束**：Jersey 3 连接器的必需实现；仅重复声明属冗余 |
| `org.apache.httpcomponents.client5:httpclient5` | 5.6.4 | compile | 是 | 无风险 |
| `org.glassfish.hk2:hk2-api` / `hk2-utils` | 4.0.3 | compile | 是 | **冗余**：排除后同坐标重引入 |
| `com.fasterxml.woodstox:woodstox-core` | 7.3.0 | compile | 是 | **冗余**：同上 |
| `org.springframework.boot:spring-boot-starter-web` / `starter-tomcat`（`tomcat-embed-core 11.0.24`）/ `starter-actuator` | 4.1.1 / 11.0.24 | compile | 是 | 无风险（注册中心的必要组成），但需在手册说明体积影响 |

## 2. 目标状态（修复后，已实测）

| 坐标 | 期望 | 实测结果 |
| --- | --- | --- |
| `spring-boot-devtools` | **不存在**（任何 scope） | ✅ 已消失 |
| `jersey-apache-connector` | **保留 3.1.12** | ✅ 保留，版本不变 |
| `jersey-apache5-connector` | **不引入** | ✅ 未引入（替换方案实测失败） |
| `httpclient`（4.x） | **保留**（传递引入），模块内不再直接声明 | ✅ 仍为 4.5.14，来源变为传递 |
| `hk2-api` / `hk2-utils` / `woodstox-core` | 版本仍为 4.0.3 / 4.0.3 / 7.3.0，模块内不再出现 `<exclusion>` + 同坐标重声明 | ✅ 版本不变，结构简化 |
| `spring-cloud-starter-netflix-eureka-server` | 5.0.2（由 BOM 管理，模块内无 `<version>`） | ✅ 仍为 5.0.2 |
| 其余坐标 | 解析版本与基线逐坐标一致 | ✅ 一致（含 `tomcat-embed-core 11.0.24`、`httpclient5 5.6.4`、`jersey-hk2 4.0.3`） |

## 3. 验收断言

1. `mvn -pl leaf-base-eureka-server dependency:tree` 中不存在 `spring-boot-devtools`。
2. 依赖树中 `jersey-apache-connector:3.1.12`、`jersey-server:4.0.2`、`httpclient:4.5.14` 三个坐标**必须存在且版本不变**；不存在 `jersey-apache5-connector`。
3. `.tree-before.txt` 与 `.tree-after.txt` 逐坐标比对，唯一允许的差异是 `spring-boot-devtools:4.1.1` 行消失；`hk2-api`/`hk2-utils`/`woodstox-core`/`httpclient` 允许由直接声明变为传递引入（缩进层级变化），但版本与 scope 必须一致。
4. `mvn -pl leaf-framework dependency:tree` 中不含 `leaf-base-eureka-server`（已实测）。
5. 根 POM 的 `httpclient.version`、`hk2.version`、`woodstox-core.version`、`jersey-apache-connector.version` 覆盖条目**全部保留**，未新增 `jersey-apache5-connector` 条目。

## 4. 禁止项

- 为消除告警而删除根 POM 中已登记的冲突覆盖（宪法附加约束 2）。
- 在模块内新增任何第三方依赖（含测试期；宪法附加约束 3）。
- 通过 `<optional>true</optional>` 掩盖 devtools 问题（本模块无使用场景，应直接移除）。
- 把根属性 `jersey-apache-connector.version` 改为 4.x——该坐标无 4.x 版本，会直接导致解析失败，并连带破坏共用该属性的 `leaf-base-manticore`。
- 移除或替换 `jersey-apache-connector`（Jersey 3）——实测导致对等复制初始化 `NoClassDefFoundError: ApacheConnectorProvider`。
- 直接升级 Jersey/Spring Cloud 主版本（未经本计划验证）。

## 5. 变更失败时的处置

若未来出现"对等复制无法初始化"或 `NoClassDefFoundError: org/glassfish/jersey/apache/connector/ApacheConnectorProvider`：

1. 确认 `jersey-apache-connector` 仍在依赖树中且版本为 `3.1.12`；
2. 确认 `httpclient` 4.5.x 仍在依赖树中；
3. 若因升级 Spring Cloud 导致版本漂移，按根 POM 的 `jersey-apache-connector.version` 属性重新锁定，并在 PR 说明中记录升级前后对等复制实测结果。
