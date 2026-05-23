/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/21/2026 mir0n  created: the DARK-side director -- extends the bright ATaijituRigY (single
 *                   active monad) to the full Taijitu: two equal AMonad instances (serving +
 *                   shadow), a swappable role pair (the double-buffer pointer flip), per-monad
 *                   gate driving, event fanout to the shadow during a sweep, and off-queue
 *                   CHECKSUM collection. Reads route to the serving monad. (Night-watch sweep
 *                   orchestration lands next.)
 */
package pro.mir0n.utils.taijitu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The full Taijitu director: two equal {@link AMonad}s behind one director. {@code serving}
 * (yang role) answers reads and receives every event; {@code shadow} (yin role) is idle until a
 * night-watch sweep loads it fresh from the source of truth, fans events to it, CHECKSUMs both,
 * and promotes-or-discards via {@link #swapYinYang()}. Bright {@link ATaijituRigY} drives ONE active
 * monad; this adds the second monad and the swap.
 *
 * The two monads each get their OWN gate-driving listener (the bright single-active onStarted/
 * onResult cannot tell the monads apart), so this director does not register itself as their
 * {@link ICmdResponseListener}.
 */
public abstract class ATaijituRig extends ATaijituRigY {

    protected AtomicReference<IMonad> yinMonad = new AtomicReference<>();

    protected ATaijituRig(IMonad monad, IMonad domad) {
        super(monad);   // the inherited bright 'active' seeds to the initial serving monad
        yinMonad.set(domad);
    }

    /* --- Lifecycle ------------------------------------------------------- */
    protected IMonad yin() {
        return yinMonad.get();
    }
    protected void swapYinYang() {
        //most important to swap yang side first: yin swap can be second:
        //no concurrency on dark side: after the swap: next dark command will be from rig: CLEAR
        yinMonad.set(yangMonad.getAndSet(yinMonad.get()));

    }
    @Override
    public void start() {
        IMonad shadow  = yin();
        shadow.setCmdResponseListener(gateFor(shadow));
        shadow.start();
        super.start(); //xxx: runs bootstrap
        log.info("{}: start -- both monads up (serving={}, shadow={}); awaiting first sweep",
                getClass().getSimpleName(), yang().id(), shadow.id());
    }

    @Override
    public void shutdown() {
        super.shutdown();
        yin().shutdown();
    }


    /* --- Event intake ---------------------------------------------------- */

    @Override
    public void onEntityBroadcast(String eventType, String entityId, int entityKind,
                                  String requestId, String correlationId,
                                  String messageEncoding, String text) {
        QueueItem item = new QueueItem(eventType, entityId, entityKind,
                requestId, correlationId, messageEncoding, text);
        yang().offer(item);
        yin().offer(item);
    }


}
