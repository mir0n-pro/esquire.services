/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: command domain controller (v1.2.5 Taijitu refactor Step 2, MonadY).
 *                   The cmdHub twin of the eventHub (MessageHandlerHub). Executes
 *                   INIT / CLEAN / CHECKSUM on the worker thread, driving the owning
 *                   MonadY's status + gates via direct (package-private) calls -- no
 *                   control interface. Houses the cancel() collaboration point.
 */
package pro.mir0n.esquire.bizTree.taijitu;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command-side domain controller for one monad. The worker hands each
 * {@link IMonadCommand} here; the hub runs it and drives the monad's state
 * machine + gates by calling back into its owning {@link MonadY} directly
 * (same package, package-private setters). Concrete on purpose -- there is
 * one monad shape, so no executor interface earns its keep.
 *
 *   INIT     -- status LOADING; load via ICacheLoad; on success status LOADED +
 *               processing enabled (buffered events drain); on failure status
 *               FAILED + buffered events dropped.
 *   CLEAN    -- gates off, buffered events dropped, status IDLE.
 *   CHECKSUM -- stub until Yin lands (full Taijitu night-watch).
 */
@Slf4j
final class MonadCmdHub {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + MonadCmdHub.class.getName());

    private final ICacheLoad cacheLoad;
    private final MonadY     monad;

    MonadCmdHub(ICacheLoad cacheLoad, MonadY monad) {
        this.cacheLoad = cacheLoad;
        this.monad     = monad;
    }

    void handle(IMonadCommand command) {
        if (command instanceof IMonadCommand.Init) {
            handleInit();
        } else if (command instanceof IMonadCommand.Clean) {
            handleClean();
        } else if (command instanceof IMonadCommand.Checksum) {
            handleChecksum();
        }
    }

    private void handleInit() {
        monad.setStatusInternal(MonadStatus.LOADING);
        log.info("monad[{}]: INIT -- loading", monad.name());
        try {
            cacheLoad.load();
            monad.setStatusInternal(MonadStatus.LOADED);
            monad.setProcessingEnabled(true);   // open gate; buffered events drain
            log.info("monad[{}]: INIT -- loaded, processing enabled", monad.name());
            monad.cmdResponseListener().onResult(new IMonadCommand.Init(), MonadStatus.LOADED);
        } catch (Exception e) {
            monad.setStatusInternal(MonadStatus.FAILED);
            monad.dropBufferedEventsInternal();  // load failed; buffered events are meaningless
            log.error("monad[{}]: INIT -- failed: {}", monad.name(), e.getMessage());
            monad.errorListener().onError("INIT load (monad=" + monad.name() + ")", e);
            monad.cmdResponseListener().onResult(new IMonadCommand.Init(), MonadStatus.FAILED);
        }
    }

    private void handleClean() {
        monad.setProcessingEnabled(false);
        monad.setQueueEnabled(false);
        monad.dropBufferedEventsInternal();
        monad.setStatusInternal(MonadStatus.IDLE);
        log.info("monad[{}]: CLEAN -- idle", monad.name());
        monad.cmdResponseListener().onResult(new IMonadCommand.Clean(), MonadStatus.IDLE);
    }

    private void handleChecksum() {
        // Stub in the MonadY slice -- no Yin to compare against yet. The
        // cancel() collaboration (aborting a long-running CHECKSUM on the
        // director's timeout) will be wired here when CHECKSUM gains a body.
        devLog.debug("monad[{}]: CHECKSUM stub (no-op until Yin lands)", monad.name());
    }
}
