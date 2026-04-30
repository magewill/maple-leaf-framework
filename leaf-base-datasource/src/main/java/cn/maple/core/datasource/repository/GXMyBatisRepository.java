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

public abstract class GXMyBatisRepository<M extends GXBaseMapper<T>, T extends GXBaseModel, D extends GXMyBatisDao<M, T, ID>, ID extends Serializable> implements GXBaseRepository<T, ID> {
    @SuppressWarnings("all")
    private static final Logger LOGGER = LoggerFactory.getLogger(GXMyBatisRepository.class);

    @SuppressWarnings("all")
    @Autowired
    protected D baseDao;

    @Override
    public ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        GXValidatorUtils.validateEntity(entity);
        return baseDao.updateOrCreate(entity, condition);
    }

    @Override
    public ID updateOrCreate(T entity) {
        return updateOrCreate(entity, CollUtil.newArrayList());
    }

    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return baseDao.findByCondition(dbQueryParamInnerDto);
    }

    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        return baseDao.findByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    @Override
    public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).columns(CollUtil.newHashSet("*")).build();
        return findByCondition(queryParamInnerDto);
    }

    public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).tableNameAlias(tableName).columns(columns).condition(condition).build();
        return findByCondition(queryParamInnerDto);
    }

    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (CharSequenceUtil.isEmpty(dbQueryParamInnerDto.getTableName())) {
            dbQueryParamInnerDto.setTableName(getTableName());
        }
        return baseDao.findOneByCondition(dbQueryParamInnerDto);
    }

    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isEmpty(masterQueryParamInnerDto.getTableName())) {
            masterQueryParamInnerDto.setTableName(getTableName());
        }
        return baseDao.findOneByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    @Override
    public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        return findOneByCondition(tableName, condition, null);
    }

    @Override
    public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
        Assert.notNull(condition, "条件不能为null");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).columns(columns).build();
        return findOneByCondition(queryParamInnerDto);
    }

    @Override
    public Dict findOneById(String tableName, ID id) {
        return findOneById(tableName, id, CollUtil.newHashSet("*"));
    }

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

    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (CharSequenceUtil.isBlank(dbQueryParamInnerDto.getRawSQL()) && Objects.isNull(dbQueryParamInnerDto.getColumns())) {
            dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("*"));
        }
        return baseDao.paginate(dbQueryParamInnerDto);
    }

    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (CharSequenceUtil.isBlank(masterQueryParamInnerDto.getRawSQL()) && Objects.isNull(masterQueryParamInnerDto.getColumns())) {
            masterQueryParamInnerDto.setColumns(CollUtil.newHashSet("*"));
        }
        return baseDao.paginate(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    @Override
    public GXPaginationResDto<Dict> paginate(String tableName, Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns) {
        Assert.notNull(condition, "条件不能为null");
        if (Objects.isNull(columns)) {
            columns = CollUtil.newHashSet("*");
        }
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().page(page).pageSize(pageSize).tableName(tableName).condition(condition).columns(columns).build();
        return paginate(queryParamInnerDto);
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        return baseDao.deleteSoftCondition(tableName, updateFieldList, condition, extraData);
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        return deleteSoftCondition(tableName, CollUtil.newArrayList(), condition, extraData);
    }

    @Override
    public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        return baseDao.deleteCondition(tableName, condition);
    }

    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("条件不能为空!");
        }
        return baseDao.checkRecordIsExists(tableName, condition);
    }

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

    @Override
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        Assert.notNull(condition, "条件不能为null");
        if (condition.isEmpty()) {
            throw new GXBusinessException("更新数据需要指定条件");
        }
        return baseDao.updateFieldByCondition(tableName, updateFields, condition);
    }

    @Override
    public String getPrimaryKeyName(T entity) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
        return tableInfo.getKeyProperty();
    }

    @Override
    public String getPrimaryKeyName() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(GXCommonUtils.getGenericClassType(getClass(), 1));
        return tableInfo.getKeyProperty();
    }

    @Override
    public String getTableName(T entity) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
        return tableInfo.getTableName();
    }
    
    @Override
    public String getTableName() {
        Class<?> entityClass = GXCommonUtils.getGenericClassType(getClass(), 1);
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        return tableInfo.getTableName();
    }
}
