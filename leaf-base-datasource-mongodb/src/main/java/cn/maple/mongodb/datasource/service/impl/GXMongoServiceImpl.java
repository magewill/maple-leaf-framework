package cn.maple.mongodb.datasource.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.mongodb.datasource.dao.GXMongoDao;
import cn.maple.mongodb.datasource.model.GXMongoModel;
import cn.maple.mongodb.datasource.repository.GXMongoRepository;
import cn.maple.mongodb.datasource.service.GXMongoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class GXMongoServiceImpl<P extends GXMongoRepository<T, D, ID>, T extends GXMongoModel, D extends GXMongoDao<T, ID>, R extends GXBaseDBResDto, ID extends Serializable> extends GXBusinessServiceImpl implements GXMongoService<P, T, D, R, ID> {
    @Autowired
    @SuppressWarnings("all")
    protected P repository;

    @Override
    public <E> E useMongoTemplate(String mongoTemplateName, Supplier<E> supplier) {
        return repository.executeWithMongoTemplate(mongoTemplateName, supplier);
    }

    @Override
    public void useMongoTemplate(String mongoTemplateName, Runnable runnable) {
        repository.executeWithMongoTemplate(mongoTemplateName, () -> {
            runnable.run();
            return null;
        });
    }

    @Override
    public Runnable wrapMongoTemplateContext(Runnable runnable) {
        return repository.wrapMongoTemplateContext(runnable);
    }

    @Override
    public <E> Supplier<E> wrapMongoTemplateContext(Supplier<E> supplier) {
        return repository.wrapMongoTemplateContext(supplier);
    }

    @Override
    public <E> Callable<E> wrapMongoTemplateContext(Callable<E> callable) {
        return repository.wrapMongoTemplateContext(callable);
    }

    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Condition cannot be empty");
        }
        String collectionName = CharSequenceUtil.isBlank(tableName) ? repository.getTableName() : tableName;
        return repository.checkRecordIsExists(collectionName, condition);
    }

    @Override
    public boolean checkRecordIsExists(List<GXCondition<?>> condition) {
        return checkRecordIsExists(repository.getTableName(), condition);
    }

    @Override
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Condition cannot be empty");
        }
        if (CollUtil.isEmpty(updateFields)) {
            throw new GXBusinessException("updateFields cannot be empty");
        }
        String collectionName = CharSequenceUtil.isBlank(tableName) ? repository.getTableName() : tableName;
        if (!checkRecordIsExists(collectionName, condition)) {
            log.error("Data to update does not exist");
            return GXCommonConstant.DB_RECORD_NOT_FOUND;
        }
        return repository.updateFieldByCondition(collectionName, updateFields, condition);
    }

    @Override
    public Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        return updateFieldByCondition(repository.getTableName(), updateFields, condition);
    }

    @Override
    public GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto queryParamInnerDto) {
        GXBaseQueryParamInnerDto queryParam = requireQueryParam(queryParamInnerDto);
        CopyOptions copyOptions = getCopyOptions(queryParam);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 3);
        GXPaginationResDto<Dict> paginate = repository.paginate(queryParam);
        List<R> records = paginate.getRecords().stream().map(dict -> {
            Object extraData = Optional.ofNullable(queryParam.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, queryParam.getMethodName(), copyOptions, extraData);
        }).collect(Collectors.toList());
        return new GXPaginationResDto<>(records, paginate.getTotal(), paginate.getPageSize(), paginate.getCurrentPage());
    }

    @Override
    public GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("MongoDB union pagination is not supported");
    }

    @Override
    public List<R> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        GXBaseQueryParamInnerDto queryParam = requireQueryParam(queryParamInnerDto);
        CopyOptions copyOptions = getCopyOptions(queryParam);
        String[] methodName = new String[]{queryParam.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 3);
        Function<Dict, R> rowMapper = dict -> {
            Object extraData = Optional.ofNullable(queryParam.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        return findByCondition(queryParam, rowMapper);
    }

    @Override
    public List<R> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("MongoDB union query is not supported");
    }

    @Override
    public <E> List<E> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper) {
        GXBaseQueryParamInnerDto queryParam = requireQueryParam(queryParamInnerDto);
        String tableName = queryParam.getTableName();
        if (CharSequenceUtil.isBlank(tableName)) {
            queryParam.setTableName(repository.getTableName());
        }
        return repository.findByCondition(queryParam).stream().map(rowMapper).collect(Collectors.toList());
    }

    @Override
    public List<R> findByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition) {
        return findByCondition(tableName, condition, columns, null, null);
    }

    @Override
    public List<R> findByCondition(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(CollUtil.newHashSet("*")).condition(condition).build();
        return findByCondition(queryParamInnerDto);
    }

    @Override
    public List<R> findByCondition(List<GXCondition<?>> condition) {
        return findByCondition(repository.getTableName(), CollUtil.newHashSet("*"), condition);
    }

    @Override
    public List<R> findByCondition(List<GXCondition<?>> condition, Object extraData) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(repository.getTableName())
                .columns(CollUtil.newHashSet("*"))
                .condition(condition)
                .extraData(extraData)
                .build();
        return findByCondition(queryParamInnerDto);
    }

    @Override
    public List<R> findByCondition(List<GXCondition<?>> condition, Set<String> columns) {
        return findByCondition(repository.getTableName(), columns, condition);
    }

    @Override
    public List<R> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Map<String, String> orderField, Set<String> groupField) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(columns).condition(condition).orderByField(orderField).groupByField(groupField).build();
        return findByCondition(queryParamInnerDto);
    }

    @Override
    public List<R> findByCondition(List<GXCondition<?>> condition, Map<String, String> orderField, Set<String> groupField) {
        return findByCondition(repository.getTableName(), condition, CollUtil.newHashSet("*"), orderField, groupField);
    }

    @Override
    public List<R> findByCondition(List<GXCondition<?>> condition, Map<String, String> orderField) {
        return findByCondition(condition, orderField, null);
    }

    @Override
    public R findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Map<String, String> orderField, Set<String> groupField) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(columns).condition(condition).orderByField(orderField).groupByField(groupField).build();
        return findOneByCondition(queryParamInnerDto);
    }

    @Override
    public R findOneByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition, Object extraData) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(columns).condition(condition).extraData(extraData).build();
        return findOneByCondition(queryParamInnerDto);
    }

    @Override
    public R findOneByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition) {
        return findOneByCondition(tableName, columns, condition, null);
    }

    @Override
    public R findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        GXBaseQueryParamInnerDto queryParam = requireQueryParam(queryParamInnerDto);
        CopyOptions copyOptions = getCopyOptions(queryParam);
        String[] methodName = new String[]{queryParam.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 3);
        return findOneByCondition(queryParam, dict -> {
            Object extraData = Optional.ofNullable(queryParam.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        });
    }

    @Override
    public R findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("MongoDB union query is not supported");
    }

    @Override
    public <E> E findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper) {
        GXBaseQueryParamInnerDto queryParam = requireQueryParam(queryParamInnerDto);
        if (CharSequenceUtil.isBlank(queryParam.getTableName())) {
            queryParam.setTableName(repository.getTableName());
        }
        Dict dict = repository.findOneByCondition(queryParam);
        return CollUtil.isEmpty(dict) ? null : rowMapper.apply(dict);
    }

    @Override
    public R findOneByCondition(String tableName, List<GXCondition<?>> condition) {
        return findOneByCondition(tableName, CollUtil.newHashSet("*"), condition);
    }

    @Override
    public R findOneByCondition(List<GXCondition<?>> condition, Object extraData) {
        return findOneByCondition(repository.getTableName(), CollUtil.newHashSet("*"), condition, extraData);
    }

    @Override
    public R findOneByCondition(List<GXCondition<?>> condition) {
        return findOneByCondition(repository.getTableName(), CollUtil.newHashSet("*"), condition);
    }

    @Override
    public R findOneByCondition(List<GXCondition<?>> condition, Set<String> columns) {
        return findOneByCondition(repository.getTableName(), columns, condition);
    }

    @Override
    public ID updateOrCreate(T entity) {
        return repository.updateOrCreate(entity, Collections.emptyList());
    }

    @Override
    public ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        return repository.updateOrCreate(entity, condition);
    }

    @Override
    public <Q extends GXBaseReqDto> ID updateOrCreate(Q req, List<GXCondition<?>> condition, CopyOptions copyOptions) {
        Class<T> targetClazz = GXCommonUtils.getGenericClassType(getClass(), 1);
        T entity = convertSourceToTarget(req, targetClazz, GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME, copyOptions);
        return updateOrCreate(entity, condition);
    }

    @Override
    public <Q extends GXBaseReqDto> ID updateOrCreate(Q req, CopyOptions copyOptions) {
        return updateOrCreate(req, Collections.emptyList(), copyOptions);
    }

    @Override
    public <Q extends GXBaseReqDto> ID updateOrCreate(Q req) {
        return updateOrCreate(req, CopyOptions.create());
    }

    @Override
    public ID copyOneData(List<GXCondition<?>> copyCondition, Dict replaceData, Dict extraData) {
        if (CollUtil.isEmpty(copyCondition)) {
            throw new GXBusinessException("Condition cannot be empty");
        }
        R oneData = findOneByCondition(repository.getTableName(), copyCondition);
        if (Objects.isNull(oneData)) {
            throw new GXBusinessException("Data to copy does not exist");
        }
        T entity = GXCommonUtils.convertSourceToTarget(oneData, GXCommonUtils.getGenericClassType(getClass(), 1), null, null, Optional.ofNullable(extraData).orElseGet(Dict::create));
        if (Objects.isNull(entity)) {
            throw new GXBusinessException("Data conversion failed");
        }
        String primaryKeyName = getPrimaryKeyName(entity);
        GXCommonUtils.reflectCallObjectMethod(entity, CharSequenceUtil.format("set{}", CharSequenceUtil.upperFirst(CharSequenceUtil.toCamelCase(primaryKeyName))), (Object) null);
        Optional.ofNullable(replaceData).orElseGet(Dict::create)
                .forEach((key, value) -> GXCommonUtils.reflectCallObjectMethod(entity, CharSequenceUtil.format("set{}", CharSequenceUtil.upperFirst(CharSequenceUtil.toCamelCase(key))), value));
        return updateOrCreate(entity);
    }

    @Override
    public ID copyOneData(List<GXCondition<?>> copyCondition, Dict replaceData) {
        return copyOneData(copyCondition, replaceData, Dict.create());
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Condition cannot be empty");
        }
        String collectionName = CharSequenceUtil.isBlank(tableName) ? repository.getTableName() : tableName;
        return repository.deleteSoftCondition(collectionName, condition, Optional.ofNullable(extraData).orElseGet(Dict::create));
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition) {
        return deleteSoftCondition(tableName, condition, Dict.create());
    }

    @Override
    public Integer deleteSoftCondition(List<GXCondition<?>> condition) {
        return deleteSoftCondition(repository.getTableName(), condition);
    }

    @Override
    public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Condition cannot be empty");
        }
        String collectionName = CharSequenceUtil.isBlank(tableName) ? repository.getTableName() : tableName;
        return repository.deleteCondition(collectionName, condition);
    }

    @Override
    public Integer deleteCondition(List<GXCondition<?>> condition) {
        return deleteCondition(repository.getTableName(), condition);
    }

    @Override
    public Integer deleteById(ID id) {
        if (id == null) {
            return 0;
        }
        return deleteCondition(repository.getTableName(), Collections.singletonList(buildIdCondition(id)));
    }

    @Override
    public <E> List<E> findMultiFieldByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(CharSequenceUtil.isBlank(tableName) ? getTableName() : tableName)
                .condition(condition)
                .columns(columns)
                .build();
        return findMultiFieldByCondition(queryParamInnerDto, targetClazz);
    }

    @Override
    public <E> List<E> findMultiFieldByCondition(List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz) {
        return findMultiFieldByCondition(getTableName(), condition, columns, targetClazz);
    }

    @Override
    public <E> List<E> findMultiFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
        if (Objects.isNull(queryParamInnerDto) || Objects.isNull(targetClazz)) {
            throw new GXBusinessException("queryParamInnerDto and targetClazz cannot be null");
        }
        if (CharSequenceUtil.isBlank(queryParamInnerDto.getTableName())) {
            queryParamInnerDto.setTableName(getTableName());
        }
        CopyOptions copyOptions = getCopyOptions(queryParamInnerDto);
        String[] methodName = new String[]{queryParamInnerDto.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        return repository.findByCondition(queryParamInnerDto).stream()
                .map(dict -> GXCommonUtils.convertSourceToTarget(dict, targetClazz, methodName[0], copyOptions))
                .collect(Collectors.toList());
    }

    @Override
    public <E> E findSingleFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
        if (Objects.isNull(queryParamInnerDto) || Objects.isNull(targetClazz)) {
            throw new GXBusinessException("queryParamInnerDto and targetClazz cannot be null");
        }
        if (CollUtil.isEmpty(queryParamInnerDto.getColumns()) || queryParamInnerDto.getColumns().size() != 1) {
            throw new GXBusinessException("columns size must be exactly 1");
        }
        queryParamInnerDto.setLimit(1);
        String column = queryParamInnerDto.getColumns().toArray(new String[0])[0];
        if (CharSequenceUtil.isBlank(queryParamInnerDto.getTableName())) {
            queryParamInnerDto.setTableName(getTableName());
        }
        Dict dict = repository.findOneByCondition(queryParamInnerDto);
        if (CollUtil.isEmpty(dict)) {
            return null;
        }
        return Convert.convert(targetClazz, readColumnValue(dict, column));
    }

    @Override
    public <E> List<E> findSingleFieldLstByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
        if (Objects.isNull(queryParamInnerDto) || Objects.isNull(targetClazz)) {
            throw new GXBusinessException("queryParamInnerDto and targetClazz cannot be null");
        }
        if (CollUtil.isEmpty(queryParamInnerDto.getColumns()) || queryParamInnerDto.getColumns().size() != 1) {
            throw new GXBusinessException("columns size must be exactly 1");
        }
        String column = queryParamInnerDto.getColumns().toArray(new String[0])[0];
        if (CharSequenceUtil.isBlank(queryParamInnerDto.getTableName())) {
            queryParamInnerDto.setTableName(getTableName());
        }
        List<E> result = new ArrayList<>();
        repository.findByCondition(queryParamInnerDto).forEach(dict -> {
            Object value = readColumnValue(dict, column);
            if (Objects.nonNull(value)) {
                result.add(Convert.convert(targetClazz, value));
            }
        });
        return result;
    }

    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params) {
        throw new GXBusinessException("MongoDB mapper method calls are not supported");
    }

    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodMethod, Object... params) {
        throw new GXBusinessException("MongoDB mapper method calls are not supported");
    }

    @Override
    public R findOneByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params) {
        throw new GXBusinessException("MongoDB mapper method calls are not supported");
    }

    @Override
    public R findOneByCallMapperMethod(String mapperMethodName, Object... params) {
        throw new GXBusinessException("MongoDB mapper method calls are not supported");
    }

    @Override
    public Long countByCondition(List<GXCondition<?>> conditions) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(getTableName())
                .condition(Optional.ofNullable(conditions).orElseGet(ArrayList::new))
                .page(1)
                .pageSize(1)
                .build();
        return countByCondition(queryParamInnerDto);
    }

    @Override
    public Long countByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        if (Objects.isNull(queryParamInnerDto)) {
            throw new GXBusinessException("queryParamInnerDto cannot be null");
        }
        if (CharSequenceUtil.isBlank(queryParamInnerDto.getTableName())) {
            queryParamInnerDto.setTableName(getTableName());
        }
        queryParamInnerDto.setPage(1);
        queryParamInnerDto.setPageSize(1);
        return repository.paginate(queryParamInnerDto).getTotal();
    }

    @Override
    public String getPrimaryKeyName(T entity) {
        return repository.getPrimaryKeyName(entity);
    }

    @Override
    public String getTableName() {
        return repository.getTableName();
    }

    private GXCondition<?> buildIdCondition(ID id) {
        if (id instanceof Number number) {
            return new GXConditionEQ(getTableName(), "id", number);
        }
        return new GXConditionStrEQ(getTableName(), "id", Convert.toStr(id));
    }

    private GXBaseQueryParamInnerDto requireQueryParam(GXBaseQueryParamInnerDto queryParamInnerDto) {
        if (queryParamInnerDto == null) {
            throw new GXBusinessException("queryParamInnerDto cannot be null");
        }
        return queryParamInnerDto;
    }

    private Object readColumnValue(Dict dict, String column) {
        if (Objects.isNull(dict) || CharSequenceUtil.isBlank(column)) {
            return null;
        }
        Object value = dict.get(column);
        if (Objects.nonNull(value)) {
            return value;
        }
        value = dict.get(CharSequenceUtil.toUnderlineCase(column));
        if (Objects.nonNull(value)) {
            return value;
        }
        return dict.get(CharSequenceUtil.toCamelCase(column));
    }
}
