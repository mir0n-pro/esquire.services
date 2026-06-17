/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the Request/Response x-Rod pod -- a specialised XRod for an R&R leg's two nodes
 *                   (request + response). A service is a CLIENT (post requests / receive responses) or a SERVER
 *                   (receive requests / post responses). It overrides legTransport (refine the base wire with the
 *                   request or response NODE by role, overlaid via XRodParams.overlayGroups, provider / endpoint
 *                   excepted) and consumeSelector (CLIENT filters by rod-id, SERVER by slot-id); the rest is base XRod.
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.BusTransport;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;

import java.util.LinkedHashMap;
import java.util.Map;

/** The Request/Response x-Rod (two nodes, role-driven): a specialised {@link XRod} for R&R legs. It overrides
 *  the per-leg transport (it refines the base wire with the request or response NODE) and the receive selector;
 *  everything else is the base transceiver. */
public class XRodRR extends XRod {

    private static final String TP    = "transport.";
    private static final String NODE = "transport.node.";

    /** R&R: refine the base transport with the request or response NODE for this leg's direction. A node owns the
     *  {@code destination} and may override any transport scalar / params group EXCEPT provider / endpoint (the base
     *  owns the wire). Direction: produce CLIENT / consume SERVER -> request; produce SERVER / consume CLIENT ->
     *  response. A non-R&R role or a leg with no node falls back to the base single transport. */
    @Override
    protected BusTransport legTransport(boolean produce, Role role) {
        BusTransport ret;
        Map<String, Object> raw = params != null ? params.raw() : null;
        boolean wantRequest = produce == (role == Role.CLIENT);
        Object nodeId = raw == null || (role != Role.CLIENT && role != Role.SERVER)
                ? null : raw.get(wantRequest ? "transport.request-node" : "transport.response-node");
        if (nodeId == null) {
            ret = super.legTransport(produce, role);   // not a two-node R&R leg -> the base single transport
        } else {
            String nodePrefix = nodePrefix(raw, nodeId.toString());
            // base transport-relative (strip "transport."), minus the node machinery (it is XRodRR-only wiring).
            Map<String, Object> baseRel = new LinkedHashMap<>();
            raw.forEach((k, v) -> {
                if (k.startsWith(TP)) {
                    String rel = k.substring(TP.length());
                    if (!rel.equals("request-node") && !rel.equals("response-node") && !rel.startsWith("node")) {
                        baseRel.put(rel, v);
                    }
                }
            });
            // node transport-relative (strip the node prefix), minus id / the base-owned wire (provider / endpoint).
            Map<String, Object> nodeRel = new LinkedHashMap<>();
            if (nodePrefix != null) {
                raw.forEach((k, v) -> {
                    if (k.startsWith(nodePrefix)) {
                        String rel = k.substring(nodePrefix.length());
                        if (!rel.equals("node-id") && !rel.equals("provider") && !rel.equals("endpoint")) {
                            nodeRel.put(rel, v);
                        }
                    }
                });
            }
            // node overlays the base per group (destination / topic / params), then re-bind via transport().
            Map<String, Object> merged = XRodParams.overlayGroups(baseRel, nodeRel);
            Map<String, Object> effRaw = new LinkedHashMap<>();
            merged.forEach((k, v) -> effRaw.put(TP + k, v));
            ret = new XRodParams(null, null, effRaw).transport();
        }
        return ret;
    }

    /** R&R: a CLIENT consumes its own responses (filter by rod-id); a SERVER consumes its service's requests
     *  (filter by slot-id) off a possibly-shared request node. */
    @Override
    protected String consumeSelector(Role role, BusIdentity identity) {
        String ret;
        if (role == Role.CLIENT) {
            ret = EsqMsgConstants.FIELD_ROD_ID + " = '" + identity.rodId() + "'";
        } else if (role == Role.SERVER) {
            ret = EsqMsgConstants.FIELD_SLOT_ID + " = '" + identity.slotId() + "'";
        } else {
            ret = null;
        }
        return ret;
    }

    /** The flattened-key prefix of the node whose {@code node-id} == {@code nodeId} (e.g.
     *  {@code transport.node.0.}); null if no such node. A YAML {@code node} list binds (under a Map) as
     *  numeric-keyed entries, so a node's id key is {@code transport.node.<idx>.node-id} -- one index segment
     *  between the prefix and {@code .node-id}. */
    private static String nodePrefix(Map<String, Object> raw, String nodeId) {
        String ret = null;
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String k = e.getKey();
            if (k.startsWith(NODE) && k.endsWith(".node-id") && e.getValue() != null
                    && e.getValue().toString().equals(nodeId)) {
                String idx = k.substring(NODE.length(), k.length() - ".node-id".length());
                if (!idx.isEmpty() && idx.indexOf('.') < 0) {       // the index segment only (not a nested .node-id)
                    ret = k.substring(0, k.length() - "node-id".length());   // drop trailing "node-id"; keep the "."
                    break;
                }
            }
        }
        return ret;
    }
}
