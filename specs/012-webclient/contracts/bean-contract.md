# Contract: Bean 装配契约（冻结 + 增量）

**Module**: `leaf-base-webclient` | **Auto-config**: `cn.maple.webclient.config.GXWebClientConfig`（`META-INF/spring/...AutoConfiguration.imports` 注册，`@ConditionalOnClass(WebClient.class)`）
**Consumers**: `leaf-base-eureka-client`（`@AutoConfiguration(after = {GXFeignConfig.class, GXWebClientConfig.class, ...})` + 测试直接 import）、下游业务应用。

## 冻结项（宪法 II，不得变更）

| Bean | 类型 | 作用域 | 依赖注入约定 |
| --- | --- | --- | --- |
| `mapleWebClientConnectionProvider` | `reactor.netty.resources.ConnectionProvider` | singleton | `@ConditionalOnMissingBean(name=...)` 可按名覆盖 |
| `mapleWebClientConnector` | `ReactorClientHttpConnector` | singleton | `@Qualifier("mapleWebClientConnector")` 注入；`@ConditionalOnMissingBean(name=...)` |
| `webClientBuilder` | `WebClient.Builder` | **prototype**（可变 builder 不共享） | `@LoadBalanced`（服务名寻址语义，见 filter-chain-contract.md） |
| `webClient` | `WebClient` | singleton | 由 `webClientBuilder` 构建；下游可 `@Autowired WebClient` |
| `httpServiceProxyFactoryBuilder` | `HttpServiceProxyFactory.Builder` | singleton | 注入 singleton `webClient` |
| `httpServiceProxyFactory` | `HttpServiceProxyFactory` | singleton | 由 builder `build()` |

- Bean **名称、类型、作用域**冻结；`@AutoConfiguration` 类名冻结（eureka-client 排序依赖）。
- 配置键、默认值见 [config-contract.md](./config-contract.md)。
- 实现说明：`webClientBuilder` Bean 由 `@Bean(name = "webClientBuilder")` 的 `loadBalancedWebClientBuilder(...)` 提供（`@LoadBalanced` + `@Primary` + prototype），原 4 参方法 `webClientBuilder(ObjectProvider, ObjectProvider, ReactorClientHttpConnector, Environment)` 保留为**非 Bean 兼容重载**（`lbMode=false`），保证既有测试与下游直接调用零改动；`@Primary` 用于在新增 `webClientDirectBuilder` 后保持按类型注入仍解析到原 LB builder。

## 本次变更（增量，均需兼容说明）

| 变更 | Bean/成员 | 性质 | 兼容性 |
| --- | --- | --- | --- |
| 销毁路径 | `mapleWebClientConnectionProvider` 增 `@Bean(destroyMethod = "dispose")` | 行为增强 | 容器关闭释放池（修复 F-04）；对下游透明 |
| 可覆盖性口径统一 | `webClient` / `httpServiceProxyFactoryBuilder` / `httpServiceProxyFactory` 补 `@ConditionalOnMissingBean`（T048） | 行为放宽 | 原先覆盖会 `BeanDefinitionOverrideException`，现允许覆盖；无破坏 |
| 新增直连 builder | `webClientDirectBuilder`（`WebClient.Builder`，prototype，**无** `@LoadBalanced`） | 新增 | 新名字，不影响现有注入；直连外网 URL 的官方出口（F-01） |
| no-arg `webClientBuilder()` | 方法签名不变 | 行为修正 | 复用容器池；缺 Bean 时经 `GXWebClientResourceHolder` 单池复用（F-04/E-09），不再每次自建 |
| **方法签名稳定性** | `webClientBuilder(ObjectProvider, ObjectProvider, ReactorClientHttpConnector, Environment)` 4 参签名保留；`lbMode` 由包级私有重载承载，Bean 方法委托并默认 `lbMode=true` | 不变（T050 验收） | 既有 `GXWebClientConfigTest` 直接调用该方法并以含点主机 `https://example.test` 断言 503×4，签名或默认 `lbMode` 处理不当会直接破坏 P-02/SC-005 |
| 装配顺序 | `@AutoConfiguration` 无 `afterName` 变化 | 不变 | eureka-client `after = GXWebClientConfig` 继续成立 |

## 验收断言

1. `context.getBean("webClient", WebClient.class)` 单例存在；`webClientBuilder` 每次取到新实例（既有用例保持）。
2. eureka-client 模块 `mvn -pl leaf-base-eureka-client test` 不受影响（SC-007）。
3. 10 次 `new GXWebClientConfig().webClientBuilder()`（无池环境）→ 活跃池 = 1；上下文关闭 → `dispose()` 被调用。
4. 下游自定义 `webClient`/`httpServiceProxyFactory*` Bean → 启动成功（无 `BeanDefinitionOverrideException`），默认装配下三者仍单例（T048）。
5. `webClientBuilder(` 4 参方法可直接调用且编译通过（既有测试零改），含点主机的既有 503×4 用例行为不变（T050）。
