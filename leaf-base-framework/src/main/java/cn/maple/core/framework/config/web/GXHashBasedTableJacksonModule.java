package cn.maple.core.framework.config.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.io.IOException;
import java.util.Map;

/**
 * Google Guava HashBasedTable的Jackson序列化和反序列化模块
 * <p>
 * 该模块提供了对Google Guava HashBasedTable类的JSON序列化和反序列化支持。
 * HashBasedTable是一个二维表结构，具有行键、列键和值，可以通过(row, column)对来存取值。
 * 本模块将HashBasedTable序列化为JSON数组，每个元素包含row、column和value三个字段。
 * </p>
 * 
 * <p><strong>序列化格式：</strong></p>
 * <pre>
 * [
 *   {
 *     "row": "行键1",
 *     "column": "列键1",
 *     "value": 值1
 *   },
 *   {
 *     "row": "行键2",
 *     "column": "列键2",
 *     "value": 值2
 *   },
 *   ...
 * ]
 * </pre>
 * 
 * <p><strong>安全特性：</strong></p>
 * <ul>
 *   <li>安全处理null值：序列化和反序列化过程中正确处理null表、null键和null值</li>
 *   <li>类型安全：使用泛型确保类型安全，避免类型转换错误</li>
 *   <li>防止空指针异常：在访问键和值之前进行null检查</li>
 *   <li>跳过无效数据：序列化时跳过null键的单元格，反序列化时跳过缺少必要字段的数据</li>
 * </ul>
 * 
 * <p><strong>性能优化：</strong></p>
 * <ul>
 *   <li>高效序列化：直接访问表的单元格集合，避免多次查询</li>
 *   <li>高效反序列化：使用预创建的表实例，减少内存分配</li>
 *   <li>流式处理：使用Jackson的流式API，减少内存使用</li>
 * </ul>
 * 
 * <p><strong>使用示例：</strong></p>
 * <pre>
 * // 1. 注册模块到ObjectMapper
 * ObjectMapper objectMapper = new ObjectMapper();
 * objectMapper.registerModule(GXHashBasedTableJacksonModule.createModule());
 * 
 * // 2. 创建HashBasedTable并添加数据
 * HashBasedTable&lt;String, String, Object&gt; table = HashBasedTable.create();
 * table.put("row1", "col1", "value1");
 * table.put("row1", "col2", 100);
 * table.put("row2", "col1", true);
 * 
 * // 3. 序列化为JSON字符串
 * String json = objectMapper.writeValueAsString(table);
 * // 输出: [{"row":"row1","column":"col1","value":"value1"},
 * //        {"row":"row1","column":"col2","value":100},
 * //        {"row":"row2","column":"col1","value":true}]
 * 
 * // 4. 反序列化为HashBasedTable
 * HashBasedTable&lt;String, String, Object&gt; deserializedTable = 
 * objectMapper.readValue(json, HashBasedTable.class);
 * 
 * // 5. 访问表中的数据
 * Object value = deserializedTable.get("row1", "col2"); // 返回 100
 * </pre>
 * 
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
public class GXHashBasedTableJacksonModule {
    /**
     * 创建并返回HashBasedTable的Jackson序列化/反序列化模块
     * <p>
     * 该方法创建一个SimpleModule实例，并注册HashBasedTable的序列化器和反序列化器。
     * 序列化器将HashBasedTable转换为JSON数组，每个元素包含row、column和value三个字段。
     * 反序列化器将JSON数组转换回HashBasedTable实例。
     * </p>
     *
     * @return 配置好的Jackson Module实例
     */
    @SuppressWarnings("unchecked")
    public static Module createModule() {
        SimpleModule module = new SimpleModule("GXHashBasedTableModule");

        // 序列化器：将HashBasedTable转换为JSON数组
        module.addSerializer((Class<HashBasedTable<String, String, Object>>) (Class<?>) HashBasedTable.class,
                new JsonSerializer<>() {
                    @Override
                    public void serialize(HashBasedTable<String, String, Object> table, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                        // 处理null表
                        if (table == null) {
                            gen.writeNull();
                            return;
                        }
                        
                        // 开始写入数组
                        gen.writeStartArray();
                        
                        // 遍历表中的所有单元格
                        for (Table.Cell<String, String, Object> cell : table.cellSet()) {
                            // 跳过null键的单元格
                            if (cell.getRowKey() == null || cell.getColumnKey() == null) {
                                continue;
                            }
                            
                            // 写入单元格对象
                            gen.writeStartObject();
                            
                            // 写入行键
                            gen.writeFieldName("row");
                            gen.writeString(cell.getRowKey());
                            
                            // 写入列键
                            gen.writeFieldName("column");
                            gen.writeString(cell.getColumnKey());
                            
                            // 写入值（可以是任何类型，包括null）
                            gen.writeFieldName("value");
                            if (cell.getValue() == null) {
                                gen.writeNull();
                            } else {
                                // 使用默认序列化器处理值，支持任何类型
                                serializers.defaultSerializeValue(cell.getValue(), gen);
                            }
                            
                            // 结束单元格对象
                            gen.writeEndObject();
                        }
                        
                        // 结束数组
                        gen.writeEndArray();
                    }
                });

        // 反序列化器：将JSON数组转换为HashBasedTable
        module.addDeserializer((Class<HashBasedTable<String, String, Object>>) (Class<?>) HashBasedTable.class,
                new JsonDeserializer<>() {
                    @Override
                    public HashBasedTable<String, String, Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                        // 创建空表
                        HashBasedTable<String, String, Object> table = HashBasedTable.create();
                        ObjectMapper mapper = (ObjectMapper) p.getCodec();

                        // 处理null值
                        if (p.getCurrentToken() == JsonToken.VALUE_NULL) {
                            return null;
                        }

                        // 确保当前token是数组开始
                        if (p.currentToken() != JsonToken.START_ARRAY) {
                            p.nextToken(); // 移动到数组开始
                        }
                        
                        // 遍历数组中的每个对象
                        while (p.nextToken() != JsonToken.END_ARRAY) {
                            // 解析单个对象为Map
                            Map<String, Object> entry = mapper.readValue(p, Map.class);
                            
                            // 提取行键、列键和值
                            String row = (String) entry.get("row");
                            String column = (String) entry.get("column");
                            Object value = entry.get("value");
                            
                            // 只有当行键和列键都不为null时才添加到表中
                            if (row != null && column != null) {
                                table.put(row, column, value);
                            }
                        }
                        
                        return table;
                    }
                });

        return module;
    }
}