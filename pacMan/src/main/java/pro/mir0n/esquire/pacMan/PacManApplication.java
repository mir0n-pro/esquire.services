/*
 *  Esquire frameworks (tm)
 *  PAckMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/28/2025 mir0n logging added using Slf4j
 * 01/23/2026 mir0n explicitly EntityScan, EnableJpaRepositories
 * 02/12/2026 mir0n  initiate EsqObjectKindStorage
 * 03/06/2026 mir0n  ValidatorFactory.init(BizValidatorFactory) called on startup
 *                   EnyManApplicationStartingListener renamed PacManApplicationStartingListener
 * 03/09/2026 mir0n  EsqRolesStorage.init() via ApplicationReadyEvent listener
 *                   @EnableJpaRepositories extended with backend.storage.roles
 * 03/10/2026 mir0n  scanBasePackages: backend.service, backend.security, backend.exception added
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 * 04/09/2026 mir0n  @EntityScan + @EnableJpaRepositories extended to pro.mir0n.esquire.pacMan.acct.jpa
 * 06/22/2026 mir0n  the 3 separate bus listeners collapsed into ONE MessagingBusLifecycleRegistrar (Ordered
 *                   LOWEST_PRECEDENCE, registered last): env-prepared -> bus.init(env, {BUS_KEY_ENTITY,
 *                   BUS_KEY_AUDIT}); ready -> bus.start(); context-closed -> bus.close(). ReadyListener keeps
 *                   only the roles load.
 * 06/22/2026 mir0n  registrar registers a BusHealthIndicator (bus facade handed in) into the Actuator
 *                   HealthContributorRegistry programmatically at ApplicationReadyEvent (no @Bean) -> /actuator/health
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 */

package pro.mir0n.esquire.pacMan;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.BusHealthIndicator;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.pacMan.service.BizValidatorFactory;

@Slf4j
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.pacMan",
        "pro.mir0n.esquire.backend.service",
        "pro.mir0n.esquire.backend.security",
        "pro.mir0n.esquire.backend.exception"
})
@EntityScan(basePackages = {"pro.mir0n.esquire.backend.jpa", "pro.mir0n.esquire.pacMan.acct.jpa"})
@EnableJpaRepositories(basePackages = {
        "pro.mir0n.esquire.pacMan.jpa",
        "pro.mir0n.esquire.pacMan.acct.jpa",
        "pro.mir0n.esquire.backend.storage.roles"
})
public class PacManApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + PacManApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication( PacManApplication.class);
        app.addListeners(new PacManApplicationStartingListener());
        app.addListeners(new PacManApplicationReadyListener());
        // the bus lifecycle (build/start/close) in one call -- registered LAST so start() runs after roles load.
        app.addListeners(new MessagingBusLifecycleRegistrar());

        app.run(args);
    }

    public static class PacManApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
        @Override
        public void onApplicationEvent(ApplicationStartingEvent event) {
            devLog.debug("ApplicationStartingEvent received: {}", event.getTimestamp());

            boolean result = EsqObjectKindStorage.getInstance().init((String)null);
            if (!result) {
                System.out.println("Failed to load esq-object-kinds.xml");
                System.exit(-1); // Exit the JVM immediately
            }
            devLog.debug("EsqObjectKindStorage loaded");

            result = EsqEntityDictionaryStorage.getInstance().init((String)null);
            if (!result) {
                System.out.println("Failed to load esq-entity-dictionaries.xml");
                System.exit(-1); // Exit the JVM immediately
            }
            devLog.debug("EsqEntityDictionaryStorage loaded");
            ValidatorFactory.getInstance().init(BizValidatorFactory.getBizValidators());
        }
    }

    public static class PacManApplicationReadyListener implements ApplicationListener<ApplicationReadyEvent> {
        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            JpaRolesRepository repo = event.getApplicationContext().getBean(JpaRolesRepository.class);
            boolean result = EsqRolesStorage.getInstance().init(repo);
            if (!result) {
                System.out.println("Failed to load EsqRolesStorage");
                System.exit(-1);
            }
            devLog.debug("EsqRolesStorage loaded");
        }
    }


    public static class MessagingBusLifecycleRegistrar implements ApplicationListener<ApplicationEvent>, Ordered {

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;   // each bus phase runs after the service's same-event listeners
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            MessagingBus bus = MessagingBus.getInstance();
            if (event instanceof ApplicationEnvironmentPreparedEvent e) {
                bus.init(e.getEnvironment(), new String[]{EsqConstants.BUS_KEY_ENTITY, EsqConstants.BUS_KEY_AUDIT});
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
