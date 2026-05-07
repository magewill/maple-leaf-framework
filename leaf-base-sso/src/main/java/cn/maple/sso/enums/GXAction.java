package cn.maple.sso.enums;

public enum GXAction {
    Normal("0", "verify permission"),

    Skip("1", "skip permission");

    private final String key;

    private final String desc;

    GXAction(final String key, final String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static GXAction fromKey(String key) {
        if (key == null || key.isEmpty()) {
            return Normal;
        }

        for (GXAction action : values()) {
            if (action.getKey().equals(key)) {
                return action;
            }
        }
        return Normal;
    }

    public String getKey() {
        return this.key;
    }

    public String getDesc() {
        return this.desc;
    }

    public boolean isSkip() {
        return this == Skip;
    }

    public boolean isNormal() {
        return this == Normal;
    }
}
