package cn.maple.redisson.adapter;

/**
 * 处理debezium server的redis stream消息
 * 由于RedissonReliableTopic在处理消息时的格式是固定的
 * {@code
 * "m": {
 * "@class": "cn.hutool.core.lang.Dict",
 * "age": 0,
 * "name": "子曦"
 * }
 * }
 * 而debezium server的redis stream消息格式是
 * {@link <a href="https://debezium.io/documentation/reference/2.3/operations/debezium-server.html#_redis_stream">redis stream消息格式</a>}
 * 所以需要进行单独的处理
 * <p>
 * 使用方法
 * {@code
 *
 * @Override public void registerRedissonListener() {
 * StringCodec stringCodec = new StringCodec();
 * CommandAsyncExecutor commandAsyncExecutor = ((Redisson) redissonMQClient).getCommandExecutor();
 * GXRedissonDebeziumReliableTopic reliableTopic = new GXRedissonDebeziumReliableTopic(stringCodec, commandAsyncExecutor, "debezium-topic", null);
 * <p>
 * reliableTopic.addListener(Object.class, new MessageListener<Object>() {
 * <p>
 * @Override public void onMessage(CharSequence channel,Object msg){
 * System.out.println("aaaaa : " + msg);
 * }
 * });
 * }
 *
 * 注意: 数字会被Base64编码 所以需要进行解码
 * NumberUtil.fromUnsignedByteArray(Base64Decoder.decode("AIlUQA==")) 解码成 9000000
 */
