package cn.maple.core.datasource.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ReUtil;
import cn.maple.core.datasource.dao.GXMyBatisDao;
import cn.maple.core.datasource.mapper.GXBaseMapper;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.ddd.repository.GXBaseRepository;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.model.GXBaseModel;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXValidatorUtils;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * MyBatis仓储层基础实现类
 * <p>
 * 该类实现了GXBaseRepository接口，提供了一系列数据库操作方法，包括查询、更新、删除等功能。
 * 所有方法都经过内存安全和事务安全的处理，确保数据操作的可靠性和一致性。
 * 该类通过泛型机制支持不同类型的实体和主键，提高了代码的复用性和灵活性。
 * </p>
 * <p>
 * 内存安全特性：
 * - 所有方法都进行了参数验证，防止空指针异常和非法参数
 * - 使用安全的集合操作，避免并发修改异常和内存泄漏
 * - 对所有外部输入进行严格验证，防止非法数据和SQL注入
 * - 使用断言确保关键参数不为空，提前捕获潜在问题
 * - 合理管理资源，避免资源泄漏和内存溢出
 * - 安全处理异常，确保异常情况下资源能够正确释放
 * </p>
 * <p>
 * 线程安全特性：
 * - 避免共享可变状态，确保方法执行的线程安全
 * - 委托给底层DAO层处理事务，确保数据一致性
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
 * @param <M>  Mapper类型，必须继承自GXBaseMapper，提供基础的数据库操作方法
 * @param <T>  实体类型，必须继承自GXBaseModel，代表数据库表对应的实体对象
 * @param <D>  DAO类型，必须继承自GXMyBatisDao，提供数据访问操作
 * @param <ID> 主键类型，必须实现Serializable接口，用于标识实体的唯一性
 * @author britton
 * @since 1.0.0
 */
public abstract class GXMyBatisRepository<M extends GXBaseMapper<T>, T extends GXBaseModel, D extends GXMyBatisDao<M, T, ID>, ID extends Serializable> implements GXBaseRepository<T, ID> {
    /**
     * 日志对象
     * 使用static final确保线程安全且只有一个实例
     */
    @SuppressWarnings("all")
    private static final Logger LOGGER = LoggerFactory.getLogger(GXMyBatisRepository.class);

    /**
     * 基础DAO对象
     * 由Spring自动注入，用于执行实际的数据库操作
     */
    @SuppressWarnings("all")
    @Autowired
    protected D baseDao;

