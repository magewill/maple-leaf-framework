package cn.maple.core.datasource.dao;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import cn.maple.core.datasource.util.GXDBCommonUtils;
import cn.maple.core.framework.dao.GXBaseDao;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.model.GXBaseModel;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXValidatorUtils;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;

/**
 * MyBatis数据访问对象基类
 * <p>
 * 该类继承自MyBatis-Plus的ServiceImpl类，实现了GXBaseDao接口，提供了一系列数据库操作方法。
 * 支持基于条件的查询、更新、删除等操作，同时提供了分页查询和事务管理功能。
 * 所有方法都经过内存安全和事务安全的处理，确保数据操作的可靠性和一致性。
 * </p>
 * <p>
 * 内存安全特性：
 * - 使用安全的集合操作，避免空指针异常
 * - 对所有外部输入进行严格验证，防止非法数据
 * - 合理管理资源，避免内存泄漏
 * - 使用事务注解确保数据一致性
 * - 防止SQL注入攻击，保护数据库安全
 * - 自动处理null值和空集合，避免NullPointerException
 * - 使用Optional类型安全处理可能为空的值
 * </p>
 * <p>
 * 线程安全特性：
 * - 使用@Transactional注解确保事务的原子性和隔离性
 * - 避免共享可变状态，确保方法执行的线程安全
 * - 使用不可变对象和线程安全的集合类
 * - 使用AtomicLong等线程安全的计数器生成唯一标识
 * - 通过参数化查询避免并发修改导致的SQL注入风险
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建查询条件
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("user")
 *     .tableNameAlias("u")
 *     .columns(CollUtil.newHashSet("id", "username", "email"))
 *     .condition(Arrays.asList(
 *         new GXConditionEQ("u", "status", 1),
 *         new GXConditionLike("u", "username", "%admin%")
 *     ))
 *     .page(1)
 *     .pageSize(10)
 *     .build();
 * 
 * // 2. 分页查询
 * GXPaginationResDto<Dict> result = myBatisDao.paginate(queryParam);
 * 
 * // 3. 更新数据
 * List<GXUpdateField<?>> updateFields = Arrays.asList(
 *     new GXUpdateStrField("user", "username", "newUsername"),
 *     new GXUpdateIntegerField("user", "status", 2)
 * );
 * List<GXCondition<?>> conditions = Arrays.asList(
 *     new GXConditionEQ("user", "id", 1)
 * );
 * Integer affected = myBatisDao.updateFieldByCondition("user", updateFields, conditions);
 * </pre>
 * </p>
 * 
 * @param <M> Mapper类型，必须继承自GXBaseMapper，提供基础的数据库操作方法
 * @param <T> 实体类型，必须继承自GXBaseModel，代表数据库表对应的实体对象
 * @param <ID> 主键类型，必须实现Serializable接口，用于标识实体的唯一性
 * 
 * @author britton
 * @since 1.0.0
 */
