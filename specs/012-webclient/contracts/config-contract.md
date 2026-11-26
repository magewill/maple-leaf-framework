# Contract: 配置键契约（冻结 + 增量）

**Prefix**: `maple.framework.web.client.*` | **Binding**: `GXWebClientProperties`（`@ConfigurationProperties`，R-08）
**Metadata**: 每个键（含新增）同步 `leaf-base-webclient/src/main/resources/META-INF/additional-spring-configuration-metadata.json`（宪法 IV）。

## 冻结键（键名 + 默认值不得变更）

| 键 | 类型 | 默认 | 语义 |
| --- | --- | --- | --- |
| `timeout-seconds` | int | 30 | 基准超时（现状：connect/read/write/response 共用） |
| `memory-buffer-size-mb` | int | 16 | 编解码 `maxInMemorySize`；Content-Length 告警阈值（修复后同源，F-07） |
| `max-connections` | int | 500 | 连接池上限 |
| `acquire-timeout-millis` | int | 3000 | 获取连接等待上限 |
| `pending-acquire-max-count` | int | 1000 | 等待队列上限 |
| `idle-timeout-millis` | int | 30000 | 连接空闲驱逐 |
| `max-life-time-millis` | int | 300000 | 连接最大存活 |
| `evict-in-background-seconds` | int | 120 | 后台驱逐周期 |
| `retry-max-attempts` | int | 3 | 最大重试次数（0=关闭） |
| `retry-delay-millis` | int | 1000 | 固定退避延迟 |

**非法值语义（F-17 收敛，按 data-model §1 的"0 语义表"执行）**：`timeout-seconds`/`memory-buffer-size-mb`/`max-connections`/`acquire-timeout-millis`/`pending-acquire-max-count`/`evict-in-background-seconds`/`connect-timeout-millis`/`write-timeout-millis` 的 0 或负数 → 启动期 fail-fast（文案含键名）；`idle-timeout-millis`/`max-life-time-millis`/`read-timeout-millis`/`response-timeout-millis`/`retry-*` 的 0 合法（含义见 data-model §1）；任何键的负数一律 fail-fast；非数字串 → fail-fast（现状抛转换异常，文案改为可定位）。

## 新增键（全部可缺省，旧配置零迁移；共 8 个 → 总键数 18）

| 键 | 类型 | 默认 | 语义 |
| --- | --- | --- | --- |
| `retry-methods` | 逗号分隔 HttpMethod | `GET,HEAD,OPTIONS` | 可重试方法集合；空=不重试；`GET,HEAD,OPTIONS,POST,PUT,DELETE` 恢复旧范围（R-04，兼容路径） |
| `user-agent-suffix` | String | 空 | 追加到固定 UA `Maple-Leaf-WebClient/1.0`；**不再嵌入 os.name**（F-16） |
| `connect-timeout-millis` | Integer | 回退 `timeout-seconds×1000` | 独立连接超时（R-08 可选拆分） |
| `read-timeout-millis` | Integer | 回退 `timeout-seconds×1000` | 读超时；**0=禁用**（流式/长响应安全出口，F-11） |
| `write-timeout-millis` | Integer | 回退 `timeout-seconds×1000` | 写超时 |
| `response-timeout-millis` | Integer | 回退 `timeout-seconds×1000` | 响应整体超时；0=禁用 |
| `lb-guard-enabled` | Boolean | `true` | LB 守卫逃生口（**兼容别名**）：`false` 时完全回到旧行为（交 LB 过滤器，含合成 503）。默认开启属缺陷收敛，理由与回退见 plan Complexity ⑤ |
| `lb-guard-allow-hosts` | Set\<String> | 空集合 | 守卫白名单：命中的主机（含带点服务名 `svc.ns.svc.cluster.local`）一律放行；空白项忽略；**条目端口被归一化忽略**（按主机匹配）；含协议/路径等非法条目在构建期 WARN 并忽略 |

**统一功能开关（宪法 IV 命名，不在 `web.client` 前缀下）**：

| 键 | 类型 | 默认 | 语义 |
| --- | --- | --- | --- |
| `maple.framework.enable.webclient-lb-guard` | Boolean | `true` | LB 守卫的正式开关；一旦配置即覆盖 `lb-guard-enabled`。默认 `true` 属 F-01 缺陷收敛的显式豁免，需在手册与 AGENTS 声明 |

> 元数据条目数 = 18（web.client）+ 1（enable）= **19**（`additional-spring-configuration-metadata.json`）。

## token 相关键（`GXWebClientService` 消费，冻结）

| 键 | 语义 | 不变性 |
| --- | --- | --- |
| `maple.framework.web.client.token` | token 源；缺失且存在 `GXWebClientService` Bean → 出站 fail-fast（E-24 既定语义） | 不变 |
| `maple.framework.web.client.secret` | 编解码密钥；缺失同上 | 不变 |

- token fail-fast 是否增加关闭开关（F-19）：**本次不加**，以文档显式声明该既定语义；如需开关留待后续提案（避免语义面扩大）。
- token 缓存（F-06/FR-025）：在 `GXWebClientService.generateHttpAuthToken()` 默认实现内做**短窗缓存**（≤ expiry/2 且 ≤60s 的窗口内复用生成结果，按 secret+source 失效），键名零新增；验收口径与 SC-006 一致：同一 `source+secret` 的 60s 窗口内生成 **1 次**、150s 有效期内 ≤3 次。
