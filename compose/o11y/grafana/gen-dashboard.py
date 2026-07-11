#!/usr/bin/env python3
# Esquire frameworks (tm) -- Grafana dashboard generator (v1.2.11 observability).
#
# Single source of truth for the "Esquire Services" Grafana dashboard. Emits the SAME dashboard JSON to both
# deploy targets so they never drift:
#   * docker : compose/o11y/grafana/provisioning/dashboards/esquire-services.json
#   * k8s    : k8s/charts/infra/grafana/dashboards/esquire-services.json
# Run with no arguments (python gen-dashboard.py) after changing a panel; commit the .py AND both .json.
#
# Rows: Overview / JVM / Pool-DB-Logs / CPU / DB-connection-detail / BFF(Node) / Postgres / KeyCloak /
#       Messaging bus (the x-rod meters) / Latency bands (the 4-layer request timing) / Bandwidth.
# The Java-service panels filter by the $application template var (Micrometer common tag); Postgres (via
# postgres-exporter) and KeyCloak (Quarkus mgmt :9000/kc-auth/metrics) carry no application tag, so those
# panels select by job / datname instead.

import json
import os

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
                     "(rate(http_server_requests_seconds_bucket{%s}[5m])))" % APP, "{{application}} {{instance}}")]))
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
                     'clamp_min(sum(rate(http_server_requests_seconds_count{job="keycloak"}[1m])), 1)', "avg")]))
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
                     "clamp_min(sum by (application, bus_id)(rate(messaging_send_duration_seconds_count{%s}[5m])), 1)"
                     % (APP, APP), "avg {{application}} -> {{bus_id}}"),
                 tgt("1000 * histogram_quantile(0.95, sum by (le, application, bus_id) "
                     "(rate(messaging_send_duration_seconds_bucket{%s}[5m])))" % APP,
                     "p95 {{application}} -> {{bus_id}}")],
                desc="avg is always available. p95 needs the percentile buckets -- turn on the sub-switch "
                     "esquire.observability.metrics.histograms-enabled (ESQ_METRICS_HISTOGRAMS); with it off the "
                     "p95 series are simply absent and the avg still plots."))
    p.append(ts("Feed depth (tx queue)", 8, 102, 8, "short",
                [tgt("messaging_feed_depth{%s}" % APP, "{{application}} {{bus_id}}")]))
    p.append(ts("Send-retry: held / dropped / backoff", 16, 102, 8, "short",
                [tgt("messaging_retry_held{%s}" % APP, "held (count) {{application}} {{bus_id}}"),
                 tgt("sum by (application, bus_id) (rate(messaging_retry_dropped{%s}[5m]))" % APP,
                     "dropped/s {{application}} {{bus_id}}"),
                 tgt("sum by (application, bus_id)(rate(messaging_retry_backoff_sum{%s}[5m])) / "
                     "clamp_min(sum by (application, bus_id)(rate(messaging_retry_backoff_count{%s}[5m])), 1)"
                     % (APP, APP), "backoff avg (ms) {{application}} {{bus_id}}")],
                desc="The send-retry sublayer. FLAT AT ZERO is the healthy state -- these only move when the "
                     "transport is failing sends: held = messages parked awaiting re-dispatch, dropped/s = given up "
                     "after max attempts, backoff avg = the ladder step being waited out. Mixed units (count, /s, "
                     "ms) on one axis deliberately: this is a diagnostic panel you read when something is wrong, "
                     "and all three are small numbers."))
    # ---- Latency bands (4-layer request timing decomposed into timers, O1/T5-B) ----
    p.append(row("Latency bands", 110))
    # The RAW timers (each layer as measured), the DERIVED bands (the subtractions those layers imply), and the
    # gateway percentile. Three across, so the Bandwidth row below keeps its y.
    AVG = "sum(rate(esq_%s_seconds_sum[5m])) / clamp_min(sum(rate(esq_%s_seconds_count[5m])), 1)"
    # srv_inner (the DB band) is CAPTURE-gated: when X-Capture-Metrics is off the JPA timer never runs, so the
    # series does not exist at all. A PromQL subtraction against an EMPTY vector yields EMPTY -- which would make
    # the whole 'srv self' band vanish even though srv_outer has data. Default it to 0 so the decomposition
    # degrades gracefully: srv self then simply absorbs the DB time.
    DB = "((%s) or vector(0))" % (AVG % ("srv_inner", "srv_inner"))
    p.append(ts("Request latency bands -- DERIVED (avg ms)", 8, 111, 8, "ms",
                [tgt("1000 * ((%s) - (%s))" % (AVG % ("gw_outer", "gw_outer"), AVG % ("gw_inner", "gw_inner")),
                     "net (client <-> gw)"),
                 tgt("1000 * ((%s) - (%s))" % (AVG % ("gw_inner", "gw_inner"), AVG % ("srv_outer", "srv_outer")),
                     "in-cluster (gw <-> srv)"),
                 tgt("1000 * ((%s) - %s)" % (AVG % ("srv_outer", "srv_outer"), DB),
                     "srv self (compute)"),
                 tgt("1000 * %s" % DB, "srv inner (db)")],
                minv=None,   # a band can dip slightly negative on clock/rounding skew -- do not clamp it away
                desc="The four raw timers SUBTRACTED into the bands they imply: net = gw.outer - gw.inner; "
                     "in-cluster = gw.inner - srv.outer; srv self = srv.outer - srv.inner; srv inner = DB time. "
                     "Fully aggregated (scalars) on purpose: the gw timers are tagged application=gateway and the "
                     "srv timers application=<service>, so they cannot be subtracted label-wise. NOTE: srv inner "
                     "(DB) is CAPTURE-gated -- with X-Capture-Metrics off the series does not exist, so it is "
                     "defaulted to 0 here and 'srv self' absorbs the DB time (the bands still sum to the total)."))
    p.append(ts("Request latency bands -- RAW (avg ms by layer)", 0, 111, 8, "ms",
                [tgt("1000 * sum(rate(esq_gw_outer_seconds_sum[5m])) / clamp_min(sum(rate(esq_gw_outer_seconds_count[5m])), 1)",
                     "gw outer (total)"),
                 tgt("1000 * sum(rate(esq_gw_inner_seconds_sum[5m])) / clamp_min(sum(rate(esq_gw_inner_seconds_count[5m])), 1)",
                     "gw inner (proxied)"),
                 tgt("1000 * sum(rate(esq_srv_outer_seconds_sum[5m])) / clamp_min(sum(rate(esq_srv_outer_seconds_count[5m])), 1)",
                     "srv outer (wall)"),
                 tgt("1000 * sum(rate(esq_srv_inner_seconds_sum[5m])) / clamp_min(sum(rate(esq_srv_inner_seconds_count[5m])), 1)",
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
    return p


def build_dashboard():
    return {
        "uid": "esq-services",
        "title": "Esquire Services -- REST / JVM / Pool / CPU / BFF / DB / KC / Bus / Latency",
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
        "panels": build_panels(),
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
