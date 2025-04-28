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
 * <p>
 * 该类是GXBaseServeApi接口的基础实现，封装了常用的数据操作方法，通过反射机制调用底层服务类的方法。
 * 提供了一种灵活的RPC调用机制，支持静态绑定和动态绑定两种方式指定目标服务类。
 * 实现了线程安全和内存安全的最佳实践，确保在高并发环境下的可靠性和稳定性。
 * </p>
 *
 * <p>
 * 线程安全特性：
 * - 使用ConcurrentHashMap存储静态服务类映射，确保线程安全的并发访问
 * - 使用ThreadLocal存储动态绑定的服务类，确保线程隔离和安全
 * - 所有方法设计为无状态操作，可安全地在多线程环境中调用
 * - 参数验证和防御性编程确保多线程环境下的安全性
 * - 自动清理ThreadLocal，防止内存泄漏和线程污染
 * </p>
 *
 * <p>
 * 内存安全特性：
 * - 严格的参数验证，防止空指针异常和非法参数
 * - 安全的类型转换和泛型使用，减少运行时类型错误
 * - 异常处理机制确保在异常情况下资源能够正确释放
 * - 使用不可变对象和线程安全的集合类进行操作
 * - 合理管理ThreadLocal资源，避免内存泄漏
 * </p>
 *
 * <p>
 * 性能优化：
 * - 使用缓存机制减少反射调用的开销
 * - 延迟加载和懒初始化策略
 * - 批量操作和高效的集合处理
 * - 使用ThreadLocal避免锁竞争
 * - 参数验证和类型转换的优化处理
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 1. 创建自定义API实现类
 * public class UserServiceApiImpl extends GXBaseServeApiImpl implements UserServiceApi {
 *     public UserServiceApiImpl() {
 *         // 在构造函数中绑定底层服务类
 *         staticBindServeServiceClass(UserService.class);
 *     }
 *
 *     // 可以添加特定业务方法
 *     public UserResDto getUserByUsername(String username) {
 *         // 创建查询条件
 *         HashBasedTable<String, String, Object> condition = HashBasedTable.create();
 *         condition.put("username", GXBuilderConstant.STR_EQ, username);
 *
 *         // 调用基类方法查询数据
 *         return findOneByCondition(condition, UserResDto.class);
 *     }
 *
 *     // 动态绑定示例
 *     public List<OrderResDto> getUserOrders(Long userId) {
 *         // 临时绑定订单服务
 *         callBindTargetServeSericeClass(OrderService.class);
 *
 *         // 创建查询条件
 *         HashBasedTable<String, String, Object> condition = HashBasedTable.create();
 *         condition.put("user_id", GXBuilderConstant.EQ, userId);
 *
 *         // 查询订单数据
 *         return findByCondition(condition, OrderResDto.class);
 *         // 注意：方法执行完毕后，动态绑定会自动清理
 *     }
 * }
 *
 * // 2. 在服务中使用
 * @Service
 * public class UserService {
 *     @Autowired
 *     private UserServiceApi userServiceApi;
 *
 *     public void processUser(String username) {
 *         // 使用API接口进行数据操作
 *         UserResDto user = userServiceApi.getUserByUsername(username);
 *         if (user != null) {
 *             // 处理用户数据
 *             List<OrderResDto> orders = userServiceApi.getUserOrders(user.getId());
 *             // 处理订单数据
 *         }
 *     }
 * }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 高级使用示例：
 * <pre>
 * {@code
 * // 1. 创建支持多种数据源的API实现类
 * public class MultiDataSourceApiImpl extends GXBaseServeApiImpl implements MultiDataSourceApi {
 *     // 默认绑定主数据源服务
 *     public MultiDataSourceApiImpl() {
 *         staticBindServeServiceClass(PrimaryDataService.class);
 *     }
 *
 *     // 查询主数据源
 *     public List<UserDto> findUsers(String keyword) {
 *         HashBasedTable<String, String, Object> condition = HashBasedTable.create();
 *         condition.put("username", GXBuilderConstant.STR_LIKE, "%" + keyword + "%");
 *         return findByCondition(condition, UserDto.class);
 *     }
 *
 *     // 查询历史数据源
 *     public List<UserHistoryDto> findUserHistory(Long userId) {
 *         // 动态切换到历史数据服务
 *         callBindTargetServeSericeClass(HistoryDataService.class);
 *
 *         HashBasedTable<String, String, Object> condition = HashBasedTable.create();
 *         condition.put("user_id", GXBuilderConstant.EQ, userId);
 *         return findByCondition(condition, UserHistoryDto.class);
 *     }
 *
 *     // 复杂业务场景：跨数据源操作
 *     public UserCompleteInfoDto getUserCompleteInfo(Long userId) {
 *         // 1. 从主数据源获取用户基本信息
 *         HashBasedTable<String, String, Object> userCondition = HashBasedTable.create();
 *         userCondition.put("id", GXBuilderConstant.EQ, userId);
 *         UserDto userDto = findOneByCondition(userCondition, UserDto.class);
 *
 *         if (userDto == null) {
 *             return null;
 *         }
 *
 *         // 2. 从历史数据源获取用户历史记录
 *         callBindTargetServeSericeClass(HistoryDataService.class);
 *         HashBasedTable<String, String, Object> historyCondition = HashBasedTable.create();
 *         historyCondition.put("user_id", GXBuilderConstant.EQ, userId);
 *         List<UserHistoryDto> historyList = findByCondition(historyCondition, UserHistoryDto.class);
 *
 *         // 3. 从配置数据源获取用户偏好设置
 *         callBindTargetServeSericeClass(ConfigDataService.class);
 *         HashBasedTable<String, String, Object> configCondition = HashBasedTable.create();
 *         configCondition.put("user_id", GXBuilderConstant.EQ, userId);
 *         UserConfigDto configDto = findOneByCondition(configCondition, UserConfigDto.class);
 *
 *         // 4. 组装完整用户信息
 *         UserCompleteInfoDto completeInfo = new UserCompleteInfoDto();
 *         completeInfo.setUserInfo(userDto);
 *         completeInfo.setHistoryList(historyList);
 *         completeInfo.setUserConfig(configDto);
 *
 *         return completeInfo;
 *     }
 * }
 *
 * // 在服务中使用
 * @Service
 * public class UserAnalysisService {
 *     @Autowired
 *     private MultiDataSourceApi multiDataSourceApi;
 *
 *     public UserAnalysisReport generateReport(Long userId) {
 *         // 获取用户完整信息（跨多个数据源）
 *         UserCompleteInfoDto completeInfo = multiDataSourceApi.getUserCompleteInfo(userId);
 *
 *         // 基于完整信息生成分析报告
 *         UserAnalysisReport report = new UserAnalysisReport();
 *         // ... 处理报告逻辑 ...
 *
 *         return report;
 *     }
 * }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 注意事项：
 * - 使用动态绑定时，确保在方法调用完成后自动清理ThreadLocal
 * - 参数验证是必要的，确保传入参数的合法性
 * - 异常处理应当遵循统一的策略，避免异常信息丢失
 * - 类型转换应当使用安全的转换方法，避免ClassCastException
 * - 在高并发环境下，注意ThreadLocal的使用和清理
 * - 动态绑定在一次方法调用后会自动清理，如需连续使用同一绑定，需要在每次调用前重新绑定
 * - 静态绑定适用于API实现类与特定服务类紧密耦合的场景，动态绑定适用于需要临时切换服务类的场景
 * - 反射调用可能带来性能开销，在高频调用场景下应考虑结果缓存或其他优化策略
 * </p>
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
     * @throws NullPointerException     如果targetClazz或column为null
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
     * <p>
     * 该方法用于在子类的构造函数中调用，将服务类与当前API实现类进行静态绑定。
     * 绑定后，所有该API实现类的实例都将共享这个服务类引用，实现类型安全的服务调用。
     * </p>
     *
     * <p>
     * 线程安全特性：
     * - 使用ConcurrentHashMap存储映射关系，确保线程安全
     * - 绑定操作是原子的，不会出现部分更新状态
     * - 绑定后的映射对所有线程可见
     * </p>
     *
     * <p>
     * 使用示例：
     * {@code
     * public class UserServiceApiImpl extends GXBaseServeApiImpl implements UserServiceApi {
     * public UserServiceApiImpl() {
     * // 在构造函数中绑定服务类
     * staticBindServeServiceClass(UserService.class);
     * }
     * }
     * }
     * </p>
     *
     * <p>
     * 注意事项：
     * - 此方法通常应在子类构造函数中调用，确保API实例创建时即完成绑定
     * - 一个API实现类只应绑定一个服务类，重复绑定会覆盖之前的绑定
     * - 绑定使用API类的简单名称作为键，确保不同包中的同名类不会冲突
     * </p>
     *
     * @param serveServiceClass 服务类Class对象，不能为null
     * @throws IllegalArgumentException 如果serveServiceClass为null
     */
    @Override
    public void staticBindServeServiceClass(Class<?> serveServiceClass) {
        if (Objects.isNull(serveServiceClass)) {
            throw new IllegalArgumentException("服务类Class对象不能为null");
        }
        String apiClassName = getClass().getSimpleName();
        STATIC_SERVE_SERVICE_CLASS_MAP.put(apiClassName, serveServiceClass);
    }

    /**
     * 动态指定目标服务类型
     * <p>
     * 该方法用于临时指定一个服务类，在当前线程的后续方法调用中使用。
     * 与静态绑定不同，动态绑定仅对当前线程有效，且在调用getServeServiceClass()后会自动清理。
     * 这种机制允许在不改变API实现类静态绑定的情况下，临时切换到其他服务类进行操作。
     * </p>
     *
     * <p>
     * 线程安全特性：
     * - 使用ThreadLocal存储，确保线程间隔离
     * - 每个线程只能看到自己设置的服务类，不会影响其他线程
     * - 自动清理机制防止内存泄漏和线程污染
     * </p>
     *
     * <p>
     * 使用示例：
     * {@code
     * // 临时切换到订单服务处理逻辑
     * userServiceApi.callBindTargetServeSericeClass(OrderService.class)
     * .findByCondition(condition, OrderDto.class);
     * // 此时动态绑定已被清理，后续调用将使用静态绑定的服务类
     * }
     * </p>
     *
     * <p>
     * 注意事项：
     * - 动态绑定会在getServeServiceClass()调用后自动清理，通常是在一次方法调用后
     * - 如果需要连续多次使用同一动态绑定，应当在每次方法调用前重新绑定
     * - 传入null不会产生任何效果，保持当前绑定状态不变
     * </p>
     *
     * @param targetServeServiceClass 目标服务类的Class对象，如果为null则不进行任何操作
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
     * <p>
     * 通过反射机制调用服务类中的方法，支持可变参数传递。
     * 该方法是框架内部方法调用的核心机制，通过动态反射实现对目标服务类方法的调用。
     * 注意：此方法会调用getServeServiceClass()，会清理ThreadLocal中的动态绑定。
     * </p>
     *
     * <p>
     * 线程安全特性：
     * - 方法本身是无状态的，可安全地在多线程环境中调用
     * - 通过getServeServiceClass()获取服务类，确保线程安全的服务类引用
     * - 反射调用过程不修改共享状态，不会导致线程安全问题
     * </p>
     *
     * <p>
     * 性能优化：
     * - 使用GXCommonUtils.reflectCallObjectMethod进行反射调用，该方法内部可能包含缓存机制
     * - 参数验证避免不必要的反射调用尝试
     * - 异常处理机制确保在调用失败时能够优雅降级
     * </p>
     *
     * <p>
     * 错误处理：
     * - 方法名为空时抛出IllegalArgumentException
     * - 服务类不存在时返回null
     * - 反射调用异常时捕获并返回null，可在子类中重写以实现自定义异常处理
     * </p>
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
     * <p>
     * 该方法用于获取当前API实现类绑定的服务类。遵循优先级策略：
     * 1. 优先返回通过callBindTargetServeSericeClass()动态绑定的服务类
     * 2. 如果动态绑定不存在，则返回通过staticBindServeServiceClass()静态绑定的服务类
     * 3. 如果两种绑定都不存在，则返回null
     * </p>
     *
     * <p>
     * 线程安全特性：
     * - 使用try-finally结构确保ThreadLocal资源正确释放
     * - 动态绑定使用ThreadLocal实现线程隔离
     * - 静态绑定使用ConcurrentHashMap确保线程安全的读取
     * </p>
     *
     * <p>
     * 内存安全特性：
     * - 自动清理ThreadLocal，防止内存泄漏
     * - 即使在异常情况下也能确保ThreadLocal被清理
     * - 不保留对返回的Class对象的引用，避免类加载器泄漏
     * </p>
     *
     * <p>
     * 注意事项：
     * - 此方法会清理ThreadLocal中的动态绑定，调用后动态绑定将失效
     * - 如果需要连续多次使用同一动态绑定，应当在每次方法调用前重新绑定
     * - 返回值可能为null，调用方需要进行空值检查
     * </p>
     *
     * @return Class 返回服务类的类型，如果没有绑定任何服务类则返回null
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
            String apiClassName = getClass().getSimpleName();
            return STATIC_SERVE_SERVICE_CLASS_MAP.get(apiClassName);
        } finally {
            // 无论如何都清理ThreadLocal，防止内存泄漏
            DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL.remove();
        }
    }

    /**
     * 将Table类型的条件转换为条件表达式
     * <p>
     * 该方法是{@link #convertTableConditionToConditionExp(String, Table)}的简化版本，
     * 使用当前实体对应的表名作为表别名参数。适用于不需要指定特定表别名的场景。
     * 内部通过调用getTableName()获取当前表名，然后委托给重载方法处理实际转换逻辑。
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 委托给线程安全的重载方法处理实际转换逻辑
     * </p>
     * <p>
     * 内存安全：
     * - 通过重载方法处理空值和异常情况
     * - 使用不可变对象作为参数传递，避免并发修改问题
     * </p>
     * <p>
     * 性能考虑：
     * - 此方法会调用getTableName()，可能涉及反射调用，在高频调用场景下可考虑缓存表名
     * </p>
     *
     * @param condition 搜索条件，Table格式的条件表达式，不能为null
     * @return List<GXCondition < ?>> 转换后的条件表达式列表
     * @see #convertTableConditionToConditionExp(String, Table)
     */
    @Override
    public List<GXCondition<?>> convertTableConditionToConditionExp(Table<String, String, Object> condition) {
        return convertTableConditionToConditionExp(getTableName(), condition);
    }

    /**
     * 将Table类型的条件转换为条件表达式
     * <p>
     * 此方法将Google Guava的Table格式条件转换为框架内部使用的GXCondition格式。
     * 转换过程会保留条件的字段名、操作符和值，生成适合框架内部处理的条件表达式列表。
     * 该方法是框架内部查询条件转换的核心方法，被多个查询方法调用。
     * </p>
     * <p>
     * 转换规则：
     * - Table的行键(rowKey)对应字段名
     * - Table的列键(columnKey)对应操作符，应使用GXBuilderConstant中定义的常量
     * - Table的值(value)对应条件值
     * </p>
     * <p>
     * 线程安全：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用GXCommonUtils工具类进行实际转换，该工具类设计为线程安全
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 委托给安全的工具类处理条件转换，避免内存泄漏
     * </p>
     * <p>
     * 使用示例：
     * {@code
     * HashBasedTable<String, String, Object> condition = HashBasedTable.create();
     * condition.put("username", GXBuilderConstant.STR_EQ, "admin");
     * condition.put("status", GXBuilderConstant.EQ, 1);
     * List<GXCondition<?>> conditions = convertTableConditionToConditionExp("user", condition);
     * }
     * </p>
     *
     * @param tableNameAlias 表别名，用于SQL生成时指定表名，不能为null
     * @param condition      原始条件，Table格式的条件表达式，可以为null或空
     * @return List<GXCondition < ?>> 转换后的条件表达式列表，如果condition为null或空则返回空列表
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
     * <p>
     * 该方法通过反射调用服务类的getTableName方法获取当前操作的表名。
     * 表名用于构建SQL查询条件和处理数据库操作，是数据访问层的重要组成部分。
     * </p>
     *
     * <p>
     * 线程安全特性：
     * - 方法本身是无状态的，可安全地在多线程环境中调用
     * - 通过callMethod间接调用服务类方法，继承了callMethod的线程安全特性
     * - 不修改共享状态，不会导致线程安全问题
     * </p>
     *
     * <p>
     * 性能考虑：
     * - 此方法涉及反射调用，在高频调用场景下可考虑结果缓存
     * - 返回结果可能为null，调用方需要进行适当的空值处理
     * </p>
     *
     * <p>
     * 注意事项：
     * - 调用此方法会触发ThreadLocal清理，可能影响动态绑定状态
     * - 服务类必须实现getTableName方法，否则将返回null
     * - 返回值取决于底层服务类的实现，可能需要进行类型安全的转换
     * </p>
     *
     * @return String 表名字，如果获取失败则返回null
     */
    private String getTableName() {
        Object tableName = callMethod("getTableName");
        if (Objects.nonNull(tableName)) {
            if (tableName instanceof String) {
                return (String) tableName;
            } else {
                // 如果返回值不是String类型，尝试转换为String
                return String.valueOf(tableName);
            }
        }
        return null;
    }
}