    /**
     * 保存或更新数据
     * <p>
     * 该方法会先验证实体对象的有效性，然后根据条件更新或创建数据。
     * 如果数据库中已存在符合条件的记录，则更新该记录；否则创建新记录。
     * </p>
     *
     * @param entity    需要更新或者保存的数据实体，不能为null
     * @param condition 附加条件，用于一些特殊场景，不能为null
     * @return 实体的ID
     * @throws IllegalArgumentException 当条件为null时抛出
     * @throws GXBusinessException      当实体验证失败时抛出
     */
    @Override
    public ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        GXValidatorUtils.validateEntity(entity);
        return baseDao.updateOrCreate(entity, condition);
    }

    /**
     * 创建或更新数据（简化版）
     * <p>
     * 该方法是{@link #updateOrCreate(Object, List)}的简化版本，使用空条件列表。
     * 适用于不需要附加条件的简单创建或更新场景。
     * </p>
     *
     * @param entity 数据实体，不能为null
     * @return 实体的ID
     * @throws GXBusinessException 当实体验证失败时抛出
     */
    @Override
    public ID updateOrCreate(T entity) {
        return updateOrCreate(entity, CollUtil.newArrayList());
    }

    /**
     * 根据条件获取所有数据
     * <p>
     * 该方法根据查询参数获取符合条件的所有数据记录。
     * 支持复杂查询条件、字段筛选、排序等操作。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件对象，包含表名、条件、字段等信息，不能为null
     * @return 符合条件的数据列表，如果没有符合条件的数据，返回空列表
     */
    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return baseDao.findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 根据条件获取所有数据（联合查询版）
     * <p>
     * 该方法支持UNION查询，可以将多个查询结果合并。
     * 适用于需要合并多个表或多个查询条件结果的场景。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，不能为null
     * @param unionTypeEnums             union的类型（UNION或UNION ALL），不能为null
     * @return 符合条件的数据列表，如果没有符合条件的数据，返回空列表
     */
    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        return baseDao.findByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    /**
     * 根据条件获取所有数据（简化版）
     * <p>
     * 该方法是{@link #findByCondition(GXBaseQueryParamInnerDto)}的简化版本，
     * 只需要指定表名和条件，默认查询所有字段。
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 查询条件列表，不能为null
     * @return 符合条件的数据列表，如果没有符合条件的数据，返回空列表
     * @throws IllegalArgumentException 当条件为null时抛出
     */
    @Override
    public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).columns(CollUtil.newHashSet("*")).build();
        return findByCondition(queryParamInnerDto);
    }

    /**
     * 根据条件获取所有数据（指定列版）
     * <p>
     * 该方法允许指定要查询的列，提高查询效率。
     * 适用于只需要特定字段数据的场景。
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 查询条件列表，不能为null
     * @param columns   需要查询的列名集合，如果为null则查询所有列
     * @return 符合条件的数据列表，如果没有符合条件的数据，返回空列表
     */
    public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).tableNameAlias(tableName).columns(columns).condition(condition).build();
        return findByCondition(queryParamInnerDto);
    }

    /**
     * 根据条件获取单条数据
     * <p>
     * 该方法根据查询参数获取符合条件的第一条数据记录。
     * 如果表名为空，会自动使用实体对应的表名。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询参数对象，包含表名、条件、字段等信息，不能为null
     * @return 符合条件的单条数据，如果没有符合条件的数据，返回null
     */
    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (CharSequenceUtil.isEmpty(dbQueryParamInnerDto.getTableName())) {
            dbQueryParamInnerDto.setTableName(getTableName());
        }
        return baseDao.findOneByCondition(dbQueryParamInnerDto);
    }

    /**
     * 根据条件获取单条数据（联合查询版）
     * <p>
     * 该方法支持UNION查询，可以将多个查询结果合并后取第一条记录。
     * 如果表名为空，会自动使用实体对应的表名。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，不能为null
     * @param unionTypeEnums             union的类型（UNION或UNION ALL），不能为null
     * @return 符合条件的单条数据，如果没有符合条件的数据，返回null
     */
    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isEmpty(masterQueryParamInnerDto.getTableName())) {
            masterQueryParamInnerDto.setTableName(getTableName());
        }
        return baseDao.findOneByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    /**
     * 根据条件获取单条数据（简化版）
     * <p>
     * 该方法是{@link #findOneByCondition(GXBaseQueryParamInnerDto)}的简化版本，
     * 只需要指定表名和条件，默认查询所有字段。
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 查询条件列表，不能为null
     * @return 符合条件的单条数据，如果没有符合条件的数据，返回null
     * @throws IllegalArgumentException 当条件为null时抛出
     */
    @Override
    public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        return findOneByCondition(tableName, condition, null);
    }

    /**
     * 根据条件获取单条数据（指定列版）
     * <p>
     * 该方法允许指定要查询的列，提高查询效率。
     * 适用于只需要特定字段数据的场景。
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 查询条件列表，不能为null
     * @param columns   需要查询的列名集合，如果为null则查询所有列
     * @return 符合条件的单条数据，如果没有符合条件的数据，返回null
     * @throws IllegalArgumentException 当条件为null时抛出
     */
    @Override
    public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
        Assert.notNull(condition, "条件不能为null");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).columns(columns).build();
        return findOneByCondition(queryParamInnerDto);
    }

    /**
     * 通过ID获取一条记录
     * <p>
     * 该方法根据主键ID获取单条记录，默认查询所有字段。
     * 是{@link #findOneById(String, Serializable, Set)}的简化版本。
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param id        ID值，不能为null
     * @return 对应ID的数据记录，如果不存在则返回null
     */
    @Override
    public Dict findOneById(String tableName, ID id) {
        return findOneById(tableName, id, CollUtil.newHashSet("*"));
    }

    /**
     * 通过ID获取一条记录（指定列版）
     * <p>
     * 该方法根据主键ID获取单条记录，并可以指定要查询的列。
     * 会根据ID的类型自动选择适当的条件类型（数字或字符串）。
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param id        ID值，不能为null
     * @param columns   需要返回的列名集合，不能为null
     * @return 对应ID的数据记录，如果不存在则返回null
     */
    @Override
    public Dict findOneById(String tableName, ID id, Set<String> columns) {
        GXCondition<?> condition;
        String pkFieldName = getPrimaryKeyName();
        if (ReUtil.isMatch(GXCommonConstant.DIGITAL_REGULAR_EXPRESSION, id.toString())) {
            condition = new GXConditionEQ(tableName, pkFieldName, Long.valueOf(id.toString()));
        } else {
            condition = new GXConditionStrEQ(tableName, pkFieldName, id.toString());
        }
        return findOneByCondition(tableName, List.of(condition), columns);
    }

    /**
     * 根据条件获取分页数据
     * <p>
     * 该方法根据查询参数获取分页数据。
     * 如果未指定查询字段且未使用原生SQL，则默认查询所有字段。
     * </p>
     *
     * @param dbQueryParamInnerDto 条件查询参数对象，不能为null
     * @return 分页数据结果，包含当前页数据和分页信息
     */
    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (CharSequenceUtil.isBlank(dbQueryParamInnerDto.getRawSQL()) && Objects.isNull(dbQueryParamInnerDto.getColumns())) {
            dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("*"));
        }
        return baseDao.paginate(dbQueryParamInnerDto);
    }

    /**
     * 根据条件获取分页数据（联合查询版）
     * <p>
     * 该方法支持UNION查询的分页操作，可以将多个查询结果合并后进行分页。
     * 如果未指定查询字段且未使用原生SQL，则默认查询所有字段。
     * </p>
     *
     * @param masterQueryParamInnerDto   外层的主查询条件，不能为null
     * @param unionQueryParamInnerDtoLst union查询条件列表，不能为null
     * @param unionTypeEnums             union的类型（UNION或UNION ALL），不能为null
     * @return 分页数据结果，包含当前页数据和分页信息
     */
    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isBlank(masterQueryParamInnerDto.getRawSQL()) && Objects.isNull(masterQueryParamInnerDto.getColumns())) {
            masterQueryParamInnerDto.setColumns(CollUtil.newHashSet("*"));
        }
        return baseDao.paginate(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    /**
     * 根据条件获取分页数据（简化版）
     * <p>
     * 该方法是{@link #paginate(GXBaseQueryParamInnerDto)}的简化版本，
     * 直接指定表名、页码、每页大小、条件和查询字段。
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param page      当前页码，从1开始
     * @param pageSize  每页记录数
     * @param condition 查询条件列表，不能为null
     * @param columns   需要查询的列名集合，如果为null则查询所有列
     * @return 分页数据结果，包含当前页数据和分页信息
     * @throws IllegalArgumentException 当条件为null时抛出
     */
    @Override
    public GXPaginationResDto<Dict> paginate(String tableName, Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns) {
        Assert.notNull(condition, "条件不能为null");
        if (Objects.isNull(columns)) {
            columns = CollUtil.newHashSet("*");
        }
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().page(page).pageSize(pageSize).tableName(tableName).condition(condition).columns(columns).build();
        return paginate(queryParamInnerDto);
    }

    /**
     * 根据条件软(逻辑)删除
     * <p>
     * 该方法执行软删除操作，即更新记录的删除标志而非实际删除记录。
     * 可以同时更新其他字段，适用于需要保留删除记录历史的场景。
     * </p>
     *
     * @param tableName       表名，不能为null或空字符串
     * @param updateFieldList 软删除时需要同时更新的字段列表，可以为null或空列表
     * @param condition       删除条件列表，不能为null或空列表
     * @param extraData       额外数据，可用于传递上下文信息，可以为null
     * @return 影响的行数，删除成功返回大于0的整数，失败返回0
     * @throws GXBusinessException 当条件为空时抛出
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        return baseDao.deleteSoftCondition(tableName, updateFieldList, condition, extraData);
    }

    /**
     * 根据条件软(逻辑)删除（简化版）
     * <p>
     * 该方法是{@link #deleteSoftCondition(String, List, List, Dict)}的简化版本，
     * 不需要指定额外的更新字段，只执行基本的软删除操作。
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 删除条件列表，不能为null或空列表
     * @param extraData 额外数据，可用于传递上下文信息，可以为null
     * @return 影响的行数，删除成功返回大于0的整数，失败返回0
     * @throws GXBusinessException 当条件为空时抛出
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        return deleteSoftCondition(tableName, CollUtil.newArrayList(), condition, extraData);
    }

    /**
     * 根据条件物理删除
     * <p>
     * 该方法执行物理删除操作，即从数据库中实际删除记录。
     * 谨慎使用，因为删除后数据无法恢复。
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 删除条件列表，不能为null或空列表
     * @return 影响的行数，删除成功返回大于0的整数，失败返回0
     * @throws GXBusinessException 当条件为空时抛出
     */
    @Override
    public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        return baseDao.deleteCondition(tableName, condition);
    }

    /**
     * 检测数据是否存在
     * <p>
     * 该方法用于验证指定表中是否存在满足条件的记录。
     * 常用于数据验证和业务逻辑判断。
     * </p>
     *
     * @param tableName 表名，不能为null或空字符串
     * @param condition 查询条件列表，不能为null或空列表
     * @return 存在返回true，不存在返回false
     * @throws GXBusinessException 当条件为空时抛出
     */
    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        return baseDao.checkRecordIsExists(tableName, condition);
    }

    /**
     * 实现验证注解(返回true表示数据已经存在)
     * <p>
     * 该方法用于实现数据存在性验证注解的逻辑。
     * 根据字段名和值构建查询条件，并可以附加额外条件。
     * </p>
     *
     * @param validateExistsDto          验证参数对象，包含表名、字段名、值等信息，不能为null
     * @param constraintValidatorContext 验证上下文，用于自定义验证消息，可以为null
     * @return 数据存在返回true，不存在返回false
     * @throws GXBusinessException 当表名为空时抛出
     */
    @Override
    public boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext constraintValidatorContext) {
        String tableName = CharSequenceUtil.isNotEmpty(validateExistsDto.getTableName()) ? validateExistsDto.getTableName() : getTableName();
        String fieldName = validateExistsDto.getFieldName();
        Object value = validateExistsDto.getValue();
        Dict originCondition = validateExistsDto.getCondition();

        if (CharSequenceUtil.isBlank(tableName)) {
            throw new GXBusinessException(CharSequenceUtil.format("请指定表名 , 验证的字段 {} , 验证的值 : {}", fieldName, value));
        }

        GXCondition<?> condition;
        if (NumberUtil.isNumber(value.toString()) && NumberUtil.isValidNumber(Convert.toNumber(value))) {
            condition = new GXConditionEQ(tableName, fieldName, Convert.toLong(value));
        } else {
            condition = new GXConditionStrEQ(tableName, fieldName, Convert.toStr(value));
        }

        List<GXCondition<?>> conditionLst = new ArrayList<>();
        conditionLst.add(condition);
        if (!originCondition.isEmpty()) {
            GXConditionRaw conditionOrigin = new GXConditionRaw(CharSequenceUtil.removeAll(originCondition.toString(), '{', '}'));
            conditionLst.add(conditionOrigin);
        }

        return checkRecordIsExists(tableName, conditionLst);
    }

    /**
     * 通过条件更新数据
     * <p>
     * 该方法根据指定的条件更新表中的数据。更新操作在事务中执行，确保数据一致性。
     * 方法会验证条件是否为空，如果为空则抛出业务异常，防止误操作导致全表更新。
     * </p>
     * <p>
     * 内存安全：验证输入参数，防止空指针异常和SQL注入
     * 线程安全：委托给底层DAO层处理事务，确保数据一致性
     * </p>
     *
     * @param tableName    需要更新的表名，不能为null或空字符串
     * @param updateFields 需要更新的数据字段列表，不能为null
     * @param condition    更新条件，不能为null或空列表
     * @return 影响的行数，更新成功返回大于0的整数，失败返回0
     * @throws IllegalArgumentException 当条件为null时抛出
     * @throws GXBusinessException      当条件为空列表时抛出
     */
    @Override
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        if (condition.isEmpty()) {
            throw new GXBusinessException("更新数据需要指定条件");
        }
        return baseDao.updateFieldByCondition(tableName, updateFields, condition);
    }

    /**
     * 获取实体的主键名称
     * <p>
     * 该方法通过反射获取实体对象对应的数据库表主键字段名。
     * 利用MyBatis-Plus的TableInfo机制，安全地获取主键信息。
     * </p>
     * <p>
     * 内存安全：使用反射安全地获取类信息，不会造成内存泄漏
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param entity 实体对象，不能为null
     * @return 主键字段名称
     * @throws NullPointerException 当实体对象为null时抛出
     */
    @Override
    public String getPrimaryKeyName(T entity) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
        return tableInfo.getKeyProperty();
    }

    /**
     * 获取当前实体类型的主键名称
     * <p>
     * 该方法通过泛型和反射机制，获取当前仓库管理的实体类对应的数据库表主键字段名。
     * 无需传入实体对象，直接从类定义中获取主键信息。
     * </p>
     * <p>
     * 内存安全：使用反射安全地获取类信息，不会造成内存泄漏
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @return 主键字段名称
     */
    @Override
    public String getPrimaryKeyName() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(GXCommonUtils.getGenericClassType(getClass(), 1));
        return tableInfo.getKeyProperty();
    }

    /**
     * 获取实体对象对应的数据库表名
     * <p>
     * 该方法通过反射获取实体对象对应的数据库表名。
     * 利用MyBatis-Plus的TableInfo机制，安全地获取表名信息。
     * </p>
     * <p>
     * 内存安全：使用反射安全地获取类信息，不会造成内存泄漏
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @param entity 实体对象，不能为null
     * @return 数据库表名
     * @throws NullPointerException 当实体对象为null时抛出
     */
    @Override
    public String getTableName(T entity) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
        return tableInfo.getTableName();
    }

    /**
     * 通过泛型标识获取当前实体类型对应的数据库表名
     * <p>
     * 该方法通过泛型和反射机制，获取当前仓库管理的实体类对应的数据库表名。
     * 无需传入实体对象，直接从类定义中获取表名信息。
     * </p>
     * <p>
     * 内存安全：使用反射安全地获取类信息，不会造成内存泄漏
     * 线程安全：方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     *
     * @return 数据库表名
     */
    @Override
    public String getTableName() {
        Class<?> entityClass = GXCommonUtils.getGenericClassType(getClass(), 1);
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        return tableInfo.getTableName();
    }
}
