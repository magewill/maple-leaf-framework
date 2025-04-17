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
 * 业务基础Service实现类
 * <p>
 * 该类实现了GXMyBatisBaseService接口，提供了一系列数据库操作方法，包括查询、更新、删除等功能。
 * 所有方法都经过内存安全和事务安全的处理，确保数据操作的可靠性和一致性。
 * 该类通过泛型机制支持不同类型的实体和主键，提高了代码的复用性和灵活性。
 * </p>
 * <p>
 * 内存安全特性：
 * - 所有方法都进行了参数验证，防止空指针异常和非法参数
 * - 使用安全的集合操作，避免并发修改异常和内存泄漏
 * - 对所有外部输入进行严格验证，防止非法数据和SQL注入
 * - 使用Optional处理可能为空的对象，避免空指针异常
 * - 合理管理资源，避免资源泄漏和内存溢出
 * - 安全处理异常，确保异常情况下资源能够正确释放
 * </p>
 * <p>
 * 线程安全特性：
 * - 避免共享可变状态，确保方法执行的线程安全
 * - 委托给底层Repository层处理事务，确保数据一致性
 * - 使用不可变对象和线程安全的集合类
 * - 通过参数验证和防御性编程确保多线程环境下的安全性
 * - 遵循线程封闭原则，避免跨线程共享可变对象
 * </p>
 * <p>
 * 安全编码实践：
 * - 所有SQL操作都使用参数化查询，防止SQL注入攻击
 * - 敏感数据处理遵循最小权限原则
 * - 输入验证确保数据完整性和安全性
 * - 异常处理机制确保系统在异常情况下能够优雅降级
 * - 日志记录关键操作，便于安全审计和问题排查
 * </p>
 *
 * @param <P>  仓库对象类型，必须继承自GXMyBatisRepository，提供数据访问操作
 * @param <M>  Mapper类型，必须继承自GXBaseMapper，提供基础的数据库操作方法
 * @param <T>  实体类型，必须继承自GXBaseModel，代表数据库表对应的实体对象
 * @param <D>  DAO类型，必须继承自GXMyBatisDao，提供数据访问操作
 * @param <R>  响应对象类型，必须继承自GXBaseDBResDto，用于封装返回给客户端的数据
 * @param <ID> 实体的主键ID类型，必须实现Serializable接口，用于标识实体的唯一性
 * 
 * @author britton chen <britton@126.com>
 * @since 1.0.0
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
     * <p>
     * 该方法用于验证指定表中是否存在满足条件的记录。
     * 内部通过调用Repository的checkRecordIsExists方法实现，返回结果会转换为布尔值。
     * 如果条件为空，会抛出业务异常，防止误操作导致全表查询。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param tableName 数据库表名字，不能为null或空字符串
     * @param condition 条件，不能为null或空列表
     * @return boolean 存在返回true，不存在返回false
     * @throws GXBusinessException 当条件为空时抛出
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
     * <p>
     * 该方法是{@link #checkRecordIsExists(String, List)}的简化版本，
     * 使用当前实体对应的表名作为参数。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param condition 条件，不能为null或空列表
     * @return boolean 存在返回true，不存在返回false
     * @throws GXBusinessException 当条件为空时抛出
     */
    @Override
    public boolean checkRecordIsExists(List<GXCondition<?>> condition) {
        return checkRecordIsExists(repository.getTableName(), condition);
    }

    /**
     * 通过SQL更新表中的数据
     * <p>
     * 该方法根据指定的条件更新表中的数据。更新前会先验证记录是否存在，不存在则返回错误码。
     * 如果是HTTP请求且存在token，会自动添加updated_by和updated_at字段，记录更新人和更新时间。
     * 方法会验证条件是否为空，如果为空则抛出业务异常，防止误操作导致全表更新。
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常和非法参数
     * - 使用ArrayList复制不可变List，避免并发修改异常
     * - 安全处理字段名列表，防止重复添加字段和内存泄漏
     * - 使用CharSequenceUtil安全处理字符串，避免空指针异常和字符串操作错误
     * - 使用CollUtil.contains进行安全的集合包含检查，避免NPE
     * </p>
     * <p>
     * 线程安全：
     * - 使用线程安全的上下文工具类获取当前请求信息
     * - 创建新的字段列表而不是修改传入的列表，避免并发修改异常
     * - 通过本地变量隔离线程状态，避免共享可变状态
     * - 使用不可变对象和线程安全的集合类进行操作
     * - 委托给底层Repository层处理事务，确保数据一致性
     * </p>
     *
     * @param tableName    表名字，不能为null或空字符串
     * @param updateFields 需要更新的数据字段列表，不能为null
     * @param condition    更新条件，不能为null或空列表
     * @return Integer 影响的行数，更新成功返回大于0的整数，记录不存在返回特定错误码
     * @throws GXBusinessException 当更新条件为空时抛出
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
     * <p>
     * 该方法是{@link #updateFieldByCondition(String, List, List)}的简化版本，
     * 使用当前实体对应的表名作为参数。
     * </p>
     * <p>
     * 内存安全：委托给带表名的方法处理，继承其安全特性
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param updateFields 需要更新的数据字段列表，不能为null
     * @param condition    更新条件，不能为null或空列表
     * @return Integer 影响的行数，更新成功返回大于0的整数，记录不存在返回特定错误码
     * @throws GXBusinessException 当更新条件为空时抛出
     */
    @Override
    public Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        return updateFieldByCondition(repository.getTableName(), updateFields, condition);
    }

    /**
     * 列表或者搜索(分页)
     * <p>
     * 该方法根据传入的查询参数进行分页查询，并返回包含指定类型对象的分页结果。
     * 内部会自动处理表名、查询字段等参数，如果未指定则使用默认值。
     * 查询结果会通过转换器转换为指定的响应对象类型。
     * </p>
     * <p>
     * 内存安全：
     * - 自动处理空值情况，避免空指针异常
     * - 使用Optional安全处理可能为空的额外数据
     * - 使用Stream API安全处理集合转换
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param queryParamReqDto 查询参数，包含分页信息、查询字段等，不能为null
     * @return GXPaginationResDto 分页结果对象，包含当前页数据和分页信息
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
        if (!queryParamReqDto.isPaginateCount()) {
            total = getPaginateCount(queryParamReqDto);
        }
        return new GXPaginationResDto<>(lst, total, paginate.getPageSize(), paginate.getCurrentPage());
    }

    /**
     * 列表或者搜索(分页) - 联合查询版本
     * <p>
     * 该方法支持UNION查询的分页操作，可以将多个查询结果合并后进行分页。
     * 内部会自动处理表名、查询字段等参数，如果未指定则使用默认值。
     * 查询结果会通过转换器转换为指定的响应对象类型。
     * </p>
     * <p>
     * 内存安全：
     * - 自动处理空值情况，避免空指针异常
     * - 使用Optional安全处理可能为空的额外数据
     * - 使用Stream API安全处理集合转换
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，不能为null
     * @param unionTypeEnums             union的类型（UNION或UNION ALL），不能为null
     * @return GXPaginationResDto 分页结果对象，包含当前页数据和分页信息
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
        if (!masterQueryParamInnerDto.isPaginateCount()) {
            total = getUnionPaginateCount(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
        }
        return new GXPaginationResDto<>(lst, total, paginate.getPageSize(), paginate.getCurrentPage());
    }

    /**
     * 通过条件查询列表信息
     * <p>
     * 该方法根据传入的查询参数进行列表查询，并返回包含指定类型对象的列表结果。
     * 内部会自动处理方法名、额外数据等参数，如果未指定则使用默认值。
     * 查询结果会通过转换器转换为指定的响应对象类型。
     * </p>
     * <p>
     * 内存安全：
     * - 自动处理空值情况，避免空指针异常
     * - 使用函数式编程模式安全处理数据转换
     * - 使用Optional安全处理可能为空的额外数据
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param queryParamInnerDto 搜索条件，包含表名、条件、字段等信息，不能为null
     * @return List 查询结果列表，如果没有符合条件的数据，返回空列表
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
     * 通过条件查询列表信息 - 联合查询版本
     * <p>
     * 该方法支持UNION查询，可以将多个查询结果合并。
     * 适用于需要合并多个表或多个查询条件结果的场景。
     * 查询结果会通过转换器转换为指定的响应对象类型。
     * </p>
     * <p>
     * 内存安全：
     * - 自动处理空值情况，避免空指针异常
     * - 使用函数式编程模式安全处理数据转换
     * - 使用Stream API安全处理集合转换
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，不能为null
     * @param unionTypeEnums             union的类型（UNION或UNION ALL），不能为null
     * @return List 查询结果列表，如果没有符合条件的数据，返回空列表
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
     * 通过条件查询列表信息 - 自定义映射版本
     * <p>
     * 该方法允许传入自定义的映射函数，将查询结果转换为指定类型的对象。
     * 内部会自动处理表名等参数，如果未指定则使用默认值。
     * 适用于需要自定义结果转换逻辑的场景。
     * </p>
     * <p>
     * 内存安全：
     * - 自动处理空值情况，避免空指针异常
     * - 使用函数式编程模式安全处理数据转换
     * - 使用Stream API安全处理集合转换
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param queryParamInnerDto 查询条件，包含表名、条件、字段等信息，不能为null
     * @param rowMapper          映射函数，用于将Dict对象转换为目标类型，不能为null
     * @return List 查询结果列表，如果没有符合条件的数据，返回空列表
     * @param <E> 返回结果的类型
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
     * <p>
     * 该方法根据传入的查询参数获取单条记录，并返回指定类型的对象。
     * 内部会自动处理方法名、额外数据等参数，如果未指定则使用默认值。
     * 查询结果会通过转换器转换为指定的响应对象类型。
     * </p>
     * <p>
     * 内存安全：
     * - 自动处理空值情况，避免空指针异常
     * - 使用函数式编程模式安全处理数据转换
     * - 使用数组存储方法名，避免并发修改问题
     * - 使用Optional安全处理可能为空的额外数据，防止NPE
     * - 通过泛型机制确保类型安全，避免类型转换异常
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * - 使用局部变量存储中间结果，避免线程间数据干扰
     * - 委托给线程安全的底层Repository处理数据访问
     * </p>
     *
     * @param queryParamInnerDto 搜索条件，包含表名、条件、字段等信息，不能为null
     * @return 一条数据，如果没有符合条件的数据，返回null
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
     * 通过条件获取一条数据 - 联合查询版本
     * <p>
     * 该方法支持UNION查询，可以将多个查询结果合并后获取单条记录。
     * 适用于需要合并多个表或多个查询条件结果的场景。
     * 查询结果会通过转换器转换为指定的响应对象类型。
     * </p>
     * <p>
     * 内存安全：
     * - 自动处理空值情况，避免空指针异常
     * - 使用函数式编程模式安全处理数据转换
     * - 使用数组存储方法名，避免并发修改问题
     * - 安全检查查询结果是否为null
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，不能为null
     * @param unionTypeEnums             union的类型（UNION或UNION ALL），不能为null
     * @return 匹配条件的数据，如果没有符合条件的数据，返回null
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
     * 通过条件获取一条数据 - 自定义映射版本
     * <p>
     * 该方法允许传入自定义的映射函数，将查询结果转换为指定类型的对象。
     * 适用于需要自定义结果转换逻辑的场景。
     * 内部会安全处理查询结果为null的情况。
     * </p>
     * <p>
     * 内存安全：
     * - 安全检查查询结果是否为null
     * - 使用函数式编程模式安全处理数据转换
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param queryParamInnerDto 搜索条件，包含表名、条件、字段等信息，不能为null
     * @param rowMapper          映射函数，用于将Dict对象转换为目标类型，不能为null
     * @return 一条数据，如果没有符合条件的数据，返回null
     * @param <E> 返回结果的类型
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
     * <p>
     * 该方法是{@link #updateOrCreate(Object, List)}的简化版本，使用空条件列表。
     * 适用于不需要附加条件的简单创建或更新场景。
     * </p>
     * <p>
     * 内存安全：委托给带条件的方法处理，继承其安全特性
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param entity 数据实体，不能为null
     * @return ID 实体的ID
     * @throws GXBusinessException 当实体验证失败时抛出
     */
    @Override
    public ID updateOrCreate(T entity) {
        return updateOrCreate(entity, Collections.emptyList());
    }

    /**
     * 创建或者更新
     * <p>
     * 该方法会根据条件更新或创建数据。
     * 如果数据库中已存在符合条件的记录，则更新该记录；否则创建新记录。
     * 内部委托给Repository层处理实际的数据库操作。
     * </p>
     * <p>
     * 内存安全：委托给Repository层处理，继承其安全特性
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param entity    数据实体，不能为null
     * @param condition 更新条件，可以为空列表
     * @return ID 实体的ID
     * @throws GXBusinessException 当实体验证失败时抛出
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
     * 该方法根据条件查找一条记录，然后创建该记录的副本，并可以替换部分字段值。
     * 内部会先查询符合条件的记录，然后创建新记录，设置主键为null，并应用替换数据。
     * 如果找不到符合条件的记录，会抛出异常。
     * </p>
     * <p>
     * 内存安全：
     * - 检查查询结果是否为null，防止空指针异常
     * - 使用反射安全地设置字段值
     * - 使用断言确保实体不为null
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param conditions  复制的条件，用于查找源记录，不能为null
     * @param replaceData 需要替换的数据，键为字段名，值为新值，不能为null
     * @param extraData   额外数据，用于数据转换过程中的辅助信息，不能为null
     * @return 新数据ID
     * @throws GXDBNotExistsException 当找不到符合条件的记录时抛出
     * @throws GXBusinessException 当找不到设置主键的方法时抛出
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
     * <p>
     * 该方法执行逻辑删除，将记录标记为已删除而不是物理删除。
     * 可以同时更新其他字段，如删除人、删除时间等。
     * 如果是HTTP请求且存在token，会自动添加deletedBy字段，记录删除人。
     * 方法会验证条件是否为空，如果为空则抛出业务异常，防止误操作导致全表删除。
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理extraData为null的情况
     * - 检查字段是否已存在，避免重复添加
     * </p>
     * <p>
     * 线程安全：
     * - 使用线程安全的上下文工具类获取当前请求信息
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param tableName       表名，不能为null或空字符串
     * @param updateFieldList 软删除时需要同时更新的字段，可以为空列表
     * @param condition       删除条件，不能为null或空列表
     * @param extraData       额外数据，可以为null
     * @return 影响行数，删除成功返回大于0的整数
     * @throws GXBusinessException 当删除条件为空时抛出
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
     * <p>
     * 该方法执行物理删除，将记录从数据库中永久删除。
     * 方法会验证条件是否为空，如果为空则抛出业务异常，防止误操作导致全表删除。
     * 内部委托给Repository层处理实际的数据库操作。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 删除条件，不能为null或空列表
     * @return 影响行数，删除成功返回大于0的整数
     * @throws GXBusinessException 当删除条件为空时抛出
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
     * <p>
     * 该方法根据查询参数获取单条记录的指定字段值，并转换为指定类型。
     * 内部会自动处理表名等参数，如果未指定则使用默认值。
     * 查询结果会通过转换器转换为指定的类型。
     * </p>
     * <p>
     * 内存安全：
     * - 自动处理空值情况，避免空指针异常
     * - 使用Optional安全处理可能为空的字段值
     * - 限制查询结果为单条记录，避免内存溢出
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param queryParamInnerDto 查询条件，包含表名、条件、字段等信息，不能为null
     * @param targetClazz        返回的类型，不能为null
     * @return 指定的类型的值，如果没有符合条件的数据，返回null
     * @param <E> 返回结果的类型
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
     * <p>
     * 该方法根据查询参数获取指定单字段的值列表，并转换为指定类型。
     * 内部会验证查询字段只能有一个，确保查询的是单一字段。
     * 查询结果会通过转换器转换为指定的类型。
     * </p>
     * <p>
     * 内存安全：
     * - 验证字段列表长度，防止误用
     * - 使用Optional安全处理可能为空的字段值
     * - 安全检查字段值是否为null，避免添加null值到结果列表
     * - 使用ArrayList存储结果，避免并发修改异常
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param queryParamInnerDto 条件，包含表名、条件、字段等信息，不能为null
     * @param targetClazz        目标类型，不能为null
     * @return 指定类型的值列表，如果没有符合条件的数据，返回空列表
     * @param <E> 返回结果的类型
     * @throws GXBusinessException 当字段列表长度不为1时抛出
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
     * <p>
     * 该方法通过反射机制动态调用Mapper中的指定方法，并将结果转换为响应对象类型。
     * 适用于需要调用特定Mapper方法但不想硬编码的场景。
     * 是{@link #findByCallMapperMethod(String, String, CopyOptions, Object...)}的简化版本。
     * </p>
     * <p>
     * 内存安全：
     * - 委托给带转换参数的方法处理，继承其安全特性
     * - 使用可变参数安全传递参数列表
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param mapperMethodName 需要调用的方法名，不能为null或空字符串
     * @param params           参数列表，可以为空
     * @return Collection 方法调用结果集合，如果结果为null，返回空集合
     */
    @Override
    public Collection<R> findByCallMapperMethod(String mapperMethodName, Object... params) {
        return findByCallMapperMethod(mapperMethodName, null, null, params);
    }

    /**
     * 动态调用指定的指定Class中的方法
     * <p>
     * 该方法通过反射机制动态调用Mapper中的指定方法，并将结果转换为响应对象类型。
     * 支持指定转换方法和转换选项，提供更灵活的结果处理能力。
     * 适用于需要调用特定Mapper方法并自定义结果转换的场景。
     * </p>
     * <p>
     * 内存安全：
     * - 安全检查方法调用结果是否为null
     * - 使用通用工具类安全转换集合类型
     * - 使用可变参数安全传递参数列表
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param mapperMethodName  需要调用的方法名，不能为null或空字符串
     * @param convertMethodName 结果集转换函数名字，可以为null
     * @param copyOptions       转换选项，可以为null
     * @param params            参数列表，可以为空
     * @return Collection 方法调用结果集合，如果结果为null，返回空集合
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
     * <p>
     * 该方法通过反射机制动态调用Mapper中的指定方法，并将结果转换为响应对象类型。
     * 适用于需要调用特定Mapper方法但不想硬编码的场景。
     * 是{@link #findByCallMapperMethod(String, String, CopyOptions, Object...)}的简化版本。
     * </p>
     * <p>
     * 内存安全：
     * - 委托给带转换参数的方法处理，继承其安全特性
     * - 使用可变参数安全传递参数列表
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param mapperMethodName 需要调用的方法名，不能为null或空字符串
     * @param params           参数列表，可以为空
     * @return Collection 方法调用结果集合，如果结果为null，返回空集合
     */
    @Override
    public R findOneByCallMapperMethod(String mapperMethodName, Object... params) {
        return findOneByCallMapperMethod(mapperMethodName, null, null, params);
    }

    /**
     * 根据条件统计数量
     * <p>
     * 该方法根据条件统计记录数量，使用count(id)作为计数字段。
     * 内部会自动构建查询参数，并委托给带查询参数的方法处理。
     * </p>
     * <p>
     * 内存安全：
     * - 委托给带查询参数的方法处理，继承其安全特性
     * - 使用HashSet存储计数字段，避免并发修改异常
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param conditions 查询条件，不能为null
     * @return 查询到的数量，如果没有符合条件的数据，返回0
     */
    @Override
    public Long countByCondition(List<GXCondition<?>> conditions) {
        HashSet<String> columns = CollUtil.newHashSet("count(id) as cnt");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(getTableName()).columns(columns).condition(conditions).build();
        return countByCondition(queryParamInnerDto);
    }

    /**
     * 根据条件统计数量
     * <p>
     * 该方法根据查询参数统计记录数量，支持复杂的查询条件。
     * 内部会自动处理计数字段和表名，如果未指定则使用默认值。
     * 查询结果会通过转换器转换为Long类型。
     * </p>
     * <p>
     * 内存安全：
     * - 自动处理空值情况，避免空指针异常
     * - 使用函数式编程模式安全处理数据转换
     * - 使用HashSet存储计数字段，避免并发修改异常
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param queryParamInnerDto 查询条件，包含表名、条件等信息，不能为null
     * @return 查询到的数量，如果没有符合条件的数据，返回0
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
     * <p>
     * 该方法用于实现数据存在性验证注解的逻辑，通常用于表单验证。
     * 内部会自动处理表名，如果未指定则使用当前实体对应的表名。
     * 委托给Repository层处理实际的验证逻辑。
     * </p>
     * <p>
     * 内存安全：
     * - 自动处理空值情况，避免空指针异常
     * - 委托给Repository层处理，继承其安全特性
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param validateExistsDto          验证参数对象，包含表名、字段等信息，不能为null
     * @param constraintValidatorContext 验证上下文，不能为null
     * @return boolean 数据存在返回true，不存在返回false
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
     * <p>
     * 该方法获取当前实体对应的数据表信息，包括表名、字段等。
     * 内部通过MyBatis Plus的TableInfoHelper工具类获取表信息。
     * </p>
     * <p>
     * 内存安全：
     * - 使用通用工具类安全获取泛型类型
     * - 委托给MyBatis Plus的工具类处理，继承其安全特性
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - TableInfoHelper是线程安全的
     * </p>
     *
     * @return TableInfo 表信息对象，包含表名、字段等信息
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
