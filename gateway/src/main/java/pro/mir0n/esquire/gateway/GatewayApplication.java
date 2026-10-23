/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/08/2026 mir0n @ConfigurationPropertiesScan is required now
 * 04/07/2026 mir0n  EsqObjectKindStorage loaded on startup (required by EntityKindRoutePredicateFactory)
 *                   SpringApplication builder; GatewayApplicationStartingListener added
 * 07/08/2026 mir0n  @Import(TracingConfig.class): the common distributed-tracing wiring (v1.2.11 O2)
 * 08/14/2026 mir0n  v1.2.13 -- @SpringBootApplication + @ConfigurationPropertiesScan split into
 *                   @SpringBootConfiguration + @EnableAutoConfiguration + @Import(GatewayConfig), which now
 *                   carries the component scan and the configuration-properties scan
 * 10/19/2026 mir0n  v2.0.1 -- MessagingBusLifecycleRegistrar, registered last: builds the biz bus at
 *                   environment-prepared, starts it and registers the bus health indicator at ready, closes it at
 *                   context close
 */
package pro.mir0n.esquire.gateway;

import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import pro.mir0n.esquire.backend.o11y.ObservabilityConfig;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.messaging.BusHealthIndicator;
import pro.mir0n.messaging.MessagingBus;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({ObservabilityConfig.class, GatewayConfig.class})
public class GatewayApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + GatewayApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(GatewayApplication.class);
        app.addListeners(new GatewayApplicationStartingListener());
        // the bus lifecycle (build/start/close) in one call -- registered LAST so start() runs after the rest
        app.addListeners(new MessagingBusLifecycleRegistrar());
        app.run(args);
    }

    public static class GatewayApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
        @Override
        public void onApplicationEvent(ApplicationStartingEvent event) {
            boolean result = EsqObjectKindStorage.getInstance().init((String) null);
            if (!result) {
                System.out.println("Failed to load esq-object-kinds.xml");
                System.exit(-1);
            }
            devLog.debug("EsqObjectKindStorage loaded");
        }
    }

    //xxx: the bus health goes to /actuator/health but NOT to the readiness group -- with the broker down the
    // gateway stays ready and only the bus routes answer 503
    public static class MessagingBusLifecycleRegistrar implements ApplicationListener<ApplicationEvent>, Ordered {

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;   // each bus phase runs after the service's same-event listeners
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            MessagingBus bus = MessagingBus.getInstance();
            if (event instanceof ApplicationEnvironmentPreparedEvent e) {
                bus.init(e.getEnvironment(), new String[]{EsqConstants.BUS_KEY_BIZ});
                devLog.debug("MessagingBus initiated (rods built, paused)");
            } else if (event instanceof ApplicationReadyEvent e) {
                bus.start();                             // run them -- traffic flows only from here
                devLog.debug("MessagingBus started (rods running)");
                BusHealthIndicator.register(e.getApplicationContext(), bus);
                devLog.debug("MessagingBus health indicator registered");
            } else if (event instanceof ContextClosedEvent) {
                bus.close();                             // drain in-flight + close transport
                devLog.debug("MessagingBus closed (rods shut down)");
            }
        }
    }
//
 /*
	@Bean
	public RouteLocator esquireRouteConfig(RouteLocatorBuilder routeLocatorBuilder) {
		return routeLocatorBuilder.routes()
            .route(p -> p
                .path("/esq/**")
                .filters( f -> f.rewritePath("/esq/(?<segment>.*)","/${segment}")
                        .addResponseHeader("X-Response-Time", LocalDateTime.now().toString()))
                .uri("lb://BIZTREE"))
                .build();
	}
*/

}
