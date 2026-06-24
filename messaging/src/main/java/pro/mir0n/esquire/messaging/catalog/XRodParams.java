/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: record {busId, slotId, raw} -- a bound x-Rod leg = its flattened config node (raw)
 *                   plus the leg identity the frontend folds in (withBus). Knobs are read FROM raw by name (the
 *                   scalars registered once in SCALARS); transport() binds the wire group; sub(key, Class) binds
 *                   a pod-owned sub-block; merge / overlayGroups overlay an override per top-level GROUP (a group
 *                   the override sets wins in full). Impl-agnostic -- any pod plus its own settings work unchanged.
 * 06/17/2026 mir0n  transport() carries transport.params.* VERBATIM (token expansion removed from here); nodes()
 *                   parses transport.node[*] into a typed List<BusNode>; expandIdentityTokens() removed
 * 06/19/2026 mir0n  nodes() reads the plural key transport.nodes[*] (was transport.node[*])
 * 06/21/2026 mir0n  transport() / bindNode() build the wire without topic (queue-vs-topic moved to the
 *                   pubSubDomain vendor param)
 * 06/22/2026 mir0n  moved to messaging.catalog (was messaging)
 * 06/23/2026 mir0n  alive-protocol knobs in SCALARS + getters: heartbeat-interval (10s) / alive-timeout (3x) /
 *                   alive-fail-fast; boolOr with a default
 */
