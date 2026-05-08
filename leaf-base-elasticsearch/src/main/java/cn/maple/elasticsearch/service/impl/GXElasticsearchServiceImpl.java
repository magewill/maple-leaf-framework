package cn.maple.elasticsearch.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.elasticsearch.dao.GXElasticsearchDao;
import cn.maple.elasticsearch.model.GXElasticsearchModel;
import cn.maple.elasticsearch.repository.GXElasticsearchRepository;
import cn.maple.elasticsearch.service.GXElasticsearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.query.BaseQuery;
import org.springframework.data.elasticsearch.core.query.BaseQueryBuilder;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class GXElasticsearchServiceImpl<P extends GXElasticsearchRepository<T, D, Q, B, ID>, T extends GXElasticsearchModel, D extends GXElasticsearchDao<T, Q, B, ID>, Q extends BaseQuery, B extends BaseQueryBuilder<Q, B>, R extends GXBaseDBResDto, ID extends Serializable> extends GXBusinessServiceImpl implements GXElasticsearchService<P, T, D, Q, B, R, ID> {
    @Autowired
    @SuppressWarnings("all")
    protected P repository;

    @Override
    public <E> E useElasticsearchTemplate(String elasticsearchTemplateName, Supplier<E> supplier) {
        return repository.useElasticsearchTemplate(elasticsearchTemplateName, supplier);
    }

    @Override
    public void useElasticsearchTemplate(String elasticsearchTemplateName, Runnable runnable) {
        repository.useElasticsearchTemplate(elasticsearchTemplateName, runnable);
    }

    @Override
    public <E> Supplier<E> wrapElasticsearchTemplate(Supplier<E> supplier) {
        return repository.wrapElasticsearchTemplate(supplier);
    }

    @Override
    public Runnable wrapElasticsearchTemplate(Runnable runnable) {
        return repository.wrapElasticsearchTemplate(runnable);
    }

    @Override
    public <E> Supplier<E> wrapElasticsearchTemplate(String elasticsearchTemplateName, Supplier<E> supplier) {
        return repository.wrapElasticsearchTemplate(elasticsearchTemplateName, supplier);
    }

    @Override
    public Runnable wrapElasticsearchTemplate(String elasticsearchTemplateName, Runnable runnable) {
        return repository.wrapElasticsearchTemplate(elasticsearchTemplateName, runnable);
    }

    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Condition cannot be empty");
        }
        String indexName = CharSequenceUtil.isBlank(tableName) ? repository.getTableName() : tableName;
        return repository.checkRecordIsExists(indexName, condition);
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
        String indexName = CharSequenceUtil.isBlank(tableName) ? repository.getTableName() : tableName;
        Integer updated = repository.updateFieldByCondition(indexName, updateFields, condition);
        return Objects.equals(updated, 0) ? GXCommonConstant.DB_RECORD_NOT_FOUND : updated;
    }

    @Override
    public Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        return updateFieldByCondition(repository.getTableName(), updateFields, condition);
    }

    @Override
    public GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto queryParamInnerDto) {
        GXBaseQueryParamInnerDto effectiveQueryParam = copyQueryParam(queryParamInnerDto);
        CopyOptions copyOptions = getCopyOptions(effectiveQueryParam);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 5);
        GXPaginationResDto<Dict> paginate = repository.paginate(effectiveQueryParam);
        List<R> lst = paginate.getRecords().stream().map(dict -> {
            Object extraData = Optional.ofNullable(effectiveQueryParam.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, effectiveQueryParam.getMethodName(), copyOptions, extraData);
        }).collect(Collectors.toList());
        return new GXPaginationResDto<>(lst, paginate.getTotal(), paginate.getPageSize(), paginate.getCurrentPage());
    }

    @Override
    public GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("Elasticsearch union pagination query is not supported");
    }

    @Override
    public List<R> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        GXBaseQueryParamInnerDto effectiveQueryParam = copyQueryParam(queryParamInnerDto);
        CopyOptions copyOptions = getCopyOptions(effectiveQueryParam);
        String[] methodName = new String[]{effectiveQueryParam.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 5);
        Function<Dict, R> rowMapper = dict -> {
            Object extraData = Optional.ofNullable(effectiveQueryParam.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        return findByCondition(effectiveQueryParam, rowMapper);
    }

    @Override
    public List<R> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("Elasticsearch union list query is not supported");
    }

    @Override
    public <E> List<E> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper) {
        GXBaseQueryParamInnerDto effectiveQueryParam = copyQueryParam(queryParamInnerDto);
        String tableName = effectiveQueryParam.getTableName();
        if (CharSequenceUtil.isBlank(tableName)) {
            tableName = repository.getTableName();
            effectiveQueryParam.setTableName(tableName);
        }
        List<Dict> list = repository.findByCondition(effectiveQueryParam);
        return list.stream().map(rowMapper).collect(Collectors.toList());
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
        HashSet<String> columns = CollUtil.newHashSet("*");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(repository.getTableName()).columns(columns).condition(condition).extraData(extraData).build();
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
        return findOneByCondition(tableName, columns, condition, Dict.create());
    }

    @Override
    public R findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        GXBaseQueryParamInnerDto effectiveQueryParam = copyQueryParam(queryParamInnerDto);
        String[] methodName = new String[]{effectiveQueryParam.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Object extraData = Optional.ofNullable(effectiveQueryParam.getExtraData()).orElseGet(Dict::create);
        CopyOptions copyOptions = getCopyOptions(effectiveQueryParam);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 5);
        Function<Dict, R> rowMapper = dict -> {
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        return findOneByCondition(effectiveQueryParam, rowMapper);
    }

    @Override
    public R findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("Elasticsearch union single query is not supported");
    }

    @Override
    public <E> E findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper) {
        GXBaseQueryParamInnerDto effectiveQueryParam = copyQueryParam(queryParamInnerDto);
        String tableName = effectiveQueryParam.getTableName();
        if (CharSequenceUtil.isBlank(tableName)) {
            effectiveQueryParam.setTableName(repository.getTableName());
        }
        Dict dict = repository.findOneByCondition(effectiveQueryParam);
        if (Objects.isNull(dict)) {
            return null;
        }
        return rowMapper.apply(dict);
    }

    @Override
    public R findOneByCondition(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(CollUtil.newHashSet("*")).condition(condition).extraData(Dict.create()).build();
        return findOneByCondition(queryParamInnerDto);
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
        Class<T> targetClazz = GXCommonUtils.getGenericClassType(getClass(), 2);
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
            throw new GXBusinessException("Source record does not exist");
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
        String indexName = CharSequenceUtil.isBlank(tableName) ? repository.getTableName() : tableName;
        return repository.deleteSoftCondition(indexName, condition, Optional.ofNullable(extraData).orElseGet(Dict::create));
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
        return repository.deleteCondition(tableName, condition);
    }

    @Override
    public Integer deleteCondition(List<GXCondition<?>> condition) {
        return deleteCondition(repository.getTableName(), condition);
    }

    @Override
    public Integer deleteById(ID id) {
        return repository.deleteById(id);
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
        GXBaseQueryParamInnerDto effectiveQueryParam = copyQueryParam(queryParamInnerDto);
        if (CharSequenceUtil.isBlank(effectiveQueryParam.getTableName())) {
            effectiveQueryParam.setTableName(getTableName());
        }
        CopyOptions copyOptions = getCopyOptions(effectiveQueryParam);
        String[] methodName = new String[]{effectiveQueryParam.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        return repository.findByCondition(effectiveQueryParam).stream()
                .map(dict -> GXCommonUtils.convertSourceToTarget(dict, targetClazz, methodName[0], copyOptions))
                .collect(Collectors.toList());
    }

    @Override
    public <E> E findSingleFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
        if (Objects.isNull(queryParamInnerDto) || Objects.isNull(targetClazz)) {
            throw new GXBusinessException("queryParamInnerDto and targetClazz cannot be null");
        }
        GXBaseQueryParamInnerDto effectiveQueryParam = copyQueryParam(queryParamInnerDto);
        if (CollUtil.isEmpty(effectiveQueryParam.getColumns()) || effectiveQueryParam.getColumns().size() != 1) {
            throw new GXBusinessException("columns size must be exactly 1");
        }
        effectiveQueryParam.setLimit(1);
        String column = effectiveQueryParam.getColumns().toArray(new String[0])[0];
        if (CharSequenceUtil.isBlank(effectiveQueryParam.getTableName())) {
            effectiveQueryParam.setTableName(getTableName());
        }
        Dict dict = repository.findOneByCondition(effectiveQueryParam);
        if (Objects.isNull(dict)) {
            return null;
        }
        return Convert.convert(targetClazz, readColumnValue(dict, column));
    }

    @Override
    public <E> List<E> findSingleFieldLstByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
        if (Objects.isNull(queryParamInnerDto) || Objects.isNull(targetClazz)) {
            throw new GXBusinessException("queryParamInnerDto and targetClazz cannot be null");
        }
        GXBaseQueryParamInnerDto effectiveQueryParam = copyQueryParam(queryParamInnerDto);
        if (CollUtil.isEmpty(effectiveQueryParam.getColumns()) || effectiveQueryParam.getColumns().size() != 1) {
            throw new GXBusinessException("columns size must be exactly 1");
        }
        String column = effectiveQueryParam.getColumns().toArray(new String[0])[0];
        if (CharSequenceUtil.isBlank(effectiveQueryParam.getTableName())) {
            effectiveQueryParam.setTableName(getTableName());
        }
        List<Dict> dictList = repository.findByCondition(effectiveQueryParam);
        List<E> result = new ArrayList<>();
        dictList.forEach(dict -> {
            Object value = readColumnValue(dict, column);
            if (Objects.nonNull(value)) {
                result.add(Convert.convert(targetClazz, value));
            }
        });
        return result;
    }

    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params) {
        throw new GXBusinessException("Elasticsearch mapper method query is not supported");
    }

    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodMethod, Object... params) {
        throw new GXBusinessException("Elasticsearch mapper method query is not supported");
    }

    @Override
    public R findOneByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params) {
        throw new GXBusinessException("Elasticsearch mapper method query is not supported");
    }

    @Override
    public R findOneByCallMapperMethod(String mapperMethodName, Object... params) {
        throw new GXBusinessException("Elasticsearch mapper method query is not supported");
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
        GXBaseQueryParamInnerDto effectiveQueryParam = copyQueryParam(queryParamInnerDto);
        if (CharSequenceUtil.isBlank(effectiveQueryParam.getTableName())) {
            effectiveQueryParam.setTableName(getTableName());
        }
        effectiveQueryParam.setPage(1);
        effectiveQueryParam.setPageSize(1);
        return repository.paginate(effectiveQueryParam).getTotal();
    }

    @Override
    public String getPrimaryKeyName(T entity) {
        return repository.getPrimaryKeyName(entity);
    }

    @Override
    public String getTableName() {
        return repository.getTableName();
    }

    private GXBaseQueryParamInnerDto copyQueryParam(GXBaseQueryParamInnerDto source) {
        if (Objects.isNull(source)) {
            throw new GXBusinessException("queryParamInnerDto cannot be null");
        }
        return GXBaseQueryParamInnerDto.builder()
                .tableName(source.getTableName())
                .tableNameAlias(source.getTableNameAlias())
                .page(source.getPage())
                .pageSize(source.getPageSize())
                .condition(source.getCondition() == null ? new ArrayList<>() : new ArrayList<>(source.getCondition()))
                .columns(source.getColumns() == null ? null : new LinkedHashSet<>(source.getColumns()))
                .orderByField(source.getOrderByField() == null ? null : new LinkedHashMap<>(source.getOrderByField()))
                .groupByField(source.getGroupByField() == null ? null : new LinkedHashSet<>(source.getGroupByField()))
                .methodName(source.getMethodName())
                .having(source.getHaving() == null ? null : new LinkedHashSet<>(source.getHaving()))
                .copyOptions(source.getCopyOptions())
                .limit(source.getLimit())
                .joins(source.getJoins() == null ? null : new ArrayList<>(source.getJoins()))
                .extraData(source.getExtraData())
                .rawSQL(source.getRawSQL())
                .ignoreDataFilter(source.isIgnoreDataFilter())
                .paginateCount(source.isPaginateCount())
                .paramMap(source.getParamMap() == null ? new HashMap<>() : new HashMap<>(source.getParamMap()))
                .build();
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
