/*
 *  Esquire frameworks (tm)
 *  xxRod service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the AUDIT director -- the first IRodDirector. Hands each event to the reused
 *                   common.xrod worker pool, which applies it via the kind->IRodEventRepo registry
 *                   (-> AuditLogWriter -> *_log). No ordering/grouping; parallelism = the pool size, itself
 *                   kept <= the audit-DB connection-pool size.
 * 06/06/2026 mir0n  self-configuring: gated by xxrod.director.type=audit (default); init() reads its OWN
 *                   xxrod.director.audit.* properties (pool-size, virtual-threads) and the active vendor,
 *                   then builds the AuditLogWriter + AuditKinds registry + the receive pod. shutdown() stops it.
 * 06/13/2026 mir0n  one transceiver: the apply side is now a RECEIVE-ONLY x-Rod (the configured rod-class,
 *                   default XRod) -- outbound=none, receive worker = the registry applier. submit() feeds it.
 * 06/15/2026 mir0n  folded onto the shared bus catalog: imports moved common.xrod -> messaging.xrod
 *                   (RodEvent / RodEventRepoRegistry / IXRod / XRods); apply-pool params (pool-size /
 *                   rod-class / virtual-threads) resolved from the audit leg via MessagingBusCatalog.resolve
 *                   ({bus-id, slot-id} from esquire.audit.messaging-bus), defaulting when no leg; the XXRod
 *                   pool replaced by IXRod rod = XRods.resolve(rodClass) started with registry.applier.
 */
package pro.mir0n.esquire.xxRod.director;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.audit.AuditKinds;
import pro.mir0n.esquire.common.audit.AuditLogWriter;
import pro.mir0n.esquire.messaging.MessagingBusCatalog;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.RodEventRepoRegistry;
import pro.mir0n.esquire.messaging.xrod.XRods;

import javax.sql.DataSource;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "xxrod.director", name = "type",
        havingValue = IRodDirector.TYPE_AUDIT, matchIfMissing = true)
public class AuditRodDirector implements IRodDirector {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + AuditRodDirector.class.getName());

    private final DataSource dataSource;
    private final String appName;
    private IXRod rod;

    public AuditRodDirector(DataSource dataSource,
                            @org.springframework.beans.factory.annotation.Value("${spring.application.name}") String appName) {
        this.dataSource = dataSource;
        this.appName    = appName;
    }

    @Override
    public String type() {
        return TYPE_AUDIT;
    }

    @Override
    public void init(Environment env) {
        // The apply pool params (pool-size / rod-class / virtual-threads) come from the audit leg in the
        // messaging-bus catalog -- the same {bus-id, slot-id} the consumer opens; defaults if no leg.
        int     poolSize       = 8;
        boolean virtualThreads = false;
        String  rodClass       = "XRod";
        String  busId          = env.getProperty("esquire.audit.messaging-bus.bus-id", "");
        String  slotId      = env.getProperty("esquire.audit.messaging-bus.slot-id", "");
        if (!busId.isBlank() && !slotId.isBlank()) {
            XRodParams leg = new MessagingBusCatalog(env).resolve(busId, slotId);
            poolSize       = leg.poolSizeOr(8);
            virtualThreads = leg.virtualThreadsOrFalse();
            rodClass       = leg.rodClassOr("XRod");
        }
        String  profile        = env.getProperty("spring.profiles.active", "dev-postgres");
        boolean oracle         = profile.contains("oracle");

        AuditLogWriter writer = new AuditLogWriter(dataSource, oracle);
        RodEventRepoRegistry registry = new RodEventRepoRegistry();
        AuditKinds.all(EsqObjectKindStorage.getInstance())
                .forEach((kind, sqlKey) -> registry.register(kind, e -> writer.applyEvent(sqlKey, e)));

        // a receive pool fed by the host's transport consumer (accept -> submit). Transport-less params + no codec
        // (objectMapper=null) -> the pod runs in-process (it does not open its own bus consumer); the pool applies
        // each event via the registry. The leg identity names the msg-audit logger (msg.<bus-id>.<slot-id>).
        XRodParams eff = XRodParams.from(Map.of("pool-size", poolSize, "virtual-threads", virtualThreads))
                .withBus(busId.isBlank() ? null : busId, slotId.isBlank() ? null : slotId, null);
        rod = XRods.resolve(rodClass);
        rod.configure(eff, Role.BROADCAST, null);
        rod.start(appName, devLog, registry.applier(devLog));
        devLog.info("audit director init: rod={}, poolSize={}, virtual={}, oracle={}", rodClass, poolSize, virtualThreads, oracle);
    }

    @Override
    public void accept(RodEvent event) {
        rod.submit(event);
    }

    @Override
    public void shutdown() {
        if (rod != null) {
            rod.shutdown();
        }
    }
}
