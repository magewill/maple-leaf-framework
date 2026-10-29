package cn.maple.core.datasource.constant;

public class GXMyBatisEventConstant {
    public static final String MYBATIS_SYNC_EVENT = "sync";

    public static final String MYBATIS_ASYNC_EVENT = "async";

    /**
     * Event param key holding the simple name of the declaring listener class.
     */
    public static final String LISTENER_CLAZZ_NAME = "listenerClazzName";

    /**
     * Event param key holding the declaring listener class.
     */
    public static final String LISTENER_CLAZZ = "listenerClazz";

    /*
     * The keys below are an internal contract between GXMyBatisListenerEventPublisher (writer) and
     * GXMyBatisBaseListener (reader). They are not part of the public event payload and listeners
     * must neither rely on nor overwrite them.
     */

    /**
     * Event param key carrying the {@code propagate-exception} switch into the dispatch layer.
     */
    public static final String PROPAGATE_EXCEPTION = "propagateException";

    /**
     * Event param key marking an asynchronous delivery. Asynchronous callback failures are always
     * isolated so they can never terminate the executor thread.
     */
    public static final String ASYNC_DELIVERY = "asyncDelivery";

    /**
     * Event param key marking a delivery that runs after transaction completion (asynchronous
     * listeners, or synchronous listeners with {@code sync-after-commit=true}). Such callbacks run
     * when the business caller is already gone, so failures stay isolated instead of stopping
     * subsequent listeners (contracts/listener-callback.md).
     */
    public static final String DEFERRED_DELIVERY = "deferredDelivery";

    /**
     * Event param key carrying the write-operation identifier so dispatch and executor logs can be
     * correlated with the originating write, including on executor threads (FR-020).
     */
    public static final String OPERATION_ID = "operationId";

    /**
     * Event param key under which the dispatch layer stashes a callback failure that must surface
     * as-is to the business caller once the synchronous delivery call returns.
     */
    public static final String PENDING_CALLBACK_FAILURE = "pendingCallbackFailure";

    private GXMyBatisEventConstant() {
    }
}
