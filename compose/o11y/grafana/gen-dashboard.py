#!/usr/bin/env python3
# Esquire frameworks (tm) -- Grafana dashboard generator (v1.2.11 observability).
#
# Single source of truth for the "Esquire Services" Grafana dashboard. Emits the SAME dashboard JSON to both
# deploy targets so they never drift:
#   * docker : compose/o11y/grafana/provisioning/dashboards/esquire-services.json
#   * k8s    : k8s/charts/infra/grafana/dashboards/esquire-services.json
# Run with no arguments (python gen-dashboard.py) after changing a panel; commit the .py AND both .json.
#
# Rows: Overview / JVM / Pool-DB-Logs / CPU / DB-connection-detail / BFF(Node) / Postgres / KeyCloak.
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


def ts(title, x, y, w, unit, targets, h=8, minv=0):
    for i, t in enumerate(targets):
        t["refId"] = chr(65 + i)
    return {
        "type": "timeseries", "title": title, "datasource": DS,
        "gridPos": {"h": h, "w": w, "x": x, "y": y},
        "fieldConfig": {"defaults": {"custom": {"drawStyle": "line", "fillOpacity": 10},
                                     "unit": unit, "min": minv}, "overrides": []},
        "targets": targets,
    }


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
    p.append(ts("BFF request rate by route", 0, 51, 6, "reqps",
                [tgt("sum by (route) (rate(bff_http_request_duration_seconds_count[1m]))", "{{route}}")]))
    p.append(ts("BFF p95 latency by route", 6, 51, 6, "s",
                [tgt("histogram_quantile(0.95, sum by (le, route) (rate(bff_http_request_duration_seconds_bucket[5m])))",
                     "{{route}}")]))
    p.append(ts("BFF memory (resident + heap used)", 12, 51, 6, "bytes",
                [tgt('process_resident_memory_bytes{application="esq-backend"}', "resident"),
                 tgt('nodejs_heap_size_used_bytes{application="esq-backend"}', "heap used")]))
    p.append(ts("BFF event-loop lag (s) + CPU (cores)", 18, 51, 6, "short",
                [tgt('nodejs_eventloop_lag_seconds{application="esq-backend"}', "event-loop lag"),
                 tgt('rate(process_cpu_seconds_total{application="esq-backend"}[1m])', "cpu (cores)")]))
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
    return p


def build_dashboard():
    return {
        "uid": "esq-services",
        "title": "Esquire Services -- REST / JVM / Pool / CPU / BFF / DB / KC",
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
