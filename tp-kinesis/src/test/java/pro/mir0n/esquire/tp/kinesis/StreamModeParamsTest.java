package pro.mir0n.esquire.tp.kinesis;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kinesis.model.StreamMode;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the stream capacity mode and shard count. The default is a COST decision: on-demand bills
 *  per stream-hour whether or not a record moves, provisioned bills per shard-hour, and one shard is what an
 *  absent partition-by already means. A wrong value is refused rather than guessed, because guessing here
 *  spends money silently. */
class StreamModeParamsTest {

    private static Map<String, String> settings(String... keyValues) {
        Map<String, String> ret = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            ret.put(keyValues[i], keyValues[i + 1]);
        }
        return ret;
    }

    @Test
    void absentSettingsGiveProvisionedOneShard() {
        assertEquals(StreamMode.PROVISIONED, TransportProvider.streamMode(null));
        assertEquals(1, TransportProvider.shardCount(null));
    }

    @Test
    void emptySettingsGiveProvisionedOneShard() {
        Map<String, String> none = settings();
        assertEquals(StreamMode.PROVISIONED, TransportProvider.streamMode(none));
        assertEquals(1, TransportProvider.shardCount(none));
    }

    @Test
    void onDemandIsTakenWhenItIsAskedFor() {
        assertEquals(StreamMode.ON_DEMAND, TransportProvider.streamMode(settings("Mode", "ON_DEMAND")));
    }

    @Test
    void provisionedIsTakenWhenItIsAskedFor() {
        assertEquals(StreamMode.PROVISIONED, TransportProvider.streamMode(settings("Mode", "PROVISIONED")));
    }

    @Test
    void theModeNameIsNotCaseSensitiveAndIsTrimmed() {
        assertEquals(StreamMode.ON_DEMAND, TransportProvider.streamMode(settings("Mode", " on_demand ")));
        assertEquals(StreamMode.PROVISIONED, TransportProvider.streamMode(settings("Mode", "Provisioned")));
    }

    @Test
    void anUnknownModeIsRefused() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TransportProvider.streamMode(settings("Mode", "ELASTIC")));
        assertTrue(ex.getMessage().contains("ELASTIC"));
        assertTrue(ex.getMessage().contains("PROVISIONED"));
        assertTrue(ex.getMessage().contains("ON_DEMAND"));
    }

    @Test
    void aShardCountIsTakenWhenItIsGiven() {
        assertEquals(4, TransportProvider.shardCount(settings("ShardCount", "4")));
        assertEquals(2, TransportProvider.shardCount(settings("ShardCount", " 2 ")));
    }

    @Test
    void aShardCountBelowOneIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> TransportProvider.shardCount(settings("ShardCount", "0")));
        assertThrows(IllegalStateException.class,
                () -> TransportProvider.shardCount(settings("ShardCount", "-1")));
    }

    @Test
    void aShardCountThatIsNotANumberIsRefused() {
        assertThrows(NumberFormatException.class,
                () -> TransportProvider.shardCount(settings("ShardCount", "many")));
    }

    @Test
    void theModeAndShardCountAreNotMistakenForUnknownStreamSettings() {
        // ensureStream reads them at create time, so applyStreamSettings must let them past rather than
        // refuse them as unknown keys.
        Map<String, String> both = settings("Mode", "ON_DEMAND", "ShardCount", "3");
        assertEquals(StreamMode.ON_DEMAND, TransportProvider.streamMode(both));
        assertEquals(3, TransportProvider.shardCount(both));
    }
}
