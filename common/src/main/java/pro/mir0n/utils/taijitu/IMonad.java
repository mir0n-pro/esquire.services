/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/22/2026 mir0n  created: the monad CONTROL contract -- the public surface a director (rig)
 *                   drives. The director depends on this interface, not the concrete AMonadY:
 *                   lifecycle, queue entry, gate control, monitor, listener, synchronous command.
 *                   The cache work itself stays an internal hook of the implementation (_processItem).
 */
package pro.mir0n.utils.taijitu;

/**
 * The controllable cache monad, as seen by its director. {@link ATaijituRigY} (and the dark-side
 * director) talk to the monad ONLY through this interface; the concrete monad ({@code AMonadY} ->
 * bizTree {@code MonadY}) implements it and adds the domain reads + the {@code _processItem} hook.
 */
public interface IMonad {

    /* --- lifecycle --- */
    void start();
    void shutdown();

    /* --- queue entry --- */
    boolean offer(QueueItem item);        // offer an event (accepted only while the queue is enabled)

    /* --- gate control + monitor (the director toggles these) --- */
    void        setQueueEnabled(boolean enabled);
    void        setProcessingEnabled(boolean enabled);
    void        queueClear();
    MonadStatus status();
    int         queueSize();
    String      id();

    /* --- listener --- */
    void setCmdResponseListener(ICmdResponseListener listener);

    /* --- synchronous command --- */
    /** Issue {@code cmd} and BLOCK until the worker completes it; returns the result (status name,
     *  digest, ...). {@code enableQueue} opens the accept-gate right after the command is posted
     *  (events queue behind it). {@code timeoutMs <= 0} waits indefinitely. */
    String doCommand(String cmd, boolean enableQueue, long timeoutMs);
}
