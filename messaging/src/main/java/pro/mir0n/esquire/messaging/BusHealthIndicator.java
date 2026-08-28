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
 * 08/15/2026 mir0n  v1.2.13 -- register() now registers into BOTH health registries: the blocking
 *                   HealthContributorRegistry as before, and the ReactiveHealthContributorRegistry via
 *                   ReactiveHealthContributor.adapt() in registerReactive(), which a WebFlux service reads
 * 08/26/2026 mir0n  registerTransportGauges publishes each bus transport state as messaging.transport.up
 *                   (1 connected, 0 not), so the state reaches Prometheus and not only /actuator/health
 */
package pro.mir0n.esquire.messaging;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.ReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.context.ApplicationContext;
import pro.mir0n.esquire.messaging.o11y.RodObserverHolder;
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

    /** Register a {@code BusHealthIndicator} over {@code bus} into the application's Actuator health registry
     *  under {@link #NAME} -- PROGRAMMATIC registration (no {@code @Bean} / {@code @Configuration}), called
     *  from the per-service {@code MessagingBusLifecycleRegistrar} at {@code ApplicationReadyEvent}, which
     *  hands in the same facade it drives. A no-op if the service has no Actuator health registry (then there
     *  is no /actuator/health to forward to).
     *  <p>
     *  BOTH registries are offered the indicator, because which one is READ depends on the web stack and a
     *  service does not tell this method which it is. See {@link #registerReactive}. */
    public static void register(ApplicationContext ctx, MessagingBus bus) {
        BusHealthIndicator indicator = new BusHealthIndicator(bus);

        try {
            ctx.getBean(HealthContributorRegistry.class).registerContributor(NAME, indicator);
        } catch (NoSuchBeanDefinitionException noBlockingRegistry) {
            // no blocking health registry on this service -- nothing to forward to here; skip.
        }

        registerReactive(ctx, indicator);
        registerTransportGauges(bus);
    }

    private static void registerTransportGauges(MessagingBus bus) {
        Map<String, TransportHealth> buses = bus.health();
        for (Map.Entry<String, TransportHealth> entry : buses.entrySet()) {
            String busKey = entry.getKey();
            RodObserverHolder.meters().registerTransportUp(busKey, () -> up(bus, busKey));
        }
    }

    private static int up(MessagingBus bus, String busKey) {
        int ret = -1;
        TransportHealth health = bus.health().get(busKey);
        if (health == TransportHealth.UP) {
            ret = 1;
        } else if (health == TransportHealth.DOWN) {
            ret = 0;
        }
        return ret;
    }

    /**
     * The same indicator, into the REACTIVE registry.
     *
     * <p><b>Why this is separate, and why it is not optional.</b> A servlet service reads
     * {@code /actuator/health} from the blocking {@link HealthContributorRegistry}; a WebFlux one reads it
     * from the reactive registry instead. Both beans exist in a reactive process, so registering into the
     * blocking one there SUCCEEDS and is then never read -- the indicator is silently absent from the health
     * report and from the readiness group that names it. Nothing throws and nothing logs, so the only symptom
     * is a service that quietly fails to depool when its broker goes down.
     *
     * <p>Boot's {@code adapt} is what bridges the two: it wraps this blocking contributor so the reactive
     * endpoint can call it. Registering at startup as a {@code @Bean} would have been adapted automatically;
     * a contributor registered LATE, as this one is, is not.
     *
     * <p>The reactive types are touched only inside this method, so a servlet service -- which may not carry
     * Reactor at all -- never resolves them: the {@code getBean} above it fails first and the rest is never
     * reached.
     */
    private static void registerReactive(ApplicationContext ctx, BusHealthIndicator indicator) {
        try {
            ctx.getBean(ReactiveHealthContributorRegistry.class)
               .registerContributor(NAME, ReactiveHealthContributor.adapt(indicator));
        } catch (NoSuchBeanDefinitionException noReactiveRegistry) {
            // a servlet service -- there is no reactive registry, and the blocking one above is the live one.
        }
    }
}
