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
 * 07/09/2026 mir0n  v1.2.11 -- instanceNo() / instanceHost() and the private parsePodNameOrdinal() moved to
 *                   pro.mir0n.utils.HostId (mir0n-utils); instanceNo() / instanceHost() and the test seams now
 *                   delegate to it
 */
package pro.mir0n.esquire.common;

import pro.mir0n.utils.HostId;


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
    // The rule itself lives ONE layer down, in pro.mir0n.utils.HostId: the messaging bus builds its
    // default rod-id from the same ordinal and must not depend on anything Esquire. These stay as the
    // Esquire-side name for it.
    public static int instanceNo() {
        return HostId.instanceNo();
    }

    /** Test-only: drop the cached instance number so a subsequent {@link #instanceNo()} call
     *  re-resolves from the host name. */
    static void resetInstanceNoCacheForTests() {
        HostId.resetInstanceNoCacheForTests();
    }

    /** Test-only: pin the cached instance number, bypassing host-name resolution. Lets tests drive
     *  the shard digit (and out-of-range guards) without depending on the runner's host name. */
    static void setInstanceNoForTests(int instanceNo) {
        HostId.setInstanceNoForTests(instanceNo);
    }

    /** This container/pod's host identity -- the pod name in k8s, the container id in Docker. */
    public static String instanceHost() {
        return HostId.instanceHost();
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


}
