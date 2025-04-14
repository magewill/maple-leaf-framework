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
    /**
     * 日志对象
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXMyBatisDao.class);

    /**
     * 分页  返回实体对象
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return GXPagination
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
     * 分页  返回实体对象
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return GXPagination
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
     *
     * @param tableName 表名字
     * @param data      需要更新的数据
     * @param condition 更新条件
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> data, List<GXCondition<?>> condition) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            throw new GXBusinessException("更新数据需要指定条件");
        }
        return baseMapper.updateFieldByCondition(tableName, data, condition);
    }

    /**
     * 检测给定条件的记录是否存在
     *
     * @param tableName 数据库表名字
     * @param condition 条件
     * @return int
     */
    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        Integer val = baseMapper.checkRecordIsExists(queryParamInnerDto);
        return Objects.nonNull(val);
    }

    /**
     * 保存数据
     *
     * @param entity    需要更新或者保存的数据
     * @param condition 附加条件,用于一些特殊场景
     * @return ID
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
     * 通过条件获取数据
     *
     * @param dbQueryParamInnerDto 查询参数
     * @return 列表
     */
    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return baseMapper.findOneByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过条件获取数据
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return 列表
     */
    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        return baseMapper.unionFindOneByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    /**
     * 通过条件获取数据列表
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return 列表
     */
    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return baseMapper.findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 通过条件获取数据列表
     *
     * @param masterQueryParamInnerDto   外层的主查询条件
     * @param unionQueryParamInnerDtoLst union查询条件
     * @param unionTypeEnums             union的类型
     * @return 列表
     */
    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto masterQueryParamInnerDto, List<GXBaseQueryParamInnerDto> unionQueryParamInnerDtoLst, GXUnionTypeEnums unionTypeEnums) {
        return baseMapper.unionFindByCondition(masterQueryParamInnerDto, unionQueryParamInnerDtoLst, unionTypeEnums);
    }

    /**
     * 根据条件软(逻辑)删除
     *
     * @param tableName       表名
     * @param updateFieldList 软删除时需要同时更新的字段
     * @param condition       删除条件
     * @param extraData       额外数据
     * @return 影响行数
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        GXBaseQueryParamInnerDto dbQueryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).extraData(extraData).build();
        return baseMapper.deleteSoftCondition(dbQueryParamInnerDto, updateFieldList);
    }

    /**
     * 根据条件软(逻辑)删除
     *
     * @param tableName 表名
     * @param condition 删除条件
     * @param extraData 额外数据
     * @return 影响行数
     */
    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        return deleteSoftCondition(tableName, CollUtil.newArrayList(), condition, extraData);
    }

    /**
     * 根据条件删除
     *
     * @param tableName 表名
     * @param condition 删除条件
     * @return 影响行数
     */
    @Override
    public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto baseQueryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        return baseMapper.deleteCondition(baseQueryParamInnerDto);
    }

    /**
     * 获取表名字
     *
     * @return 数据库表的名字
     */
    @Override
    public String getTableName() {
        return GXDBCommonUtils.getTableName(GXCommonUtils.getGenericClassType(getClass(), 1));
    }

    /**
     * 获取实体的Class 对象
     *
     * @return Class
     */
    @SuppressWarnings("all")
    private String getPrimaryKeyName() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(GXCommonUtils.getGenericClassType(getClass(), 1));
        return tableInfo.getKeyProperty();
    }
}