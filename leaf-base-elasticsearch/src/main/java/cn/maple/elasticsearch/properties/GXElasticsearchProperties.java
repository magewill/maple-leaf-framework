package cn.maple.elasticsearch.properties;

import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class GXElasticsearchProperties {
    /** Elasticsearch endpoint list. */
    private List<String> uris = new ArrayList<>(Collections.singletonList("http://localhost:9200"));

    /** Connection timeout. */
    private Duration connectionTimeout = Duration.ofSeconds(1L);

    /** Socket read timeout. */
    private Duration socketTimeout = Duration.ofSeconds(30L);

    /** Basic auth username. */
    private String username;

    /** Basic auth password. */
    private String password;

    /** API path prefix. */
    private String pathPrefix;

    /** Whether this datasource is primary. */
    private boolean primary = false;
}
