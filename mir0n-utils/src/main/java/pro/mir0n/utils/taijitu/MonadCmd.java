/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: monad command vocabulary (generalized from bizTree.taijitu).
 *                   Commands ride a QueueItem as eventType==CMD, entityId==one of these.
 * 05/23/2026 mir0n  javadoc: submit -> submitCommand; CHECKSUM is the off-queue order-independent hash.
 */
package pro.mir0n.utils.taijitu;

/**
 * Control-command vocabulary for a monad. A command rides a {@link QueueItem} as
 * {@code eventType == CMD} with {@code entityId} set to one of {@link #LOAD} /
 * {@link #CLEAR} / {@link #CHECKSUM}.
 *
 * Commands are issued ONLY via {@code AMonadY.submitCommand(commandId, ...)} using these constants, so the
 * worker can discriminate with reference equality ({@code ==}): a command item's eventType /
 * entityId are always these interned literals, and an event's eventType is never the CMD
 * literal. No interning of incoming events is needed.
 *
 *   LOAD     -- bulk-load the source into the monad's cache.
 *   CLEAR    -- drop all cached data + buffered events; status -> IDLE.
 *   CHECKSUM -- order-independent content hash of the cache, computed off-queue; the night-watch checksums both legs.
 */
public final class MonadCmd {

    /** eventType marker that distinguishes a command item from an event item. */
    public static final String CMD      = "CMD";

    public static final String LOAD     = "LOAD";
    public static final String CLEAR    = "CLEAR";
    public static final String CHECKSUM = "CHECKSUM";

    private MonadCmd() {
    }
}
