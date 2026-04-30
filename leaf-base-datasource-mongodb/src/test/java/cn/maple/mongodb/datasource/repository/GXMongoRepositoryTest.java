package cn.maple.mongodb.datasource.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.mongodb.datasource.context.GXMongoTemplateContext;
import cn.maple.mongodb.datasource.dao.GXMongoDao;
import cn.maple.mongodb.datasource.model.GXMongoModel;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GXMongoRepositoryTest {
    private final TestRepository repository = new TestRepository();

    @AfterEach
    void tearDown() {
        GXMongoTemplateContext.clear();
    }

    @Test
    void shouldBuildQueryWithColumnsSortLimitAndNormalizedId() {
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .tableName("test_collection")
                .columns(CollUtil.newHashSet("id", "userName"))
                .condition(List.of(new GXConditionEQ("test_collection", "id", 7)))
                .orderByField(Map.of("createdAt", "desc"))
                .limit(3)
                .build();

        Query query = repository.publicBuildQuery(queryParam);
        Document queryObject = query.getQueryObject();
        Document fieldsObject = query.getFieldsObject();
        Document sortObject = query.getSortObject();

        assertEquals(7, ((Document) ((List<?>) queryObject.get("$and")).getFirst()).get("_id"));
        assertEquals(1, fieldsObject.get("_id"));
        assertEquals(1, fieldsObject.get("user_name"));
        assertEquals(-1, sortObject.get("created_at"));
        assertEquals(3, query.getLimit());
    }

    @Test
    void shouldIgnoreNullConditionsWhenBuildingQuery() {
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .condition(new ArrayList<>())
                .build();
        queryParam.getCondition().add(null);
        queryParam.getCondition().add(new GXConditionStrEQ("test_collection", "name", "neo"));

        Query query = repository.publicBuildQuery(queryParam);

        assertFalse(query.getQueryObject().isEmpty());
    }

    @Test
    void shouldBuildCollectionCriteriaForInOperator() {
        GXCondition<?> condition = new TestCondition("status", "in", List.of("new", "done"));

        Document criteriaObject = repository.publicToCriteria(condition).getCriteriaObject();
        Document statusCriteria = (Document) criteriaObject.get("status");

        assertEquals(List.of("new", "done"), statusCriteria.get("$in"));
    }

    @Test
    void shouldBuildLikePatternWithoutTreatingRegexCharactersAsWildcards() {
        Pattern pattern = repository.publicBuildLikePattern(new TestCondition("name", "like", "a.c%"));

        assertTrue(pattern.matcher("a.c-value").matches());
        assertFalse(pattern.matcher("abc-value").matches());
    }

    @Test
    void shouldBuildUpdateFromFieldValueWhenParamMapDoesNotContainNullValue() {
        Update update = repository.publicBuildUpdate(List.of(new TestUpdateField("deletedAt", null)));

        assertTrue(update.getUpdateObject().containsKey("$set"));
        assertTrue(((Document) update.getUpdateObject().get("$set")).containsKey("deleted_at"));
    }

    @Test
    void shouldMapMongoIdToServiceIdInDict() {
        Dict dict = repository.publicToDict(new Document("_id", "abc").append("user_name", "neo"));

        assertEquals("abc", dict.getStr("id"));
        assertEquals("neo", dict.getStr("user_name"));
    }

    @Test
    void shouldResolveDocumentCollectionNameBeforeClassNameFallback() {
        assertEquals("annotated_collection", repository.publicGetCollectionName(AnnotatedModel.class));
        assertEquals("plainmodel", repository.publicGetCollectionName(PlainModel.class));
    }

    @Test
    void shouldThrowWhenCollectionNameIsBlank() {
        assertThrows(GXBusinessException.class, () -> repository.publicRequireCollectionName(""));
    }

    @Test
    void shouldSwitchAndRestoreMongoTemplateContext() {
        try (GXMongoRepository.MongoTemplateScope ignored = repository.switchMongoTemplate("")) {
            assertEquals("", GXMongoTemplateContext.peek());
        }

        assertNull(GXMongoTemplateContext.peek());
    }

    @Test
    void shouldResolveMongoTemplateFromMapBeforeBeanFactory() {
        MongoTemplate defaultTemplate = mock(MongoTemplate.class);
        MongoTemplate mappedTemplate = mock(MongoTemplate.class);
        MongoTemplate beanTemplate = mock(MongoTemplate.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("beanMongoTemplate", beanTemplate);
        repository.mongoTemplate = defaultTemplate;
        repository.mongoTemplateMap = Map.of("mappedMongoTemplate", mappedTemplate);
        repository.beanFactory = beanFactory;

        assertSame(defaultTemplate, repository.getMongoTemplate(""));
        assertSame(mappedTemplate, repository.getMongoTemplate("mappedMongoTemplate"));
        assertSame(beanTemplate, repository.getMongoTemplate("beanMongoTemplate"));
        assertThrows(GXBusinessException.class, () -> repository.getMongoTemplate("missingMongoTemplate"));
    }

    private static class TestRepository extends GXMongoRepository<TestModel, TestDao, String> {
        private Query publicBuildQuery(GXBaseQueryParamInnerDto queryParamInnerDto) {
            return buildQuery(queryParamInnerDto);
        }

        private org.springframework.data.mongodb.core.query.Criteria publicToCriteria(GXCondition<?> condition) {
            return toCriteria(condition);
        }

        private Pattern publicBuildLikePattern(GXCondition<?> condition) {
            return buildLikePattern(condition);
        }

        private Update publicBuildUpdate(List<GXUpdateField<?>> updateFields) {
            return buildUpdate(updateFields);
        }

        private Dict publicToDict(Document document) {
            return toDict(document);
        }

        private String publicGetCollectionName(Class<?> entityClass) {
            return getCollectionName(entityClass);
        }

        private String publicRequireCollectionName(String tableName) {
            return requireCollectionName(tableName);
        }
    }

    private interface TestDao extends GXMongoDao<TestModel, String> {
    }

    @org.springframework.data.mongodb.core.mapping.Document(collection = "annotated_collection")
    private static class AnnotatedModel extends GXMongoModel {
    }

    private static class PlainModel extends GXMongoModel {
    }

    private static class TestModel extends GXMongoModel {
    }

    private static class TestUpdateField extends GXUpdateField<Object> {
        private TestUpdateField(String fieldName, Object value) {
            super("", fieldName, value);
        }

        @Override
        public Object getFieldValue() {
            return value;
        }
    }

    private static class TestCondition extends GXCondition<Object> {
        private final String op;

        private TestCondition(String fieldName, String op, Object value) {
            super("test_collection", fieldName, value);
            this.op = op;
        }

        @Override
        public String getOp() {
            return op;
        }

        @Override
        public GXConditionSegment toSegment() {
            Object conditionValue = getValue();
            return new GXConditionSegment("", conditionValue instanceof Collection<?> values
                    ? Map.of("value", values)
                    : Map.of("value", conditionValue));
        }

        @Override
        public Object getFieldValue() {
            return getValue();
        }

        @Override
        public Object getFieldOriginalValue() {
            return getValue();
        }
    }
}
