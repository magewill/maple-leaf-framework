package cn.maple.sso.enums;

import java.util.Locale;

public enum GXRandomType {
    MIX,

    NUMBER,

    CHARACTER,

    CHINESE;

    public static GXRandomType fromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return MIX;
        }

        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
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
