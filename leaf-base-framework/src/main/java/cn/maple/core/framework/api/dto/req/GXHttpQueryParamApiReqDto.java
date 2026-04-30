package cn.maple.core.framework.api.dto.req;

import com.google.common.collect.HashBasedTable;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("all")
public class GXHttpQueryParamApiReqDto extends GXBaseApiReqDto implements Serializable {
    private String tableName;

    private HashBasedTable<String, String, Object> conditionLst;

    /**
     * 更新字段
     * Quartet<String, String, String, Object>中每个字段的含义
     * 1. String: 表名
     * 2. String: 字段名
     * 3. String: GXUpdateField的子类的全名
     * 4. Object: 字段值
     */
    private List<GXUpdateFieldRequest> updateFieldLst;

    private Set<String> columns;

    private String column;
}
