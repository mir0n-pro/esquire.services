package pro.mir0n.esquire.backend.o11y;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// I25: an unbounded tag must be STRUCTURALLY impossible, not merely asserted. The filter caps the distinct values
// per (meter name, tag key): past the cap a NEW value collapses to the sentinel, so a leak (an exception message,
// an entity id) costs one extra series instead of thousands -- while legitimate low-cardinality tags are untouched.
class EsqTagCardinalityCapTest {

    private static Meter.Id id(String name, String key, String value) {
        return new Meter.Id(name, Tags.of(key, value), null, null, Meter.Type.COUNTER);
    }

    @Test
    void distinctValues_pastTheCap_collapseToSentinel() {
        EsqTagCardinalityCap cap = new EsqTagCardinalityCap(3);   // small cap for the test
        for (int i = 0; i < 3; i++) {   // the first 3 distinct values pass through untouched
            assertThat(cap.map(id("esq.biz.perm.check.total", "kind", "k" + i)).getTag("kind")).isEqualTo("k" + i);
        }
        assertThat(cap.map(id("esq.biz.perm.check.total", "kind", "k3")).getTag("kind"))
                .as("the 4th new value must collapse, not mint a new series")
                .isEqualTo(EsqTagCardinalityCap.CAPPED);
        assertThat(cap.map(id("esq.biz.perm.check.total", "kind", "k4")).getTag("kind"))
                .isEqualTo(EsqTagCardinalityCap.CAPPED);
    }

    @Test
    void alreadySeenValue_afterCapReached_stillPasses() {
        EsqTagCardinalityCap cap = new EsqTagCardinalityCap(2);
        cap.map(id("esq.biz.x.total", "op", "a"));
        cap.map(id("esq.biz.x.total", "op", "b"));   // cap now full: {a, b}
        cap.map(id("esq.biz.x.total", "op", "c"));   // c collapses
        assertThat(cap.map(id("esq.biz.x.total", "op", "a")).getTag("op"))
                .as("a value ALREADY inside the cap keeps working -- only NEW values collapse")
                .isEqualTo("a");
        assertThat(cap.map(id("esq.biz.x.total", "op", "c")).getTag("op")).isEqualTo(EsqTagCardinalityCap.CAPPED);
    }

    @Test
    void tagKeysAreCappedIndependently() {
        EsqTagCardinalityCap cap = new EsqTagCardinalityCap(1);
        cap.map(id("esq.biz.y.total", "op", "a"));    // op budget now full
        assertThat(cap.map(id("esq.biz.y.total", "outcome", "ok")).getTag("outcome"))
                .as("a different tag KEY has its own budget")
                .isEqualTo("ok");
        assertThat(cap.map(id("esq.biz.y.total", "op", "b")).getTag("op")).isEqualTo(EsqTagCardinalityCap.CAPPED);
    }

    @Test
    void nonEsquireMeters_areLeftAlone() {
        EsqTagCardinalityCap cap = new EsqTagCardinalityCap(1);
        cap.map(id("http.server.requests", "uri", "/a"));
        assertThat(cap.map(id("http.server.requests", "uri", "/b")).getTag("uri"))
                .as("framework meters bound their own labels -- never capped by this")
                .isEqualTo("/b");
    }
}
