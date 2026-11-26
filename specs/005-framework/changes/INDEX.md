# 变更索引

| 编号 | 类型 | 状态 | 影响 | 标题 | 日期 |
| --- | --- | --- | --- | --- | --- |
| CR-20261125-009 | clarification | applied | `spec.md`（原 Clarifications） | 005-framework-module-audit 澄清会话归档 | 2026-10-22 |
| CR-20261125-010 | converge | applied | `tasks.md#T047` ~ `T069` | 005-framework-module-audit 收敛阶段归档 | 2026-11-25 |
| CR-20261126-050 | task | applied | `plan.md`；`quickstart.md`；`data-model.md`；`research.md`；`contracts/review-report-schema.md`；`checklists/report-checks.ps1` | 005-framework-module-audit 废弃报告契约与校验脚本并清理引用 | 2026-11-26 |
| CR-20261126-052 | task | applied | 无 spec 变更；`util/cglib/GXCglibUtils.java` | GXCglibUtils 补齐 Map 填充健壮性、批量拷贝语义与 API 文档 | 2026-11-26 |
| CR-20261126-053 | bugfix | applied | 无 spec 变更；`util/cglib/GXBeanCopierCache.java`；`audit/GXPerformanceBudgetAuditTest.java` | GXBeanCopierCache 改为 ClassValue 缓存，修复 F-16 缓存实际失效 | 2026-11-26 |
| CR-20261126-055 | bugfix | applied | 无 spec 变更；`util/GXCommonUtils.java` | GXCommonUtils 修复异常透传、静态方法与可变参数反射、map 字符串解析，并关闭 F-16 剩余项 | 2026-11-26 |
| CR-20261126-056 | bugfix | applied | 无 spec 变更；`util/GXCommonUtils.java`；`util/GXMethodCacheKeyUtils.java`；`util/cglib/GXCglibUtils.java`；`util/cglib/GXBeanCopierCache.java`；`leaf-base-rabbitmq/pom.xml` | 评审修复：撤回 fillBean 过度捕获、修复可变参数数组透传、校正文档与死条件 | 2026-11-26 |
| CR-20261126-057 | bugfix | applied | 无 spec 变更；`convert/GXCGLibDataConvert.java` | GXCGLibDataConvert 的 BeanCopier 缓存改为 ClassValue，修复与 F-16 同根因的缓存失效 | 2026-11-26 |
| CR-20261126-058 | bugfix | applied | 无 spec 变更；`util/GXCommonUtils.java`；`convert/GXCGLibDataConvert.java`；`util/cglib/GXBeanCopierCache.java`；`util/cglib/GXCglibUtils.java` | 第二轮评审修复：反射实参对齐、配置读取静默降级、两个弱键缓存改 ClassValue 与文档订正 | 2026-11-26 |
