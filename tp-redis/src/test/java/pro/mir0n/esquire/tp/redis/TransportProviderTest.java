package pro.mir0n.esquire.tp.redis;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.BusConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for the Redis stream-key routing: session (alive) messages go to the {@code .admin} liveness
 *  stream, every other message to the log stream -- so the append-only audit log keeps only real records. */
class TransportProviderTest {

    private static final String LOG = "esquire.rod.audit";

    @Test
    void sessionMessagesRideToTheAdminStream() {
        assertEquals(LOG + ".admin", TransportProvider.streamFor(LOG, BusConstants.MSG_TYPE_HEARTBEAT));
        assertEquals(LOG + ".admin", TransportProvider.streamFor(LOG, BusConstants.MSG_TYPE_TEST_REQUEST));
    }

    @Test
    void applicationMessagesRideToTheLogStream() {
        assertEquals(LOG, TransportProvider.streamFor(LOG, BusConstants.MSG_TYPE_AUDIT));
        assertEquals(LOG, TransportProvider.streamFor(LOG, BusConstants.MSG_TYPE_ENTITY_BROADCASTS));
        assertEquals(LOG, TransportProvider.streamFor(LOG, BusConstants.MSG_TYPE_REQUEST));
        assertEquals(LOG, TransportProvider.streamFor(LOG, null));
    }
}
