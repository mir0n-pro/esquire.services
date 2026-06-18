/*
 *  Esquire frameworks (tm)
 *  xxRod service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the @Configuration that opens the xxRod audit consumer. It NAMES the audit leg
 *                   by {bus-id, slot-id} (esquire.audit-bus.messaging-bus) into the esquire.messaging-bus
 *                   catalog; the catalog resolves provider + destination + ConsumeSettings; the rodAuditConsumer
 *                   bean opens the leg consumer feeding director::accept via RodTransportAdapter. A producer-only
 *                   transport (supportsConsume()=false) or a missing bus reference -> no consumer (stay idle).
 * 06/17/2026 mir0n  consumeLeg(busId, slotId, objectMapper) -- the Role.BROADCAST argument dropped
 */
package pro.mir0n.esquire.xxRod.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import pro.mir0n.esquire.messaging.MessagingBusCatalog;
import pro.mir0n.esquire.messaging.xrod.RodTransportAdapter;
import pro.mir0n.esquire.xxRod.director.IRodDirector;

@Configuration
public class XxRodAuditConsumerConfig {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + XxRodAuditConsumerConfig.class.getName());

    // The audit bus this xxRod consumes -- the SAME service-level ref the producers publish to (-> catalog leg).
    // bus-id AND slot-id are CONFIG values (the ref), not hardcoded.
    @Value("${esquire.audit-bus.messaging-bus.bus-id:}")   private String busId;
    @Value("${esquire.audit-bus.messaging-bus.slot-id:}")  private String slotId;

    private final IRodDirector director;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public XxRodAuditConsumerConfig(IRodDirector director, ObjectMapper objectMapper, Environment environment) {
        this.director      = director;
        this.objectMapper  = objectMapper;
        this.environment   = environment;
    }

    @Bean(destroyMethod = "close")
    public AutoCloseable rodAuditConsumer() {
        if (busId == null || busId.isBlank()) {
            devLog.info("xxRod: no esquire.audit-bus.messaging-bus reference -- no audit consumer started");
            return () -> { };
        }
        MessagingBusCatalog catalog = new MessagingBusCatalog(environment);
        MessagingBusCatalog.ConsumeLeg leg = catalog.consumeLeg(busId, slotId, objectMapper);
        // A producer-only transport (e.g. redis: the stream IS the log) has no consume leg -- stay idle.
        if (!leg.provider().supportsConsume()) {
            devLog.info("xxRod: transport for bus '{}' is producer-only -- no audit consumer started", busId);
            return () -> { };
        }
        devLog.info("xxRod: opening audit consumer on {} (bus={}, slot={}, concurrency={})",
                leg.destination(), busId, slotId, leg.settings().concurrency());
        return leg.provider().openConsumer(leg.destination(), leg.settings(),
                RodTransportAdapter.handler(director::accept, objectMapper));
    }
}
