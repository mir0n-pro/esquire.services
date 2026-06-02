package pro.mir0n.esquire.kcMaster.buffer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KcPathBufferTest {

    private KcPathBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new KcPathBuffer();
        ReflectionTestUtils.setField(buffer, "ttlMs",           60_000L);
        ReflectionTestUtils.setField(buffer, "pruneIntervalMs", 30_000L);
    }

    @Test
    @DisplayName("store then consume: returns the path and clears entry")
    void store_consume_returnsPath() {
        buffer.store("uid-1", "1.10.uid-1.");
        assertThat(buffer.size()).isEqualTo(1);

        String got = buffer.consume("uid-1");

        assertThat(got).isEqualTo("1.10.uid-1.");
        assertThat(buffer.size()).isZero();
    }

    @Test
    @DisplayName("consume: empty buffer returns null")
    void consume_empty_returnsNull() {
        assertThat(buffer.consume("nope")).isNull();
    }

    @Test
    @DisplayName("store: latest path wins (overwrite)")
    void store_overwrite() {
        buffer.store("uid-1", "1.10.");
        buffer.store("uid-1", "1.20.");

        assertThat(buffer.consume("uid-1")).isEqualTo("1.20.");
    }

    @Test
    @DisplayName("store/consume: null entityId or null path is a no-op")
    void store_nullArgs_noop() {
        buffer.store(null, "1.x.");
        buffer.store("uid-1", null);

        assertThat(buffer.size()).isZero();
        assertThat(buffer.consume(null)).isNull();
    }

    @Test
    @DisplayName("consume: entry older than ttl returns null (expired)")
    void consume_expired_returnsNull() {
        ReflectionTestUtils.setField(buffer, "ttlMs", 100L);
        // Insert a stale entry directly so the timestamp is in the past beyond ttl.
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, KcPathBuffer.TimestampedPath> entries =
                (java.util.concurrent.ConcurrentHashMap<String, KcPathBuffer.TimestampedPath>)
                        ReflectionTestUtils.getField(buffer, "entries");
        entries.put("uid-stale",
                new KcPathBuffer.TimestampedPath("1.10.", System.currentTimeMillis() - 10_000L));

        assertThat(buffer.consume("uid-stale")).isNull();
    }

    @Test
    @DisplayName("prune: removes entries older than ttl, keeps fresh ones")
    void prune_removesExpired() {
        // Fresh entry stored at "now"
        buffer.store("uid-fresh", "1.fresh.");

        // Insert an entry whose ts is in the deep past
        ReflectionTestUtils.setField(buffer, "ttlMs", 100L);
        java.util.concurrent.ConcurrentHashMap<String, KcPathBuffer.TimestampedPath> entries =
                (java.util.concurrent.ConcurrentHashMap<String, KcPathBuffer.TimestampedPath>)
                        ReflectionTestUtils.getField(buffer, "entries");
        entries.put("uid-stale",
                new KcPathBuffer.TimestampedPath("1.stale.", System.currentTimeMillis() - 10_000L));

        buffer.prune();

        assertThat(entries).containsKey("uid-fresh");
        assertThat(entries).doesNotContainKey("uid-stale");
    }
}
