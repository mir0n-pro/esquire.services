/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/28/2025 mir0n logging added using Slf4j
 * 01/23/2026 mir0n explicitly EntityScan, EnableJpaRepositories
 * 02/12/2026 mir0n initiate EsqObjectKindStorage
 * 03/06/2026 mir0n ValidatorFactory.getInstance().init() called on startup
 * 03/09/2026 mir0n  EsqRolesStorage.init() via ApplicationReadyEvent listener
 *                   @EnableJpaRepositories extended with backend.storage.roles
 * 03/10/2026 mir0n  scanBasePackages: backend.service, backend.security, backend.exception added
 */

package pro.mir0n.esquire.enyMan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;

@Slf4j
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.enyMan",
        "pro.mir0n.esquire.backend.service",
        "pro.mir0n.esquire.backend.security",
        "pro.mir0n.esquire.backend.exception"
})
@EntityScan(basePackages = "pro.mir0n.esquire.backend.jpa")
@EnableJpaRepositories(basePackages = {
        "pro.mir0n.esquire.enyMan.jpa",
        "pro.mir0n.esquire.backend.storage.roles"
})
public class EnyManApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication( EnyManApplication.class);
        // Register the listener with the SpringApplication instance
        app.addListeners(new EnyManApplicationStartingListener());
        app.addListeners(new EnyManApplicationReadyListener());
        app.run(args);
    }

    public static class EnyManApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
        @Override
        public void onApplicationEvent(ApplicationStartingEvent event) {
            log.debug("ApplicationStartingEvent received: {}", event.getTimestamp());

            boolean result = EsqObjectKindStorage.getInstance().init((String)null);
            if (!result) {
                System.out.println("Failed to load esq-object-kinds.xml");
                System.exit(-1); // Exit the JVM immediately
            }
            log.debug("EsqObjectKindStorage loaded");

            result = EsqEntityDictionaryStorage.getInstance().init((String)null);
            if (!result) {
                System.out.println("Failed to load esq-entity-dictionaries.xml");
                System.exit(-1); // Exit the JVM immediately
            }
            log.debug("EsqEntityDictionaryStorage loaded");
            ValidatorFactory.getInstance().init(null);
        }
    }

    public static class EnyManApplicationReadyListener implements ApplicationListener<ApplicationReadyEvent> {
        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            JpaRolesRepository repo = event.getApplicationContext().getBean(JpaRolesRepository.class);
            boolean result = EsqRolesStorage.getInstance().init(repo);
            if (!result) {
                System.out.println("Failed to load EsqRolesStorage");
                System.exit(-1);
            }
            log.debug("EsqRolesStorage loaded");
        }
    }

}
