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

/**
 * 业务基础Service
 *
 * @param <P>  仓库对象类型
 * @param <M>  Mapper类型
 * @param <T>  实体类型
 * @param <D>  DAO类型
 * @param <R>  响应对象类型
 * @param <ID> 实体的主键ID类型
 */
@Slf4j
public class GXMyBatisBaseServiceImpl<P extends GXMyBatisRepository<M, T, D, ID>, M extends GXBaseMapper<T>, T extends GXBaseModel, D extends GXMyBatisDao<M, T, ID>, R extends GXBaseDBResDto, ID extends Serializable> extends GXBusinessServiceImpl implements GXMyBatisBaseService<P, M, T, D, R, ID> {
    /**
     * 日志对象
     */
    @SuppressWarnings("all")
    private static final Logger LOGGER = LoggerFactory.getLogger(GXMyBatisBaseServiceImpl.class);

    /**
     * 仓库类型
     */
    @Autowired
    @SuppressWarnings("all")
    protected P repository;

    /**
     * 基础Mapper
     */
    @Autowired
    @SuppressWarnings("all")
    private M baseMapper;

    /**
     * 检测给定条件的记录是否存在
     *
     * @param tableName 数据库表名字
     * @param condition 条件
     * @return int
     */
    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        return repository.checkRecordIsExists(tableName, condition);
    }

    /**
     * 检测给定条件的记录是否存在
     *
     * @param condition 条件
     * @return int
     */
    @Override
    public boolean checkRecordIsExists(List<GXCondition<?>> condition) {
        return checkRecordIsExists(repository.getTableName(), condition);
    }

    /**
     * 通过SQL更新表中的数据
     *
     * @param tableName    表名字
     * @param updateFields 需要更新的数据
     * @param condition    更新条件
     * @return Integer
     */
    @Override
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        boolean b = checkRecordIsExists(tableName, condition);
        if (!b) {
            log.error("待更新的数据不存在!");
            return GXCommonConstant.DB_RECORD_NOT_FOUND;
        }
        // 判断是HTTP请求还是RPC请求
        if (GXCurrentRequestContextUtils.isHTTP() && GXCurrentRequestContextUtils.tokenExists()) {
            String loginUserName = getLoginUserName();
            if (CharSequenceUtil.isNotEmpty(loginUserName)) {
                List<String> updateFieldNameLst = new ArrayList<>();
                updateFields.forEach(field -> {
                    String fieldName = field.getFieldName();
                    updateFieldNameLst.add(fieldName);
                });
                // updateFields字段有可能是一个不可变List 所以需要将其变成一个可变的List
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

    /**
     * 通过SQL更新表中的数据
     *
     * @param updateFields 需要更新的数据
     * @param condition    更新条件
     * @return Integer
     */
    @Override
    public Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        return updateFieldByCondition(repository.getTableName(), updateFields, condition);
    }

    /**
     * 列表或者搜索(分页)
     *
     * @param queryParamReqDto 参数
     * @return GXPaginationResDto
     */
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
        if (!queryParamReqDto.isPaginateCount() && !lst.isEmpty() && total == 0) {
            total = getPaginateCount(queryParamReqDto);
        }
        return new GXPaginationResDto<>(lst, total, paginate.getPageSize(), paginate.getCurrentPage());
    }

    /**
     * 列表或者搜索(分页)
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return GXPaginationResDto
     */
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
        if (!masterQueryParamInnerDto.isPaginateCount() && !lst.isEmpty() && total == 0) {
            total = getUnionPaginateCount(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
        }
        return new GXPaginationResDto<>(lst, total, paginate.getPageSize(), paginate.getCurrentPage());
    }

    /**
     * 通过条件查询列表信息
     *
     * @param queryParamInnerDto 搜索条件
     * @return List
     */
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

    /**
     * 通过条件查询列表信息
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return List
     */
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

    /**
     * 通过条件查询列表信息
     *
     * @param queryParamInnerDto 查询条件
     * @param rowMapper          映射函数
     * @return List
     */
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

    /**
     * 通过条件查询列表信息
     *
     * @param tableName 表名字
     * @param columns   需要查询的字段
     * @param condition 搜索条件
     * @return List
     */
    @Override
    public List<R> findByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition) {
        return findByCondition(tableName, condition, columns, null, null);
    }

    /**
     * 通过条件查询列表信息
     *
     * @param condition 搜索条件
     * @return List
     */
    @Override
    public List<R> findByCondition(List<GXCondition<?>> condition) {
        return findByCondition(repository.getTableName(), CollUtil.newHashSet("*"), condition);
    }

    /**
     * 通过条件查询列表信息
     *
     * @param condition 搜索条件
     * @param extraData 额外数据
     * @return List
     */
    @Override
    public List<R> findByCondition(List<GXCondition<?>> condition, Object extraData) {
        HashSet<String> columns = CollUtil.newHashSet("*");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(repository.getTableName()).columns(columns).condition(condition).extraData(extraData).build();
        return findByCondition(queryParamInnerDto);
    }

    /**
     * 通过条件查询列表信息
     *
     * @param condition 搜索条件
     * @param columns   需要查询的列
     * @return List
     */
    @Override
    public List<R> findByCondition(List<GXCondition<?>> condition, Set<String> columns) {
        return findByCondition(repository.getTableName(), columns, condition);
    }

    /**
     * 通过条件查询列表信息
     *
     * @param tableName  表名字
     * @param condition  搜索条件
     * @param columns    需要查询的字段
     * @param orderField 排序字段
     * @param groupField 分组字段
     * @return List
     */
    @Override
    public List<R> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Map<String, String> orderField, Set<String> groupField) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(columns).condition(condition).orderByField(orderField).groupByField(groupField).build();
        return findByCondition(queryParamInnerDto);
    }

    /**
     * 通过条件查询列表信息
     *
     * @param tableName 表名字
     * @param condition 搜索条件
     * @return List
     */
    @Override
    public List<R> findByCondition(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(CollUtil.newHashSet("*")).condition(condition).build();
        return findByCondition(queryParamInnerDto);
    }

    /**
     * 通过条件查询列表信息
     *
     * @param condition  搜索条件
     * @param orderField 排序字段
     * @param groupField 分组字段
     * @return List
     */
    @Override
    public List<R> findByCondition(List<GXCondition<?>> condition, Map<String, String> orderField, Set<String> groupField) {
        return findByCondition(repository.getTableName(), condition, CollUtil.newHashSet("*"), orderField, groupField);
    }

    /**
     * 通过条件查询列表信息
     *
     * @param condition  搜索条件
     * @param orderField 排序字段
     * @return List
     */
    @Override
    public List<R> findByCondition(List<GXCondition<?>> condition, Map<String, String> orderField) {
        return findByCondition(condition, orderField, null);
    }

    /**
     * 通过条件获取一条数据
     *
     * @param queryParamInnerDto 搜索条件
     * @return 一条数据
     */
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

    /**
     * 通过条件获取一条数据
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return 匹配条件的数据
     */
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

    /**
     * 通过条件获取一条数据
     *
     * @param queryParamInnerDto 搜索条件
     * @param rowMapper          映射函数
     * @return 一条数据
     */
    @Override
    public <E> E findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper) {
        Dict dict = repository.findOneByCondition(queryParamInnerDto);
        if (Objects.isNull(dict)) {
            return null;
        }
        return rowMapper.apply(dict);
    }

    /**
     * 通过条件查询列表信息
     *
     * @param tableName  表名字
     * @param condition  搜索条件
     * @param columns    需要查询的字段
     * @param orderField 排序字段
     * @param groupField 分组字段
     * @return List
     */
    @Override
    public R findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Map<String, String> orderField, Set<String> groupField) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(columns).condition(condition).orderByField(orderField).groupByField(groupField).build();
        return findOneByCondition(queryParamInnerDto);
    }

    /**
     * 通过条件获取一条数据
     *
     * @param tableName 表名字
     * @param columns   需要查询的字段
     * @param condition 搜索条件
     * @param extraData 额外参数
     * @return 一条数据
     */
    @Override
    public R findOneByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition, Object extraData) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(columns).condition(condition).extraData(extraData).build();
        return findOneByCondition(queryParamInnerDto);
    }

    /**
     * 通过条件获取一条数据
     *
     * @param tableName 表名字
     * @param condition 搜索条件
     * @return 一条数据
     */
    @Override
    public R findOneByCondition(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).columns(CollUtil.newHashSet("*")).condition(condition).extraData(Dict.create()).build();
        return findOneByCondition(queryParamInnerDto);
    }

    /**
     * 通过条件获取一条数据
     *
     * @param tableName 表名字
     * @param columns   需要查询的字段
     * @param condition 搜索条件
     * @return 一条数据
     */
    @Override
    public R findOneByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition) {
        return findOneByCondition(tableName, columns, condition, Dict.create());
    }

    /**
     * 通过条件获取一条数据
     *
     * @param condition 搜索条件
     * @param extraData 额外参数
     * @return 一条数据
     */
    @Override
    public R findOneByCondition(List<GXCondition<?>> condition, Object extraData) {
        return findOneByCondition(repository.getTableName(), CollUtil.newHashSet("*"), condition, extraData);
    }

    /**
     * 通过条件获取一条数据
     *
     * @param condition 搜索条件
     * @return 一条数据
     */
    @Override
    public R findOneByCondition(List<GXCondition<?>> condition) {
        return findOneByCondition(repository.getTableName(), CollUtil.newHashSet("*"), condition);
    }

    /**
     * 通过条件获取一条数据
     *
     * @param condition 搜索条件
     * @param columns   字段集合
     * @return 一条数据
     */
    @Override
    public R findOneByCondition(List<GXCondition<?>> condition, Set<String> columns) {
        return findOneByCondition(repository.getTableName(), columns, condition);
    }

    /**
     * 通过条件获取一条数据
     *
     * @param condition 搜索条件
     * @param columns   字段集合
     * @param extraData 额外参数
     * @return 一条数据
     */
    @Override
    public R findOneByCondition(List<GXCondition<?>> condition, Set<String> columns, Object extraData) {
        return findOneByCondition(repository.getTableName(), columns, condition, extraData);
    }

    /**
     * 创建或者更新
     *
     * @param entity 数据实体
     * @return ID
     */
    @Override
    public ID updateOrCreate(T entity) {
        return updateOrCreate(entity, Collections.emptyList());
    }

    /**
     * 创建或者更新
     *
     * @param entity    数据实体
     * @param condition 更新条件
     * @return ID
     */
    @Override
    public ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        return repository.updateOrCreate(entity, condition);
    }

    /**
     * 创建或者更新
     *
     * @param req         请求参数
     * @param condition   条件
     * @param copyOptions 复制可选项
     * @return ID
     */
    @Override
    public <Q extends GXBaseReqDto> ID updateOrCreate(Q req, List<GXCondition<?>> condition, CopyOptions copyOptions) {
        Class<T> targetClazz = GXCommonUtils.getGenericClassType(getClass(), 2);
        T entity = convertSourceToTarget(req, targetClazz, GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME, copyOptions);
        return updateOrCreate(entity, condition);
    }

    /**
     * 创建或者更新
     *
     * @param req         请求参数
     * @param copyOptions 复制可选项
     * @return ID
     */
    @Override
    public <Q extends GXBaseReqDto> ID updateOrCreate(Q req, CopyOptions copyOptions) {
        return updateOrCreate(req, Collections.emptyList(), copyOptions);
    }

    /**
     * 创建或者更新
     *
     * @param req 请求参数
     * @return ID
     */
    @Override
    public <Q extends GXBaseReqDto> ID updateOrCreate(Q req) {
        return updateOrCreate(req, CopyOptions.create());
    }

    /**
     * 复制一条数据
     *
     * @param conditions  复制的条件
     * @param replaceData 需要替换的数据
     * @param extraData   额外数据
     * @return 新数据ID
     */
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

    /**
     * 复制一条数据
     *
     * @param conditions  复制的条件
     * @param replaceData 需要替换的数据
     * @return 新数据ID
     */
    @Override
    public ID copyOneData(List<GXCondition<?>> conditions, Dict replaceData) {
        return copyOneData(conditions, replaceData, Dict.create());
    }

    /**
     * 根据条件软(逻辑)删除
     *
     * @param tableName       表名
     * @param updateFieldList 软删除时需要同时更新的字段
     * @param condition       删除条件
     * @param extraData       额外数据
     * @return 影响行数
     */
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

    /**
     * 根据条件软(逻辑)删除
     *
     * @param tableName 表名
     * @param condition 删除条件
     * @param extraData 额外数据
     * @return 影响行数
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        return deleteSoftCondition(tableName, CollUtil.newArrayList(), condition, extraData);
    }

    /**
     * 根据条件软(逻辑)删除
     *
     * @param tableName 表名
     * @param condition 删除条件
     * @return 影响行数
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition) {
        return deleteSoftCondition(tableName, condition, Dict.create());
    }

    /**
     * 根据条件软(逻辑)删除
     *
     * @param condition 删除条件
     * @return 影响行数
     */
    @Override
    public Integer deleteSoftCondition(List<GXCondition<?>> condition) {
        return deleteSoftCondition(repository.getTableName(), condition);
    }

    /**
     * 根据条件删除
     *
     * @param tableName 表名
     * @param condition 删除条件
     * @return 影响行数
     */
    @Override
    public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        return repository.deleteCondition(tableName, condition);
    }

    /**
     * 根据条件删除
     *
     * @param condition 删除条件
     * @return 影响行数
     */
    @Override
    public Integer deleteCondition(List<GXCondition<?>> condition) {
        return deleteCondition(repository.getTableName(), condition);
    }

    /**
     * 查询指定字段的值
     * <pre>
     *     {@code findFieldByCondition("s_admin", condition1, CollUtil.newHashSet("nickname", "username"), Dict.class);}
     * </pre>
     *
     * @param condition   查询条件
     * @param columns     字段名字集合
     * @param targetClazz 值的类型
     * @return 返回指定的类型的值对象
     */
    @Override
    public <E> List<E> findMultiFieldByCondition(List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz) {
        return findMultiFieldByCondition(repository.getTableName(), condition, columns, targetClazz);
    }

    /**
     * 查询指定字段的值
     * <pre>
     *     {@code findFieldByCondition("s_admin", condition1, CollUtil.newHashSet("nickname", "username"), Dict.class);}
     * </pre>
     *
     * @param tableName   表名字
     * @param condition   查询条件
     * @param columns     字段名字集合
     * @param targetClazz 值的类型
     * @return 返回指定的类型的值对象
     */
    @Override
    public <E> List<E> findMultiFieldByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).tableNameAlias(tableName).condition(condition).columns(columns).build();
        return findMultiFieldByCondition(queryParamInnerDto, targetClazz);
    }

    /**
     * 查询指定字段的值
     *
     * @param queryParamInnerDto 查询参数
     * @param targetClazz        返回数据的类型
     * @return 返回指定的类型的值对象
     */
    public <E> List<E> findMultiFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
        List<Dict> list = repository.findByCondition(queryParamInnerDto);
        CopyOptions copyOptions = getCopyOptions(queryParamInnerDto);
        String[] methodName = new String[]{queryParamInnerDto.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        return list.stream().map(dict -> GXCommonUtils.convertSourceToTarget(dict, targetClazz, methodName[0], copyOptions)).collect(Collectors.toList());
    }

    /**
     * 获取一条记录的指定单字段
     *
     * @param queryParamInnerDto 查询条件
     * @param targetClazz        返回的类型
     * @return 指定的类型
     */
    @Override
    public <E> E findSingleFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
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
        Object o = Optional.ofNullable(dict.get(column)).orElse(dict.get(CharSequenceUtil.toUnderlineCase(column)));
        return Convert.convert(targetClazz, o);
    }

    /**
     * 获取指定单字段的列表
     *
     * @param queryParamInnerDto 条件
     * @param targetClazz        目标类型
     * @return 指定的类型
     */
    @Override
    public <E> List<E> findSingleFieldLstByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz) {
        if (queryParamInnerDto.getColumns().size() != 1) {
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
            Object o = Optional.ofNullable(dict.get(column)).orElse(dict.get(CharSequenceUtil.toCamelCase(column)));
            if (Objects.nonNull(o)) {
                lst.add(Convert.convert(targetClazz, o));
            }
        });
        return lst;
    }

    /**
     * 动态调用指定的指定Class中的方法
     *
     * @param mapperMethodName 需要调用的方法
     * @param params           参数
     * @return Object
     */
    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodName, Object... params) {
        return findByCallMapperMethod(mapperMethodName, null, null, params);
    }

    /**
     * 动态调用指定的指定Class中的方法
     *
     * @param mapperMethodName  需要调用的方法
     * @param convertMethodName 结果集转换函数名字
     * @param copyOptions       转换选项
     * @param params            参数
     * @return Collection
     */
    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodName, String convertMethodName, CopyOptions copyOptions, Object... params) {
        Object o = callMethod(baseMapper, mapperMethodName, params);
        if (Objects.isNull(o)) {
            return Collections.emptyList();
        }
        return GXCommonUtils.convertSourceListToTargetList((Collection<?>) o, GXCommonUtils.getGenericClassType(getClass(), 4), convertMethodName, copyOptions, Dict.create());
    }

    /**
     * 动态调用指定的指定Class中的方法
     *
     * @param mapperMethodName 需要调用的方法
     * @param params           参数
     * @return Object
     */
    @Override
    public R findOneByCallMapperMethod(String mapperMethodName, Object... params) {
        return findOneByCallMapperMethod(mapperMethodName, null, null, params);
    }

    /**
     * 根据条件统计数量
     *
     * @param conditions 查询条件
     * @return 查询到的数量
     */
    @Override
    public Long countByCondition(List<GXCondition<?>> conditions) {
        HashSet<String> columns = CollUtil.newHashSet("count(id) as cnt");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(getTableName()).columns(columns).condition(conditions).build();
        return countByCondition(queryParamInnerDto);
    }

    /**
     * 根据条件统计数量
     *
     * @param queryParamInnerDto 查询条件
     * @return 查询到的数量
     */
    @Override
    public Long countByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        if (CollUtil.isEmpty(queryParamInnerDto.getColumns())) {
            HashSet<String> columns = CollUtil.newHashSet(CharSequenceUtil.format("count({}.id) as cnt", queryParamInnerDto.getTableNameAlias()));
            queryParamInnerDto.setColumns(columns);
        }
        if (CharSequenceUtil.isBlank(queryParamInnerDto.getTableName())) {
            queryParamInnerDto.setTableName(getTableName());
        }
        return findOneByCondition(queryParamInnerDto, data -> data.getLong("cnt"));
    }

    /**
     * 动态调用指定的指定Class中的方法
     *
     * @param mapperMethodName  需要调用的方法
     * @param convertMethodName 结果集转换函数名字
     * @param copyOptions       转换选项
     * @param params            参数
     * @return Object
     */
    @Override
    public R findOneByCallMapperMethod(String mapperMethodName, String convertMethodName, CopyOptions copyOptions, Object... params) {
        Object o = callMethod(baseMapper, mapperMethodName, params);
        if (Objects.isNull(o)) {
            return null;
        }
        return GXCommonUtils.convertSourceToTarget(o, GXCommonUtils.getGenericClassType(getClass(), 4), convertMethodName, copyOptions, Dict.create());
    }

    /**
     * 实现验证注解(返回true表示数据已经存在)
     *
     * @param validateExistsDto          business dto param
     * @param constraintValidatorContext The ValidatorContext
     * @return boolean
     */
    @Override
    public boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext constraintValidatorContext) {
        if (CharSequenceUtil.isEmpty(validateExistsDto.getTableName())) {
            validateExistsDto.setTableName(repository.getTableName());
        }
        return repository.validateExists(validateExistsDto, constraintValidatorContext);
    }

    /**
     * 获取MyBatis Plus数据表的信息
     *
     * @return TableInfo
     */
    @Override
    public TableInfo getTableInfo() {
        Class<?> entityClass = GXCommonUtils.getGenericClassType(getClass(), 2);
        return TableInfoHelper.getTableInfo(entityClass);
    }

    /**
     * 获取 Primary Key
     *
     * @return String
     */
    @Override
    public String getPrimaryKeyName(T entity) {
        return repository.getPrimaryKeyName(entity);
    }

    /**
     * 获取表的名字
     *
     * @return String
     */
    @Override
    public String getTableName() {
        return repository.getTableName();
    }
}
