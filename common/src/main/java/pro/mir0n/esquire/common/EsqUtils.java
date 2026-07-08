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
 * 06/16/2026 mir0n  v1.2.8 -- instanceNo() resolves from the host-name trailing ordinal only
 *                   (parsePodNameOrdinal(instanceHost())); POD_INDEX / ESQUIRE_INSTANCE_NO env /
 *                   esquire.instance.no sysprop sources dropped; instanceHost() + setInstanceNoForTests()
 *                   added; firstNonBlank() made public
 * 07/08/2026 mir0n  v1.2.11 -- W3C trace-id settlement. generateCorrelationId() now emits 32 lowercase hex
 *                   from 16 SecureRandom bytes, non-zero (was a UUID string). Added isW3cTraceId(String),
 *                   toW3cTraceId(String) (SHA-256, first 16 bytes -> 32 hex), settleCorrelationId(String)
 *                   (keep-if-W3C / convert / generate), buildTraceparent(String traceId),
 *                   isValidTraceparent(String) and traceIdFromTraceparent(String)
 */
package pro.mir0n.esquire.common;


public class EsqUtils {
	private EsqUtils() {}

    // --- Correlation-id / W3C trace-id settlement (v1.2.11 O2) -----------------------------------
    // The Esq-Correlation-ID is ALWAYS a W3C-shaped trace id: 16 bytes rendered as 32 lowercase hex
    // digits, non-zero. That single id is the traceId a span carries and the correlationId a log line
    // carries, so a trace and its logs cross-link on one value. The gateway settles the edge (generate
    // / validate / convert); every downstream service inherits the settled id unchanged.

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int W3C_TRACE_ID_LEN = 32;

    // Emit a fresh W3C-shaped trace id (16 random bytes -> 32 lowercase hex, non-zero).
    public static String generateCorrelationId() {
        byte[] bytes = new byte[16];
        String ret;
        do {
            RANDOM.nextBytes(bytes);
            ret = toHex(bytes);
        } while (isAllZero(ret));
        return ret;
    }

    // A string is a W3C trace id iff it is 32 lowercase hex digits and not all zero.
    public static boolean isW3cTraceId(String s) {
        boolean ret = false;
        if (s != null && s.length() == W3C_TRACE_ID_LEN) {
            boolean allHex = true;
            boolean allZero = true;
            for (int i = 0; i < W3C_TRACE_ID_LEN; i++) {
                char c = s.charAt(i);
                boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
                if (!hex) {
                    allHex = false;
                    break;
                }
                if (c != '0') {
                    allZero = false;
                }
            }
            ret = allHex && !allZero;
        }
        return ret;
    }

    // Convert any arbitrary value to a W3C-shaped trace id: a stable SHA-256 hash of its UTF-8 bytes,
    // first 16 bytes rendered as 32 hex. Deterministic -- the same input always maps to the same id,
    // so an external correlation id or the BFF's X-Request-ID seeds a reproducible traceId.
    public static String toW3cTraceId(String value) {
        String ret;
        if (value == null) {
            ret = generateCorrelationId();
        } else {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                byte[] first16 = new byte[16];
                System.arraycopy(digest, 0, first16, 0, 16);
                ret = toHex(first16);
                if (isAllZero(ret)) {
                    ret = generateCorrelationId();
                }
            } catch (java.security.NoSuchAlgorithmException e) {
                // SHA-256 is mandated on every JVM -- unreachable; fall back to a fresh id.
                ret = generateCorrelationId();
            }
        }
        return ret;
    }

    // Settle the edge correlation id:
    //   (1) an incoming correlation id -> keep it if already W3C-shaped, else convert it;
    //   (2) else GENERATE a fresh id.
    // The per-request id is NEVER a seed -- the correlation id is its own identity (it, in turn, IS
    // the trace id). Always yields a valid W3C-shaped trace id.
    public static String settleCorrelationId(String incomingCorrelationId) {
        String ret;
        if (incomingCorrelationId != null && !incomingCorrelationId.isBlank()) {
            ret = isW3cTraceId(incomingCorrelationId)
                    ? incomingCorrelationId
                    : toW3cTraceId(incomingCorrelationId);
        } else {
            ret = generateCorrelationId();
        }
        return ret;
    }

    // Build a W3C traceparent that carries the given (W3C-shaped) trace id: version 00, a fresh
    // non-zero span id, sampled flag 01. The gateway stamps this so downstream OTel instrumentation
    // extracts it and every span in the request inherits traceId == the settled correlationId.
    public static String buildTraceparent(String traceId) {
        return "00-" + traceId + "-" + generateSpanId() + "-01";
    }

    // A traceparent is well-formed iff it is 4 hyphen-separated hex fields: 2-hex version, a W3C
    // trace id (32 hex, non-zero), a 16-hex non-zero span id, and a 2-hex flags byte.
    public static boolean isValidTraceparent(String traceparent) {
        boolean ret = false;
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length == 4
                    && parts[0].length() == 2 && isLowerHex(parts[0])
                    && isW3cTraceId(parts[1])
                    && parts[2].length() == 16 && isLowerHex(parts[2]) && !isAllZero(parts[2])
                    && parts[3].length() == 2 && isLowerHex(parts[3])) {
                ret = true;
            }
        }
        return ret;
    }

    // The trace id carried by a well-formed traceparent, else null.
    public static String traceIdFromTraceparent(String traceparent) {
        String ret = null;
        if (isValidTraceparent(traceparent)) {
            ret = traceparent.split("-")[1];
        }
        return ret;
    }

    private static String generateSpanId() {
        byte[] bytes = new byte[8];
        String ret;
        do {
            RANDOM.nextBytes(bytes);
            ret = toHex(bytes);
        } while (isAllZero(ret));
        return ret;
    }

    private static boolean isLowerHex(String s) {
        boolean ret = !s.isEmpty();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                ret = false;
                break;
            }
        }
        return ret;
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    private static boolean isAllZero(String hex) {
        boolean ret = true;
        for (int i = 0; i < hex.length(); i++) {
            if (hex.charAt(i) != '0') {
                ret = false;
                break;
            }
        }
        return ret;
    }
    // --------------------------------------------------------------------------------------------

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
    // and drop it via resetInstanceNoCacheForTests() (both package-private).
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
    static void resetInstanceNoCacheForTests() {
        cachedInstanceNo = null;
    }

    /** Test-only: pin the cached instance number, bypassing host-name resolution. Lets tests drive
     *  the shard digit (and out-of-range guards) without depending on the runner's host name. */
    static void setInstanceNoForTests(int instanceNo) {
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

    public static String firstNonBlank(String... candidates) {
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
