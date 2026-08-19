/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/16/2026 mir0n  created: v1.2.13 T3.1 -- a composed service names the Esquire service behind each
 *                   meter, so enyMan, keySmith and kcMaster keep their metric identity inside one process
 */

package pro.mir0n.esquire.backend.o11y;

import io.micrometer.core.instrument.Meter;

/**
 * Which Esquire service a meter belongs to, in a process that runs more than one of them.
 *
 * A composed service (Mesnie, gateWard) contributes ONE implementation; a classic service contributes
 * none, and then every meter falls back to the process name. Answering null means NOT ATTRIBUTABLE --
 * the meter belongs to the process itself, which is the honest answer for the JVM, the connection pool
 * and everything else there is only one of.
 *
 * Called ONCE per meter, when the meter is registered. The answer must therefore depend only on the id,
 * never on the calling thread: a meter's tags are fixed at creation, so a per-request value would freeze
 * whichever service happened to touch it first.
 */
public interface IMeterOwner {

    String ownerOf(Meter.Id id);
}