package pro.mir0n.esquire.messaging.catalog;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A bound x-Rod leg: its flattened config node ({@code raw}) plus the leg identity (bus-id / slot-id) the
 * frontend folds in. Knobs are read FROM {@code raw} by name -- the scalar knobs are registered in
 * {@link #SCALARS}, {@code transport} is the wire group, and each x-rod binds its OWN sub-block via {@link #sub}
 * (the x-rod owns the name; {@code datasource} / {@code info} etc. are NOT known here). Stays impl-agnostic, so any x-rod
 * plus its own settings works unchanged.
 */
public record XRodParams(String busId, String slotId, Map<String, Object> raw) {

    /** The scalar knob keys -- the ONE place that registers a framework scalar (add one here + a getter). The
     *  groups ({@code transport} = the wire, and an x-rod's own sub-blocks) are NOT scalars: they bind as a whole. */
    public static final List<String> SCALARS = List.of(
            "rod-id", "rod-class", "pool-size", "feed-capacity", "virtual-threads", "publisher-pool-size", "concurrency",
            "heartbeat-interval", "alive-timeout", "alive-fail-fast");

    /** Build from the raw x-rod node: flatten it (so nested transport / sub-blocks read by dotted key) and keep
     *  the flat map. bus-id / slot-id are NOT in the node -- the frontend folds them in via {@link #withBus}. */
    public static XRodParams from(Map<String, Object> rawNode) {
        Map<String, Object> flat = new LinkedHashMap<>();
        flatten(null, rawNode != null ? rawNode : Map.of(), flat);
        return new XRodParams(null, null, flat);
    }

    /** Fold in the leg identity the frontend resolved: bus-id / slot-id from the ref, and an unset/blank
     *  rod-id defaulted to {@code rodIdDefault} (the per-instance id {@code <app>.<instanceNo>}). Returns a copy. */
    public XRodParams withBus(String busId, String slotId, String rodIdDefault) {
        Map<String, Object> r = new LinkedHashMap<>(raw != null ? raw : Map.of());
        Object rid = r.get("rod-id");
        if ((rid == null || rid.toString().isBlank()) && rodIdDefault != null) {
            r.put("rod-id", rodIdDefault);
        }
        return new XRodParams(busId, slotId, r);
    }

    /** Overlay {@code override} onto this BASE per top-level GROUP: any group the override sets (a scalar like
     *  {@code pool-size}, the {@code transport} wire, or an x-rod sub-block like {@code datasource}) replaces the base's
     *  WHOLE group -- the service's group wins in full (provide it whole; no field-merge into a group). It never
     *  names a group, so it works for any x-rod's settings. bus-id / slot-id are not in raw -> never merged. */
    public XRodParams merge(XRodParams override) {
        XRodParams ret;
        if (override == null || override.raw == null) {
            ret = this;
        } else if (raw == null) {
            ret = new XRodParams(busId, slotId, override.raw);
        } else {
            ret = new XRodParams(busId, slotId, overlayGroups(raw, override.raw));
        }
        return ret;
    }

    /** Overlay {@code override}'s flat keys onto {@code base} per top-level GROUP: any group the override names
     *  replaces the base's WHOLE group. Shared by the leg-level merge AND XRodRR's per-node transport merge
     *  (called on transport-relative keys, where the groups are destination / params / ...). */
    public static Map<String, Object> overlayGroups(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        for (String group : groups(override)) {
            merged.keySet().removeIf(k -> k.equals(group) || k.startsWith(group + "."));
        }
        merged.putAll(override);
        return merged;
    }

    /** The distinct top-level groups in a flattened node: {@code transport.endpoint} -> {@code transport}; a
     *  scalar key (no dot) is its own group. */
    private static Set<String> groups(Map<String, Object> flat) {
        Set<String> ret = new LinkedHashSet<>();
        for (String k : flat.keySet()) {
            int dot = k.indexOf('.');
            ret.add(dot < 0 ? k : k.substring(0, dot));
        }
        return ret;
    }

    /** Bind an x-rod-owned named sub-block (the x-rod passes its OWN key, e.g. "datasource" / "info") into the x-rod's
     *  params record; null if absent. */
    public <T> T sub(String key, Class<T> type) {
        T ret;
        if (raw == null || raw.isEmpty()) {
            ret = null;
        } else {
            ret = new Binder(new MapConfigurationPropertySource(raw)).bind(key, Bindable.of(type)).orElse(null);
        }
        return ret;
    }

    /** The wire group bound into {@link BusTransport}; null if the leg has no transport. The {@code params} map is
     *  built straight from raw ({@code transport.params.*} with the prefix stripped) so ANY vendor key -- including
     *  dotted ones like {@code jms.useAsyncSend} / {@code transport.connectTimeout} -- survives VERBATIM (Spring's
     *  own Map binding is unreliable for dotted keys). A param value may reference the leg's runtime identity with
     *  the tokens {@code ${rod-id}} / {@code ${bus-id}} / {@code ${slot-id}}; those are left as-is HERE and resolved
     *  later against the leg {@link pro.mir0n.esquire.messaging.transport.BusIdentity} when the transport settings
     *  are built ({@code BusIdentity.expandTokens}) -- so the resolution sees the same identity the driver gets,
     *  uniformly for a single-node leg and an R&R node. */
    public BusTransport transport() {
        BusTransport ret;
        if (raw == null || raw.isEmpty()) {
            ret = null;
        } else {
            BusTransport bound = new Binder(new MapConfigurationPropertySource(raw))
                    .bind("transport", Bindable.of(BusTransport.class)).orElse(null);
            if (bound == null) {
                ret = null;
            } else {
                Map<String, String> params = new LinkedHashMap<>();
                String prefix = "transport.params.";
                raw.forEach((k, v) -> {
                    if (k.startsWith(prefix) && v != null) {
                        params.put(k.substring(prefix.length()), v.toString());
                    }
                });
                ret = new BusTransport(bound.provider(), bound.endpoint(), bound.destination(),
                        params.isEmpty() ? null : params);
            }
        }
        return ret;
    }

    /** The R&R network nodes declared under {@code transport.nodes[*]}, as a typed list (each: {@code node-id}
     *  plus the wire fields a node may own). Empty if the leg declares none. The ONE place the flattened
     *  {@code transport.nodes.<idx>.*} keys are read -- an x-rod (XRodRR) then selects a node by id. */
    public List<BusNode> nodes() {
        List<BusNode> ret = new ArrayList<>();
        if (raw != null) {
            String prefix = "transport.nodes.";
            Map<String, Map<String, Object>> byIndex = new LinkedHashMap<>();
            raw.forEach((k, v) -> {
                if (k.startsWith(prefix)) {
                    String rest = k.substring(prefix.length());   // "<idx>.<field...>"
                    int dot = rest.indexOf('.');
                    if (dot > 0) {
                        byIndex.computeIfAbsent(rest.substring(0, dot), x -> new LinkedHashMap<>())
                               .put(rest.substring(dot + 1), v);
                    }
                }
            });
            byIndex.values().forEach(node -> ret.add(bindNode(node)));
        }
        return ret;
    }

    /** Build one {@link BusNode} from its node-relative keys ({@code node-id} / {@code destination} /
     *  {@code params.*}); {@code provider} / {@code endpoint} are not node-owned, so ignored. */
    private static BusNode bindNode(Map<String, Object> node) {
        Map<String, String> params = new LinkedHashMap<>();
        String pp = "params.";
        node.forEach((k, v) -> {
            if (k.startsWith(pp) && v != null) {
                params.put(k.substring(pp.length()), v.toString());
            }
        });
        Object id    = node.get("node-id");
        Object dest  = node.get("destination");
        return new BusNode(id != null ? id.toString() : null,
                dest != null ? dest.toString() : null,
                params.isEmpty() ? null : params);
    }

    public String  rodId()                        { return str("rod-id"); }
    public String  rodClass()                     { return str("rod-class"); }
    public String  rodClassOr(String def)         { String v = str("rod-class"); return v != null ? v : def; }
    public int     poolSizeOr(int def)            { return intOr("pool-size", def); }
    public int     feedCapacityOr(int def)        { return intOr("feed-capacity", def); }
    public boolean virtualThreadsOrFalse()        { return boolOr("virtual-threads"); }
    public int     publisherPoolSizeOr(int def)   { return intOr("publisher-pool-size", def); }
    public int     concurrencyOr(int def)         { return intOr("concurrency", def); }

    // --- alive-protocol (x-rod session) knobs (seconds; on the x-rod, kept in sync across a slot by the operator) ---
    public int     heartbeatIntervalSecOr(int def){ return intOr("heartbeat-interval", def); }
    public int     aliveTimeoutSecOr(int def)     { return intOr("alive-timeout", def); }
    public boolean aliveFailFastOr(boolean def)   { return boolOr("alive-fail-fast", def); }

    private String str(String key) {
        Object v = raw != null ? raw.get(key) : null;
        return v != null ? v.toString() : null;
    }

    private int intOr(String key, int def) {
        Object v = raw != null ? raw.get(key) : null;
        int ret;
        if (v instanceof Number n) {
            ret = n.intValue();
        } else if (v != null && !v.toString().isBlank()) {
            ret = Integer.parseInt(v.toString().trim());
        } else {
            ret = def;
        }
        return ret;
    }

    private boolean boolOr(String key) {
        Object v = raw != null ? raw.get(key) : null;
        return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(v.toString().trim());
    }

    private boolean boolOr(String key, boolean def) {
        Object v = raw != null ? raw.get(key) : null;
        boolean ret;
        if (v instanceof Boolean b) {
            ret = b;
        } else if (v != null && !v.toString().isBlank()) {
            ret = Boolean.parseBoolean(v.toString().trim());
        } else {
            ret = def;
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> in, Map<String, Object> out) {
        for (Map.Entry<String, Object> e : in.entrySet()) {
            String key = prefix == null ? e.getKey() : prefix + "." + e.getKey();
            Object value = e.getValue();
            if (value instanceof Map<?, ?> m) {
                flatten(key, (Map<String, Object>) m, out);
            } else {
                out.put(key, value);
            }
        }
    }
}
