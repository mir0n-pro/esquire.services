/*
 *  Esquire frameworks (tm)
 *  tp-redis -- Redis transport provider
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/14/2026 mir0n  created: suppress Spring Boot's Redis auto-configuration. Transport is owned by the
 *                   messaging-bus catalog (x-rod.transport) + this provider, NOT Spring Boot -- a service that
 *                   depends on tp-redis stays transport-agnostic (no stray RedisConnectionFactory / Redis
 *                   health probe wired from spring.* defaults; the provider builds its own Lettuce template).
 *                   Registered as an AutoConfigurationImportFilter via META-INF/spring.factories.
 */
package pro.mir0n.esquire.tp.redis;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

import java.util.Set;

/** Filters Boot's Redis auto-configuration out of any application that ships this provider. */
public class TpRedisAutoConfigFilter implements AutoConfigurationImportFilter {

    private static final Set<String> SUPPRESSED = Set.of(
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration");

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
