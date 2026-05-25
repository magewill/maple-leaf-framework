package cn.maple.core.datasource.service;

import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.datasource.dto.GXDataFilterContext;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXIgnoreDataFilterCondition;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class GXDataFilterSqlResolverTest {
    @Test
    void resolveBuildsFilterWhenNoQueryParamExists() throws Exception {
        GXDataFilter annotation = getAnnotation("defaultFilter");
        JoinPoint point = Mockito.mock(JoinPoint.class);
        when(point.getArgs()).thenReturn(new Object[0]);

        GXDataFilterContext context = GXDataFilterSqlResolver.resolve(new TestDataScopeService(), annotation, point, "test");

        assertFalse(context.isIgnored());
        assertEquals(" (dept_id in (1,2)) ", context.getSqlFilter());
    }

    @Test
    void resolveIgnoresAndRemovesIgnoreCondition() throws Exception {
        GXDataFilter annotation = getAnnotation("defaultFilter");
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(List.of(new GXIgnoreDataFilterCondition()))
                .build();
        JoinPoint point = Mockito.mock(JoinPoint.class);
        when(point.getArgs()).thenReturn(new Object[]{queryParam});

        GXDataFilterContext context = GXDataFilterSqlResolver.resolve(new TestDataScopeService(), annotation, point, "test");

        assertTrue(context.isIgnored());
        assertEquals("", context.getSqlFilter());
        assertTrue(queryParam.getCondition().isEmpty());
    }

    @Test
    void resolveReturnsDenyAllWhenConditionsAreBlank() throws Exception {
        GXDataFilter annotation = getAnnotation("defaultFilter");
        JoinPoint point = Mockito.mock(JoinPoint.class);
        when(point.getArgs()).thenReturn(new Object[0]);

        GXDataFilterContext context = GXDataFilterSqlResolver.resolve(new BlankConditionDataScopeService(), annotation, point, "test");

        assertFalse(context.isIgnored());
        assertEquals(" (1 = 0) ", context.getSqlFilter());
    }

    @Test
    void resolveUsesConfiguredAliasAndFieldNames() throws Exception {
        GXDataFilter annotation = getAnnotation("customFilter");
        JoinPoint point = Mockito.mock(JoinPoint.class);
        when(point.getArgs()).thenReturn(new Object[0]);

        GXDataFilterContext context = GXDataFilterSqlResolver.resolve(new TestDataScopeService(), annotation, point, "test");

        assertEquals(" (u.org_id in (1,2)) ", context.getSqlFilter());
    }

    @GXDataFilter
    void defaultFilter() {
    }

    @GXDataFilter(tableAlias = "u", userIdFieldNames = "owner_id", deptIdFieldNames = "org_id")
    void customFilter() {
    }

    private GXDataFilter getAnnotation(String methodName) throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod(methodName);
        return method.getAnnotation(GXDataFilter.class);
    }

    private static class BlankConditionDataScopeService extends TestDataScopeService {
        @Override
        public String getDeptCondition(String tableAlias, String[] deptIdFieldNames) {
            return "   ";
        }

        @Override
        public String getUserCondition(String tableAlias, String[] userIdFieldNames) {
            return "";
        }
    }

    private static class TestDataScopeService implements GXDataScopeService {
        @Override
        public Set<Number> getDeptIdLst() {
            return new LinkedHashSet<>(List.of(1, 2));
        }

        @Override
        public Long getLoginUserId() {
            return 7L;
        }
    }
}
