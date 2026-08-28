/*
 *  Esquire frameworks (tm)
 *  gateWard service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/14/2026 mir0n  created: the gateway and the bizTree CACHE in ONE process. Imports GatewayConfig and
 *                   BizTreeCacheConfig, scans gateWard's own handlers and read scheduler, loads the object
 *                   kinds at start-up, and runs one MessagingBusLifecycleRegistrar over the entity bus. No
 *                   web starter comes in with the cache, so the process stays WebFlux
 * 08/17/2026 mir0n  v1.2.13 T3.2 -- @Bean IMeterOwner meterOwner(entityBusId) declared here: a
 *                   GateWardMeterOwner, handed the entity bus id from the property the bus itself reads
 */

package pro.mir0n.esquire.gateWard;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import pro.mir0n.esquire.backend.o11y.IMeterOwner;
import pro.mir0n.esquire.backend.o11y.ObservabilityConfig;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.bizTree.BizTreeCacheConfig;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.gateway.GatewayConfig;
import pro.mir0n.esquire.messaging.BusHealthIndicator;
import pro.mir0n.esquire.messaging.MessagingBus;

/**
 * gateWard -- the gate and the ward behind it: the gateway with the bizTree CACHE in the same process.
 * <p>
 * The gate comes in as its own {@code @Configuration}, so its routes, filters, reactive security chain and
 * token relay are exactly the ones the gateway service runs. bizTree comes in as the CACHE only --
 * {@link BizTreeCacheConfig} names the director, the monads, the H2 cache and its loader, the JPA read side
 * and the entity-bus consumer. Its controller and its servlet security stay behind, and with them
 * {@code spring-boot-starter-web}: a servlet starter on this classpath would make Boot choose a servlet
 * context and Spring Cloud Gateway would not start at all.
 * <p>
 * What gateWard adds of its own is small: five handlers that answer the tree routes from the cache instead of
 * proxying them, and the scheduler those reads run on.
 * <p>
 * <b>The state it holds changed kind.</b> The gate already carried state: the object kinds, loaded at start
 * from a file in the image, and the per-instance token relay cache. Both look after themselves -- the kinds
 * are there before anything can ask, and a cold relay cache fills itself from the next request at the cost of
 * one round trip. The tree cache does neither: it is read out of the database at start, kept in step by the
 * entity topic, and has no miss path, so a cold copy answers with an empty tree -- wrong rather than slow.
 * That is what the cache readiness gate is for: until the cache has loaded, this process reports itself not
 * ready and takes no traffic.
 */
@Slf4j
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({ObservabilityConfig.class, GatewayConfig.class, BizTreeCacheConfig.class})
@ComponentScan(basePackages = "pro.mir0n.esquire.gateWard",
        excludeFilters = @ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = GateWardApplication.class))
public class GateWardApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + GateWardApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(GateWardApplication.class);
        app.addListeners(new GateWardApplicationStartingListener());
        // the bus lifecycle (build/start/close) in one call -- registered LAST so start() runs after the rest
        app.addListeners(new MessagingBusLifecycleRegistrar());

        app.run(args);
    }

    /** Whether a meter is the gate's or the cache's. Wired here, in the process that composes them; a service
     *  standing alone contributes none and every one of its meters carries its own name. The entity bus id
     *  comes from the same property the bus itself reads. */
    @Bean
    public IMeterOwner meterOwner(@Value("${esquire.entity-bus.messaging-bus.bus-id:}") String entityBusId) {
        return new GateWardMeterOwner(entityBusId);
    }

    /** The object kinds both halves read; the gateway loads them for its route predicate, bizTree for the cache. */
    public static class GateWardApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
        @Override
        public void onApplicationEvent(ApplicationStartingEvent event) {
            boolean result = EsqObjectKindStorage.getInstance().init((String) null);
            if (!result) {
                System.out.println("Failed to load esq-object-kinds.xml");
                System.exit(-1);       // Exit the JVM immediately
            }
            devLog.debug("EsqObjectKindStorage loaded");
        }
    }

    /** One registrar for the process, over the one bus the ward speaks: entity broadcasts feeding the cache. */
    public static class MessagingBusLifecycleRegistrar implements ApplicationListener<ApplicationEvent>, Ordered {

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;   // each bus phase runs after the service's same-event listeners
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            MessagingBus bus = MessagingBus.getInstance();
            if (event instanceof ApplicationEnvironmentPreparedEvent e) {
                bus.init(e.getEnvironment(), new String[]{EsqConstants.BUS_KEY_ENTITY});
                devLog.debug("MessagingBus initiated (rods built, paused)");
            } else if (event instanceof ApplicationReadyEvent e) {
                bus.start();                             // run them -- traffic flows only from here
                devLog.debug("MessagingBus started (rods running)");
                BusHealthIndicator.register(e.getApplicationContext(), bus);   // forward bus connection health to /actuator/health (no @Bean)
                devLog.debug("MessagingBus health indicator registered");
            } else if (event instanceof ContextClosedEvent) {
                bus.close();                             // drain in-flight + close transport
                devLog.debug("MessagingBus closed (rods shut down)");
            }
        }
    }
}
