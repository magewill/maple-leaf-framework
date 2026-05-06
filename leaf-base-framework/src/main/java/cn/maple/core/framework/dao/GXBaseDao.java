package cn.maple.core.framework.dao;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.GXBaseData;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface GXBaseDao<T extends GXBaseData, ID extends Serializable> {
    ID updateOrCreate(T entity, List<GXCondition<?>> condition);

    Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> data, List<GXCondition<?>> condition);

    default Integer updateFieldByCondition(List<GXUpdateField<?>> data, List<GXCondition<?>> condition) {
        return updateFieldByCondition(getTableName(), data, condition);
    }

    boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition);

    default boolean checkRecordIsExists(List<GXCondition<?>> condition) {
        return checkRecordIsExists(getTableName(), condition);
    }

    Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData);

    default Integer deleteSoftCondition(List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        return deleteSoftCondition(getTableName(), updateFieldList, condition, extraData);
    }

    Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData);

    default Integer deleteSoftCondition(List<GXCondition<?>> condition, Dict extraData) {
        return deleteSoftCondition(getTableName(), condition, extraData);
    }

    Integer deleteCondition(String tableName, List<GXCondition<?>> condition);

    default Integer deleteCondition(List<GXCondition<?>> condition) {
        return deleteCondition(getTableName(), condition);
    }

    GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    default GXPaginationResDto<Dict> paginate(Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .tableName(getTableName())
                .page(page)
                .pageSize(pageSize)
                .condition(condition == null ? Collections.emptyList() : condition)
                .columns(columns)
                .build();
        return paginate(queryParam);
    }

    default GXPaginationResDto<Dict> paginate(Integer page, Integer pageSize, List<GXCondition<?>> condition) {
        return paginate(page, pageSize, condition, null);
    }

    String getTableName();
}
