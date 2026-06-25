package pro.mir0n.esquire.messaging.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The leg identity resolves the {@code ${rod-id}} / {@code ${bus-id}} / {@code ${slot-id}} tokens in vendor
 *  params, and the transport settings apply it -- so the driver receives the real per-instance values, the same
 *  way for a single-node leg and an R&R node (both go through a settings object that carries the leg identity). */
class BusIdentityTest {

    @Test
    void expandTokensResolvesTheIdentityTriple() {
        BusIdentity id = new BusIdentity("esquire.kc", "kc", "enyman.0");
        Map<String, String> out = id.expandTokens(Map.of(
                "jms.clientID", "${rod-id}",
                "label", "${bus-id}/${slot-id}",
                "plain", "verbatim"));
        assertThat(out).containsEntry("jms.clientID", "enyman.0");        // ${rod-id}
        assertThat(out).containsEntry("label", "esquire.kc/kc");          // ${bus-id} / ${slot-id}
        assertThat(out).containsEntry("plain", "verbatim");               // no token -> untouched
    }

    @Test
    void aNullIdentityFieldExpandsToEmpty() {
        BusIdentity id = new BusIdentity("esquire.kc", "kc", null);       // no rod-id
        assertThat(id.expandTokens(Map.of("c", "${rod-id}")).get("c")).isEmpty();
    }

    @Test
    void settingsResolveTokensFromTheIdentity() {
        // the relocation: every driver-facing settings object expands its params against the BusIdentity it
        // carries -- one point, uniform for a single-node leg AND an R&R node.
        ConsumeSettings cs = new ConsumeSettings(new ObjectMapper(), "tcp://localhost:61616",
                new BusIdentity("esquire.kc", "kc", "enyman.0"),
                Map.of("jms.clientID", "${rod-id}"), 1, null);
        assertThat(cs.params()).containsEntry("jms.clientID", "enyman.0");
    }
}
