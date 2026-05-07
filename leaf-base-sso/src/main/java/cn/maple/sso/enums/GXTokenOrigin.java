package cn.maple.sso.enums;

public enum GXTokenOrigin {
    COOKIE("0", "cookie"),

    HTML5("1", "html5"),

    IOS("2", "apple ios"),

    ANDROID("3", "google android");

    private final String value;

    private final String desc;

    GXTokenOrigin(final String value, final String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static GXTokenOrigin fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return COOKIE;
        }

        for (GXTokenOrigin it : values()) {
            if (it.value().equals(value)) {
                return it;
            }
        }
        return COOKIE;
    }

    public static GXTokenOrigin fromDesc(String desc) {
        if (desc == null || desc.isEmpty()) {
            return COOKIE;
        }

        String lowerDesc = desc.toLowerCase();
        for (GXTokenOrigin it : values()) {
            if (it.desc().toLowerCase().contains(lowerDesc)) {
                return it;
            }
        }
        return COOKIE;
    }

    public String value() {
        return this.value;
    }

    public String desc() {
        return this.desc;
    }

    public boolean isMobile() {
        return this == IOS || this == ANDROID;
    }

    public boolean isWeb() {
        return this == COOKIE || this == HTML5;
    }
}
