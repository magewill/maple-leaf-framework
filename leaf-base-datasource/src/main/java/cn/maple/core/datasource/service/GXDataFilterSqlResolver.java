package cn.maple.core.datasource.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.datasource.dto.GXDataFilterContext;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXIgnoreDataFilterCondition;
import org.aspectj.lang.JoinPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class GXDataFilterSqlResolver {
    private GXDataFilterSqlResolver() {
    }

    public static GXDataFilterContext resolve(GXDataScopeService dataScopeService, GXDataFilter dataFilter, JoinPoint point, String methodName) {
        if (dataScopeService.isSuperAdmin()) {
            return new GXDataFilterContext("", dataFilter, methodName, true);
        }

        boolean ignored = checkIgnoreDataFilter(point);
        if (ignored) {
            return new GXDataFilterContext("", dataFilter, methodName, true);
        }

        String tableAlias = dataFilter.tableAlias();
        String[] deptIdFieldNames = dataFilter.deptIdFieldNames();
        String[] userIdFieldNames = dataFilter.userIdFieldNames();

        List<String> whereLst = new ArrayList<>();

        String userIdCondition = dataScopeService.getUserCondition(tableAlias, userIdFieldNames);
        if (ObjectUtil.isNotNull(userIdCondition)) {
            whereLst.add(userIdCondition);
        }

        String deptCondition = dataScopeService.getDeptCondition(tableAlias, deptIdFieldNames);
        if (ObjectUtil.isNotNull(deptCondition)) {
            whereLst.add(deptCondition);
        }

        if (CollUtil.isNotEmpty(whereLst)) {
            return new GXDataFilterContext(CharSequenceUtil.format(" ({}) ", CollUtil.join(whereLst, " or ")), dataFilter, methodName, false);
        }
        return new GXDataFilterContext(" (1 = 0) ", dataFilter, methodName, false);
    }

    private static boolean checkIgnoreDataFilter(JoinPoint point) {
        Object[] pointArgs = point.getArgs();
        if (pointArgs == null || pointArgs.length == 0) {
            return false;
        }
        List<Object> args = Arrays.stream(pointArgs).filter(Objects::nonNull).collect(Collectors.toList());
        if (CollUtil.isEmpty(args)) {
            return false;
        }
        List<Object> argsLst = args.stream().filter(t -> GXBaseQueryParamInnerDto.class.isAssignableFrom(t.getClass())).collect(Collectors.toList());
        if (CollUtil.isEmpty(argsLst)) {
            return false;
        }
        GXBaseQueryParamInnerDto queryParams = (GXBaseQueryParamInnerDto) argsLst.getFirst();
        if (queryParams.isIgnoreDataFilter()) {
            return true;
        }
        List<GXCondition<?>> conditionLst = queryParams.getCondition();
        if (CollUtil.isNotEmpty(conditionLst)) {
            List<GXCondition<?>> newConditionList = new ArrayList<>();
            List<Integer> removeIndexLst = CollUtil.newArrayList();

            for (int i = 0, len = conditionLst.size(); i < len; i++) {
                GXCondition<?> condition = conditionLst.get(i);
                if (condition == null || !GXIgnoreDataFilterCondition.class.isAssignableFrom(condition.getClass())) {
                    newConditionList.add(condition);
                } else {
                    removeIndexLst.add(i);
                }
            }

            queryParams.setCondition(newConditionList);
            return CollUtil.isNotEmpty(removeIndexLst);
        }
        return false;
    }
}
