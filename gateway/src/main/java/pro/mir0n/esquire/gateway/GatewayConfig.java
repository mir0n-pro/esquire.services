/*
 *  Esquire frameworks (tm)
 *  gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/14/2026 mir0n  created: the gateway's @ComponentScan and @ConfigurationPropertiesScan, the one place its
 *                   packages are named; excludes GatewayApplication
 */

package pro.mir0n.esquire.gateway;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * The gateway, as a set of beans: the route predicate factory, the filters, the reactive security chain and
 * the token relay.
 * <p>
 * Everything that says WHICH packages make up the gateway lives here and nowhere else, so a process that
 * wants the gate inside it imports this one class instead of copying the list.
 * {@code GatewayApplication} is the gateway PROCESS: a main() and the startup listener.
 * <p>
 * The application class is excluded from the scan on purpose. Standing alone it is the primary source and is
 * registered directly, so the exclusion changes nothing; inside a composed process it must not be picked up
 * at all.
 */
@Configuration
@ComponentScan(
        basePackages = "pro.mir0n.esquire.gateway",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = GatewayApplication.class))
@ConfigurationPropertiesScan(basePackages = "pro.mir0n.esquire.gateway")
public class GatewayConfig {
}
