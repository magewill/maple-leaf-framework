package cn.maple.core.datasource.dao;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class GXMyBatisDao<M extends GXBaseMapper<T>, T extends GXBaseModel, ID extends Serializable> extends ServiceImpl<M, T> implements GXBaseDao<T, ID> {
    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (Objects.isNull(dbQueryParamInnerDto)) {
            throw new GXBusinessException("Query param must not be null");
        }
        IPage<Dict> iPage = GXDBCommonUtils.constructPageObject(dbQueryParamInnerDto.getPage(), dbQueryParamInnerDto.getPageSize(), dbQueryParamInnerDto.isPaginateCount());
        Set<String> fieldSet = dbQueryParamInnerDto.getColumns();
        if (CharSequenceUtil.isBlank(dbQueryParamInnerDto.getRawSQL()) && Objects.isNull(fieldSet)) {
            dbQueryParamInnerDto.setColumns(CollUtil.newHashSet("*"));
        }
        final List<Dict> records = baseMapper.paginate(iPage, dbQueryParamInnerDto);
        iPage.setRecords(records);
        return GXDBCommonUtils.convertPageToPaginationResDto(iPage);
    }

    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (Objects.isNull(masterQueryParamInnerDto)) {
            throw new GXBusinessException("Master query param must not be null");
        }
        if (CollUtil.isEmpty(unionQueryParamInnerDtoLst)) {
            throw new GXBusinessException("Union query param list must not be empty");
        }
        if (Objects.isNull(unionTypeEnums)) {
            throw new GXBusinessException("Union type must not be null");
        }
        IPage<Dict> iPage = GXDBCommonUtils.constructPageObject(masterQueryParamInnerDto.getPage(), masterQueryParamInnerDto.getPageSize(), masterQueryParamInnerDto.isPaginateCount());
        Set<String> fieldSet = masterQueryParamInnerDto.getColumns();
        if (CharSequenceUtil.isBlank(masterQueryParamInnerDto.getRawSQL()) && Objects.isNull(fieldSet)) {
            masterQueryParamInnerDto.setColumns(CollUtil.newHashSet("*"));
        }
        final List<Dict> records = baseMapper.unionPaginate(iPage, masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
        iPage.setRecords(records);
        return GXDBCommonUtils.convertPageToPaginationResDto(iPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> data, List<GXCondition<?>> condition) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            throw new GXBusinessException("Update condition must not be empty");
        }
        GXBaseQueryParamInnerDto dbQueryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        return baseMapper.updateFieldByCondition(dbQueryParamInnerDto, data);
    }

    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            throw new GXBusinessException("Exists check condition must not be empty");
        }
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        Boolean val = baseMapper.checkRecordIsExists(queryParamInnerDto);
        return Boolean.TRUE.equals(val);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        GXValidatorUtils.validateEntity(entity);
        List<GXCondition<?>> effectiveCondition = Objects.isNull(condition) ? new ArrayList<>(4) : new ArrayList<>(condition);
        String pkName = getPrimaryKeyName();
        String pkMethodName = CharSequenceUtil.format("get{}", CharSequenceUtil.upperFirst(pkName));
        Object o = GXCommonUtils.reflectCallObjectMethod(entity, pkMethodName);
        Class<ID> retIDClazz = GXCommonUtils.getGenericClassType(getClass(), 2);
        if (Objects.nonNull(o) && !CollUtil.contains(Arrays.asList("0", "", 0), o)) {
            if (o instanceof CharSequence) {
                effectiveCondition.add(new GXConditionStrEQ(getTableName(), pkName, o.toString()));
            } else {
                effectiveCondition.add(new GXConditionEQ(getTableName(), pkName, Long.valueOf(o.toString())));
            }
        }
        if (!effectiveCondition.isEmpty() && checkRecordIsExists(getTableName(), effectiveCondition)) {
            UpdateWrapper<T> updateWrapper = GXDBCommonUtils.assemblyUpdateWrapper(effectiveCondition);
            update(entity, updateWrapper);
        } else {
            save(entity);
        }
        String methodName = CharSequenceUtil.format("get{}", CharSequenceUtil.upperFirst(pkName));
        return Convert.convert(retIDClazz, GXCommonUtils.reflectCallObjectMethod(entity, methodName));
    }

    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (Objects.isNull(dbQueryParamInnerDto)) {
            throw new GXBusinessException("Query param must not be null");
        }
        if (CharSequenceUtil.isEmpty(dbQueryParamInnerDto.getTableName())) {
            dbQueryParamInnerDto.setTableName(getTableName());
        }
        return baseMapper.findOneByCondition(dbQueryParamInnerDto);
    }

    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (Objects.isNull(masterQueryParamInnerDto)) {
            throw new GXBusinessException("Master query param must not be null");
        }
        if (Objects.isNull(unionQueryParamInnerDtoLst)) {
            throw new GXBusinessException("Union query param list must not be null");
        }
        if (Objects.isNull(unionTypeEnums)) {
            throw new GXBusinessException("Union type must not be null");
        }
        if (CharSequenceUtil.isEmpty(masterQueryParamInnerDto.getTableName())) {
            masterQueryParamInnerDto.setTableName(getTableName());
        }
        return baseMapper.unionFindOneByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        if (Objects.isNull(dbQueryParamInnerDto)) {
            throw new GXBusinessException("Query param must not be null");
        }
        if (CharSequenceUtil.isEmpty(dbQueryParamInnerDto.getTableName())) {
            dbQueryParamInnerDto.setTableName(getTableName());
        }
        return baseMapper.findByCondition(dbQueryParamInnerDto);
    }

    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        if (Objects.isNull(masterQueryParamInnerDto)) {
            throw new GXBusinessException("Master query param must not be null");
        }
        if (Objects.isNull(unionQueryParamInnerDtoLst)) {
            throw new GXBusinessException("Union query param list must not be null");
        }
        if (Objects.isNull(unionTypeEnums)) {
            throw new GXBusinessException("Union type must not be null");
        }
        if (CharSequenceUtil.isEmpty(masterQueryParamInnerDto.getTableName())) {
            masterQueryParamInnerDto.setTableName(getTableName());
        }
        return baseMapper.unionFindByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            throw new GXBusinessException("Soft delete condition must not be empty");
        }
        GXBaseQueryParamInnerDto dbQueryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).extraData(extraData).build();
        return baseMapper.deleteSoftCondition(dbQueryParamInnerDto, updateFieldList);
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        return deleteSoftCondition(tableName, CollUtil.newArrayList(), condition, extraData);
    }

    @Override
    public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            throw new GXBusinessException("Delete condition must not be empty");
        }
        GXBaseQueryParamInnerDto baseQueryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        return baseMapper.deleteCondition(baseQueryParamInnerDto);
    }

    @Override
    public String getTableName() {
        return GXDBCommonUtils.getTableName(GXCommonUtils.getGenericClassType(getClass(), 1));
    }

    @SuppressWarnings("all")
    private String getPrimaryKeyName() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(GXCommonUtils.getGenericClassType(getClass(), 1));
        return tableInfo.getKeyProperty();
    }
}
