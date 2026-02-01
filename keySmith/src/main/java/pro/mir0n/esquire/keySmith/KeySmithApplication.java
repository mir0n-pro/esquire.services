/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.keySmith;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;

@Slf4j
@SpringBootApplication
@EntityScan(basePackages = "pro.mir0n.esquire.backend.jpa")
@EnableJpaRepositories(basePackages = "pro.mir0n.esquire.keySmith.jpa")
public class KeySmithApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication( KeySmithApplication.class);
        // Register the listener with the SpringApplication instance
        app.addListeners(new keySmithApplicationStartingListener());
        app.run(args);
    }

    public static class keySmithApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
        @Override
        public void onApplicationEvent(ApplicationStartingEvent event) {
            log.debug("ApplicationStartingEvent received: {}", event.getTimestamp());
            boolean result = EsqEntityDictionaryStorage.getInstance().init((String)null);
            if (!result) {
                System.out.println("Failed to load esq-entity-dictionaries.xml");
                System.exit(-1); // Exit the JVM immediately
            }
        }
    }

}
