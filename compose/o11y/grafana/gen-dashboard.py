#!/usr/bin/env python3
# Esquire frameworks (tm) -- Grafana dashboard generator (v1.2.11 observability).
#
# Single source of truth for the "Esquire Services" Grafana dashboard. Emits the SAME dashboard JSON to both
# deploy targets so they never drift:
#   * docker : compose/o11y/grafana/provisioning/dashboards/esquire-services.json
#   * k8s    : k8s/charts/infra/grafana/dashboards/esquire-services.json
# Run with no arguments (python gen-dashboard.py) after changing a panel; commit the .py AND both .json.
#
# Rows -- HOW THE SYSTEM RUNS:
#       Overview / JVM / Pool-DB-Logs / CPU / DB-connection-detail / BFF(Node) / Postgres / KeyCloak /
#       Messaging bus (the x-rod meters) / Latency bands (the 4-layer request timing) / Bandwidth.
# Rows -- WHAT THE SYSTEM DOES (the esq.biz.* business meters, O1/T8):
#       Business: entity operations / money / identity + token relay / cache, keep + permissions.
#       A service can be perfectly healthy on every row above and still be doing the wrong thing -- or
#       nothing at all -- and only these rows would show it.
# The Java-service panels filter by the $application template var (Micrometer common tag); Postgres (via
# postgres-exporter) and KeyCloak (Quarkus mgmt :9000/kc-auth/metrics) carry no application tag, so those
# panels select by job / datname instead.

import json
import os
import re

DS = {"type": "prometheus", "uid": "esq-prometheus"}


def tgt(expr, legend=None):
    t = {"refId": "A", "datasource": DS, "expr": expr}
    if legend is not None:
        t["legendFormat"] = legend
    return t


def ts(title, x, y, w, unit, targets, h=8, minv=0, desc=None):
    for i, t in enumerate(targets):
        t["refId"] = chr(65 + i)
    panel = {
        "type": "timeseries", "title": title, "datasource": DS,
        "gridPos": {"h": h, "w": w, "x": x, "y": y},
        "fieldConfig": {"defaults": {"custom": {"drawStyle": "line", "fillOpacity": 10},
                                     "unit": unit, "min": minv}, "overrides": []},
        "targets": targets,
    }
    if desc is not None:
        panel["description"] = desc   # Grafana renders this as the panel's info tooltip
    return panel


def stat(title, x, y, w, targets, unit=None, h=5):
    for i, t in enumerate(targets):
        t["refId"] = chr(65 + i)
    defaults = {}
    if unit is not None:
        defaults["unit"] = unit
    return {
        "type": "stat", "title": title, "datasource": DS,
        "gridPos": {"h": h, "w": w, "x": x, "y": y},
        "fieldConfig": {"defaults": defaults, "overrides": []},
        "targets": targets,
    }


def row(title, y):
    return {"type": "row", "title": title, "gridPos": {"h": 1, "w": 24, "x": 0, "y": y}}


# ---------------------------------------------------------------------------------------------------------------
# Series arithmetic. THE TRAP: in PromQL, `A - B` where B is an EMPTY vector yields EMPTY -- it does not yield A,
# it deletes the whole series. So a band whose subtrahend has no samples yet (a fresh deploy, a service not hit
# yet, a meter behind an opt-in flag) does not degrade -- it VANISHES, silently, with no error anywhere. That is
# how the derived latency bands read "No data" on k8s while every raw timer had data.
#
# safe() and band() are the ONLY way this generator subtracts one series from another. Nothing hand-writes a `-`
# between two series -- check_no_naked_subtraction() below refuses to emit a dashboard that does.
# ---------------------------------------------------------------------------------------------------------------

def safe(expr):
    """A series that may legitimately be EMPTY, defaulted to 0 so arithmetic on it survives."""
    return "((%s) or vector(0))" % expr


def band(minuend, subtrahend):
    """minuend - subtrahend, with the subtrahend defaulted to 0 so an empty B cannot delete the whole band."""
    return "((%s) - %s)" % (minuend, safe(subtrahend))


