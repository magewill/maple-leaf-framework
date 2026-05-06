package cn.maple.core.framework.ddd.repository;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXBaseRepositoryTest {
    @Test
    void defaultMethodsDelegateToTableNameBasedMethods() {
        TestRepository repository = new TestRepository();
        TestEntity entity = new TestEntity();
        List<GXCondition<?>> conditions = Collections.emptyList();
        List<GXUpdateField<?>> updateFields = Collections.emptyList();
        Dict extraData = Dict.create();
        Set<String> columns = Set.of("id");

        assertEquals(1L, repository.updateOrCreate(entity));
        assertSame(repository.list, repository.findByCondition(conditions));
        assertSame(repository.list, repository.findByCondition(conditions, columns));
        assertSame(repository.list, repository.findByCondition(columns));
        assertSame(repository.one, repository.findOneByCondition(conditions));
        assertSame(repository.one, repository.findOneByCondition(conditions, columns));
        assertSame(repository.one, repository.findOneById(1L));
        assertSame(repository.one, repository.findOneById(1L, columns));
        assertSame(repository.pagination, repository.paginate(1, 10, conditions, columns));
        assertEquals(2, repository.deleteSoftCondition(conditions, extraData));
        assertEquals(3, repository.deleteSoftCondition(updateFields, conditions, extraData));
        assertEquals(4, repository.deleteCondition(conditions));
        assertTrue(repository.checkRecordIsExists(conditions));
        assertEquals(6, repository.updateFieldByCondition(updateFields, conditions));
        assertEquals("id", repository.getPrimaryKeyName(entity));
        assertEquals("demo_table", repository.getTableName(entity));
        assertEquals("demo_table", repository.lastTableName);
    }

    @Test
    void defaultMethodsRejectNullValues() {
        TestRepository repository = new TestRepository();

        assertThrows(IllegalArgumentException.class, () -> repository.updateOrCreate(null));
        assertThrows(IllegalArgumentException.class, () -> repository.findByCondition((List<GXCondition<?>>) null));
        assertThrows(IllegalArgumentException.class, () -> repository.findOneById(null));
        assertThrows(IllegalArgumentException.class, () -> repository.deleteCondition(null));
        assertThrows(IllegalArgumentException.class, () -> repository.getTableName(null));
    }

    static class TestEntity {
    }

    static class TestRepository implements GXBaseRepository<TestEntity, Long> {
        private final List<Dict> list = Collections.singletonList(Dict.create().set("id", 1L));
        private final Dict one = Dict.create().set("id", 1L);
        private final GXPaginationResDto<Dict> pagination = new GXPaginationResDto<>(list);
        private String lastTableName;

        @Override
        public Long updateOrCreate(TestEntity entity, List<GXCondition<?>> condition) {
            return 1L;
        }

        @Override
        public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
            return list;
        }

        @Override
        public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition) {
            lastTableName = tableName;
            return list;
        }

        @Override
        public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
            lastTableName = tableName;
            return list;
        }

        @Override
        public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
            return one;
        }

        @Override
        public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition) {
            lastTableName = tableName;
            return one;
        }

        @Override
        public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
            lastTableName = tableName;
            return one;
        }

        @Override
        public Dict findOneById(String tableName, Long id, Set<String> columns) {
            lastTableName = tableName;
            return one;
        }

        @Override
        public Dict findOneById(String tableName, Long id) {
            lastTableName = tableName;
            return one;
        }

        @Override
        public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
            return pagination;
        }

        @Override
        public GXPaginationResDto<Dict> paginate(String tableName, Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns) {
            lastTableName = tableName;
            return pagination;
        }

        @Override
        public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
            lastTableName = tableName;
            return 2;
        }

        @Override
        public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
            lastTableName = tableName;
            return 3;
        }

        @Override
        public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
            lastTableName = tableName;
            return 4;
        }

        @Override
        public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
            lastTableName = tableName;
            return true;
        }

        @Override
        public boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext constraintValidatorContext) {
            return true;
        }

        @Override
        public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
            lastTableName = tableName;
            return 6;
        }

        @Override
        public String getPrimaryKeyName() {
            return "id";
        }

        @Override
        public String getTableName() {
            return "demo_table";
        }
    }
}
