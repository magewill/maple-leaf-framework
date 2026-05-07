package cn.maple.core.datasource.util;

import cn.hutool.core.collection.CollUtil;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNULL;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNotNULL;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXDBCommonUtilsTest {
    @Test
    void assemblyUpdateWrapperSupportsNullConditions() {
        UpdateWrapper<Object> wrapper = assertDoesNotThrow(() -> GXDBCommonUtils.assemblyUpdateWrapper(CollUtil.newArrayList(
                new GXConditionIsNULL("user", "deletedAt"),
                new GXConditionIsNotNULL("user", "createdAt")
        )));

        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("deleted_at IS NULL"), sqlSegment);
        assertTrue(sqlSegment.contains("created_at IS NOT NULL"), sqlSegment);
    }
}
