/*
 *  Esquire frameworks (tm)
 *  messaging library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.CapturingTransportProvider;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.xrod.impl.XRod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Subscription mechanics on a single-node (broadcast) CLIENT rod: the selector applied to the receive consumer
 *  is the caller's subscription ALONE (own-exclusion is the transport's noLocal, NOT folded in here), and the
 *  consumer is re-opened ONLY when the selector actually changes. A CLIENT over a both-legs transport runs both
 *  legs on one shared connection, so the consumer is opened via openConsumerOn. */
class XRodSubscriptionSelectorTest {

    private static final String CAPTURING = "pro.mir0n.esquire.messaging.CapturingTransportProvider";

    private static XRodParams leg() {
        Map<String, Object> wire = Map.of("provider", CAPTURING, "endpoint", "x://h", "destination", "s");
        Map<String, Object> raw  = Map.of("transport", wire);
        return XRodParams.from(raw).withBus("test-bus", "test-slot", "test.0");
    }

    @Test
    void subscription_isAppliedAlone_andSharesTheConnection() {
        CapturingTransportProvider.reset();
        XRod rod = new XRod();
        rod.configure(leg(), Role.CLIENT, new ObjectMapper());
        rod.init("rx", null);   // CLIENT over a both-legs transport -> the consumer shares the publisher's connection
        assertThat(CapturingTransportProvider.openConsumerOnCount.get()).isEqualTo(1);   // opened via openConsumerOn (shared)
        assertThat(CapturingTransportProvider.openConsumerCount.get()).isZero();         // never a separate-connection open
        assertThat(CapturingTransportProvider.lastConsume.selector()).isNull();          // base selector (broadcast = null)

        rod.setWorker("EventType = 'I'", e -> { });            // selector changes -> re-open
        assertThat(CapturingTransportProvider.openConsumerOnCount.get()).isEqualTo(2);
        assertThat(CapturingTransportProvider.lastConsume.selector()).isEqualTo("EventType = 'I'");  // the caller's predicate ALONE
        rod.shutdown();
    }

    @Test
    void unchangedSubscription_doesNotRecreateTheConsumer() {
        CapturingTransportProvider.reset();
        XRod rod = new XRod();
        rod.configure(leg(), Role.CLIENT, new ObjectMapper());
        rod.init("rx", null);
        assertThat(CapturingTransportProvider.openConsumerOnCount.get()).isEqualTo(1);

        rod.setWorker(null, e -> { });   // null subscription == the base selector -> nothing changes
        assertThat(CapturingTransportProvider.openConsumerOnCount.get()).isEqualTo(1);   // NOT re-opened -- worker just attached
        assertThat(CapturingTransportProvider.lastConsume.selector()).isNull();
        rod.shutdown();
    }
}
