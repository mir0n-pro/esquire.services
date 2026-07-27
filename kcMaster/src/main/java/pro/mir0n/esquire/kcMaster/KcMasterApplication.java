/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial scaffold
 * 04/06/2026 mir0n  EsqObjectKindStorage loaded on ApplicationStartingEvent; devLog added
 * 06/22/2026 mir0n  MessagingBusLifecycleRegistrar inner class added (ApplicationListener + Ordered.LOWEST_PRECEDENCE)
 *                   and registered; drives MessagingBus.init(env, {BUS_KEY_KC, BUS_KEY_ENTITY}) on
 *                   ApplicationEnvironmentPreparedEvent, start() on ApplicationReadyEvent, close() on
 *                   ContextClosedEvent (no roles-Ready listener in kcMaster, so the registrar owns start)
 * 06/22/2026 mir0n  registrar registers a BusHealthIndicator (bus facade handed in) into the Actuator
 *                   HealthContributorRegistry programmatically at ApplicationReadyEvent (no @Bean) -> /actuator/health
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 * 07/08/2026 mir0n  @Import(TracingConfig.class): the common distributed-tracing wiring (v1.2.11 O2)
 */

package pro.mir0n.esquire.kcMaster;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import pro.mir0n.esquire.backend.o11y.ObservabilityConfig;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.BusHealthIndicator;
import pro.mir0n.esquire.messaging.MessagingBus;

@Slf4j
@EnableAsync
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.kcMaster"
})
@Import(ObservabilityConfig.class)
public class KcMasterApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcMasterApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(KcMasterApplication.class);
        app.addListeners(new KcMasterApplicationStartingListener());
        // the bus lifecycle (build/start/close) in one call.
        app.addListeners(new MessagingBusLifecycleRegistrar());
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

    public static class MessagingBusLifecycleRegistrar implements ApplicationListener<ApplicationEvent>, Ordered {

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;   // each bus phase runs after the service's same-event listeners
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            MessagingBus bus = MessagingBus.getInstance();
            if (event instanceof ApplicationEnvironmentPreparedEvent e) {
                bus.init(e.getEnvironment(), new String[]{EsqConstants.BUS_KEY_KC, EsqConstants.BUS_KEY_ENTITY});
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
