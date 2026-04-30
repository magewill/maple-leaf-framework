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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @SuppressWarnings("all")
    private static final Logger LOGGER = LoggerFactory.getLogger(GXElasticsearchServiceImpl.class);

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
            throw new GXBusinessException("条件不能为空!");
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
            throw new GXBusinessException("条件不能为空!");
        }
        if (CollUtil.isEmpty(updateFields)) {
            throw new GXBusinessException("updateFields cannot be empty");
        }
        String indexName = CharSequenceUtil.isBlank(tableName) ? repository.getTableName() : tableName;
        if (!checkRecordIsExists(indexName, condition)) {
            log.error("待更新的数据不存在!");
            return GXCommonConstant.DB_RECORD_NOT_FOUND;
        }
        return repository.updateFieldByCondition(indexName, updateFields, condition);
    }

    @Override
    public Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        return updateFieldByCondition(repository.getTableName(), updateFields, condition);
    }

    @Override
    public GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto queryParamInnerDto) {
        CopyOptions copyOptions = getCopyOptions(queryParamInnerDto);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 5);
        GXPaginationResDto<Dict> paginate = repository.paginate(queryParamInnerDto);
        List<R> lst = paginate.getRecords().stream().map(dict -> {
            Object extraData = Optional.ofNullable(queryParamInnerDto.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, queryParamInnerDto.getMethodName(), copyOptions, extraData);
        }).collect(Collectors.toList());
        return new GXPaginationResDto<>(lst, paginate.getTotal(), paginate.getPageSize(), paginate.getCurrentPage());
    }

    @Override
    public GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        LOGGER.error("请自己实现此方法");
        return null;
    }

    @Override
    public List<R> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        CopyOptions copyOptions = getCopyOptions(queryParamInnerDto);
        String[] methodName = new String[]{queryParamInnerDto.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 5);
        Function<Dict, R> rowMapper = dict -> {
            Object extraData = Optional.ofNullable(queryParamInnerDto.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        return findByCondition(queryParamInnerDto, rowMapper);
    }

    @Override
    public List<R> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        LOGGER.error("请自己实现该方法");
        return null;
    }

    @Override
    public <E> List<E> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper) {
        String tableName = queryParamInnerDto.getTableName();
        if (CharSequenceUtil.isBlank(tableName)) {
            tableName = repository.getTableName();
            queryParamInnerDto.setTableName(tableName);
        }
        List<Dict> list = repository.findByCondition(queryParamInnerDto);
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
        String[] methodName = new String[]{queryParamInnerDto.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Object extraData = Optional.ofNullable(queryParamInnerDto.getExtraData()).orElse(Dict.class);
        CopyOptions copyOptions = getCopyOptions(queryParamInnerDto);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 5);
        Function<Dict, R> rowMapper = dict -> {
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        return findOneByCondition(queryParamInnerDto, rowMapper);
    }

    @Override
    public R findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        LOGGER.error("请自己实现此方法");
        return null;
    }

    @Override
    public <E> E findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper) {
        Dict dict = repository.findOneByCondition(queryParamInnerDto);
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
            throw new GXBusinessException("条件不能为空!");
        }
        R oneData = findOneByCondition(repository.getTableName(), copyCondition);
        if (Objects.isNull(oneData)) {
            throw new GXBusinessException("待拷贝的数据不存在!!");
        }
        T entity = GXCommonUtils.convertSourceToTarget(oneData, GXCommonUtils.getGenericClassType(getClass(), 1), null, null, Optional.ofNullable(extraData).orElseGet(Dict::create));
        if (Objects.isNull(entity)) {
            throw new GXBusinessException("数据转换失败!");
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
            throw new GXBusinessException("条件不能为空!");
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
        if (CollUtil.isEmpty(queryParamInnerDto.getColumns()) || queryParamInnerDto.getColumns().size() != 1) {
            throw new GXBusinessException("columns size must be exactly 1");
        }
        String column = queryParamInnerDto.getColumns().toArray(new String[0])[0];
        if (CharSequenceUtil.isBlank(queryParamInnerDto.getTableName())) {
            queryParamInnerDto.setTableName(getTableName());
        }
        List<Dict> dictList = repository.findByCondition(queryParamInnerDto);
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
        return null;
    }

    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodMethod, Object... params) {
        return null;
    }

    @Override
    public R findOneByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params) {
        return null;
    }

    @Override
    public R findOneByCallMapperMethod(String mapperMethodName, Object... params) {
        return null;
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
