package cn.maple.core.framework.ddd.repository;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;

import jakarta.validation.ConstraintValidatorContext;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 领域驱动设计(DDD)中的仓储基础接口
 * <p>
 * 该接口定义了仓储层的基本操作，负责领域对象的持久化和查询。实现类需要确保线程安全和事务一致性。
 * 在多线程环境下，实现类应当注意：
 * 1. 所有方法的实现应当是线程安全的
 * 2. 避免在方法实现中使用静态或实例级别的可变状态
 * 3. 合理使用数据库事务确保数据一致性
 * </p>
 * 
 * <p>使用示例:</p>
 * <pre>
 * // 创建条件查询
 * List&lt;GXCondition&lt;?&gt;&gt; conditions = new ArrayList&lt;&gt;();
 * conditions.add(new GXConditionEQ("user", "status", 1));
 * 
 * // 查询单个实体
 * Dict userInfo = userRepository.findOneByCondition("user", conditions);
 * 
 * // 创建或更新实体
 * User user = new User();
 * user.setUsername("maple");
 * user.setEmail("maple@example.com");
 * ID userId = userRepository.updateOrCreate(user);
 * 
 * // 分页查询
 * GXPaginationResDto&lt;Dict&gt; pagination = userRepository.paginate(
 *     "user", 1, 10, conditions, Set.of("id", "username", "email")
 * );
 * </pre>
 * 
 * @param <T> 领域实体类型
 * @param <ID> 实体标识类型，必须实现Serializable接口
 */
public interface GXBaseRepository<T, ID extends Serializable> {
    /**
     * 保存或者更新数据
     * <p>
     * 该方法根据实体对象的状态决定是创建新记录还是更新已有记录。
     * 实现类应确保在并发环境下的数据一致性，建议在事务中执行此操作。
     * </p>
     *
     * @param entity    需要更新或者保存的数据，不能为null
     * @param condition 附加条件，用于一些特殊场景，如乐观锁等
     * @return ID 返回实体的ID
     * @throws IllegalArgumentException 当entity为null时抛出
     */
    ID updateOrCreate(T entity, List<GXCondition<?>> condition);

    /**
     * 创建或者更新数据（简化版）
     * <p>
     * 不带条件的保存或更新操作，内部调用带条件的方法并传入空条件列表。
     * 适用于简单的保存场景。
     * </p>
     *
     * @param entity 数据实体，不能为null
     * @return ID 返回实体的ID
     * @throws IllegalArgumentException 当entity为null时抛出
     */
    ID updateOrCreate(T entity);

