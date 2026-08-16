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
 */
package pro.mir0n.esquire.gateway;

import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Import;
import pro.mir0n.esquire.backend.o11y.ObservabilityConfig;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

//TODO: get Roles with permissions from keySmith
//      use tool id (100 for tree) id instead of role name
//
//TODO: have JWT optionally encrypted
//

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({ObservabilityConfig.class, GatewayConfig.class})
public class GatewayApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + GatewayApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(GatewayApplication.class);
        app.addListeners(new GatewayApplicationStartingListener());
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
