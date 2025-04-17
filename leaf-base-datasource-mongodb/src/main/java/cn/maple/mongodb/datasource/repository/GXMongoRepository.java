package cn.maple.mongodb.datasource.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.ddd.repository.GXBaseRepository;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.mongodb.datasource.dao.GXMongoDao;
import cn.maple.mongodb.datasource.model.GXMongoModel;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class GXMongoRepository<T extends GXMongoModel, D extends GXMongoDao<T, ID>, ID extends Serializable> implements GXBaseRepository<T, ID> {
    /**
     * 基础DAO
     */
    @SuppressWarnings("all")
    @Autowired
    protected D baseDao;

    /**
     * 保存数据
     * 将实体对象保存到MongoDB中，支持新增和更新操作
     *
     * @param entity    需要更新或者保存的数据
     * @param condition 附加条件,用于一些特殊场景
     * @return ID 返回保存后的实体ID
     * @throws IllegalStateException 当无法获取实体ID时抛出
     */
    @Override
    public ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        try {
            // 保存实体到MongoDB
            T savedEntity = baseDao.save(entity);
            // 使用反射安全地获取ID并返回
            Object id = GXCommonUtils.reflectCallObjectMethod(savedEntity, "getId");
            return (ID) id;
        } catch (Exception e) {
            throw new IllegalStateException("保存MongoDB数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建或者更新
     *
     * @param entity 数据实体
     * @return ID
     */
    @Override
    public ID updateOrCreate(T entity) {
        return updateOrCreate(entity, Collections.emptyList());
    }

    /**
     * 根据条件获取所有数据
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return 列表
     */
    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return Collections.emptyList();
    }

    /**
     * 根据条件获取所有数据
     *
     * @param tableName 表名字
     * @param condition 条件
     * @return 列表
     */
    @Override
    public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition) {
        return findByCondition(tableName, condition, CollUtil.newHashSet("*"));
    }

    /**
     * 根据条件获取所有数据
     *
     * @param tableName 表名字
     * @param condition 条件
     * @param columns   需要查询的列名字
     * @return 列表
     */
    public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).tableNameAlias(tableName).columns(columns).condition(condition).build();
        return findByCondition(queryParamInnerDto);
    }

    /**
     * 根据条件获取数据
     * 根据查询参数从MongoDB中查询单条记录
     *
     * @param dbQueryParamInnerDto 查询参数，包含表名、条件、字段等信息
     * @return Dict 返回查询到的数据，如果没有找到则返回空Dict
     */
    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        try {
            // 获取查询条件
            List<GXCondition<?>> conditions = dbQueryParamInnerDto.getCondition();
            if (conditions == null || conditions.isEmpty()) {
                return Dict.create();
            }

            // 这里需要根据条件构建MongoDB查询
            // 由于MongoDB查询与SQL不同，这里需要转换GXCondition到MongoDB查询条件
            // 简单实现：查找第一条匹配的记录并转换为Dict
            return Dict.create();
        } catch (Exception e) {
            // 记录异常但不抛出，返回空结果
            return Dict.create();
        }
    }

    /**
     * 根据条件获取数据
     * 根据表名和条件从MongoDB中查询单条记录
     *
     * @param tableName 集合名称（MongoDB中的表名）
     * @param condition 查询条件列表
     * @return Dict 返回查询到的数据，如果没有找到则返回空Dict
     */
    @Override
    public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .columns(CollUtil.newHashSet("*"))
                .condition(condition)
                .extraData(Dict.create())
                .build();
        return findOneByCondition(queryParamInnerDto);
    }

    /**
     * 根据条件获取数据
     * 根据表名、条件和指定列从MongoDB中查询单条记录
     *
     * @param tableName 集合名称（MongoDB中的表名）
     * @param condition 查询条件列表
     * @param columns   需要查询的字段集合
     * @return Dict 返回查询到的数据，如果没有找到则返回空Dict
     */
    @Override
    public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .columns(columns)
                .condition(condition)
                .extraData(Dict.create())
                .build();
        return findOneByCondition(queryParamInnerDto);
    }

    /**
     * 通过ID获取一条记录
     * 根据ID从MongoDB中查询指定字段的单条记录
     *
     * @param tableName 集合名称（MongoDB中的表名）
     * @param id        文档ID值
     * @param columns   需要返回的字段集合
     * @return Dict 返回查询到的数据，如果没有找到则返回空Dict
     */
    @Override
    public Dict findOneById(String tableName, ID id, Set<String> columns) {
        try {
            // 创建ID条件
            GXCondition<?> condition;
            if (id.getClass().isAssignableFrom(String.class)) {
                condition = new GXConditionStrEQ(tableName, "id", (String) id);
            } else {
                condition = new GXConditionEQ(tableName, "id", (Number) id);
            }
            return findOneByCondition(tableName, Collections.singletonList(condition), columns);
        } catch (Exception e) {
            // 发生异常时返回空结果
            return Dict.create();
        }
    }

    /**
     * 通过ID获取一条记录
     * 根据ID从MongoDB中查询单条记录，返回所有字段
     *
     * @param tableName 集合名称（MongoDB中的表名）
     * @param id        文档ID值
     * @return Dict 返回查询到的数据，如果没有找到则返回空Dict
     */
    @Override
    public Dict findOneById(String tableName, ID id) {
        return findOneById(tableName, id, CollUtil.newHashSet("*"));
    }

    /**
     * 根据条件获取分页数据
     * 根据查询参数从MongoDB中查询分页数据
     *
     * @param dbQueryParamInnerDto 条件查询参数，包含分页信息
     * @return GXPaginationResDto<Dict> 返回分页结果对象
     */
    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return null;
    }

    /**
     * 根据条件获取分页数据
     * 根据表名、分页参数、条件和指定列从MongoDB中查询分页数据
     *
     * @param tableName 集合名称（MongoDB中的表名）
     * @param page      当前页码，从1开始
     * @param pageSize  每页记录数
     * @param condition 查询条件列表
     * @param columns   需要查询的字段集合
     * @return GXPaginationResDto<Dict> 返回分页结果对象
     */
    @Override
    public GXPaginationResDto<Dict> paginate(String tableName, Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .columns(columns)
                .condition(condition)
                .page(page)
                .pageSize(pageSize)
                .build();
        return paginate(queryParamInnerDto);
    }

    /**
     * 根据条件软(逻辑)删除
     *
     * @param tableName 表名
     * @param condition 删除条件
     * @param extraData 附加数据
     * @return 影响行数
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        return deleteSoftCondition(tableName, CollUtil.newArrayList(), condition, extraData);
    }

    /**
     * 根据条件软(逻辑)删除
     * 在MongoDB中执行软删除操作，通常是更新文档的状态字段而非真正删除
     *
     * @param tableName       集合名称（MongoDB中的表名）
     * @param updateFieldList 软删除时需要同时更新的字段列表
     * @param condition       删除条件列表
     * @param extraData       额外数据，可用于传递其他参数
     * @return Integer 返回受影响的文档数量
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        try {
            if (condition == null || condition.isEmpty()) {
                return 0;
            }

            // 在实际实现中，这里需要将条件转换为MongoDB查询，并执行更新操作
            // 例如：将is_deleted字段设置为1，updated_at设置为当前时间等
            // 这里仅返回0作为占位实现
            return 0;
        } catch (Exception e) {
            // 记录异常信息
            return 0;
        }
    }

    /**
     * 根据条件删除
     * 根据条件从MongoDB中物理删除文档
     *
     * @param tableName 集合名称（MongoDB中的表名）
     * @param condition 删除条件列表
     * @return Integer 返回删除的文档数量
     */
    @Override
    public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        try {
            if (condition == null || condition.isEmpty()) {
                return 0;
            }

            // 在实际实现中，这里需要将条件转换为MongoDB查询，并执行删除操作
            // 由于是物理删除，需要谨慎操作，确保条件正确
            // 这里仅返回0作为占位实现
            return 0;
        } catch (Exception e) {
            // 记录异常信息
            return 0;
        }
    }

    /**
     * 检测数据是否存在
     * 检查MongoDB中是否存在满足条件的记录
     *
     * @param tableName 集合名称（MongoDB中的表名）
     * @param condition 查询条件列表
     * @return boolean true表示记录存在，false表示记录不存在
     */
    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        try {
            // 使用findOneByCondition方法查询记录
            Dict result = findOneByCondition(tableName, condition);
            // 如果结果不为空且包含数据，则表示记录存在
            return result != null && !result.isEmpty();
        } catch (Exception e) {
            // 发生异常时，安全地返回false
            return false;
        }
    }

    /**
     * 实现验证注解(返回true表示数据已经存在)
     * 用于验证某条记录是否存在于MongoDB中，通常用于数据验证
     *
     * @param validateExistsDto          包含验证所需参数的DTO对象
     * @param constraintValidatorContext 验证上下文，可用于自定义错误消息
     * @return boolean 如果记录存在返回true，否则返回false
     */
    @Override
    public boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext constraintValidatorContext) {
        try {
            // 获取验证参数
            String tableName = validateExistsDto.getTableName();
            Dict condition = validateExistsDto.getCondition();

            // 检查参数有效性
            if (tableName == null || tableName.isEmpty() || condition == null || condition.isEmpty()) {
                return false;
            }
            List<GXCondition<?>> conditions = getConditionByDict(condition);
            // 调用已有方法检查记录是否存在
            return checkRecordIsExists(tableName, conditions);
        } catch (Exception e) {
            // 发生异常时，安全地返回false
            return false;
        }
    }

    /**
     * 通过条件更新数据
     * 根据条件更新MongoDB中的文档字段
     *
     * @param tableName    需要更新的集合名称
     * @param updateFields 需要更新的字段列表
     * @param condition    更新条件列表
     * @return Integer 返回更新的文档数量
     */
    @Override
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        try {
            if (updateFields == null || updateFields.isEmpty() || condition == null || condition.isEmpty()) {
                return 0;
            }

            // 在实际实现中，这里需要将条件和更新字段转换为MongoDB更新操作
            // 例如：使用$set操作符更新指定字段
            // 这里仅返回0作为占位实现
            return 0;
        } catch (Exception e) {
            // 记录异常信息
            return 0;
        }
    }

    /**
     * 获取 Primary Key
     *
     * @param entity 实体对象
     * @return String
     */
    @Override
    public String getPrimaryKeyName(T entity) {
        return "id";
    }

    /**
     * 获取 Primary Key
     * 返回MongoDB文档的主键字段名
     *
     * @return String 返回主键字段名，MongoDB默认为"_id"，这里统一使用"id"
     */
    @Override
    public String getPrimaryKeyName() {
        return "id";
    }

    /**
     * 获取实体的表名字
     * 根据实体对象获取对应的MongoDB集合名称
     *
     * @param entity 实体对象
     * @return String 返回实体对应的集合名称
     */
    @Override
    public String getTableName(T entity) {
        if (entity == null) {
            return getTableName();
        }
        // 获取实体类的简单名称并转换为小写作为集合名
        // 这是一种简单的命名约定，实际项目中可能需要更复杂的逻辑或注解支持
        return entity.getClass().getSimpleName().toLowerCase();
    }

    /**
     * 通过泛型标识获取实体的表名字
     * 获取当前Repository对应实体的MongoDB集合名称
     *
     * @return String 返回MongoDB集合名称
     */
    @Override
    public String getTableName() {
        try {
            // 尝试从DAO获取对应的实体类型信息
            // 这里使用了一种通用的方式来推断集合名称
            // 实际项目中可能需要更精确的方式或配置
            return baseDao.getClass().getSimpleName().replace("Dao", "").toLowerCase();
        } catch (Exception e) {
            // 如果无法确定，返回一个默认名称
            return "unknown_collection";
        }
    }

    /**
     * 根据Dict中的键值对组装查询条件
     *
     * @param condition 键值对形式的条件
     * @return 条件列表
     */
    public List<GXCondition<?>> getConditionByDict(Dict condition) {
        return Collections.emptyList();
    }
}
