package cn.maple.elasticsearch.properties;

import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class GXElasticsearchProperties {
    /**
     * Elasticsearch 服务地址列表
     */
    private List<String> uris = new ArrayList<>(Collections.singletonList("http://localhost:9200"));

    /**
     * 连接超时时间
     */
    private Duration connectionTimeout = Duration.ofSeconds(1L);

    /**
     * 读取超时时间
     */
    private Duration socketTimeout = Duration.ofSeconds(30L);

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 路径前缀
     */
    private String pathPrefix;

    /**
     * 是否为主要的连接
     */
    private boolean primary = false;
}
