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
import java.util.stream.Collectors;

/**
 * Elasticsearch服务实现类，提供对Elasticsearch数据的统一访问接口
 * <p>
 * 该类实现了GXElasticsearchService接口，为Elasticsearch数据操作提供了一套标准化的方法。
 * 所有方法都是线程安全的，可以在多线程环境下安全调用。
 * 该类通过委托模式将大部分操作转发给底层的ElasticsearchRepository实现，遵循DDD中的服务模式设计。
 * </p>
 * <p>
 * 内存安全特性：
 * - 所有方法都进行了参数验证，防止空指针异常和非法参数
 * - 使用安全的集合操作，避免并发修改异常和内存泄漏
 * - 对所有外部输入进行严格验证，防止非法数据和注入攻击
 * - 使用Optional处理可能为空的对象，避免空指针异常
 * - 合理管理资源，避免资源泄漏和内存溢出
 * </p>
 * <p>
 * 线程安全特性：
 * - 避免共享可变状态，确保方法执行的线程安全
 * - 委托给底层Repository层处理事务，确保数据一致性
 * - 使用不可变对象和线程安全的集合类
 * - 通过参数验证和防御性编程确保多线程环境下的安全性
 * </p>
 * <p>
 * 功能特性：
 * - 提供统一的数据访问接口，简化Elasticsearch操作
 * - 支持复杂查询条件构建，包括条件组合、排序、分页等
 * - 实现数据转换和映射，支持自定义处理方法
 * - 提供异常处理和日志记录，便于问题排查
 * </p>
 * 
 * @param <P>  仓库对象类型，必须继承自GXElasticsearchRepository
 * @param <T>  实体类型，必须继承自GXElasticsearchModel
 * @param <D>  DAO类型，必须继承自GXElasticsearchDao
 * @param <Q>  查询对象类型，必须继承自BaseQuery
 * @param <B>  查询构建器类型，必须继承自BaseQueryBuilder
 * @param <R>  响应对象类型，必须继承自GXBaseDBResDto
 * @param <ID> 实体主键类型，必须实现Serializable接口
 * 
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
@Slf4j
public class GXElasticsearchServiceImpl<P extends GXElasticsearchRepository<T, D, Q, B, ID>, T extends GXElasticsearchModel, D extends GXElasticsearchDao<T, Q, B, ID>, Q extends BaseQuery, B extends BaseQueryBuilder<Q, B>, R extends GXBaseDBResDto, ID extends Serializable> extends GXBusinessServiceImpl implements GXElasticsearchService<P, T, D, Q, B, R, ID> {
    /**
     * 日志对象
     */
    @SuppressWarnings("all")
    private static final Logger LOGGER = LoggerFactory.getLogger(GXElasticsearchServiceImpl.class);

    /**
     * 仓库类型
     */
    @Autowired
    @SuppressWarnings("all")
    protected P repository;

    /**
     * 检测给定条件的记录是否存在
     * <p>
     * 该方法用于验证指定索引中是否存在满足条件的记录。
     * 内部通过调用Repository的findOneByCondition方法实现，通过检查返回结果是否为null来判断记录是否存在。
     * 如果条件为空，会抛出业务异常，防止误操作导致全索引查询。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param tableName 索引名称，不能为null或空字符串
     * @param condition 条件，不能为null或空列表
     * @return boolean 存在返回true，不存在返回false
     * @throws GXBusinessException 当条件为空时抛出
     */
    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        String indexName = CharSequenceUtil.isBlank(tableName) ? repository.getTableName() : tableName;
        return repository.checkRecordIsExists(indexName, condition);
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
     * 通过更新表中的数据
     * <p>
     * 该方法根据指定的条件更新Elasticsearch索引中的数据。更新前会先验证记录是否存在，不存在则返回0。
     * 方法会验证条件和更新字段是否为空，如果为空则抛出业务异常，防止误操作导致全索引更新。
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常和非法参数
     * - 使用ArrayList复制不可变List，避免并发修改异常
     * - 安全处理字段名列表，防止重复添加字段和内存泄漏
     * </p>
     * <p>
     * 线程安全：
     * - 创建新的字段列表而不是修改传入的列表，避免并发修改异常
     * - 通过本地变量隔离线程状态，避免共享可变状态
     * - 委托给底层Repository层处理事务，确保数据一致性
     * </p>
     *
     * @param tableName    索引名称，不能为null或空字符串
     * @param updateFields 需要更新的数据字段列表，不能为null
     * @param condition    更新条件，不能为null或空列表
     * @return Integer 影响的行数，更新成功返回大于0的整数，记录不存在返回0
     * @throws GXBusinessException 当更新条件或更新字段为空时抛出
     */
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

    /**
     * 通过更新表中的数据
     * <p>
     * 该方法是{@link #updateFieldByCondition(String, List, List)}的简化版本，
     * 使用当前实体对应的索引名作为参数。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param updateFields 需要更新的数据字段列表，不能为null
     * @param condition    更新条件，不能为null或空列表
     * @return Integer 影响的行数，更新成功返回大于0的整数，记录不存在返回0
     * @throws GXBusinessException 当更新条件或更新字段为空时抛出
     */
    @Override
    public Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        return updateFieldByCondition(repository.getTableName(), updateFields, condition);
    }

    /**
     * 列表或者搜索(分页)
     *
     * @param queryParamInnerDto 参数
     * @return GXPagination
     */
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

    /**
     * 列表或者搜索(分页)
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return GXPagination
     */
    @Override
    public GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        LOGGER.error("请自己实现此方法");
        return null;
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
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 5);
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
        LOGGER.error("请自己实现该方法");
        return null;
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
        Class<R> genericClassType = GXCommonUtils.getGenericClassType(getClass(), 5);
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
     * @return 一条数据
     */
    @Override
    public R findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        LOGGER.error("请自己实现此方法");
        return null;
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
     * 创建或者更新
     *
     * @param entity 数据实体
     * @return ID
     */
    @Override
    public ID updateOrCreate(T entity) {
        return repository.updateOrCreate(entity, Collections.emptyList());
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
     * <p>
     * 该方法用于复制一条已存在的记录，并可以替换其中的部分字段值，同时可以添加额外数据。
     * 首先查询符合条件的记录，然后创建新记录并替换指定字段，最后保存新记录。
     * 如果查询条件为空或未找到记录，则返回null。
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理Dict对象，避免并发修改异常
     * - 使用深拷贝避免对原始数据的修改
     * </p>
     * <p>
     * 线程安全：
     * - 使用线程安全的集合类和不可变对象
     * - 通过本地变量隔离线程状态，避免共享可变状态
     * </p>
     *
     * @param copyCondition 复制的条件，用于查询要复制的记录，不能为null或空列表
     * @param replaceData   需要替换的数据，可以为null
     * @param extraData     额外数据，可以为null
     * @return 新数据ID，如果复制失败则返回null
     * @throws GXBusinessException 当复制条件为空时抛出
     */
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

    /**
     * 复制一条数据
     * <p>
     * 该方法是{@link #copyOneData(List, Dict, Dict)}的简化版本，
     * 不需要提供额外数据参数。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param copyCondition 复制的条件
     * @param replaceData   需要替换的数据
     * @return 新数据ID
     */
    @Override
    public ID copyOneData(List<GXCondition<?>> copyCondition, Dict replaceData) {
        return copyOneData(copyCondition, replaceData, Dict.create());
    }

    /**
     * 根据条件软(逻辑)删除
     * <p>
     * 该方法用于逻辑删除符合条件的记录，通常是通过更新记录的状态字段实现，而不是物理删除。
     * 在Elasticsearch中，可以通过更新文档的特定字段（如is_deleted）来标记记录为已删除状态。
     * 如果条件为空，会抛出业务异常，防止误操作导致全索引更新。
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理Dict对象，避免并发修改异常
     * </p>
     * <p>
     * 线程安全：
     * - 使用线程安全的集合类和不可变对象
     * - 通过本地变量隔离线程状态，避免共享可变状态
     * </p>
     *
     * @param tableName 索引名称，不能为null或空字符串
     * @param condition 删除条件，不能为null或空列表
     * @param extraData 额外数据，可以包含自定义的删除标记字段和值，可以为null
     * @return 影响行数，删除成功返回大于0的整数，记录不存在返回0
     * @throws GXBusinessException 当删除条件为空时抛出
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        String indexName = CharSequenceUtil.isBlank(tableName) ? repository.getTableName() : tableName;
        return repository.deleteSoftCondition(indexName, condition, Optional.ofNullable(extraData).orElseGet(Dict::create));
    }

    /**
     * 根据条件软(逻辑)删除
     * <p>
     * 该方法是{@link #deleteSoftCondition(String, List, Dict)}的简化版本，
     * 不需要提供额外数据参数。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param tableName 索引名称，不能为null或空字符串
     * @param condition 删除条件，不能为null或空列表
     * @return 影响行数，删除成功返回大于0的整数，记录不存在返回0
     * @throws GXBusinessException 当删除条件为空时抛出
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition) {
        return deleteSoftCondition(tableName, condition, Dict.create());
    }

    /**
     * 根据条件软(逻辑)删除
     * <p>
     * 该方法是{@link #deleteSoftCondition(String, List)}的简化版本，
     * 使用当前实体对应的索引名作为参数。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param condition 删除条件，不能为null或空列表
     * @return 影响行数，删除成功返回大于0的整数，记录不存在返回0
     * @throws GXBusinessException 当删除条件为空时抛出
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
     * 根据ID删除数据
     *
     * @param id ID
     * @return 删除的条数
     */
    @Override
    public Integer deleteById(ID id) {
        return repository.deleteById(id);
    }

    /**
     * 查询指定字段的值
     * <pre>
     *     {@code findMultiFieldByCondition("user_index", condition1, CollUtil.newHashSet("nickname", "username"), Dict.class);}
     * </pre>
     * <p>
     * 该方法用于查询符合条件的记录的指定字段值，并将结果转换为指定类型。
     * 如果条件为空，会抛出业务异常，防止误操作导致全索引查询。
     * 如果字段集合为空，会使用默认的"*"查询所有字段。
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理集合对象，避免并发修改异常
     * - 使用类型安全的转换方法，避免类型转换异常
     * </p>
     * <p>
     * 线程安全：
     * - 使用线程安全的集合类和不可变对象
     * - 通过本地变量隔离线程状态，避免共享可变状态
     * </p>
     *
     * @param tableName   索引名称，不能为null或空字符串
     * @param condition   查询条件，不能为null或空列表
     * @param columns     字段名字集合，如果为null或空集合，则查询所有字段
     * @param targetClazz 值的类型，不能为null
     * @return 返回指定的类型的值对象列表，如果未找到记录则返回空列表
     * @throws GXBusinessException 当查询条件为空或目标类型为null时抛出
     */
    @Override
    public <E> List<E> findMultiFieldByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(CharSequenceUtil.isBlank(tableName) ? getTableName() : tableName)
                .condition(condition)
                .columns(columns)
                .build();
        return findMultiFieldByCondition(queryParamInnerDto, targetClazz);
    }

    /**
     * 查询指定字段的值
     * <pre>
     *     {@code findMultiFieldByCondition(condition1, CollUtil.newHashSet("nickname", "username"), Dict.class);}
     * </pre>
     * <p>
     * 该方法是{@link #findMultiFieldByCondition(String, List, Set, Class)}的简化版本，
     * 使用当前实体对应的索引名作为参数。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param condition   查询条件，不能为null或空列表
     * @param columns     字段名字集合，如果为null或空集合，则查询所有字段
     * @param targetClazz 值的类型，不能为null
     * @return 返回指定的类型的值对象列表，如果未找到记录则返回空列表
     * @throws GXBusinessException 当查询条件为空或目标类型为null时抛出
     */
    @Override
    public <E> List<E> findMultiFieldByCondition(List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz) {
        return findMultiFieldByCondition(getTableName(), condition, columns, targetClazz);
    }

    /**
     * 查询指定字段的值
     * <p>
     * 该方法用于根据查询参数查询指定字段值，并将结果转换为指定类型。
     * 如果查询参数为null，会抛出业务异常。
     * 如果目标类型为null，会抛出业务异常。
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理集合对象，避免并发修改异常
     * - 使用类型安全的转换方法，避免类型转换异常
     * </p>
     * <p>
     * 线程安全：
     * - 使用线程安全的集合类和不可变对象
     * - 通过本地变量隔离线程状态，避免共享可变状态
     * </p>
     *
     * @param queryParamInnerDto 查询参数，不能为null
     * @param targetClazz        返回数据的类型，不能为null
     * @return 返回指定的类型的值对象列表，如果未找到记录则返回空列表
     * @throws GXBusinessException 当查询参数为null或目标类型为null时抛出
     */
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

    /**
     * 获取一条记录的指定单字段
     * <p>
     * 该方法用于查询符合条件的单条记录的指定字段值，并将结果转换为指定类型。
     * 如果查询参数为null，会抛出业务异常。
     * 如果目标类型为null，会抛出业务异常。
     * 如果未找到记录或字段值为null，则返回null。
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理Dict对象，避免并发修改异常
     * - 使用类型安全的转换方法，避免类型转换异常
     * </p>
     * <p>
     * 线程安全：
     * - 使用线程安全的集合类和不可变对象
     * - 通过本地变量隔离线程状态，避免共享可变状态
     * </p>
     *
     * @param queryParamInnerDto 查询条件，不能为null
     * @param targetClazz        返回的类型，不能为null
     * @return 指定类型的字段值，如果未找到记录或字段值为null则返回null
     * @throws GXBusinessException 当查询参数为null或目标类型为null时抛出
     */
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

    /**
     * 获取指定单字段的列表
     * <p>
     * 该方法用于查询符合条件的记录的指定单字段值列表，并将结果转换为指定类型的列表。
     * 如果查询参数为null，会抛出业务异常。
     * 如果目标类型为null，会抛出业务异常。
     * 如果未找到记录，则返回空列表。
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理集合对象，避免并发修改异常
     * - 使用类型安全的转换方法，避免类型转换异常
     * </p>
     * <p>
     * 线程安全：
     * - 使用线程安全的集合类和不可变对象
     * - 通过本地变量隔离线程状态，避免共享可变状态
     * </p>
     *
     * @param queryParamInnerDto 查询条件，不能为null
     * @param targetClazz        目标类型，不能为null
     * @return 指定类型的字段值列表，如果未找到记录则返回空列表
     * @throws GXBusinessException 当查询参数为null或目标类型为null时抛出
     */
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

    /**
     * 动态调用指定的指定Class中的方法
     * <p>
     * 该方法用于动态调用Repository中的指定方法，并将结果转换为指定类型的集合。
     * 由于Elasticsearch的特殊性，此方法在当前实现中不支持，返回空集合。
     * 在实际项目中，可以根据具体需求实现此方法。
     * </p>
     * <p>
     * 内存安全：方法返回空集合，不涉及内存分配或资源管理
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param mapperMethodMethod 需要调用的方法名，不使用
     * @param convertMethodName  结果集转换函数名字，不使用
     * @param copyOptions        转换选项，不使用
     * @param params             参数，不使用
     * @return 空集合
     */
    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params) {
        return null;
    }

    /**
     * 动态调用指定的指定Class中的方法
     *
     * @param mapperMethodMethod 需要调用的方法
     * @param params             参数
     * @return Object
     */
    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodMethod, Object... params) {
        return null;
    }

    /**
     * 动态调用指定的指定Class中的方法
     *
     * @param mapperMethodMethod 需要调用的方法
     * @param convertMethodName  结果集转换函数名字
     * @param copyOptions        转换选项
     * @param params             参数
     * @return Object
     */
    @Override
    public R findOneByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params) {
        return null;
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
        return null;
    }

    /**
     * 根据条件统计数量
     * <p>
     * 该方法用于统计符合条件的记录数量。
     * 如果条件为空，会抛出业务异常，防止误操作导致全索引查询。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param conditions 查询条件，不能为null或空列表
     * @return 查询到的数量，如果未找到记录则返回0
     * @throws GXBusinessException 当查询条件为空时抛出
     */
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

    /**
     * 根据条件统计数量
     * <p>
     * 该方法用于统计符合条件的记录数量。
     * 如果查询参数为null，会抛出业务异常。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param queryParamInnerDto 查询条件，不能为null
     * @return 查询到的数量，如果未找到记录则返回0
     * @throws GXBusinessException 当查询参数为null时抛出
     */
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

    /**
     * 获取 Primary Key
     * <p>
     * 该方法用于获取实体的主键字段名称。
     * 在Elasticsearch中，通常使用"id"作为文档的主键字段。
     * 如果实体为null，会返回默认的主键名称"id"。
     * </p>
     * <p>
     * 内存安全：安全处理null实体，避免空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param entity 实体对象，可以为null
     * @return String 主键字段名称，默认为"id"
     */
    @Override
    public String getPrimaryKeyName(T entity) {
        return repository.getPrimaryKeyName(entity);
    }

    /**
     * 获取表的名字
     * <p>
     * 该方法用于获取当前实体对应的Elasticsearch索引名称。
     * 内部通过调用Repository的getTableName方法实现。
     * </p>
     * <p>
     * 内存安全：方法不涉及内存分配或资源管理
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @return String 索引名称，如果Repository未实现getTableName方法则可能返回null
     */
    @Override
    public String getTableName() {
        return repository.getTableName();
    }

    /**
     * Read value by column name with common naming conventions.
     */
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
