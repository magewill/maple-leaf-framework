package cn.maple.mongodb.datasource.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.ddd.repository.GXBaseRepository;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.mongodb.datasource.dao.GXMongoDao;
import cn.maple.mongodb.datasource.model.GXMongoModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import jakarta.validation.ConstraintValidatorContext;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class GXMongoRepository<T extends GXMongoModel, D extends GXMongoDao<T, ID>, ID extends Serializable> implements GXBaseRepository<T, ID> {
    private static final Set<String> ALL_COLUMNS = CollUtil.newHashSet("*");

    @SuppressWarnings("all")
    @Autowired
    protected D baseDao;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Override
    public ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        try {
            T savedEntity = baseDao.save(entity);
            Object id = GXCommonUtils.reflectCallObjectMethod(savedEntity, "getId");
            return (ID) id;
        } catch (Exception e) {
            throw new IllegalStateException("Save MongoDB data failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ID updateOrCreate(T entity) {
        return updateOrCreate(entity, Collections.emptyList());
    }

    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        Query query = buildQuery(dbQueryParamInnerDto);
        applyPage(query, dbQueryParamInnerDto);
        return mongoTemplate.find(query, Document.class, resolveCollectionName(dbQueryParamInnerDto))
                .stream()
                .map(this::toDict)
                .toList();
    }

    @Override
    public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition) {
        return findByCondition(tableName, condition, ALL_COLUMNS);
    }

    @Override
    public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .tableNameAlias(tableName)
                .columns(columns)
                .condition(nullSafeConditions(condition))
                .build();
        return findByCondition(queryParamInnerDto);
    }

    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        Query query = buildQuery(dbQueryParamInnerDto).limit(1);
        Document document = mongoTemplate.findOne(query, Document.class, resolveCollectionName(dbQueryParamInnerDto));
        return document == null ? Dict.create() : toDict(document);
    }

    @Override
    public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition) {
        return findOneByCondition(tableName, condition, ALL_COLUMNS);
    }

    @Override
    public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .tableNameAlias(tableName)
                .columns(columns)
                .condition(nullSafeConditions(condition))
                .extraData(Dict.create())
                .build();
        return findOneByCondition(queryParamInnerDto);
    }

    @Override
    public Dict findOneById(String tableName, ID id, Set<String> columns) {
        if (id == null) {
            return Dict.create();
        }
        if (CharSequenceUtil.isBlank(tableName)) {
            return baseDao.findById(id).map(entity -> Convert.convert(Dict.class, entity)).orElse(Dict.create());
        }
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .columns(columns)
                .condition(Collections.singletonList(buildIdCondition(tableName, id)))
                .build();
        return findOneByCondition(queryParamInnerDto);
    }

    @Override
    public Dict findOneById(String tableName, ID id) {
        return findOneById(tableName, id, ALL_COLUMNS);
    }

    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        Query countQuery = buildQuery(dbQueryParamInnerDto);
        String collectionName = resolveCollectionName(dbQueryParamInnerDto);
        long total = mongoTemplate.count(countQuery, collectionName);

        Query pageQuery = buildQuery(dbQueryParamInnerDto);
        int page = normalizePage(dbQueryParamInnerDto == null ? null : dbQueryParamInnerDto.getPage());
        int pageSize = normalizePageSize(dbQueryParamInnerDto == null ? null : dbQueryParamInnerDto.getPageSize());
        pageQuery.skip((long) (page - 1) * pageSize).limit(pageSize);

        List<Dict> records = mongoTemplate.find(pageQuery, Document.class, collectionName)
                .stream()
                .map(this::toDict)
                .toList();
        return new GXPaginationResDto<>(records, total, pageSize, page);
    }

    @Override
    public GXPaginationResDto<Dict> paginate(String tableName, Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .columns(columns)
                .condition(nullSafeConditions(condition))
                .page(page)
                .pageSize(pageSize)
                .build();
        return paginate(queryParamInnerDto);
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        return deleteSoftCondition(tableName, CollUtil.newArrayList(), condition, extraData);
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        if (CollUtil.isEmpty(condition)) {
            return 0;
        }
        List<GXUpdateField<?>> fields = updateFieldList == null ? new ArrayList<>() : new ArrayList<>(updateFieldList);
        if (CollUtil.isEmpty(fields) && (extraData == null || extraData.isEmpty())) {
            return 0;
        }
        Update update = buildUpdate(fields);
        if (extraData != null) {
            extraData.forEach((key, value) -> update.set(normalizeFieldName(Convert.toStr(key)), value));
        }
        UpdateResult result = mongoTemplate.updateMulti(buildQuery(tableName, condition), update, requireCollectionName(tableName));
        return Math.toIntExact(result.getModifiedCount());
    }

    @Override
    public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            return 0;
        }
        DeleteResult result = mongoTemplate.remove(buildQuery(tableName, condition), requireCollectionName(tableName));
        return Math.toIntExact(result.getDeletedCount());
    }

    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(condition)) {
            return false;
        }
        return mongoTemplate.exists(buildQuery(tableName, condition).limit(1), requireCollectionName(tableName));
    }

    @Override
    public boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext constraintValidatorContext) {
        if (validateExistsDto == null) {
            return false;
        }
        String tableName = CharSequenceUtil.isNotBlank(validateExistsDto.getTableName()) ? validateExistsDto.getTableName() : getTableName();
        String fieldName = validateExistsDto.getFieldName();
        Object value = validateExistsDto.getValue();
        if (CharSequenceUtil.isBlank(tableName) || CharSequenceUtil.isBlank(fieldName)) {
            return false;
        }

        List<GXCondition<?>> conditions = new ArrayList<>();
        conditions.add(buildCondition(tableName, fieldName, value));
        Dict originCondition = validateExistsDto.getCondition();
        if (originCondition != null && !originCondition.isEmpty()) {
            originCondition.forEach((key, conditionValue) -> conditions.add(buildCondition(tableName, Convert.toStr(key), conditionValue)));
        }
        return checkRecordIsExists(tableName, conditions);
    }

    @Override
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        if (CollUtil.isEmpty(updateFields) || CollUtil.isEmpty(condition)) {
            return 0;
        }
        UpdateResult result = mongoTemplate.updateMulti(buildQuery(tableName, condition), buildUpdate(updateFields), requireCollectionName(tableName));
        return Math.toIntExact(result.getModifiedCount());
    }

    @Override
    public String getPrimaryKeyName(T entity) {
        return "id";
    }

    @Override
    public String getPrimaryKeyName() {
        return "id";
    }

    @Override
    public String getTableName(T entity) {
        if (entity == null) {
            return getTableName();
        }
        return getCollectionName(entity.getClass());
    }

    @Override
    public String getTableName() {
        return getCollectionName(GXCommonUtils.getGenericClassType(getClass(), 0));
    }

    public List<GXCondition<?>> getConditionByDict(Dict condition) {
        if (condition == null || condition.isEmpty()) {
            return Collections.emptyList();
        }
        List<GXCondition<?>> conditions = new ArrayList<>();
        condition.forEach((key, value) -> conditions.add(buildCondition(getTableName(), Convert.toStr(key), value)));
        return conditions;
    }

    protected Query buildQuery(String tableName, List<GXCondition<?>> conditions) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder()
                .tableName(tableName)
                .condition(nullSafeConditions(conditions))
                .build();
        return buildQuery(queryParamInnerDto);
    }

    protected Query buildQuery(GXBaseQueryParamInnerDto queryParamInnerDto) {
        Query query = new Query();
        List<GXCondition<?>> conditions = queryParamInnerDto == null ? Collections.emptyList() : nullSafeConditions(queryParamInnerDto.getCondition());
        if (!conditions.isEmpty()) {
            List<Criteria> criteriaList = conditions.stream().map(this::toCriteria).filter(Objects::nonNull).toList();
            if (!criteriaList.isEmpty()) {
                query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
            }
        }
        applyColumns(query, queryParamInnerDto == null ? null : queryParamInnerDto.getColumns());
        applySort(query, queryParamInnerDto == null ? null : queryParamInnerDto.getOrderByField());
        applyLimit(query, queryParamInnerDto == null ? null : queryParamInnerDto.getLimit());
        return query;
    }

    protected Criteria toCriteria(GXCondition<?> condition) {
        String op = condition.getOp();
        if (CharSequenceUtil.isBlank(op)) {
            return null;
        }
        String fieldName = normalizeFieldName(condition.getFieldExpression());
        Object value = condition.getValue();
        Criteria criteria = Criteria.where(fieldName);
        return switch (op.toLowerCase()) {
            case "=" -> criteria.is(value);
            case "!=", "<>" -> criteria.ne(value);
            case ">" -> criteria.gt(value);
            case ">=" -> criteria.gte(value);
            case "<" -> criteria.lt(value);
            case "<=" -> criteria.lte(value);
            case "like" -> criteria.regex(buildLikePattern(condition));
            case "in" -> criteria.in(toCollectionValue(condition));
            case "not in" -> criteria.nin(toCollectionValue(condition));
            case "is" -> criteria.is(null);
            case "is not" -> criteria.ne(null);
            default -> throw new GXBusinessException("Unsupported MongoDB condition op: " + op);
        };
    }

    protected Update buildUpdate(List<GXUpdateField<?>> updateFields) {
        Update update = new Update();
        for (GXUpdateField<?> updateField : updateFields) {
            if (updateField == null) {
                continue;
            }
            update.set(normalizeFieldName(updateField.getFieldName()), resolveUpdateValue(updateField));
        }
        return update;
    }

    protected Object resolveUpdateValue(GXUpdateField<?> updateField) {
        if (updateField.getParamMap().containsKey(updateField.getParamName())) {
            return updateField.getParamMap().get(updateField.getParamName());
        }
        return updateField.getFieldValue();
    }

    protected Object toCollectionValue(GXCondition<?> condition) {
        Object value = condition.getValue();
        if (value instanceof Collection<?>) {
            return value;
        }
        Map<String, Object> paramMap = condition.toSegment().params();
        return paramMap.values();
    }

    protected Pattern buildLikePattern(GXCondition<?> condition) {
        Map<String, Object> paramMap = condition.toSegment().params();
        Object patternValue = paramMap.isEmpty() ? condition.getValue() : paramMap.values().iterator().next();
        String likePattern = Convert.toStr(patternValue);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < likePattern.length(); i++) {
            char ch = likePattern.charAt(i);
            if (ch == '%') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    protected void applyColumns(Query query, Set<String> columns) {
        if (CollUtil.isEmpty(columns) || columns.contains("*")) {
            return;
        }
        columns.stream()
                .filter(CharSequenceUtil::isNotBlank)
                .map(this::normalizeFieldName)
                .forEach(field -> query.fields().include(field));
    }

    protected void applySort(Query query, Map<String, String> orderByField) {
        if (orderByField == null || orderByField.isEmpty()) {
            return;
        }
        List<Sort.Order> orders = orderByField.entrySet().stream()
                .filter(entry -> CharSequenceUtil.isNotBlank(entry.getKey()))
                .map(entry -> new Sort.Order(resolveSortDirection(entry.getValue()), normalizeFieldName(entry.getKey())))
                .toList();
        if (!orders.isEmpty()) {
            query.with(Sort.by(orders));
        }
    }

    protected Sort.Direction resolveSortDirection(String direction) {
        return CharSequenceUtil.equalsIgnoreCase(direction, "desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    protected void applyLimit(Query query, Integer limit) {
        if (limit != null && limit > 0) {
            query.limit(limit);
        }
    }

    protected void applyPage(Query query, GXBaseQueryParamInnerDto queryParamInnerDto) {
        if (queryParamInnerDto == null || queryParamInnerDto.getPage() == null || queryParamInnerDto.getPageSize() == null) {
            return;
        }
        int page = normalizePage(queryParamInnerDto.getPage());
        int pageSize = normalizePageSize(queryParamInnerDto.getPageSize());
        query.skip((long) (page - 1) * pageSize).limit(pageSize);
    }

    protected int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    protected int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : pageSize;
    }

    protected String resolveCollectionName(GXBaseQueryParamInnerDto queryParamInnerDto) {
        if (queryParamInnerDto != null && CharSequenceUtil.isNotBlank(queryParamInnerDto.getTableName())) {
            return queryParamInnerDto.getTableName();
        }
        return getTableName();
    }

    protected String requireCollectionName(String tableName) {
        if (CharSequenceUtil.isBlank(tableName)) {
            throw new GXBusinessException("MongoDB collection name must not be blank");
        }
        return tableName;
    }

    protected String getCollectionName(Class<?> entityClass) {
        org.springframework.data.mongodb.core.mapping.Document document =
                entityClass.getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class);
        if (document != null && CharSequenceUtil.isNotBlank(document.collection())) {
            return document.collection();
        }
        if (document != null && CharSequenceUtil.isNotBlank(document.value())) {
            return document.value();
        }
        return entityClass.getSimpleName().toLowerCase();
    }

    protected String normalizeFieldName(String fieldName) {
        if (CharSequenceUtil.isBlank(fieldName)) {
            throw new GXBusinessException("MongoDB field name must not be blank");
        }
        String normalized = CharSequenceUtil.toUnderlineCase(fieldName);
        return "id".equals(normalized) ? "_id" : normalized;
    }

    protected List<GXCondition<?>> nullSafeConditions(List<GXCondition<?>> condition) {
        return condition == null ? Collections.emptyList() : condition;
    }

    protected GXCondition<?> buildIdCondition(String tableName, ID id) {
        return buildCondition(tableName, "id", id);
    }

    protected GXCondition<?> buildCondition(String tableName, String fieldName, Object value) {
        if (value instanceof Number number) {
            return new GXConditionEQ(tableName, fieldName, number);
        }
        return new GXConditionStrEQ(tableName, fieldName, Convert.toStr(value));
    }

    protected Dict toDict(Document document) {
        Dict dict = Dict.create();
        document.forEach((key, value) -> dict.set("_id".equals(key) ? "id" : key, value));
        return dict;
    }
}
