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

    default ID updateOrCreate(T entity) {
        Assert.notNull(entity, "Entity must not be null");
        return updateOrCreate(entity, Collections.emptyList());
    }

    List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    default List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("Please implement findByCondition");
    }

    List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition);

    List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns);

    default List<Dict> findByCondition(List<GXCondition<?>> condition) {
        Assert.notNull(condition, "Condition list must not be null");
        return findByCondition(getTableName(), condition);
    }

    default List<Dict> findByCondition(List<GXCondition<?>> condition, Set<String> columns) {
        Assert.notNull(condition, "Condition list must not be null");
        return findByCondition(getTableName(), condition, columns);
    }

    default List<Dict> findByCondition() {
        return findByCondition(getTableName(), Collections.emptyList());
    }

    default List<Dict> findByCondition(Set<String> columns) {
        return findByCondition(getTableName(), Collections.emptyList(), columns);
    }

    Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    default Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("Please implement findOneByCondition");
    }

    Dict findOneByCondition(String tableName, List<GXCondition<?>> condition);

    default Dict findOneByCondition(List<GXCondition<?>> condition) {
        Assert.notNull(condition, "Condition list must not be null");
        return findOneByCondition(getTableName(), condition);
    }

    Dict findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns);

    default Dict findOneByCondition(List<GXCondition<?>> condition, Set<String> columns) {
        Assert.notNull(condition, "Condition list must not be null");
        return findOneByCondition(getTableName(), condition, columns);
    }

    Dict findOneById(String tableName, ID id, Set<String> columns);

    Dict findOneById(String tableName, ID id);

    default Dict findOneById(ID id) {
        Assert.notNull(id, "ID must not be null");
        return findOneById(getTableName(), id);
    }

    default Dict findOneById(ID id, Set<String> columns) {
        Assert.notNull(id, "ID must not be null");
        return findOneById(getTableName(), id, columns);
    }

    GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    default GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("Please implement paginate");
    }

    GXPaginationResDto<Dict> paginate(String tableName, Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns);

    default GXPaginationResDto<Dict> paginate(Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns) {
        return paginate(getTableName(), page, pageSize, condition, columns);
    }

    default GXPaginationResDto<Dict> paginate(Integer page, Integer pageSize, List<GXCondition<?>> condition) {
        return paginate(getTableName(), page, pageSize, condition, null);
    }

    Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData);

    default Integer deleteSoftCondition(List<GXCondition<?>> condition, Dict extraData) {
        Assert.notNull(condition, "Condition list must not be null");
        return deleteSoftCondition(getTableName(), condition, extraData);
    }

    Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData);

    default Integer deleteSoftCondition(List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        Assert.notNull(updateFieldList, "Update field list must not be null");
        Assert.notNull(condition, "Condition list must not be null");
        return deleteSoftCondition(getTableName(), updateFieldList, condition, extraData);
    }

    Integer deleteCondition(String tableName, List<GXCondition<?>> condition);

    default Integer deleteCondition(List<GXCondition<?>> condition) {
        Assert.notNull(condition, "Condition list must not be null");
        return deleteCondition(getTableName(), condition);
    }

    boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition);

    default boolean checkRecordIsExists(List<GXCondition<?>> condition) {
        Assert.notNull(condition, "Condition list must not be null");
        return checkRecordIsExists(getTableName(), condition);
    }

    boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext constraintValidatorContext);

    Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition);

    default Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        Assert.notNull(updateFields, "Update field list must not be null");
        Assert.notNull(condition, "Condition list must not be null");
        return updateFieldByCondition(getTableName(), updateFields, condition);
    }

    default String getPrimaryKeyName(T entity) {
        Assert.notNull(entity, "Entity must not be null");
        return getPrimaryKeyName();
    }

    String getPrimaryKeyName();

    default String getTableName(T entity) {
        Assert.notNull(entity, "Entity must not be null");
        return getTableName();
    }

    String getTableName();
}
