# Quickstart — 如何验证本特性已就位

**Feature**: `009-eureka-client-module-audit` | **Date**: 2026-10-22
**契约**：[assembly-contract.md](./contracts/assembly-contract.md)、[configuration-contract.md](./contracts/configuration-contract.md)、[dependency-contract.md](./contracts/dependency-contract.md)

所有命令在仓库根 `e:\project\java\maple-leaf-framework` 执行，PowerShell，`-o` 离线可用（本机已缓存依赖）。

## 0. 前置条件

- JDK 21、Maven 3.9+；本地仓库已有 `4.3.0-SNAPSHOT` 的 `leaf-base-*` 制品（`mvn -o -pl leaf-base-eureka-client -am install -DskipTests` 可自给）。
- 无需网络、无需运行中的注册中心。

## 1. 复现修复前的红基线（实施开始前）

```powershell
mvn -o -pl leaf-base-eureka-client test
```

**期望（当前状态，2026-10-22）**：`Tests run: 4, Failures: 1` → `GXEurekaClientConfigTest.applicationYamlContainsExpectedClientDefaults:82`，`BUILD FAILURE`。
**判定**：若此步为绿，说明基线已变，必须先在 `audit-report.md` §1 更新基线再继续（不得直接进入修复）。

## 2. 修复后必须全绿

```powershell
mvn -o -pl leaf-base-eureka-client test
```

**期望**：`Tests run: ≥23, Failures: 0, Errors: 0`，`BUILD SUCCESS`（收敛完成后为 23）。
**判定**：以下三条必须同时成立，否则视为未修复：①失败用例被**改写**而非删除（`git diff` 中可见）；②新增用例数 ≥ 3；③无 `@Disabled`、无 `@Test` 被注释。

## 3. 开关关闭：无 Bean、无注册、无残留值

验证点（见 `GXEurekaClientSwitchTest`）：

| 检查项 | 期望 |
| --- | --- |
| 上下文加载（`@SpringBootTest(webEnvironment=NONE)`，`maple.framework.enable.eureka-client=false`） | 成功 |
| `context.getBeansOfType(EurekaClient.class)` | 空 |
| `context.containsBean("eurekaAutoServiceRegistration")` | `false` |
| `Environment#getProperty("eureka.client.enabled")` | `false` |
| 日志 | 含且仅含 1 条 `Eureka client disabled via maple.framework.enable.eureka-client=false` |

```powershell
mvn -o -pl leaf-base-eureka-client test "-Dtest=GXEurekaClientSwitchTest"
```

## 4. 开关开启：注册请求确实发往配置的地址

验证点（见 `GXEurekaClientRegistrationTest`）：

1. 测试内启动 JDK `com.sun.net.httpserver.HttpServer`（随机端口），记录收到的请求方法与路径。
2. `@SpringBootTest(properties = {"spring.application.name=it-app", "eureka.client.service-url.defaultZone=http://127.0.0.1:<port>/eureka/", "eureka.client.register-with-eureka=true"})`。
3. 期望：服务端收到 `POST /eureka/apps/IT-APP`（大小写按应用名规范化），且 `Environment` 中的 `defaultZone` 与设置值一致；上下文启动成功。

```powershell
mvn -o -pl leaf-base-eureka-client test "-Dtest=GXEurekaClientRegistrationTest"
```

**判定**：只断言"配置值等于设置值"不够，必须有一次真实 HTTP 请求落地（spec FR-011）。

## 5. jar 内不再有 `classpath:/application.yml`

```powershell
mvn -o -pl leaf-base-eureka-client process-resources
Get-ChildItem leaf-base-eureka-client\target\classes -Filter application.yml -Recurse
Get-ChildItem leaf-base-eureka-client\target\classes\examples
```

**期望**：第一条命令**无输出**；`examples\` 下有 `eureka-client-default.yml` 与 `eureka-client-multi-registry.yml`。
**同时校验**：`Test` 侧断言 `getClass().getClassLoader().getResourceAsStream("application.yml")` 为 `null`。

## 6. 依赖树逐行比对

```powershell
mvn -o -pl leaf-base-eureka-client dependency:tree "-DoutputFile=target/deptree-after.txt"
mvn -o -pl leaf-base-eureka-client dependency:tree "-Dverbose=true" "-DoutputFile=target/deptree-verbose-after.txt"
git diff --no-index target/deptree-before.txt target/deptree-after.txt
```

**期望**：除"被删除的重复重声明那一行"外无差异；`httpclient 4.5.14` 与 `httpclient5 5.6.4` 仍在；`eureka-client 2.0.6` 未变版本。
**判定**：出现坐标版本变化即按 [dependency-contract.md](./contracts/dependency-contract.md) §4 回退。

## 7. 构建门禁（ErrorProne + NullAway）

```powershell
mvn -o -pl leaf-base-eureka-client clean install -DskipTests
```

**期望**：`BUILD SUCCESS`，无 `[ERROR] ... NullAway` / `error-prone` 诊断。
**判定**：不得通过移除 `maven-compiler-plugin` 的编译参数或注解处理器来"通过"（宪法第 VI 条）。

## 8. 双平台一致性

- Windows 工作区（CRLF）：上述命令结果为准。
- Linux / LF 归一化：`git config core.autocrlf false` 后重新检出，或把模块内相关文件转为 LF 后重跑 §2 ~ §6。
- **期望**：两侧用例数与通过数完全相同；不存在依赖换行符的断言（spec FR-010）。

## 9. 跨模块回归（不得破坏已交付的 008）

```powershell
mvn -o -pl leaf-base-eureka-server test
```

**期望**：008 的用例保持全绿（本特性不应改动 `leaf-base-eureka-server`）。
**同时检查**：`git status --porcelain` 不含 `leaf-framework/`、`leaf-base-feign/`、`leaf-base-webclient/`、根 `pom.xml`。

## 10. 交付物自检

| 检查 | 命令 / 位置 |
| --- | --- |
| 审查报告存在且含 E-01~E-27、F-01~F-13 | `specs/009-eureka-client-module-audit/audit-report.md` |
| 模块手册存在 | `docs/leaf-base-eureka-client开发手册.md` |
| 元数据声明开关且默认 `true` | `leaf-base-eureka-client/src/main/resources/META-INF/additional-spring-configuration-metadata.json` |
| `AGENTS.md` 章节连续（§1~§7）且无过时表述 | `leaf-base-eureka-client/AGENTS.md` |
| `README.md` 行含"引入即注册 / 可关闭 / 无默认地址" | `README.md` 模块表 |
