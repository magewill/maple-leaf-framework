# 依赖契约：leaf-base-eureka-client

**Feature**: `009-eureka-client-module-audit` | **Date**: 2026-10-22

本契约约束本模块可以声明什么、版本从哪来、排除与重声明怎么写、变更如何验证。

## 1. 允许的依赖集合（修复后）

| 坐标 | 版本来源 | 作用域 | 是否可省略 | 备注 |
| --- | --- | --- | --- | --- |
| `cn.maple.framework:leaf-base-feign` | `${project.parent.version}` | compile | 否 | 提供 Feign 扫描前置能力（本模块不开启扫描） |
| `cn.maple.framework:leaf-base-webclient` | `${project.parent.version}` | compile | 否 | 提供 `@LoadBalanced WebClient.Builder` 与 `HttpServiceProxyFactory` |
| `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client` | **BOM**（`spring-cloud-dependencies 2025.1.3` → `5.0.2`） | compile | 否 | **禁止**在模块内写死版本 |
| `org.antlr:antlr-runtime` | 根 POM `antlr-runtime.version=3.5.3` | compile | 保留 | 冲突锚定，见 §3 |
| `com.google.errorprone:error_prone_annotations` | 根 POM `error-prone.version=2.50.0` | compile | 视 verbose 树而定 | 见 §3.2 |
| `org.apache.httpcomponents:httpclient` | 根 POM `httpclient.version=4.5.14` | compile | 保留 | `eureka-client 2.0.6` 声明 4.5.3，需锚定 |
| `org.springframework.boot:spring-boot-starter-test` | BOM | test | 否 | 不新增其它测试依赖 |

**硬性约束**：不得新增第三方依赖；不得引入 MockWebServer / WireMock / Testcontainers（spec Clarifications）。

## 2. 版本与冲突治理规则

1. 模块内不得出现本模块自行决定的版本号（当前遵守，保持）。
2. 冲突覆盖只能声明在根 `pom.xml` `<properties>` + `<dependencyManagement>`，且必须带中文注释说明"解决哪两个组件的冲突"；**不得删除**既有覆盖（宪法附加约束 2）。
3. 模块内的 `<exclusion>` 与随后的同坐标重声明属于"版本锚定"手段，必须成对出现并注释意图；单独出现（只排除不重声明、或只重声明）视为契约违反。
4. `httpclient`（4.x）与 `httpclient5`（5.x）并存是既定事实：前者由 `eureka-client 2.0.6` 需要，后者由 `spring-cloud-netflix-eureka-client 5.0.2` 使用。禁止为了"看起来干净"而移除 `httpclient`。

## 3. 三组 exclusion/重声明的判定与动作

| 坐标 | 解析结果 | 净效果 | 判定 | 动作 |
| --- | --- | --- | --- | --- |
| `org.antlr:antlr-runtime` | `3.5.3`（仅顶层出现） | 排除后重新引入 = 版本锚定 | 冗余但必要 | 保留 + 中文注释（seata/eureka 冲突） |
| `com.google.errorprone:error_prone_annotations` | `2.50.0`（`-Dverbose` 显示 guava 路径亦为 2.50.0，标记 `omitted for duplicate`） | 排除后仍由 guava 引入，重声明为重复 | 冗余 | **仅当**删除重声明前后 `dependency:tree -Dverbose` 完全一致时删除；否则保留 + 注释 |
| `org.apache.httpcomponents:httpclient` | `4.5.14`（`eureka-client` 声明 4.5.3） | 排除 + 重声明 = 版本抬升 | 必要 | 保留 + 注释"4.5.14 由根 POM 统一；与 httpclient5 分工" |

## 4. 依赖变更的验证流程（每次改动依赖必须执行）

```powershell
mvn -o -pl leaf-base-eureka-client dependency:tree "-DoutputFile=target/deptree-after.txt"
mvn -o -pl leaf-base-eureka-client dependency:tree "-Dverbose=true" "-DoutputFile=target/deptree-verbose-after.txt"
```

判定：

1. 与改动前文件逐行 diff，**只允许出现被声明修改那一行**的差异；出现其它坐标的版本/作用域变化即为不合格。
2. 新增坐标必须能回答"用途 / 是否与现有依赖重叠 / 是否可用既有封装替代"（宪法附加约束 3）。
3. 变更后 `mvn -o -pl leaf-base-eureka-client test` 全绿。

## 5. 禁止项清单

| 禁止 | 原因 |
| --- | --- |
| 在模块内钉 `spring-cloud-starter-netflix-eureka-client` 版本 | BOM 升级会被钉死（008 已出现过同类问题） |
| 声明 `spring-boot-devtools` | 库模块不得传递开发期工具（008 F-04） |
| 声明 `spring-boot-starter-web` / `tomcat` | 本模块不提供 Web 容器能力 |
| 声明任何注册中心服务端依赖（jersey/hk2 等） | 越界到 `leaf-base-eureka-server` |
| 通过 `<scope>provided</scope>` 规避可传递性检查 | 会隐藏真实运行期依赖 |
| 新增测试依赖 | spec Clarifications；JDK `HttpServer` 已足够 |
