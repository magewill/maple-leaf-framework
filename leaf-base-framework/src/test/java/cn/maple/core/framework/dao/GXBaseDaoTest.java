package cn.maple.core.framework.dao;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.GXBaseData;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXBaseDaoTest {
    @Test
    void defaultMethodsDelegateToConfiguredTableName() {
        TestDao dao = new TestDao();
        List<GXCondition<?>> conditions = Collections.emptyList();
        List<GXUpdateField<?>> updateFields = Collections.emptyList();
        Dict extraData = Dict.create();

        assertEquals(2, dao.updateFieldByCondition(updateFields, conditions));
        assertTrue(dao.checkRecordIsExists(conditions));
        assertEquals(3, dao.deleteSoftCondition(updateFields, conditions, extraData));
        assertEquals(4, dao.deleteSoftCondition(conditions, extraData));
        assertEquals(5, dao.deleteCondition(conditions));

        GXPaginationResDto<Dict> pagination = dao.paginate(1, 20, conditions, Set.of("id"));
        assertSame(dao.pagination, pagination);
        assertEquals("demo_table", dao.lastTableName);
        assertEquals(1, dao.lastQuery.getPage());
        assertEquals(20, dao.lastQuery.getPageSize());
        assertEquals(conditions, dao.lastQuery.getCondition());
        assertEquals(Set.of("id"), dao.lastQuery.getColumns());
    }

    static class TestData extends GXBaseData {
    }

    static class TestDao implements GXBaseDao<TestData, Long> {
        private final GXPaginationResDto<Dict> pagination = new GXPaginationResDto<>(Collections.emptyList());
        private String lastTableName;
        private GXBaseQueryParamInnerDto lastQuery;

        @Override
        public Long updateOrCreate(TestData entity, List<GXCondition<?>> condition) {
            return 1L;
        }

        @Override
        public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> data, List<GXCondition<?>> condition) {
            this.lastTableName = tableName;
            return 2;
        }

        @Override
        public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
            this.lastTableName = tableName;
            return true;
        }

        @Override
        public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
            return Dict.create();
        }

        @Override
        public Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
            return Dict.create();
        }

        @Override
        public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
            return Collections.emptyList();
        }

        @Override
        public List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
            return Collections.emptyList();
        }

        @Override
        public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
            this.lastTableName = tableName;
            return 3;
        }

        @Override
        public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
            this.lastTableName = tableName;
            return 4;
        }

        @Override
        public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
            this.lastTableName = tableName;
            return 5;
        }

        @Override
        public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
            this.lastQuery = dbQueryParamInnerDto;
            return pagination;
        }

        @Override
        public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
            return pagination;
        }

        @Override
        public String getTableName() {
            return "demo_table";
        }
    }
}
