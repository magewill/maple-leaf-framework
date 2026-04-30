package cn.maple.core.datasource.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXIgnoreDataFilterCondition;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.aspectj.lang.JoinPoint;

import java.util.*;
import java.util.stream.Collectors;

public interface GXDataScopeService {
    default Set<Number> getDeptIdLst() {
        return new HashSet<>();
    }

    default String getDeptCondition(String tableAlias, String[] deptIdFieldNames) {
        Set<Number> deptIdLst = getDeptIdLst();
        if (CollUtil.isNotEmpty(deptIdLst)) {
            String inStr = CollUtil.join(deptIdLst, ",");
            return CharSequenceUtil.format("{} in ({})", qualifyField(tableAlias, getDeptIdFieldName(deptIdFieldNames)), inStr);
        }
        return null;
    }

    default String getUserCondition(String tableAlias, String[] userIdFieldNames) {
        GXDataScopeService dataScopeService = GXSpringContextUtils.getBean(GXDataScopeService.class);
        if (ObjectUtil.isNull(dataScopeService)) {
            return null;
        }
        Long userId = getLoginUserId();
        //GXConditionEQ conditionEQ = new GXConditionEQ(tableAlias, getUserIdFieldName(userIdFieldNames), userId);
        return CharSequenceUtil.format("{} = {}", qualifyField(tableAlias, getUserIdFieldName(userIdFieldNames)), userId);
    }

    default Long getLoginUserId() {
        return 0L;
    }

    default boolean isSuperAdmin() {
        return false;
    }

    default String getSqlFilter(GXDataFilter dataFilter, JoinPoint point) {
        if (isSuperAdmin()) {
            return "";
        }

        boolean hasIgnoreDataFilterCondition = checkIgnoreDataFilter(point);
        GXDataScopeService dataScopeService = GXSpringContextUtils.getBean(GXDataScopeService.class);
        if (hasIgnoreDataFilterCondition || ObjectUtil.isNull(dataScopeService)) {
            return "";
        }

        String tableAlias = dataFilter.tableAlias();

        String[] deptIdFieldNames = dataFilter.deptIdFieldNames();
        String[] userIdFieldNames = dataFilter.userIdFieldNames();

        List<String> whereLst = new ArrayList<>();

        String userIdCondition = getUserCondition(tableAlias, userIdFieldNames);
        if (ObjectUtil.isNotNull(userIdCondition)) {
            //sqlFilter.append(userIdCondition);
            whereLst.add(userIdCondition);
        }

        String deptCondition = getDeptCondition(tableAlias, deptIdFieldNames);
        if (ObjectUtil.isNotNull(deptCondition)) {
            whereLst.add(deptCondition);
        }
        if (CollUtil.isNotEmpty(whereLst)) {
            return CharSequenceUtil.format(" ({}) ", CollUtil.join(whereLst, " or "));
        }
        return " (1 = 0) ";
    }

    private boolean checkIgnoreDataFilter(JoinPoint point) {
        List<Object> args = Arrays.asList(point.getArgs());
        if (CollUtil.isEmpty(List.of(args))) {
            return true;
        }
        List<Object> argsLst = args.stream().filter(t -> t.getClass().isAssignableFrom(GXBaseQueryParamInnerDto.class)).collect(Collectors.toList());
        if (CollUtil.isEmpty(argsLst)) {
            return true;
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
                if (!conditionLst.get(i).getClass().isAssignableFrom(GXIgnoreDataFilterCondition.class)) {
                    newConditionList.add(conditionLst.get(i));
                } else {
                    removeIndexLst.add(i);
                }
            }

            queryParams.setCondition(newConditionList);
            return CollUtil.isNotEmpty(removeIndexLst);
        }
        return false;
    }

    default String getDeptIdFieldName(String[] deptIdFieldNames) {
        if (deptIdFieldNames == null || deptIdFieldNames.length == 0 || CharSequenceUtil.isBlank(deptIdFieldNames[0])) {
            return "dept_id";
        }
        return deptIdFieldNames[0];
    }

    default String getUserIdFieldName(String[] userIdFieldNames) {
        if (userIdFieldNames == null || userIdFieldNames.length == 0 || CharSequenceUtil.isBlank(userIdFieldNames[0])) {
            return "user_id";
        }
        return userIdFieldNames[0];
    }

    private String qualifyField(String tableAlias, String fieldName) {
        if (CharSequenceUtil.isBlank(tableAlias)) {
            return fieldName;
        }
        return CharSequenceUtil.format("{}.{}", tableAlias, fieldName);
    }

    default GXBaseQueryParamInnerDto getGXBaseQueryParamInnerDto(JoinPoint point) {
        GXBaseQueryParamInnerDto dbQueryParamInnerDto = null;
        for (Object arg : point.getArgs()) {
            if (arg instanceof GXBaseQueryParamInnerDto) {
                dbQueryParamInnerDto = (GXBaseQueryParamInnerDto) arg;
                break;
            }
        }
        return dbQueryParamInnerDto;
    }
}
