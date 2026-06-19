package pro.mir0n.esquire.messaging;

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
        env.setProperty("esquire.messaging-bus[0].slot[0].slot-id", "rod-audit");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.rod-id", "rod.0");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.publisher-pool-size", "2");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.provider", FAKE);
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.endpoint", "tcp://localhost:61616");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.destination", "esquire.rod.audit");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.topic", "false");
        return new MessagingBusCatalog(env);
    }

    @Test
    void resolvesTheLegFromConfig() {
        XRodParams p = catalog().resolve("esquire.rod", "rod-audit");

        assertThat(p.rodId()).isEqualTo("rod.0");
        assertThat(p.publisherPoolSizeOr(0)).isEqualTo(2);
        assertThat(p.transport().provider()).isEqualTo(FAKE);
        assertThat(p.transport().endpoint()).isEqualTo("tcp://localhost:61616");
        assertThat(p.transport().destination()).isEqualTo("esquire.rod.audit");
        assertThat(p.transport().topicOrFalse()).isFalse();
    }

    @Test
    void unknownLegThrows() {
        assertThatThrownBy(() -> catalog().resolve("esquire.rod", "nope"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slot-id=nope");
    }

    @Test
    void duplicateLegResolvesToTheLastWithoutThrowing() {
        // a config mistake -- two legs share (bus-id, slot-id). find() WARNS and takes the last; it must NOT
        // throw (boot does not crash on a duplicate -- the warning surfaces it).
        MockEnvironment env = new MockEnvironment();
        env.setProperty("esquire.messaging-bus[0].bus-id", "esquire.rod");
        env.setProperty("esquire.messaging-bus[0].slot[0].slot-id", "rod-audit");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.rod-id", "first");
        env.setProperty("esquire.messaging-bus[0].slot[1].slot-id", "rod-audit");   // duplicate
        env.setProperty("esquire.messaging-bus[0].slot[1].x-rod.rod-id", "last");

        XRodParams p = new MessagingBusCatalog(env).find("esquire.rod", "rod-audit");
        assertThat(p).isNotNull();
        assertThat(p.rodId()).isEqualTo("last");
    }

    @Test
    void transportParamsKeepDottedVendorKeysVerbatim() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("esquire.messaging-bus[0].bus-id", "esquire.rod");
        env.setProperty("esquire.messaging-bus[0].slot[0].slot-id", "rod-audit");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.provider", FAKE);
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.endpoint", "tcp://localhost:61616");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.destination", "esquire.rod.audit");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.params.jms.useAsyncSend", "true");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.transport.params.transport.connectTimeout", "10000");

        Map<String, String> params = new MessagingBusCatalog(env).resolve("esquire.rod", "rod-audit").transport().params();

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
        env.setProperty("esquire.messaging-bus[0].slot[0].slot-id", "audit");
        env.setProperty("esquire.messaging-bus[0].slot[0].x-rod.rod-class", "XRod");
        env.setProperty("enyman.messaging-bus[0].bus-id", "audit-b");
        env.setProperty("enyman.messaging-bus[0].slot[0].slot-id", "audit");
        env.setProperty("enyman.messaging-bus[0].slot[0].x-rod.rod-class", "XRodInProcess");

        MessagingBusCatalog c = new MessagingBusCatalog(env);

        assertThat(c.find("audit-c", "audit")).isNotNull();                 // shared topology leg
        assertThat(c.find("audit-b", "audit")).isNotNull();                 // service-local extension
        assertThat(c.find("audit-b", "audit").rodClass()).isEqualTo("XRodInProcess");
    }
}
