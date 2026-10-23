/*
 *  Esquire frameworks (tm)
 *  paacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/19/2026 mir0n  created: the paacMan process: pacMan's code without PacManController and PacManApplication in
 *                   the scan, the bizBus BusServerAdapter on BizRequestHandler, and the buses entity, audit and biz
 */

package pro.mir0n.esquire.paacMan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import pro.mir0n.esquire.backend.messaging.BusServerAdapter;
import pro.mir0n.esquire.backend.o11y.ObservabilityConfig;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.pacMan.PacManApplication;
import pro.mir0n.esquire.paacMan.messaging.BizRequestHandler;
import pro.mir0n.esquire.pacMan.controller.PacManController;

/**
 * paacMan -- pacMan served over the biz bus. pacMan's code runs as it is; paacMan adds the bus face
 * ({@code BizRequestHandler} on pacMan's commands) and the biz bus.
 *
 * <p>pacMan's REST controller is left out of the scan: paacMan has no REST face. pacMan's application class
 * is left out too -- this one is the process.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(ObservabilityConfig.class)
@ComponentScan(
        basePackages = {
                "pro.mir0n.esquire.paacMan",
                "pro.mir0n.esquire.pacMan",
                "pro.mir0n.esquire.backend.service",
                "pro.mir0n.esquire.backend.security",
                "pro.mir0n.esquire.backend.exception"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {PacManApplication.class, PacManController.class}))
@EntityScan(basePackages = {"pro.mir0n.esquire.backend.jpa", "pro.mir0n.esquire.pacMan.acct.jpa"})
@EnableJpaRepositories(basePackages = {
        "pro.mir0n.esquire.pacMan.jpa",
        "pro.mir0n.esquire.pacMan.acct.jpa",
        "pro.mir0n.esquire.backend.storage.roles"
})
public class PaacManApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PaacManApplication.class);
        app.addListeners(new PacManApplication.PacManApplicationStartingListener());
        app.addListeners(new PacManApplication.PacManApplicationReadyListener());
        // the bus lifecycle (build/start/close) in one call -- registered LAST so start() runs after roles load.
        app.addListeners(new MessagingBusLifecycleRegistrar());

        app.run(args);
    }

    /** The biz bus in the SERVER role: its requests go to the handler, its answers back out the same rod. */
    @Bean(initMethod = "start")
    public BusServerAdapter bizBus(BizRequestHandler handler) {
        return new BusServerAdapter(EsqConstants.BUS_KEY_BIZ, handler);
    }

    /** pacMan's bus lifecycle, with the biz bus added. */
    public static class MessagingBusLifecycleRegistrar extends PacManApplication.MessagingBusLifecycleRegistrar {

        @Override
        protected String[] busKeys() {
            return new String[]{EsqConstants.BUS_KEY_ENTITY, EsqConstants.BUS_KEY_AUDIT, EsqConstants.BUS_KEY_BIZ};
        }
    }
}
