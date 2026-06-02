/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/26/2026 mir0n  generateEntityId(): epoch-based long id -- (ms since esquireEpoch) * 1000 + random sub-ms offset
 * 06/01/2026 mir0n  generateEntityId() removed -- moved to enyMan.service.EntityIdGenerator (v1.2.6
 *                   instanceNo() added: this service instance's number
 * 06/02/2026 mir0n  instanceNo() lazy-caches its result in a volatile field (resolved once per
 *                   JVM lifetime); added package-private resetInstanceNoCacheForTests()
 */
package pro.mir0n.esquire.common;


public class EsqUtils {
	private EsqUtils() {}

    public static String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString();
    }

// This service instance's number within its replica set. v1.2.6 weaves
    //
    // Sources, in priority order:
    //   1. ESQUIRE_INSTANCE_NO env  -- explicit override, set anywhere
    //   2. POD_INDEX env            -- k8s 1.28+ downward API
    //                                  (metadata.labels['apps.kubernetes.io/pod-index'])
    //   3. POD_NAME env             -- downward API metadata.name, parse the
    //                                  trailing "-N" StatefulSet ordinal
    //   4. esquire.instance.no sysprop
    //   5. default 0                -- single unsharded instance / local dev
    //
    // POD_INDEX and POD_NAME both come from StatefulSet pods via the
    // downward API. Both are supported so the same chart works on older
    // k8s without the pod-index label.
    //
    // Lazy-cached: the instance number is fixed per JVM lifetime (env / pod
    // label / sysprop are set at startup and don't change). EntityIdGenerator
    // and the move-queue worker call this on every mint / reconcile, so the
    // env/sysprop walk is amortised to a single resolution. Tests can null
    // the cache via resetInstanceNoCacheForTests() (package-private).
    private static volatile Integer cachedInstanceNo;

    public static int instanceNo() {
        Integer cached = cachedInstanceNo;
        if (cached != null) {
            return cached;
        }
        int ret = 0;
        String raw = firstNonBlank(
            System.getenv("ESQUIRE_INSTANCE_NO"),
            System.getenv("POD_INDEX"),
            parsePodNameOrdinal(System.getenv("POD_NAME")),
            System.getProperty("esquire.instance.no")
        );
        if (raw != null) {
            try {
                ret = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                // ret stays 0 -- treat unparseable as "not configured"
            }
        }
        cachedInstanceNo = ret;
        return ret;
    }

    /** Test-only: drop the cached instance number so a subsequent {@link #instanceNo()}
     *  call re-resolves from env / sysprop. Lets tests vary the sysprop between cases. */
    static void resetInstanceNoCacheForTests() {
        cachedInstanceNo = null;
    }

    private static String firstNonBlank(String... candidates) {
        String ret = null;
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                ret = c;
                break;
            }
        }
        return ret;
    }

    // StatefulSet pod names are "<set>-<ordinal>" (e.g. "enyman-0").
    // Returns the trailing integer as a string, or null if the input
    // doesn't match the pattern.
    private static String parsePodNameOrdinal(String podName) {
        String ret = null;
        if (podName != null) {
            int dash = podName.lastIndexOf('-');
            if (dash >= 0 && dash < podName.length() - 1) {
                String tail = podName.substring(dash + 1);
                if (!tail.isEmpty() && tail.chars().allMatch(Character::isDigit)) {
                    ret = tail;
                }
            }
        }
        return ret;
    }

}
