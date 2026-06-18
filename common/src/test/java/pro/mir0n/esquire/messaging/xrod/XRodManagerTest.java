package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import pro.mir0n.esquire.common.EsqUtils;
import pro.mir0n.esquire.messaging.CapturingTransportProvider;
import pro.mir0n.esquire.messaging.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Frontend resolution: rod-id defaults to the instance id; a service-level x-rod fully overwrites the catalog
 *  leg; a service-level-only leg resolves without a catalog throw. The role-driven receive selector AND the
 *  request/response node selection are XRodRR's job -- base XRod is single-node (the one destination), whole-node
 *  consume (no selector). */
class XRodManagerTest {

    private static final String CAP = "pro.mir0n.esquire.messaging.CapturingTransportProvider";
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clear() {
        CapturingTransportProvider.reset();
    }

    /** The expected default CLIENT selector: rod-id = "<app>.<instanceNo>" -- mirrors
     *  XRodManager.instanceId() so the assertion stays correct whatever the runner's instance number. */
    private static String defaultClientSelector() {
        return "RodID = 'enyman." + EsqUtils.instanceNo() + "'";
    }

    /** The shared scaffolding: a kc bus-id + slot-id "kc", a capturing transport, and the service-level ref. */
    private MockEnvironment baseEnv() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "enyman");
        env.setProperty("esquire.messaging-bus[0].bus-id", "esquire.kc");
        env.setProperty("esquire.messaging-bus[0].slot[0].slot-id", "kc");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.provider", CAP);
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.endpoint", "tcp://localhost:61616");
        env.setProperty("esquire.kc-bus.messaging-bus.bus-id", "esquire.kc");
        env.setProperty("esquire.kc-bus.messaging-bus.slot-id", "kc");
        return env;
    }

    /** An R&R leg: rod-class XRodRR + a single destination (the selector tests don't need the two nodes). */
    private MockEnvironment rrEnv(String legRodId) {
        MockEnvironment env = baseEnv();
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.rod-class", "XRodRR");
        if (legRodId != null) {
            env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.rod-id", legRodId);
        }
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.destination", "esquire.kc.q");
        return env;
    }

    @Test
    void rodIdDefaultsToTheServiceInstanceId() {
        // default rod-id = "<app>.<instanceNo>" (the instance number from the host name), so each replica owns a distinct selector.
        XRodManager mgr = new XRodManager(rrEnv(null), om);
        mgr.consumer("kc-bus", Role.CLIENT, e -> { });   // CLIENT consume -> a RodID selector built from the id
        assertThat(CapturingTransportProvider.lastConsume.selector()).isEqualTo(defaultClientSelector());
        mgr.close();
    }

    @Test
    void explicitLegRodIdWins() {
        XRodManager mgr = new XRodManager(rrEnv("enyman.custom"), om);
        mgr.consumer("kc-bus", Role.CLIENT, e -> { });
        assertThat(CapturingTransportProvider.lastConsume.selector()).isEqualTo("RodID = 'enyman.custom'");
        mgr.close();
    }

    @Test
    void serverConsumerFiltersByServiceId() {
        XRodManager mgr = new XRodManager(rrEnv(null), om);
        mgr.consumer("kc-bus", Role.SERVER, e -> { });
        assertThat(CapturingTransportProvider.lastConsume.selector()).isEqualTo("SlotID = 'kc'");
        mgr.close();
    }

    @Test
    void serviceLevelXRodOverwritesTheCatalogLeg() {
        // catalog leg rod-id = "from-catalog"; the service-level ref carries its OWN x-rod -> it fully replaces it.
        MockEnvironment env = rrEnv("from-catalog");
        env.setProperty("esquire.kc-bus.messaging-bus.x-rod.rod-id", "from-service");
        env.setProperty("esquire.kc-bus.messaging-bus.x-rod.rod-class", "XRodRR");
        env.setProperty("esquire.kc-bus.messaging-bus.x-rod.transport.provider", CAP);
        env.setProperty("esquire.kc-bus.messaging-bus.x-rod.transport.endpoint", "tcp://localhost:61616");
        env.setProperty("esquire.kc-bus.messaging-bus.x-rod.transport.destination", "esquire.kc.svc");

        XRodManager mgr = new XRodManager(env, om);
        mgr.consumer("kc-bus", Role.CLIENT, e -> { });
        assertThat(CapturingTransportProvider.lastConsume.selector()).isEqualTo("RodID = 'from-service'");
        assertThat(CapturingTransportProvider.lastConsumeNode).isEqualTo("esquire.kc.svc");   // service node, not catalog's
        mgr.close();
    }

    @Test
    void serviceLevelOnlyLegResolvesWithoutCatalog() {
        // NO catalog leg at all -- the config lives only on the service-level ref. Must NOT throw.
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "enyman");
        env.setProperty("esquire.kc-bus.messaging-bus.bus-id", "esquire.kc");
        env.setProperty("esquire.kc-bus.messaging-bus.slot-id", "kc");
        env.setProperty("esquire.kc-bus.messaging-bus.x-rod.rod-class", "XRodRR");
        env.setProperty("esquire.kc-bus.messaging-bus.x-rod.transport.provider", CAP);
        env.setProperty("esquire.kc-bus.messaging-bus.x-rod.transport.endpoint", "tcp://localhost:61616");
        env.setProperty("esquire.kc-bus.messaging-bus.x-rod.transport.destination", "esquire.kc.q");

        XRodManager mgr = new XRodManager(env, om);
        mgr.consumer("kc-bus", Role.CLIENT, e -> { });
        assertThat(CapturingTransportProvider.lastConsume.selector()).isEqualTo(defaultClientSelector());
        mgr.close();
    }

    @Test
    void noConfigAtAllYieldsTheDisabledPod() {
        // neither the catalog nor the service-level ref defines x-rod -> the OFF pod (no-op), not an error.
        MockEnvironment env = new MockEnvironment();
        env.setProperty("esquire.kc-bus.messaging-bus.bus-id", "esquire.kc");
        env.setProperty("esquire.kc-bus.messaging-bus.slot-id", "kc");
        XRodManager mgr = new XRodManager(env, om);
        pro.mir0n.esquire.messaging.xrod.IXRod rod = mgr.producer("kc-bus", Role.BROADCAST);
        assertThat(rod).isInstanceOf(pro.mir0n.esquire.messaging.xrod.impl.XRodDisabled.class);
        assertThat(rod.isEnabled()).isFalse();
        mgr.close();
    }

    @Test
    void explicitRodClassDisabledYieldsTheDisabledPod() {
        // a leg that exists but sets rod-class = XRodDisabled is the same OFF pod (slot intentionally disabled).
        MockEnvironment env = baseEnv();
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.rod-class", "XRodDisabled");
        XRodManager mgr = new XRodManager(env, om);
        pro.mir0n.esquire.messaging.xrod.IXRod rod = mgr.producer("kc-bus", Role.BROADCAST);
        assertThat(rod).isInstanceOf(pro.mir0n.esquire.messaging.xrod.impl.XRodDisabled.class);
        assertThat(rod.isEnabled()).isFalse();
        mgr.close();
    }

    @Test
    void incompleteTransportFailsFast() {
        // a leg that declares a transport but omits the destination -> fail-fast at resolve (XRod.validate),
        // not a late silent no-op. baseEnv() sets provider + endpoint but NO destination; default rod-class XRod.
        XRodManager mgr = new XRodManager(baseEnv(), om);
        assertThatThrownBy(() -> mgr.producer("kc-bus", Role.BROADCAST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transport.destination");
        mgr.close();
    }

    @Test
    void shutdownClosesTheProducerTransportPublisher() {
        // a producer leg owns a transport publisher; shutting the rod down must close it (release the broker connection).
        MockEnvironment env = baseEnv();
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.destination", "esquire.kc.q");
        XRodManager mgr = new XRodManager(env, om);
        mgr.producer("kc-bus", Role.BROADCAST);
        assertThat(CapturingTransportProvider.publisherCloseCount.get()).isZero();
        mgr.close();
        assertThat(CapturingTransportProvider.publisherCloseCount.get()).isEqualTo(1);
    }

    // --- R&R behaviour (request/response node + role selector) lives ONLY in XRodRR, not base XRod ---

    @Test
    void baseXRodIsSingleNodeWithNoRoleSelector() {
        // a base-XRod leg with a single destination AND R&R nodes present: base XRod ignores role -- null selector,
        // and it always uses the single `destination`, never the request/response nodes.
        MockEnvironment env = baseEnv();   // no rod-class -> default XRod
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.destination", "esquire.kc.single");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.request-node", "request");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.response-node", "response");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.node[0].node-id", "request");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.node[0].destination", "esquire.kc.request");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.node[1].node-id", "response");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.node[1].destination", "esquire.kc.response");

        XRodManager mgr = new XRodManager(env, om);
        mgr.consumer("kc-bus", Role.CLIENT, e -> { });
        assertThat(CapturingTransportProvider.lastConsume.selector()).isNull();
        assertThat(CapturingTransportProvider.lastConsumeNode).isEqualTo("esquire.kc.single");
        mgr.close();
    }

    @Test
    void xRodRRPicksTheRequestResponseNodeByRole() {
        // an R&R leg (rod-class XRodRR, request + response nodes): CLIENT consumes the RESPONSE node (filter by
        // rod-id); SERVER consumes the REQUEST node (filter by slot-id).
        MockEnvironment env = baseEnv();
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.rod-class", "XRodRR");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.request-node", "request");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.response-node", "response");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.node[0].node-id", "request");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.node[0].destination", "esquire.kc.request");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.node[1].node-id", "response");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.node[1].destination", "esquire.kc.response");

        XRodManager client = new XRodManager(env, om);
        client.consumer("kc-bus", Role.CLIENT, e -> { });
        assertThat(CapturingTransportProvider.lastConsumeNode).isEqualTo("esquire.kc.response");
        assertThat(CapturingTransportProvider.lastConsume.selector()).isEqualTo(defaultClientSelector());
        client.close();

        CapturingTransportProvider.reset();
        XRodManager server = new XRodManager(env, om);
        server.consumer("kc-bus", Role.SERVER, e -> { });
        assertThat(CapturingTransportProvider.lastConsumeNode).isEqualTo("esquire.kc.request");
        assertThat(CapturingTransportProvider.lastConsume.selector()).isEqualTo("SlotID = 'kc'");
        server.close();
    }
}
