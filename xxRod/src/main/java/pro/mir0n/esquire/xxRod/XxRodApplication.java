/*
 *  Esquire frameworks (tm)
 *  xxRod service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the standalone xx-Rod audit consumer (x-Rod option c). Consumes the durable
 *                   audit queue and writes the *_log tables; horizontally redundant (competing consumers).
 *                   Loads EsqObjectKindStorage on ApplicationStartingEvent (needed by the kind->sql-key map).
 */
package pro.mir0n.esquire.xxRod;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

@Slf4j
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.xxRod"
})
public class XxRodApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + XxRodApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(XxRodApplication.class);
        app.addListeners(new XxRodApplicationStartingListener());
        app.run(args);
    }

    public static class XxRodApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
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
