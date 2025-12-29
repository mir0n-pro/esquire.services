/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/28/2025 mir0n logging added using Slf4j
 */

package pro.mir0n.esquire.bizTree;

import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.bizTree.storage.EsqEntityDictionaryStorage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;

@Slf4j
@SpringBootApplication
public class BizTreeApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication( BizTreeApplication.class);
        // Register the listener with the SpringApplication instance
        app.addListeners(new BizTreeApplicationStartingListener());
        app.run(args);
    }

    public static class BizTreeApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
        @Override
        public void onApplicationEvent(ApplicationStartingEvent event) {
            log.debug("ApplicationStartingEvent received: {}", event.getTimestamp());
            boolean result = EsqEntityDictionaryStorage.getInstance().init("esq-entity-dictionaries.xml" );
            if (!result) {
                System.exit(-1); // Exit the JVM immediately
            }
        }
    }

}
