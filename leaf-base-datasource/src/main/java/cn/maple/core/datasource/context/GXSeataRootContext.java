package cn.maple.core.datasource.context;

public final class GXSeataRootContext {
    private static final ThreadLocal<String> XID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> GLOBAL_LOCK_HOLDER = new ThreadLocal<>();

    private GXSeataRootContext() {
    }

    public static String getXID() {
        return XID_HOLDER.get();
    }

    public static void bind(String xid) {
        XID_HOLDER.set(xid);
    }

    public static String unbind() {
        String xid = XID_HOLDER.get();
        XID_HOLDER.remove();
        return xid;
    }

    public static void bindGlobalLockFlag() {
        GLOBAL_LOCK_HOLDER.set(Boolean.TRUE);
    }

    public static void unbindGlobalLockFlag() {
        GLOBAL_LOCK_HOLDER.remove();
    }

    public static boolean requireGlobalLock() {
        return Boolean.TRUE.equals(GLOBAL_LOCK_HOLDER.get());
    }
}
