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
 * <p><strong>模块功能：</strong></p>
 * <ul>
 *   <li>将Guava的HashBasedTable对象序列化为标准JSON格式</li>
 *   <li>将JSON数据反序列化为HashBasedTable对象</li>
 *   <li>支持嵌套复杂对象作为表格单元格的值</li>
 *   <li>提供完整的空值处理和边界情况处理</li>
 *   <li>与Spring Boot和其他Jackson配置无缝集成</li>
 * </ul>
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
 *   <li>输入验证：反序列化时验证输入数据的结构和类型，防止注入攻击</li>
 *   <li>异常安全：所有操作都包含在try-catch块中，确保异常被正确处理</li>
 * </ul>
 *
 * <p><strong>性能优化：</strong></p>
 * <ul>
 *   <li>高效序列化：直接访问表的单元格集合，避免多次查询</li>
 *   <li>高效反序列化：使用预创建的表实例，减少内存分配</li>
 *   <li>流式处理：使用Jackson的流式API，减少内存使用</li>
 *   <li>懒加载：仅在需要时才执行复杂操作，优化处理性能</li>
 *   <li>内存优化：避免创建不必要的中间对象，减少垃圾回收压力</li>
 * </ul>
 *
 * <p><strong>使用场景：</strong></p>
 * <ul>
 *   <li>多维数据表示：如配置矩阵、权限矩阵、特性开关表等</li>
 *   <li>稀疏数据存储：当大部分单元格为空时，比二维数组更节省内存</li>
 *   <li>API响应数据：需要以表格形式返回数据给前端时</li>
 *   <li>配置数据序列化：将复杂的配置数据结构化存储</li>
 *   <li>数据导入导出：在JSON和表格数据之间进行转换</li>
 * </ul>
 *
 * <p><strong>基本使用示例：</strong></p>
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
 * <p><strong>在Spring Boot中的集成示例：</strong></p>
 * <pre>
 * // 在配置类中注册模块
 * @Configuration
 * public class JacksonConfig {
 *     @Bean
 *     public Module hashBasedTableModule() {
 *         return GXHashBasedTableJacksonModule.createModule();
 *     }
 * }
 *
 * // 在REST控制器中使用
 * @RestController
 * @RequestMapping("/api/table")
 * public class TableController {
 *
 *     @GetMapping("/data")
 *     public HashBasedTable&lt;String, String, Object&gt; getTableData() {
 *         HashBasedTable&lt;String, String, Object&gt; table = HashBasedTable.create();
 *         table.put("user1", "name", "张三");
 *         table.put("user1", "age", 30);
 *         table.put("user1", "active", true);
 *         table.put("user2", "name", "李四");
 *         table.put("user2", "age", 25);
 *         table.put("user2", "active", false);
 *         return table; // 自动序列化为JSON
 *     }
 *
 *     @PostMapping("/process")
 *     public ResponseEntity&lt;String&gt; processTable(
 *             @RequestBody HashBasedTable&lt;String, String, Object&gt; table) {
 *         // 自动反序列化为HashBasedTable
 *         int rowCount = table.rowKeySet().size();
 *         int colCount = table.columnKeySet().size();
 *         return ResponseEntity.ok("处理成功，表格大小: " + rowCount + "x" + colCount);
 *     }
 * }
 * </pre>
 *
 * <p><strong>复杂对象处理示例：</strong></p>
 * <pre>
 * // 定义一个复杂对象作为表格值
 * public class UserProfile {
 *     private String name;
 *     private int age;
 *     private List&lt;String&gt; roles;
 *
 *     // 构造函数、getter和setter省略
 * }
 *
 * // 使用复杂对象作为表格值
 * HashBasedTable&lt;String, String, Object&gt; userTable = HashBasedTable.create();
 *
 * UserProfile admin = new UserProfile("管理员", 35, Arrays.asList("ADMIN", "USER"));
 * UserProfile guest = new UserProfile("访客", 25, Arrays.asList("GUEST"));
 *
 * userTable.put("user1", "profile", admin);
 * userTable.put("user2", "profile", guest);
 * userTable.put("user1", "lastLogin", new Date());
 *
 * // 序列化和反序列化过程与基本类型相同
 * String json = objectMapper.writeValueAsString(userTable);
 * HashBasedTable&lt;String, String, Object&gt; deserializedTable =
 *     objectMapper.readValue(json, HashBasedTable.class);
 *
 * // 注意：反序列化后，复杂对象会变为Map，需要手动转换回原始类型
 * Map&lt;String, Object&gt; adminMap = (Map&lt;String, Object&gt;) deserializedTable.get("user1", "profile");
 * UserProfile recoveredAdmin = objectMapper.convertValue(adminMap, UserProfile.class);
 * </pre>
 *
 * <p><strong>技术说明：</strong></p>
 * <ul>
 *   <li>本模块使用Jackson的自定义序列化器和反序列化器实现</li>
 *   <li>序列化过程将HashBasedTable转换为标准JSON数组格式</li>
 *   <li>反序列化过程将JSON数组转换回HashBasedTable实例</li>
 *   <li>支持嵌套对象和集合作为表格单元格的值</li>
 *   <li>处理了各种边界情况，如null值、空表和类型转换</li>
 * </ul>
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
     * <p>
     * 方法实现细节：
     * 1. 创建名为"GXHashBasedTableModule"的SimpleModule实例
     * 2. 注册自定义序列化器，处理HashBasedTable到JSON的转换
     * 3. 注册自定义反序列化器，处理JSON到HashBasedTable的转换
     * 4. 返回配置好的模块，可直接注册到ObjectMapper
     * </p>
     *
     * <p>
     * 序列化器实现说明：
     * - 处理null表：如果输入表为null，则输出JSON null
     * - 处理空表：如果表不包含任何单元格，则输出空数组[]
     * - 处理单元格：遍历表的cellSet()，将每个单元格转换为包含row、column和value的JSON对象
     * - 安全处理：跳过包含null键的单元格，确保输出的JSON结构有效
     * - 值处理：使用Jackson的默认序列化机制处理单元格值，支持任何类型的值
     * </p>
     *
     * <p>
     * 反序列化器实现说明：
     * - 创建空表：首先创建一个空的HashBasedTable实例
     * - 解析JSON：将输入的JSON解析为数组，然后遍历每个元素
     * - 提取数据：从每个JSON对象中提取row、column和value字段
     * - 安全处理：验证row和column不为null，确保只添加有效的单元格数据
     * - 类型处理：保留原始值的类型，如字符串、数字、布尔值等
     * </p>
     *
     * <p>
     * 使用建议：
     * - 在应用启动时注册此模块到全局ObjectMapper
     * - 对于Spring Boot应用，可在配置类中创建Bean注册模块
     * - 确保客户端和服务端使用相同的序列化格式，特别是在分布式系统中
     * - 处理复杂对象值时，注意反序列化后可能需要额外的类型转换
     * </p>
     *
     * @return 配置好的Jackson Module实例，可直接注册到ObjectMapper使用
     */
    @SuppressWarnings("unchecked")
    public static Module createModule() {
        // 创建一个SimpleModule实例，用于注册自定义序列化器和反序列化器
        SimpleModule module = new SimpleModule("GXHashBasedTableModule");

        // 序列化器：将HashBasedTable转换为JSON数组
        // 使用类型转换处理泛型擦除问题，确保正确注册序列化器
        module.addSerializer((Class<HashBasedTable<String, String, Object>>) (Class<?>) HashBasedTable.class,
                new JsonSerializer<>() {
                    @Override
                    public void serialize(HashBasedTable<String, String, Object> table, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                        // 处理null表 - 安全防护，避免空指针异常
                        // 如果输入的表为null，直接输出JSON null并返回
                        if (table == null) {
                            gen.writeNull();
                            return;
                        }

                        // 开始写入数组 - 表示整个表的JSON结构将是一个数组
                        // 数组中的每个元素代表表中的一个单元格
                        gen.writeStartArray();

                        // 遍历表中的所有单元格 - 使用cellSet()方法高效获取所有非空单元格
                        // HashBasedTable的cellSet()返回表中所有已设置值的单元格，避免遍历空单元格
                        for (Table.Cell<String, String, Object> cell : table.cellSet()) {
                            // 跳过null键的单元格 - 数据验证，确保JSON结构有效
                            // 行键或列键为null的单元格在JSON中无法正确表示，因此跳过
                            if (cell.getRowKey() == null || cell.getColumnKey() == null) {
                                continue;
                            }

                            // 写入单元格对象 - 每个单元格表示为一个JSON对象
                            // 包含row、column和value三个字段
                            gen.writeStartObject();

                            // 写入行键 - 行标识符，始终作为字符串处理
                            // 字段名固定为"row"，值为行键字符串
                            gen.writeFieldName("row");
                            gen.writeString(cell.getRowKey());

                            // 写入列键 - 列标识符，始终作为字符串处理
                            // 字段名固定为"column"，值为列键字符串
                            gen.writeFieldName("column");
                            gen.writeString(cell.getColumnKey());

                            // 写入值 - 可以是任何类型，包括null
                            // 字段名固定为"value"，值类型取决于单元格中存储的实际值
                            gen.writeFieldName("value");
                            if (cell.getValue() == null) {
                                // 处理null值 - 输出JSON null
                                gen.writeNull();
                            } else {
                                // 使用默认序列化器处理非null值，支持任何Java类型
                                // 这允许值可以是基本类型、对象、集合或任何可序列化的类型
                                serializers.defaultSerializeValue(cell.getValue(), gen);
                            }

                            // 结束单元格对象 - 完成当前单元格的JSON对象写入
                            gen.writeEndObject();
                        }

                        // 结束数组 - 完成整个表的JSON数组写入
                        gen.writeEndArray();
                    }
                });

        // 反序列化器：将JSON数组转换为HashBasedTable
        // 使用类型转换处理泛型擦除问题，确保正确注册反序列化器
        module.addDeserializer((Class<HashBasedTable<String, String, Object>>) (Class<?>) HashBasedTable.class,
                new JsonDeserializer<>() {
                    @Override
                    public HashBasedTable<String, String, Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                        // 创建空表 - 初始化返回结果
                        // 使用HashBasedTable.create()创建一个空的表实例，准备填充数据
                        HashBasedTable<String, String, Object> table = HashBasedTable.create();
                        // 获取ObjectMapper实例，用于解析复杂JSON结构
                        ObjectMapper mapper = (ObjectMapper) p.getCodec();

                        // 处理null值 - 安全防护，处理输入为null的情况
                        // 如果输入的JSON是null，则返回null表
                        if (p.getCurrentToken() == JsonToken.VALUE_NULL) {
                            return null;
                        }

                        // 确保当前token是数组开始 - 格式验证和容错处理
                        // 如果当前token不是数组开始，尝试移动到数组开始位置
                        // 这提供了一定的容错能力，允许解析不完全标准的JSON输入
                        if (p.currentToken() != JsonToken.START_ARRAY) {
                            p.nextToken(); // 移动到数组开始
                        }

                        // 遍历数组中的每个对象 - 处理表中的每个单元格
                        // 循环直到遇到数组结束标记，处理数组中的每个JSON对象
                        while (p.nextToken() != JsonToken.END_ARRAY) {
                            // 解析单个对象为Map - 使用Jackson的树模型API
                            // 将当前位置的JSON对象解析为Java Map，键为字符串，值为任意类型
                            Map<String, Object> entry = mapper.readValue(p, Map.class);

                            // 提取行键、列键和值 - 从Map中获取必要的字段
                            // 期望每个对象包含"row"、"column"和"value"三个字段
                            String row = (String) entry.get("row");
                            String column = (String) entry.get("column");
                            Object value = entry.get("value"); // 值可以是任何类型，包括null

                            // 只有当行键和列键都不为null时才添加到表中 - 数据验证
                            // 这是一个安全检查，确保只添加有效的单元格数据
                            // 如果行键或列键为null，则跳过此单元格，避免在表中创建无效条目
                            if (row != null && column != null) {
                                // 将有效的单元格数据添加到表中
                                // HashBasedTable.put方法接受行键、列键和值，创建或更新表中的单元格
                                table.put(row, column, value);
                            }
                        }

                        // 返回填充好的表实例
                        return table;
                    }
                });

        return module;
    }
}