/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/15/2026 mir0n  created (v1.2.11 T11/I25): a MeterFilter that CAPS the distinct values any esq.biz.* /
 *                   messaging.* tag may take, so an unbounded tag is structurally impossible rather than merely
 *                   asserted. Past the cap a new value collapses to a sentinel instead of minting a new series --
 *                   the runtime PREVENTION that pairs with o11y-verify's detection (I21). The make-the-trap-
 *                   unwritable pattern, same as Gauges. Registered as a @Bean in ObservabilityConfig.
 * 07/23/2026 mir0n  v1.2.11 -- javadoc: the once-per-id property relies on Micrometer's preFilterIdToMeterMap cache
 *                   (>= 1.12), so map() is not per-op; and the cap is a best-effort SAFETY BOUND, not an exact quota
 */
package pro.mir0n.esquire.backend.o11y;

import pro.mir0n.o11y.Gauges;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime guard against unbounded meter cardinality on Esquire's own meters (the {@code esq.biz.*} and
 * {@code messaging.*} families).
 *
 * <p>Tag VALUES are supposed to be small enumerations (an op, an outcome, a kind, a bus id). The one failure mode
 * that turns a cheap counter into a memory / scrape problem is a caller-supplied UNBOUNDED value reaching a tag --
 * an exception message, an entity id, a correlationId (I6 is the live instance of that class). Asserting "the tags
 * are bounded" does not stop the next developer from writing {@code "outcome", ex.getMessage()}.
 *
 * <p>This makes it structurally impossible: it counts the distinct values seen per (meter name, tag key), and once
 * a tag reaches {@code maxValuesPerTag}, every FURTHER new value collapses to {@link #CAPPED} instead of creating a
 * new series. So the blast radius of the mistake is ONE extra series, not thousands -- and the sentinel is loud in
 * the data, so o11y-verify (I21) still flags that a cap was tripped. The set of seen values is itself bounded to
 * {@code maxValuesPerTag} per tag, so the guard cannot leak memory. The cap is a best-effort SAFETY BOUND, not an
 * exact quota: the size-check and the {@code add} are not atomic, so under a concurrent burst of DISTINCT new
 * values the set can exceed {@code maxValuesPerTag} by up to about the number of racing threads. That is
 * intended -- the goal is to bound the blast radius (a small constant, never thousands), and a handful over the
 * cap is immaterial to that; the memory bound still holds. Making it exact would need a lock on the map() path
 * for no real gain. Micrometer applies {@code map()} once per
 * distinct meter id (a new tag combination), which is exactly where a runaway would appear -- and that
 * once-per-id property is not per-call: it relies on the registry's {@code preFilterIdToMeterMap} cache
 * (Micrometer >= 1.12), which short-circuits a repeat {@code counter(name, tags)} lookup to the cached meter
 * WITHOUT re-running the filters, so {@code map()} runs only on the FIRST resolution of each id, not on every
 * meter op. (On a Micrometer older than 1.12 the filters ran per lookup; this project builds on 1.15.)
 *
 * <p>Only OUR meters are touched; framework meters ({@code http.server.requests} etc.) bound their own labels.
 */
public final class EsqTagCardinalityCap implements MeterFilter {

    /** The sentinel a tag value collapses to once its key has reached the cap. Loud on purpose. */
    public static final String CAPPED = "__capped__";

    private final int maxValuesPerTag;

    // (meter name + '\0' + tag key) -> the distinct values seen so far, held to at most maxValuesPerTag.
    private final Map<String, Set<String>> seen = new ConcurrentHashMap<>();

    public EsqTagCardinalityCap(int maxValuesPerTag) {
        this.maxValuesPerTag = maxValuesPerTag;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        Meter.Id ret = id;
        String name = id.getName();
        if (name.startsWith("esq.biz.") || name.startsWith("messaging.")) {
            List<Tag> original = id.getTags();
            List<Tag> capped = null;   // built only if a cap actually trips, so the clean path allocates nothing
            for (int i = 0; i < original.size(); i++) {
                Tag t = original.get(i);
                Set<String> vals = seen.computeIfAbsent(name + '\0' + t.getKey(),
                        k -> ConcurrentHashMap.newKeySet());
                if (vals.size() >= maxValuesPerTag && !vals.contains(t.getValue())) {
                    if (capped == null) {
                        capped = new ArrayList<>(original);
                    }
                    capped.set(i, Tag.of(t.getKey(), CAPPED));
                } else {
                    vals.add(t.getValue());
                }
            }
            if (capped != null) {
                ret = id.replaceTags(capped);
            }
        }
        return ret;
    }
}
