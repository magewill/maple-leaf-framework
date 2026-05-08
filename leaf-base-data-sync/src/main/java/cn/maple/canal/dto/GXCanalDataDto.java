package cn.maple.canal.dto;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Canal change event payload.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GXCanalDataDto extends GXBaseDto {
    /**
     * New row values.
     */
    private List<Dict> data;
    /**
     * Primary key value if provided by upstream payload.
     */
    private Long id;
    /**
     * Database name.
     */
    private String database;
    /**
     * Event time in milliseconds.
     */
    private Long es;
    /**
     * Whether this event is a DDL operation.
     */
    private Boolean isDdl;
    /**
     * Column name to MySQL type mapping.
     */
    private Dict mysqlType;
    /**
     * Old row values, usually available for UPDATE.
     */
    private List<Dict> old;
    /**
     * Primary key column names.
     */
    private List<String> pkNames;
    /**
     * Original SQL from upstream payload.
     */
    private String sql;
    /**
     * Column name to JDBC type mapping.
     */
    private Dict sqlType;
    /**
     * Table name.
     */
    private String table;
    /**
     * Transaction commit time in milliseconds.
     */
    private Long ts;
    /**
     * Operation type, for example INSERT/UPDATE/DELETE.
     */
    private String type;
}
