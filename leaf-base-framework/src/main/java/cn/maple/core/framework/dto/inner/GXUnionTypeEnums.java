package cn.maple.core.framework.dto.inner;

import lombok.Getter;

@Getter
public enum GXUnionTypeEnums {
    UNION_ALL("UNION ALL", "UNION ALL"),

    UNION("UNION", "UNION");

    private final String unionType;

    private final String desc;

    GXUnionTypeEnums(String unionType, String desc) {
        this.unionType = unionType;
        this.desc = desc;
    }
}
