package cn.maple.elasticsearch.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.res.GXBaseDBResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.elasticsearch.config.GXElasticsearchBeanDefinitionRegistryPostProcessor;
import cn.maple.elasticsearch.dao.GXElasticsearchDao;
import cn.maple.elasticsearch.model.GXElasticsearchModel;
import cn.maple.elasticsearch.properties.GXElasticsearchSourceProperties;
import cn.maple.elasticsearch.properties.local.GXLocalElasticsearchProperties;
import cn.maple.elasticsearch.repository.GXElasticsearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.annotation.Id;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.CriteriaQueryBuilder;
import org.springframework.data.elasticsearch.core.query.MoreLikeThisQuery;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.mapping.model.SimpleTypeHolder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXElasticsearchServiceImplTest {
    private final CapturingRepository repository = new CapturingRepository();

    private final TestService service = new TestService(repository);

    private static <T> T defaultMethodProxy(Class<T> type) {
        return defaultMethodProxy(type, null);
    }

    private static <T> T defaultMethodProxy(Class<T> type, ElasticsearchTemplate elasticsearchTemplate) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (target, method, args) -> {
            if (elasticsearchTemplate != null && method.getName().equals("getElasticsearchTemplate") && method.getParameterCount() == 0) {
                return elasticsearchTemplate;
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(target, method, args);
            }
            throw new UnsupportedOperationException(method.toString());
        });
        return type.cast(proxy);
    }

    @Test
    void findByConditionFillsDefaultIndexNameAndMapsRows() {
        repository.findByConditionResult = List.of(Dict.create().set("name", "maple"));
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder().build();

        List<TestResDto> result = service.findByCondition(queryParam);

        assertThat(queryParam.getTableName()).isNull();
        assertThat(repository.lastQueryParam).isNotSameAs(queryParam);
        assertThat(repository.lastQueryParam.getTableName()).isEqualTo("test_index");
        assertThat(result).extracting(TestResDto::getName).containsExactly("maple");
    }

    @Test
    void findOneByConditionFillsDefaultIndexNameBeforeCallingRepository() {
        repository.findOneByConditionResult = Dict.create().set("name", "leaf");
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder().build();

        String name = service.findOneByCondition(queryParam, dict -> dict.getStr("name"));

        assertThat(name).isEqualTo("leaf");
        assertThat(queryParam.getTableName()).isNull();
        assertThat(repository.lastQueryParam).isNotSameAs(queryParam);
        assertThat(repository.lastQueryParam.getTableName()).isEqualTo("test_index");
    }

    @Test
    void deleteConditionRejectsEmptyConditions() {
        assertThatThrownBy(() -> service.deleteCondition("test_index", Collections.emptyList()))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Condition cannot be empty");
    }

    @Test
    void daoDeleteConditionRejectsEmptyConditionsBeforeBuildingDeleteAllQuery() {
        TestDao dao = defaultMethodProxy(TestDao.class);

        assertThatThrownBy(() -> dao.deleteCondition("test_index", Collections.emptyList()))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Condition cannot be empty");
    }

    @Test
    void unsupportedUnionAndMapperMethodsFailLoudly() {
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder().build();

        assertThatThrownBy(() -> service.paginate(queryParam, Collections.emptyList(), GXUnionTypeEnums.UNION))
                .isInstanceOf(GXBusinessException.class);
        assertThatThrownBy(() -> service.findByCondition(queryParam, Collections.emptyList(), GXUnionTypeEnums.UNION))
                .isInstanceOf(GXBusinessException.class);
        assertThatThrownBy(() -> service.findOneByCondition(queryParam, Collections.emptyList(), GXUnionTypeEnums.UNION))
                .isInstanceOf(GXBusinessException.class);
        assertThatThrownBy(() -> service.findByCallMapperMethod("selectByName"))
                .isInstanceOf(GXBusinessException.class);
        assertThatThrownBy(() -> service.findByCallMapperMethod("selectByName", "customerProcess", CopyOptions.create()))
                .isInstanceOf(GXBusinessException.class);
        assertThatThrownBy(() -> service.findOneByCallMapperMethod("selectOne"))
                .isInstanceOf(GXBusinessException.class);
        assertThatThrownBy(() -> service.findOneByCallMapperMethod("selectOne", "customerProcess", CopyOptions.create()))
                .isInstanceOf(GXBusinessException.class);
    }

    @Test
    void repositoryDeleteSoftConditionConvertsExtraDataToUpdateFields() {
        List<GXCondition<?>> conditions = List.of(new GXConditionStrEQ("", "id", "1"));
        Dict extraData = Dict.create().set("deletedBy", "tester").set("deletedFlag", 1);

        Integer updated = repository.deleteSoftCondition("test_index", conditions, extraData);

        assertThat(updated).isEqualTo(2);
        assertThat(repository.lastTableName).isEqualTo("test_index");
        assertThat(repository.lastConditions).isSameAs(conditions);
        assertThat(repository.lastUpdateFields).hasSize(2);
        assertThat(repository.lastUpdateFields)
                .extracting(GXUpdateField::getFieldName)
                .containsExactlyInAnyOrder("deleted_by", "deleted_flag");
    }

    @Test
    void repositoryDeleteSoftConditionReturnsZeroWhenNoUpdateFieldExists() {
        Integer updated = repository.deleteSoftCondition(
                "test_index",
                List.of(new GXConditionStrEQ("", "id", "1")),
                Dict.create()
        );

        assertThat(updated).isZero();
        assertThat(repository.lastUpdateFields).isEmpty();
    }

    @Test
    void serviceUpdateFieldByConditionDoesNotProbeExistenceBeforeUpdating() {
        repository.updateFieldByConditionResult = 1;
        List<GXCondition<?>> conditions = List.of(new GXConditionStrEQ("", "id", "1"));
        List<GXUpdateField<?>> updates = List.of(new TestUpdateField("status", "active"));

        Integer updated = service.updateFieldByCondition("test_index", updates, conditions);

        assertThat(updated).isEqualTo(1);
        assertThat(repository.updateFieldByConditionCalls).isEqualTo(1);
        assertThat(repository.checkRecordCalls).isZero();
    }

    @Test
    void serviceUpdateFieldByConditionReturnsNotFoundWhenRepositoryUpdatesNothing() {
        repository.updateFieldByConditionResult = 0;
        List<GXCondition<?>> conditions = List.of(new GXConditionStrEQ("", "id", "1"));
        List<GXUpdateField<?>> updates = List.of(new TestUpdateField("status", "active"));

        Integer updated = service.updateFieldByCondition("test_index", updates, conditions);

        assertThat(updated).isEqualTo(GXCommonConstant.DB_RECORD_NOT_FOUND);
    }

    @Test
    void daoRejectsUnsupportedConditionOperator() {
        TestDao dao = defaultMethodProxy(TestDao.class);

        assertThatThrownBy(() -> dao.conditions2Criteria(GXBaseQueryParamInnerDto.builder()
                        .condition(List.of(new TestCondition("name", "contains", "leaf")))
                        .build()))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("unsupported condition operator");
    }

    @Test
    void daoRejectsUnsafeUpdateFieldName() {
        TestDao dao = defaultMethodProxy(TestDao.class);

        assertThatThrownBy(() -> dao.updateFieldByCondition(
                "test_index",
                List.of(new TestUpdateField("status['x']", "active")),
                List.of(new GXConditionStrEQ("", "id", "1"))))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Invalid Elasticsearch update field name");
    }

    @Test
    void daoUpdateFieldByConditionUsesConstantPainlessScriptAndUpdateParams() {
        ElasticsearchTemplate elasticsearchTemplate = mock(ElasticsearchTemplate.class);
        when(elasticsearchTemplate.updateByQuery(any(UpdateQuery.class), any()))
                .thenReturn(ByQueryResponse.builder().withUpdated(3).build());
        TestDao dao = defaultMethodProxy(TestDao.class, elasticsearchTemplate);

        Integer updated = dao.updateFieldByCondition(
                "test_index",
                List.of(new TestUpdateField("userName", "maple"), new TestUpdateField("status", "active")),
                List.of(new GXConditionStrEQ("", "id", "1")));

        ArgumentCaptor<UpdateQuery> updateQueryCaptor = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(elasticsearchTemplate).updateByQuery(updateQueryCaptor.capture(), any());
        UpdateQuery updateQuery = updateQueryCaptor.getValue();
        assertThat(updated).isEqualTo(3);
        assertThat(updateQuery.getScript())
                .isEqualTo("for (update in params._updates) { ctx._source[update['field']] = update['value']; }");
        assertThat(updateQuery.getLang()).isEqualTo("painless");
        assertThat(updateQuery.getParams()).containsOnlyKeys("_updates");
        assertThat((List<Map<String, Object>>) updateQuery.getParams().get("_updates"))
                .containsExactly(
                        Map.of("field", "user_name", "value", "maple"),
                        Map.of("field", "status", "value", "active"));
    }

    @Test
    void daoSearchSimilarUsesSpringDataIdentifierMetadataBeforeIdGetterFallback() {
        ElasticsearchTemplate elasticsearchTemplate = mock(ElasticsearchTemplate.class);
        MappingElasticsearchConverter converter = new MappingElasticsearchConverter(new SimpleElasticsearchMappingContext());
        SearchHits<TestEntityWithCustomId> searchHits = mock(SearchHits.class);
        when(searchHits.getSearchHits()).thenReturn(Collections.emptyList());
        when(searchHits.getTotalHits()).thenReturn(0L);
        when(elasticsearchTemplate.getElasticsearchConverter()).thenReturn(converter);
        when(elasticsearchTemplate.search(any(MoreLikeThisQuery.class), eq(TestEntityWithCustomId.class))).thenReturn(searchHits);
        CustomIdDao dao = defaultMethodProxy(CustomIdDao.class, elasticsearchTemplate);
        TestEntityWithCustomId entity = new TestEntityWithCustomId();
        entity.setDocId("doc-42");

        dao.searchSimilar(entity, new String[]{"name"}, org.springframework.data.domain.PageRequest.of(0, 10));

        ArgumentCaptor<MoreLikeThisQuery> queryCaptor = ArgumentCaptor.forClass(MoreLikeThisQuery.class);
        verify(elasticsearchTemplate).search(queryCaptor.capture(), eq(TestEntityWithCustomId.class));
        assertThat(queryCaptor.getValue().getId()).isEqualTo("doc-42");
    }

    @Test
    void daoAppliesSourceFilterAndLimitFromQueryParam() {
        TestDao dao = defaultMethodProxy(TestDao.class);
        GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
                .columns(CollUtil.newHashSet("userName", "status"))
                .limit(7)
                .build();
        CriteriaQuery query = new CriteriaQueryBuilder(new org.springframework.data.elasticsearch.core.query.Criteria()).build();

        dao.buildPageable(query, queryParam);
        dao.buildSourceFilter(query, queryParam);

        assertThat(query.getMaxResults()).isEqualTo(7);
        assertThat(query.getSourceFilter()).isNotNull();
        assertThat(query.getSourceFilter().getIncludes()).containsExactlyInAnyOrder("userName", "user_name", "status");
    }

    @Test
    void daoRejectsGroupByFieldForUnsupportedQueryShape() {
        TestDao dao = defaultMethodProxy(TestDao.class);

        assertThatThrownBy(() -> dao.executeQuery(GXBaseQueryParamInnerDto.builder()
                        .groupByField(Set.of("status"))
                        .build()))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("groupByField is not supported");
    }

    @Test
    void registeredBeansPreferDynamicOperationsButStillExposeDefaultTemplateByName() {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("elasticsearch.datasource.primary.uris[0]", "http://localhost:9200");
        properties.put("elasticsearch.datasource.primary.primary", "true");
        properties.put("elasticsearch.datasource.secondary.uris[0]", "http://localhost:9201");
        properties.put("elasticsearch.datasource.secondary.primary", "false");
        environment.getPropertySources().addFirst(new MapPropertySource("test-elasticsearch", properties));

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
            GXElasticsearchBeanDefinitionRegistryPostProcessor postProcessor = new GXElasticsearchBeanDefinitionRegistryPostProcessor();
            postProcessor.setEnvironment(environment);
            postProcessor.postProcessBeanDefinitionRegistry(context);
            context.registerBean(ElasticsearchInjectionTarget.class);

            context.refresh();

            ElasticsearchInjectionTarget target = context.getBean(ElasticsearchInjectionTarget.class);
            assertThat(target.elasticsearchOperations).isSameAs(context.getBean("elasticsearchOperations"));
            assertThat(target.elasticsearchOperations).isNotInstanceOf(ElasticsearchTemplate.class);
            assertThat(target.elasticsearchTemplate).isSameAs(context.getBean("elasticsearchTemplate"));
            assertThat(target.templateWithNonDefaultFieldName).isSameAs(context.getBean("elasticsearchTemplate"));
            assertThat(target.elasticsearchTemplate).isSameAs(context.getBean("primaryElasticsearchTemplate"));
            assertThat(context.getBean("elasticsearchCustomConversions")).isInstanceOf(ElasticsearchCustomConversions.class);
            assertThat(context.getBean("elasticsearchSimpleTypeHolder")).isInstanceOf(SimpleTypeHolder.class);
            assertThat(context.getBean("elasticsearchSourceProperties")).isInstanceOf(GXLocalElasticsearchProperties.class);
            assertThat(context.getBean(GXElasticsearchSourceProperties.class)).isSameAs(context.getBean("elasticsearchSourceProperties"));
            assertThat(context.getBean(GXLocalElasticsearchProperties.class)).isSameAs(context.getBean("elasticsearchSourceProperties"));
        }
    }

    @Test
    void registeredBeansFailWhenDatasourcePropertiesWereNotLoaded() {
        StandardEnvironment environment = new StandardEnvironment();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            GXElasticsearchBeanDefinitionRegistryPostProcessor postProcessor = new GXElasticsearchBeanDefinitionRegistryPostProcessor();
            postProcessor.setEnvironment(environment);
            assertThatThrownBy(() -> postProcessor.postProcessBeanDefinitionRegistry(context))
                    .isInstanceOf(GXBusinessException.class)
                    .hasMessageContaining("No valid Elasticsearch datasource configuration found");
        }
    }

    @Test
    void registeredBeansReuseUserProvidedElasticsearchCustomConversions() {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("elasticsearch.datasource.primary.uris[0]", "http://localhost:9200");
        properties.put("elasticsearch.datasource.primary.primary", "true");
        environment.getPropertySources().addFirst(new MapPropertySource("test-elasticsearch", properties));

        ElasticsearchCustomConversions customConversions = new ElasticsearchCustomConversions(Collections.emptyList());

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
            context.registerBean("elasticsearchCustomConversions", ElasticsearchCustomConversions.class, () -> customConversions);

            GXElasticsearchBeanDefinitionRegistryPostProcessor postProcessor = new GXElasticsearchBeanDefinitionRegistryPostProcessor();
            postProcessor.setEnvironment(environment);
            postProcessor.postProcessBeanDefinitionRegistry(context);

            context.refresh();

            assertThat(context.getBean("elasticsearchCustomConversions")).isSameAs(customConversions);
            assertThat(context.getBean("elasticsearchSimpleTypeHolder"))
                    .isSameAs(customConversions.getSimpleTypeHolder());
        }
    }

    @Test
    void registeredBeansKeepDynamicOperationsPrimaryWhenPrimaryTemplateIsRegularCandidate() {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("elasticsearch.datasource.primary.uris[0]", "http://localhost:9200");
        properties.put("elasticsearch.datasource.primary.primary", "true");
        properties.put("elasticsearch.datasource.secondary.uris[0]", "http://localhost:9201");
        properties.put("elasticsearch.datasource.secondary.primary", "false");
        environment.getPropertySources().addFirst(new MapPropertySource("test-elasticsearch", properties));

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            GXElasticsearchBeanDefinitionRegistryPostProcessor postProcessor = new GXElasticsearchBeanDefinitionRegistryPostProcessor();
            postProcessor.setEnvironment(environment);
            postProcessor.postProcessBeanDefinitionRegistry(context);
            context.refresh();

            assertThat(context.getBean(ElasticsearchOperations.class)).isSameAs(context.getBean("elasticsearchOperations"));
            assertThat(context.getBean(ElasticsearchTemplate.class)).isSameAs(context.getBean("elasticsearchTemplate"));
        }
    }

    interface TestDao extends GXElasticsearchDao<TestEntity, CriteriaQuery, CriteriaQueryBuilder, String> {
    }

    interface CustomIdDao extends GXElasticsearchDao<TestEntityWithCustomId, CriteriaQuery, CriteriaQueryBuilder, String> {
    }

    static class TestService extends GXElasticsearchServiceImpl<CapturingRepository, TestEntity, TestDao, CriteriaQuery, CriteriaQueryBuilder, TestResDto, String> {
        TestService(CapturingRepository repository) {
            this.repository = repository;
        }
    }

    static class CapturingRepository extends GXElasticsearchRepository<TestEntity, TestDao, CriteriaQuery, CriteriaQueryBuilder, String> {
        private GXBaseQueryParamInnerDto lastQueryParam;

        private List<Dict> findByConditionResult = new ArrayList<>();

        private Dict findOneByConditionResult;

        private String lastTableName;

        private List<GXUpdateField<?>> lastUpdateFields = new ArrayList<>();

        private List<GXCondition<?>> lastConditions = new ArrayList<>();

        private Integer updateFieldByConditionResult;

        private Integer updateFieldByConditionCalls = 0;

        private Integer checkRecordCalls = 0;

        @Override
        public List<Dict> findByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
            lastQueryParam = dbQueryParamInnerDto;
            return findByConditionResult;
        }

        @Override
        public Dict findOneByCondition(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
            lastQueryParam = dbQueryParamInnerDto;
            return findOneByConditionResult;
        }

        @Override
        public Integer updateFieldByCondition(String tableName, List<GXUpdateField<?>> updateFields, List<GXCondition<?>> condition) {
            lastTableName = tableName;
            lastUpdateFields = updateFields;
            lastConditions = condition;
            updateFieldByConditionCalls++;
            return updateFieldByConditionResult == null ? CollUtil.size(updateFields) : updateFieldByConditionResult;
        }

        @Override
        public Integer deleteCondition(String tableName, List<GXCondition<?>> condition) {
            lastTableName = tableName;
            lastConditions = condition;
            return CollUtil.size(condition);
        }

        @Override
        public boolean checkRecordIsExists(String tableName, List<GXCondition<?>> condition) {
            checkRecordCalls++;
            return true;
        }

        @Override
        public String getTableName() {
            return "test_index";
        }
    }

    static class ElasticsearchInjectionTarget {
        @Autowired
        private ElasticsearchOperations elasticsearchOperations;

        @Autowired
        private ElasticsearchTemplate elasticsearchTemplate;

        @Autowired
        private ElasticsearchTemplate templateWithNonDefaultFieldName;
    }

    @Document(indexName = "test_index")
    static class TestEntity extends GXElasticsearchModel {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    static class TestResDto extends GXBaseDBResDto {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Document(indexName = "test_index")
    static class TestEntityWithCustomId extends GXElasticsearchModel {
        @Id
        private String docId;

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }
    }

    static class TestCondition extends GXCondition<String> {
        private final String op;

        TestCondition(String fieldExpression, String op, String value) {
            super(fieldExpression, value);
            this.op = op;
        }

        @Override
        public String getOp() {
            return op;
        }

        @Override
        public String getFieldValue() {
            Object value = getValue();
            return value == null ? null : value.toString();
        }

        @Override
        public String getFieldOriginalValue() {
            return getFieldValue();
        }
    }

    static class TestUpdateField extends GXUpdateField<String> {
        TestUpdateField(String fieldName, String value) {
            super("", fieldName, value);
        }

        @Override
        public String getFieldValue() {
            return (String) value;
        }
    }

}
