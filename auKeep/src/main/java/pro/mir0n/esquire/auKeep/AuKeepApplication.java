/*
 *  Esquire frameworks (tm)
 *  auKeep service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/18/2026 mir0n  created (was the xxRod app): auKeep -- the standalone audit-bus keep consumer (x-rod option c).
 *                   Consumes the durable audit bus and applies the *_log tables through the generic keep engine;
 *                   horizontally redundant (competing consumers). Loads EsqObjectKindStorage on
 *                   ApplicationStartingEvent (needed by the kind->sql-key map). Scans dataKeep + audit + auKeep.
 */
package pro.mir0n.esquire.auKeep;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

// The keep owns its OWN datasource pool (esquire.keep.datasource, via KeepApplier) -- there is no
// spring.datasource, so Boot's DataSourceAutoConfiguration must not try to build one.
@Slf4j
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.dataKeep",
        "pro.mir0n.esquire.audit",
        "pro.mir0n.esquire.auKeep"
}, exclude = { DataSourceAutoConfiguration.class })
public class AuKeepApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + AuKeepApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AuKeepApplication.class);
        app.addListeners(new AuKeepApplicationStartingListener());
        app.run(args);
    }

    public static class AuKeepApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
        @Override
        public void onApplicationEvent(ApplicationStartingEvent event) {
            boolean result = EsqObjectKindStorage.getInstance().init((String) null);
            if (!result) {
                System.out.println("Failed to load esq-object-kinds.xml");
                System.exit(-1);
            }
            devLog.debug("EsqObjectKindStorage loaded");
        }
    }
}
