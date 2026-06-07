/*
 *  Esquire frameworks (tm)
 *  xxRod service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: option (c) integration test on real Postgres + ActiveMQ (Testcontainers) --
 *                   publish a RodEvent to the audit queue, the booted xxRod consumer writes esq_account_log;
 *                   a redelivery of the same (crl,pk) dedups to one row. Skipped when Docker is absent.
 */
package pro.mir0n.esquire.xxRod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.audit.RodEventBusPublisher;
import pro.mir0n.esquire.common.xrod.RodEvent;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = XxRodApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RodBusIntegrationTest {

    // The kind dictionary is loaded by an ApplicationStartingListener in main(), which @SpringBootTest does
    // not invoke -- load it before the context (and AuditConfig) build.
    static {
        EsqObjectKindStorage.getInstance().init((String) null);
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
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.activemq.broker-url", RodBusIntegrationTest::brokerUrl);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void busPathWritesAccountLogAndDedups() {
        JmsTemplate jms = new JmsTemplate(new ActiveMQConnectionFactory(brokerUrl()));
        jms.setPubSubDomain(false);
        RodEventBusPublisher publisher =
                new RodEventBusPublisher(jms, EsqMsgConstants.QUEUE_ROD_AUDIT, new ObjectMapper());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "IT-ACC");
        body.put("ccy", "USD");
        body.put("balance", 4242);
        body.put("status", "O");
        body.put("parentId", "1");
        body.put("desc", "c-it");
        body.put("negativeAllowed", "N");
        RodEvent e = new RodEvent(RodEvent.Op.UPDATE, 50, "777", null, System.currentTimeMillis(),
                "it-crl-1", "it-req-1", "it-uid", body);

        publisher.accept(e);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(count(jdbc, "it-crl-1")).isEqualTo(1));

        // redelivery of the same (crl, pk) -> ON CONFLICT DO NOTHING -> still one row
        publisher.accept(e);
        await().pollDelay(2, TimeUnit.SECONDS).atMost(6, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(count(jdbc, "it-crl-1")).isEqualTo(1));

        assertThat(jdbc.queryForObject(
                "select accl_id from esq_account_log where accl_crl_id='it-crl-1'", String.class))
                .isEqualTo("IT-ACC");
    }

    private static Integer count(JdbcTemplate jdbc, String crl) {
        return jdbc.queryForObject(
                "select count(*) from esq_account_log where accl_crl_id = ?", Integer.class, crl);
    }
}
