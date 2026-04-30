package cn.maple.mongodb.datasource.config;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GXMongoBeanDefinitionRegistryPostProcessorTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestInjectionConfiguration.class)
            .withPropertyValues(
                    "mongodb.datasource.primary.uri=mongodb://localhost:27017/primary",
                    "mongodb.datasource.primary.primary=true",
                    "mongodb.datasource.primary.bean-name=primaryMongoTemplate",
                    "mongodb.datasource.report.uri=mongodb://localhost:27017/report",
                    "mongodb.datasource.report.bean-name=reportMongoTemplate");

    @Test
    void shouldRegisterPrimaryCandidatesForStandardMongoInjectionPoints() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(MongoClient.class)).hasSize(2);
            assertThat(context.getBeansOfType(MongoDatabaseFactory.class)).hasSize(3);
            assertThat(context.getBeansOfType(MongoTemplate.class)).hasSize(3);
            assertThat(context.getBeansOfType(MongoOperations.class)).hasSize(3);

            assertThat(context.getBean(MongoClient.class)).isSameAs(context.getBean("mongoClient"));
            assertThat(context.getBean(MongoDatabaseFactory.class)).isSameAs(context.getBean("mongoDatabaseFactory"));
            assertThat(context.getBean(MongoTemplate.class)).isSameAs(context.getBean("mongoTemplate"));
            assertThat(context.getBean(MongoOperations.class)).isSameAs(context.getBean("mongoOperations"));
            assertThat(context.getBean("mongoTemplate")).isSameAs(context.getBean("dynamicMongoTemplate"));

            MongoInjectionTarget injectionTarget = context.getBean(MongoInjectionTarget.class);
            assertThat(injectionTarget.mongoClient).isSameAs(context.getBean("mongoClient"));
            assertThat(injectionTarget.mongoDatabaseFactory).isSameAs(context.getBean("mongoDatabaseFactory"));
            assertThat(injectionTarget.mongoTemplate).isSameAs(context.getBean("mongoTemplate"));
            assertThat(injectionTarget.mongoOperations).isSameAs(context.getBean("mongoOperations"));
        });
    }

    @Test
    void shouldExposeEveryConcreteMongoTemplateForMapInjection() {
        contextRunner.run(context -> {
            Map<String, MongoTemplate> mongoTemplates = context.getBeansOfType(MongoTemplate.class);

            assertThat(mongoTemplates)
                    .containsKeys("dynamicMongoTemplate", "primaryMongoTemplate", "reportMongoTemplate")
                    .doesNotContainKeys("mongoTemplate", "mongoOperations");
        });
    }

    @Test
    void shouldFailFastWhenBeanNameConflictsWithGeneratedNames() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestInjectionConfiguration.class)
                .withPropertyValues(
                        "mongodb.datasource.primary.uri=mongodb://localhost:27017/primary",
                        "mongodb.datasource.primary.primary=true",
                        "mongodb.datasource.primary.bean-name=reportMongoClient",
                        "mongodb.datasource.report.uri=mongodb://localhost:27017/report")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailFastWhenPrimaryDatasourceIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestInjectionConfiguration.class)
                .withPropertyValues(
                        "mongodb.datasource.primary.uri=mongodb://localhost:27017/primary",
                        "mongodb.datasource.report.uri=mongodb://localhost:27017/report")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailFastWhenMultiplePrimaryDatasourcesAreConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestInjectionConfiguration.class)
                .withPropertyValues(
                        "mongodb.datasource.primary.uri=mongodb://localhost:27017/primary",
                        "mongodb.datasource.primary.primary=true",
                        "mongodb.datasource.report.uri=mongodb://localhost:27017/report",
                        "mongodb.datasource.report.primary=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({GXMongoBeanDefinitionRegistryPostProcessor.class, MongoInjectionTarget.class})
    static class TestInjectionConfiguration {
    }

    static class MongoInjectionTarget {
        @Autowired
        private MongoClient mongoClient;

        @Autowired
        private MongoDatabaseFactory mongoDatabaseFactory;

        @Autowired
        private MongoTemplate mongoTemplate;

        @Autowired
        private MongoOperations mongoOperations;
    }
}
