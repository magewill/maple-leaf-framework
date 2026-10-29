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

    /*
     * The keys below are the event payload contract between the write aspects (writers) and the
     * listener dispatch layer (reader); see contracts/event-payload.md. Aspects must not redeclare
     * them locally, otherwise the two sides can drift apart.
     */

    /**
     * Payload key holding the intercepted write operation name.
     */
    public static final String OPERATION = "operation";

    /**
     * Payload key holding the written entity as a dictionary.
     */
    public static final String ENTITY_DATA = "entityData";

    /**
     * Payload key holding the batch rows; it references the same rows as {@link #ENTITY_DATA}.
     */
    public static final String BATCH_ENTITIES = "batchEntities";

    /**
     * Payload key holding the fields written by an update-field operation.
     */
    public static final String UPDATE_FIELD_DATA = "updateFieldData";

    /**
     * Payload key holding the structured condition fields.
     */
    public static final String CONDITION_FIELD_DATA = "conditionFieldData";

    /**
     * Payload key mapping each conditioned field to its comparison operator.
     */
    public static final String KEY_OPERATOR_PAIRS = "keyOperatorPairs";

    /**
     * Payload key mapping each conditioned field to its value.
     */
    public static final String KEY_VALUE_PAIRS = "keyValuePairs";

    /**
     * Payload key holding the original {@code WHERE} SQL of an entity update.
     */
    public static final String RAW_WHERE_SQL = "rawWhereSql";

    /**
     * Payload key holding the raw condition SQL, kept when the structured form is lossy.
     */
    public static final String RAW_CONDITION_SQL = "rawConditionSql";

    /**
     * Payload key stating whether the structured condition fields represent the original condition
     * completely.
     */
    public static final String CONDITION_LOSSLESS = "conditionLossless";

    /**
     * Payload key listing why the payload is degraded.
     */
    public static final String DEGRADE_REASONS = "degradeReasons";

    /**
     * Payload key marking a payload that still keeps references to caller-owned mutable values.
     */
    public static final String SNAPSHOT_DEGRADED = "snapshotDegraded";

    /**
     * Payload key marking a payload the strict-snapshot scan could not verify within its budget.
     */
    public static final String SNAPSHOT_SCAN_TRUNCATED = "snapshotScanTruncated";

    private GXMyBatisEventConstant() {
    }
}
