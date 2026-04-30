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

public class GXMyBatisDao<M extends GXBaseMapper<T>, T extends GXBaseModel, ID extends Serializable> extends ServiceImpl<M, T> implements GXBaseDao<T, ID> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXMyBatisDao.class);

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> data, List<GXCondition<?>> condition) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            throw new GXBusinessException("更新数据需要指定条件");
        }
        GXBaseQueryParamInnerDto dbQueryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        return baseMapper.updateFieldByCondition(dbQueryParamInnerDto, data);
    }

    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        Boolean val = baseMapper.checkRecordIsExists(queryParamInnerDto);
        return Boolean.TRUE.equals(val);
    }

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

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            throw new GXBusinessException("软删除数据需要指定条件，防止误删除全表数据");
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
            throw new GXBusinessException("删除数据需要指定条件，防止误删除全表数据");
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
