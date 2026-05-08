package cn.maple.elasticsearch.dao;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.elasticsearch.constant.GXEsCriteriaMethodMappingConstant;
import cn.maple.elasticsearch.model.GXElasticsearchModel;
import cn.maple.elasticsearch.support.GXElasticsearchTemplateContext;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.*;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface GXElasticsearchDao<T extends GXElasticsearchModel, Q extends BaseQuery, B extends BaseQueryBuilder<Q, B>, ID extends Serializable> extends ElasticsearchRepository<T, ID> {
    Pattern PAINLESS_FIELD_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_@.-]+");

    Pattern PAINLESS_PARAM_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    default <R> R useElasticsearchTemplate(String elasticsearchTemplateName, Supplier<R> supplier) {
        Assert.hasText(elasticsearchTemplateName, "ElasticsearchTemplate bean name must not be blank");
        Assert.notNull(supplier, "Supplier must not be null");
        return withElasticsearchTemplateContext(elasticsearchTemplateName, supplier);
    }

    default void useElasticsearchTemplate(String elasticsearchTemplateName, Runnable runnable) {
        Assert.notNull(runnable, "Runnable must not be null");
        useElasticsearchTemplate(elasticsearchTemplateName, () -> {
            runnable.run();
            return null;
        });
    }

    default <R> Supplier<R> wrapElasticsearchTemplate(Supplier<R> supplier) {
        Assert.notNull(supplier, "Supplier must not be null");
        return GXElasticsearchTemplateContext.wrap(supplier);
    }

    default Runnable wrapElasticsearchTemplate(Runnable runnable) {
        Assert.notNull(runnable, "Runnable must not be null");
        return GXElasticsearchTemplateContext.wrap(runnable);
    }

    default <R> Supplier<R> wrapElasticsearchTemplate(String elasticsearchTemplateName, Supplier<R> supplier) {
        Assert.hasText(elasticsearchTemplateName, "ElasticsearchTemplate bean name must not be blank");
        Assert.notNull(supplier, "Supplier must not be null");
        return () -> withElasticsearchTemplateContext(elasticsearchTemplateName, supplier);
    }

    default Runnable wrapElasticsearchTemplate(String elasticsearchTemplateName, Runnable runnable) {
        Assert.hasText(elasticsearchTemplateName, "ElasticsearchTemplate bean name must not be blank");
        Assert.notNull(runnable, "Runnable must not be null");
        return () -> withElasticsearchTemplateContext(elasticsearchTemplateName, () -> {
            runnable.run();
            return null;
        });
    }

    @Override
    default <S extends T> S save(S entity) {
        Assert.notNull(entity, "Entity must not be null");
        return getElasticsearchTemplate().save(entity);
    }

    @Override
    default <S extends T> S save(S entity, RefreshPolicy refreshPolicy) {
        Assert.notNull(entity, "Entity must not be null");
        return getElasticsearchOperations(refreshPolicy).save(entity);
    }

    @Override
    default <S extends T> Iterable<S> saveAll(Iterable<S> entities) {
        Assert.notNull(entities, "Entities must not be null");
        return getElasticsearchTemplate().save(entities);
    }

    @Override
    default <S extends T> Iterable<S> saveAll(Iterable<S> entities, RefreshPolicy refreshPolicy) {
        Assert.notNull(entities, "Entities must not be null");
        return getElasticsearchOperations(refreshPolicy).save(entities);
    }

    @Override
    default Optional<T> findById(ID id) {
        Assert.notNull(id, "Id must not be null");
        T entity = getElasticsearchTemplate().get(Convert.toStr(id), getGenericEntityClassType());
        return Optional.ofNullable(entity);
    }

    @Override
    default boolean existsById(ID id) {
        Assert.notNull(id, "Id must not be null");
        return getElasticsearchTemplate().exists(Convert.toStr(id), getGenericClassType());
    }

    @Override
    default Iterable<T> findAll() {
        Query query = Query.findAll();
        query.setPageable(Pageable.unpaged());
        return searchContents(query);
    }

    @Override
    default Iterable<T> findAllById(Iterable<ID> ids) {
        Assert.notNull(ids, "Ids must not be null");
        List<String> idList = new ArrayList<>();
        ids.forEach(id -> idList.add(Convert.toStr(id)));
        if (idList.isEmpty()) {
            return List.of();
        }
        return getElasticsearchTemplate().multiGet(Query.multiGetQuery(idList), getGenericEntityClassType()).stream()
                .filter(MultiGetItem::hasItem)
                .map(MultiGetItem::getItem)
                .collect(Collectors.toList());
    }

    @Override
    default long count() {
        return getElasticsearchTemplate().count(Query.findAll(), getGenericClassType());
    }

    @Override
    default void deleteById(ID id) {
        Assert.notNull(id, "Id must not be null");
        getElasticsearchTemplate().delete(Convert.toStr(id), getGenericClassType());
    }

    @Override
    default void deleteById(ID id, RefreshPolicy refreshPolicy) {
        Assert.notNull(id, "Id must not be null");
        getElasticsearchOperations(refreshPolicy).delete(Convert.toStr(id), getGenericClassType());
    }

    @Override
    default void delete(T entity) {
        Assert.notNull(entity, "Entity must not be null");
        getElasticsearchTemplate().delete(entity);
    }

    @Override
    default void delete(T entity, RefreshPolicy refreshPolicy) {
        Assert.notNull(entity, "Entity must not be null");
        getElasticsearchOperations(refreshPolicy).delete(entity);
    }

    @Override
    default void deleteAllById(Iterable<? extends ID> ids) {
        Assert.notNull(ids, "Ids must not be null");
        ids.forEach(this::deleteById);
    }

    @Override
    default void deleteAllById(Iterable<? extends ID> ids, RefreshPolicy refreshPolicy) {
        Assert.notNull(ids, "Ids must not be null");
        ElasticsearchOperations elasticsearchOperations = getElasticsearchOperations(refreshPolicy);
        ids.forEach(id -> elasticsearchOperations.delete(Convert.toStr(id), getGenericClassType()));
    }

    @Override
    default void deleteAll(Iterable<? extends T> entities) {
        Assert.notNull(entities, "Entities must not be null");
        entities.forEach(this::delete);
    }

    @Override
    default void deleteAll(Iterable<? extends T> entities, RefreshPolicy refreshPolicy) {
        Assert.notNull(entities, "Entities must not be null");
        ElasticsearchOperations elasticsearchOperations = getElasticsearchOperations(refreshPolicy);
        entities.forEach(elasticsearchOperations::delete);
    }

    @Override
    default void deleteAll() {
        DeleteQuery deleteQuery = DeleteQuery.builder(Query.findAll()).build();
        getElasticsearchTemplate().delete(deleteQuery, getGenericClassType());
    }

    @Override
    default void deleteAll(RefreshPolicy refreshPolicy) {
        DeleteQuery deleteQuery = DeleteQuery.builder(Query.findAll()).build();
        getElasticsearchOperations(refreshPolicy).delete(deleteQuery, getGenericClassType());
    }

    @Override
    default Iterable<T> findAll(Sort sort) {
        Assert.notNull(sort, "Sort must not be null");
        Query query = Query.findAll();
        query.setPageable(Pageable.unpaged());
        query.addSort(sort);
        return searchContents(query);
    }

    @Override
    default Page<T> findAll(Pageable pageable) {
        Assert.notNull(pageable, "Pageable must not be null");
        Query query = Query.findAll();
        query.setPageable(pageable);
        SearchHits<T> searchHits = getElasticsearchTemplate().search(query, getGenericEntityClassType());
        List<T> content = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
        return new PageImpl<>(content, pageable, searchHits.getTotalHits());
    }

    @Override
    default Page<T> searchSimilar(T entity, String[] fields, Pageable pageable) {
        Assert.notNull(entity, "Entity must not be null");
        Assert.notNull(fields, "Fields must not be null");
        Assert.notNull(pageable, "Pageable must not be null");
        Object id = getEntityId(entity);
        Assert.notNull(id, "Entity id must not be null");
        MoreLikeThisQuery moreLikeThisQuery = new MoreLikeThisQuery();
        moreLikeThisQuery.setId(Convert.toStr(id));
        moreLikeThisQuery.addFields(fields);
        moreLikeThisQuery.setPageable(pageable);
        SearchHits<T> searchHits = getElasticsearchTemplate().search(moreLikeThisQuery, getGenericEntityClassType());
        List<T> content = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
        return new PageImpl<>(content, pageable, searchHits.getTotalHits());
    }

    default List<Dict> findByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Dict queryData = executeQuery(queryParamInnerDto);
        List<Dict> lst = Convert.convert(new TypeReference<>() {
        }, queryData.getObj("records"));
        return lst.stream().map(d -> Convert.convert(Dict.class, d.getObj("content"))).collect(Collectors.toList());
    }

    default Dict findOneByCondition(GXBaseQueryParamInnerDto queryParamInnerDto) {
        queryParamInnerDto.setPage(0);
        queryParamInnerDto.setPageSize(1);
        Dict queryData = executeQuery(queryParamInnerDto);
        List<Dict> lst = Convert.convert(new TypeReference<>() {
        }, queryData.getObj("records"));
        Optional<Dict> data = lst.stream().findFirst();
        return data.map(t -> Convert.convert(Dict.class, t.getObj("content"))).orElse(null);
    }

    default GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Dict queryData = executeQuery(queryParamInnerDto);
        List<Dict> lst = Convert.convert(new TypeReference<>() {
        }, queryData.getObj("records"));
        List<Dict> records = lst.stream().map(d -> {
            Dict content = Convert.convert(Dict.class, d.getObj("content"), Dict.create());
            Dict highlightFields = Convert.convert(Dict.class, d.getObj("highlightFields"), Dict.create());
            if (!highlightFields.isEmpty()) {
                content.putAll(highlightFields);
            }
            return content;
        }).collect(Collectors.toList());
        long totalCount = queryData.getInt("totalHits");
        long currentPage = Optional.ofNullable(queryParamInnerDto.getPage()).orElse(1);
        long pageSize = NumberUtil.max(Optional.ofNullable(queryParamInnerDto.getPageSize()).orElse(GXCommonConstant.DEFAULT_MAX_PAGE_SIZE), 1);
        long pages = (long) Math.ceil((double) totalCount / pageSize);
        return new GXPaginationResDto<>(records, totalCount, pages, pageSize, currentPage);
    }

    default <ID extends Serializable> ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        Assert.notNull(entity, "Entity must not be null");
        if (CollUtil.isNotEmpty(condition)) {
            deleteCondition(null, condition);
        }
        T save = getElasticsearchTemplate().save(entity);
        Class<ID> retIDClazz = GXCommonUtils.getGenericClassType((Class<?>) getClass().getGenericInterfaces()[0], 3);
        return Convert.convert(retIDClazz, getEntityId(save));
    }

    default Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            throw new GXBusinessException("Condition cannot be empty");
        }
        Assert.notNull(condition, "Condition must not be null");
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .condition(condition)
                .build();
        Query query = buildQuery(queryParamInnerDto);
        DeleteQuery deleteQuery = DeleteQuery.builder(query).build();
        ElasticsearchTemplate elasticsearchTemplate = getElasticsearchTemplate();
        ByQueryResponse deleteResponse = CharSequenceUtil.isEmpty(tableName)
                ? elasticsearchTemplate.delete(deleteQuery, getGenericClassType())
                : elasticsearchTemplate.delete(deleteQuery, getGenericClassType(), IndexCoordinates.of(tableName));
        return Math.toIntExact(deleteResponse.getDeleted());
    }

    private Criteria buildConditionCriteria(String fieldName, String op, Object value) {
        Criteria criteria = new Criteria(fieldName);
        return switch (op) {
            case "=" -> criteria.is(value);
            case "!=" -> criteria.not().is(value);
            case "in" -> criteria.in(toIterableValue(value, op));
            case "not in" -> criteria.notIn(toIterableValue(value, op));
            case ">" -> criteria.greaterThan(value);
            case "<" -> criteria.lessThan(value);
            case ">=" -> criteria.greaterThanEqual(value);
            case "<=" -> criteria.lessThanEqual(value);
            case "like" -> criteria.fuzzy(Convert.toStr(value));
            case "between" -> {
                List<Object> rangeValues = toRangeValues(value);
                yield criteria.between(rangeValues.get(0), rangeValues.get(1));
            }
            case "is" -> value == null ? criteria.not().exists() : criteria.is(value);
            case "is not" -> value == null ? criteria.exists() : criteria.not().is(value);
            default ->
                    throw new GXBusinessException(CharSequenceUtil.format("Elasticsearch unsupported condition operator: {}", op));
        };
    }

    private Iterable<?> toIterableValue(Object value, String op) {
        if (value instanceof Iterable<?> iterable) {
            return iterable;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(value, i));
            }
            return values;
        }
        throw new GXBusinessException(CharSequenceUtil.format("Elasticsearch {} condition value must be Iterable or array", op));
    }

    private List<Object> toRangeValues(Object value) {
        List<Object> values = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            values.addAll(collection);
        } else if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(value, i));
            }
        }
        if (values.size() != 2) {
            throw new GXBusinessException("Elasticsearch between condition requires exactly two values");
        }
        return values;
    }

    default Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        Assert.notNull(updateFields, "Update fields must not be null");
        Assert.notNull(condition, "Condition must not be null");
        if (CollUtil.isEmpty(updateFields) || CollUtil.isEmpty(condition)) {
            return 0;
        }

        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .condition(condition)
                .build();
        Query query = buildQuery(queryParamInnerDto);
        Map<String, Object> params = new HashMap<>(updateFields.size());
        StringBuilder script = new StringBuilder();
        updateFields.forEach(updateField -> {
            if (updateField == null || CharSequenceUtil.isBlank(updateField.getFieldName())) {
                return;
            }
            String paramName = updateField.getParamName();
            validatePainlessFieldName(updateField.getFieldName());
            validatePainlessParamName(paramName);
            Object paramValue = updateField.getParamMap().get(paramName);
            params.put(paramName, paramValue);
            script.append("ctx._source['")
                    .append(updateField.getFieldName())
                    .append("'] = params['")
                    .append(paramName)
                    .append("'];");
        });
        if (params.isEmpty()) {
            return 0;
        }

        UpdateQuery updateQuery = UpdateQuery.builder(query)
                .withScript(script.toString())
                .withParams(params)
                .withLang("painless")
                .build();
        ElasticsearchTemplate elasticsearchTemplate = getElasticsearchTemplate();
        IndexCoordinates indexCoordinates = CharSequenceUtil.isEmpty(tableName)
                ? elasticsearchTemplate.getIndexCoordinatesFor(getGenericClassType())
                : IndexCoordinates.of(tableName);
        ByQueryResponse updateResponse = elasticsearchTemplate.updateByQuery(updateQuery, indexCoordinates);
        return Math.toIntExact(updateResponse.getUpdated());
    }

    default Dict executeQuery(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Assert.notNull(queryParamInnerDto, "Query parameters must not be null");
        rejectUnsupportedGroupBy(queryParamInnerDto);

        Q query = buildQuery(queryParamInnerDto);
        query = buildOrderBy(query, queryParamInnerDto);
        query = buildPageable(query, queryParamInnerDto);
        query = buildSourceFilter(query, queryParamInnerDto);

        ElasticsearchTemplate elasticsearchTemplate = getElasticsearchTemplate();
        Class<?> genericClassType = GXCommonUtils.getGenericClassType((Class<?>) getClass().getGenericInterfaces()[0], 0);

        String indexName = queryParamInnerDto.getTableName();
        SearchHits<?> search = CharSequenceUtil.isEmpty(indexName)
                ? elasticsearchTemplate.search(query, genericClassType)
                : elasticsearchTemplate.search(query, genericClassType, IndexCoordinates.of(indexName));

        String[] methodName = new String[]{queryParamInnerDto.getMethodName()};
        if (CharSequenceUtil.isEmpty(methodName[0])) {
            methodName[0] = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
        }
        CopyOptions copyOptions = ObjectUtil.defaultIfNull(queryParamInnerDto.getCopyOptions(), GXCommonUtils::getDefaultCopyOptions);

        Function<SearchHit<?>, Dict> rowMapper = obj -> {
            Object extraData = Optional.ofNullable(queryParamInnerDto.getExtraData()).orElse(Dict.create());
            return GXCommonUtils.convertSourceToTarget(obj, Dict.class, methodName[0], copyOptions, extraData);
        };

        List<Dict> records = search.getSearchHits().stream().map(rowMapper).collect(Collectors.toList());
        long totalHits = search.getTotalHits();

        return Dict.create().set("totalHits", totalHits).set("records", records);
    }

    @SuppressWarnings("all")
    default Q buildQuery(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Class<BaseQuery> queryClazz = GXCommonUtils.getGenericClassType((Class<?>) getClass().getGenericInterfaces()[0], 1);
        B queryBuilder = buildQueryBuilder(queryParamInnerDto);

        BaseQuery query = ReflectUtil.newInstance(queryClazz, queryBuilder);
        return (Q) query;
    }

    @SuppressWarnings("all")
    default B buildQueryBuilder(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Class<BaseQueryBuilder<Q, B>> queryBuilderClazz = GXCommonUtils.getGenericClassType((Class<?>) getClass().getGenericInterfaces()[0], 2);

        if (CriteriaQueryBuilder.class.isAssignableFrom(queryBuilderClazz)) {
            Criteria criteria = conditions2Criteria(queryParamInnerDto);
            BaseQueryBuilder<Q, B> queryBuilder = ReflectUtil.newInstance(queryBuilderClazz, criteria);
            return (B) queryBuilder;
        }
        BaseQueryBuilder<Q, B> queryBuilder = ReflectUtil.newInstance(queryBuilderClazz);
        return (B) queryBuilder;
    }

    default Q buildPageable(Q query, GXBaseQueryParamInnerDto queryParamInnerDto) {
        int page = NumberUtil.max(Optional.ofNullable(queryParamInnerDto.getPage()).orElse(0) - 1, 0);
        int pageSize = Optional.ofNullable(queryParamInnerDto.getPageSize()).orElse(GXCommonConstant.DEFAULT_MAX_PAGE_SIZE);
        pageSize = NumberUtil.max(pageSize, 1);
        query.setPageable(PageRequest.of(page, pageSize));
        Integer limit = queryParamInnerDto.getLimit();
        if (limit != null && limit > 0) {
            query.setMaxResults(limit);
        }
        return query;
    }

    default Q buildOrderBy(Q query, GXBaseQueryParamInnerDto queryParamInnerDto) {
        Map<String, String> orderByField = queryParamInnerDto.getOrderByField();
        if (!CollUtil.isEmpty(orderByField)) {
            List<Sort.Order> orders = CollUtil.newArrayList();
            orderByField.keySet().forEach(column -> {
                String value = orderByField.get(column);
                try {
                    Sort.Direction direction = Sort.Direction.fromString(value);
                    if (Sort.Direction.DESC.equals(direction)) {
                        orders.add(Sort.Order.desc(column));
                    } else if (Sort.Direction.ASC.equals(direction)) {
                        orders.add(Sort.Order.asc(column));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            });
            if (CollUtil.isNotEmpty(orders)) {
                Sort sort = Sort.by(orders);
                query.addSort(sort);
            }
        }
        return query;
    }

    default Q buildSourceFilter(Q query, GXBaseQueryParamInnerDto queryParamInnerDto) {
        Set<String> columns = queryParamInnerDto.getColumns();
        if (CollUtil.isEmpty(columns) || columns.contains("*")) {
            return query;
        }
        String[] includes = columns.stream()
                .filter(CharSequenceUtil::isNotBlank)
                .map(CharSequenceUtil::toUnderlineCase)
                .toArray(String[]::new);
        if (includes.length > 0) {
            query.addSourceFilter(new FetchSourceFilterBuilder().withIncludes(includes).build());
        }
        return query;
    }

    default Criteria buildCriteria(Criteria criteria) {
        return criteria;
    }

    default Criteria conditions2Criteria(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        Assert.notNull(dbQueryParamInnerDto, "Query parameters must not be null");

        List<GXCondition<?>> conditions = dbQueryParamInnerDto.getCondition();
        Criteria criteria = new Criteria();
        Map<String, String> methodMapping = GXEsCriteriaMethodMappingConstant.METHOD_MAPPING;

        if (CollUtil.isNotEmpty(conditions)) {
            conditions.forEach(condition -> {
                if (condition == null || CharSequenceUtil.isEmpty(condition.getFieldExpression())) {
                    return;
                }

                String fieldName = condition.getFieldExpression();
                Object value = condition.getValue();
                String op = CharSequenceUtil.trim(condition.getOp());

                if (CharSequenceUtil.isBlank(op)) {
                    throw new GXBusinessException("Elasticsearch condition operator must not be blank");
                }
                if (!methodMapping.containsKey(op)) {
                    throw new GXBusinessException(CharSequenceUtil.format("Elasticsearch unsupported condition operator: {}", op));
                }
                criteria.and(buildConditionCriteria(fieldName, op, value));
            });
        }

        return buildCriteria(criteria);
    }

    default ElasticsearchTemplate getElasticsearchTemplate() {
        String contextTemplateName = GXElasticsearchTemplateContext.getTemplateName();
        return getElasticsearchTemplate(CharSequenceUtil.isBlank(contextTemplateName) ? getElasticsearchTemplateName() : contextTemplateName);
    }

    default Class<?> getGenericClassType() {
        return GXCommonUtils.getGenericClassType((Class<?>) getClass().getGenericInterfaces()[0], 0);
    }

    default String getElasticsearchTemplateName() {
        return "primaryElasticsearchTemplate";
    }

    default ElasticsearchTemplate getElasticsearchTemplate(String beanName) {
        if (CharSequenceUtil.isEmpty(beanName)) {
            String contextTemplateName = GXElasticsearchTemplateContext.getTemplateName();
            beanName = CharSequenceUtil.isBlank(contextTemplateName) ? getElasticsearchTemplateName() : contextTemplateName;
        }

        ElasticsearchTemplate elasticsearchTemplate = GXSpringContextUtils.getBean(beanName, ElasticsearchTemplate.class);

        Assert.notNull(elasticsearchTemplate, "ElasticsearchTemplate bean is required -> [" +
                GXSpringContextUtils.getBeans(ElasticsearchTemplate.class).keySet().stream()
                        .map(name -> CharSequenceUtil.format("'{}'", name))
                        .collect(Collectors.joining(",")) +
                "]");

        return elasticsearchTemplate;
    }

    @SuppressWarnings("unchecked")
    private Class<T> getGenericEntityClassType() {
        return (Class<T>) getGenericClassType();
    }

    private List<T> searchContents(Query query) {
        SearchHits<T> searchHits = getElasticsearchTemplate().search(query, getGenericEntityClassType());
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    private Object getEntityId(T entity) {
        String methodName = CharSequenceUtil.format("get{}", CharSequenceUtil.upperFirst("id"));
        return GXCommonUtils.reflectCallObjectMethod(entity, methodName);
    }

    private ElasticsearchOperations getElasticsearchOperations(RefreshPolicy refreshPolicy) {
        ElasticsearchTemplate elasticsearchTemplate = getElasticsearchTemplate();
        return refreshPolicy == null ? elasticsearchTemplate : elasticsearchTemplate.withRefreshPolicy(refreshPolicy);
    }

    private void rejectUnsupportedGroupBy(GXBaseQueryParamInnerDto queryParamInnerDto) {
        if (CollUtil.isNotEmpty(queryParamInnerDto.getGroupByField())) {
            throw new GXBusinessException("Elasticsearch groupByField is not supported");
        }
    }

    private void validatePainlessFieldName(String fieldName) {
        if (!PAINLESS_FIELD_NAME_PATTERN.matcher(fieldName).matches()) {
            throw new GXBusinessException(CharSequenceUtil.format("Invalid Elasticsearch update field name: {}", fieldName));
        }
    }

    private void validatePainlessParamName(String paramName) {
        if (CharSequenceUtil.isBlank(paramName) || !PAINLESS_PARAM_NAME_PATTERN.matcher(paramName).matches()) {
            throw new GXBusinessException(CharSequenceUtil.format("Invalid Elasticsearch update param name: {}", paramName));
        }
    }

    private <R> R withElasticsearchTemplateContext(String elasticsearchTemplateName, Supplier<R> supplier) {
        return GXElasticsearchTemplateContext.withTemplateName(elasticsearchTemplateName, supplier);
    }
}
