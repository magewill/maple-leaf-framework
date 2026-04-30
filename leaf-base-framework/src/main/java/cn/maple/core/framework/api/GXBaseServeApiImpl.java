package cn.maple.core.framework.api;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.convert.ConvertException;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.api.dto.req.GXBaseApiReqDto;
import cn.maple.core.framework.api.dto.res.GXBaseApiResDto;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.protocol.req.GXQueryParamReqProtocol;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.core.framework.util.GXCommonUtils;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GXBaseServeApiImpl<S extends GXBusinessService> implements GXBaseServeApi {
    protected static final Map<String, Class<?>> STATIC_SERVE_SERVICE_CLASS_MAP = new ConcurrentHashMap<>();

    protected static final ThreadLocal<Class<?>> DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL = ThreadLocal.withInitial(() -> null);

    @Override
    public <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Class<R> targetClazz) {
        List<R> rs = findByCondition(condition, targetClazz, Dict.create());
        return GXCommonUtils.convertSourceListToTargetList(rs, targetClazz, null, null);
    }

    @Override
    public <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Map<String, String> orderField, Class<R> targetClazz) {
        List<GXCondition<?>> conditionList = convertTableConditionToConditionExp(getTableName(), condition);
        Object rLst = callMethod("findByCondition", conditionList, orderField);
        if (Objects.nonNull(rLst)) {
            return GXCommonUtils.convertSourceListToTargetList((Collection<?>) rLst, targetClazz, null, null);
        }
        return Collections.emptyList();
    }

    @Override
    public <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Class<R> targetClazz, Object extraData) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        Object rLst = callMethod("findByCondition", convertTableConditionToConditionExp(condition), extraData);
        if (Objects.nonNull(rLst)) {
            return GXCommonUtils.convertSourceListToTargetList((Collection<?>) rLst, targetClazz, null, null);
        }
        return Collections.emptyList();
    }

    @Override
    public <E> List<E> findFieldByCondition(Table<String, String, Object> condition, Set<String> columns, Class<E> targetClazz) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        if (Objects.isNull(columns) || columns.isEmpty()) {
            columns = CollUtil.newHashSet("*");
        }
        List<GXCondition<?>> conditions = convertTableConditionToConditionExp(condition);
        Object o = callMethod("findMultiFieldByCondition", conditions, columns, targetClazz);
        if (Objects.isNull(o)) {
            return Collections.emptyList();
        }
        try {
            return Convert.convert(new TypeReference<List<E>>() {
            }, o);
        } catch (ConvertException e) {
            // 记录转换异常，但返回空列表而不是抛出异常，保持与原方法行为一致
            return Collections.emptyList();
        }
    }

    @Override
    public <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Class<R> targetClazz) {
        return findOneByCondition(condition, targetClazz, Dict.create());
    }

    @Override
    public <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Set<String> columns, Class<R> targetClazz, Object extraData) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        if (Objects.isNull(columns) || columns.isEmpty()) {
            columns = CollUtil.newHashSet("*");
        }
        Object r = callMethod("findOneByCondition", convertTableConditionToConditionExp(condition), columns, extraData);
        if (Objects.nonNull(r)) {
            try {
                return GXCommonUtils.convertSourceToTarget(r, targetClazz, null, CopyOptions.create());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Class<R> targetClazz, Object extraData) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        return findOneByCondition(condition, CollUtil.newHashSet("*"), targetClazz, extraData);
    }

    @Override
    public <R extends GXBaseApiResDto> R findById(Long id, Set<String> columns, Class<R> targetClazz) {
        if (Objects.isNull(id)) {
            throw new NullPointerException("ID不能为null");
        }
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        HashBasedTable<String, String, Object> conditionTable = HashBasedTable.create();
        conditionTable.put("id", GXBuilderConstant.EQ, id);
        return findOneByCondition(conditionTable, columns, targetClazz, Dict.create());
    }

    @Override
    public <R extends GXBaseApiResDto> R findById(Long id, Class<R> targetClazz) {
        if (Objects.isNull(id)) {
            throw new NullPointerException("ID不能为null");
        }
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        return findById(id, CollUtil.newHashSet("*"), targetClazz);
    }

    @Override
    public <E> E findSingleFieldByCondition(Table<String, String, Object> condition, String column, Class<E> targetClazz) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("目标类型不能为null");
        }
        if (Objects.isNull(column)) {
            throw new NullPointerException("字段名不能为null");
        }
        if (CharSequenceUtil.isEmpty(column)) {
            throw new IllegalArgumentException("字段名不能为空字符串");
        }
        Object r = callMethod("findSingleFieldByCondition", convertTableConditionToConditionExp(condition), column, targetClazz);
        if (Objects.nonNull(r)) {
            try {
                return Convert.convert(targetClazz, r);
            } catch (ConvertException e) {
                // 记录转换异常，但返回null而不是抛出异常，保持与原方法行为一致
                return null;
            }
        }
        return null;
    }

    @Override
    public <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto, Table<String, String, Object> condition, CopyOptions copyOptions) {
        if (Objects.isNull(reqDto)) {
            throw new NullPointerException("请求参数不能为null");
        }
        if (Objects.isNull(copyOptions)) {
            throw new NullPointerException("复制选项不能为null");
        }

        List<GXCondition<?>> conditionList = null;
        if (Objects.nonNull(condition)) {
            conditionList = convertTableConditionToConditionExp(condition);
        } else {
            conditionList = Collections.emptyList();
        }

        Object id = callMethod("updateOrCreate", reqDto, conditionList, copyOptions);
        if (Objects.nonNull(id)) {
            try {
                return (ID) id;
            } catch (ClassCastException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto, CopyOptions copyOptions) {
        if (Objects.isNull(reqDto)) {
            throw new NullPointerException("请求参数不能为null");
        }
        if (Objects.isNull(copyOptions)) {
            throw new NullPointerException("复制选项不能为null");
        }
        return updateOrCreate(reqDto, HashBasedTable.create(), copyOptions);
    }

    @Override
    public <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto) {
        if (Objects.isNull(reqDto)) {
            throw new NullPointerException("请求参数不能为null");
        }
        return updateOrCreate(reqDto, CopyOptions.create());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> GXPaginationResDto<R> paginate(GXQueryParamReqProtocol reqProtocol, Class<R> targetClazz, CopyOptions copyOptions) {
        GXBaseQueryParamInnerDto baseQueryParamInnerDto = GXCommonUtils.convertSourceToTarget(reqProtocol, GXBaseQueryParamInnerDto.class, null, copyOptions);
        if (CharSequenceUtil.isEmpty(baseQueryParamInnerDto.getTableName())) {
            baseQueryParamInnerDto.setTableName(getTableName());
        }
        Object paginate = callMethod("paginate", baseQueryParamInnerDto);
        if (Objects.nonNull(paginate)) {
            GXPaginationResDto<R> retPaginate = (GXPaginationResDto<R>) paginate;
            // XXXDBResDto
            List<?> records = retPaginate.getRecords();
            List<R> rs = GXCommonUtils.convertSourceListToTargetList(records, targetClazz, null, copyOptions);
            retPaginate.setRecords(rs);
            return retPaginate;
        }
        return null;
    }

    @Override
    public <R> GXPaginationResDto<R> paginate(GXQueryParamReqProtocol reqProtocol, Class<R> targetClazz) {
        return paginate(reqProtocol, targetClazz, CopyOptions.create());
    }

    @Override
    public Integer deleteCondition(Table<String, String, Object> condition) {
        Object cnt = callMethod("deleteCondition", convertTableConditionToConditionExp(condition));
        if (Objects.nonNull(cnt)) {
            return (Integer) cnt;
        }
        return 0;
    }

    @Override
    public Integer deleteSoftCondition(Table<String, String, Object> condition) {
        Object cnt = callMethod("deleteSoftCondition", convertTableConditionToConditionExp(condition));
        if (Objects.nonNull(cnt)) {
            return (Integer) cnt;
        }
        return 0;
    }

    @Override
    public Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, Table<String, String, Object> condition) {
        List<GXCondition<?>> conditionList = convertTableConditionToConditionExp(condition);
        Object cnt = callMethod("updateFieldByCondition", updateFields, conditionList);
        if (Objects.nonNull(cnt)) {
            return (Integer) cnt;
        }
        return 0;
    }

    @Override
    public boolean checkRecordIsExists(Table<String, String, Object> condition) {
        Object exists = callMethod("checkRecordIsExists", convertTableConditionToConditionExp(condition));
        return (Boolean) exists;
    }

    @Override
    public Long count(Table<String, String, Object> condition) {
        Object cnt = callMethod("countByCondition", convertTableConditionToConditionExp(condition));
        return (Long) cnt;
    }

    @Override
    public <T, Q extends GXBaseApiReqDto> T sourceToTarget(Q reqDto, Class<T> targetClass, String methodName, CopyOptions copyOptions, Dict extraData) {
        return GXCommonUtils.convertSourceToTarget(reqDto, targetClass, methodName, copyOptions, extraData);
    }

    @Override
    public <T, Q extends GXBaseApiReqDto> T sourceToTarget(Q reqDto, Class<T> targetClass, String methodName, CopyOptions copyOptions) {
        return sourceToTarget(reqDto, targetClass, methodName, copyOptions, Dict.create());
    }

    @Override
    public <T, Q extends GXBaseApiReqDto> T sourceToTarget(Q reqDto, Class<T> targetClass) {
        return sourceToTarget(reqDto, targetClass, null, CopyOptions.create());
    }

    @Override
    public void staticBindServeServiceClass(Class<?> serveServiceClass) {
        if (Objects.isNull(serveServiceClass)) {
            throw new IllegalArgumentException("服务类Class对象不能为null");
        }
        String apiClassName = getClass().getSimpleName();
        STATIC_SERVE_SERVICE_CLASS_MAP.put(apiClassName, serveServiceClass);
    }

    @Override
    public GXBaseServeApi callBindTargetServeSericeClass(Class<?> targetServeServiceClass) {
        if (Objects.nonNull(targetServeServiceClass)) {
            DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL.set(targetServeServiceClass);
        }
        return this;
    }

    @Override
    public Object callMethod(String methodName, Object... params) {
        if (CharSequenceUtil.isEmpty(methodName)) {
            throw new IllegalArgumentException("方法名不能为空");
        }

        Class<?> serveServiceClass = getServeServiceClass();
        if (Objects.nonNull(serveServiceClass)) {
            try {
                return GXCommonUtils.reflectCallObjectMethod(serveServiceClass, methodName, params);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public Class<?> getServeServiceClass() {
        try {
            Class<?> serveServiceClass = DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL.get();
            if (Objects.nonNull(serveServiceClass)) {
                return serveServiceClass;
            }
            String apiClassName = getClass().getSimpleName();
            return STATIC_SERVE_SERVICE_CLASS_MAP.get(apiClassName);
        } finally {
            DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL.remove();
        }
    }

    @Override
    public List<GXCondition<?>> convertTableConditionToConditionExp(Table<String, String, Object> condition) {
        return convertTableConditionToConditionExp(getTableName(), condition);
    }

    @Override
    public List<GXCondition<?>> convertTableConditionToConditionExp(String tableNameAlias, Table<String, String, Object> condition) {
        if (Objects.isNull(tableNameAlias)) {
            throw new NullPointerException("表别名不能为null");
        }
        return GXCommonUtils.convertTableConditionToConditionExp(tableNameAlias, condition);
    }

    private String getTableName() {
        Object tableName = callMethod("getTableName");
        if (Objects.nonNull(tableName)) {
            if (tableName instanceof String) {
                return (String) tableName;
            } else {
                return String.valueOf(tableName);
            }
        }
        return null;
    }
}
