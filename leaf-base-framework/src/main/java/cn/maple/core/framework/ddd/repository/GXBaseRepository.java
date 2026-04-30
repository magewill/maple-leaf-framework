package cn.maple.core.framework.ddd.repository;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import jakarta.validation.ConstraintValidatorContext;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface GXBaseRepository<T, ID extends Serializable> {
    ID updateOrCreate(T entity, List<GXCondition<?>> condition);

    ID updateOrCreate(T entity);

    List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    default List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("请实现findByCondition方法");
    }

    List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition);

    List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns);

    default List<Dict> findByCondition(List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        return findByCondition(getTableName(), condition);
    }

    default List<Dict> findByCondition() {
        return findByCondition(getTableName(), Collections.emptyList());
    }

    Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    default Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("请实现findOneByCondition方法");
    }

    Dict findOneByCondition(String tableName, List<GXCondition<?>> condition);

    default Dict findOneByCondition(List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        return findOneByCondition(getTableName(), condition);
    }

    Dict findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns);

    Dict findOneById(String tableName, ID id, Set<String> columns);

    Dict findOneById(String tableName, ID id);

    default Dict findOneById(ID id) {
        Assert.notNull(id, "ID不能为null");
        return findOneById(getTableName(), id);
    }

    GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    default GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("请实现paginate方法");
    }

    GXPaginationResDto<Dict> paginate(String tableName, Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns);

    Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData);

    Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData);

    Integer deleteCondition(String tableName, List<GXCondition<?>> condition);

    boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition);

    boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext constraintValidatorContext);

    Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition);

    String getPrimaryKeyName(T entity);

    String getPrimaryKeyName();

    String getTableName(T entity);

    String getTableName();
}
