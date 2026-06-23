/*
 *  Esquire frameworks (tm)
 *  esquire-dataKeep
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created (was audit.XRodAuditKeep, generalized): the in-process KEEP x-rod. A generic in-process
 *                   relay ({@link XRodInProcess}) that APPLIES each event to a DB sink. It builds its OWN dedicated
 *                   keep pool (the leg's "datasource" sub-block) + a KeepApplier driven by the leg's "director"
 *                   (a class name -> the IKeepDirector providing the SQL group + kind->statement map), and sets the
 *                   applier as its own receive worker. The director is config-resolved, so this stays generic --
 *                   any keep (audit, ...) names its director on the leg.
 */
package pro.mir0n.esquire.dataKeep.keep;

import org.slf4j.Logger;
import pro.mir0n.esquire.dataKeep.director.IKeepDirector;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.xrod.impl.XRodInProcess;

/** The in-process keep x-rod: a generic {@link XRodInProcess} that applies each transmitted event to its own
 *  dedicated DB pool via a config-named {@link IKeepDirector}. AuditBusBridge.transmit() (or any producer) feeds
 *  the in-process pool, which applies the event. The OWNER of an in-process keep leg. */
public final class XRodInProcessKeep extends XRodInProcess {

    /** The leg's datasource sub-block: the keep's own dedicated pool (a url is mandatory). */
    private static final String DATASOURCE = "datasource";
    /** The leg's director class name: the IKeepDirector providing the SQL group + kind->statement map. */
    private static final String DIRECTOR = "director";

    private KeepApplier keepApplier;   // the DB pool; closed on shutdown

    @Override
    public void validate(XRodParams params) {
        // the in-process keep REQUIRES its own datasource AND a director -- fail fast (like XRod requires transport).
        KeepDataSourceParams ds = params != null ? params.sub(DATASOURCE, KeepDataSourceParams.class) : null;
        require(ds != null && ds.url() != null && !ds.url().isBlank(), "datasource.url", params);
        require(directorClass(params) != null, "director", params);
    }

    @Override
    public void init(String name, Logger devLog) {
        // build the applier here (init carries the devLog); OPEN the engine first (creates the receive pool,
        // paused), THEN wire the applier as the live worker -- setWorker requires the pool to exist, and
        // applyWorker reads the worker live, so wiring it after the (paused) engine is built is correct. The
        // in-process feed loops each transmit into the pool, which applies the live worker (the applier).
        KeepDataSourceParams ds = params.sub(DATASOURCE, KeepDataSourceParams.class);
        IKeepDirector dir = resolveDirector(directorClass(params));
        this.keepApplier = new KeepApplier(ds, new KeepSqlStore(dir.sqlGroup()), dir.kinds(), devLog);
        super.init(name, devLog);   // XRodInProcess.init -> buildEngine(this::receive, this::applyWorker), paused
        setWorker(keepApplier.applier());
    }

    @Override
    public void shutdown() {
        super.shutdown();           // drain the pool first
        if (keepApplier != null) {
            keepApplier.close();    // then close the keep's pool
        }
    }

    private static String directorClass(XRodParams params) {
        Object v = params != null && params.raw() != null ? params.raw().get(DIRECTOR) : null;
        return v != null ? v.toString() : null;
    }

    /** Resolve the {@link IKeepDirector} from its full class name (loaded at runtime -- this stays free of any
     *  specific keep module). Throws a clear error if absent or not an IKeepDirector. */
    private static IKeepDirector resolveDirector(String fqcn) {
        IKeepDirector ret;
        try {
            Object o = Class.forName(fqcn).getDeclaredConstructor().newInstance();
            if (!(o instanceof IKeepDirector dir)) {
                throw new IllegalStateException(fqcn + " does not implement IKeepDirector");
            }
            ret = dir;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate keep director " + fqcn, e);
        }
        return ret;
    }
}