    /**
     * 根据条件获取所有数据
     * <p>
     * 根据查询参数获取多条记录。此方法适用于列表查询场景。
     * 实现类应当注意内存使用，避免一次性加载过多数据导致内存溢出。
     * 对于大数据量查询，建议使用分页方法。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件、排序等信息，不能为null
     * @return List<Dict> 返回匹配的记录列表，如果没有匹配记录则返回空列表
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    /**
     * 根据UNION条件获取所有数据
     * <p>
     * 使用UNION查询获取多条记录。此方法支持复杂的联合查询场景。
     * 实现类应确保生成的SQL语句高效且安全，并注意结果集大小控制。
     * 默认实现抛出异常，子类需要根据具体数据库实现此方法。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表
     * @param unionTypeEnums             union的类型（UNION或UNION ALL）
     * @return List<Dict> 返回匹配的记录列表
     * @throws GXBusinessException 当未实现此方法时抛出
     */
    default List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("请实现findByCondition方法");
    }

    /**
     * 根据条件获取所有数据
     * <p>
     * 根据表名和条件获取多条记录。此方法是基础查询方法。
     * 实现类应确保查询性能和内存使用的平衡。
     * </p>
     *
     * @param tableName 表名字，不能为空
     * @param condition 条件列表，用于筛选记录
     * @return List<Dict> 返回匹配的记录列表，如果没有匹配记录则返回空列表
     * @throws IllegalArgumentException 当tableName为空时抛出
     */
    List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition);

    /**
     * 根据条件获取所有数据（指定列）
     * <p>
     * 根据表名、条件和指定列获取多条记录。此方法允许精确控制返回的字段。
     * 通过只查询需要的列，可以优化查询性能和减少内存使用。
     * </p>
     *
     * @param tableName 表名字，不能为空
     * @param condition 条件列表，用于筛选记录
     * @param columns   需要查询的列名集合，如果为空则查询所有列
     * @return List<Dict> 返回匹配的记录列表，如果没有匹配记录则返回空列表
     * @throws IllegalArgumentException 当tableName为空时抛出
     */
    List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns);

    /**
     * 根据条件获取所有数据
     *
     * @param condition 条件
     * @return 列表
     */
    default List<Dict> findByCondition(List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        return findByCondition(getTableName(), condition);
    }

    /**
     * 获取所有数据
     *
     * @return 列表
     */
    default List<Dict> findByCondition() {
        return findByCondition(getTableName(), Collections.emptyList());
    }

    /**
     * 根据条件获取单条数据
     * <p>
     * 根据查询参数获取单条记录。当有多条记录匹配条件时，实现类应当返回第一条记录。
     * 此方法适用于需要获取详细信息的场景。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询参数，包含表名、条件、字段等信息，不能为null
     * @return Dict 返回匹配的记录，如果没有匹配记录则返回空Dict
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    /**
     * 根据UNION条件获取单条数据
     * <p>
     * 使用UNION查询获取单条记录。此方法支持复杂的联合查询场景。
     * 默认实现抛出异常，子类需要根据具体数据库实现此方法。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表
     * @param unionTypeEnums             union的类型（UNION或UNION ALL）
     * @return Dict 返回匹配的记录，如果没有匹配记录则返回空Dict
     * @throws GXBusinessException 当未实现此方法时抛出
     */
    default Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("请实现findOneByCondition方法");
    }

    /**
     * 根据条件获取数据
     *
     * @param tableName 表名字
     * @param condition 查询条件
     * @return R 返回数据
     */
    Dict findOneByCondition(String tableName, List<GXCondition<?>> condition);

    /**
     * 根据条件获取数据
     *
     * @param condition 查询条件
     * @return R 返回数据
     */
    default Dict findOneByCondition(List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        return findOneByCondition(getTableName(), condition);
    }

    /**
     * 根据条件获取数据
     *
     * @param tableName 表名字
     * @param condition 查询条件
     * @param columns   需要查询的列
     * @return R 返回数据
     */
    Dict findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns);

    /**
     * 通过ID获取一条记录
     *
     * @param tableName 表名字
     * @param id        ID值
     * @param columns   需要返回的列
     * @return 返回数据
     */
    Dict findOneById(String tableName, ID id, Set<String> columns);

    /**
     * 通过ID获取一条记录
     * <p>
     * 根据ID从数据库中查询单条记录，返回所有字段。
     * 此方法是根据主键查询的便捷方法。
     * </p>
     *
     * @param tableName 表名字，不能为空
     * @param id        ID值，实体的唯一标识
     * @return Dict 返回查询到的数据，如果没有找到则返回空Dict
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Dict findOneById(String tableName, ID id);
    
    /**
     * 通过ID获取一条记录（使用默认表名）
     * <p>
     * 使用实体默认表名根据ID查询单条记录。
     * 此方法是findOneById的便捷版本，自动使用getTableName()获取表名。
     * </p>
     *
     * @param id ID值，实体的唯一标识
     * @return Dict 返回查询到的数据，如果没有找到则返回空Dict
     * @throws IllegalArgumentException 当id为null时抛出
     */
    default Dict findOneById(ID id) {
        Assert.notNull(id, "ID不能为null");
        return findOneById(getTableName(), id);
    }

    /**
     * 根据条件获取分页数据
     * <p>
     * 根据查询参数进行分页查询。此方法适用于需要分页展示数据的场景。
     * 实现类应优化查询性能，特别是在大数据量情况下的count查询。
     * </p>
     *
     * @param dbQueryParamInnerDto 条件查询，包含表名、条件、分页参数等信息，不能为null
     * @return GXPaginationResDto<Dict> 分页结果，包含当前页数据和分页信息
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    /**
     * 根据UNION条件获取分页数据
     * <p>
     * 使用UNION查询进行分页。此方法支持复杂的联合查询分页场景。
     * 默认实现抛出异常，子类需要根据具体数据库实现此方法。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表
     * @param unionTypeEnums             union的类型（UNION或UNION ALL）
     * @return GXPaginationResDto<Dict> 分页结果，包含当前页数据和分页信息
     * @throws GXBusinessException 当未实现此方法时抛出
     */
    default GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        throw new GXBusinessException("请实现paginate方法");
    }

    /**
     * 根据条件获取分页数据（基础版）
     * <p>
     * 根据表名、分页参数、条件和指定列进行分页查询。
     * 此方法是分页查询的基础实现，提供了最直接的分页功能。
     * </p>
     *
     * @param tableName 表名字，不能为空
     * @param page      当前页，从1开始
     * @param pageSize  每页大小，必须大于0
     * @param condition 查询条件，用于筛选记录
     * @param columns   需要的数据列集合，如果为空则查询所有列
     * @return GXPaginationResDto<Dict> 分页对象，包含当前页数据和分页信息
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    GXPaginationResDto<Dict> paginate(String tableName, Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns);

    /**
     * 根据条件软(逻辑)删除
     * <p>
     * 执行软删除操作。软删除通常是将状态字段标记为删除状态，而非物理删除数据。
     * 实现类应在事务中执行此操作，确保数据一致性。
     * </p>
     *
     * @param tableName 表名，不能为空
     * @param condition 删除条件，用于定位需要删除的记录
     * @param extraData 额外数据，可用于记录删除原因、操作人等信息
     * @return Integer 影响行数
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData);

    /**
     * 根据条件软(逻辑)删除并更新其他字段
     * <p>
     * 执行软删除操作，同时更新指定的其他字段。软删除通常是将状态字段标记为删除状态，而非物理删除数据。
     * 实现类应在事务中执行此操作，确保数据一致性。
     * </p>
     *
     * @param tableName       表名，不能为空
     * @param updateFieldList 软删除时需要同时更新的字段列表
     * @param condition       删除条件，用于定位需要删除的记录
     * @param extraData       额外数据，可用于记录删除原因、操作人等信息
     * @return Integer 影响行数
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData);

    /**
     * 根据条件物理删除数据
     * <p>
     * 执行物理删除操作，从数据库中永久移除匹配条件的记录。
     * 此操作不可逆，实现类应谨慎处理，建议在事务中执行并考虑添加日志记录。
     * </p>
     *
     * @param tableName 表名，不能为空
     * @param condition 删除条件，用于定位需要删除的记录
     * @return Integer 影响行数
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Integer deleteCondition(String tableName, List<GXCondition<?>> condition);

    /**
     * 检测数据是否存在
     * <p>
     * 根据条件检查数据库中是否存在匹配的记录。此方法通常用于数据验证。
     * 实现类应优化查询性能，例如使用EXISTS语句而非完整查询。
     * </p>
     *
     * @param tableName 表名字，不能为空
     * @param condition 查询条件，用于定位记录
     * @return boolean 存在返回true，不存在返回false
     * @throws IllegalArgumentException 当tableName为空时抛出
     */
    boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition);

    /**
     * 实现验证注解(返回true表示数据已经存在)
     * <p>
     * 用于Bean Validation框架的存在性验证。此方法通常被验证注解调用。
     * 实现类应确保验证逻辑的正确性和性能。
     * </p>
     *
     * @param validateExistsDto          业务验证DTO参数，包含验证所需的表名、字段和值等信息
     * @param constraintValidatorContext 验证上下文，可用于自定义验证消息
     * @return boolean 数据存在返回true，不存在返回false
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext constraintValidatorContext);

    /**
     * 通过条件更新数据
     * <p>
     * 根据指定条件更新表中的数据字段。此方法适用于部分字段更新的场景。
     * 实现类应确保在高并发环境下的数据一致性，推荐使用乐观锁机制。
     * </p>
     *
     * @param tableName    需要更新的表名，不能为空
     * @param updateFields 需要更新的数据字段列表，不能为空
     * @param condition    更新条件，用于定位需要更新的记录
     * @return Integer 影响的行数
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition);

    /**
     * 获取实体的主键名称
     * <p>
     * 根据实体对象获取其对应表的主键字段名。
     * 实现类通常基于实体类的注解或命名约定确定主键名。
     * </p>
     *
     * @param entity 实体对象，不能为null
     * @return String 主键字段名
     * @throws IllegalArgumentException 当entity为null时抛出
     */
    String getPrimaryKeyName(T entity);

    /**
     * 获取实体的主键名称（无参版）
     * <p>
     * 获取当前仓储操作的实体表的主键字段名。
     * 实现类通常基于泛型类型的注解或命名约定确定主键名。
     * </p>
     *
     * @return String 主键字段名
     */
    String getPrimaryKeyName();

    /**
     * 获取实体对应的表名
     * <p>
     * 根据实体对象获取其对应的数据库表名。
     * 实现类通常基于实体类的注解或命名约定确定表名。
     * </p>
     *
     * @param entity 实体对象，不能为null
     * @return String 实体对应的数据库表名
     * @throws IllegalArgumentException 当entity为null时抛出
     */
    String getTableName(T entity);

    /**
     * 获取实体对应的表名（无参版）
     * <p>
     * 获取当前仓储操作的实体表名。
     * 实现类通常基于泛型类型的注解或命名约定确定表名。
     * </p>
     *
     * @return String 数据库表名
     */
    String getTableName();
}
