# Coverage Ledger: leaf-base-framework

**Feature**: `005-framework-module-audit` | **Date**: 2026-10-22 | **Baseline**: `mvn -o -pl leaf-base-framework test` → 518 tests / 0 failures

覆盖口径见 [research.md](./research.md) D1：实现包逐文件读实现（`IMPLEMENTATION`），纯契约包做契约核对（`CONTRACT_ONLY`）。
判定值域见 [contracts/audit-verdict-contract.md](./contracts/audit-verdict-contract.md) §5；`note` 列给出该行判定的依据类别。

| 文件 | 包 | 强度 | 判定 | 关联发现 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `annotation/GXBusinessLog.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `annotation/GXEnableLeafFramework.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `annotation/GXHttpInvokerAuthToken.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `annotation/GXIgnoreLoginIntercept.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `annotation/GXPermission.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `annotation/GXPermissionCtl.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `annotation/GXSensitiveData.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `annotation/GXSensitiveField.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `annotation/GXStopWatch.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `annotation/GXValidateCRS.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `annotation/package-info.java` | annotation | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: attributes, retention, target |
| `api/GXBaseServeApi.java` | api | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `api/GXBaseServeApiImpl.java` | api | IMPLEMENTATION | RISK | F-20, F-22 | risk: reflection failure returns null with warn only |
| `api/package-info.java` | api | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `api/dto/req/GXBaseApiReqDto.java` | api | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: request/response DTO field contract |
| `api/dto/req/GXHttpQueryParamApiReqDto.java` | api | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: request/response DTO field contract |
| `api/dto/req/GXRpcQueryParamApiReqDto.java` | api | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: request/response DTO field contract |
| `api/dto/req/GXUpdateFieldRequest.java` | api | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: request/response DTO field contract |
| `api/dto/req/package-info.java` | api | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: request/response DTO field contract |
| `api/dto/res/GXBaseApiResDto.java` | api | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: request/response DTO field contract |
| `api/dto/res/GXHttpInvokerApiErrorResDto.java` | api | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: request/response DTO field contract |
| `api/dto/res/package-info.java` | api | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: request/response DTO field contract |
| `aspect/GXBusinessLogAspect.java` | aspect | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `aspect/GXStopWatchAspect.java` | aspect | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `aspect/GXValidateRequestParamAspect.java` | aspect | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `aspect/package-info.java` | aspect | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `code/GXBusinessResultStatusCode.java` | code | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: enum implements GXResultStatusCode, code segments |
| `code/GXDefaultResultStatusCode.java` | code | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: enum implements GXResultStatusCode, code segments |
| `code/GXResultStatusCode.java` | code | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: enum implements GXResultStatusCode, code segments |
| `code/package-info.java` | code | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: enum implements GXResultStatusCode, code segments |
| `config/GXAsyncConfig.java` | config | IMPLEMENTATION | DEFECT | F-01 | defect: @Async executor created per call, no context wrapper |
| `config/GXCommandLineRunner.java` | config | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `config/GXCORSConfig.java` | config | IMPLEMENTATION | RISK | F-19 | risk: dead branch; duplicate CORS configuration |
| `config/GXFilterConfig.java` | config | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `config/GXFrameworkConfig.java` | config | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `config/package-info.java` | config | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `config/aware/GXApplicationContextAware.java` | config | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `config/aware/GXApplicationContextSingleton.java` | config | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `config/aware/package-info.java` | config | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `config/cache/GXCaffeineCacheConfig.java` | config | IMPLEMENTATION | RISK | F-12 | risk: dynamic cache names disabled; softValues default |
| `config/cache/package-info.java` | config | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `config/support/GXCachingConfigurerSupport.java` | config | IMPLEMENTATION | RISK | F-12, F-20 | risk: error handler logs only; conditional ordering |
| `config/support/package-info.java` | config | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `config/web/GXBaseWebConfig.java` | config | IMPLEMENTATION | RISK | F-19 | risk: filter order interplay with XSS wrapper |
| `config/web/package-info.java` | config | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `constant/GXBuilderConstant.java` | constant | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: visibility, immutability, no mutable state |
| `constant/GXCommonConstant.java` | constant | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: visibility, immutability, no mutable state |
| `constant/GXDataSourceConstant.java` | constant | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: visibility, immutability, no mutable state |
| `constant/GXEventNameConstant.java` | constant | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: visibility, immutability, no mutable state |
| `constant/GXHttpInvokerConstant.java` | constant | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: visibility, immutability, no mutable state |
| `constant/GXTokenConstant.java` | constant | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: visibility, immutability, no mutable state |
| `constant/package-info.java` | constant | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: visibility, immutability, no mutable state |
| `controller/GXBase64DecodedMultipartFile.java` | controller | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `controller/GXBaseController.java` | controller | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `controller/package-info.java` | controller | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `convert/GXCGLibDataConvert.java` | convert | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: conversion behavior, null handling |
| `convert/GXHutoolDataConvert.java` | convert | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: conversion behavior, null handling |
| `convert/package-info.java` | convert | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: conversion behavior, null handling |
| `dao/GXBaseDao.java` | dao | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contract |
| `dao/package-info.java` | dao | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contract |
| `ddd/annotation/GXDomainEntity.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/annotation/package-info.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/factory/GXBaseDomainObjectFactory.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/factory/package-info.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/gateway/GXBaseGateway.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/gateway/package-info.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/presenter/GXBasePresenter.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/presenter/package-info.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/publisher/GXDomainEventPublisher.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/publisher/package-info.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/repository/GXBaseRepository.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `ddd/repository/package-info.java` | ddd | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: interface contracts, no runtime state |
| `deserializer/req/protocol/IntegerDeserializerProtocol.java` | deserializer | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: deserialization behavior, null handling |
| `deserializer/req/protocol/package-info.java` | deserializer | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: deserialization behavior, null handling |
| `dto/GXBaseData.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/GXBaseDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/GXBaseEventDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/GXBaseQueryParamInnerDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/GXBusinessLogDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/GXJoinDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/GXJoinTypeEnums.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/GXUnionTypeEnums.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/GXValidateExistsDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXCondition.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionEQ.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionGE.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionGT.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionIn.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionIsNotNULL.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionIsNULL.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionJsonEQ.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionLE.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionLikeFull.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionLikeLeft.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionLikeRight.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionLT.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionNE.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionNotIn.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionRaw.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionSegment.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionStrEQ.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionStrIn.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionStrNE.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXConditionStrNotIn.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXExclusionDeletedFieldCondition.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/GXIgnoreDataFilterCondition.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/func/GXConditionFunc.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/func/GXConditionFuncConcat.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/func/GXConditionFuncDialectSupport.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/func/GXConditionFuncJsonContains.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/func/GXConditionFuncJsonOverlaps.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/func/GXConditionFuncJsonSearch.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/condition/func/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/field/GXUpdateField.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/field/GXUpdateJsonDialectSupport.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/field/GXUpdateJsonRemoveField.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/field/GXUpdateJsonSetMapField.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/field/GXUpdateJsonSetStrField.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/field/GXUpdateMapField.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/field/GXUpdateNumberField.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/field/GXUpdateRawField.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/field/GXUpdateStrField.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/field/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinEQ.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinGE.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinGT.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinLE.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinLT.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinNE.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinOp.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinValue.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinValueEQ.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinValueGEQ.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinValueGT.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinValueLEQ.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinValueLT.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/GXDbJoinValueNE.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/op/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/permission/GXBasePermissionInnerDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/inner/permission/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/protocol/req/GXBaseReqProtocol.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/protocol/req/GXQueryParamReqProtocol.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/protocol/req/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/protocol/res/GXBaseResProtocol.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/protocol/res/GXPaginationResProtocol.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/protocol/res/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/req/GXBaseReqDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/req/GXDynamicCallParamAttributeReqDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/req/GXDynamicCallParamReqDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/req/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/res/GXBaseDBResDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/res/GXBaseResDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/res/GXPaginationResDto.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `dto/res/package-info.java` | dto | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: field types, defaults, generic bounds, serialization |
| `event/GXBaseEvent.java` | event | IMPLEMENTATION | DEFECT | F-08 | defect: custom payload not snapshotted; map keys snapshotted |
| `event/GXExceptionNotifyEvent.java` | event | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `event/GXMethodArgumentResolverEvent.java` | event | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `event/GXPermissionEvent.java` | event | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `event/package-info.java` | event | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `event/center/AsyncEventBusCenter.java` | event | IMPLEMENTATION | RISK | F-10 | risk: separate daemon executor, never closed |
| `event/center/package-info.java` | event | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `event/center/SyncEventBusCenter.java` | event | IMPLEMENTATION | RISK | F-10 | risk: static init reads Environment |
| `event/config/GuavaEventBusConfig.java` | event | IMPLEMENTATION | RISK | F-10 | risk: concurrency limit blocks publisher; dual executor models |
| `event/config/package-info.java` | event | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `event/dto/GXExceptionNotifyEventDto.java` | event | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `event/dto/package-info.java` | event | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `event/processor/GuavaASyncEventBusBeanPostProcessor.java` | event | IMPLEMENTATION | RISK | F-09 | risk: proxy class annotations not detected; beanName dedup |
| `event/processor/GuavaSyncEventBusBeanPostProcessor.java` | event | IMPLEMENTATION | RISK | F-09 | risk: proxy class annotations not detected; beanName dedup |
| `event/processor/package-info.java` | event | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `exception/GXBeanNotExistsException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXBeanValidateException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXBusinessException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXConciseBusinessException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXConvertException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXCookieValueException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXCorsConfigException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXDataFormatIncorrectException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXDBConditionException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXDBDuplicateKeyException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXDBException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXDBExistsException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXDBNotExistsException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXDBSQLException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXFeignAuthTokenException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXManticoreException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXRemoteServiceException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXSentinelFlowException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXSqlInjectionException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXTokenInvalidException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXUpdateFieldFormatException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/GXWebClientAuthTokenException.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `exception/package-info.java` | exception | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: hierarchy, code semantics, no runtime state |
| `factory/GXYamlPropertySourceFactory.java` | factory | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: PropertySource factory contract |
| `factory/package-info.java` | factory | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: PropertySource factory contract |
| `filter/GXBaseRequestLoggingFilter.java` | filter | IMPLEMENTATION | DEFECT | F-02, F-21 | defect: only traceId restored, other MDC keys leak |
| `filter/GXHTMLFilter.java` | filter | IMPLEMENTATION | DEFECT | F-05 | defect: non-thread-safe state; P_QUOTE equals P_LEFT_ARROW |
| `filter/GXSQLFilter.java` | filter | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `filter/GXXssFilter.java` | filter | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `filter/GXXssHttpServletRequestWrapper.java` | filter | IMPLEMENTATION | DEFECT | F-05, F-22 | defect: 50MB full-body rewrite, Content-Length header mismatch |
| `filter/package-info.java` | filter | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `handler/GXAsyncExceptionHandler.java` | handler | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `handler/GXExceptionHandler.java` | handler | IMPLEMENTATION | DEFECT | F-07 | defect: cause message echoed; parameter error returns ok(500) |
| `handler/package-info.java` | handler | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `lang/GXDict.java` | lang | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: value object semantics |
| `lang/package-info.java` | lang | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: value object semantics |
| `listener/GXApplicationStartedListener.java` | listener | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `listener/package-info.java` | listener | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `manager/GXExternalFeatureManager.java` | manager | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `manager/package-info.java` | manager | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `model/GXBaseModel.java` | model | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: model field contract |
| `model/package-info.java` | model | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: model field contract |
| `properties/GXCaffeineCacheManagerProperties.java` | properties | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: binding keys, defaults, conditional wiring |
| `properties/GXCaffeineCacheProperties.java` | properties | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: binding keys, defaults, conditional wiring |
| `properties/GXLocalAppEnvironmentProperties.java` | properties | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: binding keys, defaults, conditional wiring |
| `properties/package-info.java` | properties | CONTRACT_ONLY | CONTRACT_CHECKED | - | contract-only: binding keys, defaults, conditional wiring |
| `service/GXBaseCacheLockService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXBaseCacheService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXBotNotificationExceptionService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXBusinessLogService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXBusinessService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXCacheKeyGeneratorService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXCallRemoteValidateService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXCommandLineRunnerService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXDynamicCallMethodService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXFileUploadService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXParseDynamicCallParamService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXRenewalTokenService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXRequestBodyAdviceService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXResponseBodyAdviceService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXSensitiveDataDecryptService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXSensitiveDataEncryptService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/GXSensitiveFieldDeEncryptService.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/package-info.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/impl/GXBaseCacheLockServiceImpl.java` | service | IMPLEMENTATION | RISK | F-22 | risk: THREAD_LOCKS cleaned only by releaseAllLocks |
| `service/impl/GXBusinessServiceImpl.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/impl/GXDynamicCallMethodServiceImpl.java` | service | IMPLEMENTATION | DEFECT | F-06 | defect: Class.forName on caller-supplied name, no allowlist |
| `service/impl/GXFileUploadServiceImpl.java` | service | IMPLEMENTATION | RISK | F-15 | risk: volatile instance field shared across requests |
| `service/impl/GXParseDynamicCallParamServiceImpl.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `service/impl/GXSensitiveDataDecryptServiceImpl.java` | service | IMPLEMENTATION | DEFECT | F-03 | defect: catch ignored, no log |
| `service/impl/GXSensitiveDataEncryptServiceImpl.java` | service | IMPLEMENTATION | DEFECT | F-03 | defect: catch ignored, no log |
| `service/impl/GXSensitiveFieldDeEncryptServiceImpl.java` | service | IMPLEMENTATION | DEFECT | F-03 | defect: silent fallback to input; hardcoded default key |
| `service/impl/package-info.java` | service | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXAuthCodeUtils.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXCacheKeysUtils.java` | util | IMPLEMENTATION | RISK | F-12 | risk: missing prefix collapses namespaces |
| `util/GXCaffeineCacheUtils.java` | util | IMPLEMENTATION | RISK | F-12 | risk: default cache without TTL; clear/remove ordering |
| `util/GXCommonUtils.java` | util | IMPLEMENTATION | RISK | F-16 | risk: reflective cache clear-all; approximate overload match; buildTree O(n^2) |
| `util/GXConcurrentToolsUtils.java` | util | IMPLEMENTATION | RISK | F-13 | risk: CallerRunsPolicy on submit; static pool not rebuildable |
| `util/GXCookieHelperUtil.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXCurrentRequestContextUtils.java` | util | IMPLEMENTATION | RISK | F-14, F-19 | risk: static unbounded rate-limit map; isRPC heuristic |
| `util/GXDBStringUtils.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXEventPublisherUtils.java` | util | IMPLEMENTATION | RISK | F-11 | risk: identityHashCode key, unbounded registry, no unregister callers |
| `util/GXLoggerUtils.java` | util | IMPLEMENTATION | RISK | F-20 | risk: Throwable not extracted at some levels |
| `util/GXMdcThreadUtils.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXMethodCacheKeyUtils.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXResultUtils.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXSignatureUtils.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXSpELToolUtils.java` | util | IMPLEMENTATION | RISK | F-17 | risk: StandardEvaluationContext; method name concatenated into expression |
| `util/GXSpringContextUtils.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXSqlDbTypeResolver.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXTokenManagerUtils.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/GXTraceIdContextUtils.java` | util | IMPLEMENTATION | DEFECT | F-02, F-19 | defect: restore limited to single key; empty-string return contract |
| `util/GXValidatorUtils.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/package-info.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/cglib/GXBeanCopierCache.java` | util | IMPLEMENTATION | RISK | F-16 | risk: weak key plus new String key defeats cache |
| `util/cglib/GXCglibUtils.java` | util | IMPLEMENTATION | RISK | F-16 | risk: depends on defeated copier cache |
| `util/cglib/package-info.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/manticore/GXManticoreAutocompleteOperations.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/manticore/GXManticoreBuilder.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/manticore/GXManticoreClient.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/manticore/GXManticoreDocumentOperations.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/manticore/GXManticorePercolateOperations.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/manticore/GXManticoreQueryOperations.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/manticore/GXManticoreSqlOperations.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/manticore/GXManticoreUtils.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `util/manticore/package-info.java` | util | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/GXValidateCallRemoteServiceValidator.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/package-info.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/GXAddGroup.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/GXCreateGroup.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/GXGroup.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/GXIdGroup.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/GXIdsGroup.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/GXLoginGroup.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/GXOtherGroup.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/GXQueryGroup.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/GXUpdateGroup.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/GXUpdatePasswordGroup.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `validator/group/package-info.java` | validator | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `web/advice/GXRequestBodyAdvice.java` | web | IMPLEMENTATION | DEFECT | F-04 | defect: full body JSON stored as request attribute |
| `web/advice/GXResponseBodyAdvice.java` | web | IMPLEMENTATION | DEFECT | F-04 | defect: default cookie secret hardcoded; cookie emitted per response when enabled |
| `web/advice/package-info.java` | web | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `web/config/GXHandlerMethodArgumentResolverConfigurer.java` | web | IMPLEMENTATION | RISK | F-18 | risk: assert used for production null check |
| `web/config/GXWebMvcConfigurer.java` | web | IMPLEMENTATION | RISK | F-18 | risk: interceptor order by bean name; no include/exclude |
| `web/config/package-info.java` | web | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `web/interceptor/GXAuthorizationInterceptor.java` | web | IMPLEMENTATION | RISK | F-19 | risk: default allow-all base interceptor |
| `web/interceptor/GXBaseSSOPermissionInterceptor.java` | web | IMPLEMENTATION | RISK | F-19 | risk: empty class registered as bean |
| `web/interceptor/GXRenewalTokenInterceptor.java` | web | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `web/interceptor/GXTraceIdInterceptor.java` | web | IMPLEMENTATION | DEFECT | F-02 | defect: afterCompletion empty, no async handling |
| `web/interceptor/GXVerifyDeployEnvironmentInterceptor.java` | web | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `web/interceptor/package-info.java` | web | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `web/support/GXCustomerHandlerMethodArgumentResolver.java` | web | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `web/support/package-info.java` | web | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
| `wrapper/mdc/GXMdcWrapperForkJoinPool.java` | wrapper | IMPLEMENTATION | DEFECT | F-01 | defect: submit(ForkJoinTask)/invoke not wrapped |
| `wrapper/mdc/GXMdcWrapperThreadPoolExecutor.java` | wrapper | IMPLEMENTATION | DEFECT | F-01 | defect: never registered as bean (E-01) so wrap capability silently unused; wrap covers execute/submit/invokeAll |
| `wrapper/mdc/GXMdcWrapperThreadPoolTaskExecutor.java` | wrapper | IMPLEMENTATION | DEFECT | F-01 | defect: unassembled + no TaskDecorator; Spring 7 removed submitListenable (E-23) so that gap is gone; wrap covers execute/submit/submitCompletable |
| `wrapper/mdc/package-info.java` | wrapper | IMPLEMENTATION | CLEAN | - | reviewed: no defect found |
