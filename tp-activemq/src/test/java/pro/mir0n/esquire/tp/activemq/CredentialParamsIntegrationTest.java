/*
 *  Esquire frameworks (tm)
 *  tp-activemq -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.tp.activemq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.security.AuthenticationUser;
import org.apache.activemq.security.SimpleAuthenticationPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration test against an EMBEDDED ActiveMQ broker that DEMANDS a user and a password -- the shape a managed
 *  broker (Amazon MQ) always has, and the one our own broker never had.
 *
 *  <p>The allow-case proves a second thing it does not assert directly: ActiveMQ refuses a broker URI carrying an
 *  option it does not know ("Invalid connect parameters"), so a send that succeeds is also proof that userName and
 *  password were applied with a setter and kept OUT of the URI -- which is what keeps them out of the develop log. */
class CredentialParamsIntegrationTest {

    private static final TransportProvider PROVIDER = new TransportProvider();
    private static final ObjectMapper OM = new ObjectMapper();

    private static final String USER = "esq";
    private static final String PASSWORD = "esq-secret";

    private static BrokerService broker;
    private static String url;

    @BeforeAll
    static void startBroker() throws Exception {
        List<AuthenticationUser> users = new ArrayList<>();
        users.add(new AuthenticationUser(USER, PASSWORD, "users"));
        SimpleAuthenticationPlugin auth = new SimpleAuthenticationPlugin();
        auth.setUsers(users);
        auth.setAnonymousAccessAllowed(false);

        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setBrokerName("tpAmqCredentialsTest");
        broker.setPlugins(new BrokerPlugin[] { auth });
        broker.addConnector("tcp://localhost:0");
        broker.start();
        broker.waitUntilStarted();
        url = broker.getTransportConnectors().get(0).getPublishableConnectString();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }

    private static BusIdentity id() {
        return new BusIdentity("test-bus", "test-slot", "test.0");
    }

    private static Map<String, String> params(String user, String password) {
        Map<String, String> ret;
        if (user == null) {
            ret = Map.of("pubSubDomain", "true");
        } else {
            ret = Map.of("pubSubDomain", "true", "userName", user, "password", password);
        }
        return ret;
    }

    private static TransportMessage msg() {
        return new TransportMessage(
                Map.of(BusConstants.FIELD_ROD_ID, "test.0", BusConstants.FIELD_EVENT_TYPE, BusConstants.EVENT_CREATE), "e1");
    }

    @Test
    void credentialsGiven_publishAndConsumeWork() throws Exception {
        String topic = "test.credentials.ok";
        CountDownLatch got = new CountDownLatch(1);

        TransportPublisher pub = PROVIDER.openPublisher(topic, new PublishSettings(OM, url, id(), params(USER, PASSWORD), 0));
        TransportConsumer con = PROVIDER.openConsumer(topic,
                new ConsumeSettings(OM, url, id(), params(USER, PASSWORD), 1, null), m -> got.countDown());
        con.start();
        Thread.sleep(600);

        pub.dispatch(pub.encode(msg()));

        assertThat(got.await(5, TimeUnit.SECONDS)).isTrue();

        con.close();
        pub.close();
    }

    @Test
    void noCredentials_theBrokerRefuses() {
        String topic = "test.credentials.none";
        TransportPublisher pub = PROVIDER.openPublisher(topic, new PublishSettings(OM, url, id(), params(null, null), 0));

        assertThatThrownBy(() -> pub.dispatch(pub.encode(msg())))
                .hasMessageContaining("User name [null] or password is invalid");
    }

    @Test
    void wrongPassword_theBrokerRefuses() {
        String topic = "test.credentials.wrong";
        TransportPublisher pub = PROVIDER.openPublisher(topic,
                new PublishSettings(OM, url, id(), params(USER, "not-the-password"), 0));

        assertThatThrownBy(() -> pub.dispatch(pub.encode(msg())))
                .hasMessageContaining("password is invalid");
    }
}
