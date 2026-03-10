/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/08/2026 mir0n @ConfigurationPropertiesScan is required now
 */
package pro.mir0n.esquire.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

//TODO: get Roles with permissions from keySmith
//      use tool id (100 for tree) id instead of role name
//
//TODO: have JWT optionally encrypted
//

@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
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
