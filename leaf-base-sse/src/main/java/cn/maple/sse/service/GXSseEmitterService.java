package cn.maple.sse.service;

import cn.maple.core.framework.service.GXBusinessService;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Server-Sent Events service contract.
 * <p>
 * Provides the basic contract for creating SSE connections, querying active emitters,
 * sending messages, broadcasting messages and closing connections.
 * </p>
 * <p>
 * Guarantees:
 * </p>
 * <ul>
 *   <li>messages for the same client id are delivered in send order, including while a
 *   failed message is being retried;</li>
 *   <li>a connection removed by this service is always completed, never left hanging;</li>
 *   <li>blank or missing inputs are logged and ignored, they never throw.</li>
 * </ul>
 * <p>
 * Known limitations (see {@code specs/011-sse-module-audit/contracts/}):
 * the connection timeout is absolute, so heartbeats only detect dead peers and never
 * extend the lifetime; {@link #closeConnect(String)} does not stop the browser from
 * reconnecting; there is no replay or {@code Last-Event-ID} support.
 * </p>
 * <p>
 * Typical controller usage:
 * </p>
 * <pre>{@code
 * @Autowired
 * private GXSseEmitterService sseEmitterService;
 *
 * @GetMapping("/connect")
 * public SseEmitter connect(@RequestParam(required = false) String clientId) {
 *     return sseEmitterService.createSseConnect(clientId, 300);
 * }
 *
 * @PostMapping("/send/{clientId}")
 * public void sendMessage(@PathVariable String clientId, @RequestBody String message) {
 *     sseEmitterService.sendMessageToOneClient(clientId, message, true);
 * }
 *
 * @PostMapping("/broadcast")
 * public void broadcast(@RequestBody String message) {
 *     sseEmitterService.sendMessageToAllClient(message);
 * }
 * }</pre>
 */
public interface GXSseEmitterService extends GXBusinessService {
    /**
     * Creates an SSE connection.
     * <p>
     * If {@code clientId} is blank, the implementation creates a new client id and sends
     * it to the client in the first SSE event. A minimum timeout is enforced to protect
     * server resources; the timeout is absolute from the moment the connection is
     * accepted and is not extended by writes or heartbeats.
     * </p>
     *
     * @param clientId client id used to identify the connection; may be {@code null} or blank
     * @param timeout  requested connection timeout in seconds
     * @return created emitter
     * @throws cn.maple.core.framework.exception.GXBusinessException when the service has been destroyed or the
     * handshake failed for a connection that is still the active one
     */
    SseEmitter createSseConnect(@Nullable String clientId, long timeout);

    /**
     * Returns the emitter for the specified client id.
     * <p>
     * The returned emitter may already be replaced or completed. Writing to it directly
     * bypasses this service's retry, ordering, cleanup and metrics, so prefer
     * {@link #sendMessageToOneClient(String, String, boolean)}.
     * </p>
     *
     * @param clientId client id; may be {@code null} or blank
     * @return matching emitter, or {@code null} when it does not exist
     */
    @Nullable
    SseEmitter getSseEmitterByClientId(@Nullable String clientId);

    /**
     * Sends a message to all active clients.
     * <p>
     * Blank messages are ignored. Deliveries are performed on the calling thread; each
     * client keeps its own ordering, clients are independent of each other.
     * </p>
     *
     * @param msg message content; may be {@code null} or blank
     */
    void sendMessageToAllClient(@Nullable String msg);

    /**
     * Sends a message to one client.
     * <p>
     * When {@code splitMsg} is {@code true} the message is cut into fragments of at most
     * 100 characters; all fragments share one message id and carry their index in the SSE
     * comment so that the client can reassemble them. Once a send fails, the message and
     * every following message for that client are delivered asynchronously in order.
     * </p>
     *
     * @param clientId target client id; may be {@code null} or blank
     * @param msg      message content; may be {@code null} or blank
     * @param splitMsg whether to split the message before sending
     */
    void sendMessageToOneClient(@Nullable String clientId, @Nullable String msg, boolean splitMsg);

    /**
     * Closes the SSE connection for the specified client id.
     * <p>
     * A {@code close} event is sent before the emitter is completed. This does not and
     * cannot stop the browser from reconnecting; only an HTTP 204 response would.
     * </p>
     *
     * @param clientId client id to close; may be {@code null} or blank
     */
    void closeConnect(@Nullable String clientId);

    /**
     * Splits a message into smaller fragments.
     * <p>
     * The requested {@code length} is clamped to {@code [1, 100]} and each fragment uses a
     * randomized length within that bound to avoid fixed-size patterns. Surrogate pairs
     * are never split.
     * </p>
     *
     * @param msg    message content; blank values yield an empty list
     * @param length requested maximum fragment length in characters
     * @return split message fragments
     */
    List<String> splitMessage(@Nullable String msg, int length);

    /**
     * Returns a weakly consistent snapshot of the delivery counters.
     * <p>
     * Implementations that do not track counters return {@link GXSseMetrics#empty()}.
     * </p>
     *
     * @return metrics snapshot, never {@code null}
     */
    default GXSseMetrics getMetrics() {
        return GXSseMetrics.empty();
    }
}
