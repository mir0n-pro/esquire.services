/*
 *  Esquire frameworks (tm)
 *  esquire-dataKeep
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the pluggable consumer strategy of the generic keep.
 * 06/18/2026 mir0n  reduced to a pure declaration: a director says ONLY what the keep handles -- the SQL
 *                   resource group + the kind -> statement-key map. The generic keep engine does everything
 *                   else (datasource + pool, the SQL store, the RodEvent->DB writer, the registry, the relay).
 */
package pro.mir0n.esquire.dataKeep.director;

import java.util.Map;

/**
 * A keep director declares ONLY what a keep handles, nothing else: which classpath SQL group its statements
 * live in ({@code META-INF/<group>}) and the {@code kind -> statement-key} map. The generic keep engine reads
 * this and builds the whole pipeline (pool, SQL store, {@code RodEvent}->DB writer, registry, in-process /
 * consumer relay). Audit is the first director; a future one (replication, doc-DB) is just another declaration.
 */
public interface IKeepDirector {

    /** The classpath resource group holding this keep's vendor SQL ({@code META-INF/<group>/<dialect>.xml}). */
    String sqlGroup();

    /** The kinds this keep handles -> the SQL statement key for each (requires the kind dictionary loaded). */
    Map<Integer, String> kinds();
}
