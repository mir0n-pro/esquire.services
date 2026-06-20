/*
 *  Esquire frameworks (tm)
 *  tp-kafka -- Kafka transport provider
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/14/2026 mir0n  created: suppress Spring Boot's Kafka auto-configuration. Transport is owned by the
 *                   messaging-bus catalog (x-rod.transport) + this provider, NOT Spring Boot -- a service that
 *                   depends on tp-kafka stays transport-agnostic (no stray KafkaTemplate wired from spring.*
 *                   defaults; the provider builds its own producer/consumer factories). Registered as an
 *                   AutoConfigurationImportFilter via META-INF/spring.factories.
 */
package pro.mir0n.esquire.tp.kafka;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

import java.util.Set;

/** Filters Boot's Kafka auto-configuration out of any application that ships this provider. */
public class TpKafkaAutoConfigFilter implements AutoConfigurationImportFilter {

    private static final Set<String> SUPPRESSED = Set.of(
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata metadata) {
        boolean[] ret = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            String candidate = autoConfigurationClasses[i];
            ret[i] = candidate == null || !SUPPRESSED.contains(candidate);
        }
        return ret;
    }
}
