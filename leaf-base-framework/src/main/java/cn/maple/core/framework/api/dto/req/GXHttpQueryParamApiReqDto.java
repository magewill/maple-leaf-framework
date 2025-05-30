package cn.maple.core.framework.api.dto.req;

import com.google.common.collect.HashBasedTable;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javatuples.Quartet;

import java.io.Serializable;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("all")
@Builder
public class GXHttpQueryParamApiReqDto extends GXBaseApiReqDto implements Serializable {
    /**
     * 需要查询的主表名字(在有join查询时,需要有主表、次表的区分)
     */
    private String tableName;

    /**
     * 搜索条件
     */
    private HashBasedTable<String, String, Object> conditionLst;

    /**
     * 更新字段
     * Quartet<String, String, String, Object>中每个字段的含义
     * 1. String: 表名
     * 2. String: 字段名
     * 3. String: GXUpdateField的子类的全名
     * 4. Object: 字段值
     */
    private List<Quartet<String, String, String, Object>> updateFieldLst;
}
