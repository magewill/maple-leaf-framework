package cn.maple.elasticsearch.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
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
import cn.maple.elasticsearch.dao.GXElasticsearchDao;
import cn.maple.elasticsearch.model.GXElasticsearchModel;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.BaseQuery;
import org.springframework.data.elasticsearch.core.query.BaseQueryBuilder;

import java.io.Serializable;
import java.util.*;
import java.util.function.Supplier;

public class GXElasticsearchRepository<T extends GXElasticsearchModel, D extends GXElasticsearchDao<T, Q, B, ID>, Q extends BaseQuery, B extends BaseQueryBuilder<Q, B>, ID extends Serializable> implements GXBaseRepository<T, ID> {
    @SuppressWarnings("all")
    @Autowired
    protected D baseDao;

    public <R> R useElasticsearchTemplate(String elasticsearchTemplateName, Supplier<R> supplier) {
        return baseDao.useElasticsearchTemplate(elasticsearchTemplateName, supplier);
    }

    public void useElasticsearchTemplate(String elasticsearchTemplateName, Runnable runnable) {
        baseDao.useElasticsearchTemplate(elasticsearchTemplateName, runnable);
    }

    public <R> Supplier<R> wrapElasticsearchTemplate(Supplier<R> supplier) {
        return baseDao.wrapElasticsearchTemplate(supplier);
    }

    public Runnable wrapElasticsearchTemplate(Runnable runnable) {
        return baseDao.wrapElasticsearchTemplate(runnable);
    }

    public <R> Supplier<R> wrapElasticsearchTemplate(String elasticsearchTemplateName, Supplier<R> supplier) {
        return baseDao.wrapElasticsearchTemplate(elasticsearchTemplateName, supplier);
    }

    public Runnable wrapElasticsearchTemplate(String elasticsearchTemplateName, Runnable runnable) {
        return baseDao.wrapElasticsearchTemplate(elasticsearchTemplateName, runnable);
    }

    @Override
    public ID updateOrCreate(T entity, List<GXCondition<?>> condition) {
        return baseDao.updateOrCreate(entity, condition);
    }

    @Override
    public ID updateOrCreate(T entity) {
        return updateOrCreate(entity, Collections.emptyList());
    }

    @Override
    public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return baseDao.findByCondition(dbQueryParamInnerDto);
    }

    @Override
    public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition) {
        return findByCondition(tableName, condition, CollUtil.newHashSet("*"));
    }

    public List<Dict> findByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).tableNameAlias(tableName).columns(columns).condition(condition).build();
        return findByCondition(queryParamInnerDto);
    }

    @Override
    public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return baseDao.findOneByCondition(dbQueryParamInnerDto);
    }

    @Override
    public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition) {
        return findOneByCondition(tableName, condition, CollUtil.newHashSet("*"));
    }

    @Override
    public Dict findOneByCondition(String tableName, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).tableNameAlias(tableName).columns(columns).condition(condition).build();
        return findOneByCondition(queryParamInnerDto);
    }

    @Override
    public Dict findOneById(String tableName, ID id, Set<String> columns) {
        if (CharSequenceUtil.isBlank(tableName)) {
            Optional<T> data = baseDao.findById(id);
            return data.map(t -> Convert.convert(Dict.class, t)).orElse(null);
        }
        ElasticsearchTemplate elasticsearchTemplate = baseDao.getElasticsearchTemplate();
        Object data = elasticsearchTemplate.get(Convert.toStr(id), baseDao.getGenericClassType(), IndexCoordinates.of(tableName));
        return data == null ? null : Convert.convert(Dict.class, data);
    }

    @Override
    public Dict findOneById(String tableName, ID id) {
        return findOneById(tableName, id, CollUtil.newHashSet("*"));
    }

    @Override
    public GXPaginationResDto<Dict> paginate(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return baseDao.paginate(dbQueryParamInnerDto);
    }

    @Override
    public GXPaginationResDto<Dict> paginate(String tableName, Integer page, Integer pageSize, List<GXCondition<?>> condition, Set<String> columns) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).page(page).pageSize(pageSize).condition(condition).columns(columns).build();
        return paginate(queryParamInnerDto);
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXCondition<?>> condition, Dict extraData) {
        return deleteSoftCondition(tableName, CollUtil.newArrayList(), condition, extraData);
    }

    @Override
    public Integer deleteSoftCondition(String tableName, List<GXUpdateField<?>> updateFieldList, List<GXCondition<?>> condition, Dict extraData) {
        if (CollUtil.isEmpty(updateFieldList)) {
            return 0;
        }
        return updateFieldByCondition(tableName, updateFieldList, condition);
    }

    @Override
    public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
        return baseDao.deleteCondition(tableName, condition);
    }

    public Integer deleteById(ID id) {
        baseDao.deleteById(id);
        return 1;
    }

    @Override
    public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
        GXBaseQueryParamInnerDto queryParamInnerDto = GXBaseQueryParamInnerDto.builder().tableName(tableName).condition(condition).build();
        return ObjectUtil.isNotNull(baseDao.findOneByCondition(queryParamInnerDto));
    }

    @Override
    public boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext constraintValidatorContext) {
        String tableName = CharSequenceUtil.isNotEmpty(validateExistsDto.getTableName()) ? validateExistsDto.getTableName() : getTableName();
        String fieldName = validateExistsDto.getFieldName();
        Object value = validateExistsDto.getValue();

        if (CharSequenceUtil.isBlank(tableName)) {
            throw new GXBusinessException(CharSequenceUtil.format("请指定Elasticsearch索引名称, 验证字段: {}, 验证值: {}", fieldName, value));
        }

        GXCondition<?> condition;
        if (value instanceof Number number) {
            condition = new GXConditionEQ(tableName, fieldName, number);
        } else {
            condition = new GXConditionStrEQ(tableName, fieldName, Convert.toStr(value));
        }
        List<GXCondition<?>> conditions = new ArrayList<>();
        conditions.add(condition);
        Dict originCondition = validateExistsDto.getCondition();
        if (originCondition != null && !originCondition.isEmpty()) {
            for (Map.Entry<String, Object> entry : originCondition.entrySet()) {
                Object conditionValue = entry.getValue();
                if (conditionValue instanceof Number number) {
                    conditions.add(new GXConditionEQ(tableName, entry.getKey(), number));
                } else {
                    conditions.add(new GXConditionStrEQ(tableName, entry.getKey(), Convert.toStr(conditionValue)));
                }
            }
        }
        return checkRecordIsExists(tableName, conditions);
    }

    @Override
    public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
        return baseDao.updateFieldByCondition(tableName, updateFields, condition);
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
        return getIndexName(entity.getClass());
    }

    @Override
    public String getTableName() {
        return getIndexName(GXCommonUtils.getGenericClassType(getClass(), 0));
    }

    protected String getIndexName(Class<?> entityClass) {
        Document document = entityClass.getAnnotation(Document.class);
        if (document == null || CharSequenceUtil.isBlank(document.indexName())) {
            throw new GXBusinessException(CharSequenceUtil.format("{}未配置@Document(indexName)", entityClass.getName()));
        }
        return document.indexName();
    }
}
