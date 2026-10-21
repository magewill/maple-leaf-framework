package cn.maple.core.datasource.it.entity;

import cn.maple.core.datasource.model.GXMyBatisModel;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_listener_user")
public class ListenerUserEntity extends GXMyBatisModel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private Integer score;

    private String status;
}
