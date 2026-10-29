package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;

/**
 * Batch event that carries an already-built payload instead of snapshotting it a second time.
 *
 * <p>{@link GXBaseEvent} defensively snapshots its source on construction. The batch aspect already
 * builds a fresh {@code List<Dict>} detached from the business entities, so snapshotting it again
 * only duplicates memory for large batches ({@code SC-005}). This subclass keeps the public event
 * type hierarchy intact and returns the pre-built payload directly.</p>
 *
 * <p>The publisher uses this event for every batch delivery. With a single listener the pre-built
 * payload is returned as is; with multiple listeners the publisher wraps it once into a read-only
 * snapshot ({@code GXReadOnlyPayload}) shared by all listeners, so there is still only one copy of
 * the batch data and one listener cannot affect another listener's payload ({@code FR-008},
 * {@code SC-005}).</p>
 */
@SuppressWarnings("unchecked")
public class GXMyBatisModelSaveBatchEntitySnapshotEvent<M extends Dict> extends GXMyBatisModelSaveBatchEntityEvent<M> {
    private final M payload;

    public GXMyBatisModelSaveBatchEntitySnapshotEvent(M payload, String eventType, Dict param, String eventName) {
        super((M) Dict.create(), eventType, param, eventName);
        this.payload = payload;
    }

    @Override
    public M getSource() {
        return payload;
    }
}
