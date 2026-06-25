/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created (the AuditBusBridge bean moved out of the retired EnyManMessaging): the audit @Bean
 *                   wraps the audit-bus x-rod the FACADE builds -- getXRod(BUS_KEY_AUDIT). The audit-bus ref's
 *                   role (SERVER) + AUDIT_BUS_ID drive the sink (in-process keep audit-b / bus producer audit-c /
 *                   audit-off XRodDisabled); no per-leg sink selection logic here.
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 */
package pro.mir0n.esquire.enyMan.audit;

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
