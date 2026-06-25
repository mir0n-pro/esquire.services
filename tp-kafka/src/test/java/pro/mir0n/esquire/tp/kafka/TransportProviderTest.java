package pro.mir0n.esquire.tp.kafka;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.BusConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for the Kafka topic routing: session (alive) messages go to the {@code .admin} liveness topic,
 *  every other message to the log topic -- so the append-only audit log topic keeps only real records. */
class TransportProviderTest {

    private static final String LOG = "esquire.rod.audit";

    @Test
    void sessionMessagesRideToTheAdminTopic() {
        assertEquals(LOG + ".admin", TransportProvider.topicFor(LOG, BusConstants.MSG_TYPE_HEARTBEAT));
        assertEquals(LOG + ".admin", TransportProvider.topicFor(LOG, BusConstants.MSG_TYPE_TEST_REQUEST));
    }

    @Test
    void applicationMessagesRideToTheLogTopic() {
        assertEquals(LOG, TransportProvider.topicFor(LOG, BusConstants.MSG_TYPE_AUDIT));
        assertEquals(LOG, TransportProvider.topicFor(LOG, BusConstants.MSG_TYPE_ENTITY_BROADCASTS));
        assertEquals(LOG, TransportProvider.topicFor(LOG, BusConstants.MSG_TYPE_REQUEST));
        assertEquals(LOG, TransportProvider.topicFor(LOG, null));
    }
}
