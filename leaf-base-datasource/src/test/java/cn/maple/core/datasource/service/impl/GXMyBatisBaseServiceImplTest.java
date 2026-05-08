package cn.maple.core.datasource.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.dao.GXMyBatisDao;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import cn.maple.core.datasource.repository.GXMyBatisRepository;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.core.framework.model.GXBaseModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GXMyBatisBaseServiceImplTest {
    @Test
    void findByConditionRejectsNullQueryParamBeforeRepositoryCall() {
        TestRepository repository = Mockito.mock(TestRepository.class);
        TestService service = new TestService();
        ReflectionTestUtils.setField(service, "repository", repository);

        assertThrows(cn.maple.core.framework.exception.GXBusinessException.class, () -> service.findByCondition((GXBaseQueryParamInnerDto) null));
        verifyNoInteractions(repository);
    }

    @Test
    void findByConditionDoesNotMutateCallerQueryParamWhenDefaultTableIsApplied() {
        TestRepository repository = Mockito.mock(TestRepository.class);
        TestService service = new TestService();
        ReflectionTestUtils.setField(service, "repository", repository);
        when(repository.getTableName()).thenReturn("user");
        when(repository.findByCondition(any(GXBaseQueryParamInnerDto.class))).thenReturn(List.of(Dict.create().set("id", 1)));

        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();
        List<Long> ids = service.findByCondition(query, dict -> dict.getLong("id"));

        ArgumentCaptor<GXBaseQueryParamInnerDto> captor = ArgumentCaptor.forClass(GXBaseQueryParamInnerDto.class);
        verify(repository).findByCondition(captor.capture());
        assertEquals(List.of(1L), ids);
        assertNotSame(query, captor.getValue());
        assertEquals("user", captor.getValue().getTableName());
        assertNull(query.getTableName());
        assertEquals(Set.of("id"), query.getColumns());
    }

    @Test
    void findSingleFieldByConditionDoesNotMutateCallerLimitOrTableName() {
        TestRepository repository = Mockito.mock(TestRepository.class);
        TestService service = new TestService();
        ReflectionTestUtils.setField(service, "repository", repository);
        when(repository.getTableName()).thenReturn("user");
        when(repository.findOneByCondition(any(GXBaseQueryParamInnerDto.class))).thenReturn(Dict.create().set("id", 7));

        GXBaseQueryParamInnerDto query = GXBaseQueryParamInnerDto.builder()
                .columns(CollUtil.newLinkedHashSet("id"))
                .build();
        Long id = service.findSingleFieldByCondition(query, Long.class);

        ArgumentCaptor<GXBaseQueryParamInnerDto> captor = ArgumentCaptor.forClass(GXBaseQueryParamInnerDto.class);
        verify(repository).findOneByCondition(captor.capture());
        assertEquals(7L, id);
        assertNotSame(query, captor.getValue());
        assertEquals("user", captor.getValue().getTableName());
        assertEquals(1, captor.getValue().getLimit());
        assertNull(query.getTableName());
        assertNull(query.getLimit());
    }

    static class TestModel extends GXBaseModel {
    }

    interface TestMapper extends GXBaseMapper<TestModel> {
    }

    static class TestDao extends GXMyBatisDao<TestMapper, TestModel, Long> {
    }

    static class TestRepository extends GXMyBatisRepository<TestMapper, TestModel, TestDao, Long> {
    }

    static class TestRes extends GXBaseDBResDto {
    }

    static class TestService extends GXMyBatisBaseServiceImpl<TestRepository, TestMapper, TestModel, TestDao, TestRes, Long> {
    }
}
