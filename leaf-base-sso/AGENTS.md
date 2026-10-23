# AGENTS.md - leaf-base-sso

本文用于指导 `leaf-base-sso` 模块维护。修改本模块时，优先保持 SSO 行为兼容、接口语义稳定、安全策略一致。

## 模块职责

`leaf-base-sso` 负责：

1. SSO Token 的生成、解析、缓存同步和校验。
2. 登录态管理，包括 Cookie 写入、清理、安全属性、重定向登录和登出。
3. 登录拦截、权限拦截、URL 白名单、注解跳过和权限注解校验。
4. SSO 扩展点，例如 `GXSSOPlugin`、`GXSSOAuthorization` 和拦截器扩展服务。
5. 验证码生成、存储和校验。
6. 本地配置与 Nacos 配置读取。

本模块不承载具体业务域规则、业务权限模型或业务流程编排。

## 实现原则

1. 不要随意复杂化任何逻辑实现。优先沿用现有拦截器、服务、配置和扩展点；不要为局部需求引入新的认证框架、状态同步机制或多套并行策略。
2. 遵循单一职责原则。Token、Cookie、拦截、权限、验证码和配置加载应保持职责清晰；禁止一个类型同时包揽多类安全流程。
3. 安全默认值必须保守。扩展点默认实现不能静默放行、静默返回空业务数据或绕过服务端状态校验。
4. 不要随意改变公开接口、配置契约、扩展点契约、异常语义、Cookie 行为、Token 语义和默认拦截策略。
5. 修改既有逻辑前，先确认旧行为的成功路径、失败路径、空值、边界输入和安全语义。修复缺陷时，必须补回归测试。
6. 日志、异常消息和断言消息优先使用 ASCII 文本，避免运行环境、终端编码或日志采集链路出现乱码。

## 关键行为

1. Token 校验、Cookie 安全属性、登录拦截、权限校验和白名单策略属于安全关键路径，变更前必须评估兼容性和风险。
2. 新增配置项必须有明确默认值、失效语义和兼容策略，避免隐式行为变化。
3. 登录拦截链和权限拦截链的顺序不得随意改变；顺序由 `@Order` + `GXSSOConstant` 显式声明，不得依赖 bean 名称字典序。
4. AJAX 请求、普通请求、未登录、无权限、验证码失败等分支必须保持明确语义。
5. 插件扩展点不能吞掉关键失败，也不能绕过核心校验；`plugin.login=false` 必须使登录失败并回滚缓存。

## 安全红线（不得回退）

1. **默认 fail-closed**：没有 `GXSSOCache` 实现时必须拒绝登录、拒绝校验；只有显式 `sso.config.cache-fallback-enabled=true` 才允许降级为本地 Token（TTL ≤ 900 秒 + ERROR 日志）。禁止把"无缓存也能登录"重新变成默认行为。
2. **密钥无源码默认值**：Token 密钥不得使用框架公开常量作为生产默认；`prod` profile 下 `GXAbstractSSOService#validateConfiguration` 必须拒绝启动。`signKey`/`rsaKeypass`/`rsaStorepass` 等字段不得写入真实口令。
3. **拒绝路径零副作用**：未登录或被拒绝的请求（含**可解析但校验失败**的 Token）不得删除缓存键、不得调用 `plugin.logout`、不得写 INFO 日志；`clearRedirectLogin` 只清 Cookie，完整登出语义只属于显式 `clearLogin`。
4. **权限单一判定链**：`@GXIgnoreLoginIntercept` → `@GXPermissionAnnotation(Skip)` → URI 模式（终局）→ 注解模式 → `nothingAnnotationPass`；任何"判定失败后再被默认值放行"的实现都视为缺陷。
5. **Token 可达过期**：`sso.config.cache-expires` 默认必须为正数；`-1`（永不过期）只能作为显式配置并打 ERROR。
6. **配置源必须显式**：local / nacos 两套配置源由 `sso.config.source`（默认 `matchIfMissing=true` → nacos）互斥选择，不得回退为"依赖传递类路径隐式判断"（本模块以 compile 依赖引入 `leaf-base-nacos`，`@ConditionalOnMissingClass` 恒为假）。该机制为宪法 v1.1.0 §IV 认可的等价方式，改动时必须同时满足四条护栏：默认值保持既有行为、启动 INFO 打印生效源、`GXSSOConfigSourceTest` 覆盖互斥与默认值、新键登记 `additional-spring-configuration-metadata.json` 与手册配置表。

## 测试要求

1. 需要完整的单元测试。涉及行为变更时，测试必须覆盖成功、失败、异常、空值、边界和回归场景。
2. 涉及线程并发、缓存同步、Token 解析性能、验证码生成、拦截器链、安全关键分支等功能点时，必须补充冒烟测试或集成测试。
3. 涉及性能关键节点时，至少要有可执行的冒烟验证，覆盖重复执行、批量输入、空输入和异常输入；无法自动化时，在交付说明中写清手工验证命令、样本规模和观察指标。
4. 依赖 Spring 容器、自动配置、Web 请求上下文、拦截器链或 Bean 装配的功能，必须使用接近真实运行形态的 Spring Boot 测试。
5. 重点覆盖 Token 获取与校验、登录态写入与清理、白名单策略、权限拦截、验证码生成/存储/校验、配置来源切换。
6. 修改本模块任一安全红线相关逻辑时，必须同时更新或新增以下用例：`GXSSOCacheContractTest`（缓存失败语义）、`GXSSOServiceBehaviorTest`（登录/登出/降级/插件拒绝）、`GXSSOInterceptorBehaviorTest`（权限矩阵 16 组合）、`GXSSOAuthFlowSpringBootTest`（真实拦截链）、`GXSSOConcurrencyTest`（并发与共享状态）、`CaptchaRenderTest`（真实渲染与边界）、`GXSSOConfigSourceTest`（配置源与默认值）。
7. 测试改写全局静态配置（`GXSSOHelperUtil#setSsoConfig`）时必须 `@AfterEach` 复原并标注 `@ResourceLock("sso-global-config")`；禁止依赖反射构造失败现场（使用 `GXSSOTestDoubles`）。

## 变更检查

提交或交付前确认：

1. 是否只修改了 `leaf-base-sso` 模块；确需跨模块时，先说明原因和最小影响范围。
2. 是否影响 `GXSSOService`、`GXAbstractSSOService`、`GXSSOSupportService` 的对外语义。
3. 是否影响登录拦截链、权限拦截链、Cookie 属性、Token 校验或白名单规则。
4. 是否引入了不必要的复杂分支、抽象层、缓存、反射或跨职责类型。
5. 是否补充并运行了与风险相匹配的单元测试、冒烟测试或集成测试。

## 推荐命令

```powershell
mvn -pl leaf-base-sso -am test
```
