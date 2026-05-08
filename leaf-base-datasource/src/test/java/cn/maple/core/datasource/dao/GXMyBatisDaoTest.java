package cn.maple.core.datasource.dao;

import cn.maple.core.datasource.mapper.GXBaseMapper;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.model.GXBaseModel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GXMyBatisDaoTest {
    @Test
    void checkRecordIsExistsRejectsEmptyConditionBeforeMapperCall() {
        TestDao dao = new TestDao();
        TestMapper mapper = Mockito.mock(TestMapper.class);
        ReflectionTestUtils.setField(dao, "baseMapper", mapper);

        assertThrows(GXBusinessException.class, () -> dao.checkRecordIsExists("user", List.of()));
        verifyNoInteractions(mapper);
    }

    @Test
    void findByConditionDoesNotMutateCallerQueryParamWhenDefaultTableIsApplied() {
        TestDao dao = new TestDao();
        TestMapper mapper = Mockito.mock(TestMapper.class);
        ReflectionTestUtils.setField(dao, "baseMapper", mapper);
        when(mapper.findByCondition(any(GXBaseQueryParamInnerDto.class))).thenReturn(List.of());

        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder().columns(Set.of("id")).build();

        dao.findByCondition(query);

        ArgumentCaptor<GXBaseQueryParamInnerDto> captor = ArgumentCaptor.forClass(GXBaseQueryParamInnerDto.class);
        Mockito.verify(mapper).findByCondition(captor.capture());
        assertNotSame(query, captor.getValue());
        assertEquals("test_model", captor.getValue().getTableName());
        assertNull(query.getTableName());
        assertEquals(Set.of("id"), query.getColumns());
    }

    @Test
    void paginateDoesNotMutateCallerColumnsWhenDefaultColumnsAreApplied() {
        TestDao dao = new TestDao();
        TestMapper mapper = Mockito.mock(TestMapper.class);
        ReflectionTestUtils.setField(dao, "baseMapper", mapper);
        when(mapper.paginate(any(IPage.class), any(GXBaseQueryParamInnerDto.class))).thenReturn(Collections.emptyList());

        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .tableName("test_model")
                .build();

        dao.paginate(query);

        ArgumentCaptor<GXBaseQueryParamInnerDto> captor = ArgumentCaptor.forClass(GXBaseQueryParamInnerDto.class);
        Mockito.verify(mapper).paginate(any(IPage.class), captor.capture());
        assertNotSame(query, captor.getValue());
        assertEquals(Set.of("*"), captor.getValue().getColumns());
        assertNull(query.getColumns());
    }

    static class TestModel extends GXBaseModel {
    }

    interface TestMapper extends GXBaseMapper<TestModel> {
    }

    static class TestDao extends GXMyBatisDao<TestMapper, TestModel, Long> {
        @Override
        public String getTableName() {
            return "test_model";
        }
    }
}
