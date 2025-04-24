package cn.maple.core.datasource.model;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.model.GXBaseModel;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MyBatis Plus基础模型类
 * <p>
 * 该类为所有MyBatis Plus实体类的基类，提供了通用的审计字段和扩展字段。
 * 继承此类的实体将自动获得创建时间、更新时间、创建人、更新人等审计功能，
 * 以及可灵活存储额外数据的扩展字段。
 * </p>
 * 
 * <p>
 * 特性：
 * <ul>
 *   <li>自动填充：创建和更新时间、创建人和更新人会自动填充，无需手动设置</li>
 *   <li>类型转换：使用JacksonTypeHandler自动处理JSON与Dict之间的转换</li>
 *   <li>审计追踪：记录数据的创建和修改信息，便于追溯</li>
 *   <li>扩展存储：通过ext字段存储不固定结构的扩展数据</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建实体类继承GXMyBatisModel
 * @Data
 * @EqualsAndHashCode(callSuper = true)
 * @TableName("tb_user")
 * public class UserEntity extends GXMyBatisModel {
 *     @TableId(type = IdType.AUTO)
 *     private Long id;
 *     
 *     private String username;
 *     
 *     private String email;
 *     
 *     // 无需定义createdAt、updatedAt等字段，已由父类提供
 * }
 * 
 * // 2. 创建新实体时，审计字段会自动填充
 * UserEntity user = new UserEntity();
 * user.setUsername("张三");
 * user.setEmail("zhangsan@example.com");
 * // 可以设置扩展字段
 * Dict extInfo = Dict.create()
 *     .set("address", "北京市海淀区")
 *     .set("phoneNumber", "13800138000");
 * user.setExt(extInfo);
 * 
 * // 3. 保存实体，createdAt和createdBy会自动填充
 * userMapper.insert(user);
 * 
 * // 4. 更新实体，updatedAt和updatedBy会自动填充
 * user.setEmail("zhangsan_new@example.com");
 * userMapper.updateById(user);
 * 
 * // 5. 查询时可以获取扩展字段中的数据
 * UserEntity queryUser = userMapper.selectById(1L);
 * String address = queryUser.getExt().getStr("address");
 * </pre>
 * </p>
 * 
 * <p>
 * 注意事项：
 * <ul>
 *   <li>需要配置MyBatis Plus的自动填充处理器才能实现字段自动填充</li>
 *   <li>ext字段在数据库中通常定义为TEXT或JSON类型</li>
 *   <li>审计字段的自动填充依赖于MetaObjectHandler的实现</li>
 * </ul>
 * </p>
 * 
 * @author 塵渊
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GXMyBatisModel extends GXBaseModel {
    /**
     * 创建时间（秒级时间戳）
     * <p>
     * 在实体被创建时自动填充，记录数据的创建时间
     * 通常使用当前时间的秒级时间戳
     * </p>
     */
    @TableField(fill = FieldFill.INSERT)
    protected Integer createdAt;

    /**
     * 更新时间（秒级时间戳）
     * <p>
     * 在实体被更新时自动填充，记录数据的最后修改时间
     * 通常使用当前时间的秒级时间戳
     * </p>
     */
    @TableField(fill = FieldFill.UPDATE)
    protected Integer updatedAt;

    /**
     * 创建人标识
     * <p>
     * 在实体被创建时自动填充，记录创建该数据的用户标识
     * 通常使用当前登录用户的ID或用户名
     * </p>
     */
    @TableField(fill = FieldFill.INSERT)
    protected String createdBy;

    /**
     * 更新人标识
     * <p>
     * 在实体被更新时自动填充，记录最后修改该数据的用户标识
     * 通常使用当前登录用户的ID或用户名
     * </p>
     */
    @TableField(fill = FieldFill.UPDATE)
    protected String updatedBy;

    /**
     * 扩展字段
     * <p>
     * 用于存储不固定结构的扩展数据，采用键值对形式
     * 使用JacksonTypeHandler自动处理JSON与Dict之间的转换
     * 可以存储实体类中未定义的额外属性，提高灵活性
     * </p>
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    protected Dict ext;
}
