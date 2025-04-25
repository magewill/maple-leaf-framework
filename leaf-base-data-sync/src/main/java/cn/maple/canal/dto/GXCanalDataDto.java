package cn.maple.canal.dto;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Canal数据传输对象
 * <p>
 * 该类用于封装从Canal接收的数据库变更事件信息，包含完整的变更数据结构。
 * 每个GXCanalDataDto实例代表一个数据库变更事件，包含数据库名、表名、变更类型、
 * 新旧数据内容等完整信息。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 从JSON解析Canal消息
 * GXCanalDataDto canalData = JSONUtil.toBean(message, GXCanalDataDto.class);
 * 
 * // 获取变更类型
 * String type = canalData.getType(); // "INSERT", "UPDATE", "DELETE"
 * 
 * // 获取变更数据
 * List<Dict> newData = canalData.getData();
 * List<Dict> oldData = canalData.getOld(); // 仅UPDATE和DELETE操作有值
 * 
 * // 获取表信息
 * String database = canalData.getDatabase();
 * String table = canalData.getTable();
 * </pre>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GXCanalDataDto extends GXBaseDto {
    /**
     * 新数据
     * <p>
     * 对于INSERT操作，包含新插入的数据；
     * 对于UPDATE操作，包含更新后的数据；
     * 对于DELETE操作，包含被删除的数据。
     * </p>
     * <p>
     * 数据格式为Dict列表，每个Dict代表一行数据，键为列名，值为列值。
     * </p>
     */
    private List<Dict> data;

    /**
     * 主键ID值
     * <p>
     * 表示当前操作记录的主键ID值，通常用于标识具体操作的记录。
     * 注意：此字段可能为null，具体主键信息应参考pkNames字段。
     * </p>
     */
    private Long id;

    /**
     * 数据库名称
     * <p>
     * 表示发生变更的数据库名称，用于确定数据来源。
     * 此字段与表名(table)一起用于唯一标识数据源。
     * </p>
     */
    private String database;

    /**
     * 事件时间戳(毫秒)
     * <p>
     * 表示Canal捕获到变更事件的时间戳，单位为毫秒。
     * 可用于事件排序和时间相关的业务处理。
     * </p>
     */
    private Long es;

    /**
     * 是否DDL修改
     * <p>
     * 标识当前变更是否为数据定义语言(DDL)操作，如CREATE TABLE、ALTER TABLE等。
     * true表示是DDL操作，false表示是DML操作(INSERT/UPDATE/DELETE)。
     * </p>
     */
    private Boolean isDdl;

    /**
     * 数据库字段类型映射
     * <p>
     * 包含表中各字段的数据库类型定义，格式为Dict，键为列名，值为类型定义字符串。
     * 例如：
     * {
     *   "id": "int(11)",
     *   "content": "varchar(255)",
     *   "create_time": "datetime"
     * }
     * </p>
     * <p>
     * 此信息可用于类型转换和数据验证。
     * </p>
     */
    private Dict mysqlType;

    /**
     * 旧数据
     * <p>
     * 仅在UPDATE操作时包含变更前的数据。
     * 数据格式为Dict列表，每个Dict代表一行数据，键为列名，值为列值。
     * </p>
     * <p>
     * 通过比较old和data可以确定具体哪些字段发生了变化。
     * </p>
     */
    private List<Dict> old;

    /**
     * 数据表的主键字段名列表
     * <p>
     * 包含表的主键字段名称列表，例如：["id"] 或 ["user_id", "role_id"]（联合主键）。
     * 此信息对于确定记录唯一性和执行更新/删除操作非常重要。
     * </p>
     */
    private List<String> pkNames;

    /**
     * 原始SQL语句
     * <p>
     * 包含触发当前变更事件的原始SQL语句。
     * 此字段可用于审计、调试和特殊处理场景。
     * </p>
     * <p>
     * 注意：出于安全考虑，在生产环境中应谨慎使用和记录此字段。
     * </p>
     */
    private String sql;

    /**
     * SQL字段的JDBC数据类型
     * <p>
     * 包含表中各字段的JDBC数据类型代码，格式为Dict，键为列名，值为类型代码。
     * 例如：
     * {
     *   "id": 4,        // INTEGER
     *   "content": 12,  // VARCHAR
     *   "status": -6    // TINYINT
     * }
     * </p>
     * <p>
     * 类型代码参考java.sql.Types中的常量定义。
     * 此信息可用于JDBC类型映射和数据转换。
     * </p>
     */
    private Dict sqlType;

    /**
     * 数据表名称
     * <p>
     * 表示发生变更的数据表名称。
     * 此字段与数据库名(database)一起用于唯一标识数据源。
     * </p>
     */
    private String table;

    /**
     * 事务提交时间戳(毫秒)
     * <p>
     * 表示数据库事务提交的时间戳，单位为毫秒。
     * 此字段可用于事务级别的时序分析和处理。
     * </p>
     */
    private Long ts;

    /**
     * 操作类型
     * <p>
     * 表示当前变更的操作类型，可能的值包括：
     * - INSERT: 插入操作
     * - UPDATE: 更新操作
     * - DELETE: 删除操作
     * </p>
     * <p>
     * 此字段是处理逻辑分支的关键依据。
     * </p>
     */
    private String type;
}
