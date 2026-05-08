package cn.maple.core.datasource.util;

import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;

public final class GXQueryParamUtils {
    private GXQueryParamUtils() {
    }

    public static GXBaseQueryParamInnerDto copy(GXBaseQueryParamInnerDto source) {
        if (source == null) {
            return null;
        }
        return GXBaseQueryParamInnerDto.builder()
                .tableName(source.getTableName())
                .tableNameAlias(source.getTableNameAlias())
                .page(source.getPage())
                .pageSize(source.getPageSize())
                .condition(source.getCondition())
                .columns(Objects.isNull(source.getColumns()) ? null : new LinkedHashSet<>(source.getColumns()))
                .orderByField(Objects.isNull(source.getOrderByField()) ? null : new LinkedHashMap<>(source.getOrderByField()))
                .groupByField(Objects.isNull(source.getGroupByField()) ? null : new LinkedHashSet<>(source.getGroupByField()))
                .methodName(source.getMethodName())
                .having(Objects.isNull(source.getHaving()) ? null : new LinkedHashSet<>(source.getHaving()))
                .copyOptions(source.getCopyOptions())
                .limit(source.getLimit())
                .joins(source.getJoins())
                .extraData(source.getExtraData())
                .rawSQL(source.getRawSQL())
                .ignoreDataFilter(source.isIgnoreDataFilter())
                .paginateCount(source.isPaginateCount())
                .paramMap(Objects.isNull(source.getParamMap()) ? new HashMap<>() : new HashMap<>(source.getParamMap()))
                .build();
    }
}
