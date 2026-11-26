# 契约：监听声明注解

**类型**：公共注解（跨模块可见，下游可直接使用）
**稳定性**：冻结。本次加固**不新增、不删除、不重命名**任何属性。

## 签名

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface GXMyBatisListener {
    Class<? extends GXMybatisListenerService<?>> listenerClazz();
    String runType() default GXMyBatisEventConstant.MYBATIS_SYNC_EVENT;
}
```

## 属性语义

| 属性 | 必填 | 默认值 | 语义 | 兼容性 |
| --- | --- | --- | --- | --- |
| `listenerClazz` | 是 | 无 | 目标监听器类型；必须可从容器取到唯一 Bean | 不可变更 |
| `runType` | 否 | 同步 | `MYBATIS_SYNC_EVENT`：业务线程内投递；`MYBATIS_ASYNC_EVENT`：事务提交后投递 | 新增取值只允许追加，不允许改变既有取值语义 |

## 声明优先级

1. 方法级声明（数据访问接口方法 / 批量服务方法）
2. 声明所在类型的类型级声明
3. 父接口或父类上的同名声明（按"方法 → 接口 → 父类"逐层回溯，带环路保护）

同一优先级内若命中多个声明，按"监听器类型 + 事件种类"去重后全部生效。

## 行为约束

- 注解只对**写操作**生效；读操作即使标注也不产生事件。
- 未标注的写操作不产生事件，且不得引入可测量的额外开销。
- 注解声明不得改变原写操作的返回值与异常语义（除显式开启 `propagate-exception`）。
- 一个写操作命中 N 个不同监听器时，按接口全限定名排序后依次投递，保证顺序确定。
- 同一写操作对同一监听器只投递一次。

## 兼容性规则

- 后续如需新增属性，必须提供默认值，且默认语义等于"当前行为"，否则按破坏性变更处理。
- 不允许把 `runType` 的既有取值改为新语义；需要新语义时新增取值。
