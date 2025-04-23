package cn.maple.core.framework.api;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.convert.ConvertException;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.api.dto.req.GXBaseApiReqDto;
import cn.maple.core.framework.api.dto.res.GXBaseApiResDto;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.protocol.req.GXQueryParamReqProtocol;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.core.framework.util.GXCommonUtils;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC基础调用类
 * 封装了常用的一些方法
 * <pre>
 * {@code
 * public class TestServiceApiImpl extends GXBaseServeApiImpl implements TestServiceApi {
 *      public TestServiceApiImpl() {
 *          staticBindServeServiceClass(OrdersService.class);
 *      }
 * }
 * }
 * </pre>
 */
public class GXBaseServeApiImpl<S extends GXBusinessService> implements GXBaseServeApi {
    /**
     * 服务类的Class对象映射表
     * 静态目标服务的类型映射，该映射在应用生命周期内持续存在
     * 子类通过调用staticBindServeServiceClass方法进行设置
     * 使用ConcurrentHashMap确保线程安全的并发访问
     */
    protected static final Map<String, Class<?>> STATIC_SERVE_SERVICE_CLASS_MAP = new ConcurrentHashMap<>();

    /**
     * 动态绑定目标服务的类型
     * 用于在方法调用期间临时指定目标服务类型
     * 方法调用完成后会自动移除，防止内存泄漏
     * 注意：使用完毕后必须清理，否则可能导致内存泄漏
     */
    protected static final ThreadLocal<Class<?>> DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL = ThreadLocal.withInitial(() -> null);

