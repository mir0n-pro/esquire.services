package pro.mir0n.esquire.tp.sqns;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the param gate. The other drivers can hand every param on to one place; AWS has four, so a
 *  key either names its call with a prefix or is one the driver owns. Both sides are tested: what must be let
 *  through, and what must be refused -- a gate that refuses something real is worse than no gate. */
class SqsSupportParamsTest {

    private static final Set<String> KNOWN = Set.of("region", "route-by", "wait-seconds", "batch-size", "noLocal");
    private static final Set<String> GROUPS = Set.of("client", "queue", "topic", "subscription");

    private static Map<String, String> params(String... keyValues) {
        Map<String, String> ret = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            ret.put(keyValues[i], keyValues[i + 1]);
        }
        return ret;
    }

    @Test
    void everyKeyTheDriverOwnsPasses() {
        assertDoesNotThrow(() -> SqsSupport.requireKnownParams("tp-sqs",
                params("region", "us-east-1", "route-by", "RodID", "wait-seconds", "20",
                       "batch-size", "10", "noLocal", "true"), KNOWN, GROUPS));
    }

    @Test
    void aKeyUnderAKnownPrefixPassesWhateverItIsCalled() {
        // the point of the prefix: the name after it is the VENDOR'S, so the driver must not have an opinion.
        assertDoesNotThrow(() -> SqsSupport.requireKnownParams("tp-sqs",
                params("queue.VisibilityTimeout", "60",
                       "queue.MessageRetentionPeriod", "345600",
                       "topic.DisplayName", "esquire",
                       "subscription.RawMessageDelivery", "true",
                       "client.apiCallTimeout", "5000"), KNOWN, GROUPS));
    }

    @Test
    void noParamsAtAllIsFine() {
        assertDoesNotThrow(() -> SqsSupport.requireKnownParams("tp-sqs", Map.of(), KNOWN, GROUPS));
    }

    @Test
    void anUnknownBareKeyIsRefused() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SqsSupport.requireKnownParams("tp-sqs", params("max-len", "0"), KNOWN, GROUPS));
        assertTrue(ex.getMessage().contains("max-len"), ex.getMessage());
    }

    @Test
    void anUnknownPrefixIsRefused() {
        // 'stream' belongs to tp-kinesis; on an SQS leg it has no call to go to.
        assertThrows(IllegalStateException.class, () -> SqsSupport.requireKnownParams("tp-sqs",
                params("stream.RetentionPeriodHours", "48"), KNOWN, GROUPS));
    }

    @Test
    void aTypoInAKnownKeyIsRefusedRatherThanIgnored() {
        // the whole reason the gate exists: this used to be dropped in silence, and the leg then ran with the
        // default while the topology said otherwise.
        assertThrows(IllegalStateException.class,
                () -> SqsSupport.requireKnownParams("tp-sqs", params("routeBy", "RodID"), KNOWN, GROUPS));
    }
}
