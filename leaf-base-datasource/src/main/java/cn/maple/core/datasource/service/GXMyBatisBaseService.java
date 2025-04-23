package cn.maple.core.datasource.service;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.dao.GXMyBatisDao;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import cn.maple.core.datasource.repository.GXMyBatisRepository;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.model.GXBaseModel;
import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.core.framework.util.GXCommonUtils;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 业务DB基础Service
 * <p>
 * 该接口定义了MyBatis数据库操作的基础服务方法，提供了一系列数据库操作功能，包括：
 * 1. 条件查询 - 支持多种条件组合的单条和批量查询
 * 2. 数据更新 - 支持条件更新和创建或更新操作
 * 3. 数据删除 - 支持物理删除和逻辑删除
 * 4. 分页查询 - 支持复杂条件的分页数据获取
 * 5. 字段查询 - 支持查询指定字段或单个字段的值
 * </p>
 * <p>
 * 线程安全说明：
 * 该接口的实现类应确保所有方法在多线程环境下是安全的。特别是：
 * - 查询方法应避免修改共享状态
 * - 更新方法应使用事务确保数据一致性
 * - 所有方法应防御性地处理输入参数，避免并发修改异常
 * </p>
 * <p>
 * 事务安全：
 * - 所有修改数据的方法应在事务控制下执行
 * - 实现类应使用Spring的@Transactional注解或编程式事务管理
 * - 事务边界应明确定义，避免长事务
 * </p>
 * <p>
 * 安全性说明：
 * - 所有SQL操作均通过MyBatis的参数化查询机制执行，有效防止SQL注入攻击
 * - 条件查询使用GXCondition对象构建，参数值通过#{param}方式绑定，而非字符串拼接
 * - 更新操作使用GXUpdateField对象，确保字段名和值的安全处理
 * - 所有用户输入都应经过验证和清洗，特别是用于构建查询条件的参数
 * </p>
 * <p>
 * 性能优化：
 * - 查询时应只选择必要的字段，避免SELECT *
 * - 合理使用索引，确保查询条件中的字段已建立适当的索引
 * - 分页查询应设置合理的页大小，避免一次获取过多数据
 * - 批量操作应控制批次大小，避免单次事务处理过多数据
 * - 对于频繁查询的数据，考虑使用缓存机制
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 条件查询示例
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(new GXConditionEQ("status", 1));
 * conditions.add(new GXConditionLike("name", "%测试%"));
 * List<UserResDto> users = userService.findByCondition(conditions);
 * 
 * // 2. 创建或更新示例
 * UserReqDto reqDto = new UserReqDto();
 * reqDto.setUsername("test_user");
 * reqDto.setEmail("test@example.com");
 * Long userId = userService.updateOrCreate(reqDto);
 * 
 * // 3. 分页查询示例
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("t_user")
 *     .condition(conditions)
 *     .page(1)
 *     .pageSize(10)
 *     .build();
 * GXPaginationResDto<UserResDto> pageData = userService.paginate(queryParam);
 * 
 * // 4. 字段更新示例
 * List<GXUpdateField<?>> updateFields = new ArrayList<>();
 * updateFields.add(new GXUpdateField<>("status", 2));
 * updateFields.add(new GXUpdateField<>("update_time", new Date()));
 * 
 * List<GXCondition<?>> updateConditions = new ArrayList<>();
 * updateConditions.add(new GXConditionEQ("id", 100));
 * 
 * Integer rows = userService.updateFieldByCondition(updateFields, updateConditions);
 * 
 * // 5. 软删除示例
 * List<GXCondition<?>> deleteConditions = new ArrayList<>();
 * deleteConditions.add(new GXConditionEQ("id", 100));
 * userService.deleteSoftCondition(deleteConditions);
 * </pre>
 * </p>
 *
 * @param <P>  仓库对象类型，必须继承自GXMyBatisRepository
 * @param <M>  Mapper类型，必须继承自GXBaseMapper
 * @param <T>  实体类型，必须继承自GXBaseModel
 * @param <D>  DAO类型，必须继承自GXMyBatisDao
 * @param <R>  响应对象类型，必须继承自GXBaseDBResDto
 * @param <ID> 实体的主键ID类型，必须实现Serializable接口
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public interface GXMyBatisBaseService<P extends GXMyBatisRepository<M, T, D, ID>, M extends GXBaseMapper<T>, T extends GXBaseModel, D extends GXMyBatisDao<M, T, ID>, R extends GXBaseDBResDto, ID extends Serializable> extends GXBusinessService, GXValidateDBExistsService {
    /**
     * 检测给定条件的记录是否存在
     * <p>
     * 该方法用于验证指定表中是否存在满足条件的记录。
     * 内部通过调用Repository的checkRecordIsExists方法实现，返回结果会转换为布尔值。
     * </p>
     * <p>
     * 线程安全：该方法不修改共享状态，可在多线程环境中安全调用
     * </p>
     * <p>
     * 安全性说明：
     * 该方法使用GXCondition对象构建查询条件，参数通过MyBatis的参数化查询机制(#{})绑定，
     * 有效防止SQL注入攻击。所有条件参数都应经过验证，避免恶意输入。
     * </p>
     * <p>
     * 性能优化：
     * 1. 该方法通常用于验证记录是否存在，查询时应只返回必要的字段（如COUNT(1)）
     * 2. 确保条件字段已建立适当的索引，特别是经常用于查询的字段
     * 3. 对于频繁查询的相同条件，考虑使用缓存机制
     * </p>
     *
     * @param tableName 数据库表名字，不能为null或空字符串
     * @param condition 条件，不能为null或空列表
     * @return boolean 存在返回true，不存在返回false
     */
    boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition);

    /**
     * 检测给定条件的记录是否存在
     * <p>
     * 该方法是{@link #checkRecordIsExists(String, List)}的简化版本，
     * 使用当前实体对应的表名作为参数。
     * </p>
     * <p>
     * 线程安全：该方法不修改共享状态，可在多线程环境中安全调用
     * </p>
     *
     * @param condition 条件，不能为null或空列表
     * @return boolean 存在返回true，不存在返回false
     */
    boolean checkRecordIsExists(List<GXCondition<?>> condition);

    /**
     * 通过SQL更新表中的数据
     * <p>
     * 该方法根据指定的条件更新表中的数据。更新前会先验证记录是否存在，不存在则返回错误码。
     * 实现类应确保该方法在事务中执行，以保证数据一致性。
     * </p>
     * <p>
     * 线程安全：实现类应确保在多线程环境下安全更新数据，避免并发修改问题
     * 事务安全：实现类应使用@Transactional注解确保事务的原子性
     * </p>
     * <p>
     * 安全性说明：
     * 1. 该方法使用GXUpdateField对象构建更新字段，参数通过MyBatis的参数化查询机制(#{})绑定
     * 2. 更新条件使用GXCondition对象构建，同样通过参数化方式处理，有效防止SQL注入
     * 3. 所有更新字段和条件参数都应经过验证，避免恶意输入
     * 4. 实现类应检查用户权限，确保只有授权用户才能执行更新操作
     * </p>
     * <p>
     * 性能优化：
     * 1. 更新操作应在事务中执行，但应避免长事务
     * 2. 确保条件字段已建立适当的索引，减少锁定范围
     * 3. 批量更新时应控制批次大小，避免锁定过多数据
     * 4. 考虑使用乐观锁机制（如版本号）处理并发更新
     * </p>
     *
     * @param tableName    表名字，不能为null或空字符串
     * @param updateFields 需要更新的数据字段列表，不能为null
     * @param condition    更新条件，不能为null或空列表
     * @return Integer 影响的行数，更新成功返回大于0的整数，记录不存在返回特定错误码
     */
    Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition);

    /**
     * 通过SQL更新表中的数据
     * <p>
     * 该方法是{@link #updateFieldByCondition(String, List, List)}的简化版本，
     * 使用当前实体对应的表名作为参数。
     * </p>
     * <p>
     * 线程安全：实现类应确保在多线程环境下安全更新数据，避免并发修改问题
     * 事务安全：实现类应使用@Transactional注解确保事务的原子性
     * </p>
     *
     * @param updateFields 需要更新的数据字段列表，不能为null
     * @param condition    更新条件，不能为null或空列表
     * @return Integer 影响的行数，更新成功返回大于0的整数，记录不存在返回特定错误码
     */
    Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition);

    /**
     * 列表或者搜索(分页)
     * <p>
     * 该方法根据查询参数执行分页查询，返回分页结果对象。
     * 支持复杂条件查询、字段筛选、排序和分组等操作。
     * </p>
     * <p>
     * 安全性说明：
     * 1. 查询条件通过GXCondition对象构建，参数通过MyBatis的参数化查询机制(#{})绑定
     * 2. 所有查询参数都应经过验证，避免恶意输入
     * 3. 实现类应检查用户权限，确保只返回用户有权访问的数据
     * </p>
     * <p>
     * 性能优化：
     * 1. 分页查询应设置合理的页大小，避免一次获取过多数据
     * 2. 查询时应只选择必要的字段，避免SELECT *
     * 3. 确保查询条件中的字段已建立适当的索引
     * 4. 对于复杂查询，考虑使用索引覆盖或复合索引
     * 5. 对于频繁查询的相同条件，考虑使用缓存机制
     * </p>
     *
     * @param searchReqDto 查询参数，包含分页信息、查询条件、排序字段等
     * @return GXPaginationResDto 分页结果对象，包含总记录数、当前页数据等
     */
    GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto searchReqDto);

    /**
     * 列表或者搜索(分页)
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return GXPagination
     */
    GXPaginationResDto<R> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    /**
     * 通过条件查询列表信息
     * <p>
     * 该方法根据查询参数执行列表查询，返回符合条件的数据列表。
     * 支持复杂条件查询、字段筛选、排序和分组等操作。
     * </p>
     * <p>
     * 安全性说明：
     * 1. 查询条件通过GXCondition对象构建，参数通过MyBatis的参数化查询机制(#{})绑定
     * 2. 所有查询参数都应经过验证，避免恶意输入和SQL注入攻击
     * 3. 实现类应检查用户权限，确保只返回用户有权访问的数据
     * </p>
     * <p>
     * 性能优化：
     * 1. 查询时应只选择必要的字段，避免SELECT *
     * 2. 确保查询条件中的字段已建立适当的索引
     * 3. 对于大数据量查询，应考虑分页或限制返回记录数
     * 4. 对于频繁查询的相同条件，考虑使用缓存机制
     * </p>
     *
     * @param queryParamInnerDto 查询参数，包含查询条件、排序字段等
     * @return List 符合条件的数据列表
     */
    List<R> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto);

    /**
     * 通过条件查询列表信息
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return List
     */
    List<R> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    /**
     * 通过条件查询列表信息
     *
     * @param queryParamInnerDto 查询条件
     * @param rowMapper          映射函数
     * @return List
     */
    <E> List<E> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper);

    /**
     * 通过条件查询列表信息
     *
     * @param tableName 表名字
     * @param columns   需要查询的字段
     * @param condition 搜索条件
     * @return List
     */
    List<R> findByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition);

    /**
     * 通过条件查询列表信息
     *
     * @param tableName 表名字
     * @param condition 搜索条件
     * @return List
     */
    List<R> findByCondition(String tableName, List<GXCondition<?>> condition);

    /**
     * 通过条件查询列表信息
     *
     * @param condition 搜索条件
     * @return List
     */
    List<R> findByCondition(List<GXCondition<?>> condition);

    /**
     * 通过条件查询列表信息
     *
     * @param condition 搜索条件
     * @param extraData 额外数据
     * @return List
     */
    List<R> findByCondition(List<GXCondition<?>> condition, Object extraData);

    /**
     * 通过条件查询列表信息
     *
     * @param condition 搜索条件
     * @param columns   需要查询的列
     * @return List
     */
    List<R> findByCondition(List<GXCondition<?>> condition, Set<String> columns);

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
    List<R> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Map<String, String> orderField, Set<String> groupField);

    /**
     * 通过条件查询列表信息
     *
     * @param condition  搜索条件
     * @param orderField 排序字段
     * @param groupField 分组字段
     * @return List
     */
    List<R> findByCondition(List<GXCondition<?>> condition, Map<String, String> orderField, Set<String> groupField);

    /**
     * 通过条件查询列表信息
     *
     * @param condition  搜索条件
     * @param orderField 排序字段
     * @return List
     */
    List<R> findByCondition(List<GXCondition<?>> condition, Map<String, String> orderField);

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
    R findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Map<String, String> orderField, Set<String> groupField);

    /**
     * 通过条件获取一条数据
     *
     * @param tableName 表名字
     * @param columns   需要查询的字段
     * @param condition 搜索条件
     * @param extraData 额外参数
     * @return 一条数据
     */
    R findOneByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition, Object extraData);

    /**
     * 通过条件获取一条数据
     *
     * @param tableName 表名字
     * @param columns   需要查询的字段
     * @param condition 搜索条件
     * @return 一条数据
     */
    R findOneByCondition(String tableName, Set<String> columns, List<GXCondition<?>> condition);

    /**
     * 通过条件获取一条数据
     *
     * @param queryParamInnerDto 搜索条件
     * @return 一条数据
     */
    R findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto);

    /**
     * 通过条件获取一条数据
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return 一条数据
     */
    R findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    /**
     * 通过条件获取一条数据
     *
     * @param queryParamInnerDto 搜索条件
     * @param rowMapper          映射函数
     * @return 一条数据
     */
    <E> E findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Function<Dict, E> rowMapper);

    /**
     * 通过条件获取一条数据
     *
     * @param tableName 表名字
     * @param condition 搜索条件
     * @return 一条数据
     */
    R findOneByCondition(String tableName, List<GXCondition<?>> condition);

    /**
     * 通过条件获取一条数据
     *
     * @param condition 搜索条件
     * @param extraData 额外参数
     * @return 一条数据
     */
    R findOneByCondition(List<GXCondition<?>> condition, Object extraData);

    /**
     * 通过条件获取一条数据
     *
     * @param condition 搜索条件
     * @return 一条数据
     */
    R findOneByCondition(List<GXCondition<?>> condition);

    /**
     * 通过条件获取一条数据
     *
     * @param condition 搜索条件
     * @param columns   字段集合
     * @return 一条数据
     */
    R findOneByCondition(List<GXCondition<?>> condition, Set<String> columns);

    /**
     * 通过条件获取一条数据
     *
     * @param condition 搜索条件
     * @param columns   字段集合
     * @return 一条数据
     */
    R findOneByCondition(List<GXCondition<?>> condition, Set<String> columns, Object extraData);

    /**
     * 创建或者更新
     * <p>
     * 该方法根据实体对象执行创建或更新操作。如果实体ID为空或数据库中不存在该ID的记录，则创建新记录；
     * 否则更新已有记录。
     * </p>
     * <p>
     * 安全性说明：
     * 1. 实体对象的属性通过MyBatis的参数化查询机制(#{})绑定，有效防止SQL注入攻击
     * 2. 所有实体属性都应经过验证，避免恶意输入
     * 3. 实现类应检查用户权限，确保只能创建或更新有权限的数据
     * 4. 敏感字段（如密码）应在持久化前进行加密处理
     * </p>
     * <p>
     * 性能优化：
     * 1. 创建或更新操作应在事务中执行，但应避免长事务
     * 2. 更新时应只更新已修改的字段，避免不必要的数据库操作
     * 3. 考虑使用乐观锁机制（如版本号）处理并发更新
     * 4. 对于频繁更新的数据，应评估缓存失效策略
     * </p>
     *
     * @param entity 数据实体，包含需要创建或更新的数据
     * @return ID 实体的主键ID
     */
    ID updateOrCreate(T entity);

    /**
     * 创建或者更新
     *
     * @param entity    数据实体
     * @param condition 更新条件
     * @return ID
     */
    ID updateOrCreate(T entity, List<GXCondition<?>> condition);

    /**
     * 创建或者更新
     *
     * @param req         请求参数
     * @param condition   条件
     * @param copyOptions 复制可选项
     * @return ID
     */
    <Q extends GXBaseReqDto> ID updateOrCreate(Q req, List<GXCondition<?>> condition, CopyOptions copyOptions);

    /**
     * 创建或者更新
     *
     * @param req         请求参数
     * @param copyOptions 复制可选项
     * @return ID
     */
    <Q extends GXBaseReqDto> ID updateOrCreate(Q req, CopyOptions copyOptions);

    /**
     * 创建或者更新
     *
     * @param req 请求参数
     * @return ID
     */
    <Q extends GXBaseReqDto> ID updateOrCreate(Q req);

    /**
     * 复制一条数据
     *
     * @param copyCondition 复制的条件
     * @param replaceData   需要替换的数据
     * @param extraData     额外数据
     * @return 新数据ID
     */
    ID copyOneData(List<GXCondition<?>> copyCondition, Dict replaceData, Dict extraData);

    /**
     * 复制一条数据
     *
     * @param copyCondition 复制的条件
     * @param replaceData   需要替换的数据
     * @return 新数据ID
     */
    ID copyOneData(List<GXCondition<?>> copyCondition, Dict replaceData);

    /**
     * 根据条件软(逻辑)删除
     * <p>
     * 该方法执行软删除操作，通常是将数据标记为已删除状态，而非物理删除。
     * 可同时更新其他字段，如删除时间、删除人等信息。
     * </p>
     * <p>
     * 安全性说明：
     * 1. 删除条件通过GXCondition对象构建，参数通过MyBatis的参数化查询机制(#{})绑定
     * 2. 更新字段通过GXUpdateField对象构建，同样通过参数化方式处理
     * 3. 所有参数都应经过验证，避免恶意输入和SQL注入攻击
     * 4. 实现类应检查用户权限，确保只能删除有权限的数据
     * 5. 应记录删除操作的日志，便于审计和追踪
     * </p>
     * <p>
     * 性能优化：
     * 1. 软删除操作应在事务中执行，但应避免长事务
     * 2. 确保条件字段已建立适当的索引，减少锁定范围
     * 3. 批量软删除时应控制批次大小，避免锁定过多数据
     * 4. 考虑在已删除数据上建立索引，提高查询效率
     * </p>
     *
     * @param tableName       表名，不能为null或空字符串
     * @param updateFieldList 软删除时需要同时更新的字段列表，不能为null
     * @param condition       删除条件，不能为null或空列表
     * @param extraData       额外数据，可用于传递上下文信息
     * @return Integer 影响的行数，删除成功返回大于0的整数
     */
    Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, @NotNull Dict extraData);

    /**
     * 根据条件软(逻辑)删除
     *
     * @param tableName 表名
     * @param condition 删除条件
     * @param extraData 额外数据
     * @return 影响行数
     */
    Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData);

    /**
     * 根据条件软(逻辑)删除
     *
     * @param tableName 表名
     * @param condition 删除条件
     * @return 影响行数
     */
    Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition);

    /**
     * 根据条件软(逻辑)删除
     *
     * @param condition 删除条件
     * @return 影响行数
     */
    Integer deleteSoftCondition(List<GXCondition<?>> condition);

    /**
     * 根据条件删除
     * <p>
     * 该方法执行物理删除操作，将数据从数据库中永久移除。
     * 与软删除不同，物理删除后数据无法恢复，应谨慎使用。
     * </p>
     * <p>
     * 安全性说明：
     * 1. 删除条件通过GXCondition对象构建，参数通过MyBatis的参数化查询机制(#{})绑定
     * 2. 所有参数都应经过严格验证，避免恶意输入和SQL注入攻击
     * 3. 实现类应检查用户权限，确保只能删除有权限的数据
     * 4. 应记录删除操作的详细日志，包括删除条件和影响行数
     * 5. 考虑使用软删除代替物理删除，除非确实需要彻底移除数据
     * </p>
     * <p>
     * 性能优化：
     * 1. 物理删除操作应在事务中执行，但应避免长事务
     * 2. 确保条件字段已建立适当的索引，减少锁定范围
     * 3. 对于大量数据的删除，应考虑分批执行，避免锁表
     * 4. 注意删除操作可能触发级联删除，影响性能
     * 5. 删除前应评估对关联数据的影响，避免破坏数据完整性
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 删除条件，不能为null或空列表
     * @return Integer 影响的行数，删除成功返回大于0的整数
     */
    Integer deleteCondition(String tableName, List<GXCondition<?>> condition);

    /**
     * 根据条件删除
     *
     * @param condition 删除条件
     * @return 影响行数
     */
    Integer deleteCondition(List<GXCondition<?>> condition);

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
    <E> List<E> findMultiFieldByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz);

    /**
     * 查询指定字段的值
     * <pre>
     *     {@code findFieldByCondition(condition1, CollUtil.newHashSet("nickname", "username"), Dict.class);}
     * </pre>
     *
     * @param condition   查询条件
     * @param columns     字段名字集合
     * @param targetClazz 值的类型
     * @return 返回指定的类型的值对象
     */
    <E> List<E> findMultiFieldByCondition(List<GXCondition<?>> condition, Set<String> columns, Class<E> targetClazz);

    /**
     * 查询指定字段的值
     *
     * @param queryParamInnerDto 查询参数
     * @param targetClazz        返回数据的类型
     * @return 返回指定的类型的值对象
     */
    <E> List<E> findMultiFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz);

    /**
     * 获取一条记录的指定单字段
     *
     * @param condition   条件
     * @param column      字段名字
     * @param targetClazz 返回的类型
     * @return 指定的类型
     */
    /**
     * 获取一条记录的指定单字段
     * <p>
     * 该方法根据条件查询指定表中的单个字段值，返回指定类型的结果。
     * 内部通过构建GXBaseQueryParamInnerDto对象，调用重载的findSingleFieldByCondition方法实现。
     * </p>
     * <p>
     * 安全性说明：
     * 1. 查询条件通过GXCondition对象构建，参数通过MyBatis的参数化查询机制(#{})绑定
     * 2. 所有查询参数都应经过验证，避免恶意输入和SQL注入攻击
     * 3. 字段名作为参数传入，应确保是有效的数据库字段名
     * </p>
     * <p>
     * 性能优化：
     * 1. 该方法只查询单个字段，避免了不必要的数据传输
     * 2. 确保条件字段和查询字段已建立适当的索引
     * 3. 对于频繁查询的相同条件，考虑使用缓存机制
     * </p>
     *
     * @param condition   查询条件，不能为null或空列表
     * @param column      要查询的字段名，不能为null或空字符串
     * @param targetClazz 返回结果的目标类型
     * @return E 指定类型的字段值，如果未找到记录则可能返回null
     */
    default <E> E findSingleFieldByCondition(List<GXCondition<?>> condition, String column, Class<E> targetClazz) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(getTableName()).condition(condition).columns(CollUtil.newHashSet(column)).build();
        return findSingleFieldByCondition(queryParamInnerDto, targetClazz);
    }

    /**
     * 获取一条记录的指定单字段
     *
     * @param queryParamInnerDto 查询条件
     * @param targetClazz        返回的类型
     * @return 指定的类型
     */
    <E> E findSingleFieldByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz);

    /**
     * 获取指定单字段的列表
     * <p>
     * 该方法根据条件查询指定表中的单个字段值列表，支持分组，返回指定类型的结果列表。
     * 内部通过构建GXBaseQueryParamInnerDto对象，调用重载的findSingleFieldLstByCondition方法实现。
     * </p>
     * <p>
     * 安全性说明：
     * 1. 查询条件通过GXCondition对象构建，参数通过MyBatis的参数化查询机制(#{})绑定
     * 2. 所有查询参数都应经过验证，避免恶意输入和SQL注入攻击
     * 3. 字段名和分组字段作为参数传入，应确保是有效的数据库字段名
     * </p>
     * <p>
     * 性能优化：
     * 1. 该方法只查询单个字段，避免了不必要的数据传输
     * 2. 确保条件字段、查询字段和分组字段已建立适当的索引
     * 3. 对于大数据量查询，应考虑分页或限制返回记录数
     * 4. 对于频繁查询的相同条件，考虑使用缓存机制
     * 5. 分组查询可能会消耗较多资源，应谨慎使用并评估性能影响
     * </p>
     *
     * @param condition    查询条件，不能为null或空列表
     * @param column       要查询的字段名，不能为null或空字符串
     * @param targetClazz  返回结果的目标类型
     * @param groupByField 分组字段集合，可以为null或空集合表示不分组
     * @return List<E> 指定类型的字段值列表，如果未找到记录则返回空列表
     */
    default <E> List<E> findSingleFieldLstByCondition(List<GXCondition<?>> condition, String column, Class<E> targetClazz, Set<String> groupByField) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(getTableName()).condition(condition).columns(CollUtil.newHashSet(column)).groupByField(groupByField).build();
        return findSingleFieldLstByCondition(queryParamInnerDto, targetClazz);
    }

    /**
     * 获取指定单字段的列表
     *
     * @param queryParamInnerDto 条件
     * @param targetClazz        目标类型
     * @return 指定的类型
     */
    <E> List<E> findSingleFieldLstByCondition(GXBaseQueryParamInnerDto queryParamInnerDto, Class<E> targetClazz);

    /**
     * 动态调用指定的指定Class中的方法
     *
     * @param mapperMethodMethod 需要调用的方法
     * @param convertMethodName  结果集转换函数名字
     * @param copyOptions        转换选项
     * @param params             参数
     * @return Collection
     */
    Collection<R> findByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params);

    /**
     * 动态调用指定的指定Class中的方法
     *
     * @param mapperMethodMethod 需要调用的方法
     * @param params             参数
     * @return Object
     */
    Collection<R> findByCallMapperMethod(String mapperMethodMethod, Object... params);

    /**
     * 动态调用指定的指定Class中的方法
     *
     * @param mapperMethodMethod 需要调用的方法
     * @param convertMethodName  结果集转换函数名字
     * @param copyOptions        转换选项
     * @param params             参数
     * @return Object
     */
    R findOneByCallMapperMethod(String mapperMethodMethod, String convertMethodName, CopyOptions copyOptions, Object... params);

    /**
     * 动态调用指定的指定Class中的方法
     *
     * @param mapperMethodName 需要调用的方法
     * @param params           参数
     * @return Object
     */
    R findOneByCallMapperMethod(String mapperMethodName, Object... params);

    /**
     * 根据条件统计数量
     *
     * @param conditions 查询条件
     * @return 查询到的数量
     */
    Long countByCondition(List<GXCondition<?>> conditions);

    /**
     * 根据条件统计数量
     *
     * @param queryParamInnerDto 查询条件
     * @return 查询到的数量
     */
    Long countByCondition(GXBaseQueryParamInnerDto queryParamInnerDto);

    /**
     * 获取 Primary Key
     *
     * @return String
     */
    String getPrimaryKeyName(T entity);

    /**
     * 获取表的名字
     *
     * @return String
     */
    String getTableName();

    /**
     * 获取MyBatis Plus数据表的信息
     *
     * @return TableInfo
     */
    TableInfo getTableInfo();

    /**
     * 从参数中获取 CopyOptions
     *
     * @param queryParamInnerDto 查询参数
     * @return CopyOptions
     */
    default CopyOptions getCopyOptions(GXBaseQueryParamInnerDto queryParamInnerDto) {
        return ObjectUtil.defaultIfNull(queryParamInnerDto.getCopyOptions(), GXCommonUtils::getDefaultCopyOptions);
    }

    /**
     * 获取分页列表中的count(*)数量
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return Long 满足条件的总数
     */
    default long getUnionPaginateCount(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        return 0L;
    }

    /**
     * 获取分页列表中的count(*)数量
     *
     * @param queryParamReqDto 参数
     * @return GXPaginationResDto
     */
    default long getPaginateCount(GXBaseQueryParamInnerDto queryParamReqDto) {
        return 0L;
    }
}
