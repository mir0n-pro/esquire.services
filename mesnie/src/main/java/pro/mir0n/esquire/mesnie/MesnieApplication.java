/*
 *  Esquire frameworks (tm)
 *  Mesnie service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/12/2026 mir0n  created: enyMan and keySmith in ONE process, each imported as its own @Configuration, with
 *                   kcMaster's identity work served in that same process. Declares what a PROCESS owns: the roles
 *                   repository, one AuditBusBridge, one IIdentityGateway, the startup storages and one
 *                   MessagingBusLifecycleRegistrar over the entity and audit buses. No kc rod is built.
 * 08/17/2026 mir0n  v1.2.13 T3.1 -- @Bean IMeterOwner meterOwner(entityBusId) declared here: a
 *                   MesnieMeterOwner, handed the entity bus id from the property the bus itself reads
 */

package pro.mir0n.esquire.mesnie;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.backend.identity.IIdentityGateway;
import pro.mir0n.esquire.backend.o11y.IMeterOwner;
import pro.mir0n.esquire.backend.o11y.ObservabilityConfig;
import pro.mir0n.esquire.kcMaster.identity.KcIdentityGateway;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.enyMan.EnyManConfig;
import pro.mir0n.esquire.keySmith.KeySmithConfig;
import pro.mir0n.esquire.keySmith.service.BizValidatorFactory;
import pro.mir0n.esquire.messaging.BusHealthIndicator;
import pro.mir0n.esquire.messaging.MessagingBus;

/**
 * Mesnie -- the household under one roof: enyMan and keySmith in ONE process, with kcMaster's identity work
 * served in that same process.
 * <p>
 * enyMan and keySmith come in as their own {@code @Configuration}, so the package lists stay where they
 * belong and nothing is copied here. kcMaster comes in as code only: Mesnie names the few beans the identity
 * gateway needs and takes nothing else, so kcMaster's bus consumer and its entity-topic worker are no part of
 * this process.
 * <p>
 * What Mesnie owns is what a PROCESS owns: the roles repository, the audit bridge, the identity gateway, the
 * startup storages, and one bus lifecycle over the two buses the household still speaks -- entity and audit.
 * The kc bus is gone: KeyCloak is reached by a method call onto an in-memory queue, which is what lets a
 * create that follows a move find the move already applied.
 */
@Slf4j
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({ObservabilityConfig.class, EnyManConfig.class, KeySmithConfig.class})
@EnableJpaRepositories(basePackages = "pro.mir0n.esquire.backend.storage.roles")
public class MesnieApplication {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + MesnieApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MesnieApplication.class);
        app.addListeners(new MesnieApplicationStartingListener());
        app.addListeners(new MesnieApplicationReadyListener());
        // the bus lifecycle (build/start/close) in one call -- registered LAST so start() runs after roles load.
        app.addListeners(new MessagingBusLifecycleRegistrar());

        app.run(args);
    }

    /** The ONE audit bridge of this process, over the audit-bus x-rod the facade built from the audit-bus ref
     *  (role SERVER): an in-process keep (audit-b) or a bus producer (audit-c), per AUDIT_BUS_ID. Both composed
     *  services inject it by type, so exactly one may exist -- each service's own AuditConfig is out of its scan. */
    @Bean
    public AuditBusBridge audit() {
        return new AuditBusBridge(MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_AUDIT));
    }

    /** Which of the three each meter belongs to. Wired here, in the process that composes them; a service
     *  standing alone contributes none and every one of its meters carries its own name. The entity bus id
     *  comes from the same property the bus itself reads. */
    @Bean
    public IMeterOwner meterOwner(@Value("${esquire.entity-bus.messaging-bus.bus-id:}") String entityBusId) {
        return new MesnieMeterOwner(entityBusId);
    }

    /**
     * The household's way to the identity provider. The bus is skipped for what is in the house: kcMaster runs
     * in this process, so the call is a method call onto its queue instead of a message onto the kc leg. Both
     * composed services are handed this one gateway and neither can tell.
     *
     * <p>Mesnie names the class and hands over the environment. What a KeyCloak gateway is made of -- the admin
     * client, the path park, the identity service, the request handler -- is the gateway's own business, the
     * same deal the messaging bus makes with a transport provider.
     *
     * <p>The answer handle is set here because in this process there is no requester rod to receive it. On the
     * bus the URS/URR travels back to the keySmith or enyMan adapter that asked; here it comes straight back
     * from the gateway. Neither side does more than log it yet -- correlating an answer to its request is the
     * open item on both.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public IIdentityGateway identityGateway(Environment env) {
        KcIdentityGateway ret = new KcIdentityGateway(env);
        ret.setResultHandler(e ->
                log.info("KC | {} | {} | {} | {} | {}", e.msgType(), e.opCode(), e.kind(), e.entityId(), e.requestId()));
        return ret;
    }

    /** The union of the two services' startup work. ValidatorFactory is one per JVM and takes the whole map at
     *  once: enyMan contributes none (it passes null standing alone), keySmith the roles validators, so the
     *  union IS keySmith's map -- passing enyMan's null here would drop keySmith's permission guards silently. */
    public static class MesnieApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {
        @Override
        public void onApplicationEvent(ApplicationStartingEvent event) {
            devLog.debug("ApplicationStartingEvent received: {}", event.getTimestamp());

            boolean result = EsqObjectKindStorage.getInstance().init((String) null);
            if (!result) {
                System.out.println("Failed to load esq-object-kinds.xml");
                System.exit(-1); // Exit the JVM immediately
            }
            devLog.debug("EsqObjectKindStorage loaded");

            result = EsqEntityDictionaryStorage.getInstance().init((String) null);
            if (!result) {
                System.out.println("Failed to load esq-entity-dictionaries.xml");
                System.exit(-1); // Exit the JVM immediately
            }
            devLog.debug("EsqEntityDictionaryStorage loaded");
            ValidatorFactory.getInstance().init(BizValidatorFactory.getBizValidators());
        }
    }

    public static class MesnieApplicationReadyListener implements ApplicationListener<ApplicationReadyEvent> {
        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            JpaRolesRepository repo = event.getApplicationContext().getBean(JpaRolesRepository.class);
            boolean result = EsqRolesStorage.getInstance().init(repo);
            if (!result) {
                System.out.println("Failed to load EsqRolesStorage");
                System.exit(-1);
            }
            devLog.debug("EsqRolesStorage loaded");
        }
    }


    /** One registrar for the whole household, over the buses it still speaks: entity (enyMan) and audit
     *  (both). The identity gateway is in-process, so there is no kc rod to build. */
    public static class MessagingBusLifecycleRegistrar implements ApplicationListener<ApplicationEvent>, Ordered {

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;   // each bus phase runs after the service's same-event listeners
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            MessagingBus bus = MessagingBus.getInstance();
            if (event instanceof ApplicationEnvironmentPreparedEvent e) {
                bus.init(e.getEnvironment(), new String[]{EsqConstants.BUS_KEY_ENTITY, EsqConstants.BUS_KEY_AUDIT});
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
