/*
 *  Esquire frameworks (tm)
 *  PAckMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
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
 */

package pro.mir0n.esquire.pacMan;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.pacMan.service.BizValidatorFactory;

@Slf4j
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.pacMan",
        "pro.mir0n.esquire.backend.service",
        "pro.mir0n.esquire.backend.security",
        "pro.mir0n.esquire.backend.exception"
})
@EntityScan(basePackages = "pro.mir0n.esquire.backend.jpa")
@EnableJpaRepositories(basePackages = {
        "pro.mir0n.esquire.pacMan.jpa",
        "pro.mir0n.esquire.backend.storage.roles"
})
public class PacManApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + PacManApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication( PacManApplication.class);
        app.addListeners(new PacManApplicationStartingListener());
        app.addListeners(new PacManApplicationReadyListener());
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

}
