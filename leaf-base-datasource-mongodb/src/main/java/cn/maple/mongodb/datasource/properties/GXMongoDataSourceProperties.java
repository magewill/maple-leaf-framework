package cn.maple.mongodb.datasource.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;

@Data
@EqualsAndHashCode(callSuper = true)
public class GXMongoDataSourceProperties extends MongoProperties {
    private String beanName;

    private Boolean primary = false;
}
