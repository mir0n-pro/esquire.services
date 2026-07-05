/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/29/2026 mir0n  created: result of the R6 slow-query test hook (server-measured elapsed + timedOut flag).
 */
package pro.mir0n.esquire.enyMan.testhook;

/** Outcome of a {@link SlowQueryTestService} run: server-measured elapsed time and whether the cap cancelled it. */
public record SlowQueryResult(String mode, int requestedSeconds, long elapsedMs, boolean timedOut, String error) {
}
