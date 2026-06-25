/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created: a generic Actuator HealthIndicator over a SINGLE TransportHealth source (a keep
 *                   datasource, a leg, ...), beside the per-bus BusHealthIndicator. DOWN when the source is
 *                   DOWN; UP otherwise (UNKNOWN is reported as a detail, not failed). Registered programmatically
 *                   (no @Bean) into the Actuator registry; used by auKeep to forward its keep-datasource health.
 */
package pro.mir0n.esquire.messaging;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import java.util.function.Supplier;

/**
 * Forwards a single {@link TransportHealth} source (e.g. a keep datasource connection) to Spring Actuator,
 * beside the per-bus {@link BusHealthIndicator}. DOWN if the source is DOWN; UP otherwise (an UNKNOWN source is
 * reported as a detail but does not fail it). Contribute it to the <b>readiness</b> group, like the bus health.
 * Registered per service via {@link #register} (no {@code @Bean} / {@code @Configuration}).
 */
public class TransportHealthIndicator implements HealthIndicator {

    private final Supplier<TransportHealth> source;

    public TransportHealthIndicator(Supplier<TransportHealth> source) {
        this.source = source;
    }

    @Override
    public Health health() {
        TransportHealth state = source.get();
        Health.Builder builder = state == TransportHealth.DOWN ? Health.down() : Health.up();
        return builder.withDetail("state", state.name()).build();
    }

    /** Register a single-source health contributor under {@code name} into the Actuator registry -- PROGRAMMATIC
     *  (no {@code @Bean}). A no-op if the service has no Actuator health registry. */
    public static void register(ApplicationContext ctx, String name, Supplier<TransportHealth> source) {
        try {
            ctx.getBean(HealthContributorRegistry.class).registerContributor(name, new TransportHealthIndicator(source));
        } catch (NoSuchBeanDefinitionException noActuator) {
            // no Actuator health registry on this service -- nothing to forward to; skip.
        }
    }
}
