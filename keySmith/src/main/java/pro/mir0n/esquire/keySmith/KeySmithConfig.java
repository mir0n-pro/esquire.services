/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/12/2026 mir0n  created: keySmith's @ComponentScan / @EntityScan / @EnableJpaRepositories, the one place its
 *                   packages are named; excludes KeySmithApplication and AuditConfig from the scan
 */

package pro.mir0n.esquire.keySmith;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import pro.mir0n.esquire.keySmith.audit.AuditConfig;

/**
 * The keySmith service, as a set of beans.
 * <p>
 * Everything that says WHICH packages make up keySmith lives here and nowhere else, so a process that
 * wants keySmith inside it imports this one class instead of copying the lists.
 * {@code KeySmithApplication} is the keySmith PROCESS: a main(), the startup listeners and the bus
 * lifecycle.
 * <p>
 * The application class is excluded from the scan on purpose. Standing alone it is the primary source
 * and is registered directly, so the exclusion changes nothing; inside a composed process it must not
 * be picked up at all.
 * <p>
 * The roles repository ({@code backend.storage.roles}) is NOT here -- it is shared with enyMan, so
 * whichever process runs keySmith declares it once. {@code AuditConfig} is out for the same reason: there is
 * ONE audit bridge per PROCESS, and it is injected by type, so the process declares it.
 */
@Configuration
@ComponentScan(
        basePackages = {
                "pro.mir0n.esquire.keySmith",
                "pro.mir0n.esquire.backend.service",
                "pro.mir0n.esquire.backend.security",
                "pro.mir0n.esquire.backend.exception"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {KeySmithApplication.class, AuditConfig.class}))
@EntityScan(basePackages = "pro.mir0n.esquire.backend.jpa")
@EnableJpaRepositories(basePackages = "pro.mir0n.esquire.keySmith.jpa")
public class KeySmithConfig {
}
