/*
 *  Esquire frameworks (tm)
 *  KeySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created (was enyMan.rod.RodConfig): keySmith audit-logging wiring.
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
 * 06/22/2026 mir0n  the audit @Bean now wraps the audit-bus x-rod the FACADE builds: getXRod(BUS_KEY_AUDIT). The
 *                   audit-bus ref's role (SERVER) + AUDIT_BUS_ID drive the sink (in-process keep audit-b / bus
 *                   producer audit-c / audit-off XRodDisabled); the per-leg sink-selection logic + the service
 *                   DataSource inject are gone (the keep owns its datasource; the leg owns the sink).
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 */
package pro.mir0n.esquire.keySmith.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.MessagingBus;

@Configuration
public class AuditConfig {

    /** The audit bridge over the audit-bus x-rod the facade built from the audit-bus ref (role SERVER): an
     *  in-process keep (audit-b) or a bus producer (audit-c), per AUDIT_BUS_ID. AUDIT_BUS_ID=audit-off picks an
     *  explicit XRodDisabled (a no-op) to run with audit off; the audit rod is always built, so the bridge always wires. */
    @Bean
    public AuditBusBridge audit() {
        return new AuditBusBridge(MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_AUDIT));
    }
}