def avg_ms(sum_metric, count_metric, by=None, extra=""):
    """Average latency in ms = rate(sum) / rate(count).

    NO clamp_min on the denominator. `clamp_min(rate(count), 1)` looks like a safe divide-by-zero guard, and it
    is -- for HIGH-rate meters. But it DIVIDES BY ONE whenever the event rate is below 1/s, which is the normal
    case for a business meter: one KeyCloak sync in five minutes is a count rate of 0.0067/s, so the clamp turns
    a real 130 ms average into 0.3 ms. Off by a factor of 400, silently, and it LOOKS plausible.

    Dividing by the true rate yields NaN when there is no traffic, which Grafana draws as a gap -- honest, and
    the correct reading of "nothing happened". A gap is not a bug; a fabricated 0.3 ms is.
    """
    grp = ("sum by (%s) " % by) if by else "sum"
    return "1000 * (%s(rate(%s[5m])%s) / %s(rate(%s[5m])%s))" % (
        grp, sum_metric, extra, grp, count_metric, extra)


def check_no_naked_subtraction(panels):
    """Refuse to emit a naked series subtraction -- the guard that keeps the trap removed, not just fixed once.

    Every `) - (` between two series must be matched by an `or vector(0)` guard on its subtrahend, which is what
    band()/safe() produce. A panel that hand-writes the minus sign fails HERE, at generation time, naming itself.
    """
    for p in panels:
        for t in p.get("targets", []):
            expr = t.get("expr", "")
            subtractions = len(re.findall(r"\)\s*-\s*\(", expr))
            guards = expr.count("or vector(0)")
            if subtractions > guards:
                raise SystemExit(
                    "naked series subtraction in panel %r:\n  %s\n"
                    "A PromQL subtraction against an EMPTY vector yields EMPTY and deletes the band.\n"
                    "Use band(a, b) / safe(x) instead of hand-writing '-' between series."
                    % (p.get("title"), expr))


APP = 'application=~"$application"'


