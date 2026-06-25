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
 * 06/22/2026 mir0n  transmits()/receives() overridden to true (R&R runs BOTH legs for its role); validate() now
 *                   REQUIRES a complete transport (was optional). import BusNode/BusTransport/Role/XRodParams from
 *                   messaging.catalog.
 * 06/23/2026 mir0n  buildKeepAlive() (CLIENT emits TestRequest, SERVER/BOTH an unsolicited HeartBeat) + onSessionMsg()
 *                   (SERVER echoes a received TestRequest back as a HeartBeat, routing echoed)
 * 06/24/2026 mir0n  buildKeepAlive() javadoc: "an R&R SERVER" (BOTH removed from the role list)
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.catalog.BusNode;
import pro.mir0n.esquire.messaging.catalog.BusTransport;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;

/** The Request/Response x-Rod (two nodes, role-driven): a specialised {@link XRod} for R&R legs. It overrides
 *  the per-leg transport (it refines the base wire with the request or response {@link BusNode}) and the receive
 *  selector; everything else is the base transceiver. */
public class XRodRR extends XRod {

    /** R&R always runs BOTH legs for its role: a CLIENT transmits requests + receives responses; a SERVER
     *  transmits responses + receives requests. (The receive leg still opens only if a worker is set.) */
    @Override
    protected boolean transmits() {
        return true;
    }

    @Override
    protected boolean receives() {
        return true;
    }

    @Override
    public void validate(XRodParams params) {
        BusTransport t = params != null ? params.transport() : null;
        // An R&R leg also needs a real transport -> mandatory (fail fast, not a silent no-op rod).
        require(t != null, "transport", params);
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

    /** Alive protocol: an R&R CLIENT probes its SERVER with a TestRequest on inactivity (its own rod-id rides, so
     *  the SERVER's HeartBeat reply routes back via the RodID selector); an R&R SERVER keeps its
     *  response leg alive with an unsolicited HeartBeat (the base keep-alive). */
    @Override
    protected RodEvent buildKeepAlive() {
        RodEvent ret;
        if (role == Role.CLIENT) {
            ret = RodEvent.testRequest(newCorrelationId(), null);
        } else {
            ret = super.buildKeepAlive();
        }
        return ret;
    }

    /** Alive protocol: an R&R SERVER answers a received TestRequest with a HeartBeat echoing the requester's
     *  routing + correlation (the URS reply path), so the CLIENT observes the round trip. A CLIENT's received
     *  HeartBeat is liveness only (already marked by the session). */
    @Override
    protected void onSessionMsg(RodEvent in) {
        if (role == Role.SERVER && BusConstants.MSG_TYPE_TEST_REQUEST.equals(in.msgType())) {
            transmit(RodEvent.heartbeat(in.correlationId(), in.requestId(), in.rodId()));
        }
    }

    /** R&R: a CLIENT consumes its own responses (filter by rod-id); a SERVER consumes its service's requests
     *  (filter by slot-id) off a possibly-shared request node. */
    @Override
    protected String consumeSelector(Role role, BusIdentity identity) {
        String ret;
        if (role == Role.CLIENT) {
            ret = BusConstants.FIELD_ROD_ID + " = '" + identity.rodId() + "'";
        } else if (role == Role.SERVER) {
            ret = BusConstants.FIELD_SLOT_ID + " = '" + identity.slotId() + "'";
        } else {
            ret = null;
        }
        return ret;
    }
}
