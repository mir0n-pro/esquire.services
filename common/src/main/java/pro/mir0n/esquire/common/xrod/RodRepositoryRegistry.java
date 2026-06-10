/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created: the x-Rod kind -> IRodRepository register. Each asset-owning service
 *                   registers its *_log repositories by kind; several kinds may share one repository
 *                   (e.g. the person sub-kinds 992/994/996 -> one person-log repository). The xx-Rod
 *                   looks up the repository for each RodEvent's kind. One registry per xx-Rod instance:
 *                   in-process (b) holds only the service's own kinds; the standalone (c) xx-Rod holds all.
 */
package pro.mir0n.esquire.common.xrod;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps an entity {@code kind} to the {@link IRodRepository} that applies its events. Populated at
 * startup by the asset-owning service; read (lock-free) by the xx-Rod worker pool per event.
 */
public final class RodRepositoryRegistry {

    private final ConcurrentHashMap<Integer, IRodRepository> byKind = new ConcurrentHashMap<>();

    /** Register the repository for a kind. Several kinds may point to the SAME repository instance. */
    public void register(int kind, IRodRepository repository) {
        byKind.put(kind, repository);
    }

    /** The repository for this kind, or {@code null} if none is registered (the xx-Rod skips + logs). */
    public IRodRepository repositoryFor(int kind) {
        return byKind.get(kind);
    }

    /** True iff a repository is registered for this kind. */
    public boolean handles(int kind) {
        return byKind.containsKey(kind);
    }
}
