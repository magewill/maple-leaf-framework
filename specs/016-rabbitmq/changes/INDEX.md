# 变更索引

| 编号 | 类型 | 状态 | 影响 | 标题 | 日期 |
| --- | --- | --- | --- | --- | --- |
| CR-20261125-032 | clarification | applied | `spec.md`（原 Clarifications） | 016-rabbitmq-module-audit 澄清会话归档 | 2026-10-25 |
| CR-20261125-033 | converge | applied | `tasks.md#T070` ~ `T090` | 016-rabbitmq-module-audit 收敛阶段归档 | 2026-11-25 |
| CR-20261125-034 | task | applied | `tasks.md#T001` ~ `T069` | 016-rabbitmq-module-audit 执行记录与实施期新增修复归档 | 2026-11-25 |
| CR-20261126-051 | task | applied | `plan.md`；`quickstart.md` | 016-rabbitmq-module-audit 清理报告引用 | 2026-11-26 |
| CR-20261126-054 | bugfix | applied | 无 spec 变更；`pom.xml`；`properties/GXRabbitMQProperties.java`；`config/GXRabbitMQConfig.java`；`service/impl/GXQueueDeclarationConcurrencyTest.java` | 适配 Spring Boot 4.2 的 AMQP 模块拆分，恢复 leaf-base-rabbitmq 编译并稳定并发用例 | 2026-11-26 |
