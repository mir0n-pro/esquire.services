/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: in-memory timed buffer for race-8c. Topic-side UPDATE_PATH events
 *                   parked here when the KC user does not exist yet; KcIdentityService.createUser
 *                   flushes the entry after the KC user is created so the post-move path lands on
 *                   the user. ConcurrentHashMap + scheduled prune (ttl default 60s).
 */
package pro.mir0n.esquire.kcMaster.buffer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Race-8c recovery buffer. The URQ EVENT_UPDATE_PATH handler (the imperative
 * path-update channel) silent-skips when the KC user does not yet exist --
 * dropping the new path on the floor. {@link
 * pro.mir0n.esquire.kcMaster.messaging.KcEntityBroadcastConsumer} is the
 * parallel safety-net channel that catches the same event off the entity
 * broadcast TOPIC and parks it here when the KC user is missing. The next
 * keySmith EVENT_CREATE URQ for that entity flushes the buffer in {@code
 * KcIdentityService.createUser}, so the freshly-minted user picks up the
 * post-move path.
 *
 * Buffer semantics:
 *   - keyed by entityId (esq_uid) -- string for interop with the JMS property.
 *   - value: TimestampedPath (path + storage epoch ms).
 *   - {@link #store} overwrites any prior entry for the same id (latest move wins).
 *   - {@link #consume} removes-and-returns; null if absent or expired.
 *   - scheduled prune drops entries older than ttl. Lazy-check on consume too,
 *     so a stale entry that snuck past the scheduler is not applied.
 *
 * Multi-instance: kcMaster runs single-instance today, so a per-pod buffer
 * closes the race fully. Under future redundant kcMaster the TOPIC broadcasts
 * to every instance -- each pod buffers locally; the pod that handles the
 * CREATE URQ flushes its own buffer. No shared state required.
 */
@Slf4j
@Component
public class KcPathBuffer {

    private static final org.slf4j.Logger devLog =
            LoggerFactory.getLogger("develop." + KcPathBuffer.class.getName());

    public record TimestampedPath(String path, long ts) {}

    @Value("${kcmaster.path-buffer.ttl-ms:60000}")
    private long ttlMs;

    @Value("${kcmaster.path-buffer.prune-interval-ms:30000}")
    private long pruneIntervalMs;

    private final ConcurrentHashMap<String, TimestampedPath> entries = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kcmaster.path-buffer.prune");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::prune,
                pruneIntervalMs, pruneIntervalMs, TimeUnit.MILLISECONDS);
        log.info("KcPathBuffer started; ttlMs={} pruneIntervalMs={}", ttlMs, pruneIntervalMs);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public void store(String entityId, String path) {
        if (entityId == null || path == null) {
            return;
        }
        entries.put(entityId, new TimestampedPath(path, System.currentTimeMillis()));
        devLog.debug("KcPathBuffer | STORE | entityId={} | path={} | size={}",
                entityId, path, entries.size());
    }

    public String consume(String entityId) {
        String ret = null;
        if (entityId != null) {
            TimestampedPath e = entries.remove(entityId);
            if (e != null) {
                long age = System.currentTimeMillis() - e.ts();
                if (age <= ttlMs) {
                    ret = e.path();
                    devLog.debug("KcPathBuffer | CONSUME | entityId={} | path={} | ageMs={}",
                            entityId, ret, age);
                } else {
                    devLog.debug("KcPathBuffer | CONSUME_EXPIRED | entityId={} | ageMs={} | ttlMs={}",
                            entityId, age, ttlMs);
                }
            }
        }
        return ret;
    }

    public int size() {
        return entries.size();
    }

    void prune() {
        long now = System.currentTimeMillis();
        int before = entries.size();
        Iterator<Map.Entry<String, TimestampedPath>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TimestampedPath> e = it.next();
            if (now - e.getValue().ts() > ttlMs) {
                it.remove();
            }
        }
        int removed = before - entries.size();
        if (removed > 0) {
            devLog.debug("KcPathBuffer | PRUNE | removed={} | size={}", removed, entries.size());
        }
    }
}
