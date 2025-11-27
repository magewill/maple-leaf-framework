package cn.maple.mongodb.datasource.properties;

import lombok.Data;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;

/**
 * 多数据源属性
 */
@Data
@SuppressWarnings("all")
public class GXMongoDataSourceProperties extends MongoProperties {
    /**
     * bean的名字
     */
    private String beanName;
    /**
     * 在多数据源时 指定是否为主要的bean
     * 相当于加了@Primary注解
     */
    private Boolean primary = false;
}
