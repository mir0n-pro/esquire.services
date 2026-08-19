/*
 *  Esquire frameworks (tm)
 *  gateWard service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/17/2026 mir0n  created: v1.2.13 T3.2 -- whether a meter is the gate's or the tree cache's, read off the
 *                   meter id: its name, the route it was served on, or the bus it travelled. PROCESS_OWNED is
 *                   checked FIRST and stops the lookup -- reactor-netty tags the edge server with a coarse uri
 *                   that matches a cache route, which would credit the whole edge to bizTree. http.client
 *                   .requests is matched by name for the same reason: the gate's outbound leg carries a
 *                   downstream uri
 */

package pro.mir0n.esquire.gateWard;

import io.micrometer.core.instrument.Meter;
import pro.mir0n.esquire.backend.o11y.IMeterOwner;

/**
 * Whether a meter belongs to the gateway or to the tree cache, in the one process that runs both.
 *
 * The gate's work is its own timing bands, its token relay, its route meters and its breakers. The cache's
 * work is its business meters, the service bands of a locally answered tree route, the repository call that
 * loads it, and the entity bus it listens on. What the two SHARE is the process's: the JVM, the connection
 * pool, and the Netty server, which carries proxied and locally answered traffic alike.
 *
 * The name table is the code half of {@code doc/Esquire.ObservabilityStack.Inventory.csv}: its
 * {@code emitted_by} column is the same statement. A new meter belongs in both.
 */
public class GateWardMeterOwner implements IMeterOwner {

    private static final String GATEWAY = "gateway";
    private static final String BIZTREE = "biztree";

    private static final String TAG_URI = "uri";
    private static final String TAG_BUS_ID = "bus-id";

    // A proxied request has no local handler, so Spring tags it uri=UNKNOWN. It was the gate that served it.
    private static final String URI_PROXIED = "UNKNOWN";

    // The Netty server is the PROCESS's, and it carries proxied and locally answered traffic alike -- but its
    // meters are tagged with a coarse uri, which would otherwise match a cache route and credit the whole edge
    // to bizTree. Named here so the lookup stops before it reaches the route table.
    private static final String[] PROCESS_OWNED = {
            "reactor.netty.",
    };

    private static final String[][] BY_NAME = {
            {"esq.gw.",               GATEWAY},
            {"esq.biz.gw.",           GATEWAY},
            {"spring.cloud.gateway.", GATEWAY},
            {"resilience4j.",         GATEWAY},
            {"http.client.requests",  GATEWAY},
            {"esq.biz.tree.",         BIZTREE},
            {"esq.srv.",              BIZTREE},
            {"esq.svc.node",          BIZTREE},
            {"esq.svc.path",          BIZTREE},
            {"esq.svc.subtree",       BIZTREE},
            {"esq.svc.tree",          BIZTREE},
            {"spring.data.repository.", BIZTREE},
    };

    // The routes the cache answers itself, as http.server.requests tags them.
    private static final String[][] BY_ROUTE = {
            {"/esq",       BIZTREE},
            {"/esq-tree",  BIZTREE},
            {"/esq-path",  BIZTREE},
            {"/esq-enode", BIZTREE},
            {"/esq-sweep", BIZTREE},
            {URI_PROXIED,  GATEWAY},
    };

    private final String entityBusId;

    public GateWardMeterOwner(String entityBusId) {
        this.entityBusId = entityBusId;
    }

    @Override
    public String ownerOf(Meter.Id id) {
        String ret = null;
        if (!isProcessOwned(id.getName())) {
            ret = byPrefix(BY_NAME, id.getName());
            if (ret == null) {
                ret = byValue(BY_ROUTE, id.getTag(TAG_URI));
            }
            if (ret == null) {
                ret = byBus(id.getTag(TAG_BUS_ID));
            }
        }
        return ret;
    }

    private static boolean isProcessOwned(String name) {
        boolean ret = false;
        if (name != null) {
            for (String prefix : PROCESS_OWNED) {
                if (name.startsWith(prefix)) {
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }

    // The entity bus is what the cache listens on -- the gate speaks on no bus at all.
    private String byBus(String busId) {
        String ret = null;
        if (busId != null && busId.equals(entityBusId)) {
            ret = BIZTREE;
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
