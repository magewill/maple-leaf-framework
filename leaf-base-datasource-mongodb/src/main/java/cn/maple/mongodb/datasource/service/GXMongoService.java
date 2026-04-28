package cn.maple.mongodb.datasource.service;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.mongodb.datasource.dao.GXMongoDao;
import cn.maple.mongodb.datasource.model.GXMongoModel;
import cn.maple.mongodb.datasource.repository.GXMongoRepository;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public interface GXMongoService<P extends GXMongoRepository<T, D, ID>, T extends GXMongoModel, D extends GXMongoDao<T, ID>, R extends GXBaseDBResDto, ID extends Serializable> extends GXBusinessService {
    <E> E useMongoTemplate(String mongoTemplateName, Supplier<E> supplier);

    void useMongoTemplate(String mongoTemplateName, Runnable runnable);

    boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition);

    boolean checkRecordIsExists(List<GXCondition<?>> condition);

    Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition);

    Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition);

    GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto queryParamInnerDto);

    GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    List<R> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto);

    List<R> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    <E> List<E> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper);

    List<R> findByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition);

    List<R> findByCondition(String tableName, List<GXCondition<?>> condition);

    List<R> findByCondition(List<GXCondition<?>> condition);

    List<R> findByCondition(List<GXCondition<?>> condition, Object extraData);

    List<R> findByCondition(List<GXCondition<?>> condition, Set<String> columns);

    List<R> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Map<String, String> orderField, Set<String> groupField);

    List<R> findByCondition(List<GXCondition<?>> condition, Map<String, String> orderField, Set<String> groupField);

    List<R> findByCondition(List<GXCondition<?>> condition, Map<String, String> orderField);

    R findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Map<String, String> orderField, Set<String> groupField);

    R findOneByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition, Object extraData);

    R findOneByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition);

    R findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto);

    R findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    <E> E findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper);

    R findOneByCondition(String tableName, List<GXCondition<?>> condition);

    R findOneByCondition(List<GXCondition<?>> condition, Object extraData);

    R findOneByCondition(List<GXCondition<?>> condition);

    R findOneByCondition(List<GXCondition<?>> condition, Set<String> columns);

    ID updateOrCreate(T entity);

    ID updateOrCreate(T entity, List<GXCondition<?>> condition);

    <Q extends GXBaseReqDto> ID updateOrCreate(Q req, List<GXCondition<?>> condition, CopyOptions copyOptions);

    <Q extends GXBaseReqDto> ID updateOrCreate(Q req, CopyOptions copyOptions);

    <Q extends GXBaseReqDto> ID updateOrCreate(Q req);

    ID copyOneData(List<GXCondition<?>> copyCondition, Dict replaceData, Dict extraData);

    ID copyOneData(List<GXCondition<?>> copyCondition, Dict replaceData);

    Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData);

    Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition);

    Integer deleteSoftCondition(List<GXCondition<?>> condition);

    Integer deleteCondition(String tableName, List<GXCondition<?>> condition);

    Integer deleteCondition(List<GXCondition<?>> condition);

    Integer deleteById(ID id);

    <E> List<E> findMultiFieldByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz);

    <E> List<E> findMultiFieldByCondition(List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz);

    <E> List<E> findMultiFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz);

    default <E> E findSingleFieldByCondition(List<GXCondition<?>> condition, String column, Class<E> targetClazz) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(getTableName()).condition(condition).columns(CollUtil.newHashSet(column)).build();
        return findSingleFieldByCondition(queryParamInnerDto, targetClazz);
    }

    <E> E findSingleFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz);

    default <E> List<E> findSingleFieldLstByCondition(List<GXCondition<?>> condition, String column, Class<E> targetClazz, Set<String> groupByField) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(getTableName()).condition(condition).columns(CollUtil.newHashSet(column)).groupByField(groupByField).build();
        return findSingleFieldLstByCondition(queryParamInnerDto, targetClazz);
    }

    <E> List<E> findSingleFieldLstByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz);

    Collection<R> findByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params);

    Collection<R> findByCallMapperMethod(String mapperMethodMethod, Object... params);

    R findOneByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params);

    R findOneByCallMapperMethod(String mapperMethodName, Object... params);

    Long countByCondition(List<GXCondition<?>> conditions);

    Long countByCondition(GXBaseQueryParamInnerDto queryParamInnerDto);

    String getPrimaryKeyName(T entity);

    String getTableName();

    default CopyOptions getCopyOptions(GXBaseQueryParamInnerDto queryParamInnerDto) {
        return ObjectUtil.defaultIfNull(queryParamInnerDto == null ? null : queryParamInnerDto.getCopyOptions(), GXCommonUtils::getDefaultCopyOptions);
    }
}
