/*
 *  Esquire frameworks (tm)
 *  xxRod service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.auKeep;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.MessagingBus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.xrod.RodTransportAdapter;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.tp.activemq.TransportProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = {AuKeepApplication.class, RodBusIntegrationTest.BusLifecycle.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RodBusIntegrationTest {

    // The kind dictionary is loaded by an ApplicationStartingListener in main(), which @SpringBootTest does
    // not invoke -- load it before the context (and AuditConfig) build.
    static {
        EsqObjectKindStorage.getInstance().init((String) null);
    }

    /**
     * The bus lifecycle, which {@code main()} registers and {@code @SpringBootTest} does not.
     *
     * <p>{@code AuKeepApplication.main} adds a {@code MessagingBusLifecycleRegistrar} that calls
     * {@code bus.init(env, BUS_KEY_AUDIT)} on {@code ApplicationEnvironmentPreparedEvent} -- that call is what
     * BUILDS the rods. A {@code @SpringBootTest} builds the context directly, so the listener is never
     * registered, no rod exists, and the {@code auditConsumer} bean fails with "x-rod 'audit-bus' is NOT
     * built". This is the same gap the static block above closes for the kind dictionary.
     *
     * <p><b>It has to be a {@link BeanFactoryPostProcessor}</b>: the rods must exist before the first bean is
     * created, because {@code auditConsumer} asks for one during refresh. A context initializer or a
     * {@code ContextRefreshedEvent} listener is too late -- the bean is already being built by then. A BFPP
     * runs after the Environment is complete (so the {@code @DynamicPropertySource} container ports are
     * there) and before any singleton is instantiated, which is exactly the window.
     */
    @TestConfiguration
    static class BusLifecycle {

        @Bean
        static BeanFactoryPostProcessor busInit() {
            // The environment is a registered singleton by the time a BFPP runs, so take it from the factory
            // rather than as a method parameter -- a static @Bean method is invoked without its config class.
            return beanFactory -> MessagingBus.getInstance()
                    .init(beanFactory.getBean(ConfigurableEnvironment.class),
                          new String[]{EsqConstants.BUS_KEY_AUDIT});
        }

        /** Rods are built PAUSED; nothing flows until start(). main() does this on ApplicationReadyEvent. */
        @Bean
        ApplicationListener<ContextRefreshedEvent> busStart() {
            return event -> MessagingBus.getInstance().start();
        }

        /** Drain and close with the context, so a second test class does not inherit a live consumer. */
        @Bean
        ApplicationListener<ContextClosedEvent> busClose() {
            return event -> MessagingBus.getInstance().close();
        }
    }

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("esq2025").withUsername("esq2025").withPassword("q")
            .withInitScript("it-account-log.sql");

    @Container
    static final GenericContainer<?> AMQ =
            new GenericContainer<>(DockerImageName.parse("esquire-activemq:6.1.4")).withExposedPorts(61616);

    private static String brokerUrl() {
        return "tcp://" + AMQ.getHost() + ":" + AMQ.getMappedPort(61616);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.profiles.active", () -> "dev-postgres");
        // the keep applies to its OWN datasource group (esquire.keep.datasource) -- point it at the PG container.
        r.add("esquire.keep.datasource.url", PG::getJdbcUrl);
        r.add("esquire.keep.datasource.username", PG::getUsername);
        r.add("esquire.keep.datasource.password", PG::getPassword);
        // catalog config: the booted dataKeep consumer resolves the audit leg + builds its OWN connection.
        r.add("esquire.messaging-bus[0].bus-id", () -> "audit-bus");
        r.add("esquire.messaging-bus[0].slots[0].slot-id", () -> "audit");
        r.add("esquire.messaging-bus[0].slots[0].x-rod.transport.provider", () -> "activemq");
        r.add("esquire.messaging-bus[0].slots[0].x-rod.transport.endpoint", RodBusIntegrationTest::brokerUrl);
        r.add("esquire.messaging-bus[0].slots[0].x-rod.transport.destination", () -> "esquire.rod.audit");
        r.add("esquire.messaging-bus[0].slots[0].x-rod.transport.topic", () -> "false");
        // service-level bus ref: dataKeep's consumer reads esquire.audit-bus.messaging-bus.bus-id -> the catalog leg.
        r.add("esquire.audit-bus.messaging-bus.bus-id", () -> "audit-bus");
    }

    @Test
    void busPathWritesAccountLogAndDedups() {
        TransportProvider provider = new TransportProvider();
        Consumer<RodEvent> publisher = RodTransportAdapter.publisher(provider, "esquire.rod.audit",
                new PublishSettings(new ObjectMapper(), brokerUrl(),
                        new BusIdentity("audit-bus", "audit", null),
                        java.util.Map.of(), 0));
        JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "IT-ACC");
        body.put("ccy", "USD");
        body.put("balance", 4242);
        body.put("status", "O");
        body.put("parentId", "1");
        body.put("desc", "c-it");
        body.put("negativeAllowed", "N");
        RodEvent e = new RodEvent(RodEvent.Op.UPDATE, 50, "777", null, 7L, System.currentTimeMillis(),
                "it-crl-1", "it-req-1", "it-uid", null, BusConstants.MSG_TYPE_AUDIT, body);

        publisher.accept(e);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(count(jdbc, 777L, 7L)).isEqualTo(1));

        // plain redelivery of the same (pk, change number) -> ON CONFLICT DO NOTHING -> still one row
        publisher.accept(e);
        await().pollDelay(2, TimeUnit.SECONDS).atMost(6, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(count(jdbc, 777L, 7L)).isEqualTo(1));

        // THE case the old (correlation id, pk) key could not catch: the SAME change of the SAME row,
        // re-sent under a DIFFERENT correlation id -- a re-publish from another request. The old key saw
        // two different keys and wrote two rows; the change number says it is one change, so still one row.
        publisher.accept(new RodEvent(RodEvent.Op.UPDATE, 50, "777", null, 7L, System.currentTimeMillis(),
                "it-crl-2", "it-req-2", "it-uid", null, BusConstants.MSG_TYPE_AUDIT, body));
        await().pollDelay(2, TimeUnit.SECONDS).atMost(6, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(count(jdbc, 777L, 7L)).isEqualTo(1));

        // ... and the NEXT change of the same row is a different key, so it is a new record.
        publisher.accept(new RodEvent(RodEvent.Op.UPDATE, 50, "777", null, 8L, System.currentTimeMillis(),
                "it-crl-3", "it-req-3", "it-uid", null, BusConstants.MSG_TYPE_AUDIT, body));
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(count(jdbc, 777L, 8L)).isEqualTo(1));

        assertThat(jdbc.queryForObject(
                "select accl_id from esq_account_log where accl_pk = 777 and accl_change_no = 7", String.class))
                .isEqualTo("IT-ACC");
    }

    private static Integer count(JdbcTemplate jdbc, long pk, long changeNo) {
        return jdbc.queryForObject(
                "select count(*) from esq_account_log where accl_pk = ? and accl_change_no = ?",
                Integer.class, pk, changeNo);
    }
}
