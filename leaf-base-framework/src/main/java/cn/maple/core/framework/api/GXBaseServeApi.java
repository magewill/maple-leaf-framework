package cn.maple.core.framework.api;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.api.dto.req.GXBaseApiReqDto;
import cn.maple.core.framework.api.dto.res.GXBaseApiResDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.protocol.req.GXQueryParamReqProtocol;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import com.google.common.collect.Table;

import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("all")
public interface GXBaseServeApi {
    <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Class<R> targetClazz);

    <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Map<String, String> orderField, Class<R> targetClazz);

    <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Class<R> targetClazz, Object extraData);

    <E> List<E> findFieldByCondition(Table<String, String, Object> condition, Set<String> columns, Class<E> targetClazz);

    <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Class<R> targetClazz);

    <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Class<R> targetClazz, Object extraData);

    <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Set<String> columns, Class<R> targetClazz, Object extraData);

    <R extends GXBaseApiResDto> R findById(Long id, Set<String> columns, Class<R> targetClazz);

    <R extends GXBaseApiResDto> R findById(Long id, Class<R> targetClazz);

    <E> E findSingleFieldByCondition(Table<String, String, Object> condition, String column, Class<E> targetClazz);

    <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto, Table<String, String, Object> condition, CopyOptions copyOptions);

    <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto, CopyOptions copyOptions);

    <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto);

    <R> GXPaginationResDto<R> paginate(GXQueryParamReqProtocol reqProtocol, Class<R> targetClazz, CopyOptions copyOptions);

    <R> GXPaginationResDto<R> paginate(GXQueryParamReqProtocol reqProtocol, Class<R> targetClazz);

    Integer deleteCondition(Table<String, String, Object> condition);

    Integer deleteSoftCondition(Table<String, String, Object> condition);

    Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, Table<String, String, Object> condition);

    boolean checkRecordIsExists(Table<String, String, Object> condition);

    Long count(Table<String, String, Object> condition);

    <T, Q extends GXBaseApiReqDto> T sourceToTarget(Q reqDto, Class<T> targetClass, String methodName, CopyOptions copyOptions, Dict extraData);

    <T, Q extends GXBaseApiReqDto> T sourceToTarget(Q reqDto, Class<T> targetClass, String methodName, CopyOptions copyOptions);

    <T, Q extends GXBaseApiReqDto> T sourceToTarget(Q reqDto, Class<T> targetClass);

    void staticBindServeServiceClass(Class<?> serveServiceClass);

    GXBaseServeApi callBindTargetServeSericeClass(Class<?> targetServeServiceClass);

    Object callMethod(String methodName, Object... params);

    Class<?> getServeServiceClass();

    List<GXCondition<?>> convertTableConditionToConditionExp(Table<String, String, Object> condition);

    List<GXCondition<?>> convertTableConditionToConditionExp(String tableNameAlias, Table<String, String, Object> condition);
}
