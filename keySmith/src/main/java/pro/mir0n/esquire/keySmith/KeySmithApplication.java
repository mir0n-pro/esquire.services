/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/06/2026 mir0n ValidatorFactory.init(BizValidatorFactory) called on startup
 * 03/09/2026 mir0n  EsqRolesStorage.init() via ApplicationReadyEvent listener
 *                   @EnableJpaRepositories extended with backend.storage.roles
 * 03/10/2026 mir0n  scanBasePackages: backend.service, backend.security, backend.exception added
 * 03/16/2026 mir0n  @EnableAsync added (virtual thread async for KeycloakIdentityService)
 * 03/20/2026 mir0n  @EnableAsync removed; KeycloakIdentityService removed (moved to kcMaster)
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 */

package pro.mir0n.esquire.keySmith;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.keySmith.service.BizValidatorFactory;

@Slf4j
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.keySmith",
        "pro.mir0n.esquire.backend.service",
        "pro.mir0n.esquire.backend.security",
        "pro.mir0n.esquire.backend.exception"
})
@EntityScan(basePackages = "pro.mir0n.esquire.backend.jpa")
@EnableJpaRepositories(basePackages = {
        "pro.mir0n.esquire.keySmith.jpa",
        "pro.mir0n.esquire.backend.storage.roles"
})
public class KeySmithApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KeySmithApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication( KeySmithApplication.class);
        // Register the listener with the SpringApplication instance
        app.addListeners(new keySmithApplicationStartingListener());
        app.addListeners(new KeySmithApplicationReadyListener());
        app.run(args);
    }

    public static class keySmithApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
        @Override
        public void onApplicationEvent(ApplicationStartingEvent event) {
            devLog.debug("ApplicationStartingEvent received: {}", event.getTimestamp());
            boolean result = EsqEntityDictionaryStorage.getInstance().init((String)null);
            if (!result) {
                System.out.println("Failed to load esq-entity-dictionaries.xml");
                System.exit(-1); // Exit the JVM immediately
            }
            devLog.debug("EsqEntityDictionaryStorage loaded");
            ValidatorFactory.getInstance().init(BizValidatorFactory.getBizValidators());
        }
    }

    public static class KeySmithApplicationReadyListener implements ApplicationListener<ApplicationReadyEvent> {
        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            JpaRolesRepository repo = event.getApplicationContext().getBean(JpaRolesRepository.class);
            boolean result = EsqRolesStorage.getInstance().init(repo);
            if (!result) {
                System.out.println("Failed to load EsqRolesStorage");
                System.exit(-1); // Exit the JVM immediately
            }
            devLog.debug("EsqRolesStorage loaded");
        }
    }
}
