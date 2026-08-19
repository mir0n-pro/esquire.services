/*
 *  Esquire frameworks (tm)
 *  Mesnie service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/16/2026 mir0n  created: v1.2.13 T3.1 -- which of the three the household's meters belong to, read off the
 *                   meter id: its name, the route it was served on, or the bus it travelled
 */

package pro.mir0n.esquire.mesnie;

import io.micrometer.core.instrument.Meter;
import pro.mir0n.esquire.backend.o11y.IMeterOwner;

/**
 * Which of enyMan, keySmith and kcMaster produced a meter, in the one process that runs all three.
 *
 * Three ways of knowing, in order: the meter NAME (a business or service meter is emitted by exactly one
 * service), the ROUTE it was served on, and the BUS it travelled. Anything else -- the JVM, the pool, the
 * audit keep, the permission checks the three share -- is the household's own and answers null, which
 * leaves it tagged with the process.
 *
 * The name table is the code half of {@code doc/Esquire.ObservabilityStack.Inventory.csv}: its
 * {@code emitted_by} column is the same statement. A new meter belongs in both.
 */
public class MesnieMeterOwner implements IMeterOwner {

    private static final String ENYMAN = "enyman";
    private static final String KEYSMITH = "keysmith";
    private static final String KCMASTER = "kcmaster";

    private static final String TAG_URI = "uri";
    private static final String TAG_ROUTE = "route";
    private static final String TAG_BUS_ID = "bus-id";

    private static final String[][] BY_NAME = {
            {"esq.biz.entity.", ENYMAN},
            {"esq.biz.dict.",   ENYMAN},
            {"esq.biz.move.",   ENYMAN},
            {"esq.svc.create",  ENYMAN},
            {"esq.svc.read",    ENYMAN},
            {"esq.svc.save",    ENYMAN},
            {"esq.svc.delete",  ENYMAN},
            {"esq.svc.move",    ENYMAN},
            {"esq.svc.tree",    ENYMAN},
            {"esq.biz.key.",    KEYSMITH},
            {"esq.svc.key.",    KEYSMITH},
            {"esq.biz.kc.",     KCMASTER},
    };

    // The matched route pattern, which is the value of BOTH the http.server.requests "uri" tag and the
    // esq.srv.outer / esq.srv.inner "route" tag.
    private static final String[][] BY_ROUTE = {
            {"/esq-dict",             ENYMAN},
            {"/esq-cmd",              ENYMAN},
            {"/esq-cmd-save",         ENYMAN},
            {"/esq-cmd-new",          ENYMAN},
            {"/esq-cmd-del",          ENYMAN},
            {"/esq-cmd-tree",         ENYMAN},
            {"/esq-move",             ENYMAN},
            {"/esq-kinds",            ENYMAN},
            {"/test/slow-query",      ENYMAN},
            {"/test/slow-query-optout", ENYMAN},
            {"/esq-key",              KEYSMITH},
            {"/esq-key-save",         KEYSMITH},
    };

    private final String entityBusId;

    public MesnieMeterOwner(String entityBusId) {
        this.entityBusId = entityBusId;
    }

    @Override
    public String ownerOf(Meter.Id id) {
        String ret = byPrefix(BY_NAME, id.getName());
        if (ret == null) {
            ret = byValue(BY_ROUTE, id.getTag(TAG_URI));
        }
        if (ret == null) {
            ret = byValue(BY_ROUTE, id.getTag(TAG_ROUTE));
        }
        if (ret == null) {
            ret = byBus(id.getTag(TAG_BUS_ID));
        }
        return ret;
    }

    // The entity bus is enyMan's leg -- it publishes the broadcasts and listens for its peers'. The audit bus
    // is the household's one keep, so it stays with the process.
    private String byBus(String busId) {
        String ret = null;
        if (busId != null && busId.equals(entityBusId)) {
            ret = ENYMAN;
        }
        return ret;
    }

    private static String byPrefix(String[][] table, String name) {
        String ret = null;
        if (name != null) {
            for (String[] row : table) {
                if (name.startsWith(row[0])) {
                    ret = row[1];
                    break;
                }
            }
        }
        return ret;
    }

    private static String byValue(String[][] table, String value) {
        String ret = null;
        if (value != null) {
            for (String[] row : table) {
                if (value.equals(row[0])) {
                    ret = row[1];
                    break;
                }
            }
        }
        return ret;
    }
}
