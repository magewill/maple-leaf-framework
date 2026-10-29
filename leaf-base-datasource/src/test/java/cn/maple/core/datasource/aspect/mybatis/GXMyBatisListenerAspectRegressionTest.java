package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.enums.GXMyBatisListenerDegradeReasonEnums;
import cn.maple.core.datasource.event.GXMyBatisModelSaveBatchEntitySnapshotEvent;
import cn.maple.core.datasource.listener.GXMyBatisSyncListener;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.field.GXUpdateNumberField;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.event.EventListener;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GXMyBatisListenerAspectRegressionTest {
    private final GXMyBatisListenerEventPublisher eventPublisher = newEventPublisher(new GXMyBatisListenerProperties());
    private final GXMyBatisPlusSaveEntityAspect saveEntityAspect = new GXMyBatisPlusSaveEntityAspect(eventPublisher);
    private final GXMyBatisPlusUpdateFieldAspect updateFieldAspect = new GXMyBatisPlusUpdateFieldAspect(eventPublisher);
    private final GXMyBatisPlusDeleteSoftAspect deleteSoftAspect = new GXMyBatisPlusDeleteSoftAspect(eventPublisher);
    private final GXMyBatisPlusUpdateEntityAspect updateEntityAspect = new GXMyBatisPlusUpdateEntityAspect(eventPublisher);
    private final GXMyBatisPlusSaveBatchEntityAspect saveBatchEntityAspect = new GXMyBatisPlusSaveBatchEntityAspect(eventPublisher);
    private final GXMyBatisPlusDeleteAspect deleteAspect = new GXMyBatisPlusDeleteAspect(eventPublisher);

    private static GXMyBatisListenerEventPublisher newEventPublisher(GXMyBatisListenerProperties properties) {
        GXMyBatisListenerConfigResolver resolver = new GXMyBatisListenerConfigResolver(properties);
        return new GXMyBatisListenerEventPublisher(resolver, properties, new StandardEnvironment());
    }

    @Test
    void saveEntityPublishesEveryMatchedMapperConfiguration() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new DualMapperImpl(), DualMapperOne.class.getMethod("insert", Object.class));
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 1)});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> saveEntityAspect.around(point));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(2));
        }
    }

    @Test
    void saveEntityFindsListenerConfigurationOnParentMapperInterface() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new ChildAnnotatedMapperImpl(), ChildAnnotatedMapper.class.getMethod("insert", Object.class));
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 1)});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> saveEntityAspect.around(point));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
        }
    }

    @Test
    void saveBatchResolvesMethodLevelListenerConfiguration() throws Exception {
        Method method = MethodAnnotatedBatchService.class.getMethod("saveBatch", Collection.class);
        GXMyBatisListenerConfigResolver resolver = new GXMyBatisListenerConfigResolver(new GXMyBatisListenerProperties());

        List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners =
                resolver.resolve(resolver.serviceCandidateTypes(new MethodAnnotatedBatchService(), null), method);

        assertEquals(1, listeners.size());
        assertEquals(FirstListener.class, listeners.getFirst().listenerClazz());
    }

    @Test
    void saveEntitySkipsPublishWhenNoRowsAffected() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new ChildAnnotatedMapperImpl(), ChildAnnotatedMapper.class.getMethod("insert", Object.class));
        Mockito.when(point.proceed()).thenReturn(0);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 1)});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> saveEntityAspect.around(point));
            eventPublisher.verifyNoInteractions();
        }
    }

    @Test
    void saveEntitySkipsPublishWhenProceedThrows() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new ChildAnnotatedMapperImpl(), ChildAnnotatedMapper.class.getMethod("insert", Object.class));
        Mockito.when(point.proceed()).thenThrow(new IllegalStateException("write failed"));
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 1)});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertThrows(IllegalStateException.class, () -> saveEntityAspect.around(point));
            eventPublisher.verifyNoInteractions();
        }
    }

    @Test
    void payloadThatCannotBeBuiltDoesNotFailTheWrite() throws Throwable {
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();
        Method method = ChildAnnotatedMapper.class.getMethod("insert", Object.class);
        ProceedingJoinPoint point = mockJoinPoint(new ChildAnnotatedMapperImpl(), method);
        Mockito.when(point.proceed()).thenReturn(1);
        // More nodes than GXBaseEvent accepts, so the event constructor rejects the payload.
        List<String> oversizedChildren = new ArrayList<>(100_500);
        for (int index = 0; index < 100_500; index++) {
            oversizedChildren.add("child-" + index);
        }
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 1, "children", oversizedChildren)});

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            Object result = assertDoesNotThrow(() -> saveEntityAspect.around(point),
                    "Building the payload must never fail an already completed write");
            assertEquals(1, result);
            eventPublisherMock.verifyNoInteractions();
        }

        assertEquals(failedBefore + 1L, GXMyBatisListenerMetrics.snapshot().failed(),
                "An unbuildable payload must be counted as a failed delivery instead of escaping");
    }

    @Test
    void payloadBuilderFailureDoesNotFailTheCompletedWrite() throws Throwable {
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();
        ProceedingJoinPoint point = mockJoinPoint(new DeleteMapperImpl(),
                DeleteMapper.class.getMethod("deleteCondition", Object.class));
        Mockito.when(point.proceed()).thenReturn(1);
        // Condition field validation rejects the expression while the payload is being built, after
        // the DELETE already succeeded.
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(List.of(new GXConditionEQ(null, "id; drop table t_listener", 1)))
                .build();
        Mockito.when(point.getArgs()).thenReturn(new Object[]{queryParam});

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            Object result = assertDoesNotThrow(() -> deleteAspect.around(point),
                    "A payload builder failure must never fail an already completed write");
            assertEquals(1, result);
            eventPublisherMock.verifyNoInteractions();
        }

        assertEquals(failedBefore + 1L, GXMyBatisListenerMetrics.snapshot().failed(),
                "An unbuildable payload must be counted as a failed delivery instead of escaping");
    }

    @Test
    void saveEntityDefersAsyncListenerUntilCommit() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new AsyncSaveMapperImpl(), AsyncSaveMapper.class.getMethod("insert", Object.class));
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 1)});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> saveEntityAspect.around(point));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEventAfterCommit(any()), Mockito.times(1));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.never());
        }
    }

    @Test
    void saveEntityPointcutExcludesMyBatisPlusBatchInsertOverloads() throws Exception {
        Around around = GXMyBatisPlusSaveEntityAspect.class
                .getDeclaredMethod("around", ProceedingJoinPoint.class)
                .getAnnotation(Around.class);
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(around.value().substring(around.value().indexOf("execution")));

        assertTrue(pointcut.matches(InsertTarget.class.getMethod("insert", Object.class), InsertTarget.class));
        assertFalse(pointcut.matches(InsertTarget.class.getMethod("insert", Collection.class), InsertTarget.class));
    }

    @Test
    void updateFieldSkipsNullUpdatePayloadWithoutThrowing() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new UpdateFieldMapperImpl(), UpdateFieldMapper.class.getMethod("updateFieldByCondition", Object.class, Object.class));
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{GXBaseQueryParamInnerDto.builder().build(), null});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> updateFieldAspect.around(point));
            eventPublisher.verifyNoInteractions();
        }
    }

    @Test
    void updateFieldSkipsNullEntriesInUpdatePayload() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new UpdateFieldMapperImpl(), UpdateFieldMapper.class.getMethod("updateFieldByCondition", Object.class, Object.class));
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(List.of(new GXConditionEQ(null, "id", 7)))
                .build();
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{queryParam, Collections.singletonList(null)});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> updateFieldAspect.around(point));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
        }
    }

    @Test
    void updateFieldIgnoresRawConditionsWhenPublishingEvent() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new UpdateFieldMapperImpl(), UpdateFieldMapper.class.getMethod("updateFieldByCondition", Object.class, Object.class));
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(List.of(new GXConditionRaw("1 = 1")))
                .build();
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{queryParam, List.of(new GXUpdateNumberField(null, "score", 1))});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> updateFieldAspect.around(point));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
        }
    }

    @Test
    void deleteSoftSkipsNullConditionPayloadWithoutThrowing() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new DeleteSoftMapperImpl(), DeleteSoftMapper.class.getMethod("deleteSoftCondition", Object.class, Object.class));
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{null, List.of(new GXUpdateNumberField(null, "deletedFlag", 1))});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> deleteSoftAspect.around(point));
            eventPublisher.verifyNoInteractions();
        }
    }

    @Test
    void deleteSoftIgnoresRawConditionsWhenPublishingEvent() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new DeleteSoftMapperImpl(), DeleteSoftMapper.class.getMethod("deleteSoftCondition", Object.class, Object.class));
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(List.of(new GXConditionRaw("1 = 1")))
                .build();
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{queryParam, List.of(new GXUpdateNumberField(null, "deletedFlag", 1))});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> deleteSoftAspect.around(point));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
        }
    }

    @Test
    void deleteSoftKeepsUpdateFieldDataEmptyWhenCallerSuppliesNone() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new DeleteSoftMapperImpl(),
                DeleteSoftMapper.class.getMethod("deleteSoftCondition", Object.class, Object.class));
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(List.of(new GXConditionEQ(null, "id", 7)))
                .build();
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{queryParam, List.of()});

        AtomicReference<GXBaseEvent<?>> published = new AtomicReference<>();
        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any()))
                    .thenAnswer(invocation -> {
                        published.set(invocation.getArgument(0));
                        return null;
                    });
            assertDoesNotThrow(() -> deleteSoftAspect.around(point));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
        }

        Dict source = publishedSource(published);
        Dict conditionFieldData = (Dict) source.get("conditionFieldData");
        Dict updateFieldData = (Dict) source.get("updateFieldData");
        assertEquals(7, conditionFieldData.getInt("id"));
        assertTrue(updateFieldData.isEmpty(),
                "The payload only reflects update fields supplied by the caller; the framework-appended"
                        + " logical-delete column is not part of it (contracts/event-payload.md)");
        assertEquals(Boolean.TRUE, source.get("conditionLossless"));
    }

    @Test
    void rawConditionFailureDegradesUpdateFieldPayload() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new UpdateFieldMapperImpl(),
                UpdateFieldMapper.class.getMethod("updateFieldByCondition", Object.class, Object.class));
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(List.of(throwingRawCondition()))
                .build();
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{queryParam,
                List.of(new GXUpdateNumberField(null, "score", 1))});

        long degradedBefore = GXMyBatisListenerMetrics.snapshot().degraded();
        AtomicReference<GXBaseEvent<?>> published = new AtomicReference<>();
        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any()))
                    .thenAnswer(invocation -> {
                        published.set(invocation.getArgument(0));
                        return null;
                    });
            assertDoesNotThrow(() -> updateFieldAspect.around(point));
        }

        Dict source = publishedSource(published);
        assertEquals(Boolean.FALSE, source.get("conditionLossless"));
        assertEquals(List.of(GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR), source.get("degradeReasons"));
        assertEquals("", source.getStr("rawConditionSql"));
        assertEquals(degradedBefore + 1L, GXMyBatisListenerMetrics.snapshot().degraded(),
                "A skipped raw condition must count as a degraded payload");
    }

    @Test
    void rawConditionFailureDegradesDeleteSoftPayload() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new DeleteSoftMapperImpl(),
                DeleteSoftMapper.class.getMethod("deleteSoftCondition", Object.class, Object.class));
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(List.of(throwingRawCondition()))
                .build();
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{queryParam,
                List.of(new GXUpdateNumberField(null, "deletedFlag", 1))});

        long degradedBefore = GXMyBatisListenerMetrics.snapshot().degraded();
        AtomicReference<GXBaseEvent<?>> published = new AtomicReference<>();
        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any()))
                    .thenAnswer(invocation -> {
                        published.set(invocation.getArgument(0));
                        return null;
                    });
            assertDoesNotThrow(() -> deleteSoftAspect.around(point));
        }

        Dict source = publishedSource(published);
        assertEquals(Boolean.FALSE, source.get("conditionLossless"));
        assertEquals(List.of(GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR), source.get("degradeReasons"));
        assertEquals("", source.getStr("rawConditionSql"));
        assertEquals(degradedBefore + 1L, GXMyBatisListenerMetrics.snapshot().degraded(),
                "A skipped raw condition must count as a degraded payload");
    }

    @Test
    void rawConditionFailureDegradesDeletePayload() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new DeleteMapperImpl(),
                DeleteMapper.class.getMethod("deleteCondition", Object.class));
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(List.of(throwingRawCondition()))
                .build();
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{queryParam});

        long degradedBefore = GXMyBatisListenerMetrics.snapshot().degraded();
        AtomicReference<GXBaseEvent<?>> published = new AtomicReference<>();
        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any()))
                    .thenAnswer(invocation -> {
                        published.set(invocation.getArgument(0));
                        return null;
                    });
            assertDoesNotThrow(() -> deleteAspect.around(point));
        }

        Dict source = publishedSource(published);
        assertEquals(Boolean.FALSE, source.get("conditionLossless"));
        assertEquals(List.of(GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR), source.get("degradeReasons"));
        assertEquals("", source.getStr("rawConditionSql"));
        assertEquals(degradedBefore + 1L, GXMyBatisListenerMetrics.snapshot().degraded(),
                "A skipped raw condition must count as a degraded payload");
    }

    @Test
    void updateEntityParsesCommonOperatorsWithoutFailing() {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.gt("age", 18)
                .ne("status", "inactive")
                .between("score", 60, 80)
                .in("type", List.of("A", "B"))
                .isNull("deleted_at");

        Dict parsed = ReflectionTestUtils.invokeMethod(updateEntityAspect, "parseWhereSQL", wrapper);
        Dict keyOperatorPairs = (Dict) parsed.get("keyOperatorPairs");
        Dict keyValuePairs = (Dict) parsed.get("keyValuePairs");

        assertEquals(">", keyOperatorPairs.get("age"));
        assertEquals("<>", keyOperatorPairs.get("status"));
        assertEquals("between", keyOperatorPairs.get("score"));
        assertEquals("in", keyOperatorPairs.get("type"));
        assertEquals("is null", keyOperatorPairs.get("deletedAt"));
        assertEquals(18, keyValuePairs.get("age"));
        assertEquals("inactive", keyValuePairs.get("status"));
        assertEquals(List.of(60, 80), keyValuePairs.get("score"));
        assertEquals(List.of("A", "B"), keyValuePairs.get("type"));
        assertNull(keyValuePairs.get("deletedAt"));
    }

    @Test
    void updateEntityUsesWhereParameterNamesWhenSetValuesWereAddedFirst() {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.set("name", "changed").eq("id", 7);

        Dict parsed = ReflectionTestUtils.invokeMethod(updateEntityAspect, "parseWhereSQL", wrapper);

        assertEquals(7, ((Dict) parsed.get("keyValuePairs")).get("id"));
    }

    @Test
    void updateEntityParsesLambdaUpdateWrapper() {
        LambdaUpdateWrapper<Object> wrapper = new LambdaUpdateWrapper<>();
        wrapper.setSql("status = {0}", "changed").apply("id = {0}", 7L);

        Dict parsed = ReflectionTestUtils.invokeMethod(updateEntityAspect, "handleUpdateWrapper", wrapper);

        assertEquals(7L, ((Dict) parsed.get("keyValuePairs")).get("id"));
    }

    @Test
    void updateEntityRecordsWrapperOnlyUpdate() {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.set("status", "enabled").eq("id", 7);
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{null, wrapper});
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getName()).thenReturn("update");

        Dict source = ReflectionTestUtils.invokeMethod(updateEntityAspect, "handlePointArgs", point);

        assertEquals("update", source.get("operation"));
        assertEquals(Dict.create(), source.get("entityData"));
        assertEquals(7, ((Dict) source.get("keyValuePairs")).get("id"));
    }

    @Test
    void updateEntityPreservesRawWhereSqlWhenConditionsCannotBeLosslesslyParsed() {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", 7).or().eq("name", "maple");
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{null, wrapper});
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getName()).thenReturn("update");

        Dict source = ReflectionTestUtils.invokeMethod(updateEntityAspect, "handlePointArgs", point);

        assertEquals(wrapper.getTargetSql(), source.getStr("rawWhereSql"));
        assertEquals(wrapper.getTargetSql(), source.getStr("rawConditionSql"));
        assertEquals(Boolean.FALSE, source.get("conditionLossless"));
        assertEquals(List.of(GXMyBatisListenerDegradeReasonEnums.OR_EXPRESSION), source.get("degradeReasons"));
    }

    @Test
    void updateEntityPreservesTargetSqlForNonAbstractWrapper() {
        @SuppressWarnings("unchecked")
        Wrapper<Object> wrapper = Mockito.mock(Wrapper.class);
        Mockito.when(wrapper.getTargetSql()).thenReturn("id = 7");

        Dict parsed = ReflectionTestUtils.invokeMethod(updateEntityAspect, "handleUpdateWrapper", wrapper);

        assertEquals("id = 7", parsed.getStr("rawWhereSql"));
        assertEquals(Dict.create(), parsed.get("keyOperatorPairs"));
        assertEquals(Dict.create(), parsed.get("keyValuePairs"));
    }

    @Test
    void updateEntityListenerWithRawWhereSqlDelegatesToLegacyListener() {
        AtomicReference<String> receivedData = new AtomicReference<>();
        GXMybatisListenerService<String> listener = new GXMybatisListenerService<>() {
            @Override
            public void updateEntityListener(String data, Dict keyValuePairs, Dict keyOperatorPairs) {
                receivedData.set(data);
            }
        };

        assertDoesNotThrow(() -> GXMybatisListenerService.class
                .getMethod("updateEntityListener", Object.class, Dict.class, Dict.class, String.class)
                .invoke(listener, "entity", Dict.create(), Dict.create(), "id = 7"));

        assertEquals("entity", receivedData.get());
    }

    @Test
    void updateEntityParsesParameterlessNullCondition() {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.isNull("deleted_at");

        Dict parsed = ReflectionTestUtils.invokeMethod(updateEntityAspect, "parseWhereSQL", wrapper);

        assertEquals("is null", ((Dict) parsed.get("keyOperatorPairs")).get("deletedAt"));
        assertTrue(((Dict) parsed.get("keyValuePairs")).containsKey("deletedAt"));
    }

    @Test
    void updateEntityFallsBackToRawSqlForComputedConditionFields() {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.apply("DATE(created_at) = {0}", "2026-08-07");

        Dict parsed = ReflectionTestUtils.invokeMethod(updateEntityAspect, "parseWhereSQL", wrapper);

        assertEquals(Dict.create(), parsed.get("keyOperatorPairs"));
        assertEquals(Dict.create(), parsed.get("keyValuePairs"));
        assertEquals(wrapper.getTargetSql(), parsed.getStr("rawWhereSql"));
        assertEquals(Boolean.FALSE, parsed.get("conditionLossless"));
        assertEquals(wrapper.getTargetSql(), parsed.getStr("rawConditionSql"));
        assertEquals(List.of(GXMyBatisListenerDegradeReasonEnums.NON_COLUMN_LEFT), parsed.get("degradeReasons"));
    }

    @Test
    void updateEntityMarksDuplicateFieldDegrade() {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.eq("status", "enabled").eq("status", "disabled");

        Dict parsed = ReflectionTestUtils.invokeMethod(updateEntityAspect, "parseWhereSQL", wrapper);

        assertEquals(Boolean.FALSE, parsed.get("conditionLossless"));
        assertEquals(wrapper.getTargetSql(), parsed.getStr("rawConditionSql"));
        assertEquals(List.of(GXMyBatisListenerDegradeReasonEnums.DUPLICATE_FIELD), parsed.get("degradeReasons"));
        assertTrue(((Dict) parsed.get("keyValuePairs")).isEmpty());
        assertTrue(((Dict) parsed.get("keyOperatorPairs")).isEmpty());
    }

    @Test
    void updateEntityMarksParameterMismatchDegrade() {
        @SuppressWarnings("unchecked")
        AbstractWrapper<Object, ?, ?> wrapper = Mockito.mock(AbstractWrapper.class);
        // Real wrappers expose JDBC placeholders via getTargetSql() and MyBatis placeholders via
        // getSqlSegment(); only MPGENVAL1 is backed by a value, so the where parameters do not match.
        Mockito.when(wrapper.getTargetSql()).thenReturn("(id = ? AND name = ?)");
        Mockito.when(wrapper.getSqlSegment()).thenReturn(
                "id = #{ew.paramNameValuePairs.MPGENVAL1} AND name = #{ew.paramNameValuePairs.MPGENVAL2}");
        Mockito.when(wrapper.getParamNameValuePairs()).thenReturn(Map.of("MPGENVAL1", 1L));

        Dict parsed = ReflectionTestUtils.invokeMethod(updateEntityAspect, "parseWhereSQL", wrapper);

        assertEquals(Boolean.FALSE, parsed.get("conditionLossless"));
        assertEquals(wrapper.getTargetSql(), parsed.getStr("rawWhereSql"));
        assertEquals(wrapper.getTargetSql(), parsed.getStr("rawConditionSql"));
        assertEquals(List.of(GXMyBatisListenerDegradeReasonEnums.PARAM_MISMATCH), parsed.get("degradeReasons"));
        assertTrue(((Dict) parsed.get("keyValuePairs")).isEmpty());
    }

    @Test
    void updateEntitySnapshotsMutableWhereValues() {
        Date updatedAt = new Date(1_000L);
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.eq("updated_at", updatedAt);

        Dict parsed = ReflectionTestUtils.invokeMethod(updateEntityAspect, "parseWhereSQL", wrapper);
        updatedAt.setTime(2_000L);

        assertEquals(new Date(1_000L), ((Dict) parsed.get("keyValuePairs")).get("updatedAt"));
    }

    @Test
    void physicalDeletePublishesAfterCommitEvent() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new DeleteMapperImpl(), DeleteMapper.class.getMethod("deleteCondition", Object.class));
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(List.of(new GXConditionEQ(null, "id", 7)))
                .build();
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{queryParam});

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> deleteAspect.around(point));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
        }
    }

    @Test
    void updateEntityKeepsOnlyRawConditionForOrExpression() {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.eq("status", "enabled").or().eq("status", "disabled");

        Dict parsed = ReflectionTestUtils.invokeMethod(updateEntityAspect, "parseWhereSQL", wrapper);

        assertTrue(((Dict) parsed.get("keyOperatorPairs")).isEmpty());
        assertTrue(((Dict) parsed.get("keyValuePairs")).isEmpty());
        assertEquals(wrapper.getTargetSql(), parsed.get("rawWhereSql"));
        assertEquals(wrapper.getTargetSql(), parsed.getStr("rawConditionSql"));
    }

    @Test
    void saveOrUpdateBatchMarksPayloadAsBatchChange() {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{List.of(Map.of("id", 7))});
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getName()).thenReturn("saveOrUpdateBatch");

        Dict source = ReflectionTestUtils.invokeMethod(saveBatchEntityAspect, "handlePointArgs", point);

        assertEquals("saveOrUpdateBatch", source.get("operation"));
    }

    @Test
    void exposesDistinctBatchChangeEventType() {
        assertEquals("sync_batch_change", GXModelEventNamingEnums.valueOf("SYNC_BATCH_CHANGE").getEventType());
        assertEquals("async_batch_change", GXModelEventNamingEnums.valueOf("ASYNC_BATCH_CHANGE").getEventType());
    }

    @Test
    void saveBatchSkipsPublishForEmptyBatch() throws Throwable {
        Method method = MethodAnnotatedBatchService.class.getMethod("saveBatch", Collection.class);
        ProceedingJoinPoint point = mockJoinPoint(new MethodAnnotatedBatchService(), method);
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{List.of()});

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> saveBatchEntityAspect.around(point));
            eventPublisherMock.verifyNoInteractions();
        }
    }

    @Test
    void saveBatchSkipsPublishWhenAllEntitiesAreNull() throws Throwable {
        Method method = MethodAnnotatedBatchService.class.getMethod("saveBatch", Collection.class);
        ProceedingJoinPoint point = mockJoinPoint(new MethodAnnotatedBatchService(), method);
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Arrays.asList(null, null)});

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> saveBatchEntityAspect.around(point));
            eventPublisherMock.verifyNoInteractions();
        }
    }

    @Test
    void writeWithoutDeclarationIsCountedAsSkipped() throws Throwable {
        ProceedingJoinPoint point = mockJoinPoint(new PlainWriteMapperImpl(),
                PlainWriteMapper.class.getMethod("insert", Object.class));
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 1)});
        long skippedBefore = GXMyBatisListenerMetrics.snapshot().skipped();

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> saveEntityAspect.around(point));
            eventPublisherMock.verifyNoInteractions();
        }
        assertEquals(skippedBefore + 1L, GXMyBatisListenerMetrics.snapshot().skipped(),
                "A short-circuited write must be visible in the skipped counter");
    }

    @Test
    void unannotatedBatchWriteShortCircuitsAndIsCountedAsSkipped() throws Throwable {
        Method method = PlainBatchService.class.getMethod("saveBatch", Collection.class);
        ProceedingJoinPoint point = mockJoinPoint(new PlainBatchService(), method);
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{List.of(Map.of("id", 1))});
        long skippedBefore = GXMyBatisListenerMetrics.snapshot().skipped();

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            assertDoesNotThrow(() -> saveBatchEntityAspect.around(point));
            eventPublisherMock.verifyNoInteractions();
        }
        assertEquals(skippedBefore + 1L, GXMyBatisListenerMetrics.snapshot().skipped(),
                "An unannotated batch write must be counted once, and only once");
    }

    @Test
    void batchMapperTypeCacheKeepsSingleDeliveryPerListener() throws Throwable {
        Method method = MethodAnnotatedBatchService.class.getMethod("saveBatch", Collection.class);
        ProceedingJoinPoint point = mockJoinPoint(new MethodAnnotatedBatchService(), method);
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{List.of(Map.of("id", 1))});

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            // The first invocation resolves the mapper type, the second is served by the cache; both
            // must deliver exactly one event to the single declared listener.
            assertDoesNotThrow(() -> saveBatchEntityAspect.around(point));
            assertDoesNotThrow(() -> saveBatchEntityAspect.around(point));
            eventPublisherMock.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(2));
        }
    }

    @Test
    void saveBatchKeepsEntityOrderInPayload() throws Exception {
        Method method = MethodAnnotatedBatchService.class.getMethod("saveBatch", Collection.class);
        ProceedingJoinPoint point = mockJoinPoint(new MethodAnnotatedBatchService(), method);
        List<Map<String, Object>> entities = List.of(
                Map.of("username", "alpha"),
                Map.of("username", "beta"),
                Map.of("username", "gamma")
        );
        Mockito.when(point.getArgs()).thenReturn(new Object[]{entities});

        Dict source = ReflectionTestUtils.invokeMethod(saveBatchEntityAspect, "handlePointArgs", point);

        @SuppressWarnings("unchecked")
        List<Dict> batchEntities = (List<Dict>) source.get("batchEntities");
        assertEquals(List.of("alpha", "beta", "gamma"),
                batchEntities.stream().map(entity -> entity.getStr("username")).toList());
    }

    @Test
    void saveBatchPublishesOperationAndPayloadKeysThroughSharedSupport() throws Throwable {
        Method method = MethodAnnotatedBatchService.class.getMethod("saveBatch", Collection.class);
        ProceedingJoinPoint point = mockJoinPoint(new MethodAnnotatedBatchService(), method);
        Mockito.when(point.proceed()).thenReturn(true);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{List.of(Map.of("id", 1))});

        AtomicReference<GXBaseEvent<?>> published = new AtomicReference<>();
        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisherMock.when(() -> GXEventPublisherUtils.publishEvent(any()))
                    .thenAnswer(invocation -> {
                        published.set(invocation.getArgument(0));
                        return null;
                    });
            assertDoesNotThrow(() -> saveBatchEntityAspect.around(point));
            eventPublisherMock.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
        }

        Dict source = publishedSource(published);
        assertEquals("saveBatch", source.getStr("operation"));
        assertEquals(List.of("operation", "entityData", "batchEntities"), List.copyOf(source.keySet()),
                "Delegating to the shared support must not change the batch payload keys");
        assertEquals(1, ((List<?>) source.get("batchEntities")).size());
    }

    @Test
    void batchEventReusesPayloadWhenSingleListenerIsDeclared() throws Exception {
        Method method = MethodAnnotatedBatchService.class.getMethod("saveBatch", Collection.class);
        List<Dict> payload = List.of(Dict.create().set("id", 1L), Dict.create().set("id", 2L));
        Dict source = Dict.create().set("operation", "saveBatch").set("batchEntities", payload);
        AtomicReference<GXBaseEvent<?>> captured = new AtomicReference<>();

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisherMock.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                captured.set(invocation.getArgument(0));
                return null;
            });
            eventPublisher.publish(new MethodAnnotatedBatchService(), null, method,
                    GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_BATCH_ENTITY,
                    source);
        }

        assertInstanceOf(GXMyBatisModelSaveBatchEntitySnapshotEvent.class, captured.get());
        assertSame(source, captured.get().getSource(), "Single-listener batch event must reuse the built payload");
    }

    @Test
    void multipleListenersShareOneReadOnlyBatchPayload() throws Exception {
        Method method = DualBatchService.class.getMethod("saveBatch", Collection.class);
        List<Dict> entities = List.of(Dict.create().set("id", 1L), Dict.create().set("id", 2L));
        Dict source = Dict.create().set("operation", "saveBatch").set("batchEntities", entities);
        List<GXBaseEvent<?>> events = new java.util.ArrayList<>();

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisherMock.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                events.add(invocation.getArgument(0));
                return null;
            });
            eventPublisher.publish(new DualBatchService(), null, method,
                    GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_BATCH_ENTITY,
                    source);
        }

        assertEquals(2, events.size());
        Dict sharedPayload = (Dict) events.get(0).getSource();
        assertSame(sharedPayload, events.get(1).getSource(), "Both listeners must share one snapshot");
        assertThrows(UnsupportedOperationException.class, () -> sharedPayload.set("operation", "hacked"));
        assertThrows(UnsupportedOperationException.class, () -> sharedPayload.values().remove("operation"));
        assertThrows(UnsupportedOperationException.class, () -> sharedPayload.keySet().remove("operation"));
        assertThrows(UnsupportedOperationException.class,
                () -> sharedPayload.entrySet().iterator().next().setValue("hacked"));
        @SuppressWarnings("unchecked")
        List<Dict> sharedEntities = (List<Dict>) sharedPayload.get("batchEntities");
        assertThrows(UnsupportedOperationException.class, () -> sharedEntities.getFirst().set("id", 99L));
        assertEquals("saveBatch", source.getStr("operation"), "Original payload must stay untouched");
    }

    @Test
    void largeBatchWriteKeepsItsResultAndDeliversOnce() throws Throwable {
        // 1,000 rows x 120 fields is more than the GXBaseEvent snapshot node budget: re-snapshotting
        // the batch payload inside the event constructor would throw and fail the already-written
        // batch, so the payload built by the aspect must be reused as-is (FR-006/FR-011).
        Method method = MethodAnnotatedBatchService.class.getMethod("saveBatch", Collection.class);
        ProceedingJoinPoint point = mockJoinPoint(new MethodAnnotatedBatchService(), method);
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{wideBatch(1_000, 120)});
        AtomicReference<GXBaseEvent<?>> captured = new AtomicReference<>();

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisherMock.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                captured.set(invocation.getArgument(0));
                return null;
            });
            Object result = assertDoesNotThrow(() -> saveBatchEntityAspect.around(point));
            assertEquals(1, result, "A successful batch write must keep its result");
            eventPublisherMock.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
        }

        assertInstanceOf(GXMyBatisModelSaveBatchEntitySnapshotEvent.class, captured.get());
        Dict payload = (Dict) captured.get().getSource();
        @SuppressWarnings("unchecked")
        List<Dict> batchEntities = (List<Dict>) payload.get("batchEntities");
        assertEquals(1_000, batchEntities.size());
        assertEquals(0, batchEntities.getFirst().getInt("id"));
        assertEquals(999, batchEntities.getLast().getInt("id"));
    }

    @Test
    void hundredThousandRowBatchWriteKeepsItsResultAndDeliversOnce() throws Throwable {
        Method method = MethodAnnotatedBatchService.class.getMethod("saveBatch", Collection.class);
        ProceedingJoinPoint point = mockJoinPoint(new MethodAnnotatedBatchService(), method);
        Mockito.when(point.proceed()).thenReturn(1);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{wideBatch(100_000, 1)});
        AtomicReference<GXBaseEvent<?>> captured = new AtomicReference<>();

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisherMock.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                captured.set(invocation.getArgument(0));
                return null;
            });
            Object result = assertDoesNotThrow(() -> saveBatchEntityAspect.around(point));
            assertEquals(1, result, "A successful batch write must keep its result");
            eventPublisherMock.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
        }

        Dict payload = (Dict) captured.get().getSource();
        @SuppressWarnings("unchecked")
        List<Dict> batchEntities = (List<Dict>) payload.get("batchEntities");
        assertEquals(100_000, batchEntities.size());
        assertEquals(0, batchEntities.getFirst().getInt("id"));
        assertEquals(99_999, batchEntities.getLast().getInt("id"));
    }

    private List<Map<String, Object>> wideBatch(int rows, int fields) {
        List<Map<String, Object>> entities = new ArrayList<>(rows);
        for (int row = 0; row < rows; row++) {
            Map<String, Object> entity = new HashMap<>(fields + 1);
            entity.put("id", row);
            for (int field = 0; field < fields; field++) {
                entity.put("field" + field, row + field);
            }
            entities.add(entity);
        }
        return entities;
    }

    @Test
    void strictSnapshotMarksUnsnapshotableValuesAndCountsDegrade() throws Exception {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setStrictSnapshot(true);
        GXMyBatisListenerEventPublisher strictPublisher = newEventPublisher(properties);
        long degradedBefore = GXMyBatisListenerMetrics.snapshot().degraded();
        Dict source = Dict.create().set("id", 1).set("payload", new Object());
        AtomicReference<Dict> captured = new AtomicReference<>();

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisherMock.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                GXBaseEvent<?> event = invocation.getArgument(0);
                captured.set((Dict) event.getSource());
                return null;
            });
            strictPublisher.publish(new ChildAnnotatedMapperImpl(),
                    ChildAnnotatedMapper.class.getMethod("insert", Object.class),
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY, source);
        }

        assertEquals(Boolean.TRUE, captured.get().getBool("snapshotDegraded"));
        assertTrue(((List<?>) captured.get().get("degradeReasons"))
                .contains(GXMyBatisListenerDegradeReasonEnums.UNSNAPSHOTABLE_VALUE));
        assertEquals(degradedBefore + 1L, GXMyBatisListenerMetrics.snapshot().degraded());
    }

    @Test
    void strictSnapshotFlagsTruncatedScanInsteadOfReportingClean() throws Exception {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setStrictSnapshot(true);
        GXMyBatisListenerEventPublisher strictPublisher = newEventPublisher(properties);
        long degradedBefore = GXMyBatisListenerMetrics.snapshot().degraded();
        // More nodes than the bounded scan budget allows, with the unsnapshotable value placed past
        // the budget: a silent "clean" verdict would hide it from degraded and from the logs (T101).
        List<Dict> rows = new ArrayList<>(2_500);
        for (int index = 0; index < 2_500; index++) {
            rows.add(Dict.create().set("id", index));
        }
        rows.add(Dict.create().set("payload", new Object()));
        Dict source = Dict.create().set("batchEntities", rows);

        try (MockedStatic<GXEventPublisherUtils> ignored = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            strictPublisher.publish(new ChildAnnotatedMapperImpl(),
                    ChildAnnotatedMapper.class.getMethod("insert", Object.class),
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY, source);
        }

        assertEquals(Boolean.TRUE, source.getBool("snapshotScanTruncated"),
                "The scan budget must be reported instead of being treated as a clean payload");
        assertNull(source.getBool("snapshotDegraded"),
                "A truncated scan must not claim the payload keeps references to unsnapshotable values");
        assertEquals(degradedBefore + 1L, GXMyBatisListenerMetrics.snapshot().degraded(),
                "A truncated scan must be counted so it stays distinguishable from a clean payload");
    }

    @Test
    void strictSnapshotDetectsNestedUnsnapshotableValues() throws Exception {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setStrictSnapshot(true);
        GXMyBatisListenerEventPublisher strictPublisher = newEventPublisher(properties);
        long degradedBefore = GXMyBatisListenerMetrics.snapshot().degraded();
        Object customValue = new Object();
        Dict source = Dict.create()
                .set("entityData", Dict.create().set("payload", customValue))
                .set("conditionFieldData", Dict.create().set("custom", customValue))
                .set("batchEntities", List.of(Dict.create().set("payload", customValue)));
        AtomicReference<Dict> captured = new AtomicReference<>();

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisherMock.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                GXBaseEvent<?> event = invocation.getArgument(0);
                captured.set((Dict) event.getSource());
                return null;
            });
            strictPublisher.publish(new ChildAnnotatedMapperImpl(),
                    ChildAnnotatedMapper.class.getMethod("insert", Object.class),
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY, source);
        }

        assertEquals(Boolean.TRUE, captured.get().getBool("snapshotDegraded"),
                "Nested unsnapshotable values must be detected");
        assertTrue(((List<?>) captured.get().get("degradeReasons"))
                .contains(GXMyBatisListenerDegradeReasonEnums.UNSNAPSHOTABLE_VALUE));
        assertEquals(degradedBefore + 1L, GXMyBatisListenerMetrics.snapshot().degraded());
    }

    @Test
    void strictSnapshotIgnoresNestedSnapshotableValues() throws Exception {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setStrictSnapshot(true);
        GXMyBatisListenerEventPublisher strictPublisher = newEventPublisher(properties);
        Dict source = Dict.create()
                .set("entityData", Dict.create().set("updatedAt", new Date(1_000L)).set("name", "alpha"))
                .set("batchEntities", List.of(Dict.create().set("id", 1L)));
        AtomicReference<Dict> captured = new AtomicReference<>();

        try (MockedStatic<GXEventPublisherUtils> eventPublisherMock = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisherMock.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                GXBaseEvent<?> event = invocation.getArgument(0);
                captured.set((Dict) event.getSource());
                return null;
            });
            strictPublisher.publish(new ChildAnnotatedMapperImpl(),
                    ChildAnnotatedMapper.class.getMethod("insert", Object.class),
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY, source);
        }

        assertNull(captured.get().get("snapshotDegraded"));
    }

    @Test
    void updateEntityCapturesUpdateByIdWithoutInventingACondition() {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 7, "status", "enabled")});
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getName()).thenReturn("updateById");

        Dict source = ReflectionTestUtils.invokeMethod(updateEntityAspect, "handlePointArgs", point);

        assertEquals("updateById", source.get("operation"));
        assertEquals(7, ((Dict) source.get("entityData")).get("id"));
        assertEquals(Dict.create(), source.get("keyValuePairs"));
        assertEquals(Dict.create(), source.get("keyOperatorPairs"));
    }

    @Test
    void syncListenerEventHandlersDoNotDeclareSpringTransactionBoundary() {
        AnnotationTransactionAttributeSource txAttributeSource = new AnnotationTransactionAttributeSource();
        Method[] listenerMethods = Arrays.stream(GXMyBatisSyncListener.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(EventListener.class))
                .toArray(Method[]::new);

        assertTrue(listenerMethods.length > 0);
        Arrays.stream(listenerMethods).forEach(method -> {
            TransactionAttribute transactionAttribute = txAttributeSource.getTransactionAttribute(method, GXMyBatisSyncListener.class);
            assertNull(transactionAttribute, method.getName());
        });
    }

    /**
     * A raw condition whose SQL rendering fails, simulating a condition that cannot be carried into
     * the payload (FR-009/FR-010).
     */
    private static GXConditionRaw throwingRawCondition() {
        return new GXConditionRaw("1 = 1") {
            @Override
            public String whereString() {
                throw new IllegalStateException("raw condition unavailable");
            }
        };
    }

    private Dict publishedSource(AtomicReference<GXBaseEvent<?>> published) {
        assertNotNull(published.get(), "A degraded payload must still be published");
        Object source = published.get().getSource();
        assertInstanceOf(Dict.class, source);
        return (Dict) source;
    }

    private ProceedingJoinPoint mockJoinPoint(Object target, Method method) {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(point.getTarget()).thenReturn(target);
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getMethod()).thenReturn(method);
        Mockito.when(signature.getName()).thenReturn(method.getName());
        return point;
    }

    @GXMyBatisListener(listenerClazz = FirstListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface DualMapperOne {
        Object insert(Object entity);
    }

    @GXMyBatisListener(listenerClazz = SecondListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface DualMapperTwo {
        Object insert(Object entity);
    }

    private static class DualMapperImpl implements DualMapperOne, DualMapperTwo {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    @GXMyBatisListener(listenerClazz = FirstListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface ParentAnnotatedMapper {
        Object insert(Object entity);
    }

    private interface ChildAnnotatedMapper extends ParentAnnotatedMapper {
    }

    private static class ChildAnnotatedMapperImpl implements ChildAnnotatedMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    @GXMyBatisListener(listenerClazz = FirstListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface UpdateFieldMapper {
        Object updateFieldByCondition(Object query, Object updateFields);
    }

    private static class UpdateFieldMapperImpl implements UpdateFieldMapper {
        @Override
        public Object updateFieldByCondition(Object query, Object updateFields) {
            return 1;
        }
    }

    @GXMyBatisListener(listenerClazz = SecondListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface DeleteSoftMapper {
        Object deleteSoftCondition(Object query, Object updateFields);
    }

    private static class DeleteSoftMapperImpl implements DeleteSoftMapper {
        @Override
        public Object deleteSoftCondition(Object query, Object updateFields) {
            return 1;
        }
    }

    private static class InsertTarget {
        public int insert(Object entity) {
            return 1;
        }

        public Object insert(Collection<?> entities) {
            return entities;
        }
    }


    @GXMyBatisListener(listenerClazz = FirstListener.class, runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)
    private interface AsyncSaveMapper {
        Object insert(Object entity);
    }

    private static class AsyncSaveMapperImpl implements AsyncSaveMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class MethodAnnotatedBatchService {
        @GXMyBatisListener(listenerClazz = FirstListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
        public Object saveBatch(Collection<?> entities) {
            return entities;
        }
    }

    private interface PlainWriteMapper {
        Object insert(Object entity);
    }

    private static class PlainWriteMapperImpl implements PlainWriteMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class PlainBatchService {
        public Object saveBatch(Collection<?> entities) {
            return entities;
        }
    }

    @GXMyBatisListener(listenerClazz = SharedBatchListenerOne.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface BatchSharedMapperOne {
        Object saveBatch(Collection<?> entities);
    }

    @GXMyBatisListener(listenerClazz = SharedBatchListenerTwo.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface BatchSharedMapperTwo {
        Object saveBatch(Collection<?> entities);
    }

    private static class DualBatchService implements BatchSharedMapperOne, BatchSharedMapperTwo {
        @Override
        public Object saveBatch(Collection<?> entities) {
            return entities;
        }
    }

    private static class SharedBatchListenerOne implements GXMybatisListenerService<Object> {
    }

    private static class SharedBatchListenerTwo implements GXMybatisListenerService<Object> {
    }

    @GXMyBatisListener(listenerClazz = FirstListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface DeleteMapper {
        Object deleteCondition(Object query);

    }

    private static class DeleteMapperImpl implements DeleteMapper {
        @Override
        public Object deleteCondition(Object query) {
            return 1;
        }

    }

    private static class FirstListener implements GXMybatisListenerService<Object> {
    }

    private static class SecondListener implements GXMybatisListenerService<Object> {
    }

}