def build_panels():
    p = []
    # ---- Overview ----
    p.append(row("Overview", 0))
    p.append(stat("Services scraped (up)", 0, 1, 6, [tgt("sum(up)")]))
    p.append(stat("Total request rate (req/s)", 6, 1, 6,
                  [tgt("sum(rate(http_server_requests_seconds_count{%s}[1m]))" % APP)], unit="reqps"))
    p.append(ts("HTTP request rate by replica", 12, 1, 12, "reqps",
                [tgt("sum by (application, instance) (rate(http_server_requests_seconds_count{%s}[1m]))" % APP,
                     "{{application}} {{instance}}")]))
    p.append(ts("HTTP p95 latency by replica", 0, 6, 12, "s",
                [tgt("histogram_quantile(0.95, sum by (le, application, instance) "
                     "(rate(http_server_requests_seconds_bucket{%s}[5m])))" % APP, "{{application}} {{instance}}")],
                desc="A GAP HERE IS NOT A BUG -- it means histogram buckets are off, which is the DEFAULT. "
                     "Percentiles need buckets, and buckets are the single most expensive thing on this stack: "
                     "the http ones ALONE were 20.5% of the entire scrape (39 series per label-combo), on "
                     "whether or not anyone ever looked at a percentile. So they are OPT-IN: set "
                     "esquire.observability.metrics.histograms-enabled=true (ESQ_METRICS_HISTOGRAMS). With it "
                     "off the timer still records count/sum/max, so every AVERAGE panel stays populated and "
                     "only the percentile goes dark. Switch it on to investigate a percentile; switch it off "
                     "for steady state."))

    # ---- JVM ----
    p.append(row("JVM", 14))
    p.append(ts("JVM heap used", 0, 15, 8, "bytes",
                [tgt('sum by (application, instance) (jvm_memory_used_bytes{area="heap", %s})' % APP,
                     "{{application}} {{instance}}")]))
    p.append(ts("Live threads", 8, 15, 8, "short",
                [tgt("jvm_threads_live_threads{%s}" % APP, "{{application}} {{instance}}")]))
    p.append(ts("GC pause rate (s/s)", 16, 15, 8, "s",
                [tgt("sum by (application, instance) (rate(jvm_gc_pause_seconds_sum{%s}[5m]))" % APP,
                     "{{application}} {{instance}}")]))
    # ---- Pool / DB / Logs ----
    p.append(row("Pool / DB / Logs", 23))
    p.append(ts("Hikari DB pool -- total (solid) / in-use (dashed)", 0, 24, 8, "short",
                [tgt("hikaricp_connections{%s}" % APP, "{{application}} {{instance}} total"),
                 tgt("hikaricp_connections_active{%s}" % APP, "{{application}} {{instance}} in-use")]))
    p.append(ts("DB query rate (connection borrows/s)", 8, 24, 8, "ops",
                [tgt("sum by (application, instance) (rate(hikaricp_connections_usage_seconds_count{%s}[1m]))" % APP,
                     "{{application}} {{instance}}")]))
    p.append(ts("Log error / warn rate", 16, 24, 8, "short",
                [tgt('sum by (application, instance, level) '
                     '(rate(logback_events_total{level=~"error|warn", %s}[5m]))' % APP,
                     "{{application}} {{instance}} {{level}}")]))
    # ---- CPU ----
    p.append(row("CPU", 32))
    p.append(ts("CPU usage by replica (process)", 0, 33, 12, "percentunit",
                [tgt("process_cpu_usage{%s}" % APP, "{{application}} {{instance}}")]))
    p.append(ts("Host CPU (system)", 12, 33, 12, "percentunit",
                [tgt("avg(system_cpu_usage{%s})" % APP, "host")]))
    # ---- DB connection detail ----
    p.append(row("DB connection detail", 41))
    p.append(ts("Avg DB connections in use (time-weighted)", 0, 42, 12, "short",
                [tgt("sum by (application, instance) (rate(hikaricp_connections_usage_seconds_sum{%s}[1m]))" % APP,
                     "{{application}} {{instance}}")]))
    p.append(ts("Avg DB connection hold time (ms/borrow)", 12, 42, 12, "ms",
                [tgt("1000 * sum by (application, instance)(rate(hikaricp_connections_usage_seconds_sum{%s}[5m])) / "
                     "sum by (application, instance)(rate(hikaricp_connections_usage_seconds_count{%s}[5m]))"
                     % (APP, APP), "{{application}} {{instance}}")]))
    # ---- BFF (Node.js) ----
    p.append(row("BFF (Node.js)", 50))
    # The BFF runs x2 on k8s -- every panel carries the instance dimension (same convention as the Java panels),
    # so the two replicas are DISTINCT series, never silently summed into one line.
    p.append(ts("BFF request rate by replica", 0, 51, 6, "reqps",
                [tgt("sum by (instance) (rate(bff_http_request_duration_seconds_count[1m]))", "{{instance}}")],
                desc="Per-replica request rate -- shows how the load balances across the x2 BFF pods. "
                     "On docker there is a single instance."))
    p.append(ts("BFF p95 latency by route", 6, 51, 6, "s",
                [tgt("histogram_quantile(0.95, sum by (le, route) (rate(bff_http_request_duration_seconds_bucket[5m])))",
                     "{{route}}")],
                desc="Latency per route, aggregated ACROSS replicas (the question here is which route is slow, "
                     "not which pod). Use the request-rate panel for the per-replica split."))
    p.append(ts("BFF memory by replica (resident + heap used)", 12, 51, 6, "bytes",
                [tgt('process_resident_memory_bytes{application="esq-backend"}', "resident {{instance}}"),
                 tgt('nodejs_heap_size_used_bytes{application="esq-backend"}', "heap used {{instance}}")]))
    p.append(ts("BFF event-loop lag (s) + CPU (cores) by replica", 18, 51, 6, "short",
                [tgt('nodejs_eventloop_lag_seconds{application="esq-backend"}', "lag {{instance}}"),
                 tgt('rate(process_cpu_seconds_total{application="esq-backend"}[1m])', "cpu {{instance}}")]))
    # ---- Postgres (via postgres-exporter) ----
    p.append(row("Postgres", 59))
    p.append(ts("Postgres connections (backends)", 0, 60, 12, "short",
                [tgt('pg_stat_database_numbackends{datname="esq2025"}', "backends {{datname}}"),
                 tgt("pg_settings_max_connections", "max")]))
    p.append(ts("Postgres transactions/s", 12, 60, 12, "ops",
                [tgt('rate(pg_stat_database_xact_commit{datname="esq2025"}[1m])', "commit"),
                 tgt('rate(pg_stat_database_xact_rollback{datname="esq2025"}[1m])', "rollback")]))
    p.append(ts("Postgres cache hit ratio", 0, 68, 12, "percentunit",
                [tgt('rate(pg_stat_database_blks_hit{datname="esq2025"}[5m]) / '
                     '(rate(pg_stat_database_blks_hit{datname="esq2025"}[5m]) + '
                     'rate(pg_stat_database_blks_read{datname="esq2025"}[5m]))', "hit ratio")]))
    p.append(ts("Postgres database size", 12, 68, 12, "bytes",
                [tgt('pg_database_size_bytes{datname="esq2025"}', "{{datname}}")]))
    # ---- KeyCloak (Quarkus mgmt :9000/kc-auth/metrics) ----
    p.append(row("KeyCloak", 76))
    p.append(ts("KeyCloak HTTP request rate", 0, 77, 12, "reqps",
                [tgt('sum(rate(http_server_requests_seconds_count{job="keycloak"}[1m]))', "requests/s")]))
    p.append(ts("KeyCloak avg HTTP latency (ms)", 12, 77, 12, "ms",
                [tgt('1000 * sum(rate(http_server_requests_seconds_sum{job="keycloak"}[1m])) / '
                     'sum(rate(http_server_requests_seconds_count{job="keycloak"}[1m]))', "avg")]))
    p.append(ts("KeyCloak DB pool (agroal)", 0, 85, 12, "short",
                [tgt('agroal_active_count{job="keycloak"}', "active"),
                 tgt('agroal_available_count{job="keycloak"}', "available")]))
    p.append(ts("KeyCloak JVM memory (heap / non-heap)", 12, 85, 12, "bytes",
                [tgt('base_memory_usedHeap_bytes{job="keycloak"}', "heap used"),
                 tgt('base_memory_usedNonHeap_bytes{job="keycloak"}', "non-heap used")]))
    # ---- Messaging bus (x-rod meters emitted by the engine, O1/T5) ----
    p.append(row("Messaging bus", 93))
    p.append(ts("Bus send rate (msg/s)", 0, 94, 8, "ops",
                [tgt("sum by (application, bus_id) (rate(messaging_send_total{%s}[1m]))" % APP,
                     "{{application}} -> {{bus_id}}")]))
    p.append(ts("Bus receive rate (msg/s)", 8, 94, 8, "ops",
                [tgt("sum by (application, bus_id) (rate(messaging_receive_total{%s}[1m]))" % APP,
                     "{{application}} <- {{bus_id}}")]))
    p.append(ts("Bus error rate (msg/s)", 16, 94, 8, "ops",
                [tgt("sum by (application, bus_id, leg) (rate(messaging_error_total{%s}[5m]))" % APP,
                     "{{application}} {{bus_id}} {{leg}}")]))
    p.append(ts("Bus send latency (avg + p95 ms)", 0, 102, 8, "ms",
                [tgt("1000 * sum by (application, bus_id)(rate(messaging_send_duration_seconds_sum{%s}[5m])) / "
                     "sum by (application, bus_id)(rate(messaging_send_duration_seconds_count{%s}[5m]))"
                     % (APP, APP), "avg {{application}} -> {{bus_id}}"),
                 tgt("1000 * histogram_quantile(0.95, sum by (le, application, bus_id) "
                     "(rate(messaging_send_duration_seconds_bucket{%s}[5m])))" % APP,
                     "p95 {{application}} -> {{bus_id}}")],
                desc="avg is always available. p95 needs the percentile buckets -- turn on the sub-switch "
                     "esquire.observability.metrics.histograms-enabled (ESQ_METRICS_HISTOGRAMS); with it off the "
                     "p95 series are simply absent and the avg still plots."))
    p.append(ts("Feed depth (tx queue)", 8, 102, 8, "short",
                [tgt("messaging_feed_depth{%s}" % APP, "{{application}} {{bus_id}}")]))
    p.append(ts("Send-retry: held + dropped (counts)", 16, 102, 4, "short",
                [tgt("sum by (application, bus_id)(messaging_retry_held{%s})" % APP,
                     "held {{application}} {{bus_id}}"),
                 tgt("(sum by (application, bus_id)(increase(messaging_retry_dropped_total{%s}[5m]))) or vector(0)"
                     % APP, "dropped/5m {{application}} {{bus_id}}")],
                minv=0,
                desc="The send-retry sublayer. FLAT AT ZERO is the healthy state -- these only move when the "
                     "transport is failing sends: held = messages parked awaiting re-dispatch, dropped = given up "
                     "after max attempts. Both are COUNTS, so they share an axis honestly; the backoff duration "
                     "is milliseconds and lives on its own panel to the right."))
    p.append(ts("Send-retry: backoff (avg ms)", 20, 102, 4, "ms",
                [tgt(avg_ms("messaging_retry_backoff_sum{%s}" % APP,
                            "messaging_retry_backoff_count{%s}" % APP, by="application, bus_id"),
                     "{{application}} {{bus_id}}")],
                desc="The backoff ladder step being waited out. A GAP here is the healthy state -- no retries, "
                     "nothing to average. It was previously drawn on the same axis as the held/dropped COUNTS, "
                     "which put milliseconds and message counts on one scale."))
    # ---- Latency bands (4-layer request timing decomposed into timers, O1/T5-B) ----
    p.append(row("Latency bands", 110))
    # The RAW timers (each layer as measured), the DERIVED bands (the subtractions those layers imply), and the
    # gateway percentile. Three across, so the Bandwidth row below keeps its y.
    AVG = "sum(rate(esq_%s_seconds_sum[5m])) / sum(rate(esq_%s_seconds_count[5m]))"
    GW_OUTER = AVG % ("gw_outer", "gw_outer")
    GW_INNER = AVG % ("gw_inner", "gw_inner")
    SRV_OUTER = AVG % ("srv_outer", "srv_outer")
    SRV_INNER = AVG % ("srv_inner", "srv_inner")
    # Every band goes through band() -- see the trap note at the top. A subtrahend with no samples (a service not
    # hit yet, a fresh deploy) must make its band read 0, never delete it.
    p.append(ts("Request latency bands -- DERIVED (avg ms)", 8, 111, 8, "ms",
                [tgt("1000 * %s" % band(GW_OUTER, GW_INNER), "net (client <-> gw)"),
                 tgt("1000 * %s" % band(GW_INNER, SRV_OUTER), "in-cluster (gw <-> srv)"),
                 tgt("1000 * %s" % band(SRV_OUTER, SRV_INNER), "srv self (compute)"),
                 tgt("1000 * %s" % safe(SRV_INNER), "srv inner (db)")],
                minv=None,   # a band can dip slightly negative on clock/rounding skew -- do not clamp it away
                desc="The four raw timers SUBTRACTED into the bands they imply: net = gw.outer - gw.inner; "
                     "in-cluster = gw.inner - srv.outer; srv self = srv.outer - srv.inner; srv inner = DB time. "
                     "Fully aggregated (scalars) on purpose: the gw timers are tagged application=gateway and the "
                     "srv timers application=<service>, so they cannot be subtracted label-wise. The DB band is "
                     "STEADY-STATE: the JPA time is collected on every request while observability is on, so it "
                     "no longer depends on the X-Capture-Metrics load-test header."))
    p.append(ts("Request latency bands -- RAW (avg ms by layer)", 0, 111, 8, "ms",
                [tgt("1000 * sum(rate(esq_gw_outer_seconds_sum[5m])) / sum(rate(esq_gw_outer_seconds_count[5m]))",
                     "gw outer (total)"),
                 tgt("1000 * sum(rate(esq_gw_inner_seconds_sum[5m])) / sum(rate(esq_gw_inner_seconds_count[5m]))",
                     "gw inner (proxied)"),
                 tgt("1000 * sum(rate(esq_srv_outer_seconds_sum[5m])) / sum(rate(esq_srv_outer_seconds_count[5m]))",
                     "srv outer (wall)"),
                 tgt("1000 * sum(rate(esq_srv_inner_seconds_sum[5m])) / sum(rate(esq_srv_inner_seconds_count[5m]))",
                     "srv inner (db, capture-gated)")]))
    p.append(ts("Gateway total p95 by route (ms)", 16, 111, 8, "ms",
                [tgt("1000 * histogram_quantile(0.95, sum by (le, route) (rate(esq_gw_outer_seconds_bucket[5m])))",
                     "{{route}}")],
                desc="Percentile buckets are opt-in: this panel needs "
                     "esquire.observability.metrics.histograms-enabled=true (ESQ_METRICS_HISTOGRAMS). "
                     "With it off the timers still emit count/sum/max, so the avg-by-layer panel beside this one "
                     "stays populated."))
    # ---- Bandwidth (HTTP byte volume off Tomcat's GlobalRequestProcessor, O1/T5-C) ----
    BW_DESC = ("Servlet services only. The gateway runs on Netty (Spring Cloud Gateway), which has no Tomcat "
               "MBean -- so the client-facing bandwidth at the edge is NOT in these panels, only the in-cluster "
               "service traffic. Bytes-in reads 0 for GET-only services (a GET has no request body).")
    p.append(row("Bandwidth", 119))
    p.append(ts("Service HTTP bytes OUT (B/s)", 0, 120, 8, "Bps",
                [tgt("sum by (application, instance) (rate(tomcat_global_sent_bytes_total{%s}[5m]))" % APP,
                     "{{application}} {{instance}}")],
                desc=BW_DESC))
    p.append(ts("Service HTTP bytes IN (B/s)", 8, 120, 8, "Bps",
                [tgt("sum by (application, instance) (rate(tomcat_global_received_bytes_total{%s}[5m]))" % APP,
                     "{{application}} {{instance}}")],
                desc=BW_DESC))
    # The EDGE: the gateway is Netty, so its byte volume comes from reactor-netty, not the Tomcat MBean. This is
    # the client-facing bandwidth. /actuator is excluded -- that traffic is Prometheus scraping the gateway itself
    # (self-monitoring), and it otherwise dwarfs the real client bytes.
    p.append(ts("Gateway EDGE bytes (B/s) -- client-facing", 16, 120, 8, "Bps",
                [tgt('sum(rate(reactor_netty_http_server_data_sent_bytes_sum{uri!="/actuator"}[5m]))', "edge out"),
                 tgt('sum(rate(reactor_netty_http_server_data_received_bytes_sum{uri!="/actuator"}[5m]))', "edge in")],
                desc="The gateway runs on Netty, so its bytes come from reactor-netty (not the Tomcat MBean). This "
                     "is the CLIENT-facing bandwidth at the edge; the two panels to the left are in-cluster service "
                     "traffic. /actuator is excluded: that is Prometheus scraping the gateway itself."))

    # ================================================================================================
    # BUSINESS METERS (esq.biz.*, O1/T8) -- the one tier that cannot be inherited.
    #
    # Everything above this line measures how the system RUNS: requests, memory, pools, bus hops, bytes.
    # These rows measure what it DOES: entities created, money moved, identities synced, caches applied.
    # A service can be perfectly healthy on every panel above and still be doing the wrong thing -- or
    # nothing at all -- and only these rows would show it.
    #
    # Read them as OUTCOMES, not volumes: the interesting series on every panel here is the one that is
    # NOT "ok" -- a denied permission, a failed move, a handler that swallowed an exception, a keep write
    # that never reached the DB. Those are flat at zero on a healthy system, which is exactly why a single
    # non-zero point is worth looking at.
    # ================================================================================================

    # ---- Business: entity operations (enyMan) ----
    p.append(row("Business -- entity operations", 128))
    p.append(ts("Entity operations (ops/s by op + outcome)", 0, 129, 8, "ops",
                [tgt("sum by (op, outcome) (rate(esq_biz_entity_ops_total{%s}[5m]))" % APP,
                     "{{op}} {{outcome}}")],
                desc="What enyMan actually DID: creates, deletes and moves, by outcome. No free meter can see "
                     "this -- http.server.requests knows the endpoint and the HTTP status, not which KIND of "
                     "entity was acted on nor whether a refusal was an authorization decision. NOTE a MOVE here "
                     "means the command was ACCEPTED (/esq-move answers 202); whether the move SUCCEEDED is the "
                     "next panel, because the work happens off-request on the queue worker."))
    # A queue DEPTH is a count and processed/failed are rates -- two units. On one axis a depth spike to 1000
    # would flatten the rates into the floor and hide exactly the failure you opened the panel for. Two panels.
    p.append(ts("Move queue -- depth (pending)", 8, 129, 4, "short",
                [tgt("sum(esq_biz_move_queue_depth{%s})" % APP, "pending")],
                minv=0,
                desc="The move backlog. /esq-move answers 202 at submit time and the work happens on the queue "
                     "worker, so a rising depth means the worker is not keeping up -- and nothing on the request "
                     "side would tell you."))
    p.append(ts("Move outcome (per s)", 12, 129, 4, "ops",
                [tgt("sum(rate(esq_biz_move_processed_total{%s}[5m]))" % APP, "processed/s"),
                 tgt("(sum(rate(esq_biz_move_failed_total{%s}[5m]))) or vector(0)" % APP, "FAILED/s")],
                minv=0,
                desc="The async half of a move. A move that FAILS on the worker is invisible to the caller (it "
                     "already got its 202) and to every HTTP meter -- this is the only place it shows. FAILED is "
                     "flat at zero on a healthy system; `or vector(0)` keeps it drawing a zero line rather than "
                     "vanishing, because a vanished series is indistinguishable from a broken panel."))
    p.append(ts("Dictionary lookups (by kind)", 16, 129, 8, "ops",
                [tgt("sum by (kind) (rate(esq_biz_dict_lookup_total{%s}[5m]))" % APP, "kind {{kind}}")],
                desc="Which DICTIONARY is being fetched. Not a duplicate of http.server.requests: that meter is "
                     "tagged by URI template (/esq-dict), and the kind is a query param -- so the free meter can "
                     "tell you the endpoint is busy but never which dictionary."))

    # ---- Business: money (pacMan) ----
    p.append(row("Business -- money", 137))
    p.append(ts("Account transactions (tx/s by type + outcome)", 0, 138, 8, "ops",
                [tgt("sum by (type, outcome) (rate(esq_biz_acct_tx_total{%s}[5m]))" % APP,
                     "{{type}} {{outcome}}")],
                desc="The money path: deposits, withdrawals, transfers, by outcome. Both processors report here "
                     "-- the transfer processor OVERRIDES the single one and does not call super, so it needed "
                     "its own meter or every transfer would have been silently missing from this panel."))
    p.append(ts("Transaction latency (avg ms by type)", 8, 138, 8, "ms",
                [tgt(avg_ms("esq_biz_acct_tx_duration_seconds_sum{%s}" % APP,
                           "esq_biz_acct_tx_duration_seconds_count{%s}" % APP, by="type"), "{{type}}")],
                desc="How long a transaction takes end to end inside pacMan, by operation. A transfer is two "
                     "legs and a rate lookup, so it is legitimately dearer than a deposit -- the shape to watch "
                     "is a type getting slower against ITSELF, not one type against another."))
    p.append(ts("FX applied + accounts closed", 16, 138, 8, "ops",
                [tgt("sum(rate(esq_biz_acct_fx_apply_total{%s}[5m]))" % APP, "fx applied/s"),
                 tgt('sum by (purge) (rate(esq_biz_acct_close_total{%s}[5m]))' % APP, "closed ({{purge}})")],
                desc="A conversion rate is only present on the cross-currency leg of a transfer, so a non-null "
                     "convRate IS the FX application. Closures are counted only once the delete has SUCCEEDED "
                     "past the three guards; purge=test-house marks the demo-data path that forces those guards "
                     "open, so a real closure is never confused with a fixture teardown."))

    # ---- Business: identity + token relay (kcMaster, gateway) ----
    p.append(row("Business -- identity + token relay", 146))
    p.append(ts("KeyCloak identity sync (by op + outcome)", 0, 147, 6, "ops",
                [tgt("sum by (op, outcome) (rate(esq_biz_kc_sync_total{%s}[5m]))" % APP,
                     "{{op}} {{outcome}}")],
                desc="Whether Esquire and KeyCloak still AGREE about who exists. The bus meters say a sync "
                     "request arrived; only this says whether the identity was actually brought into line. A "
                     "non-zero error line means the two systems have DRIFTED -- a user Esquire thinks can log "
                     "in, and KeyCloak does not."))
    p.append(ts("KeyCloak sync latency (avg ms)", 6, 147, 6, "ms",
                [tgt(avg_ms("esq_biz_kc_sync_duration_seconds_sum{%s}" % APP,
                           "esq_biz_kc_sync_duration_seconds_count{%s}" % APP), "kc sync")],
                desc="KeyCloak is a SEPARATE SERVER and nothing else times it. Measured around the whole sync "
                     "rather than inside the admin client, so it is the sync's wall time -- but the KC "
                     "round-trip dominates it (the attribute mapping either side is microseconds against "
                     "KeyCloak's milliseconds)."))
    # The hit rate and the acquire cost are TWO UNITS -- a percentage (0..100) and milliseconds. On one axis the
    # percentage owns the scale and the latency line lies flat against zero, unreadable. They are two panels.
    p.append(ts("Token relay -- cache hit rate", 12, 147, 6, "percent",
                [tgt('100 * sum(rate(esq_biz_gw_tokenrelay_total{result="hit"}[5m])) '
                     '/ sum(rate(esq_biz_gw_tokenrelay_total[5m]))', "cache hit rate")],
                minv=0,
                desc="THE MOST LOAD-BEARING NUMBER ON THIS DASHBOARD. A cache hit serves the request without "
                     "touching KeyCloak; a miss is a live /token round-trip on the hot path. So the hit rate IS "
                     "how much of KeyCloak's latency the users are spared -- read it against the acquire cost on "
                     "the panel to the right. If it collapses (a TTL change, an unstable cache key, an eviction "
                     "storm) every request starts paying that cost, and the only other symptom is 'the gateway "
                     "got slower' with no cause visible anywhere. "
                     "NOTE: the relay is DORMANT unless the calling client is on the gateway's allowlist, so this "
                     "is a GAP on a plain-JWT workload -- that is correct, not a broken panel."))
    p.append(ts("Token relay -- KC /token acquire (avg ms)", 18, 147, 6, "ms",
                [tgt(avg_ms("esq_biz_gw_tokenrelay_duration_seconds_sum",
                            "esq_biz_gw_tokenrelay_duration_seconds_count", by="outcome"), "{{outcome}}")],
                desc="What a cache MISS costs: a live round-trip to KeyCloak, an external server, on the "
                     "request's hot path. Multiply it by (100 - hit rate) from the panel on the left to get what "
                     "the relay is actually costing users. Split BY OUTCOME so a failure is still visible without "
                     "putting a second unit on the axis -- an error or a cancelled relay has a duration too, and "
                     "those lines simply do not exist on a healthy system."))

    # ---- Business: cache, keep + permissions (bizTree, dataKeep, cross-cutting) ----
    p.append(row("Business -- cache, keep + permissions", 155))
    p.append(ts("Tree cache -- broadcast dispatch (by outcome)", 0, 156, 8, "ops",
                [tgt("sum by (outcome) (rate(esq_biz_tree_handler_dispatch_total{%s}[5m]))" % APP,
                     "{{outcome}}"),
                 tgt("sum by (outcome) (rate(esq_biz_tree_rebuild_total{%s}[5m]))" % APP,
                     "rebuild ({{outcome}})")],
                desc="What the CACHE did with each broadcast -- applied it, found no handler, found no payload, "
                     "or FAILED. The failed line is the point: the dispatch hub SWALLOWS a handler exception, so "
                     "a handler that blows up leaves the tree silently stale while the bus still counts the "
                     "message as received. Rebuilds should be RARE -- a rising rebuild rate is itself a finding."))
    p.append(ts("Audit keep -- DB writes (by op + outcome)", 8, 156, 8, "ops",
                [tgt("sum by (op, outcome) (rate(esq_biz_keep_write_total{%s}[5m]))" % APP,
                     "{{op}} {{outcome}}")],
                desc="THE DB WRITE at the keep sink -- the one thing the bus meters cannot see. "
                     "messaging.receive.total says the audit event ARRIVED; only this says whether the row was "
                     "actually WRITTEN. An audit event that lands on the bus and then fails to persist is "
                     "exactly the failure that was invisible before. These counts must RECONCILE with the bus "
                     "receive count on the Messaging bus row: a divergence is a real finding."))
    p.append(ts("Permission checks (allow vs DENY)", 16, 156, 8, "ops",
                [tgt("sum by (cmd, result) (rate(esq_biz_perm_check_total{%s}[5m]))" % APP,
                     "{{cmd}} {{result}}")],
                desc="The authorization decision itself, counted at the one gate every service goes through. A "
                     "rising DENY rate is either a misconfigured role or someone probing. NOTE the gate sees "
                     "allow and deny only: a self-update BYPASSES it entirely (a user editing their own profile "
                     "never asks permission), which is why there is no third value here."))
    return p


