package cn.maple.core.framework.dao;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.GXBaseData;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;

import java.io.Serializable;
import java.util.List;

/**
 * 基础数据访问对象接口
 * <p>
 * 该接口定义了数据访问层的基本操作，支持各种数据库操作，包括增删改查、分页查询等功能。
 * 实现类需要确保线程安全，特别是在并发环境下的数据一致性。
 * </p>
 * 
 * <p>使用示例:</p>
 * <pre>
 * // 创建条件
 * List&lt;GXCondition&lt;?&gt;&gt; conditions = new ArrayList&lt;&gt;();
 * conditions.add(new GXConditionEQ("user", "status", 1));
 * 
 * // 查询单条记录
 * Dict userInfo = userDao.findOneByCondition("user", conditions);
 * 
 * // 分页查询
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("user")
 *     .condition(conditions)
 *     .page(1)
 *     .pageSize(10)
 *     .build();
 * GXPaginationResDto&lt;Dict&gt; pagination = userDao.paginate(queryParam);
 * </pre>
 * 
 * @param <T> 数据实体类型，必须继承自GXBaseData
 * @param <ID> 主键类型，必须实现Serializable接口
 */
public interface GXBaseDao<T extends GXBaseData, ID extends Serializable> {
    /**
     * 保存或更新数据
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
     * 通过条件更新表中的数据
     * <p>
     * 根据指定条件更新表中的数据字段。此方法适用于部分字段更新的场景。
     * 实现类应确保在高并发环境下的数据一致性，推荐使用乐观锁机制。
     * </p>
     *
     * @param tableName 表名字，不能为空
     * @param data      需要更新的数据字段列表，不能为空
     * @param condition 更新条件，用于定位需要更新的记录
     * @return 影响的行数
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> data, List<GXCondition<?>> condition);

    /**
     * 检测给定条件的记录是否存在
     * <p>
     * 根据条件检查数据库中是否存在匹配的记录。此方法通常用于数据验证。
     * 实现类应优化查询性能，例如使用EXISTS语句而非完整查询。
     * </p>
     *
     * @param tableName 数据库表名字，不能为空
     * @param condition 查询条件，用于定位记录
     * @return boolean 存在返回true，不存在返回false
     * @throws IllegalArgumentException 当tableName为空时抛出
     */
    boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition);

    /**
     * 通过条件获取单条数据
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
     * 通过UNION条件获取单条数据
     * <p>
     * 使用UNION查询获取单条记录。此方法支持复杂的联合查询场景。
     * 实现类应确保生成的SQL语句高效且安全，防止SQL注入。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表
     * @param unionTypeEnums             union的类型（UNION或UNION ALL）
     * @return Dict 返回匹配的记录，如果没有匹配记录则返回空Dict
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    /**
     * 通过条件获取数据列表
     * <p>
     * 根据查询参数获取多条记录。此方法适用于列表查询场景。
     * 实现类应当注意内存使用，避免一次性加载过多数据导致内存溢出。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件、排序等信息，不能为null
     * @return List<Dict> 返回匹配的记录列表，如果没有匹配记录则返回空列表
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    /**
     * 通过UNION条件获取数据列表
     * <p>
     * 使用UNION查询获取多条记录。此方法支持复杂的联合查询场景。
     * 实现类应确保生成的SQL语句高效且安全，并注意结果集大小控制。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表
     * @param unionTypeEnums             union的类型（UNION或UNION ALL）
     * @return List<Dict> 返回匹配的记录列表，如果没有匹配记录则返回空列表
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

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
     * @return 影响行数
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData);

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
     * @return 影响行数
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData);

    /**
     * 根据条件物理删除数据
     * <p>
     * 执行物理删除操作，从数据库中永久移除匹配条件的记录。
     * 此操作不可逆，实现类应谨慎处理，建议在事务中执行并考虑添加日志记录。
     * </p>
     *
     * @param tableName 表名，不能为空
     * @param condition 删除条件，用于定位需要删除的记录
     * @return 影响行数
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    Integer deleteCondition(String tableName, List<GXCondition<?>> condition);

    /**
     * 分页查询数据
     * <p>
     * 根据查询参数进行分页查询。此方法适用于需要分页展示数据的场景。
     * 实现类应优化查询性能，特别是在大数据量情况下的count查询。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件、分页参数等信息，不能为null
     * @return GXPaginationResDto<Dict> 分页结果，包含当前页数据和分页信息
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    /**
     * 使用UNION条件进行分页查询
     * <p>
     * 使用UNION查询进行分页。此方法支持复杂的联合查询分页场景。
     * 实现类应确保生成的SQL语句高效且安全，并优化分页性能。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表
     * @param unionTypeEnums             union的类型（UNION或UNION ALL）
     * @return GXPaginationResDto<Dict> 分页结果，包含当前页数据和分页信息
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    /**
     * 获取实体对应的表名
     * <p>
     * 返回当前DAO操作的主表名。实现类通常基于实体类的注解或命名约定确定表名。
     * </p>
     *
     * @return String 数据库表的名字
     */
    String getTableName();
}
