package cn.maple.sso.enums;

public enum GXRandomType {
    MIX,

    NUMBER,

    CHARACTER,

    CHINESE;

    public static GXRandomType fromName(String name) {
        if (name == null || name.isEmpty()) {
            return MIX;
        }

        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试模糊匹配
            for (GXRandomType type : values()) {
                if (type.name().toUpperCase().contains(name.toUpperCase())) {
                    return type;
                }
            }
            return MIX;
        }
    }

    public boolean isNumberOnly() {
        return this == NUMBER;
    }

    public boolean containsLetters() {
        return this == CHARACTER || this == MIX;
    }
    
    public boolean isChinese() {
        return this == CHINESE;
    }
}