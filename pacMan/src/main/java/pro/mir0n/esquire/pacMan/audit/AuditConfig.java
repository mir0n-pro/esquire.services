/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created: pacMan audit-logging wiring (esq_account_log on account update / balance).
 * 06/13/2026 mir0n  class-name-driven transport over the unified esquire.audit.* block.
 * 06/14/2026 mir0n  bus-oriented: reads esquire.audit.mode (disabled | log-db | messaging-bus).
 * 06/14/2026 mir0n  log-db is now the XRodLogDb pod (resolved by rod-class; self-configures from the leg's
 *                   x-rod.log-db). So this config no longer wires the datasource / *_log registry: bus ->
 *                   AuditRod; log-db / disabled -> XRodLogDb (a no-op pod when disabled).
 * 06/15/2026 mir0n  resolves the audit producer through the shared XRodManager: xRod() returns
 *                   rods.producer(BUS_KEY_AUDIT, Role.BROADCAST); the leg's rod-class selects the pod
 *                   (bus / log-db / disabled), so the injected IXRod is never null. No local datasource /
 *                   JMS / Redis / Kafka wiring or @PreDestroy lifecycle here.
 * 06/17/2026 mir0n  @Bean AuditBusBridge audit() wrapping rods.producer(BUS_KEY_AUDIT, BROADCAST) (was @Bean IXRod xRod())
 */
package pro.mir0n.esquire.pacMan.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.audit.AuditBusBridge;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

@Configuration
public class AuditConfig {

    private final XRodManager rods;

    public AuditConfig(XRodManager rods) {
        this.rods = rods;
    }

    /**
     * The audit bridge -- the audit module's entry point onto the messaging bus. It wraps a plain producer on
     * the "audit-bus" collaboration (the service-level ref maps it to a catalog leg + the post msg-type UA) and
     * relays each posted change after the caller's transaction commits. The leg's {@code rod-class} is the
     * selector: XRod = bus (transmit to xxRod), XRodLogDb = in-process *_log, XRodInfo = log-only. Audit OFF --
     * no audit-bus leg, or rod-class = XRodDisabled -- resolves to the OFF x-rod, so the bridge is never null.
     */
    @Bean
    public AuditBusBridge audit() {
        return new AuditBusBridge(rods.producer(EsqMsgConstants.BUS_KEY_AUDIT, Role.BROADCAST));
    }
}
