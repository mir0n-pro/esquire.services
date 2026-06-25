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
 * 06/22/2026 mir0n  wires the bus lifecycle via the MessagingBusLifecycleRegistrar inner listener (LOWEST_PRECEDENCE):
 *                   env-prepared -> bus.init(env, {BUS_KEY_AUDIT}); ready -> bus.start(); context-closed -> bus.close().
 *                   The Starting kind-storage load is unchanged.
 * 06/22/2026 mir0n  at ApplicationReadyEvent registers (programmatically, no @Bean) a BusHealthIndicator for the
 *                   bus facade AND a TransportHealthIndicator named "keepDatasource" over AuditConsumerConfig.keepHealth()
 *                   (the keep DB) into the Actuator HealthContributorRegistry -> /actuator/health
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 */
package pro.mir0n.esquire.auKeep;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.BusHealthIndicator;
import pro.mir0n.esquire.messaging.TransportHealthIndicator;
import pro.mir0n.esquire.auKeep.messaging.AuditConsumerConfig;

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
        // the bus lifecycle (build/start/close) in one call.
        app.addListeners(new MessagingBusLifecycleRegistrar());
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

    public static class MessagingBusLifecycleRegistrar implements ApplicationListener<ApplicationEvent>, Ordered {

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;   // each bus phase runs after the service's same-event listeners
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            MessagingBus bus = MessagingBus.getInstance();
            if (event instanceof ApplicationEnvironmentPreparedEvent e) {
                bus.init(e.getEnvironment(), new String[]{EsqConstants.BUS_KEY_AUDIT});
                devLog.debug("MessagingBus initiated (rods built, paused)");
            } else if (event instanceof ApplicationReadyEvent e) {
                bus.start();                             // run them -- traffic flows only from here
                devLog.debug("MessagingBus started (rods running)");
                BusHealthIndicator.register(e.getApplicationContext(), bus);   // forward bus (broker) connection health (no @Bean)
                TransportHealthIndicator.register(e.getApplicationContext(), "keepDatasource",
                        e.getApplicationContext().getBean(AuditConsumerConfig.class).keepHealth());   // the keep DB (apply-side) health
                devLog.debug("MessagingBus + keepDatasource health indicators registered");
            } else if (event instanceof ContextClosedEvent) {
                bus.close();                             // drain in-flight + close transport
                devLog.debug("MessagingBus closed (rods shut down)");
            }
        }
    }
}
