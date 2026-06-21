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
 * 06/17/2026 mir0n  legTransport() rewritten to a typed node model: select the request / response BusNode by id
 *                   and refine the base wire via BusTransport.refinedWith(); the flattened-key surgery +
 *                   nodePrefix() removed; validate() added (provider / endpoint + nodes or a base destination)
 * 06/21/2026 mir0n  legTransport() doc: a node owns destination / params (topic dropped from the node model)
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.BusNode;
import pro.mir0n.esquire.messaging.BusTransport;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;

/** The Request/Response x-Rod (two nodes, role-driven): a specialised {@link XRod} for R&R legs. It overrides
 *  the per-leg transport (it refines the base wire with the request or response {@link BusNode}) and the receive
 *  selector; everything else is the base transceiver. */
public class XRodRR extends XRod {

    @Override
    public void validate(XRodParams params) {
        BusTransport t = params != null ? params.transport() : null;
        if (t != null) {
            require(t.provider() != null, "transport.provider", params);
            require(t.endpoint() != null, "transport.endpoint", params);
            Object reqId = params.raw() != null ? params.raw().get("transport.request-node") : null;
            if (reqId != null) {   // a two-node R&R leg: both nodes must resolve with a destination
                Object respId = params.raw().get("transport.response-node");
                require(respId != null, "transport.response-node", params);
                require(nodeHasDestination(params, reqId),  "transport node '" + reqId + "' destination", params);
                require(nodeHasDestination(params, respId), "transport node '" + respId + "' destination", params);
            } else {   // single-node XRodRR -> legTransport falls back to the base destination
                require(t.destination() != null, "transport.destination", params);
            }
        }
    }

    private static boolean nodeHasDestination(XRodParams params, Object nodeId) {
        boolean ret = false;
        if (nodeId != null) {
            for (BusNode node : params.nodes()) {
                if (nodeId.toString().equals(node.nodeId()) && node.destination() != null) {
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }

    /** R&R: refine the base transport with the request or response NODE for this leg's direction. Direction:
     *  produce CLIENT / consume SERVER -> the request node; produce SERVER / consume CLIENT -> the response node.
     *  A non-R&R role, or a leg with no such node, falls back to the base single transport. The node owns its
     *  {@code destination} / {@code params}; the base owns {@code provider} / {@code endpoint}
     *  (see {@link BusTransport#refinedWith}). */
    @Override
    protected BusTransport legTransport(boolean produce, Role role) {
        BusTransport ret = super.legTransport(produce, role);   // the base single transport (the wire)
        if (params != null && (role == Role.CLIENT || role == Role.SERVER)) {
            boolean wantRequest = produce == (role == Role.CLIENT);
            Object nodeId = params.raw() != null
                    ? params.raw().get(wantRequest ? "transport.request-node" : "transport.response-node") : null;
            if (nodeId != null) {
                BusNode node = nodeById(nodeId.toString());
                if (node != null) {
                    ret = ret.refinedWith(node);
                }
            }
        }
        return ret;
    }

    /** The declared {@link BusNode} whose {@code node-id} matches, or null. */
    private BusNode nodeById(String nodeId) {
        BusNode ret = null;
        for (BusNode node : params.nodes()) {
            if (nodeId.equals(node.nodeId())) {
                ret = node;
                break;
            }
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
}
