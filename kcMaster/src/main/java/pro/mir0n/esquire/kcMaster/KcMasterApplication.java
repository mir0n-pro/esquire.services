/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial scaffold
 * 04/06/2026 mir0n  EsqObjectKindStorage loaded on ApplicationStartingEvent; devLog added
 */

package pro.mir0n.esquire.kcMaster;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableAsync;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

@Slf4j
@EnableAsync
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.kcMaster"
})
public class KcMasterApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcMasterApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(KcMasterApplication.class);
        app.addListeners(new KcMasterApplicationStartingListener());
        app.run(args);
    }

    public static class KcMasterApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
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
