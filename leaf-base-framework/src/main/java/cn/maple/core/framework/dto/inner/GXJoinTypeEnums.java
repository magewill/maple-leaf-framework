package cn.maple.core.framework.dto.inner;

import cn.maple.core.framework.constant.GXBuilderConstant;
import lombok.Getter;

@Getter
public enum GXJoinTypeEnums {
    LEFT(GXBuilderConstant.LEFT_JOIN_TYPE, "LEFT JOIN"),

    RIGHT(GXBuilderConstant.RIGHT_JOIN_TYPE, "RIGHT JOIN"),

    INNER(GXBuilderConstant.INNER_JOIN_TYPE, "INNER JOIN");

    private final String joinType;

    private final String desc;

    GXJoinTypeEnums(String joinType, String desc) {
        this.joinType = joinType;
        this.desc = desc;
    }
}
