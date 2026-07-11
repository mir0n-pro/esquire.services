/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/28/2025 mir0n logging added using Slf4j
 * 01/23/2026 miron no needs for BizTreeApplicationStartingListener
 * 03/10/2026 mir0n  scanBasePackages: backend.service, backend.security, backend.exception added
 * 03/25/2026 mir0n  BizTreeApplicationStartingListener: EsqObjectKindStorage loaded on ApplicationStartingEvent
 * 06/22/2026 mir0n  added MessagingBusLifecycleRegistrar (ApplicationListener<ApplicationEvent>, Ordered
 *                   LOWEST_PRECEDENCE) driving the two-phase bus lifecycle: env-prepared -> bus.init(env,
 *                   {entity}) builds rods paused, ready -> bus.start() runs them, context-closed -> bus.close()
 * 06/22/2026 mir0n  registrar registers a BusHealthIndicator (bus facade handed in) into the Actuator
 *                   HealthContributorRegistry programmatically at ApplicationReadyEvent (no @Bean) -> /actuator/health
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 * 07/08/2026 mir0n  @Import(TracingConfig.class): the common distributed-tracing wiring (v1.2.11 O2)
 */

package pro.mir0n.esquire.bizTree;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import pro.mir0n.esquire.backend.o11y.ObservabilityConfig;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.BusHealthIndicator;
import pro.mir0n.esquire.messaging.MessagingBus;

@Slf4j
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.bizTree",
        "pro.mir0n.esquire.backend.service",
        "pro.mir0n.esquire.backend.security",
        "pro.mir0n.esquire.backend.exception"
})
@Import(ObservabilityConfig.class)
@EntityScan(basePackages = "pro.mir0n.esquire.backend.jpa")
@EnableJpaRepositories(basePackages = "pro.mir0n.esquire.bizTree.jpa")

public class BizTreeApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + BizTreeApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BizTreeApplication.class);
        app.addListeners(new BizTreeApplicationStartingListener());
        // the bus lifecycle (build/start/close) in one call.
        app.addListeners(new MessagingBusLifecycleRegistrar());
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

    public static class MessagingBusLifecycleRegistrar implements ApplicationListener<ApplicationEvent>, Ordered {

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;   // each bus phase runs after the service's same-event listeners
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            MessagingBus bus = MessagingBus.getInstance();
            if (event instanceof ApplicationEnvironmentPreparedEvent e) {
                bus.init(e.getEnvironment(), new String[]{EsqConstants.BUS_KEY_ENTITY});
                devLog.debug("MessagingBus initiated (rods built, paused)");
            } else if (event instanceof ApplicationReadyEvent e) {
                bus.start();                             // run them -- traffic flows only from here
                devLog.debug("MessagingBus started (rods running)");
                BusHealthIndicator.register(e.getApplicationContext(), bus);   // forward bus connection health to /actuator/health (no @Bean)
                devLog.debug("MessagingBus health indicator registered");
            } else if (event instanceof ContextClosedEvent) {
                bus.close();                             // drain in-flight + close transport
                devLog.debug("MessagingBus closed (rods shut down)");
            }
        }
    }

}
