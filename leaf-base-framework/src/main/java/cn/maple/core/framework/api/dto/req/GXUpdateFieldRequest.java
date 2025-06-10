package cn.maple.core.framework.api.dto.req;

import java.io.Serializable;

/**
 * 数据库字段更新请求数据传输对象
 * <p>
 * 该记录类封装了数据库字段更新操作所需的基本信息，包括表名、字段名、更新字段类名和字段值。
 * 作为数据传输对象(DTO)，它在服务层和数据访问层之间传递更新字段的相关信息，
 * 配合GXCommonUtils.convertUpdateFieldRequestLst方法使用，可动态创建不同类型的GXUpdateField对象。
 * </p>
 *
 * <p>
 * 设计特点：
 * 1. 使用Java 16+ Record特性，简洁且不可变，确保线程安全
 * 2. 实现Serializable接口，支持序列化/反序列化，便于网络传输和缓存
 * 3. 支持各种数据类型的字段值，通过Object类型灵活传递
 * 4. 通过className字段动态指定更新字段的具体实现类，支持扩展
 * </p>
 *
 * <p>
 * 使用场景：
 * 1. 动态构建数据库更新条件，无需硬编码字段类型
 * 2. 支持从前端或外部系统接收更新请求并转换为数据库操作
 * 3. 批量更新操作中，灵活指定不同字段的更新策略
 * 4. 微服务间传递更新请求信息
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 创建字符串类型的更新字段请求
 * GXUpdateFieldRequest strFieldReq = new GXUpdateFieldRequest(
 *     "user_table",                                           // 表名
 *     "username",                                            // 字段名
 *     "cn.maple.core.framework.dto.inner.field.GXUpdateStrField", // 更新字段类名
 *     "张三"                                                // 字段值
 * );
 *
 * // 创建数字类型的更新字段请求
 * GXUpdateFieldRequest numFieldReq = new GXUpdateFieldRequest(
 *     "user_table",                                           // 表名
 *     "age",                                                 // 字段名
 *     "cn.maple.core.framework.dto.inner.field.GXUpdateNumberField", // 更新字段类名
 *     25                                                    // 字段值
 * );
 *
 * // 创建JSON操作类型的更新字段请求
 * GXUpdateFieldRequest jsonFieldReq = new GXUpdateFieldRequest(
 *     "user_table",                                           // 表名
 *     "ext_info",                                            // 字段名
 *     "cn.maple.core.framework.dto.inner.field.GXUpdateJsonSetStrField", // 更新字段类名
 *     Map.of("key", "value")                                 // 字段值
 * );
 *
 * // 将请求列表转换为GXUpdateField列表
 * List<GXUpdateFieldRequest> requests = List.of(strFieldReq, numFieldReq, jsonFieldReq);
 * List<GXUpdateField<?>> updateFields = GXCommonUtils.convertUpdateFieldRequestLst(requests);
 * </pre>
 * </p>
 *
 * <p>
 * 安全考虑：
 * 1. 不直接暴露SQL操作，通过GXUpdateField的参数化查询机制防止SQL注入
 * 2. 字段类名需指定完整包路径，避免类加载安全问题
 * 3. 实际使用时应进行权限校验，确保调用者有权限更新相应表和字段
 * </p>
 *
 * @param tableName 表名，指定要更新的数据库表
 * @param fieldName 字段名，指定要更新的表字段
 * @param className 更新字段类名，指定GXUpdateField的具体实现类，必须提供完整类路径
 * @param value     字段值，要设置的新值，类型取决于字段类型和更新字段类的要求
 */
public record GXUpdateFieldRequest(String tableName, String fieldName, String className,
                                   Object value) implements Serializable {
}
