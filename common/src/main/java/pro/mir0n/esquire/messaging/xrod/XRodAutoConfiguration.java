/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/14/2026 mir0n  created: registers the ONE shared XRodManager bean for every service that ships common.
 *                   destroyMethod=close shuts down every rod the manager handed out at context close, so a
 *                   service's producer/consumer classes carry NO lifecycle wiring. Registered via
 *                   META-INF/spring/...AutoConfiguration.imports (the same mechanism the tp-* providers use).
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Registers the shared {@link XRodManager} (one per service); close() shuts down all its rods. */
@AutoConfiguration
public class XRodAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public XRodManager xRodManager(Environment environment, ObjectMapper objectMapper) {
        return new XRodManager(environment, objectMapper);
    }
}
