package cn.maple.core.framework.api.dto.req;

import java.io.Serializable;

public record GXUpdateFieldRequest(String tableName, String fieldName, String className,
                                   Object value) implements Serializable {
}
