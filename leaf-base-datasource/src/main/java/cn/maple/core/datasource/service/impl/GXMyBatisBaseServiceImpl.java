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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class GXMyBatisBaseServiceImpl<P extends GXMyBatisRepository<M, T, D, ID>, M extends GXBaseMapper<T>, T extends GXBaseModel, D extends GXMyBatisDao<M, T, ID>, R extends GXBaseDBResDto, ID extends Serializable> extends GXBusinessServiceImpl implements GXMyBatisBaseService<P, M, T, D, R, ID> {
    @SuppressWarnings("all")
    private static final Logger LOGGER = LoggerFactory.getLogger(GXMyBatisBaseServiceImpl.class);

    @Autowired
    @SuppressWarnings("all")
    protected P repository;

    @Autowired
    @SuppressWarnings("all")
    private M baseMapper;

    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
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
            throw new GXBusinessException("条件不能为空!");
        }
        if (CollUtil.isEmpty(updateFields)) {
            throw new GXBusinessException("updateFields cannot be empty");
        }
        boolean b = checkRecordIsExists(tableName, condition);
        if (!b) {
            log.error("待更新的数据不存在!");
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
        if (CharSequenceUtil.isBlank(queryParamReqDto.getRawSQL())) {
            if (CharSequenceUtil.isEmpty(queryParamReqDto.getTableName())) {
                queryParamReqDto.setTableName(repository.getTableName());
            }
            if (Objects.isNull(queryParamReqDto.getColumns())) {
                queryParamReqDto.setColumns(CollUtil.newHashSet("*"));
            }
        }
        if (Objects.isNull(queryParamReqDto.getMethodName())) {
            queryParamReqDto.setMethodName(GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME);
        }
        CopyOptions copyOptions = getCopyOptions(queryParamReqDto);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        GXPaginationResDto<Dict> paginate = repository.paginate(queryParamReqDto);
        List<R> lst = paginate.getRecords().stream().map(dict -> {
            Object extraData = Optional.ofNullable(queryParamReqDto.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, queryParamReqDto.getMethodName(), copyOptions, extraData);
        }).collect(Collectors.toList());
        long total = paginate.getTotal();
        if (!queryParamReqDto.isPaginateCount()) {
            total = getPaginateCount(queryParamReqDto);
        }
        return new GXPaginationResDto<>(lst, total, paginate.getPageSize(), paginate.getCurrentPage());
    }

    @Override
    public GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isBlank(masterQueryParamInnerDto.getRawSQL())) {
            if (CharSequenceUtil.isEmpty(masterQueryParamInnerDto.getTableName())) {
                masterQueryParamInnerDto.setTableName(repository.getTableName());
            }
            if (Objects.isNull(masterQueryParamInnerDto.getColumns())) {
                masterQueryParamInnerDto.setColumns(CollUtil.newHashSet("*"));
            }
        }
        if (Objects.isNull(masterQueryParamInnerDto.getMethodName())) {
            masterQueryParamInnerDto.setMethodName(GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME);
        }
        CopyOptions copyOptions = getCopyOptions(masterQueryParamInnerDto);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        GXPaginationResDto<Dict> paginate = repository.paginate(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
        List<R> lst = paginate.getRecords().stream().map(dict -> {
            Object extraData = Optional.ofNullable(masterQueryParamInnerDto.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, masterQueryParamInnerDto.getMethodName(), copyOptions, extraData);
        }).collect(Collectors.toList());
        long total = paginate.getTotal();
        if (!masterQueryParamInnerDto.isPaginateCount()) {
            total = getUnionPaginateCount(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
        }
        return new GXPaginationResDto<>(lst, total, paginate.getPageSize(), paginate.getCurrentPage());
    }

    @Override
    public List<R> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        CopyOptions copyOptions = getCopyOptions(queryParamInnerDto);
        String[] methodName = new String[]{queryParamInnerDto.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        Function<Dict, R> rowMapper = dict -> {
            Object extraData = Optional.ofNullable(queryParamInnerDto.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        return findByCondition(queryParamInnerDto, rowMapper);
    }

    @Override
    public List<R> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        CopyOptions copyOptions = getCopyOptions(masterQueryParamInnerDto);
        String[] methodName = new String[]{masterQueryParamInnerDto.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        Function<Dict, R> rowMapper = dict -> {
            Object extraData = Optional.ofNullable(masterQueryParamInnerDto.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        List<Dict> lst = repository.findByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
        return lst.stream().map(rowMapper).collect(Collectors.toList());
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
        String[] methodName = new String[]{queryParamInnerDto.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Object extraData = Optional.ofNullable(queryParamInnerDto.getExtraData()).orElse(Dict.class);
        CopyOptions copyOptions = getCopyOptions(queryParamInnerDto);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        Function<Dict, R> rowMapper = dict -> {
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        return findOneByCondition(queryParamInnerDto, rowMapper);
    }

    @Override
    public R findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        String[] methodName = new String[]{masterQueryParamInnerDto.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        Object extraData = Optional.ofNullable(masterQueryParamInnerDto.getExtraData()).orElse(Dict.class);
        CopyOptions copyOptions = getCopyOptions(masterQueryParamInnerDto);
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 4);
        Function<Dict, R> rowMapper = dict -> {
            return GXCommonUtils.convertSourceToTarget(dict, genericClassType, methodName[0], copyOptions, extraData);
        };
        Dict dict = repository.findOneByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
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
            throw new GXDBNotExistsException("待拷贝的数据不存在!!");
        }
        T entity = GXCommonUtils.convertSourceToTarget(oneData, GXCommonUtils.getGenericClassType(getClass(), 2), null, null, extraData);
        assert entity != null;
        String setPrimaryKeyMethodName = CharSequenceUtil.format("set{}", CharSequenceUtil.upperFirst(getPrimaryKeyName(entity)));
        Method method = ReflectUtil.getMethod(entity.getClass(), setPrimaryKeyMethodName, GXCommonUtils.getGenericClassType(getClass(), 5));
        if (Objects.isNull(method)) {
            throw new GXBusinessException(CharSequenceUtil.format("方法{}不存在", setPrimaryKeyMethodName));
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
            throw new GXBusinessException("条件不能为空!");
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
            throw new GXBusinessException("条件不能为空!");
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
        List<Dict> list = repository.findByCondition(queryParamInnerDto);
        CopyOptions copyOptions = getCopyOptions(queryParamInnerDto);
        String[] methodName = new String[]{queryParamInnerDto.getMethodName()};
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
        if (CollUtil.isEmpty(queryParamInnerDto.getColumns()) || queryParamInnerDto.getColumns().size() != 1) {
            throw new GXBusinessException("columns size must be exactly 1");
        }
        queryParamInnerDto.setLimit(1);
        String column = queryParamInnerDto.getColumns().toArray(new String[0])[0];
        String tableName = queryParamInnerDto.getTableName();
        if (CharSequenceUtil.isEmpty(tableName)) {
            tableName = getTableName();
            queryParamInnerDto.setTableName(tableName);
        }
        Dict dict = repository.findOneByCondition(queryParamInnerDto);
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
        if (CollUtil.isEmpty(queryParamInnerDto.getColumns()) || queryParamInnerDto.getColumns().size() != 1) {
            throw new GXBusinessException("字段列长度只能为1!!!");
        }
        String column = queryParamInnerDto.getColumns().toArray(new String[0])[0];
        String tableName = queryParamInnerDto.getTableName();
        if (CharSequenceUtil.isEmpty(tableName)) {
            tableName = getTableName();
            queryParamInnerDto.setTableName(tableName);
        }
        List<Dict> dictList = repository.findByCondition(queryParamInnerDto);
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
        if (CollUtil.isEmpty(queryParamInnerDto.getColumns())) {
            String tableNameAlias = queryParamInnerDto.getTableNameAlias();
            String countField = CharSequenceUtil.isBlank(tableNameAlias)
                    ? "count(id) as cnt"
                    : CharSequenceUtil.format("count({}.id) as cnt", tableNameAlias);
            HashSet<String> columns = CollUtil.newHashSet(countField);
            queryParamInnerDto.setColumns(columns);
        }
        if (CharSequenceUtil.isBlank(queryParamInnerDto.getTableName())) {
            queryParamInnerDto.setTableName(getTableName());
        }
        return findOneByCondition(queryParamInnerDto, data -> data.getLong("cnt"));
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
        if (CharSequenceUtil.isEmpty(validateExistsDto.getTableName())) {
            validateExistsDto.setTableName(repository.getTableName());
        }
        return repository.validateExists(validateExistsDto, constraintValidatorContext);
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
}
