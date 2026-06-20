/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/14/2026 mir0n  created: the transport (x-Rod) instance identity -- bus-id + service-id + rod-id, the
 *                   three the transport config already carries. NOT a message type: the message type is a
 *                   per-message thing the producer sets at publish, never a transport / bus property.
 * 06/17/2026 mir0n  expandTokens(Map) added: resolves ${rod-id} / ${bus-id} / ${slot-id} in vendor params
 *                   against this identity (applied once in the TransportSettings constructor)
 */
package pro.mir0n.esquire.messaging.transport;

import java.util.LinkedHashMap;
import java.util.Map;

/** The transport (x-Rod) instance identity: bus-id / slot-id / rod-id (which bus, which slot, which instance). */
public record BusIdentity(String busId, String slotId, String rodId) {

    /** Expand the runtime-identity tokens in each param value against THIS identity: {@code ${rod-id}} /
     *  {@code ${bus-id}} / {@code ${slot-id}} -> the identity's values (a null field -> empty). Lets a static
     *  config value bind to the leg's resolved identity (e.g. {@code jms.clientID: ${rod-id}}); a value with no
     *  token passes through verbatim. Returns a copy. */
    public Map<String, String> expandTokens(Map<String, String> params) {
        Map<String, String> ret = params;
        if (params != null && !params.isEmpty()) {
            Map<String, String> out = new LinkedHashMap<>();
            params.forEach((k, v) -> out.put(k, expand(v)));
            ret = out;
        }
        return ret;
    }

    private String expand(String value) {
        String ret = value;
        if (value != null && value.indexOf("${") >= 0) {
            ret = value.replace("${rod-id}",  rodId  != null ? rodId  : "")
                       .replace("${bus-id}",  busId  != null ? busId  : "")
                       .replace("${slot-id}", slotId != null ? slotId : "");
        }
        return ret;
    }
}
