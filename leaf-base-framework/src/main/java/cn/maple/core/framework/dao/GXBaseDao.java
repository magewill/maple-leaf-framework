package cn.maple.core.framework.dao;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.GXBaseData;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;

import java.io.Serializable;
import java.util.List;

public interface GXBaseDao<T extends GXBaseData, ID extends Serializable> {
    ID updateOrCreate(T entity, List<GXCondition<?>> condition);

    Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> data, List<GXCondition<?>> condition);

    boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition);

    Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData);

    Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData);

    Integer deleteCondition(String tableName, List<GXCondition<?>> condition);

    GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    String getTableName();
}
