/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/28/2025 mir0n logging added using Slf4j
 * 01/23/2026 miron no needs for BizTreeApplicationStartingListener
 * 03/10/2026 mir0n  scanBasePackages: backend.service, backend.security, backend.exception added
 * 03/25/2026 mir0n  BizTreeApplicationStartingListener: EsqObjectKindStorage loaded on ApplicationStartingEvent
 */

package pro.mir0n.esquire.bizTree;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

@Slf4j
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.bizTree",
        "pro.mir0n.esquire.backend.service",
        "pro.mir0n.esquire.backend.security",
        "pro.mir0n.esquire.backend.exception"
})
@EntityScan(basePackages = "pro.mir0n.esquire.backend.jpa")
@EnableJpaRepositories(basePackages = "pro.mir0n.esquire.bizTree.jpa")

public class BizTreeApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + BizTreeApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BizTreeApplication.class);
        app.addListeners(new BizTreeApplicationStartingListener());
        app.run(args);
    }

    public static class BizTreeApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
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
