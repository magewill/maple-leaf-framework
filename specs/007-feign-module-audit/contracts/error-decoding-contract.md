# 契约：远端错误解码（`GXFeignCustomErrorDecoder`）

**Feature**: `007-feign-module-audit` | **适用版本**: `leaf-base-feign` 4.3.0-SNAPSHOT

## 1. 生效条件

| 条件 | 结果 |
| --- | --- |
| `maple.framework.feign.error-decoder.enabled` 未配置 | **默认关闭**，使用 Feign `ErrorDecoder.Default`（抛 `FeignException`） |
| 显式 `true` 且容器无用户 `ErrorDecoder` Bean | 使用本解码器 |
| 容器已有用户 `ErrorDecoder` Bean | 用户实现优先，本解码器不注册 |

## 2. 状态码映射矩阵（解码器启用时）

| HTTP 状态 | 异常类型 | `code` | `msg` 来源 | 响应体处理 |
| --- | --- | --- | --- | --- |
| 400 | `GXBusinessException` | 400 | 远端 `msg` → 固定文案 | 保留（≤8192 字符，超长截断） |
| 401 | `GXBusinessException` | 401 | 固定文案（不拼接 body） | 不进入消息，仅 debug 日志 |
| 403 | `GXBusinessException` | 403 | 固定文案（不拼接 body） | 同上 |
| 404 | `GXBusinessException` | 404 | 固定文案 + 请求 URL | 同上 |
| 409 | `GXBusinessException` | 409 | 远端 `msg` → 固定文案 | 保留（同上限） |
| 422 | `GXBusinessException` | 422 | 远端 `msg` → 固定文案 | 保留（同上限） |
| 429 | `GXBusinessException` | 429 | 远端 `msg` → 固定文案 | 保留（同上限） |
| 500 | `GXBusinessException` | 500 | 固定文案 | 不进入消息 |
| 503 | `GXBusinessException` | 503 | 固定文案 | 不进入消息 |
| 504 | `GXBusinessException` | 504 | 固定文案 | 不进入消息 |
| 其他（204/301/418 等） | Feign `FeignException` | N/A | 默认解码器负责 | 默认解码器负责 |

**不变量**：

- `code` 必须等于远端 HTTP 状态码；**禁止**统一使用 `HTTP_INTERNAL_ERROR`。
- 401/403 的响应体不得进入异常消息（避免回显认证细节）。
- 响应体读取上限 8192 字符，超长追加 `...[truncated]`；读取 IO 异常降级为固定文案 `Failed to read error response body`。

## 3. 远端业务响应解析

当响应体可解析为 `GXResultUtils` 形态 `{"code":int,"msg":string,"data":object}` 时：

| 目标 | 取值 |
| --- | --- |
| 异常 `code` | 远端 `code`（若为 0 或缺失，回退为 HTTP 状态码） |
| 异常 `msg` | 远端 `msg`（空白则回退为固定文案） |
| 异常 `data` | 远端 `data`（缺失则为空 `Dict`） |

解析失败（非 JSON、结构不符、缺少必需字段）时，全部回退为"固定文案 + HTTP 状态码"，不得抛解析异常。

## 4. 与熔断 / fallback 的交互

| 场景 | 结果 |
| --- | --- |
| 未配置 `fallback` | 异常向上抛出，经全局异常处理返回统一响应 |
| 配置了 `fallback` | `FrameworkRetryCircuitBreaker` 捕获任意异常后调用 fallback，**业务异常也会被吞**（依赖库行为，已在 `research.md` 核实） |
| 配置了 `fallbackFactory` | 同上，`cause` 为解码后的异常 |

**约束**：本次不改变该交互（属 Spring Cloud 行为），但必须在开发手册与审查报告中显式说明"配置 fallback 后 4xx 业务异常不会抛给调用方"。

## 5. 验证断言

1. 10 个状态码各返回一次，断言"异常类型 + code + 消息是否含 body"三元组。
2. 400 返回 `{"code":1001,"msg":"库存不足","data":{}}`，断言 `code=1001`、消息含"库存不足"。
3. 401 返回含敏感信息的 body，断言消息中不含该 body。
4. body 为空 / 非 JSON / 9000 字符 / 读取中断，四类降级各一条断言。
5. 解码器默认关闭时断言异常类型为 `FeignException`（既有用例保持）。
