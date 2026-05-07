package cn.maple.sso.enums;

public enum GXTokenFlag {
    NORMAL(0, "正常"),

    CACHE_SHUT(1, "缓存宕机");

    private final Integer value;

    private final String desc;

    GXTokenFlag(final Integer value, final String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static GXTokenFlag fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return NORMAL;
        }

        try {
            int intValue = Integer.parseInt(value);
            return fromValue(intValue);
        } catch (NumberFormatException e) {
            return NORMAL;
        }
    }

    public static GXTokenFlag fromValue(Integer value) {
        if (value == null) {
            return NORMAL;
        }

        for (GXTokenFlag it : values()) {
            if (it.value().equals(value)) {
                return it;
            }
        }
        return NORMAL;
    }

    public Integer value() {
        return this.value;
    }

    public String desc() {
        return this.desc;
    }

    public boolean isNormal() {
        return this == NORMAL;
    }

    public boolean isCacheShut() {
        return this == CACHE_SHUT;
    }
}
