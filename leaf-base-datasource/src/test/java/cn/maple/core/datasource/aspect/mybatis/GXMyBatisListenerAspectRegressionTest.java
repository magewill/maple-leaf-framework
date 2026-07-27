package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.listener.GXMyBatisSyncListener;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.field.GXUpdateNumberField;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.event.EventListener;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GXMyBatisListenerAspectRegressionTest {
    private final GXMyBatisPlusSaveEntityAspect saveEntityAspect = new GXMyBatisPlusSaveEntityAspect();
    private final GXMyBatisPlusUpdateFieldAspect updateFieldAspect = new GXMyBatisPlusUpdateFieldAspect();
    private final GXMyBatisPlusDeleteSoftAspect deleteSoftAspect = new GXMyBatisPlusDeleteSoftAspect();
    private final GXMyBatisPlusUpdateEntityAspect updateEntityAspect = new GXMyBatisPlusUpdateEntityAspect();
    private final GXMyBatisPlusSaveBatchEntityAspect saveBatchEntityAspect = new GXMyBatisPlusSaveBatchEntityAspect();
    private final GXMyBatisPlusDeleteAspect deleteAspect = new GXMyBatisPlusDeleteAspect();

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

    private ProceedingJoinPoint mockJoinPoint(Object target, Method method) {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(point.getTarget()).thenReturn(target);
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getMethod()).thenReturn(method);
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
