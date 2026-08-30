package pro.mir0n.esquire.messaging.transport;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.BusConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Unit tests for own-exclusion -- what a broker does with noLocal. Its own filter, in front of whatever
 *  subscription the consumer brought. */
class OwnExcludingTest {

    private final List<TransportMessage> taken = new ArrayList<>();
    private final Consumer<TransportMessage> sink = taken::add;

    private static TransportMessage from(String rodId) {
        Map<String, Object> headers = new LinkedHashMap<>();
        if (rodId != null) {
            headers.put(BusConstants.FIELD_ROD_ID, rodId);
        }
        return new TransportMessage(headers, null);
    }

    @Test
    void withNoLocalOffTheHandlerIsUntouched() {
        assertSame(sink, OwnExcluding.wrap(sink, "enyman.0", false));
    }

    @Test
    void withoutARodIdThereIsNothingToExcludeBy() {
        assertSame(sink, OwnExcluding.wrap(sink, null, true));
        assertSame(sink, OwnExcluding.wrap(sink, "  ", true));
    }

    @Test
    void thisRodsOwnPublicationIsDroppedAndAPeersIsKept() {
        Consumer<TransportMessage> receiver = OwnExcluding.wrap(sink, "enyman.0", true);
        receiver.accept(from("enyman.0"));
        receiver.accept(from("enyman.1"));
        assertEquals(1, taken.size());
        assertEquals("enyman.1", taken.get(0).headers().get(BusConstants.FIELD_ROD_ID));
    }

    /** A message carrying no rod-id cannot be this rod's own, so it passes. */
    @Test
    void aMessageWithoutARodIdPasses() {
        Consumer<TransportMessage> receiver = OwnExcluding.wrap(sink, "enyman.0", true);
        receiver.accept(from(null));
        assertEquals(1, taken.size());
    }

    /** The two filters are independent and compose: own-exclusion in front of whatever the consumer asked for. */
    @Test
    void itComposesInFrontOfASubscription() {
        Consumer<TransportMessage> receiver = SelectingReceiver.wrap(sink, "RodID IN ('enyman.0','enyman.1')");
        receiver = OwnExcluding.wrap(receiver, "enyman.0", true);
        receiver.accept(from("enyman.0"));   // own -- dropped by own-exclusion
        receiver.accept(from("enyman.1"));   // peer, and asked for -- kept
        receiver.accept(from("enyman.2"));   // peer, but not asked for -- dropped by the subscription
        assertEquals(1, taken.size());
        assertEquals("enyman.1", taken.get(0).headers().get(BusConstants.FIELD_ROD_ID));
    }
}
