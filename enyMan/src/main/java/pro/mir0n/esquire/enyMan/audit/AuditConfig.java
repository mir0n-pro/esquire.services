/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created (was enyMan.rod.RodConfig): enyMan audit-logging wiring.
 * 06/05/2026 mir0n  thinned onto common.audit.AuditRod.
 * 06/13/2026 mir0n  class-name-driven transport over the unified esquire.audit.* block.
 * 06/14/2026 mir0n  bus-oriented: reads esquire.audit.mode (disabled | log-db | messaging-bus).
 * 06/15/2026 mir0n  resolves the audit producer through the shared XRodManager (Role.BROADCAST on the audit leg).
 * 06/18/2026 mir0n  the audit sink is selected from the leg: log-db.shared=true -> the IN-PROCESS keep on the
 *                   SERVICE's OWN pool; a log-db url -> the IN-PROCESS keep with its OWN dedicated pool; else ->
 *                   the BUS producer. Injects the service DataSource (used by the shared keep).
 * 06/21/2026 mir0n  binds the keep datasource from the leg's "datasource" sub-block (was "log-db"); the in-process
 *                   keep's SQL dialect comes from spring.datasource.url (shared) instead of spring.profiles.active.
 * 06/21/2026 mir0n  resolves the audit leg via catalog.find() (was the strict resolve()), so an unknown audit
 *                   bus-id disables the producer (XRodDisabled) instead of crashing at boot.
 */
package pro.mir0n.esquire.enyMan.audit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.audit.AuditKeepDirector;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.dataKeep.director.IKeepDirector;
import pro.mir0n.esquire.dataKeep.keep.KeepApplier;
import pro.mir0n.esquire.dataKeep.keep.KeepDataSourceParams;
import pro.mir0n.esquire.dataKeep.keep.KeepSqlStore;
import pro.mir0n.esquire.messaging.MessagingBusCatalog;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

import javax.sql.DataSource;

@Configuration
public class AuditConfig {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + AuditConfig.class.getName());
    /** The leg's datasource sub-block: its presence selects the in-process keep (option b). */
    private static final String DATASOURCE = "datasource";

    private final XRodManager rods;
    private final Environment env;
    private final DataSource dataSource;   // the service's OWN business datasource -- used by the SHARED keep
    private KeepApplier keepApplier;   // option (b) only -- the in-process *_log pool; closed on destroy

    public AuditConfig(XRodManager rods, Environment env, DataSource dataSource) {
        this.rods = rods;
        this.env  = env;
        this.dataSource = dataSource;
    }

    /**
     * The audit bridge onto the messaging bus. The audit leg picks the sink: a leg carrying a {@code datasource}
     * datasource group is the IN-PROCESS keep (option b) -- the generic keep applier (the audit director's kinds
     * + SQL data) run on an {@code XRodInProcess} (rods.consumer passes the applier as its worker); otherwise the
     * leg is the BUS producer (option c) -- rods.producer transmits to the broker for auKeep to consume + apply.
     */
    @Bean
    public AuditBusBridge audit() {
        String prefix = "esquire." + EsqMsgConstants.BUS_KEY_AUDIT + ".messaging-bus.";
        String busId  = env.getProperty(prefix + "bus-id", "");
        String slotId = env.getProperty(prefix + "slot-id", "");
        XRodParams leg = (!busId.isBlank() && !slotId.isBlank())
                ? new MessagingBusCatalog(env).find(busId, slotId) : null;
        KeepDataSourceParams ds = leg != null ? leg.sub(DATASOURCE, KeepDataSourceParams.class) : null;

        IXRod sink;
        if (ds != null && ds.isShared()) {
            // option (b-shared): in-process keep on the SERVICE's OWN connection pool -- no dedicated pool; the
            // dialect comes from the service's DataSource URL (spring.datasource.url). The keep does not own/close it.
            IKeepDirector dir = new AuditKeepDirector();
            String dialect = KeepSqlStore.dialectOf(env.getProperty("spring.datasource.url"));
            this.keepApplier = new KeepApplier(dataSource, dialect, new KeepSqlStore(dir.sqlGroup()), dir.kinds(), devLog);
            sink = rods.consumer(EsqMsgConstants.BUS_KEY_AUDIT, Role.BROADCAST, keepApplier.applier());
            devLog.info("audit: in-process keep, SHARED service pool (bus={}, slot={})", busId, slotId);
        } else if (ds != null && ds.url() != null && !ds.url().isBlank()) {
            // option (b-dedicated): in-process keep with its OWN pool from the datasource group + the audit
            // director (its kinds + SQL group); rods.consumer resolves the leg's rod-class (XRodInProcess) and
            // runs the applier on its worker pool. The bridge transmits each event into that in-process pool.
            IKeepDirector dir = new AuditKeepDirector();
            this.keepApplier = new KeepApplier(ds, new KeepSqlStore(dir.sqlGroup()), dir.kinds(), devLog);
            sink = rods.consumer(EsqMsgConstants.BUS_KEY_AUDIT, Role.BROADCAST, keepApplier.applier());
            devLog.info("audit: in-process keep, DEDICATED pool (bus={}, slot={})", busId, slotId);
        } else {
            // option (c): bus producer -- transmit to the broker; the auKeep consumer applies the *_log.
            sink = rods.producer(EsqMsgConstants.BUS_KEY_AUDIT, Role.BROADCAST);
            devLog.info("audit: bus producer (bus={}, slot={})", busId, slotId);
        }
        return new AuditBusBridge(sink);
    }

    @PreDestroy
    public void close() {
        if (keepApplier != null) {
            keepApplier.close();
        }
    }
}
