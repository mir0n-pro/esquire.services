package pro.mir0n.esquire.tp.sqns;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.transport.BusIdentity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests for the queue-name rules: SQS has no message selector, so the split XRodRR used to make with one
 *  is a queue here. The publisher takes the value off the message, the consumer off its own identity, and the
 *  two must land on the SAME name or a reply never reaches its requester. */
class SqsSupportTest {

    private static final String REQUEST  = "esquire.kc.request";
    private static final String RESPONSE = "esquire.kc.response";

    private static BusIdentity identity(String rodId, String slotId) {
        return new BusIdentity("esquire.kc", slotId, rodId);
    }

    private static Map<String, Object> headers(String field, Object value) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put(field, value);
        return ret;
    }

    @Test
    void aLegWithoutRouteByUsesTheDestinationAlone() {
        assertEquals("esquire-kc-request", SqsSupport.publishQueueName(REQUEST, null, Map.of()));
        assertEquals("esquire-kc-request", SqsSupport.consumeQueueName(REQUEST, null, identity("enyman.0", "kc")));
    }

    @Test
    void theRequestLegSplitsBySlotSoEveryServerOfTheSlotCompetes() {
        Map<String, Object> message = headers(BusConstants.FIELD_SLOT_ID, "kc");
        assertEquals("esquire-kc-request-kc",
                SqsSupport.publishQueueName(REQUEST, BusConstants.FIELD_SLOT_ID, message));
        assertEquals("esquire-kc-request-kc",
                SqsSupport.consumeQueueName(REQUEST, BusConstants.FIELD_SLOT_ID, identity("kcmaster.0", "kc")));
    }

    /** The heart of the design: the reply carries the REQUESTER's rod-id (the server x-rod echoes it), so the
     *  queue the server sends to is the queue the requester reads. If these two ever disagree, a reply is
     *  delivered to a queue nobody drains and the caller waits forever. */
    @Test
    void aReplyLandsOnTheQueueItsRequesterReads() {
        BusIdentity requester = identity("enyman.0", "kc");
        Map<String, Object> reply = headers(BusConstants.FIELD_ROD_ID, requester.rodId());

        String sentTo = SqsSupport.publishQueueName(RESPONSE, BusConstants.FIELD_ROD_ID, reply);
        String readFrom = SqsSupport.consumeQueueName(RESPONSE, BusConstants.FIELD_ROD_ID, requester);

        assertEquals("esquire-kc-response-enyman-0", sentTo);
        assertEquals(sentTo, readFrom);
    }

    @Test
    void twoClientsGetTwoDifferentResponseQueues() {
        String eny = SqsSupport.consumeQueueName(RESPONSE, BusConstants.FIELD_ROD_ID, identity("enyman.0", "kc"));
        String key = SqsSupport.consumeQueueName(RESPONSE, BusConstants.FIELD_ROD_ID, identity("keysmith.0", "kc"));
        assertEquals("esquire-kc-response-enyman-0", eny);
        assertEquals("esquire-kc-response-keysmith-0", key);
    }

    @Test
    void routingByBusIdIsSupportedToo() {
        assertEquals("esquire-kc-request-esquire-kc",
                SqsSupport.consumeQueueName(REQUEST, BusConstants.FIELD_BUS_ID, identity("enyman.0", "kc")));
    }

    @Test
    void aMessageMissingTheRoutingFieldFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> SqsSupport.publishQueueName(RESPONSE, BusConstants.FIELD_ROD_ID, Map.of()));
        assertThrows(IllegalStateException.class,
                () -> SqsSupport.publishQueueName(RESPONSE, BusConstants.FIELD_ROD_ID,
                        headers(BusConstants.FIELD_ROD_ID, "  ")));
    }

    @Test
    void aRodMissingTheRoutingIdentityFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> SqsSupport.consumeQueueName(RESPONSE, BusConstants.FIELD_ROD_ID, identity(null, "kc")));
        assertThrows(IllegalStateException.class,
                () -> SqsSupport.consumeQueueName(RESPONSE, "NoSuchField", identity("enyman.0", "kc")));
    }

    @Test
    void everyCharacterSqsRefusesBecomesAHyphen() {
        assertEquals("esquire-rod-audit", SqsSupport.sanitize("esquire.rod.audit"));
        assertEquals("keep_the-safe0nes", SqsSupport.sanitize("keep_the-safe0nes"));
        assertEquals("a-b-c-d", SqsSupport.sanitize("a.b:c d"));
    }
}
