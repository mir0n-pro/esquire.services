package pro.mir0n.esquire.messaging;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link XRodParams#transport()} carries {@code transport.params.*} VERBATIM -- including any
 *  {@code ${rod-id}} / {@code ${bus-id}} / {@code ${slot-id}} tokens, which are resolved later against the leg
 *  identity when the transport settings are built (see {@code BusIdentityTest}), not here. */
class XRodParamsTest {

    @Test
    void transportParamsAreVerbatimIncludingIdentityTokens() {
        XRodParams leg = XRodParams.from(Map.of(
                "transport.provider", "activemq",
                "transport.endpoint", "tcp://localhost:61616",
                "transport.destination", "esquire.kc.q",
                "transport.params.jms.clientID", "${rod-id}",
                "transport.params.label", "${bus-id}/${slot-id}"))
                .withBus("esquire.kc", "kc", "enyman.0");

        Map<String, String> params = leg.transport().params();
        assertThat(params).containsEntry("jms.clientID", "${rod-id}");          // verbatim -- not expanded here
        assertThat(params).containsEntry("label", "${bus-id}/${slot-id}");
    }

    @Test
    void transportParamsWithoutTokensAreVerbatim() {
        XRodParams leg = XRodParams.from(Map.of(
                "transport.provider", "activemq",
                "transport.endpoint", "tcp://localhost:61616",
                "transport.destination", "q",
                "transport.params.jms.useAsyncSend", "true"))
                .withBus("esquire.kc", "kc", "enyman.0");

        assertThat(leg.transport().params()).containsEntry("jms.useAsyncSend", "true");
    }
}