public class GXRedissonDebeziumReliableTopic {/*extends RedissonReliableTopic {
    private static final Logger log = LoggerFactory.getLogger(GXRedissonDebeziumReliableTopic.class);
    private final Map<String, Entry> listeners = new ConcurrentHashMap<>();
    private final String subscriberId;
    private final RStream<String, Object> stream;
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private volatile RFuture<Map<StreamMessageId, Map<String, Object>>> readFuture;
    private volatile Timeout timeoutTask;

    public GXRedissonDebeziumReliableTopic(Codec codec, CommandAsyncExecutor commandExecutor, String name, String subscriberId) {
        super(codec, commandExecutor, name, subscriberId);
        stream = new RedissonStream<>(new CompositeCodec(StringCodec.INSTANCE, codec), commandExecutor, name);
        if (subscriberId == null) {
            subscriberId = getServiceManager().generateId();
        }
        this.subscriberId = subscriberId;
    }

    public GXRedissonDebeziumReliableTopic(CommandAsyncExecutor commandExecutor, String name, String subscriberId) {
        this(new StringCodec(), commandExecutor, name, subscriberId);
    }

    @Override
    public <M> RFuture<String> addListenerAsync(Class<M> type, MessageListener<M> listener) {
        String id = getServiceManager().generateId();
        listeners.put(id, new GXRedissonDebeziumReliableTopic.Entry(type, listener));

        if (!subscribed.compareAndSet(false, true)) {
            return new CompletableFutureWrapper<>(id);
        }

        renewExpiration();

        RFuture<Void> addFuture = commandExecutor.evalWriteNoRetryAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "redis.call('zadd', KEYS[2], ARGV[3], ARGV[2]);" +
                        "redis.call('xgroup', 'create', KEYS[1], ARGV[2], ARGV[1], 'MKSTREAM'); ",
                Arrays.asList(getRawName(), getTimeout()),
                StreamMessageId.ALL, subscriberId, System.currentTimeMillis() + getServiceManager().getCfg().getReliableTopicWatchdogTimeout());
        CompletionStage<String> f = addFuture.thenApply(r -> {
            poll(subscriberId);
            return id;
        });

        return new CompletableFutureWrapper<>(f);
    }

    private void renewExpiration() {
        timeoutTask = getServiceManager().newTimeout(t -> {
            if (!subscribed.get()) {
                return;
            }

            RFuture<Boolean> future = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                    "if redis.call('zscore', KEYS[1], ARGV[2]) == false then "
                            + "return 0; "
                            + "end; "
                            + "redis.call('zadd', KEYS[1], ARGV[1], ARGV[2]); "
                            + "return 1; ",
                    Arrays.asList(getTimeout()),
                    System.currentTimeMillis() + getServiceManager().getCfg().getReliableTopicWatchdogTimeout(), subscriberId);
            future.whenComplete((res, e) -> {
                if (e != null) {
                    log.error("Can't update reliable topic {} expiration time", getRawName(), e);
                    return;
                }

                if (res) {
                    // reschedule itself
                    renewExpiration();
                }
            });
        }, getServiceManager().getCfg().getReliableTopicWatchdogTimeout() / 3, TimeUnit.MILLISECONDS);
    }

    private String getTimeout() {
        return suffixName(getRawName(), "timeout");
    }

    private void poll(String id) {
        RFuture<Map<StreamMessageId, Map<String, Object>>> f = stream.pendingRangeAsync(id, StreamMessageId.MIN, StreamMessageId.MAX, 100);
        CompletionStage<Map<StreamMessageId, Map<String, Object>>> ff = f.thenCompose(r -> {
            if (!subscribed.get()) {
                return CompletableFuture.completedFuture(r);
            }

            if (r.isEmpty()) {
                readFuture = stream.readGroupAsync(id, "consumer",
                        StreamReadGroupArgs.neverDelivered().timeout(Duration.ofSeconds(0)));
                return readFuture;
            }
            return CompletableFuture.completedFuture(r);
        });

        ff.whenComplete((res, ex) -> {
            if (ex != null) {
                if (ex instanceof RedissonShutdownException) {
                    return;
                }

                if (ex.getCause().getMessage().contains("NOGROUP")) {
                    return;
                }

                log.error(ex.getCause().getMessage(), ex.getCause());

                getServiceManager().newTimeout(task -> {
                    poll(id);
                }, 1, TimeUnit.SECONDS);
                return;
            }

            CompletableFuture<Void> done = new CompletableFuture<>();
            if (!listeners.isEmpty()) {
                getServiceManager().getExecutor().execute(() -> {
                    for (Map.Entry<StreamMessageId, Map<String, Object>> entry : res.entrySet()) {
                        Object value = entry.getValue().get("value");
                        listeners.values().forEach(e -> {
                            if (e.getType().isInstance(value)) {
                                ((MessageListener<Object>) e.getListener()).onMessage(getRawName(), value);
                                stream.ack(id, entry.getKey());
                            }
                        });
                    }
                    done.complete(null);
                });
            } else {
                done.complete(null);
            }

            done.thenAccept(r -> {
                long time = System.currentTimeMillis();
                RFuture<Boolean> updateFuture = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "local expired = redis.call('zrangebyscore', KEYS[2], 0, tonumber(ARGV[2]) - 1); "
                                + "for i, v in ipairs(expired) do "
                                + "redis.call('xgroup', 'destroy', KEYS[1], v); "
                                + "end; "
                                + "local r = redis.call('zscore', KEYS[2], ARGV[1]); "

                                + "local score = 92233720368547758;"
                                + "local groups = redis.call('xinfo', 'groups', KEYS[1]); " +
                                "for i, v in ipairs(groups) do "
                                + "local id1, id2 = string.match(v[8], '(.*)%-(.*)'); "
                                + "score = math.min(tonumber(id1), score); "
                                + "end; " +

                                "score = tostring(score) .. '-0';"
                                + "local range = redis.call('xrange', KEYS[1], score, '+'); "
                                + "if #range == 0 or (#range == 1 and range[1][1] == score) then "
                                + "redis.call('xtrim', KEYS[1], 'maxlen', 0); "
                                + "else "
                                + "redis.call('xtrim', KEYS[1], 'maxlen', #range); "
                                + "end;"
                                + "return r ~= false; ",
                        Arrays.asList(getRawName(), getTimeout()),
                        id, time);

                updateFuture.whenComplete((re, exc) -> {
                    if (exc != null) {
                        if (exc instanceof RedissonShutdownException) {
                            return;
                        }
                        log.error("Unable to update subscriber status", exc);
                        return;
                    }

                    if (!re || !subscribed.get()) {
                        return;
                    }

                    poll(id);
                });
            });

        });
    }

    private static class Entry {
        private final Class<?> type;
        private final MessageListener<?> listener;

        Entry(Class<?> type, MessageListener<?> listener) {
            this.type = type;
            this.listener = listener;
        }

        public Class<?> getType() {
            return type;
        }

        public MessageListener<?> getListener() {
            return listener;
        }
    }*/
}
