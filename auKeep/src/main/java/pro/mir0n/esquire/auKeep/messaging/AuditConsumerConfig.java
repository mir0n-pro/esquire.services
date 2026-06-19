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
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.dataKeep.director.IKeepDirector;
import pro.mir0n.esquire.dataKeep.keep.KeepApplier;
import pro.mir0n.esquire.dataKeep.keep.KeepDataSourceParams;
import pro.mir0n.esquire.dataKeep.keep.KeepSqlStore;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

@Configuration
public class AuditConsumerConfig {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + AuditConsumerConfig.class.getName());
    /** The keep's *_log datasource group (configured the same way a producer's in-process leg is). */
    private static final String KEEP_DATASOURCE = "esquire.keep.datasource";

    private final XRodManager rods;
    private final Environment env;
    private KeepApplier keepApplier;   // the *_log pool; closed on destroy

    public AuditConsumerConfig(XRodManager rods, Environment env) {
        this.rods = rods;
        this.env  = env;
    }

    /**
     * Open the audit consumer: build the generic keep applier (the audit director's kinds + SQL, applied to the
     * keep datasource group) and hand it to {@code rods.consumer}, which resolves the audit leg + opens the bus
     * consumer running the applier. A missing datasource or a producer-only / absent leg -> the consumer stays idle.
     */
    @Bean
    public IXRod auditConsumer() {
        KeepDataSourceParams ds = Binder.get(env)
                .bind(KEEP_DATASOURCE, Bindable.of(KeepDataSourceParams.class)).orElse(null);
        if (ds == null || ds.url() == null || ds.url().isBlank()) {
            devLog.info("auKeep: no {} configured -- no audit consumer started", KEEP_DATASOURCE);
            return rods.consumer(EsqMsgConstants.BUS_KEY_AUDIT, Role.BROADCAST, e -> { });
        }
        IKeepDirector dir = new AuditKeepDirector();
        this.keepApplier = new KeepApplier(ds, new KeepSqlStore(dir.sqlGroup()), dir.kinds(), devLog);
        devLog.info("auKeep: audit consumer applying to keep datasource (kinds={})", dir.kinds().size());
        return rods.consumer(EsqMsgConstants.BUS_KEY_AUDIT, Role.BROADCAST, keepApplier.applier());
    }

    @PreDestroy
    public void close() {
        if (keepApplier != null) {
            keepApplier.close();
        }
    }
}
