package cn.maple.core.datasource.mapper;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.builder.GXBaseBuilder;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.model.GXBaseModel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 基础Mapper接口
 * <p>
 * 该接口继承自MyBatis-Plus的BaseMapper，提供了一系列扩展的数据库操作方法。
 * 所有方法都通过GXBaseBuilder构建SQL语句，确保SQL注入防护和参数化查询。
 * 支持条件查询、字段更新、分页查询、联合查询等高级功能。
 * </p>
 * <p>
 * 内存安全特性：
 * - 所有方法都使用参数化查询，防止SQL注入攻击
 * - 查询结果通过Dict对象返回，避免直接暴露实体结构
 * - 使用JacksonTypeHandler处理复杂类型的序列化和反序列化
 * </p>
 * <p>
 * 线程安全特性：
 * - 接口方法无状态，可安全地在多线程环境中调用
 * - 依赖MyBatis的会话管理，确保数据库连接的线程安全
 * </p>
 * 
 * @param <T> 实体类型，必须继承自GXBaseModel
 * @author britton
 * @since 1.0.0
 */
@Mapper
public interface GXBaseMapper<T extends GXBaseModel> extends BaseMapper<T> {
    /**
     * 根据条件更新指定字段
     * <p>
     * 该方法允许根据指定的查询条件更新表中的特定字段。
     * 内部通过GXBaseBuilder.updateFieldByCondition方法构建安全的SQL语句。
     * 使用参数化查询防止SQL注入攻击。
     * </p>
     * <p>
     * 内存安全：使用参数化查询和类型安全的参数传递
     * 线程安全：方法无状态，可在多线程环境中安全调用
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件参数，包含表名、条件等信息，不能为null
     * @param fieldList 需要更新的字段列表，每个字段都是GXUpdateField类型，不能为null或空列表
     * @return 更新影响的记录数，成功返回大于0的整数，失败返回0
     * @throws cn.maple.core.framework.exception.GXBusinessException 当条件为空时抛出
     */
    @UpdateProvider(type = GXBaseBuilder.class, method = "updateFieldByCondition")
    Integer updateFieldByCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> fieldList);

    /**
     * 检查指定条件的记录是否存在
     * <p>
     * 该方法用于验证数据库中是否存在满足指定条件的记录。
     * 内部通过GXBaseBuilder.checkRecordIsExists方法构建安全的SQL语句。
     * 查询结果为非null值表示记录存在，null值表示记录不存在。
     * </p>
     * <p>
     * 内存安全：使用参数化查询防止SQL注入
     * 线程安全：方法无状态，可在多线程环境中安全调用
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件参数，包含表名、条件等信息，不能为null
     * @return 存在返回非null整数值，不存在返回null
     */
    @SelectProvider(type = GXBaseBuilder.class, method = "checkRecordIsExists")
    Integer checkRecordIsExists(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    /**
     * 根据条件查询单条记录
     * <p>
     * 该方法根据指定的查询条件返回单条记录。
     * 内部通过GXBaseBuilder.findOneByCondition方法构建安全的SQL语句。
     * 结果通过Dict对象返回，提供灵活的数据访问方式。
     * </p>
     * <p>
     * 内存安全：
     * - 使用参数化查询防止SQL注入
     * - 使用JacksonTypeHandler安全处理ext字段的JSON序列化/反序列化
     * </p>
     * <p>
     * 线程安全：方法无状态，可在多线程环境中安全调用
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件参数，包含表名、条件、字段等信息，不能为null
     * @return 查询结果的Dict对象，未找到时返回null
     */
    @SelectProvider(type = GXBaseBuilder.class, method = "findOneByCondition")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    Dict findOneByCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    /**
     * 根据条件查询多条记录
     * <p>
     * 该方法根据指定的查询条件返回多条记录。
     * 内部通过GXBaseBuilder.findByCondition方法构建安全的SQL语句。
     * 结果通过Dict对象列表返回，提供灵活的数据访问方式。
     * </p>
     * <p>
     * 内存安全：
     * - 使用参数化查询防止SQL注入
     * - 使用JacksonTypeHandler安全处理ext字段的JSON序列化/反序列化
     * - 返回不可变集合，防止外部修改
     * </p>
     * <p>
     * 线程安全：方法无状态，可在多线程环境中安全调用
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件参数，包含表名、条件、字段等信息，不能为null
     * @return 查询结果的Dict对象列表，未找到时返回空列表
     */
    @SelectProvider(type = GXBaseBuilder.class, method = "findByCondition")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    List<Dict> findByCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    /**
     * 分页查询记录
     * <p>
     * 该方法提供分页查询功能，根据指定的查询条件和分页参数返回当前页的记录。
     * 内部通过GXBaseBuilder.paginate方法构建安全的SQL语句。
     * 结果通过Dict对象列表返回，提供灵活的数据访问方式。
     * </p>
     * <p>
     * 内存安全：
     * - 使用参数化查询防止SQL注入
     * - 使用JacksonTypeHandler安全处理ext字段的JSON序列化/反序列化
     * - 分页机制避免大量数据加载到内存，防止内存溢出
     * </p>
     * <p>
     * 线程安全：方法无状态，可在多线程环境中安全调用
     * </p>
     *
     * @param page 分页对象，包含页码、每页记录数等信息，不能为null
     * @param dbQueryParamInnerDto 查询条件参数，包含表名、条件、字段等信息，不能为null
     * @return 当前页的Dict对象列表，未找到时返回空列表
     */
    @SelectProvider(type = GXBaseBuilder.class, method = "paginate")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    List<Dict> paginate(IPage<Dict> page, @Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    /**
     * 根据条件软删除记录
     * <p>
     * 该方法执行逻辑删除，将记录标记为已删除而不是物理删除。
     * 内部通过GXBaseBuilder.deleteSoftCondition方法构建安全的SQL语句。
     * 软删除通常会更新is_deleted字段为1，并设置deleted_at时间戳。
     * </p>
     * <p>
     * 内存安全：
     * - 使用参数化查询防止SQL注入
     * - 验证条件非空，防止误操作导致全表更新
     * </p>
     * <p>
     * 线程安全：方法无状态，可在多线程环境中安全调用
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件参数，包含表名、条件等信息，不能为null
     * @param updateFieldList 软删除时需要同时更新的其他字段，可以为null
     * @return 删除影响的记录数，成功返回大于0的整数，失败返回0
     * @throws cn.maple.core.framework.exception.GXBusinessException 当条件为空时抛出
     */
    @UpdateProvider(type = GXBaseBuilder.class, method = "deleteSoftCondition")
    Integer deleteSoftCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXUpdateField<?>> updateFieldList);

    /**
     * 根据条件物理删除记录
     * <p>
     * 该方法执行物理删除，将记录从数据库中永久移除。
     * 内部通过GXBaseBuilder.deleteCondition方法构建安全的SQL语句。
     * 物理删除操作不可恢复，应谨慎使用。
     * </p>
     * <p>
     * 内存安全：
     * - 使用参数化查询防止SQL注入
     * - 验证条件非空，防止误操作导致全表删除
     * </p>
     * <p>
     * 线程安全：方法无状态，可在多线程环境中安全调用
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件参数，包含表名、条件等信息，不能为null
     * @return 删除影响的记录数，成功返回大于0的整数，失败返回0
     * @throws cn.maple.core.framework.exception.GXBusinessException 当条件为空时抛出
     */
    @DeleteProvider(type = GXBaseBuilder.class, method = "deleteCondition")
    Integer deleteCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto);

    /**
     * 联合查询单条记录
     * <p>
     * 该方法支持UNION查询，可以将多个查询结果合并后返回单条记录。
     * 内部通过GXBaseBuilder.unionFindOneByCondition方法构建安全的SQL语句。
     * 结果通过Dict对象返回，提供灵活的数据访问方式。
     * </p>
     * <p>
     * 内存安全：
     * - 使用参数化查询防止SQL注入
     * - 使用JacksonTypeHandler安全处理ext字段的JSON序列化/反序列化
     * </p>
     * <p>
     * 线程安全：方法无状态，可在多线程环境中安全调用
     * </p>
     *
     * @param dbQueryParamInnerDto 主查询条件参数，包含表名、条件、字段等信息，不能为null
     * @param unionQueryParamInnerDtoLst 联合查询条件列表，包含多个查询条件，不能为null
     * @param unionTypeEnums 联合查询类型，UNION或UNION ALL，不能为null
     * @return 查询结果的Dict对象，未找到时返回null
     */
    @SelectProvider(type = GXBaseBuilder.class, method = "unionFindOneByCondition")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    Dict unionFindOneByCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    /**
     * 联合查询多条记录
     * <p>
     * 该方法支持UNION查询，可以将多个查询结果合并后返回多条记录。
     * 内部通过GXBaseBuilder.unionFindByCondition方法构建安全的SQL语句。
     * 结果通过Dict对象列表返回，提供灵活的数据访问方式。
     * </p>
     * <p>
     * 内存安全：
     * - 使用参数化查询防止SQL注入
     * - 使用JacksonTypeHandler安全处理ext字段的JSON序列化/反序列化
     * - 返回不可变集合，防止外部修改
     * </p>
     * <p>
     * 线程安全：方法无状态，可在多线程环境中安全调用
     * </p>
     *
     * @param dbQueryParamInnerDto 主查询条件参数，包含表名、条件、字段等信息，不能为null
     * @param unionQueryParamInnerDtoLst 联合查询条件列表，包含多个查询条件，不能为null
     * @param unionTypeEnums 联合查询类型，UNION或UNION ALL，不能为null
     * @return 查询结果的Dict对象列表，未找到时返回空列表
     */
    @SelectProvider(type = GXBaseBuilder.class, method = "unionFindByCondition")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    List<Dict> unionFindByCondition(@Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);

    /**
     * 联合查询分页记录
     * <p>
     * 该方法支持UNION查询的分页操作，可以将多个查询结果合并后进行分页。
     * 内部通过GXBaseBuilder.unionPaginate方法构建安全的SQL语句。
     * 结果通过Dict对象列表返回，提供灵活的数据访问方式。
     * </p>
     * <p>
     * 内存安全：
     * - 使用参数化查询防止SQL注入
     * - 使用JacksonTypeHandler安全处理ext字段的JSON序列化/反序列化
     * - 分页机制避免大量数据加载到内存，防止内存溢出
     * </p>
     * <p>
     * 线程安全：方法无状态，可在多线程环境中安全调用
     * </p>
     *
     * @param page 分页对象，包含页码、每页记录数等信息，不能为null
     * @param dbQueryParamInnerDto 主查询条件参数，包含表名、条件、字段等信息，不能为null
     * @param unionQueryParamInnerDtoLst 联合查询条件列表，包含多个查询条件，不能为null
     * @param unionTypeEnums 联合查询类型，UNION或UNION ALL，不能为null
     * @return 当前页的Dict对象列表，未找到时返回空列表
     */
    @SelectProvider(type = GXBaseBuilder.class, method = "unionPaginate")
    @Results(@Result(property = "ext", column = "ext", typeHandler = JacksonTypeHandler.class))
    List<Dict> unionPaginate(IPage<Dict> page, @Param("dbQueryParamInnerDto") GXBaseQueryParamInnerDto dbQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums);
}
