/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/12/2026 mir0n  created: kcMaster's @ComponentScan, the one place its packages are named; excludes
 *                   KcMasterApplication; declares the KcIdentityGateway bean
 */

package pro.mir0n.esquire.kcMaster;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import pro.mir0n.esquire.kcMaster.identity.KcIdentityGateway;

/**
 * The kcMaster service, as a set of beans.
 * <p>
 * Everything that says WHICH packages make up kcMaster lives here and nowhere else, so a process that
 * wants kcMaster inside it imports this one class instead of copying the list.
 * {@code KcMasterApplication} is the kcMaster PROCESS: a main(), the startup listener and the bus
 * lifecycle.
 * <p>
 * The application class is excluded from the scan on purpose. Standing alone it is the primary source
 * and is registered directly, so the exclusion changes nothing; inside a composed process it must not
 * be picked up at all.
 * <p>
 * This is the WHOLE service, bus consumer and topic adapter included -- what the kcMaster process runs.
 * A process reaching kcMaster through the identity gateway wants a narrower set than this.
 */
@Configuration
@ComponentScan(
        basePackages = "pro.mir0n.esquire.kcMaster",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = KcMasterApplication.class))
public class KcMasterConfig {

    /**
     * The identity workflow, in the one place it lives. kcMaster's two bus adapters hand it what arrives and
     * transmit what it answers; they hold no identity logic of their own, so the service and a process that
     * composes kcMaster run the same code rather than two copies that have to be kept in step.
     *
     * <p>It builds what it needs -- the KeyCloak admin client, the path park, the identity service and the
     * request handler -- from {@code keycloak.admin.*} and {@code kcmaster.path-buffer.*}.
     *
     * <p>The adapters serve on the rod's own worker pool, so how many syncs a pod runs at once is still the
     * pool's business ({@code KC_REQUEST_POOL_SIZE}); the gateway's queue carries work only where something
     * posts onto it.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public KcIdentityGateway identityGateway(Environment env) {
        return new KcIdentityGateway(env);
    }
}
