package cn.maple.core.datasource.model;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.model.GXBaseModel;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Base model for MyBatis entities.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GXMyBatisModel extends GXBaseModel {

    @TableField(fill = FieldFill.INSERT)
    protected Integer createdAt;

    @TableField(fill = FieldFill.UPDATE)
    protected Integer updatedAt;

    @TableField(fill = FieldFill.INSERT)
    protected String createdBy;

    @TableField(fill = FieldFill.UPDATE)
    protected String updatedBy;

    /**
     * Logical delete marker.
     * 0 means active, deleted value uses primary key id.
     */
    @TableField
    @TableLogic(value = "0", delval = "id")
    protected Long isDeleted;

    @TableField
    protected Integer deletedAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    protected Dict ext;
}
