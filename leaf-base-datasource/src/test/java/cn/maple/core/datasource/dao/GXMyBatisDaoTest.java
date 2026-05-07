package cn.maple.core.datasource.dao;

import cn.maple.core.datasource.mapper.GXBaseMapper;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.model.GXBaseModel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

class GXMyBatisDaoTest {
    @Test
    void checkRecordIsExistsRejectsEmptyConditionBeforeMapperCall() {
        TestDao dao = new TestDao();
        TestMapper mapper = Mockito.mock(TestMapper.class);
        ReflectionTestUtils.setField(dao, "baseMapper", mapper);

        assertThrows(GXBusinessException.class, () -> dao.checkRecordIsExists("user", List.of()));
        verifyNoInteractions(mapper);
    }

    static class TestModel extends GXBaseModel {
    }

    interface TestMapper extends GXBaseMapper<TestModel> {
    }

    static class TestDao extends GXMyBatisDao<TestMapper, TestModel, Long> {
    }
}
