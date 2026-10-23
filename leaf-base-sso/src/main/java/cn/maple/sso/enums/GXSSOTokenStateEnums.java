package cn.maple.sso.enums;

/**
 * Login state evaluated for the current request.
 *
 * <p>The state is written into the request attribute
 * {@link cn.maple.sso.constant.GXSSOConstant#SSO_TOKEN_STATE} so that interceptors,
 * handlers and downstream code can react with a single, explicit semantics instead of
 * guessing between "empty dict" and "exception".</p>
 */
public enum GXSSOTokenStateEnums {
    /**
     * No token present in header, cookie or request attribute.
     */
    ABSENT("absent", "no token supplied"),

    /**
     * Token parsed and validated against the server side cache.
     */
    ACTIVE("active", "token is active"),

    /**
     * Token was parsed but the cache holds no entry for it.
     */
    CACHE_MISS("cache-miss", "token not found in cache"),

    /**
     * Token signature expired or the cache entry reached its TTL.
     */
    EXPIRED("expired", "token is expired"),

    /**
     * The user logged in elsewhere, so the login timestamp no longer matches.
     */
    KICKED("kicked", "token was replaced by a newer login"),

    /**
     * No cache implementation is available and the fallback is disabled.
     */
    CACHE_UNAVAILABLE("cache-unavailable", "server side cache is unavailable"),

    /**
     * A validate plugin rejected the token.
     */
    PLUGIN_REJECTED("plugin-rejected", "token rejected by a plugin"),

    /**
     * A local only token is used because the fallback was explicitly enabled.
     */
    LOCAL_ONLY("local-only", "token validated locally without server side state");

    private final String code;

    private final String desc;

    GXSSOTokenStateEnums(final String code, final String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static GXSSOTokenStateEnums fromCode(final String code) {
        if (code == null || code.trim().isEmpty()) {
            return ABSENT;
        }

        String normalizedCode = code.trim();
        for (GXSSOTokenStateEnums it : values()) {
            if (it.code.equals(normalizedCode)) {
                return it;
            }
        }
        return ABSENT;
    }

    public String code() {
        return this.code;
    }

    public String desc() {
        return this.desc;
    }
}
