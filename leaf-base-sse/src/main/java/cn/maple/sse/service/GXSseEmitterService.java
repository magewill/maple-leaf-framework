package cn.maple.sse.service;

import cn.maple.core.framework.service.GXBusinessService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Server-Sent Events service contract.
 * <p>
 * Provides the basic contract for creating SSE connections, querying active
 * emitters, sending messages, broadcasting messages and closing connections.
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
     * If {@code clientId} is blank, the implementation creates a new client id
     * and sends it to the client in the first SSE event. Implementations may
     * enforce a minimum timeout to protect server resources.
     * </p>
     *
     * @param clientId client id used to identify the connection; may be blank
     * @param timeout  requested connection timeout in seconds
     * @return created emitter
     */
    SseEmitter createSseConnect(String clientId, long timeout);

    /**
     * Returns the emitter for the specified client id.
     *
     * @param clientId client id
     * @return matching emitter, or {@code null} when it does not exist
     */
    SseEmitter getSseEmitterByClientId(String clientId);

    /**
     * Sends a message to all active clients.
     * <p>
     * Blank messages are ignored by the default implementation.
     * </p>
     *
     * @param msg message content
     */
    void sendMessageToAllClient(String msg);

    /**
     * Sends a message to one client.
     * <p>
     * When {@code splitMsg} is {@code true}, the message may be sent in multiple
     * SSE events.
     * </p>
     *
     * @param clientId target client id
     * @param msg      message content
     * @param splitMsg whether to split the message before sending
     */
    void sendMessageToOneClient(String clientId, String msg, boolean splitMsg);

    /**
     * Closes the SSE connection for the specified client id.
     *
     * @param clientId client id to close
     */
    void closeConnect(String clientId);

    /**
     * Splits a message into smaller fragments.
     * <p>
     * The default implementation uses randomized fragment lengths up to the
     * effective length limit.
     * </p>
     *
     * @param msg    message content
     * @param length requested maximum fragment length
     * @return split message fragments
     */
    List<String> splitMessage(String msg, int length);
}
