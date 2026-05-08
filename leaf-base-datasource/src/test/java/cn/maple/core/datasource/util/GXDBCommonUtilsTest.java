package cn.maple.core.datasource.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNULL;
import cn.maple.core.framework.dto.inner.condition.GXConditionIsNotNULL;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.ibatis.jdbc.SQL;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXDBCommonUtilsTest {
    @Test
    void assemblyUpdateWrapperSupportsNullConditions() {
        UpdateWrapper<Object> wrapper = assertDoesNotThrow(() -> GXDBCommonUtils.assemblyUpdateWrapper(CollUtil.newArrayList(
                null,
                new GXConditionIsNULL("user", "deletedAt"),
                new GXConditionIsNotNULL("user", "createdAt")
        )));

        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("deleted_at IS NULL"), sqlSegment);
        assertTrue(sqlSegment.contains("created_at IS NOT NULL"), sqlSegment);
    }

    @Test
    void assemblyUpdateWrapperRejectsNullValueForNormalCondition() {
        assertThrows(GXBusinessException.class,
                () -> GXDBCommonUtils.assemblyUpdateWrapper(CollUtil.newArrayList(new GXConditionEQ("user", "nickname", null))));
    }

    @Test
    void checkSQLInjectionDoesNotSwallowFallbackDetection() {
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBCommonUtils.checkSQLInjection("select * from users", "test", true));
    }

    @Test
    void addSearchConditionIgnoresNullSourceData() {
        Dict request = Dict.create().set(GXBuilderConstant.SEARCH_CONDITION_NAME, Dict.create().set("name", "alice"));

        Dict result = GXDBCommonUtils.addSearchCondition(request, null, false);

        assertEquals("alice", result.getStr("name"));
    }

    @Test
    void utilMethodsRejectNullInputsWithBusinessException() {
        assertThrows(GXBusinessException.class, () -> GXDBCommonUtils.addSearchCondition(null, "name", "alice", false));
        assertThrows(GXBusinessException.class, () -> GXDBCommonUtils.convertPageToPaginationResDto(null));

        Set<Object> values = new LinkedHashSet<>();
        values.add(null);
        assertThrows(GXBusinessException.class, () -> GXDBCommonUtils.generateJSONSearchExpression("tags", "$", values));
    }

    @Test
    void assemblySqlObjectConditionRendersNullAsIsNull() {
        SQL sql = new SQL().SELECT("*").FROM("user");

        GXDBCommonUtils.assemblySqlObjectCondition(sql, Dict.create().set("deleted_at", null));

        assertTrue(sql.toString().contains("deleted_at IS NULL"), sql.toString());
    }

    @Test
    void safeIdentifierHelpersRejectStructuralText() {
        assertThrows(GXSqlInjectionException.class, () -> GXDBCommonUtils.safeTableName("user account"));
        assertThrows(GXSqlInjectionException.class, () -> GXDBCommonUtils.safeTableAlias("tenant.user"));
        assertThrows(GXSqlInjectionException.class, () -> GXDBCommonUtils.safeColumnName("user name"));

        assertEquals("tenant.user", GXDBCommonUtils.safeTableName("tenant.user"));
        assertEquals("u", GXDBCommonUtils.safeTableAlias("u"));
        assertEquals("u.name", GXDBCommonUtils.safeColumnName("u.name"));
    }
}
