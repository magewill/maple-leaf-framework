package cn.maple.core.datasource.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.dao.GXMyBatisDao;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNULL;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.model.GXBaseModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GXMyBatisRepositoryTest {
    @Test
    void findByConditionWithColumnsDoesNotCopyTableNameIntoAlias() {
        TestDao dao = Mockito.mock(TestDao.class);
        TestRepository repository = new TestRepository(dao);
        when(dao.findByCondition(any(GXBaseQueryParamInnerDto.class))).thenReturn(List.of());

        repository.findByCondition("tenant_a.user", CollUtil.newArrayList(), Set.of("id"));

        ArgumentCaptor<GXBaseQueryParamInnerDto> captor = ArgumentCaptor.forClass(GXBaseQueryParamInnerDto.class);
        verify(dao).findByCondition(captor.capture());
        assertEquals("tenant_a.user", captor.getValue().getTableName());
        assertNull(captor.getValue().getTableNameAlias());
    }

    @Test
    void validateExistsConvertsOriginConditionToStructuredConditions() {
        TestDao dao = Mockito.mock(TestDao.class);
        TestRepository repository = new TestRepository(dao);
        when(dao.checkRecordIsExists(eq("user"), anyList())).thenReturn(true);

        GXValidateExistsDto dto = GXValidateExistsDto.builder()
                .tableName("user")
                .fieldName("name")
                .value("alice")
                .condition(Dict.create().set("tenantId", 7).set("deletedAt", null))
                .build();

        assertTrue(repository.validateExists(dto, null));

        ArgumentCaptor<List<GXCondition<?>>> captor = ArgumentCaptor.forClass(List.class);
        verify(dao).checkRecordIsExists(eq("user"), captor.capture());
        List<GXCondition<?>> conditions = captor.getValue();
        assertEquals(3, conditions.size());
        assertInstanceOf(GXConditionStrEQ.class, conditions.get(0));
        assertInstanceOf(GXConditionEQ.class, conditions.get(1));
        assertInstanceOf(GXConditionIsNULL.class, conditions.get(2));
        assertTrue(conditions.stream().noneMatch(GXConditionRaw.class::isInstance));
    }

    @Test
    void validateExistsRejectsUnsafeFieldNamesBeforeDaoCall() {
        TestDao dao = Mockito.mock(TestDao.class);
        TestRepository repository = new TestRepository(dao);
        GXValidateExistsDto dto = GXValidateExistsDto.builder()
                .tableName("user")
                .fieldName("name or 1=1")
                .value("alice")
                .build();

        assertThrows(GXBusinessException.class, () -> repository.validateExists(dto, null));
        verifyNoInteractions(dao);
    }

    static class TestModel extends GXBaseModel {
    }

    interface TestMapper extends GXBaseMapper<TestModel> {
    }

    static class TestDao extends GXMyBatisDao<TestMapper, TestModel, Long> {
    }

    static class TestRepository extends GXMyBatisRepository<TestMapper, TestModel, TestDao, Long> {
        TestRepository(TestDao dao) {
            this.baseDao = dao;
        }

        @Override
        public String getPrimaryKeyName() {
            return "id";
        }

        @Override
        public String getTableName() {
            return "user";
        }
    }
}