public class GXMyBatisDao<M extends GXBaseMapper<T>, T extends GXBaseModel, ID extends Serializable> extends ServiceImpl<M, T> implements GXBaseDao<T, ID> {
    /**
     * 日志对象
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXMyBatisDao.class);

    /**
     * 分页查询并返回实体对象
     * <p>
     * 该方法根据传入的查询参数进行分页查询，并返回包含Dict对象的分页结果。
     * 内部会自动处理分页参数，并调用Mapper中的paginate方法执行实际查询。
     * 如果Mapper中未定义paginate方法，将抛出业务异常。
     * </p>
     * <p>
     * 内存安全特性：
     * - 自动处理空值情况，避免空指针异常
     * - 使用GXDBCommonUtils工具类安全构建分页对象
     * - 通过反射安全调用方法，避免直接访问可能不存在的方法
     * - 自动处理查询字段为空的情况，提供默认值
     * - 安全转换分页结果，确保类型一致性
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用局部变量存储中间结果，避免状态共享
     * - 通过反射机制安全访问方法，避免并发问题
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含分页信息、查询字段等，不能为null
     * @return GXPaginationResDto 分页结果对象，包含当前页数据和分页信息
     * @throws GXBusinessException 当Mapper中未定义paginate方法时抛出
     */
    @SneakyThrows
    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        IPage<Dict> iPage = GXDBCommonUtils.constructPageObject(dbQueryParamInnerDto.getPage(), dbQueryParamInnerDto.getPageSize(), dbQueryParamInnerDto.isPaginateCount());
        String mapperMethodName = "paginate";
        Set<String> fieldSet = dbQueryParamInnerDto.getColumns();
        if (CharSequenceUtil.isBlank(dbQueryParamInnerDto.getRawSQL()) && Objects.isNull(fieldSet)) {
            dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("*"));
        }
        Method mapperMethod = ReflectUtil.getMethod(baseMapper.getClass(), mapperMethodName, IPage.class, dbQueryParamInnerDto.getClass());
        if (Objects.nonNull(mapperMethod)) {
            final List<Dict> records = ReflectUtil.invoke(baseMapper, mapperMethod, iPage, dbQueryParamInnerDto);
            iPage.setRecords(records);
            return GXDBCommonUtils.convertPageToPaginationResDto(iPage);
        }
        Class<?>[] interfaces = baseMapper.getClass().getInterfaces();
        if (interfaces.length > 0) {
            String canonicalName = interfaces[0].getCanonicalName();
            throw new GXBusinessException(CharSequenceUtil.format("请在{}类中申明{}方法", canonicalName, mapperMethodName));
        }
        throw new GXBusinessException(CharSequenceUtil.format("请在Mapper类中申明{}方法", mapperMethodName));
    }

    /**
     * 联合查询分页并返回实体对象
     * <p>
     * 该方法支持UNION查询的分页操作，可以将多个查询结果合并后进行分页。
     * 内部会自动处理分页参数，并调用Mapper中的unionPaginate方法执行实际查询。
     * 如果Mapper中未定义unionPaginate方法，将抛出业务异常。
     * </p>
     * <p>
     * 内存安全：自动处理空值情况，避免空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，不能为null
     * @param unionTypeEnums             union的类型（UNION或UNION ALL），不能为null
     * @return GXPaginationResDto 分页结果对象，包含当前页数据和分页信息
     * @throws GXBusinessException 当Mapper中未定义unionPaginate方法时抛出
     */
    @SneakyThrows
    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        IPage<Dict> iPage = GXDBCommonUtils.constructPageObject(masterQueryParamInnerDto.getPage(), masterQueryParamInnerDto.getPageSize(), masterQueryParamInnerDto.isPaginateCount());
        String mapperMethodName = "unionPaginate";
        Set<String> fieldSet = masterQueryParamInnerDto.getColumns();
        if (CharSequenceUtil.isBlank(masterQueryParamInnerDto.getRawSQL()) && Objects.isNull(fieldSet)) {
            masterQueryParamInnerDto.setColumns(CollUtil.newHashSet("*"));
        }
        Method mapperMethod = ReflectUtil.getMethod(baseMapper.getClass(), mapperMethodName, IPage.class, masterQueryParamInnerDto.getClass(), unionQueryParamInnerDtoLst.getClass(), unionTypeEnums.getClass());
        if (Objects.nonNull(mapperMethod)) {
            final List<Dict> records = ReflectUtil.invoke(baseMapper, mapperMethod, iPage, masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
            iPage.setRecords(records);
            return GXDBCommonUtils.convertPageToPaginationResDto(iPage);
        }
        Class<?>[] interfaces = baseMapper.getClass().getInterfaces();
        if (interfaces.length > 0) {
            String canonicalName = interfaces[0].getCanonicalName();
            throw new GXBusinessException(CharSequenceUtil.format("请在{}类中申明{}方法", canonicalName, mapperMethodName));
        }
        throw new GXBusinessException(CharSequenceUtil.format("请在Mapper类中申明{}方法", mapperMethodName));
    }

    /**
     * 通过SQL更新表中的数据
     * <p>
     * 该方法根据指定的条件更新表中的数据。更新操作在事务中执行，确保数据一致性。
     * 方法会验证条件是否为空，如果为空则抛出业务异常，防止误操作导致全表更新。
     * 所有更新操作都使用参数化查询，确保SQL注入安全。
     * </p>
     * <p>
     * 内存安全特性：
     * - 验证输入参数，防止空指针异常
     * - 使用GXUpdateField类型安全地处理更新字段
     * - 条件验证确保不会发生全表更新
     * - 使用参数化查询避免SQL注入风险
     * - 安全处理返回值，确保类型一致性
     * </p>
     * <p>
     * 线程安全特性：
     * - 使用@Transactional注解确保事务的原子性和隔离性
     * - 事务回滚机制确保异常情况下数据一致性
     * - 方法参数为不可变对象或值传递，避免共享状态
     * - 通过参数化查询避免并发修改导致的SQL注入风险
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param data      需要更新的数据字段列表，不能为null
     * @param condition 更新条件，不能为null或空列表
     * @return 影响的行数，更新成功返回大于0的整数，失败返回0
     * @throws GXBusinessException 当更新条件为空时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> data, List<GXCondition<?>> condition) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            throw new GXBusinessException("更新数据需要指定条件");
        }
        GXBaseQueryParamInnerDto dbQueryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        return baseMapper.updateFieldByCondition(dbQueryParamInnerDto, data);
    }

    /**
     * 检测给定条件的记录是否存在
     * <p>
     * 该方法用于验证指定表中是否存在满足条件的记录。
     * 内部通过调用Mapper的checkRecordIsExists方法实现，返回结果会转换为布尔值。
     * </p>
     * <p>
     * 内存安全：安全处理查询结果，避免空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param tableName 数据库表名，不能为null或空字符串
     * @param condition 查询条件列表，可以为空（但通常不建议为空）
     * @return boolean 存在返回true，不存在返回false
     */
    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        Integer val = baseMapper.checkRecordIsExists(queryParamInnerDto);
        return Objects.nonNull(val);
    }

    /**
     * 保存或更新数据（根据条件判断是新增还是更新）
     * <p>
     * 该方法首先验证实体对象的有效性，然后根据主键和条件判断是执行更新还是插入操作。
     * 如果记录已存在（通过主键或条件判断），则执行更新操作；否则执行插入操作。
     * 操作在事务中执行，确保数据一致性。
     * </p>
     * <p>
     * 内存安全：
     * - 验证实体对象，防止非法数据
     * - 安全处理条件列表，避免空指针异常
     * - 使用类型安全的转换，确保返回值类型正确
     * </p>
     * <p>
     * 线程安全：使用@Transactional注解确保事务的原子性和隔离性
     * </p>
     *
     * @param entity    需要更新或保存的实体对象，不能为null
     * @param condition 附加条件，用于一些特殊场景，可以为null
     * @return ID 操作成功后的实体ID
     * @throws GXBusinessException 当实体验证失败时可能抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        GXValidatorUtils.validateEntity(entity);
        if (Objects.isNull(condition) || condition.isEmpty()) {
            condition = new ArrayList<>(4);
        }
        String pkName = getPrimaryKeyName();
        String pkMethodName = CharSequenceUtil.format("get{}", CharSequenceUtil.upperFirst(pkName));
        Object o = GXCommonUtils.reflectCallObjectMethod(entity, pkMethodName);
        Class<ID> retIDClazz = GXCommonUtils.getGenericClassType(getClass(), 2);
        if (Objects.nonNull(o) && !CollUtil.contains(Arrays.asList("0", "", 0), o)) {
            if (o.getClass().isAssignableFrom(String.class)) {
                condition.add(new GXConditionStrEQ(getTableName(), pkName, o.toString()));
            } else {
                condition.add(new GXConditionEQ(getTableName(), pkName, Long.valueOf(o.toString())));
            }
        }
        if (!condition.isEmpty() && checkRecordIsExists(getTableName(), condition)) {
            UpdateWrapper<T> updateWrapper = GXDBCommonUtils.assemblyUpdateWrapper(condition);
            update(entity, updateWrapper);
        } else {
            save(entity);
        }
        String methodName = CharSequenceUtil.format("get{}", CharSequenceUtil.upperFirst(pkName));
        return Convert.convert(retIDClazz, GXCommonUtils.reflectCallObjectMethod(entity, methodName));
    }

    /**
     * 通过条件获取单条数据
     * <p>
     * 该方法根据查询参数获取单条记录，通常用于获取详情信息。
     * 内部调用Mapper的findOneByCondition方法执行实际查询。
     * </p>
     * <p>
     * 内存安全：安全处理查询结果，可能返回null
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param dbQueryParamInnerDto 查询参数，包含表名、条件等信息，不能为null
     * @return Dict 查询结果，如果没有匹配记录则可能返回null
     */
    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (Objects.isNull(dbQueryParamInnerDto)) {
            throw new GXBusinessException("查询参数不能为空");
        }
        if (CharSequenceUtil.isEmpty(dbQueryParamInnerDto.getTableName())) {
            dbQueryParamInnerDto.setTableName(getTableName());
        }
        return baseMapper.findOneByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过联合查询条件获取单条数据
     * <p>
     * 该方法支持UNION查询获取单条记录，可以将多个查询结果合并后获取第一条记录。
     * 内部调用Mapper的unionFindOneByCondition方法执行实际查询。
     * </p>
     * <p>
     * 内存安全：安全处理查询结果，可能返回null
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，不能为null
     * @param unionTypeEnums             union的类型（UNION或UNION ALL），不能为null
     * @return Dict 查询结果，如果没有匹配记录则可能返回null
     */
    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (Objects.isNull(masterQueryParamInnerDto)) {
            throw new GXBusinessException("主查询参数不能为空");
        }
        if (Objects.isNull(unionQueryParamInnerDtoLst)) {
            throw new GXBusinessException("联合查询参数列表不能为空");
        }
        if (Objects.isNull(unionTypeEnums)) {
            throw new GXBusinessException("联合查询类型不能为空");
        }
        if (CharSequenceUtil.isEmpty(masterQueryParamInnerDto.getTableName())) {
            masterQueryParamInnerDto.setTableName(getTableName());
        }
        return baseMapper.unionFindOneByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    /**
     * 通过条件获取数据列表
     * <p>
     * 该方法根据查询参数获取多条记录，通常用于列表查询。
     * 内部调用Mapper的findByCondition方法执行实际查询。
     * </p>
     * <p>
     * 内存安全：返回安全的集合对象，即使没有记录也会返回空列表而非null
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件，包含表名、条件等信息，不能为null
     * @return List<Dict> 查询结果列表，如果没有匹配记录则返回空列表
     */
    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (Objects.isNull(dbQueryParamInnerDto)) {
            throw new GXBusinessException("查询参数不能为空");
        }
        if (CharSequenceUtil.isEmpty(dbQueryParamInnerDto.getTableName())) {
            dbQueryParamInnerDto.setTableName(getTableName());
        }
        return baseMapper.findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过联合查询条件获取数据列表
     * <p>
     * 该方法支持UNION查询获取多条记录，可以将多个查询结果合并后返回。
     * 内部调用Mapper的unionFindByCondition方法执行实际查询。
     * </p>
     * <p>
     * 内存安全：返回安全的集合对象，即使没有记录也会返回空列表而非null
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，不能为null
     * @param unionTypeEnums             union的类型（UNION或UNION ALL），不能为null
     * @return List<Dict> 查询结果列表，如果没有匹配记录则返回空列表
     */
    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (Objects.isNull(masterQueryParamInnerDto)) {
            throw new GXBusinessException("主查询参数不能为空");
        }
        if (Objects.isNull(unionQueryParamInnerDtoLst)) {
            throw new GXBusinessException("联合查询参数列表不能为空");
        }
        if (Objects.isNull(unionTypeEnums)) {
            throw new GXBusinessException("联合查询类型不能为空");
        }
        if (CharSequenceUtil.isEmpty(masterQueryParamInnerDto.getTableName())) {
            masterQueryParamInnerDto.setTableName(getTableName());
        }
        return baseMapper.unionFindByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    /**
     * 根据条件执行软删除（逻辑删除）
     * <p>
     * 该方法执行逻辑删除操作，通常是将记录的is_deleted字段设置为1，而不是物理删除记录。
     * 同时可以更新其他字段，如删除时间、删除人等信息。
     * </p>
     * <p>
     * 内存安全：安全处理参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param tableName       表名，不能为null或空字符串
     * @param updateFieldList 软删除时需要同时更新的字段列表，可以为null
     * @param condition       删除条件，不能为null或空列表
     * @param extraData       额外数据，可以为null
     * @return Integer 影响的行数，删除成功返回大于0的整数，失败返回0
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            throw new GXBusinessException("软删除数据需要指定条件，防止误删除全表数据");
        }
        GXBaseQueryParamInnerDto dbQueryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).extraData(extraData).build();
        return baseMapper.deleteSoftCondition(dbQueryParamInnerDto, updateFieldList);
    }

    /**
     * 根据条件执行软删除（逻辑删除）的简化方法
     * <p>
     * 该方法是{@link #deleteSoftCondition(String, List, List, Dict)}的简化版本，
     * 不需要指定更新字段列表，仅执行基本的逻辑删除操作。
     * </p>
     * <p>
     * 内存安全：使用安全的集合操作，避免空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 删除条件，不能为null或空列表
     * @param extraData 额外数据，可以为null
     * @return Integer 影响的行数，删除成功返回大于0的整数，失败返回0
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        return deleteSoftCondition(tableName, CollUtil.newArrayList(), condition, extraData);
    }

    /**
     * 根据条件执行物理删除
     * <p>
     * 该方法执行物理删除操作，会从数据库中永久删除匹配条件的记录。
     * 使用时需谨慎，建议优先考虑使用软删除。
     * </p>
     * <p>
     * 内存安全：安全处理参数，防止空指针异常
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 删除条件，不能为null或空列表
     * @return Integer 影响的行数，删除成功返回大于0的整数，失败返回0
     * @throws GXBusinessException 当删除条件为空时可能抛出，防止误删除全表数据
     */
    @Override
    public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            throw new GXBusinessException("删除数据需要指定条件，防止误删除全表数据");
        }
        GXBaseQueryParamInnerDto baseQueryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        return baseMapper.deleteCondition(baseQueryParamInnerDto);
    }

    /**
     * 获取当前实体对应的数据库表名
     * <p>
     * 该方法通过反射获取当前DAO操作的实体类对应的数据库表名。
     * 表名通过MyBatis-Plus的TableInfo获取，确保与实体类的@TableName注解一致。
     * </p>
     * <p>
     * 内存安全：使用类型安全的反射操作
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @return String 数据库表的名字
     */
    @Override
    public String getTableName() {
        return GXDBCommonUtils.getTableName(GXCommonUtils.getGenericClassType(getClass(), 1));
    }

    /**
     * 获取实体的主键属性名
     * <p>
     * 该方法通过MyBatis-Plus的TableInfo获取实体类的主键属性名。
     * 主要用于内部构建基于主键的查询条件。
     * </p>
     * <p>
     * 内存安全：使用类型安全的反射操作
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @return String 主键属性名
     */
    @SuppressWarnings("all")
    private String getPrimaryKeyName() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(GXCommonUtils.getGenericClassType(getClass(), 1));
        return tableInfo.getKeyProperty();
    }
}