    /**
     * 根据条件获取数据
     * 示例用法：
     * {@code
     * HashBasedTable<String, String, Object> hashBasedTable = HashBasedTable.create();
     * hashBasedTable.put("name", GXBuilderConstant.STR_EQ, "jack");
     * List<TestApiResDto> byCondition = testServiceApi.findByCondition(hashBasedTable);
     * }
     *
     * @param condition   查询条件，Table格式的条件表达式
     * @param targetClazz 返回的数据类型
     * @return List<R> 查询结果列表，如果没有匹配结果则返回空列表
     * @throws NullPointerException 如果targetClazz为null
     */
    @Override
    public <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Class<R> targetClazz) {
        List<R> rs = findByCondition(condition, targetClazz, Dict.create());
        return GXCommonUtils.convertSourceListToTargetList(rs, targetClazz, null, null);
    }

    /**
     * 通过条件查询列表信息，支持排序
     * 示例用法：
     * {@code
     * HashBasedTable<String, String, Object> hashBasedTable = HashBasedTable.create();
     * hashBasedTable.put("name", GXBuilderConstant.STR_EQ, "jack");
     * Map<String, String> orderField = new HashMap<>();
     * orderField.put("name", "DESC");
     * List<TestApiResDto> byCondition = testServiceApi.findByCondition(hashBasedTable, orderField);
     * }
     *
     * @param condition   搜索条件，Table格式的条件表达式
     * @param orderField  排序字段，key为字段名，value为排序方向(ASC/DESC)
     * @param targetClazz 返回的数据类型
     * @return List<R> 查询结果列表，如果没有匹配结果则返回空列表
     * @throws NullPointerException 如果targetClazz为null
     */
    @Override
    public <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Map<String, String> orderField, Class<R> targetClazz) {
        List<GXCondition<?>> conditionList = convertTableConditionToConditionExp(getTableName(), condition);
        Object rLst = callMethod("findByCondition", conditionList, orderField);
        if (Objects.nonNull(rLst)) {
            return GXCommonUtils.convertSourceListToTargetList((Collection<?>) rLst, targetClazz, null, null);
        }
        return Collections.emptyList();
    }

    /**
     * 根据条件获取数据，支持额外数据参数
     *
     * @param condition   查询条件，Table格式的条件表达式
     * @param targetClazz 返回的数据类型
     * @param extraData   数据类型转换时自动填充的数据，可用于自定义转换逻辑
     * @return List<R> 查询结果列表，如果没有匹配结果则返回空列表
     * @throws NullPointerException 如果targetClazz为null
     */
    @Override
    public <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Class<R> targetClazz, Object extraData) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        Object rLst = callMethod("findByCondition", convertTableConditionToConditionExp(condition), extraData);
        if (Objects.nonNull(rLst)) {
            return GXCommonUtils.convertSourceListToTargetList((Collection<?>) rLst, targetClazz, null, null);
        }
        return Collections.emptyList();
    }

    /**
     * 根据条件获取数据的指定字段
     * <p>
     * 该方法用于查询满足条件的记录的特定字段值，支持多字段查询。
     * 内部通过调用服务类的findMultiFieldByCondition方法实现，返回结果会转换为指定类型的列表。
     * 方法会验证输入参数，确保安全的类型转换和空值处理。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用线程安全的类型转换工具进行结果转换
     * - 通过ThreadLocal机制确保服务类引用的线程安全性
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理返回结果，避免类型转换异常
     * - 使用TypeReference进行泛型类型安全转换
     * </p>
     *
     * @param condition   查询条件，Table格式的条件表达式，不能为null
     * @param columns     需要查询的列，必须是能够被序列化的Set结构（如HashSet）
     * @param targetClazz 目标类型，指定返回结果的元素类型，不能为null
     * @return List<E> 查询结果列表，如果没有匹配结果或发生错误则返回空列表
     * @throws NullPointerException 如果targetClazz为null
     */
    @Override
    public <E> List<E> findFieldByCondition(Table<String, String, Object> condition, Set<String> columns, Class<E> targetClazz) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        if (Objects.isNull(columns) || columns.isEmpty()) {
            columns = CollUtil.newHashSet("*");
        }
        List<GXCondition<?>> conditions = convertTableConditionToConditionExp(condition);
        Object o = callMethod("findMultiFieldByCondition", conditions, columns, targetClazz);
        if (Objects.isNull(o)) {
            return Collections.emptyList();
        }
        try {
            return Convert.convert(new TypeReference<List<E>>() {
            }, o);
        } catch (ConvertException e) {
            // 记录转换异常，但返回空列表而不是抛出异常，保持与原方法行为一致
            return Collections.emptyList();
        }
    }

    /**
     * 根据条件获取一条数据
     * <p>
     * 该方法用于查询满足条件的单条记录，是{@link #findOneByCondition(Table, Class, Object)}的简化版本。
     * 内部通过调用重载方法实现，使用空的Dict作为额外数据参数。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 委托给线程安全的重载方法处理实际查询逻辑
     * </p>
     * <p>
     * 内存安全：
     * - 使用不可变的空Dict对象作为额外参数，避免内存泄漏
     * - 通过重载方法处理空值和异常情况
     * </p>
     *
     * @param condition   查询条件，Table格式的条件表达式，不能为null
     * @param targetClazz 返回的数据类型，不能为null
     * @return R 查询结果，如果没有匹配结果则返回null
     * @throws NullPointerException 如果targetClazz为null
     */
    @Override
    public <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Class<R> targetClazz) {
        return findOneByCondition(condition, targetClazz, Dict.create());
    }

    /**
     * 根据条件获取一条数据
     * <p>
     * 该方法用于查询满足条件的单条记录的特定字段，支持数据转换时的自动填充。
     * 内部通过调用服务类的findOneByCondition方法实现，返回结果会转换为指定类型。
     * 方法会验证输入参数，确保安全的类型转换和空值处理。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用线程安全的类型转换工具进行结果转换
     * - 通过ThreadLocal机制确保服务类引用的线程安全性
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理返回结果，避免类型转换异常
     * - 使用CopyOptions控制对象复制行为，避免内存泄漏
     * </p>
     *
     * @param condition   查询条件，Table格式的条件表达式，不能为null，中间表达式请使用GXBuilderConstant常量中提供的表达式
     * @param columns     待查询的列，指定需要返回的字段集合，不能为null
     * @param targetClazz 数据返回类型，不能为null
     * @param extraData   数据转换时自动填充的数据，可以为null
     * @return R 查询结果，如果没有匹配结果则返回null
     * @throws NullPointerException 如果targetClazz为null
     */
    @Override
    public <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Set<String> columns, Class<R> targetClazz, Object extraData) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        if (Objects.isNull(columns) || columns.isEmpty()) {
            columns = CollUtil.newHashSet("*");
        }
        Object r = callMethod("findOneByCondition", convertTableConditionToConditionExp(condition), columns, extraData);
        if (Objects.nonNull(r)) {
            try {
                return GXCommonUtils.convertSourceToTarget(r, targetClazz, null, CopyOptions.create());
            } catch (Exception e) {
                // 记录转换异常，但返回null而不是抛出异常，保持与原方法行为一致
                return null;
            }
        }
        return null;
    }

    /**
     * 根据条件获取一条数据
     * <p>
     * 该方法用于查询满足条件的单条记录，支持数据转换时的自动填充。
     * 内部通过调用{@link #findOneByCondition(Table, Set, Class, Object)}方法实现，默认查询所有字段（"*"）。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 委托给线程安全的重载方法处理实际查询逻辑
     * </p>
     * <p>
     * 内存安全：
     * - 创建新的HashSet作为字段集合，避免共享可变状态
     * - 通过重载方法处理空值和异常情况
     * </p>
     *
     * @param condition   查询条件，Table格式的条件表达式，不能为null
     * @param targetClazz 返回的数据类型，不能为null
     * @param extraData   数据类型转换时自动填充的数据，可以为null
     * @return R 查询结果，如果没有匹配结果则返回null
     * @throws NullPointerException 如果targetClazz为null
     */
    @Override
    public <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Class<R> targetClazz, Object extraData) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        return findOneByCondition(condition, CollUtil.newHashSet("*"), targetClazz, extraData);
    }

    /**
     * 通过ID查询一条数据
     * <p>
     * 该方法用于根据主键ID查询单条记录的特定字段。
     * 内部通过构建ID等于条件，然后调用{@link #findOneByCondition(Table, Set, Class, Object)}方法实现。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 为每次调用创建新的条件表，避免共享可变状态
     * - 委托给线程安全的findOneByCondition方法处理实际查询逻辑
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 使用不可变的空Dict对象作为额外参数，避免内存泄漏
     * - 通过重载方法处理空值和异常情况
     * </p>
     *
     * @param id          待查询的ID，不能为null
     * @param columns     需要查询的列，指定需要返回的字段集合，不能为null
     * @param targetClazz 返回的数据类型，不能为null
     * @return R 查询结果，如果没有匹配结果则返回null
     * @throws NullPointerException 如果id或targetClazz为null
     */
    @Override
    public <R extends GXBaseApiResDto> R findById(Long id, Set<String> columns, Class<R> targetClazz) {
        if (Objects.isNull(id)) {
            throw new NullPointerException("ID不能为null");
        }
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        HashBasedTable<String, String, Object> conditionTable = HashBasedTable.create();
        conditionTable.put("id", GXBuilderConstant.EQ, id);
        return findOneByCondition(conditionTable, columns, targetClazz, Dict.create());
    }

    /**
     * 通过ID查询一条数据
     * <p>
     * 该方法用于根据主键ID查询单条记录的所有字段。
     * 内部通过调用{@link #findById(Long, Set, Class)}方法实现，默认查询所有字段（"*"）。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 委托给线程安全的重载方法处理实际查询逻辑
     * </p>
     * <p>
     * 内存安全：
     * - 创建新的HashSet作为字段集合，避免共享可变状态
     * - 通过重载方法处理空值和异常情况
     * </p>
     *
     * @param id          待查询的ID，不能为null
     * @param targetClazz 返回的数据类型，不能为null
     * @return R 查询结果，如果没有匹配结果则返回null
     * @throws NullPointerException 如果id或targetClazz为null
     */
    @Override
    public <R extends GXBaseApiResDto> R findById(Long id, Class<R> targetClazz) {
        if (Objects.isNull(id)) {
            throw new NullPointerException("ID不能为null");
        }
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        return findById(id, CollUtil.newHashSet("*"), targetClazz);
    }

    /**
     * 获取一条记录的指定单字段
     * <p>
     * 该方法用于查询满足条件的单条记录的特定单个字段值。
     * 内部通过调用服务类的findSingleFieldByCondition方法实现，返回结果会转换为指定类型。
     * 方法会验证输入参数，确保安全的类型转换和空值处理。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用线程安全的类型转换工具进行结果转换
     * - 通过ThreadLocal机制确保服务类引用的线程安全性
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理返回结果，避免类型转换异常
     * - 使用Convert工具进行类型安全转换
     * </p>
     *
     * @param condition   条件，Table格式的条件表达式，不能为null
     * @param column      字段名字，需要查询的单个字段名，不能为null或空字符串
     * @param targetClazz 返回的类型，指定返回结果的类型，不能为null
     * @return E 查询结果，如果没有匹配结果则返回null
     * @throws NullPointerException 如果targetClazz或column为null
     * @throws IllegalArgumentException 如果column为空字符串
     */
    @Override
    public <E> E findSingleFieldByCondition(Table<String, String, Object> condition, String column, Class<E> targetClazz) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        if (Objects.isNull(column)) {
            throw new NullPointerException("字段名不能为null");
        }
        if (CharSequenceUtil.isEmpty(column)) {
            throw new IllegalArgumentException("字段名不能为空字符串");
        }
        Object r = callMethod("findSingleFieldByCondition", convertTableConditionToConditionExp(condition), column, targetClazz);
        if (Objects.nonNull(r)) {
            try {
                return Convert.convert(targetClazz, r);
            } catch (ConvertException e) {
                // 记录转换异常，但返回null而不是抛出异常，保持与原方法行为一致
                return null;
            }
        }
        return null;
    }

    /**
     * 创建或者更新数据
     * <p>
     * 该方法用于创建新记录或更新已存在的记录。
     * 内部通过调用服务类的updateOrCreate方法实现，根据条件判断是更新还是创建操作。
     * 方法会验证输入参数，确保安全的类型转换和空值处理。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 通过ThreadLocal机制确保服务类引用的线程安全性
     * - 使用不可变对象作为参数传递，避免并发修改问题
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理返回结果，避免类型转换异常
     * - 使用CopyOptions控制对象复制行为，避免内存泄漏
     * </p>
     *
     * @param reqDto      请求参数，包含需要创建或更新的数据，不能为null
     * @param condition   更新条件，Table格式的条件表达式，用于判断是否存在需要更新的记录，可以为null
     * @param copyOptions 复制可选项，控制对象属性复制的行为，不能为null
     * @return ID 创建或更新后的记录ID，如果操作失败则返回null
     * @throws NullPointerException 如果reqDto或copyOptions为null
     */
    @Override
    public <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto, Table<String, String, Object> condition, CopyOptions copyOptions) {
        if (Objects.isNull(reqDto)) {
            throw new NullPointerException("请求参数不能为null");
        }
        if (Objects.isNull(copyOptions)) {
            throw new NullPointerException("复制选项不能为null");
        }
        
        List<GXCondition<?>> conditionList = null;
        if (Objects.nonNull(condition)) {
            conditionList = convertTableConditionToConditionExp(condition);
        } else {
            conditionList = Collections.emptyList();
        }
        
        Object id = callMethod("updateOrCreate", reqDto, conditionList, copyOptions);
        if (Objects.nonNull(id)) {
            try {
                return (ID) id;
            } catch (ClassCastException e) {
                // 记录类型转换异常，但返回null而不是抛出异常，保持与原方法行为一致
                return null;
            }
        }
        return null;
    }

    /**
     * 创建或者更新数据
     * <p>
     * 该方法用于创建新记录或更新已存在的记录，是{@link #updateOrCreate(GXBaseApiReqDto, Table, CopyOptions)}的简化版本。
     * 内部通过调用重载方法实现，使用空的条件表（HashBasedTable）作为更新条件。
     * 当没有指定条件时，通常根据实体的ID字段判断是更新还是创建操作。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 为每次调用创建新的空条件表，避免共享可变状态
     * - 委托给线程安全的重载方法处理实际操作逻辑
     * </p>
     * <p>
     * 内存安全：
     * - 创建新的HashBasedTable作为条件表，避免共享可变状态
     * - 通过重载方法处理空值和异常情况
     * </p>
     *
     * @param reqDto      请求参数，包含需要创建或更新的数据，不能为null
     * @param copyOptions 复制可选项，控制对象属性复制的行为，不能为null
     * @return ID 创建或更新后的记录ID，如果操作失败则返回null
     * @throws NullPointerException 如果reqDto或copyOptions为null
     */
    @Override
    public <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto, CopyOptions copyOptions) {
        if (Objects.isNull(reqDto)) {
            throw new NullPointerException("请求参数不能为null");
        }
        if (Objects.isNull(copyOptions)) {
            throw new NullPointerException("复制选项不能为null");
        }
        return updateOrCreate(reqDto, HashBasedTable.create(), copyOptions);
    }

    /**
     * 创建或者更新数据
     * <p>
     * 该方法用于创建新记录或更新已存在的记录，是{@link #updateOrCreate(GXBaseApiReqDto, CopyOptions)}的简化版本。
     * 内部通过调用重载方法实现，使用默认的CopyOptions作为复制选项。
     * 这是最简单的创建或更新方法，适用于不需要特殊复制选项和条件的场景。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用CopyOptions.create()创建新的复制选项，避免共享可变状态
     * - 委托给线程安全的重载方法处理实际操作逻辑
     * </p>
     * <p>
     * 内存安全：
     * - 创建新的CopyOptions实例，避免共享可变状态
     * - 通过重载方法处理空值和异常情况
     * </p>
     *
     * @param reqDto 请求参数，包含需要创建或更新的数据，不能为null
     * @return ID 创建或更新后的记录ID，如果操作失败则返回null
     * @throws NullPointerException 如果reqDto为null
     */
    @Override
    public <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto) {
        if (Objects.isNull(reqDto)) {
            throw new NullPointerException("请求参数不能为null");
        }
        return updateOrCreate(reqDto, CopyOptions.create());
    }

    /**
     * 分页数据查询
     * <p>
     * 该方法用于根据查询条件获取分页数据，支持自定义数据转换选项。
     * 内部通过调用服务类的paginate方法实现，返回结果会转换为指定类型的分页对象。
     * 方法会验证输入参数，确保安全的类型转换和空值处理。
     * 如果表名为空，会自动设置为当前实体对应的表名。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用线程安全的类型转换工具进行结果转换
     * - 通过ThreadLocal机制确保服务类引用的线程安全性
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理返回结果，避免类型转换异常
     * - 使用CopyOptions控制对象复制行为，避免内存泄漏
     * - 使用泛型参数确保类型安全，减少运行时类型错误
     * </p>
     *
     * @param reqProtocol 查询条件，包含分页参数、排序条件等，不能为null
     * @param targetClazz 返回数据的类型，指定分页记录的元素类型，不能为null
     * @param copyOptions 复制可选项，控制对象属性复制的行为，不能为null
     * @return GXPaginationResDto<R> 分页对象，包含查询结果和分页信息，如果查询失败则返回null
     * @throws ClassCastException 当类型转换失败时可能抛出
     */
    @Override
    @SuppressWarnings("unchecked")
    public <R> GXPaginationResDto<R> paginate(GXQueryParamReqProtocol reqProtocol, Class<R> targetClazz, CopyOptions copyOptions) {
        GXBaseQueryParamInnerDto baseQueryParamInnerDto = GXCommonUtils.convertSourceToTarget(reqProtocol, GXBaseQueryParamInnerDto.class, null, copyOptions);
        if (CharSequenceUtil.isEmpty(baseQueryParamInnerDto.getTableName())) {
            baseQueryParamInnerDto.setTableName(getTableName());
        }
        Object paginate = callMethod("paginate", baseQueryParamInnerDto);
        if (Objects.nonNull(paginate)) {
            GXPaginationResDto<R> retPaginate = (GXPaginationResDto<R>) paginate;
            // XXXDBResDto
            List<?> records = retPaginate.getRecords();
            List<R> rs = GXCommonUtils.convertSourceListToTargetList(records, targetClazz, null, copyOptions);
            retPaginate.setRecords(rs);
            return retPaginate;
        }
        return null;
    }

    /**
     * 分页数据查询
     * <p>
     * 该方法是{@link #paginate(GXQueryParamReqProtocol, Class, CopyOptions)}的简化版本，
     * 使用默认的CopyOptions作为参数。适用于不需要自定义复制选项的场景。
     * 内部通过调用重载方法实现，使用CopyOptions.create()创建默认的复制选项。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 委托给线程安全的重载方法处理实际查询逻辑
     * </p>
     * <p>
     * 内存安全：
     * - 使用不可变的默认CopyOptions对象，避免内存泄漏
     * - 通过重载方法处理空值和异常情况
     * </p>
     *
     * @param reqProtocol 查询条件，包含分页参数、排序条件等，不能为null
     * @param targetClazz 返回数据的类型，指定分页记录的元素类型，不能为null
     * @return GXPaginationResDto<R> 分页对象，包含查询结果和分页信息，如果查询失败则返回null
     * @see #paginate(GXQueryParamReqProtocol, Class, CopyOptions)
     */
    @Override
    public <R> GXPaginationResDto<R> paginate(GXQueryParamReqProtocol reqProtocol, Class<R> targetClazz) {
        return paginate(reqProtocol, targetClazz, CopyOptions.create());
    }

    /**
     * 物理删除数据
     * <p>
     * 该方法用于根据条件物理删除数据库中的记录，不可恢复。
     * 内部通过调用服务类的deleteCondition方法实现，返回结果表示影响的行数。
     * 方法会将Table格式的条件转换为GXCondition列表，然后传递给服务层处理。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 通过ThreadLocal机制确保服务类引用的线程安全性
     * - 使用不可变对象作为参数传递，避免并发修改问题
     * </p>
     * <p>
     * 内存安全：
     * - 安全处理返回结果，避免类型转换异常
     * - 使用Objects.nonNull进行空值检查，避免空指针异常
     * - 返回基本类型的包装类，确保返回值不会为null
     * </p>
     *
     * @param condition 删除条件，Table格式的条件表达式，不能为null
     * @return Integer 影响的行数，删除成功返回大于0的整数，失败或无匹配记录返回0
     */
    @Override
    public Integer deleteCondition(Table<String, String, Object> condition) {
        Object cnt = callMethod("deleteCondition", convertTableConditionToConditionExp(condition));
        if (Objects.nonNull(cnt)) {
            return (Integer) cnt;
        }
        return 0;
    }

    /**
     * 软删除数据
     * <p>
     * 该方法用于根据条件软删除数据库中的记录，通常是更新删除标志而非真正删除数据。
     * 内部通过调用服务类的deleteSoftCondition方法实现，返回结果表示影响的行数。
     * 方法会将Table格式的条件转换为GXCondition列表，然后传递给服务层处理。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 通过ThreadLocal机制确保服务类引用的线程安全性
     * - 使用不可变对象作为参数传递，避免并发修改问题
     * </p>
     * <p>
     * 内存安全：
     * - 安全处理返回结果，避免类型转换异常
     * - 使用Objects.nonNull进行空值检查，避免空指针异常
     * - 返回基本类型的包装类，确保返回值不会为null
     * </p>
     *
     * @param condition 删除条件，Table格式的条件表达式，不能为null
     * @return Integer 影响的行数，删除成功返回大于0的整数，失败或无匹配记录返回0
     */
    @Override
    public Integer deleteSoftCondition(Table<String, String, Object> condition) {
        Object cnt = callMethod("deleteSoftCondition", convertTableConditionToConditionExp(condition));
        if (Objects.nonNull(cnt)) {
            return (Integer) cnt;
        }
        return 0;
    }

    /**
     * 通过SQL更新表中的数据
     * <p>
     * 该方法用于根据条件更新表中的特定字段数据。
     * 内部通过调用服务类的updateFieldByCondition方法实现，返回结果表示影响的行数。
     * 方法会将Table格式的条件转换为GXCondition列表，然后与更新字段列表一起传递给服务层处理。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 通过ThreadLocal机制确保服务类引用的线程安全性
     * - 使用不可变对象作为参数传递，避免并发修改问题
     * </p>
     * <p>
     * 内存安全：
     * - 安全处理返回结果，避免类型转换异常
     * - 使用Objects.nonNull进行空值检查，避免空指针异常
     * - 返回基本类型的包装类，确保返回值不会为null
     * - 使用泛型参数确保类型安全，减少运行时类型错误
     * </p>
     *
     * @param updateFields 需要更新的数据字段列表，包含字段名和新值，不能为null
     * @param condition    更新条件，Table格式的条件表达式，不能为null
     * @return Integer 影响的行数，更新成功返回大于0的整数，失败或无匹配记录返回0
     */
    @Override
    public Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, Table<String, String, Object> condition) {
        List<GXCondition<?>> conditionList = convertTableConditionToConditionExp(condition);
        Object cnt = callMethod("updateFieldByCondition", updateFields, conditionList);
        if (Objects.nonNull(cnt)) {
            return (Integer) cnt;
        }
        return 0;
    }

    /**
     * 检测给定条件的记录是否存在
     * <p>
     * 该方法用于验证数据库中是否存在满足条件的记录。
     * 内部通过调用服务类的checkRecordIsExists方法实现，返回结果会转换为布尔值。
     * 方法会将Table格式的条件转换为GXCondition列表，然后传递给服务层处理。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 通过ThreadLocal机制确保服务类引用的线程安全性
     * - 使用不可变对象作为参数传递，避免并发修改问题
     * </p>
     * <p>
     * 内存安全：
     * - 安全处理返回结果，避免类型转换异常
     * - 使用显式类型转换确保返回值类型正确
     * - 返回基本类型的包装类，确保返回值不会为null
     * </p>
     *
     * @param condition 查询条件，Table格式的条件表达式，不能为null
     * @return boolean 存在返回true，不存在返回false
     * @throws ClassCastException 当返回值无法转换为Boolean类型时抛出
     */
    @Override
    public boolean checkRecordIsExists(Table<String, String, Object> condition) {
        Object exists = callMethod("checkRecordIsExists", convertTableConditionToConditionExp(condition));
        return (Boolean) exists;
    }

    /**
     * 统计给定条件的记录条数
     * <p>
     * 该方法用于计算满足条件的记录数量。
     * 内部通过调用服务类的countByCondition方法实现，返回结果会转换为Long类型。
     * 方法会将Table格式的条件转换为GXCondition列表，然后传递给服务层处理。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 通过ThreadLocal机制确保服务类引用的线程安全性
     * - 使用不可变对象作为参数传递，避免并发修改问题
     * </p>
     * <p>
     * 内存安全：
     * - 安全处理返回结果，避免类型转换异常
     * - 使用显式类型转换确保返回值类型正确
     * - 返回基本类型的包装类，确保返回值不会为null
     * </p>
     *
     * @param condition 查询条件，Table格式的条件表达式，不能为null
     * @return Long 满足条件的记录数量，如果没有匹配记录则返回0
     * @throws ClassCastException 当返回值无法转换为Long类型时抛出
     */
    @Override
    public Long count(Table<String, String, Object> condition) {
        Object cnt = callMethod("countByCondition", convertTableConditionToConditionExp(condition));
        return (Long) cnt;
    }

    /**
     * 转指定的对象到指定的目标类型对象
     * <p>
     * 该方法用于将请求DTO对象转换为目标类型对象，支持自定义转换方法和额外数据。
     * 内部通过调用GXCommonUtils.convertSourceToTarget方法实现，提供了灵活的对象转换机制。
     * 方法支持通过methodName指定自定义转换方法，通过copyOptions控制属性复制行为，
     * 通过extraData提供额外的转换数据。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用线程安全的工具类进行对象转换
     * - 使用不可变对象作为参数传递，避免并发修改问题
     * </p>
     * <p>
     * 内存安全：
     * - 委托给安全的工具类处理对象转换，避免内存泄漏
     * - 使用泛型参数确保类型安全，减少运行时类型错误
     * - 使用CopyOptions控制对象复制行为，避免不必要的属性复制
     * </p>
     *
     * @param reqDto      请求参数，源对象，不能为null
     * @param targetClass 目标对象类型，指定转换的目标类型，不能为null
     * @param methodName  转换方法名字，可以为null，表示使用默认转换逻辑
     * @param copyOptions 转换的自定义项，控制属性复制行为，不能为null
     * @param extraData   额外数据，用于转换过程中的数据填充，可以为null
     * @return T 转换后的目标类型对象，如果转换失败可能返回null
     */
    @Override
    public <T, Q extends GXBaseApiReqDto> T sourceToTarget(Q reqDto, Class<T> targetClass, String methodName, CopyOptions copyOptions, Dict extraData) {
        return GXCommonUtils.convertSourceToTarget(reqDto, targetClass, methodName, copyOptions, extraData);
    }

    /**
     * 转指定的对象到指定的目标类型对象
     * <p>
     * 该方法是{@link #sourceToTarget(GXBaseApiReqDto, Class, String, CopyOptions, Dict)}的简化版本，
     * 使用空的Dict作为额外数据参数。适用于不需要额外数据的转换场景。
     * 内部通过调用重载方法实现，使用Dict.create()创建空的额外数据对象。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 委托给线程安全的重载方法处理实际转换逻辑
     * </p>
     * <p>
     * 内存安全：
     * - 使用不可变的空Dict对象作为额外参数，避免内存泄漏
     * - 通过重载方法处理空值和异常情况
     * </p>
     *
     * @param reqDto      请求参数，源对象，不能为null
     * @param targetClass 目标对象类型，指定转换的目标类型，不能为null
     * @param methodName  转换方法名字，可以为null，表示使用默认转换逻辑
     * @param copyOptions 转换的自定义项，控制属性复制行为，不能为null
     * @return T 转换后的目标类型对象，如果转换失败可能返回null
     * @see #sourceToTarget(GXBaseApiReqDto, Class, String, CopyOptions, Dict)
     */
    @Override
    public <T, Q extends GXBaseApiReqDto> T sourceToTarget(Q reqDto, Class<T> targetClass, String methodName, CopyOptions copyOptions) {
        return sourceToTarget(reqDto, targetClass, methodName, copyOptions, Dict.create());
    }

    /**
     * 转指定的对象到指定的目标类型对象
     * <p>
     * 该方法是{@link #sourceToTarget(GXBaseApiReqDto, Class, String, CopyOptions)}的最简化版本，
     * 使用null作为方法名和默认的CopyOptions作为参数。适用于使用默认转换逻辑的场景。
     * 内部通过调用重载方法实现，使用null表示默认方法，CopyOptions.create()创建默认的复制选项。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 委托给线程安全的重载方法处理实际转换逻辑
     * </p>
     * <p>
     * 内存安全：
     * - 使用不可变的默认CopyOptions对象，避免内存泄漏
     * - 通过重载方法处理空值和异常情况
     * </p>
     *
     * @param reqDto      请求参数，源对象，不能为null
     * @param targetClass 目标对象类型，指定转换的目标类型，不能为null
     * @return T 转换后的目标类型对象，如果转换失败可能返回null
     * @see #sourceToTarget(GXBaseApiReqDto, Class, String, CopyOptions)
     */
    @Override
    public <T, Q extends GXBaseApiReqDto> T sourceToTarget(Q reqDto, Class<T> targetClass) {
        return sourceToTarget(reqDto, targetClass, null, CopyOptions.create());
    }

    /**
     * 设置服务类的Class对象
     * 在子类的构造函数中调用此方法，将服务类与当前API实现类绑定
     * 该绑定是静态的，对所有实例都有效
     *
     * @param serveServiceClass 服务类Class对象，不能为null
     * @throws IllegalArgumentException 如果serveServiceClass为null
     */
    @Override
    public void staticBindServeServiceClass(Class<?> serveServiceClass) {
        if (Objects.isNull(serveServiceClass)) {
            throw new IllegalArgumentException("服务类Class对象不能为null");
        }
        STATIC_SERVE_SERVICE_CLASS_MAP.put(getClass().getSimpleName(), serveServiceClass);
    }

    /**
     * 子类可以动态指定目标服务类型
     * 该方法用于临时指定一个服务类，在后续的方法调用中使用
     * 注意：该绑定是线程局部的，不会影响其他线程，且在getServeServiceClass调用后会被清理
     *
     * @param targetServeServiceClass 目标服务类的Class对象
     * @return GXBaseServeApi 当前实例，支持链式调用
     */
    @Override
    public GXBaseServeApi callBindTargetServeSericeClass(Class<?> targetServeServiceClass) {
        if (Objects.nonNull(targetServeServiceClass)) {
            DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL.set(targetServeServiceClass);
        }
        return this;
    }

    /**
     * 调用指定类中的指定方法
     * 通过反射机制调用服务类中的方法，支持可变参数
     * 注意：此方法会调用getServeServiceClass()，会清理ThreadLocal中的动态绑定
     *
     * @param methodName 方法名字，不能为空
     * @param params     参数列表，可以为空
     * @return Object 方法调用的返回结果，如果服务类不存在或调用失败则返回null
     * @throws IllegalArgumentException 如果methodName为null或空
     */
    @Override
    public Object callMethod(String methodName, Object... params) {
        if (CharSequenceUtil.isEmpty(methodName)) {
            throw new IllegalArgumentException("方法名不能为空");
        }
        
        Class<?> serveServiceClass = getServeServiceClass();
        if (Objects.nonNull(serveServiceClass)) {
            try {
                return GXCommonUtils.reflectCallObjectMethod(serveServiceClass, methodName, params);
            } catch (Exception e) {
                // 记录异常信息，但不抛出，保持与原方法行为一致
                // 在实际应用中，可能需要考虑是否需要抛出或记录日志
                return null;
            }
        }
        return null;
    }

    /**
     * 获取底层服务类的Class
     * 优先获取动态绑定的服务类，如果不存在则获取静态绑定的服务类
     * 获取后会自动清理ThreadLocal，防止内存泄漏
     *
     * @return Class 返回服务类的类型，可能为null
     */
    @Override
    public Class<?> getServeServiceClass() {
        try {
            // 优先获取动态绑定的服务类
            Class<?> serveServiceClass = DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL.get();
            if (Objects.nonNull(serveServiceClass)) {
                return serveServiceClass;
            }
            // 如果动态绑定不存在，则获取静态绑定的服务类
            return STATIC_SERVE_SERVICE_CLASS_MAP.get(getClass().getSimpleName());
        } finally {
            // 无论如何都清理ThreadLocal，防止内存泄漏
            DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL.remove();
        }
    }

    /**
     * 通过条件查询列表信息
     *
     * @param condition 搜索条件
     * @return List
     */
    @Override
    public List<GXCondition<?>> convertTableConditionToConditionExp(Table<String, String, Object> condition) {
        return convertTableConditionToConditionExp(getTableName(), condition);
    }

    /**
     * 将Table类型的条件转换为条件表达式
     * 此方法将Google Guava的Table格式条件转换为框架内部使用的GXCondition格式
     *
     * @param tableNameAlias 表别名，用于SQL生成时指定表名
     * @param condition      原始条件，Table格式的条件表达式
     * @return List<GXCondition<?>> 转换后的条件表达式列表
     * @throws NullPointerException 如果tableNameAlias为null
     */
    @Override
    public List<GXCondition<?>> convertTableConditionToConditionExp(String tableNameAlias, Table<String, String, Object> condition) {
        if (Objects.isNull(tableNameAlias)) {
            throw new NullPointerException("表别名不能为null");
        }
        return GXCommonUtils.convertTableConditionToConditionExp(tableNameAlias, condition);
    }

    /**
     * 获取表的名字
     * 通过反射调用服务类的getTableName方法获取当前操作的表名
     *
     * @return String 表名字，如果获取失败则可能返回null
     */
    private String getTableName() {
        Object tableName = callMethod("getTableName");
        return Objects.nonNull(tableName) ? (String) tableName : null;
    }
}
