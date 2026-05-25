package cn.maple.core.datasource.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.aspectj.lang.JoinPoint;

import java.util.HashSet;
import java.util.Set;

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
        return GXDataFilterSqlResolver.resolve(this, dataFilter, point, null).getSqlFilter();
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
