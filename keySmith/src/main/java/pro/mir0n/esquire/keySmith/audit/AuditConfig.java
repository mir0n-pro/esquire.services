/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created: keySmith audit-logging wiring (esq_auth_log on access-profile update).
 * 06/13/2026 mir0n  class-name-driven transport over the unified esquire.audit.* block.
 * 06/14/2026 mir0n  bus-oriented: reads esquire.audit.mode (disabled | log-db | messaging-bus).
 * 06/14/2026 mir0n  log-db is now the XRodLogDb pod (resolved by rod-class; self-configures from the leg's
 *                   x-rod.log-db). So this config no longer wires the datasource / *_log registry: bus ->
 *                   AuditRod; log-db / disabled -> XRodLogDb (a no-op pod when disabled).
 * 06/15/2026 mir0n  resolves the audit producer through the shared XRodManager: ctor takes XRodManager;
 *                   xRod() returns rods.producer(BUS_KEY_AUDIT, Role.BROADCAST). Dropped the @Value config
 *                   block, the DataSource / JmsTemplate / Redis / Kafka publisher wiring, kindToSqlKey(),
 *                   and the @PreDestroy teardown (the manager owns the leg lifecycle).
 */
package pro.mir0n.esquire.keySmith.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

@Configuration
public class AuditConfig {

    private final XRodManager rods;

    public AuditConfig(XRodManager rods) {
        this.rods = rods;
    }

    /**
     * The audit rod -- a plain producer on the "audit-bus" collaboration (the service-level ref maps it to a
     * catalog leg + the post msg-type UA). The leg's {@code rod-class} is the selector: XRod = bus (transmit to
     * xxRod), XRodLogDb = in-process *_log, XRodInfo = log-only. Audit OFF -- no audit-bus leg, or
     * rod-class = XRodDisabled -- resolves to the OFF pod, so the injected IXRod is never null.
     */
    @Bean
    public IXRod xRod() {
        return rods.producer(EsqMsgConstants.BUS_KEY_AUDIT, Role.BROADCAST);
    }
}
