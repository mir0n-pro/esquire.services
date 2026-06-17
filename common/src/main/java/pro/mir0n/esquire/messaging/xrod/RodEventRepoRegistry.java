/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created: the x-Rod kind -> IRodEventRepo register. Each asset-owning service
 *                   registers its *_log repositories by kind; several kinds may share one repository
 *                   (e.g. the person sub-kinds 992/994/996 -> one person-log repository). The xx-Rod
 *                   looks up the repository for each RodEvent's kind. One registry per xx-Rod instance:
 *                   in-process (b) holds only the service's own kinds; the standalone (c) xx-Rod holds all.
 */
package pro.mir0n.esquire.messaging.xrod;

import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Maps an entity {@code kind} to the {@link IRodEventRepo} that applies its events. Populated at
 * startup by the asset-owning service; read (lock-free) by the x-Rod receive pool per event.
 */
public final class RodEventRepoRegistry {

    private final ConcurrentHashMap<Integer, IRodEventRepo> byKind = new ConcurrentHashMap<>();

    /** Register the repository for a kind. Several kinds may point to the SAME repository instance. */
    public void register(int kind, IRodEventRepo repository) {
        byKind.put(kind, repository);
    }

    /** The repository for this kind, or {@code null} if none is registered (the xx-Rod skips + logs). */
    public IRodEventRepo repositoryFor(int kind) {
        return byKind.get(kind);
    }

    /** True iff a repository is registered for this kind. */
    public boolean handles(int kind) {
        return byKind.containsKey(kind);
    }

    /** A receive-leg worker that resolves each event's repository by kind and applies it; a missing repository
     *  is logged and skipped (resilience -- exactly-once across redelivery is the {@code *_log} ON CONFLICT /
     *  MERGE's job, not the pool's). Hand this to an x-rod as its receive worker. */
    public Consumer<RodEvent> applier(Logger devLog) {
        return event -> {
            IRodEventRepo repo = repositoryFor(event.kind());
            if (repo == null) {
                if (devLog != null) {
                    devLog.warn("x-rod: no IRodEventRepo for kind={} (entityId={}) -- skipped",
                            event.kind(), event.entityId());
                }
            } else {
                repo.apply(event);
            }
        };
    }
}
