package pro.mir0n.esquire.tp.sqns;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the prefix sub-group of a leg's params. AWS takes its settings through several different
 *  calls, so the prefix says which call a key is for; what is handed to the vendor is the key with the prefix
 *  gone -- the vendor's own name. */
class SqsSupportParamGroupTest {

    private static Map<String, String> params(String... keyValues) {
        Map<String, String> ret = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            ret.put(keyValues[i], keyValues[i + 1]);
        }
        return ret;
    }

    @Test
    void theGroupComesBackWithoutItsPrefix() {
        Map<String, String> group = SqsSupport.paramGroup(
                params("queue.VisibilityTimeout", "60", "queue.DelaySeconds", "5"), "queue");
        assertEquals(2, group.size());
        assertEquals("60", group.get("VisibilityTimeout"));
        assertEquals("5", group.get("DelaySeconds"));
    }

    @Test
    void onlyThatGroupComesBack() {
        Map<String, String> all = params("queue.VisibilityTimeout", "60",
                "topic.DisplayName", "esquire", "region", "us-east-1");
        assertEquals(Map.of("VisibilityTimeout", "60"), SqsSupport.paramGroup(all, "queue"));
        assertEquals(Map.of("DisplayName", "esquire"), SqsSupport.paramGroup(all, "topic"));
    }

    @Test
    void aGroupNobodySetIsEmptyRatherThanNull() {
        assertTrue(SqsSupport.paramGroup(params("region", "us-east-1"), "queue").isEmpty());
        assertTrue(SqsSupport.paramGroup(params(), "queue").isEmpty());
    }

    @Test
    void aPrefixThatIsAlsoTheStartOfAnotherKeyDoesNotBleed() {
        // "queued-thing" starts with "queue" but is not in the "queue." group.
        Map<String, String> all = params("queue.VisibilityTimeout", "60", "queued-thing", "no");
        assertEquals(Map.of("VisibilityTimeout", "60"), SqsSupport.paramGroup(all, "queue"));
    }

    @Test
    void aPrefixWithNothingAfterItIsNotAKey() {
        assertTrue(SqsSupport.paramGroup(params("queue.", "60"), "queue").isEmpty());
    }
}
