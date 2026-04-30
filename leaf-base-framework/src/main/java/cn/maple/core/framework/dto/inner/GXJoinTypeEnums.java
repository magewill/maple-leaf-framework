package cn.maple.core.framework.dto.inner;

import cn.maple.core.framework.constant.GXBuilderConstant;
import lombok.Getter;

@Getter
public enum GXJoinTypeEnums {
    LEFT(GXBuilderConstant.LEFT_JOIN_TYPE, "左连接"),

    RIGHT(GXBuilderConstant.RIGHT_JOIN_TYPE, "右连接"),

    INNER(GXBuilderConstant.INNER_JOIN_TYPE, "内连接");

    private final String joinType;

    private final String desc;

    GXJoinTypeEnums(String joinType, String desc) {
        this.joinType = joinType;
        this.desc = desc;
    }
}
