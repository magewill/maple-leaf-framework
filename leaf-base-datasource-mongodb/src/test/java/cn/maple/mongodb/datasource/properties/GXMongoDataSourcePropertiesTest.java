package cn.maple.mongodb.datasource.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class GXMongoDataSourcePropertiesTest {
    @Test
    void shouldKeepPrimaryDefaultFalse() {
        GXMongoDataSourceProperties properties = new GXMongoDataSourceProperties();

        assertThat(properties.getPrimary()).isFalse();
    }

    @Test
    void shouldBindBeanNameAndPrimaryFromMongoDatasourceProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("mongodb.datasource.primary.uri", "mongodb://localhost:27017/primary")
                .withProperty("mongodb.datasource.primary.bean-name", "primaryMongoTemplate")
                .withProperty("mongodb.datasource.primary.primary", "true");

        Map<String, GXMongoDataSourceProperties> datasource = Binder.get(environment)
                .bind("mongodb.datasource", Bindable.mapOf(String.class, GXMongoDataSourceProperties.class))
                .orElseThrow(() -> new IllegalStateException("mongodb.datasource binding failed"));

        assertThat(datasource).containsOnlyKeys("primary");
        assertThat(datasource.get("primary").getUri()).isEqualTo("mongodb://localhost:27017/primary");
        assertThat(datasource.get("primary").getBeanName()).isEqualTo("primaryMongoTemplate");
        assertThat(datasource.get("primary").getPrimary()).isTrue();
    }

    @Test
    void shouldReturnImmutableEmptyDatasourceByDefault() {
        GXMongoDynamicDataSourceProperties properties = new GXMongoDynamicDataSourceProperties() {
            @Override
            public void setDatasource(Map<String, GXMongoDataSourceProperties> datasource) {
            }
        };

        assertThat(properties.getDatasource()).isEmpty();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> properties.getDatasource().put("primary", new GXMongoDataSourceProperties()));
    }
}
