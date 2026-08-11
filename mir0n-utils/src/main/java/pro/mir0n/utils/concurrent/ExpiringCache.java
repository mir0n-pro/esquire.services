/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/10/2026 mir0n  created (v1.2.12): generic hand-off cache whose entries expire by age -- a
 *                   ConcurrentHashMap with a timestamp per entry, one daemon prune thread and a lazy age
 *                   check on read, so an un-started cache is still correct. store() parks a value,
 *                   consume() takes it away, storeIfGreater() parks only when the arrival compares greater
 *                   than what is there, in one atomic merge. The value type is Comparable, so the cache
 *                   needs no comparator and never learns what its callers order by.
 */
package pro.mir0n.utils.concurrent;

import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A cache whose entries expire by AGE, with hand-off ("park it now, pick it up shortly") semantics:
 * {@link #store} puts a value under a key, {@link #consume} takes it away and returns it, and anything not
 * consumed within the TTL is dropped by a background prune.
 *
 * <p><b>Expiry happens in two places on purpose.</b> The scheduled prune keeps the map from growing, but a
 * reader must not depend on the scheduler having run: {@link #consume} re-checks the age of the entry it just
 * removed and returns null when it is too old. Without that lazy check an entry could slip past the prune
 * interval and be handed to a caller as if it were fresh -- the whole point of a TTL is that the caller never
 * has to think about it.
 *
 * <p><b>Consume REMOVES.</b> This is a hand-off, not a lookup table: a value is meant to be taken once. A
 * cache that needs read-many semantics is a different thing and should not be bent out of this one.
 *
 * <p><b>Lifecycle.</b> {@link #start} spawns one daemon prune thread; {@link #stop} shuts it down. Both are
 * idempotent. store / consume work with or without the prune thread running -- the lazy check above means an
 * un-started cache is still CORRECT, just unbounded, which is exactly what a unit test wants.
 *
 * <p>Framework-neutral by design: it takes a {@link Logger} and its timings as constructor arguments, so it
 * carries no dependency on Spring or on any particular configuration mechanism. The owning component supplies
 * both.
 *
 * <p><b>The value type is {@link Comparable}</b> so {@link #storeIfGreater} can order arrivals with no help
 * from the caller. The value already knows what "greater" means for it; passing a comparator in at every call
 * site said the same thing a second time, in a second place, where it could drift.
 *
 * @param <K> key type
 * @param <V> value type; its natural order is what {@link #storeIfGreater} compares
 */
public class ExpiringCache<K, V extends Comparable<? super V>> {

    /** A stored value plus the epoch-ms it was stored at -- the age the TTL is measured against. */
    public record Timestamped<V>(V value, long ts) {}

    private final Logger devLog;
    private final long   ttlMs;
    private final long   pruneIntervalMs;
    private final String threadName;

    private final ConcurrentHashMap<K, Timestamped<V>> entries = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    /** Prune at half the TTL when no interval is given: often enough that a dead entry does not linger for a
     *  whole extra TTL, rare enough that the thread is not a cost. */
    public ExpiringCache(Logger devLog, long ttlMs) {
        this(devLog, ttlMs, Math.max(1L, ttlMs / 2L));
    }

    public ExpiringCache(Logger devLog, long ttlMs, long pruneIntervalMs) {
        this.devLog          = devLog;
        this.ttlMs           = ttlMs;
        this.pruneIntervalMs = pruneIntervalMs;
        this.threadName      = threadNameFrom(devLog);
    }

    /** Start the prune thread. @return true when this call started it, false when it was already running. */
    public synchronized boolean start() {
        boolean ret = false;
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, threadName);
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(this::prune, pruneIntervalMs, pruneIntervalMs, TimeUnit.MILLISECONDS);
            ret = true;
        }
        return ret;
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /** Park a value. A later store for the same key REPLACES the earlier one -- last store wins. */
    public void store(K key, V value) {
        store(key, value, System.currentTimeMillis());
    }

    /** Store at an explicit timestamp. Package-private: the test seam for age-dependent behaviour, so a test
     *  can age an entry exactly instead of sleeping and hoping. */
    void store(K key, V value, long ts) {
        if (key != null && value != null) {
            entries.put(key, new Timestamped<>(value, ts));
        }
    }

    /**
     * Park a value only when it is GREATER than what is already parked, by the value's own natural order.
     *
     * <p>Named for what it does -- {@code compareTo > 0} -- not for what any one caller means by it. kcMaster
     * parks a path whose order is a change number, so there "greater" reads as "newer"; that is the caller's
     * word, not this class's.
     *
     * <p>Use this instead of {@link #store} when arrivals can come out of order and the GREATEST one must win
     * rather than the LAST one to arrive. The two are the same thing only on a single thread with an ordered
     * feed; on a worker pool they are not, and the difference is silent.
     *
     * <p><b>It is one atomic operation on purpose.</b> "Read it, compare it, put it back" is a race when two
     * threads do it for the same key at once -- the loser's write can land on top of the winner's and the
     * older value survives. That is exactly the bug this method exists to prevent, so it must not be
     * assembled by the caller out of {@link #consume} and {@link #store}.
     *
     * <p>An entry past its TTL is treated as absent: the incoming value replaces it whatever the order says,
     * because a stale entry is not a fact about anything.
     *
     * @return true when the incoming value was stored
     */
    public boolean storeIfGreater(K key, V value) {
        boolean ret = false;
        if (key != null && value != null) {
            long now = System.currentTimeMillis();
            Timestamped<V> incoming = new Timestamped<>(value, now);
            Timestamped<V> winner = entries.merge(key, incoming, (parked, fresh) ->
                    (now - parked.ts() > ttlMs) || fresh.value().compareTo(parked.value()) > 0
                            ? fresh
                            : parked);
            ret = winner == incoming;
        }
        return ret;
    }

    /** Take the value away. Returns null when there is none, or when the one found is older than the TTL. */
    public V consume(K key) {
        V ret = null;
        if (key != null) {
            Timestamped<V> e = entries.remove(key);
            if (e != null && System.currentTimeMillis() - e.ts() <= ttlMs) {
                ret = e.value();
            }
        }
        return ret;
    }

    public int size() {
        return entries.size();
    }

    public long ttlMs() {
        return ttlMs;
    }

    /** Drop every entry older than the TTL. Safe to call directly (the scheduler calls it too). */
    public void prune() {
        long now = System.currentTimeMillis();
        int before = entries.size();
        Iterator<Map.Entry<K, Timestamped<V>>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, Timestamped<V>> e = it.next();
            if (now - e.getValue().ts() > ttlMs) {
                it.remove();
            }
        }
        if (devLog != null && before != entries.size()) {
            devLog.debug("expiring-cache[{}]: pruned {} -> {}", threadName, before, entries.size());
        }
    }

    /** A thread name the owner will recognise in a dump, taken from its logger rather than asking for one. */
    private static String threadNameFrom(Logger devLog) {
        String ret = "expiring-cache";
        if (devLog != null && devLog.getName() != null) {
            String n = devLog.getName();
            int dot = n.lastIndexOf('.');
            ret = (dot >= 0 && dot < n.length() - 1 ? n.substring(dot + 1) : n) + ".expiring-cache";
        }
        return ret;
    }
}
