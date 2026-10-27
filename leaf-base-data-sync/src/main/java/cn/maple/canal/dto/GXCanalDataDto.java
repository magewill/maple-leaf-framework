package cn.maple.canal.dto;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Canal change event payload.
 *
 * <p>All fields come straight from the Canal flat message and may be absent, therefore every field is
 * nullable. The DTO instance and its {@link #data()} / {@link #old()} collections are shared with the
 * table handler; a handler that stores them must copy the values it wants to keep, because the
 * collections are mutable and are not reused after the event has been processed.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GXCanalDataDto extends GXBaseDto {
    /**
     * New row values. Shared with the handler, not copied.
     */
    private @Nullable List<Dict> data;
    /**
     * Primary key value if provided by upstream payload.
     *
     * <p>Not part of the Canal flat message contract; kept for backward compatibility only.</p>
     */
    private @Nullable Long id;
    /**
     * Database name.
     */
    private @Nullable String database;
    /**
     * Event time in milliseconds.
     */
    private @Nullable Long es;
    /**
     * Whether this event is a DDL operation.
     */
    private @Nullable Boolean isDdl;
    /**
     * Column name to MySQL type mapping.
     */
    private @Nullable Dict mysqlType;
    /**
     * Old row values, usually available for UPDATE. Shared with the handler, not copied.
     */
    private @Nullable List<Dict> old;
    /**
     * Primary key column names.
     */
    private @Nullable List<String> pkNames;
    /**
     * Original SQL from upstream payload.
     */
    private @Nullable String sql;
    /**
     * Column name to JDBC type mapping.
     */
    private @Nullable Dict sqlType;
    /**
     * Table name.
     */
    private @Nullable String table;
    /**
     * Transaction commit time in milliseconds.
     */
    private @Nullable Long ts;
    /**
     * Operation type, for example INSERT/UPDATE/DELETE.
     */
    private @Nullable String type;
}
