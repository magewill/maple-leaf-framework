package cn.maple.mongodb.datasource.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.mongodb.datasource.context.GXMongoTemplateContext;
import cn.maple.mongodb.datasource.dao.GXMongoDao;
import cn.maple.mongodb.datasource.model.GXMongoModel;
import cn.maple.mongodb.datasource.repository.GXMongoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXMongoServiceImplTest {
    private final TestRepository repository = mock(TestRepository.class);

    private final TestService service = new TestService(repository);

    @AfterEach
    void tearDown() {
        GXMongoTemplateContext.clear();
    }

    @Test
    void shouldDelegateMongoTemplateContextOperations() throws Exception {
        Supplier<String> supplier = () -> "ok";
        Runnable runnable = () -> {
        };
        Callable<String> callable = () -> "ok";
        Executor executor = Runnable::run;
        ThreadFactory threadFactory = Thread::new;

        when(repository.executeWithMongoTemplate("archive", supplier)).thenReturn("ok");
        when(repository.wrapMongoTemplateContext(runnable)).thenReturn(runnable);
        when(repository.wrapMongoTemplateContext(supplier)).thenReturn(supplier);
        when(repository.wrapMongoTemplateContext(callable)).thenReturn(callable);
        when(repository.wrapMongoTemplateContext(executor)).thenReturn(executor);
        when(repository.wrapMongoTemplateContext(threadFactory)).thenReturn(threadFactory);

        assertEquals("ok", service.useMongoTemplate("archive", supplier));
        service.useMongoTemplate("archive", runnable);
        assertSame(runnable, service.wrapMongoTemplateContext(runnable));
        assertSame(supplier, service.wrapMongoTemplateContext(supplier));
        assertSame(callable, service.wrapMongoTemplateContext(callable));
        assertSame(executor, service.wrapMongoTemplateContext(executor));
        assertSame(threadFactory, service.wrapMongoTemplateContext(threadFactory));

        verify(repository, times(2)).executeWithMongoTemplate(eq("archive"), any(Supplier.class));
    }

    @Test
    void shouldRejectNullRunnableBeforeOpeningMongoTemplateScope() {
        assertThrows(IllegalArgumentException.class, () -> service.useMongoTemplate("archive", (Runnable) null));

        verify(repository, never()).executeWithMongoTemplate(eq("archive"), any(Supplier.class));
    }

    @Test
    void shouldUseRepositoryTableNameForBlankTableArguments() {
        List<GXCondition<?>> conditions = List.of(new GXConditionEQ("test_collection", "id", 1));
        when(repository.getTableName()).thenReturn("test_collection");
        when(repository.checkRecordIsExists("test_collection", conditions)).thenReturn(true);

        assertTrue(service.checkRecordIsExists("", conditions));

        verify(repository).checkRecordIsExists("test_collection", conditions);
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingData() {
        List<GXCondition<?>> conditions = List.of(new GXConditionEQ("test_collection", "id", 1));
        List<GXUpdateField<?>> fields = List.of(new TestUpdateField("name", "neo"));
        when(repository.getTableName()).thenReturn("test_collection");
        when(repository.checkRecordIsExists("test_collection", conditions)).thenReturn(false);

        assertEquals(GXCommonConstant.DB_RECORD_NOT_FOUND, service.updateFieldByCondition("", fields, conditions));

        verify(repository, never()).updateFieldByCondition(any(), any(), any());
    }

    @Test
    void shouldMapFindByConditionWithCustomRowMapperAndDefaultTableName() {
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .columns(CollUtil.newHashSet("*"))
                .condition(List.of(new GXConditionStrEQ("test_collection", "name", "neo")))
                .build();
        when(repository.getTableName()).thenReturn("test_collection");
        when(repository.findByCondition(queryParam)).thenReturn(List.of(Dict.create().set("name", "neo")));

        List<String> names = service.findByCondition(queryParam, dict -> dict.getStr("name"));

        assertEquals(List.of("neo"), names);
        assertEquals("test_collection", queryParam.getTableName());
    }

    @Test
    void shouldReturnNullWhenFindOneGetsEmptyDict() {
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .tableName("test_collection")
                .columns(CollUtil.newHashSet("*"))
                .build();
        when(repository.findOneByCondition(queryParam)).thenReturn(Dict.create());

        assertNull(service.findOneByCondition(queryParam, dict -> dict.getStr("name")));
    }

    @Test
    void shouldReadMongoIdForSingleFieldQueries() {
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .tableName("test_collection")
                .columns(CollUtil.newHashSet("id"))
                .build();
        when(repository.findOneByCondition(queryParam)).thenReturn(Dict.create().set("_id", "mongo-id"));

        assertEquals("mongo-id", service.findSingleFieldByCondition(queryParam, String.class));
    }

    @Test
    void shouldFilterNullValuesWhenReadingSingleFieldList() {
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .tableName("test_collection")
                .columns(CollUtil.newHashSet("userName"))
                .build();
        when(repository.findByCondition(queryParam)).thenReturn(List.of(
                Dict.create().set("user_name", "neo"),
                Dict.create().set("userName", "trinity"),
                Dict.create()));

        assertEquals(List.of("neo", "trinity"), service.findSingleFieldLstByCondition(queryParam, String.class));
    }

    @Test
    void shouldCountByPaginationTotal() {
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder().build();
        when(repository.getTableName()).thenReturn("test_collection");
        when(repository.paginate(queryParam)).thenReturn(new GXPaginationResDto<>(new ArrayList<>(), 12, 1, 1));

        assertEquals(12L, service.countByCondition(queryParam));
        assertEquals("test_collection", queryParam.getTableName());
        assertEquals(1, queryParam.getPage());
        assertEquals(1, queryParam.getPageSize());
    }

    @Test
    void shouldRejectUnsupportedOperationsAndEmptyConditions() {
        assertThrows(GXBusinessException.class, () -> service.checkRecordIsExists(List.of()));
        assertThrows(GXBusinessException.class, () -> service.deleteCondition(List.of()));
        assertThrows(GXBusinessException.class, () -> service.deleteSoftCondition(List.of()));
        assertThrows(GXBusinessException.class, () -> service.findByCallMapperMethod("selectAll"));
        assertThrows(GXBusinessException.class, () -> service.findOneByCallMapperMethod("selectOne"));
    }

    @Test
    void shouldReturnZeroWhenDeletingNullId() {
        assertEquals(0, service.deleteById(null));
    }

    @Test
    void shouldWrapRepositoryContextForRunnableExecution() {
        TestRepository realRepository = new TestRepository();
        TestService realService = new TestService(realRepository);
        AtomicBoolean ran = new AtomicBoolean(false);
        GXMongoTemplateContext.push("archiveMongoTemplate");

        Runnable wrapped = realService.wrapMongoTemplateContext(() -> {
            ran.set(true);
            assertEquals("archiveMongoTemplate", GXMongoTemplateContext.peek());
        });
        GXMongoTemplateContext.clear();
        wrapped.run();

        assertTrue(ran.get());
        assertNull(GXMongoTemplateContext.peek());
    }

    private static class TestService extends GXMongoServiceImpl<TestRepository, TestModel, TestDao, TestRes, String> {
        private TestService(TestRepository repository) {
            this.repository = repository;
        }
    }

    private static class TestRepository extends GXMongoRepository<TestModel, TestDao, String> {
        @Override
        public String getTableName() {
            return "test_collection";
        }
    }

    private interface TestDao extends GXMongoDao<TestModel, String> {
    }

    private static class TestModel extends GXMongoModel {
    }

    private static class TestRes extends GXBaseDBResDto {
    }

    private static class TestUpdateField extends GXUpdateField<Object> {
        private TestUpdateField(String fieldName, Object value) {
            super("", fieldName, value);
        }

        @Override
        public Object getFieldValue() {
            return value;
        }
    }
}
