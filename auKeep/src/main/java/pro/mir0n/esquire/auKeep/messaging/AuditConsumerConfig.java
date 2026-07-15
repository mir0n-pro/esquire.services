/*
 *  Esquire frameworks (tm)
 *  auKeep service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the @Configuration that opens the audit consumer onto the audit bus.
 * 06/18/2026 mir0n  builds the generic keep applier (the audit director's kinds + SQL data, applied to the keep
 *                   datasource group esquire.keep.datasource) and runs it behind the bus consumer that
 *                   rods.consumer opens on the audit leg. The director knows only its kinds; the engine is generic.
 * 06/22/2026 mir0n  rewired onto the facade: takes the audit rod from MessagingBus.getXRod (audit-bus ref role
 *                   CLIENT) and sets the keep applier as its receive worker; guarded with isEnabled() so an
 *                   explicitly-disabled audit bus leaves the consumer idle.
 * 06/22/2026 mir0n  added keepHealth() -> Supplier<TransportHealth> over the keep applier (UP when no keep active);
 *                   the lifecycle registrar registers it as the "keepDatasource" health contributor.
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 * 07/10/2026 mir0n  v1.2.11 O1 -- injects ObjectProvider<MeterRegistry> and hands getIfAvailable() to the
 *                   KeepApplier, so the keep pool's hikaricp_* meters report when observability is enabled.
 * 07/15/2026 mir0n  v1.2.11 T11 -- wraps the keep applier so the audit consumer stamps MDC via
 *                   EsqContextHolder.applyMessage(event) before applying and clears in a finally, correlating its
 *                   log lines to the audited message (I10)
 */
package pro.mir0n.esquire.auKeep.messaging;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import pro.mir0n.esquire.audit.AuditKeepDirector;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.dataKeep.director.IKeepDirector;
import pro.mir0n.esquire.dataKeep.keep.KeepApplier;
import pro.mir0n.esquire.dataKeep.keep.KeepDataSourceParams;
import pro.mir0n.esquire.dataKeep.keep.KeepSqlStore;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Configuration
public class AuditConsumerConfig {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + AuditConsumerConfig.class.getName());
    /** The keep's *_log datasource group (configured the same way a producer's in-process leg is). */
    private static final String KEEP_DATASOURCE = "esquire.keep.datasource";

    private final Environment env;
    // The Micrometer registry (present only when observability is enabled) -- handed to the keep pool so its
    // hikaricp_* meters report; the keep pool is an OWN HikariDataSource, invisible to Boot's auto-instrumentation.
    private final org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistry;
    private KeepApplier keepApplier;   // the *_log pool; closed on destroy

    public AuditConsumerConfig(Environment env,
                               org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistry) {
        this.env = env;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Open the audit consumer: take the audit rod the facade built (audit-bus ref, role CLIENT) and set the
     * generic keep applier (the audit director's kinds + SQL, applied to the keep datasource group) as its
     * receive worker. If the audit bus is explicitly disabled (XRodDisabled, e.g. audit-off) the consumer stays
     * idle; a missing keep datasource also leaves it idle.
     */
    @Bean
    public IXRod auditConsumer() {
        IXRod rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_AUDIT);
        if (!rod.isEnabled()) {
            devLog.info("auKeep: audit bus is disabled (XRodDisabled) -- audit consumer idle");
        } else {
            KeepDataSourceParams ds = Binder.get(env)
                    .bind(KEEP_DATASOURCE, Bindable.of(KeepDataSourceParams.class)).orElse(null);
            if (ds == null || ds.url() == null || ds.url().isBlank()) {
                devLog.info("auKeep: no {} configured -- no audit consumer started", KEEP_DATASOURCE);
            } else {
                IKeepDirector dir = new AuditKeepDirector();
                this.keepApplier = new KeepApplier(ds, new KeepSqlStore(dir.sqlGroup()), dir.kinds(), devLog,
                        meterRegistry.getIfAvailable());
                // The generic keep applier logs the apply (develop channel) but does not establish a context, and
                // its item IS a RodEvent -- so applyMessage (not set) is the right tool. Wrapped HERE (auKeep, above
                // common) so the keep engine in dataKeep never learns the MDC key vocabulary.
                final Consumer<RodEvent> keepWorker = keepApplier.applier();
                rod.setWorker(e -> {
                    EsqContextHolder.applyMessage(e);
                    try {
                        keepWorker.accept(e);
                    } finally {
                        EsqContextHolder.clear();
                    }
                });
                devLog.info("auKeep: audit consumer applying to keep datasource (kinds={})", dir.kinds().size());
            }
        }
        return rod;
    }

    /** The keep datasource health source -- the lifecycle registrar registers it as the "keepDatasource" health
     *  contributor (auKeep's consumer rod carries the BROKER health; this is the separate DB-side health). UP
     *  when no keep is active (nothing to be down). */
    public Supplier<TransportHealth> keepHealth() {
        return keepApplier != null ? keepApplier::health : () -> TransportHealth.UP;
    }

    @PreDestroy
    public void close() {
        if (keepApplier != null) {
            keepApplier.close();
        }
    }
}
