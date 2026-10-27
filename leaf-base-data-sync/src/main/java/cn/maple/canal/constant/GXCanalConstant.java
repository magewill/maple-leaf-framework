package cn.maple.canal.constant;

import java.util.Set;

/**
 * Default constants for Canal message consumption.
 */
public class GXCanalConstant {
    public static final String LOG_LEVEL_OFF = "off";
    public static final String LOG_LEVEL_DEBUG = "debug";
    public static final String LOG_LEVEL_WARN_ONCE = "warn-once";
    public static final String LOG_LEVEL_WARN = "warn";

    public static final String RABBITMQ_CANAL_CONCURRENCY_COUNT = "10";
    public static final String RABBITMQ_CANAL_QUEUE_NAME = "canalQueue";
    public static final String RABBITMQ_CANAL_EXCHANGE_NAME = "exchange.fanout.canal";
    public static final String RABBITMQ_CANAL_ROUTING_KEY = "canal.example.exchange.routingkey";

    public static final String DEFAULT_DEAD_LETTER_EXCHANGE_NAME = "canal.dlx";
    public static final String DEFAULT_DEAD_LETTER_QUEUE_NAME = "canal.dlq";
    public static final String DEFAULT_DEAD_LETTER_ROUTING_KEY = "canal.dead";
    public static final String DEFAULT_ROCKETMQ_TAG = "*";
    public static final int ROCKETMQ_CONSUME_THREAD_MAX = 20;
    public static final String DEFAULT_HANDLER_MISS_LOG_LEVEL = LOG_LEVEL_WARN_ONCE;
    public static final int DEFAULT_RETRY_MAX_ATTEMPTS = 3;
    public static final int DEFAULT_HANDLER_CACHE_SIZE = 4096;

    public static final String HEADER_RETRY_COUNT = "x-canal-retry-count";
    public static final String HEADER_TRACE_ID = "traceId";
    public static final String PARAM_OPERATOR = "operator";
    public static final String OPERATOR_INSERT = "insert";
    public static final String OPERATOR_UPDATE = "update";
    public static final String OPERATOR_DELETE = "delete";

    public static final String RESULT_KEY_STATUS = "status";
    public static final String RESULT_KEY_MESSAGE = "message";
    public static final String RESULT_STATUS_SUCCESS = "success";
    public static final String RESULT_STATUS_ERROR = "error";
    public static final String RESULT_STATUS_WARNING = "warning";

    public static final String TYPE_INSERT = "INSERT";
    public static final String TYPE_UPDATE = "UPDATE";
    public static final String TYPE_DELETE = "DELETE";

    /**
     * Canal DDL operation types. They are skipped instead of being reported as errors.
     */
    public static final Set<String> DDL_OPERATION_TYPES = Set.of("CREATE", "ALTER", "ERASE", "DROP", "TRUNCATE");

    public static final String METRIC_PREFIX = "canal.consumer.";

    /**
     * Startup hint: keys read while the context is being assembled only take effect after a restart.
     */
    public static final String ASSEMBLY_RESTART_HINT = "Canal assembly-time keys (mq-type, config-source, "
            + "queue/exchange/routing-key, concurrency, rocketmq topic/consumer-group) only take effect after restart";

    private GXCanalConstant() {
        // Utility class.
    }
}
