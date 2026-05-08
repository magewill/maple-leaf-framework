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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class GXBaseServeApiImpl<S extends GXBusinessService> implements GXBaseServeApi {
    private static final Logger LOG = LoggerFactory.getLogger(GXBaseServeApiImpl.class);

    protected static final Map<String, Class<?>> STATIC_SERVE_SERVICE_CLASS_MAP = new ConcurrentHashMap<>();

    protected static final ThreadLocal<Class<?>> DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL = ThreadLocal.withInitial(() -> null);

    @Override
    public <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Class<R> targetClazz) {
        return findByCondition(condition, targetClazz, Dict.create());
    }

    @Override
    public <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Map<String, String> orderField, Class<R> targetClazz) {
        return executeWithDynamicBindingCleanup(() -> {
            assertTargetClassNotNull(targetClazz);
            List<GXCondition<?>> conditionList = convertTableConditionToConditionExp(getTableName(), condition);
            Object rLst = invokeServeServiceMethod("findByCondition", conditionList, orderField);
            return convertCollectionResult(rLst, targetClazz, null);
        });
    }

    @Override
    public <R extends GXBaseApiResDto> List<R> findByCondition(Table<String, String, Object> condition, Class<R> targetClazz, Object extraData) {
        return executeWithDynamicBindingCleanup(() -> {
            assertTargetClassNotNull(targetClazz);
            Object rLst = invokeServeServiceMethod("findByCondition", convertTableConditionWithDefaultTable(condition), extraData);
            return convertCollectionResult(rLst, targetClazz, null);
        });
    }

    @Override
    public <E> List<E> findFieldByCondition(Table<String, String, Object> condition, Set<String> columns, Class<E> targetClazz) {
        return executeWithDynamicBindingCleanup(() -> {
            assertTargetClassNotNull(targetClazz);
            Set<String> queryColumns = defaultColumns(columns);
            List<GXCondition<?>> conditions = convertTableConditionWithDefaultTable(condition);
            Object o = invokeServeServiceMethod("findMultiFieldByCondition", conditions, queryColumns, targetClazz);
            if (Objects.isNull(o)) {
                return Collections.emptyList();
            }
            try {
                return Convert.convert(new TypeReference<List<E>>() {
                }, o);
            } catch (ConvertException e) {
                LOG.warn("Failed to convert multi-field result: targetType={}, error={}", targetClazz.getName(), e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    @Override
    public <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Class<R> targetClazz) {
        return findOneByCondition(condition, targetClazz, Dict.create());
    }

    @Override
    public <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Set<String> columns, Class<R> targetClazz, Object extraData) {
        return executeWithDynamicBindingCleanup(() -> {
            assertTargetClassNotNull(targetClazz);
            Set<String> queryColumns = defaultColumns(columns);
            Object r = invokeServeServiceMethod("findOneByCondition", convertTableConditionWithDefaultTable(condition), queryColumns, extraData);
            if (Objects.nonNull(r)) {
                try {
                    return GXCommonUtils.convertSourceToTarget(r, targetClazz, null, CopyOptions.create());
                } catch (Exception e) {
                    LOG.warn("Failed to convert single result: targetType={}, error={}", targetClazz.getName(), e.getMessage());
                    return null;
                }
            }
            return null;
        });
    }

    @Override
    public <R extends GXBaseApiResDto> R findOneByCondition(Table<String, String, Object> condition, Class<R> targetClazz, Object extraData) {
        assertTargetClassNotNull(targetClazz);
        return findOneByCondition(condition, CollUtil.newHashSet("*"), targetClazz, extraData);
    }

    @Override
    public <R extends GXBaseApiResDto> R findById(Long id, Set<String> columns, Class<R> targetClazz) {
        if (Objects.isNull(id)) {
            throw new NullPointerException("Id must not be null");
        }
        assertTargetClassNotNull(targetClazz);
        HashBasedTable<String, String, Object> conditionTable = HashBasedTable.create();
        conditionTable.put("id", GXBuilderConstant.EQ, id);
        return findOneByCondition(conditionTable, columns, targetClazz, Dict.create());
    }

    @Override
    public <R extends GXBaseApiResDto> R findById(Long id, Class<R> targetClazz) {
        if (Objects.isNull(id)) {
            throw new NullPointerException("Id must not be null");
        }
        assertTargetClassNotNull(targetClazz);
        return findById(id, CollUtil.newHashSet("*"), targetClazz);
    }

    @Override
    public <E> E findSingleFieldByCondition(Table<String, String, Object> condition, String column, Class<E> targetClazz) {
        return executeWithDynamicBindingCleanup(() -> {
            assertTargetClassNotNull(targetClazz);
            if (Objects.isNull(column)) {
                throw new NullPointerException("Column must not be null");
            }
            if (CharSequenceUtil.isEmpty(column)) {
                throw new IllegalArgumentException("Column must not be empty");
            }
            Object r = invokeServeServiceMethod("findSingleFieldByCondition", convertTableConditionWithDefaultTable(condition), column, targetClazz);
            if (Objects.nonNull(r)) {
                try {
                    return Convert.convert(targetClazz, r);
                } catch (ConvertException e) {
                    LOG.warn("Failed to convert single-field result: targetType={}, error={}", targetClazz.getName(), e.getMessage());
                    return null;
                }
            }
            return null;
        });
    }

    @Override
    public <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto, Table<String, String, Object> condition, CopyOptions copyOptions) {
        return executeWithDynamicBindingCleanup(() -> {
            if (Objects.isNull(reqDto)) {
                throw new NullPointerException("Request dto must not be null");
            }
            if (Objects.isNull(copyOptions)) {
                throw new NullPointerException("Copy options must not be null");
            }

            List<GXCondition<?>> conditionList = Objects.nonNull(condition)
                    ? convertTableConditionWithDefaultTable(condition)
                    : Collections.emptyList();

            Object id = invokeServeServiceMethod("updateOrCreate", reqDto, conditionList, copyOptions);
            if (Objects.nonNull(id)) {
                try {
                    return (ID) id;
                } catch (ClassCastException e) {
                    LOG.warn("Failed to cast update result id: resultType={}, error={}", id.getClass().getName(), e.getMessage());
                    return null;
                }
            }
            return null;
        });
    }

    @Override
    public <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto, CopyOptions copyOptions) {
        if (Objects.isNull(reqDto)) {
            throw new NullPointerException("Request dto must not be null");
        }
        if (Objects.isNull(copyOptions)) {
            throw new NullPointerException("Copy options must not be null");
        }
        return updateOrCreate(reqDto, HashBasedTable.create(), copyOptions);
    }

    @Override
    public <ID, Q extends GXBaseApiReqDto> ID updateOrCreate(Q reqDto) {
        if (Objects.isNull(reqDto)) {
            throw new NullPointerException("Request dto must not be null");
        }
        return updateOrCreate(reqDto, CopyOptions.create());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> GXPaginationResDto<R> paginate(GXQueryParamReqProtocol reqProtocol, Class<R> targetClazz, CopyOptions copyOptions) {
        return executeWithDynamicBindingCleanup(() -> {
            assertTargetClassNotNull(targetClazz);
            CopyOptions safeCopyOptions = copyOptions == null ? CopyOptions.create() : copyOptions;
            GXBaseQueryParamInnerDto baseQueryParamInnerDto = GXCommonUtils.convertSourceToTarget(reqProtocol, GXBaseQueryParamInnerDto.class, null, safeCopyOptions);
            if (baseQueryParamInnerDto == null) {
                return null;
            }
            if (CharSequenceUtil.isEmpty(baseQueryParamInnerDto.getTableName())) {
                baseQueryParamInnerDto.setTableName(getTableName());
            }
            Object paginate = invokeServeServiceMethod("paginate", baseQueryParamInnerDto);
            if (paginate instanceof GXPaginationResDto<?> retPaginate) {
                List<?> records = retPaginate.getRecords();
                List<R> rs = GXCommonUtils.convertSourceListToTargetList(records, targetClazz, null, safeCopyOptions);
                GXPaginationResDto<R> result = (GXPaginationResDto<R>) retPaginate;
                result.setRecords(rs);
                return result;
            }
            return null;
        });
    }

    @Override
    public <R> GXPaginationResDto<R> paginate(GXQueryParamReqProtocol reqProtocol, Class<R> targetClazz) {
        return paginate(reqProtocol, targetClazz, CopyOptions.create());
    }

    @Override
    public Integer deleteCondition(Table<String, String, Object> condition) {
        return executeWithDynamicBindingCleanup(() -> convertToInteger(invokeServeServiceMethod("deleteCondition", convertTableConditionWithDefaultTable(condition))));
    }

    @Override
    public Integer deleteSoftCondition(Table<String, String, Object> condition) {
        return executeWithDynamicBindingCleanup(() -> convertToInteger(invokeServeServiceMethod("deleteSoftCondition", convertTableConditionWithDefaultTable(condition))));
    }

    @Override
    public Integer updateFieldByCondition(List<GXUpdateField<?>> updateFields, Table<String, String, Object> condition) {
        return executeWithDynamicBindingCleanup(() -> {
            List<GXCondition<?>> conditionList = convertTableConditionWithDefaultTable(condition);
            return convertToInteger(invokeServeServiceMethod("updateFieldByCondition", updateFields, conditionList));
        });
    }

    @Override
    public boolean checkRecordIsExists(Table<String, String, Object> condition) {
        return executeWithDynamicBindingCleanup(() -> Boolean.TRUE.equals(invokeServeServiceMethod("checkRecordIsExists", convertTableConditionWithDefaultTable(condition))));
    }

    @Override
    public Long count(Table<String, String, Object> condition) {
        return executeWithDynamicBindingCleanup(() -> {
            Object cnt = invokeServeServiceMethod("countByCondition", convertTableConditionWithDefaultTable(condition));
            return cnt == null ? 0L : Convert.convert(Long.class, cnt);
        });
    }

    @Override
    public <T, Q extends GXBaseApiReqDto> T sourceToTarget(Q reqDto, Class<T> targetClass, String methodName, CopyOptions copyOptions, Dict extraData) {
        return executeWithDynamicBindingCleanup(() -> GXCommonUtils.convertSourceToTarget(reqDto, targetClass, methodName, copyOptions, extraData));
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
            throw new IllegalArgumentException("Service class must not be null");
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
        return executeWithDynamicBindingCleanup(() -> invokeServeServiceMethod(methodName, params));
    }

    @Override
    public Class<?> getServeServiceClass() {
        Class<?> serveServiceClass = DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL.get();
        if (Objects.nonNull(serveServiceClass)) {
            return serveServiceClass;
        }
        String apiClassName = getClass().getSimpleName();
        return STATIC_SERVE_SERVICE_CLASS_MAP.get(apiClassName);
    }

    @Override
    public List<GXCondition<?>> convertTableConditionToConditionExp(Table<String, String, Object> condition) {
        return executeWithDynamicBindingCleanup(() -> convertTableConditionWithDefaultTable(condition));
    }

    @Override
    public List<GXCondition<?>> convertTableConditionToConditionExp(String tableNameAlias, Table<String, String, Object> condition) {
        if (Objects.isNull(tableNameAlias)) {
            throw new NullPointerException("Table alias must not be null");
        }
        return GXCommonUtils.convertTableConditionToConditionExp(tableNameAlias, condition);
    }

    private String getTableName() {
        Object tableName = invokeServeServiceMethod("getTableName");
        if (Objects.nonNull(tableName)) {
            if (tableName instanceof String) {
                return (String) tableName;
            } else {
                return String.valueOf(tableName);
            }
        }
        return null;
    }

    private List<GXCondition<?>> convertTableConditionWithDefaultTable(Table<String, String, Object> condition) {
        return convertTableConditionToConditionExp(getTableName(), condition);
    }

    private <T> T executeWithDynamicBindingCleanup(Supplier<T> supplier) {
        try {
            return supplier.get();
        } finally {
            DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL.remove();
        }
    }

    private Object invokeServeServiceMethod(String methodName, Object... params) {
        if (CharSequenceUtil.isEmpty(methodName)) {
            throw new IllegalArgumentException("Method name must not be empty");
        }

        Class<?> serveServiceClass = getServeServiceClass();
        if (Objects.isNull(serveServiceClass)) {
            LOG.warn("No bound service class found: api={}", getClass().getName());
            return null;
        }
        try {
            return GXCommonUtils.reflectCallObjectMethod(serveServiceClass, methodName, params);
        } catch (Exception e) {
            LOG.warn("Service method invocation failed: service={}, method={}, error={}",
                    serveServiceClass.getName(), methodName, e.getMessage());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Service method invocation failure details", e);
            }
            return null;
        }
    }

    private void assertTargetClassNotNull(Class<?> targetClazz) {
        if (Objects.isNull(targetClazz)) {
            throw new NullPointerException("Target type must not be null");
        }
    }

    private Set<String> defaultColumns(Set<String> columns) {
        return Objects.isNull(columns) || columns.isEmpty() ? CollUtil.newHashSet("*") : columns;
    }

    private <R> List<R> convertCollectionResult(Object result, Class<R> targetClazz, CopyOptions copyOptions) {
        if (Objects.isNull(result)) {
            return Collections.emptyList();
        }
        if (!(result instanceof Collection<?> collection)) {
            LOG.warn("Service result is not a collection: resultType={}", result.getClass().getName());
            return Collections.emptyList();
        }
        return GXCommonUtils.convertSourceListToTargetList(collection, targetClazz, null, copyOptions);
    }

    private Integer convertToInteger(Object value) {
        if (value == null) {
            return 0;
        }
        return Convert.convert(Integer.class, value);
    }
}
