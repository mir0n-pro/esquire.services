package pro.mir0n.esquire.messaging.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessagingBusCatalogTest {

    private static final String FAKE = "pro.mir0n.esquire.messaging.FakeTransportProvider";
    private final ObjectMapper om = new ObjectMapper();

    private MessagingBusCatalog catalog() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("esquire.messaging-bus[0].bus-id", "esquire.rod");
        env.setProperty("esquire.messaging-bus[0].slots[0].slot-id", "rod-audit");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.rod-id", "rod.0");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.publisher-pool-size", "2");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.provider", FAKE);
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.endpoint", "tcp://localhost:61616");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.destination", "esquire.rod.audit");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.params.pubSubDomain", "false");
        MessagingBusCatalog catalog = new MessagingBusCatalog(env);
        catalog.load();
        return catalog;
    }

    @Test
    void resolvesTheLegFromConfig() {
        XRodParams p = catalog().resolve("esquire.rod", "rod-audit");

        assertThat(p.rodId()).isEqualTo("rod.0");
        assertThat(p.publisherPoolSizeOr(0)).isEqualTo(2);
        assertThat(p.transport().provider()).isEqualTo(FAKE);
        assertThat(p.transport().endpoint()).isEqualTo("tcp://localhost:61616");
        assertThat(p.transport().destination()).isEqualTo("esquire.rod.audit");
        assertThat(p.transport().params()).containsEntry("pubSubDomain", "false");   // JMS knob rides params now
    }

    @Test
    void unknownLegThrows() {
        assertThatThrownBy(() -> catalog().resolve("esquire.rod", "nope"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slot-id=nope");
    }

    @Test
    void duplicateSlotIdFailsFastAtLoad() {
        // the topology is a LIST used AS A MAP -- two legs sharing (bus-id, slot-id) is a config mistake the
        // catalog REJECTS at construction (was: find() warned and took the last).
        MockEnvironment env = new MockEnvironment();
        env.setProperty("esquire.messaging-bus[0].bus-id", "esquire.rod");
        env.setProperty("esquire.messaging-bus[0].slots[0].slot-id", "rod-audit");
        env.setProperty("esquire.messaging-bus[0].slots[1].slot-id", "rod-audit");   // duplicate slot-id

        assertThatThrownBy(() -> new MessagingBusCatalog(env).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate slot-id=rod-audit");
    }

    @Test
    void duplicateBusIdFailsFastAtLoad() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("esquire.messaging-bus[0].bus-id", "esquire.rod");
        env.setProperty("esquire.messaging-bus[0].slots[0].slot-id", "a");
        env.setProperty("esquire.messaging-bus[1].bus-id", "esquire.rod");           // duplicate bus-id
        env.setProperty("esquire.messaging-bus[1].slots[0].slot-id", "b");

        assertThatThrownBy(() -> new MessagingBusCatalog(env).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate bus-id=esquire.rod");
    }

    @Test
    void duplicateNodeIdFailsFastAtLoad() {
        // an R&R leg's transport.nodes is also a list-as-map: node-id must be unique within the x-rod.
        MockEnvironment env = new MockEnvironment();
        env.setProperty("esquire.messaging-bus[0].bus-id", "esquire.kc");
        env.setProperty("esquire.messaging-bus[0].slots[0].slot-id", "kc");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.nodes[0].node-id", "request");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.nodes[1].node-id", "request");  // duplicate

        assertThatThrownBy(() -> new MessagingBusCatalog(env).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate node-id=request");
    }

    @Test
    void transportParamsKeepDottedVendorKeysVerbatim() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("esquire.messaging-bus[0].bus-id", "esquire.rod");
        env.setProperty("esquire.messaging-bus[0].slots[0].slot-id", "rod-audit");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.provider", FAKE);
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.endpoint", "tcp://localhost:61616");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.destination", "esquire.rod.audit");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.params.jms.useAsyncSend", "true");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.transport.params.transport.connectTimeout", "10000");

        MessagingBusCatalog catalog = new MessagingBusCatalog(env);
        catalog.load();
        Map<String, String> params = catalog.resolve("esquire.rod", "rod-audit").transport().params();

        assertThat(params).containsEntry("jms.useAsyncSend", "true")
                          .containsEntry("transport.connectTimeout", "10000");
    }

    @Test
    void findReturnsNullForAnAbsentLeg() {
        // the config may live at the service level only -- find() does NOT throw (the frontend layers on top).
        assertThat(catalog().find("esquire.rod", "nope")).isNull();
    }

    @Test
    void overrideMergesPerGroup_serviceGroupWinsInFull() {
        XRodParams base = catalog().resolve("esquire.rod", "rod-audit");
        // the service override sets only the pool-size group; every other group stays from the base.
        XRodParams override = XRodParams.from(Map.of("pool-size", 8));

        XRodParams merged = base.merge(override);

        assertThat(merged.poolSizeOr(0)).isEqualTo(8);                  // overwritten
        assertThat(merged.rodId()).isEqualTo("rod.0");                  // base
        assertThat(merged.publisherPoolSizeOr(0)).isEqualTo(2);         // base
        assertThat(merged.transport().endpoint()).isEqualTo("tcp://localhost:61616");  // base group untouched
    }

    @Test
    void overrideTransportReplacesTheWholeGroup() {
        XRodParams base = catalog().resolve("esquire.rod", "rod-audit");
        // the service sets the transport group -> it wins IN FULL; the base's transport.endpoint is gone.
        XRodParams override = XRodParams.from(Map.of("transport", Map.of("provider", FAKE, "destination", "svc.q")));

        XRodParams merged = base.merge(override);

        assertThat(merged.transport().destination()).isEqualTo("svc.q");   // service's
        assertThat(merged.transport().endpoint()).isNull();                // base's endpoint dropped (whole-group)
        assertThat(merged.rodId()).isEqualTo("rod.0");                     // other groups from base
    }

    @Test
    void consumeLegTakesTheWholeNodeNoSelector() {
        // the catalog consume path is always whole-node, no selector -- a selector is the x-rod's concern (XRodRR
        // computes the R&R one: CLIENT by rod-id, SERVER by slot-id). The xxRod audit director uses this path.
        MessagingBusCatalog.ConsumeLeg leg = catalog().consumeLeg("esquire.rod", "rod-audit", om);

        assertThat(leg.destination()).isEqualTo("esquire.rod.audit");
        assertThat(leg.settings().concurrency()).isEqualTo(1);   // default
        assertThat(leg.settings().selector()).isNull();
    }

    @Test
    void catalogUnionsSharedTopologyWithServiceLocalLegs() {
        // the catalog = the shared cross-service topology (esquire.messaging-bus, the imported file) UNIONED with
        // each service's OWN topology (<spring.application.name>.messaging-bus) -- this is how a service
        // adds its in-process audit-b leg on top of the shared buses. BOTH keys' legs must resolve.
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "enyman");
        env.setProperty("esquire.messaging-bus[0].bus-id", "audit-c");
        env.setProperty("esquire.messaging-bus[0].slots[0].slot-id", "audit");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.rod-class", "XRod");
        env.setProperty("enyman.messaging-bus[0].bus-id", "audit-b");
        env.setProperty("enyman.messaging-bus[0].slots[0].slot-id", "audit");
        env.setProperty("enyman.messaging-bus[0].slots[0].x-rod.rod-class", "XRodInProcess");

        MessagingBusCatalog c = new MessagingBusCatalog(env);
        c.load();

        assertThat(c.find("audit-c", "audit")).isNotNull();                 // shared topology leg
        assertThat(c.find("audit-b", "audit")).isNotNull();                 // service-local extension
        assertThat(c.find("audit-b", "audit").rodClass()).isEqualTo("XRodInProcess");
    }

    @Test
    void overlayReplacesTheSharedSlotBySameId() {
        // the overlay declares the SAME (bus-id, slot-id) as the shared catalog -> it REPLACES it (the service
        // wins), NOT a duplicate. The shared rod-class XRod is overridden by the overlay's XRodInProcess.
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "enyman");
        env.setProperty("esquire.messaging-bus[0].bus-id", "audit-c");
        env.setProperty("esquire.messaging-bus[0].slots[0].slot-id", "audit");
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.rod-class", "XRod");
        env.setProperty("enyman.messaging-bus[0].bus-id", "audit-c");                 // SAME bus-id
        env.setProperty("enyman.messaging-bus[0].slots[0].slot-id", "audit");         // SAME slot-id
        env.setProperty("enyman.messaging-bus[0].slots[0].x-rod.rod-class", "XRodInProcess");

        MessagingBusCatalog c = new MessagingBusCatalog(env);
        c.load();

        assertThat(c.find("audit-c", "audit").rodClass()).isEqualTo("XRodInProcess");  // overlay won
    }

    @Test
    void overlayAddsANewSlotToTheSharedBus() {
        // the overlay declares the SAME bus-id but a NEW slot-id -> the slot is ADDED to the shared bus; the
        // shared slot stays. Both resolve.
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "enyman");
        env.setProperty("esquire.messaging-bus[0].bus-id", "esquire.kc");
        env.setProperty("esquire.messaging-bus[0].slots[0].slot-id", "kc");
        env.setProperty("enyman.messaging-bus[0].bus-id", "esquire.kc");              // SAME bus-id
        env.setProperty("enyman.messaging-bus[0].slots[0].slot-id", "kc-extra");      // NEW slot-id

        MessagingBusCatalog c = new MessagingBusCatalog(env);
        c.load();

        assertThat(c.find("esquire.kc", "kc")).isNotNull();          // shared slot kept
        assertThat(c.find("esquire.kc", "kc-extra")).isNotNull();    // overlay slot added
    }
}