def build_dashboard():
    panels = build_panels()
    check_no_naked_subtraction(panels)   # refuse to emit a band that an empty vector could delete
    return {
        "uid": "esq-services",
        "title": "Esquire Services -- REST / JVM / Pool / CPU / BFF / DB / KC / Bus / Latency / Bandwidth / Biz",
        "tags": ["esquire", "o11y"],
        "timezone": "",
        "schemaVersion": 39,
        "version": 1,
        "refresh": "30s",
        "time": {"from": "now-1h", "to": "now"},
        "templating": {"list": [{
            "name": "application", "label": "Service", "type": "query", "datasource": DS,
            "query": "label_values(up, application)", "refresh": 2,
            "includeAll": True, "multi": True, "allValue": ".*",
            "current": {"text": "All", "value": "$__all"}, "sort": 1,
        }]},
        "panels": panels,
    }


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    svc_root = os.path.abspath(os.path.join(here, "..", "..", ".."))   # compose/o11y/grafana -> services
    outputs = [
        os.path.join(svc_root, "compose", "o11y", "grafana", "provisioning", "dashboards", "esquire-services.json"),
        os.path.join(svc_root, "k8s", "charts", "infra", "grafana", "dashboards", "esquire-services.json"),
    ]
    d = build_dashboard()
    for path in outputs:
        with open(path, "w") as f:
            json.dump(d, f, indent=1)
        print("wrote", path)


if __name__ == "__main__":
    main()
