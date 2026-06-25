/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created: the Actuator HealthIndicator that forwards the messaging bus's per-bus connection
 *                   health (MessagingBus.health()) to /actuator/health. DOWN if any bus connection is down;
 *                   UNKNOWN buses (a transport that cannot observe its connection) are reported but do NOT fail
 *                   it. A service registers it programmatically via register(ctx,bus) (no @Bean) into the
 *                   Actuator HealthContributorRegistry and adds it to the READINESS group (not liveness), so a
 *                   broker outage depools the pod rather than killing it.
 */
package pro.mir0n.esquire.messaging;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import java.util.Map;

/**
 * Forwards the messaging bus's connection health to Spring Actuator. Reads {@link MessagingBus#health()} (the
 * per-bus {@code busKey -> TransportHealth} map) and reports:
 * <ul>
 *   <li><b>DOWN</b> if any bus's connection is DOWN -- contribute this indicator to the <b>readiness</b> group,
 *       so a broker outage depools the pod (k8s readiness), never the liveness probe;</li>
 *   <li><b>UP</b> otherwise -- a bus that cannot observe its connection reports {@code UNKNOWN} as a detail but
 *       does NOT fail the indicator (the framework does not fake confidence it lacks).</li>
 * </ul>
 * Each bus's state is a health detail. Registered per service programmatically via {@link #register} (no
 * {@code @Bean}, no auto-config).
 */
public class BusHealthIndicator implements HealthIndicator {

    /** The contributor name under which the indicator registers -- referenced by the readiness health group. */
    public static final String NAME = "messagingBus";

    /** The facade whose per-bus health this forwards -- passed in EXPLICITLY (not reached via the singleton),
     *  so the indicator's dependency on the bus is visible, not hidden global wiring. */
    private final MessagingBus bus;

    public BusHealthIndicator(MessagingBus bus) {
        this.bus = bus;
    }

    @Override
    public Health health() {
        Map<String, TransportHealth> buses = bus.health();
        boolean anyDown = buses.values().stream().anyMatch(h -> h == TransportHealth.DOWN);
        Health.Builder builder = anyDown ? Health.down() : Health.up();
        buses.forEach((busKey, h) -> builder.withDetail(busKey, h.name()));
        return builder.build();
    }

    /** Register a {@code BusHealthIndicator} over {@code bus} into the application's Actuator
     *  {@link HealthContributorRegistry} under {@link #NAME} -- PROGRAMMATIC registration (no {@code @Bean} /
     *  {@code @Configuration}), called from the per-service {@code MessagingBusLifecycleRegistrar} at
     *  {@code ApplicationReadyEvent}, which hands in the same facade it drives. A no-op if the service has no
     *  Actuator health registry (then there is no /actuator/health to forward to). */
    public static void register(ApplicationContext ctx, MessagingBus bus) {
        try {
            ctx.getBean(HealthContributorRegistry.class).registerContributor(NAME, new BusHealthIndicator(bus));
        } catch (NoSuchBeanDefinitionException noActuator) {
            // no Actuator health registry on this service -- nothing to forward to; skip.
        }
    }
}
