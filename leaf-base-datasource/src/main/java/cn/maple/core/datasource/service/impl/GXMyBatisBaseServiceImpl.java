package cn.maple.core.datasource.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.datasource.dao.GXMyBatisDao;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import cn.maple.core.datasource.repository.GXMyBatisRepository;
import cn.maple.core.datasource.service.GXMyBatisBaseService;
import cn.maple.core.datasource.util.GXQueryParamUtils;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.inner.field.GXUpdateNumberField;
import cn.maple.core.framework.dto.inner.field.GXUpdateStrField;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXDBNotExistsException;
import cn.maple.core.framework.model.GXBaseModel;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class GXMyBatisBaseServiceImpl<P extends GXMyBatisRepository<M, T, D, ID>, M extends GXBaseMapper<T>, T extends GXBaseModel, D extends GXMyBatisDao<M, T, ID>, R extends GXBaseDBResDto, ID extends Serializable> extends GXBusinessServiceImpl implements GXMyBatisBaseService<P, M, T, D, R, ID> {
    @Autowired
    @SuppressWarnings("all")
    protected P repository;

    @Autowired
    @SuppressWarnings("all")
    private M baseMapper;

    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Condition list must not be empty");
        }
        return repository.checkRecordIsExists(tableName, condition);
    }


    @Override
    public boolean checkRecordIsExists(List<GXCondition<?>> condition) {
        return checkRecordIsExists(repository.getTableName(), condition);
    }

    @Override
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Condition list must not be empty");
        }
        if (CollUtil.isEmpty(updateFields)) {
            throw new GXBusinessException("updateFields cannot be empty");
        }
        boolean b = checkRecordIsExists(tableName, condition);
        if (!b) {
            log.error("Record to update does not exist");
            return GXCommonConstant.DB_RECORD_NOT_FOUND;
        }
        if (GXCurrentRequestContextUtils.isHTTP() && GXCurrentRequestContextUtils.tokenExists()) {
            String loginUserName = getLoginUserName();
            if (CharSequenceUtil.isNotEmpty(loginUserName)) {
                List<String> updateFieldNameLst = new ArrayList<>();
                updateFields.forEach(field -> {
                    String fieldName = field.getFieldName();
                    updateFieldNameLst.add(fieldName);
                });
                ArrayList<GXUpdateField<?>> newUpdateFields = CollUtil.newArrayList(updateFields);
                if (!CollUtil.contains(updateFieldNameLst, "updated_by")) {
                    GXUpdateStrField updateCreatedByField = new GXUpdateStrField(tableName, "updated_by", loginUserName);
                    newUpdateFields.add(updateCreatedByField);
                }
                if (!CollUtil.contains(updateFieldNameLst, "updated_at")) {
                    GXUpdateNumberField updateUpdatedAtField = new GXUpdateNumberField(tableName, "updated_at", Math.toIntExact(DateUtil.currentSeconds()));
                    newUpdateFields.add(updateUpdatedAtField);
                }
                return repository.updateFieldByCondition(tableName, newUpdateFields, condition);
            }
        }
        return repository.updateFieldByCondition(tableName, updateFields, condition);
    }

    @Override
    public Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        return updateFieldByCondition(repository.getTableName(), updateFields, condition);
    }

    @Override
    public GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto queryParamReqDto) {
        GXBaseQueryParamInnerDto queryParam = copyQueryParam(queryParamReqDto);
        if (CharSequenceUtil.isBlank(queryParam.getRawSQL())) {
            if (CharSequenceUtil.isEmpty(queryParam.getTableName())) {
                queryParam.setTableName(repository.getTableName());
            }
            if (Objects.isNull(queryParam.getColumns())) {
                queryParam.setColumns(CollUtil.newHashSet("*"));
            }
        }
        if (Objects.isNull(queryParam.getMethodName())) {
            queryParam.setMethodName(GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME);
        }
        CopyOptions copyOptions = getCopyOptions(queryParam);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        GXPaginationResDto<Dict> paginate = repository.paginate(queryParam);
        List<R> lst = paginate.getRecords().stream().map(dict -> {
            Object extraData = Optional.ofNullable(queryParam.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, queryParam.getMethodName(), copyOptions, extraData);
        }).collect(Collectors.toList());
        long total = paginate.getTotal();
        if (!queryParam.isPaginateCount()) {
            total = getPaginateCount(queryParam);
        }
        return new GXPaginationResDto<>(lst, total, paginate.getPageSize(), paginate.getCurrentPage());
    }

    @Override
    public GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        GXBaseQueryParamInnerDto masterQueryParam = copyQueryParam(masterQueryParamInnerDto);
        if (CharSequenceUtil.isBlank(masterQueryParam.getRawSQL())) {
            if (CharSequenceUtil.isEmpty(masterQueryParam.getTableName())) {
                masterQueryParam.setTableName(repository.getTableName());
            }
            if (Objects.isNull(masterQueryParam.getColumns())) {
                masterQueryParam.setColumns(CollUtil.newHashSet("*"));
            }
        }
        if (Objects.isNull(masterQueryParam.getMethodName())) {
            masterQueryParam.setMethodName(GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME);
        }
        CopyOptions copyOptions = getCopyOptions(masterQueryParam);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        GXPaginationResDto<Dict> paginate = repository.paginate(masterQueryParam, unionQueryParamInnerDtoLst, unionTypeEnums);
        List<R> lst = paginate.getRecords().stream().map(dict -> {
            Object extraData = Optional.ofNullable(masterQueryParam.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, masterQueryParam.getMethodName(), copyOptions, extraData);
        }).collect(Collectors.toList());
        long total = paginate.getTotal();
        if (!masterQueryParam.isPaginateCount()) {
            total = getUnionPaginateCount(masterQueryParam, unionQueryParamInnerDtoLst, unionTypeEnums);
        }
        return new GXPaginationResDto<>(lst, total, paginate.getPageSize(), paginate.getCurrentPage());
    }

    @Override
    public List<R> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        GXBaseQueryParamInnerDto queryParam = copyQueryParam(queryParamInnerDto);
        CopyOptions copyOptions = getCopyOptions(queryParam);
        String[] methodName = new String[]{queryParam.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        Function<Dict, R> rowMapper = dict -> {
            Object extraData = Optional.ofNullable(queryParam.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        return findByConditionPrepared(queryParam, rowMapper);
    }

    @Override
    public List<R> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        GXBaseQueryParamInnerDto masterQueryParam = copyQueryParam(masterQueryParamInnerDto);
        CopyOptions copyOptions = getCopyOptions(masterQueryParam);
        String[] methodName = new String[]{masterQueryParam.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        Function<Dict, R> rowMapper = dict -> {
            Object extraData = Optional.ofNullable(masterQueryParam.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        List<Dict> lst = repository.findByCondition(masterQueryParam, unionQueryParamInnerDtoLst, unionTypeEnums);
        return lst.stream().map(rowMapper).collect(Collectors.toList());
    }

    @Override
    public <E> List<E> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper) {
        GXBaseQueryParamInnerDto queryParam = copyQueryParam(queryParamInnerDto);
        String tableName = queryParam.getTableName();
        if (CharSequenceUtil.isBlank(tableName)) {
            tableName = repository.getTableName();
            queryParam.setTableName(tableName);
        }
        List<Dict> list = repository.findByCondition(queryParam);
        return list.stream().map(rowMapper).collect(Collectors.toList());
    }

    @Override
    public List<R> findByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition) {
        return findByCondition(tableName, condition, columns, null, null);
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
    public List<R> findByCondition(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(CollUtil.newHashSet("*")).condition(condition).build();
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
    public R findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        GXBaseQueryParamInnerDto queryParam = copyQueryParam(queryParamInnerDto);
        String[] methodName = new String[]{queryParam.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Object extraData = Optional.ofNullable(queryParam.getExtraData()).orElse(Dict.class);
        CopyOptions copyOptions = getCopyOptions(queryParam);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        Function<Dict, R> rowMapper = dict -> {
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        return findOneByCondition(queryParam, rowMapper);
    }

    @Override
    public R findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        GXBaseQueryParamInnerDto masterQueryParam = copyQueryParam(masterQueryParamInnerDto);
        String[] methodName = new String[]{masterQueryParam.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Object extraData = Optional.ofNullable(masterQueryParam.getExtraData()).orElse(Dict.class);
        CopyOptions copyOptions = getCopyOptions(masterQueryParam);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        Function<Dict, R> rowMapper = dict -> {
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        Dict dict = repository.findOneByCondition(masterQueryParam, unionQueryParamInnerDtoLst, unionTypeEnums);
        if (Objects.isNull(dict)) {
            return null;
        }
        return rowMapper.apply(dict);
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
    public R findOneByCondition(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(CollUtil.newHashSet("*")).condition(condition).extraData(Dict.create()).build();
        return findOneByCondition(queryParamInnerDto);
    }

    @Override
    public R findOneByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition) {
        return findOneByCondition(tableName, columns, condition, Dict.create());
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
    public R findOneByCondition(List<GXCondition<?>> condition, Set<String> columns, Object extraData) {
        return findOneByCondition(repository.getTableName(), columns, condition, extraData);
    }

    @Override
    public ID updateOrCreate(T entity) {
        return updateOrCreate(entity, Collections.emptyList());
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
    public ID copyOneData(List<GXCondition<?>> conditions, Dict replaceData, Dict extraData) {
        R oneData = findOneByCondition(repository.getTableName(), conditions);
        if (Objects.isNull(oneData)) {
            throw new GXDBNotExistsException("Record to copy does not exist");
        }
        T entity = GXCommonUtils.convertSourceToTarget(oneData, GXCommonUtils.getGenericClassType(getClass(), 2), null, null, extraData);
        assert entity != null;
        String setPrimaryKeyMethodName = CharSequenceUtil.format("set{}", CharSequenceUtil.upperFirst(getPrimaryKeyName(entity)));
        Method method = ReflectUtil.getMethod(entity.getClass(), setPrimaryKeyMethodName, GXCommonUtils.getGenericClassType(getClass(), 5));
        if (Objects.isNull(method)) {
            throw new GXBusinessException(CharSequenceUtil.format("Method {} does not exist", setPrimaryKeyMethodName));
        }
        ReflectUtil.invoke(entity, method, (Object) null);
        replaceData.forEach((k, v) -> GXCommonUtils.reflectCallObjectMethod(entity, CharSequenceUtil.format("set{}", CharSequenceUtil.upperFirst(CharSequenceUtil.toCamelCase(k))), v));

        return updateOrCreate(entity);
    }

    @Override
    public ID copyOneData(List<GXCondition<?>> conditions, Dict replaceData) {
        return copyOneData(conditions, replaceData, Dict.create());
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, @NotNull Dict extraData) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Condition list must not be empty");
        }
        if (ObjectUtil.isNull(extraData)) {
            extraData = Dict.create();
        }
        if (GXCurrentRequestContextUtils.isHTTP()
                && GXCurrentRequestContextUtils.tokenExists()
                && !extraData.containsKey("deletedBy")) {
            String loginUserName = getLoginUserName();
            if (CharSequenceUtil.isNotEmpty(loginUserName)) {
                extraData.set("deletedBy", loginUserName);
            }
        }
        return repository.deleteSoftCondition(tableName, updateFieldList, condition, extraData);
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        return deleteSoftCondition(tableName, CollUtil.newArrayList(), condition, extraData);
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
            throw new GXBusinessException("Condition list must not be empty");
        }
        return repository.deleteCondition(tableName, condition);
    }

    @Override
    public Integer deleteCondition(List<GXCondition<?>> condition) {
        return deleteCondition(repository.getTableName(), condition);
    }

    @Override
    public <E> List<E> findMultiFieldByCondition(List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz) {
        return findMultiFieldByCondition(repository.getTableName(), condition, columns, targetClazz);
    }

    @Override
    public <E> List<E> findMultiFieldByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).tableNameAlias(tableName).condition(condition).columns(columns).build();
        return findMultiFieldByCondition(queryParamInnerDto, targetClazz);
    }

    public <E> List<E> findMultiFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
        GXBaseQueryParamInnerDto queryParam = copyQueryParam(queryParamInnerDto);
        List<Dict> list = repository.findByCondition(queryParam);
        CopyOptions copyOptions = getCopyOptions(queryParam);
        String[] methodName = new String[]{queryParam.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        return list.stream().map(dict -> GXCommonUtils.convertSourceToTarget(dict, targetClazz, methodName[0], copyOptions)).collect(Collectors.toList());
    }

    @Override
    public <E> E findSingleFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
        if (Objects.isNull(queryParamInnerDto) || Objects.isNull(targetClazz)) {
            throw new GXBusinessException("queryParamInnerDto and targetClazz cannot be null");
        }
        GXBaseQueryParamInnerDto queryParam = copyQueryParam(queryParamInnerDto);
        if (CollUtil.isEmpty(queryParam.getColumns()) || queryParam.getColumns().size() != 1) {
            throw new GXBusinessException("columns size must be exactly 1");
        }
        queryParam.setLimit(1);
        String column = queryParam.getColumns().toArray(new String[0])[0];
        String tableName = queryParam.getTableName();
        if (CharSequenceUtil.isEmpty(tableName)) {
            tableName = getTableName();
            queryParam.setTableName(tableName);
        }
        Dict dict = repository.findOneByCondition(queryParam);
        if (Objects.isNull(dict)) {
            return null;
        }
        Object o = readColumnValue(dict, column);
        return Convert.convert(targetClazz, o);
    }

    @Override
    public <E> List<E> findSingleFieldLstByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
        if (Objects.isNull(queryParamInnerDto) || Objects.isNull(targetClazz)) {
            throw new GXBusinessException("queryParamInnerDto and targetClazz cannot be null");
        }
        GXBaseQueryParamInnerDto queryParam = copyQueryParam(queryParamInnerDto);
        if (CollUtil.isEmpty(queryParam.getColumns()) || queryParam.getColumns().size() != 1) {
            throw new GXBusinessException("columns size must be exactly 1");
        }
        String column = queryParam.getColumns().toArray(new String[0])[0];
        String tableName = queryParam.getTableName();
        if (CharSequenceUtil.isEmpty(tableName)) {
            tableName = getTableName();
            queryParam.setTableName(tableName);
        }
        List<Dict> dictList = repository.findByCondition(queryParam);
        ArrayList<E> lst = new ArrayList<>();
        dictList.forEach(dict -> {
            Object o = readColumnValue(dict, column);
            if (Objects.nonNull(o)) {
                lst.add(Convert.convert(targetClazz, o));
            }
        });
        return lst;
    }

    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodName, Object... params) {
        return findByCallMapperMethod(mapperMethodName, null, null, params);
    }

    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodName, String convertMethodName, CopyOptions copyOptions, Object... params) {
        Object o = callMethod(baseMapper, mapperMethodName, params);
        if (Objects.isNull(o)) {
            return Collections.emptyList();
        }
        return GXCommonUtils.convertSourceListToTargetList((Collection<?>) o, GXCommonUtils.getGenericClassType(getClass(), 4), convertMethodName, copyOptions, Dict.create());
    }

    @Override
    public R findOneByCallMapperMethod(String mapperMethodName, Object... params) {
        return findOneByCallMapperMethod(mapperMethodName, null, null, params);
    }

    @Override
    public Long countByCondition(List<GXCondition<?>> conditions) {
        HashSet<String> columns = CollUtil.newHashSet("count(id) as cnt");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(getTableName()).columns(columns).condition(conditions).build();
        return countByCondition(queryParamInnerDto);
    }

    @Override
    public Long countByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        GXBaseQueryParamInnerDto queryParam = copyQueryParam(queryParamInnerDto);
        if (CollUtil.isEmpty(queryParam.getColumns())) {
            String tableNameAlias = queryParam.getTableNameAlias();
            String countField = CharSequenceUtil.isBlank(tableNameAlias)
                    ? "count(id) as cnt"
                    : CharSequenceUtil.format("count({}.id) as cnt", tableNameAlias);
            HashSet<String> columns = CollUtil.newHashSet(countField);
            queryParam.setColumns(columns);
        }
        if (CharSequenceUtil.isBlank(queryParam.getTableName())) {
            queryParam.setTableName(getTableName());
        }
        return findOneByCondition(queryParam, data -> data.getLong("cnt"));
    }

    @Override
    public R findOneByCallMapperMethod(String mapperMethodName, String convertMethodName, CopyOptions copyOptions, Object... params) {
        Object o = callMethod(baseMapper, mapperMethodName, params);
        if (Objects.isNull(o)) {
            return null;
        }
        return GXCommonUtils.convertSourceToTarget(o, GXCommonUtils.getGenericClassType(getClass(), 4), convertMethodName, copyOptions, Dict.create());
    }

    @Override
    public boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext constraintValidatorContext) {
        if (Objects.isNull(validateExistsDto)) {
            throw new GXBusinessException("Validate exists param must not be null");
        }
        GXValidateExistsDto query = GXValidateExistsDto.builder()
                .fieldName(validateExistsDto.getFieldName())
                .tableName(validateExistsDto.getTableName())
                .value(validateExistsDto.getValue())
                .spEL(validateExistsDto.getSpEL())
                .condition(validateExistsDto.getCondition())
                .groups(validateExistsDto.getGroups())
                .build();
        if (CharSequenceUtil.isEmpty(query.getTableName())) {
            query.setTableName(repository.getTableName());
        }
        return repository.validateExists(query, constraintValidatorContext);
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

    @Override
    public TableInfo getTableInfo() {
        Class<?> entityClass = GXCommonUtils.getGenericClassType(getClass(), 2);
        return TableInfoHelper.getTableInfo(entityClass);
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
            throw new GXBusinessException("Query param must not be null");
        }
        return GXQueryParamUtils.copy(source);
    }

    private <E> List<E> findByConditionPrepared(GXBaseQueryParamInnerDto queryParam, Function<Dict, E> rowMapper) {
        String tableName = queryParam.getTableName();
        if (CharSequenceUtil.isBlank(tableName)) {
            tableName = repository.getTableName();
            queryParam.setTableName(tableName);
        }
        List<Dict> list = repository.findByCondition(queryParam);
        return list.stream().map(rowMapper).collect(Collectors.toList());
    }
}
