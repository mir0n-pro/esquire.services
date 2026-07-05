/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/29/2026 mir0n  created: the R6 request-path query-timeout opt-out helper. Long ops (the move transaction,
 *                   the bizTree full-tree cache load) must never inherit the global request-path cap; they run
 *                   with a "no practical limit" timeout instead. Shared here so the sentinel + the resolve rule
 *                   are defined ONCE and unit-tested (the sentinel must survive the JDBC seconds->millis int
 *                   conversion -- Integer.MAX_VALUE overflows it and the driver rejects the negative result).
 */
package pro.mir0n.esquire.common;

/** Shared resolution for the R6 query-timeout opt-out (move / bizTree cache load). */
public final class QueryTimeouts {

    private QueryTimeouts() {}

    /**
     * The "no practical limit" transaction/query timeout (seconds) a long op opts out with, so the request-path
     * cap never cuts it. It is large enough that a move / cache load is never bounded in practice (~11.5 days),
     * but small enough that the JDBC driver's {@code seconds * 1000} millisecond conversion stays within int
     * range: {@code Integer.MAX_VALUE} seconds overflows that product (it wraps negative and pgjdbc rejects it).
     */
    public static final int NO_PRACTICAL_LIMIT_SECONDS = 1_000_000;

    /**
     * Resolve the effective opt-out timeout: a positive configured value caps the long op (an operator-chosen
     * safety ceiling); 0 or negative (the default) leaves it uncapped via {@link #NO_PRACTICAL_LIMIT_SECONDS} so
     * it never inherits the global request-path default.
     */
    public static int resolveOptOut(int configuredSeconds) {
        return configuredSeconds > 0 ? configuredSeconds : NO_PRACTICAL_LIMIT_SECONDS;
    }
}
