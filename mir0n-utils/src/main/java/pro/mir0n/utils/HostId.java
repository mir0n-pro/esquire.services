/*
 *  mir0n utils
 *  base library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/09/2026 mir0n  created: this instance's host identity -- instanceHost() + the lazy-cached instanceNo(),
 *                   moved down from esquire common.EsqUtils (which now delegates) so the messaging bus can
 *                   build its default rod-id without depending on anything Esquire.
 */
package pro.mir0n.utils;

/**
 * This container/pod's host identity, and the instance number derived from it. ONE source for both: the host
 * name. Callers above (the entity-id shard digit, the default rod-id {@code <app>.<instanceNo>}) read it here.
 */
public final class HostId {

    private HostId() {
    }

    // This service instance's number within its replica set -- the bottom-digit shard key for
    // entity-id minting AND the per-instance token of the default rod-id (<app>.<instanceNo>).
    //
    // ONE source: the trailing ordinal of this instance's host name (instanceHost() -- HOSTNAME /
    // POD_NAME, always present in Docker and k8s). The deployment carries the sequence IN the name
    // whenever it runs for resilience: a StatefulSet pod name ("enyman-0" -> 0), or a
    // "hostname: <app>-N" on the Docker container. No ordinal in the name (a single instance / a
    // plain Deployment) -> 0.
    //
    // Lazy-cached: the number is fixed per JVM lifetime (the host name is set at startup and doesn't
    // change). EntityIdGenerator and the move-queue worker call this on every mint / reconcile, so
    // the resolution is amortised to a single walk. Tests inject a value via setInstanceNoForTests()
    // and drop it via resetInstanceNoCacheForTests().
    private static volatile Integer cachedInstanceNo;

    public static int instanceNo() {
        Integer cached = cachedInstanceNo;
        if (cached != null) {
            return cached;
        }
        int ret = 0;
        String ordinal = parsePodNameOrdinal(instanceHost());
        if (ordinal != null) {
            try {
                ret = Integer.parseInt(ordinal);
            } catch (NumberFormatException ignored) {
                // ret stays 0 -- parsePodNameOrdinal returns only all-digit strings, so this guards
                // a pathologically long tail (overflow) alone
            }
        }
        cachedInstanceNo = ret;
        return ret;
    }

    /** Test-only: drop the cached instance number so a subsequent {@link #instanceNo()} call
     *  re-resolves from the host name. */
    public static void resetInstanceNoCacheForTests() {
        cachedInstanceNo = null;
    }

    /** Test-only: pin the cached instance number, bypassing host-name resolution. Lets tests drive
     *  the shard digit (and out-of-range guards) without depending on the runner's host name. */
    public static void setInstanceNoForTests(int instanceNo) {
        cachedInstanceNo = instanceNo;
    }

    // This container/pod's host identity -- THE single instance-identity source. The pod name in
    // k8s (the kubelet sets the pod hostname to metadata.name) or the container id in Docker.
    // instanceNo() parses its trailing ordinal for the shard digit; callers needing the full token
    // (e.g. diagnostics) use it directly. Resolved in priority order:
    //   1. HOSTNAME env  -- set by every container runtime (k8s pod name / Docker container id)
    //   2. POD_NAME env  -- the downward API metadata.name, when HOSTNAME is suppressed
    //   3. the resolved local hostname  -- the bare-metal / local-dev fallback
    // Returns null only when no hostname is resolvable at all; instanceNo() then defaults to 0.
    public static String instanceHost() {
        String ret = firstNonBlank(
            System.getenv("HOSTNAME"),
            System.getenv("POD_NAME")
        );
        if (ret == null) {
            try {
                ret = java.net.InetAddress.getLocalHost().getHostName();
            } catch (java.net.UnknownHostException ignored) {
                // ret stays null -- caller defaults (e.g. to instanceNo())
            }
        }
        return ret;
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
