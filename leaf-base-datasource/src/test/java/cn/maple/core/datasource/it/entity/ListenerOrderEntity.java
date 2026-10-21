package cn.maple.core.datasource.it.entity;

import cn.maple.core.framework.model.GXBaseModel;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_listener_order")
public class ListenerOrderEntity extends GXBaseModel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Integer amount;
}
