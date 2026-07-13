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


def tgt(expr, legend=None, exemplar=False):
    """A Prometheus target. exemplar=True asks Grafana to ALSO fetch exemplars for this query and draw them.

    The flag is not cosmetic. The services attach a trace_id to histogram bucket samples and Prometheus stores
    them, but Grafana does not go looking for exemplars unless the QUERY asks -- so without this the diamonds
    simply never appear and the metrics -> trace hop is dead at the last inch, with everything upstream of it
    working perfectly. Only meaningful on a query that reads _bucket series (a percentile); an avg built from
    rate(sum)/rate(count) is a division and carries no exemplars, so setting it there would be a lie.
    """
    t = {"refId": "A", "datasource": DS, "expr": expr}
    if legend is not None:
        t["legendFormat"] = legend
    if exemplar:
        t["exemplar"] = True
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
# The OTHER TWO SIGNALS (T9-B). Until now every panel on this dashboard was Prometheus -- 53 of them -- and the
# logs and traces were reachable only by LEAVING the pane for Explore. Grafana hands Explore to every provisioned
# datasource for free, the moment it exists; that proves the store is reachable and nothing more. It is the
# baseline, not the goal. These two builders are what put the other two signals ON the pane.
#
# Both stream selectors use job=~"esq-.*" on purpose: Alloy labels docker logs esq-docker and k8s logs esq-k8s,
# and ONE dashboard JSON serves both targets.
# ---------------------------------------------------------------------------------------------------------------

DS_LOKI = {"type": "loki", "uid": "esq-loki"}
DS_TEMPO = {"type": "tempo", "uid": "esq-tempo"}

JOB_ANY = 'job=~"esq-.*"'

# THE VIEWER IS NOT THE SYSTEM. Alloy ships the logs of EVERY container on the stack -- the LGTM stack's own
# included. Grafana and Tempo alone produced as many lines as the gateway (216 + 196 vs 412 in ten minutes), so a
# log panel filled up with Tempo compaction cycles and Grafana's own request log: the tooling talking about
# itself, burying the thing it exists to show.
#
# An ALLOW-list, not a deny-list, and deliberately so: a deny-list has to be updated every time the stack gains a
# container, and the failure mode of forgetting is that noise silently reappears. Naming the services we mean can
# only ever fail the safe way -- a container we forgot is absent, which is visible, rather than present, which is
# not.
#
# Fully anchored by Loki, and covers both targets' naming (docker `esq-gateway`, k8s `gateway`).
ESQ_SERVICES = ('container=~"(esq-)?(gateway|biztree|enyman|pacman|keysmith|kcmaster|aukeep|backend|frontend)"')


def logs(title, x, y, w, expr, h=9, desc=None):
    panel = {
        "type": "logs", "title": title, "datasource": DS_LOKI,
        "gridPos": {"h": h, "w": w, "x": x, "y": y},
        "options": {"showTime": True, "wrapLogMessage": True, "sortOrder": "Descending",
                    "enableLogDetails": True, "prettifyLogMessage": False},
        "targets": [{"refId": "A", "datasource": DS_LOKI, "expr": expr, "queryType": "range"}],
    }
    if desc is not None:
        panel["description"] = desc
    return panel


def traces(title, x, y, w, query, h=9, limit=20, desc=None):
    panel = {
        "type": "table", "title": title, "datasource": DS_TEMPO,
        "gridPos": {"h": h, "w": w, "x": x, "y": y},
        "targets": [{"refId": "A", "datasource": DS_TEMPO, "queryType": "traceql",
                     "query": query, "limit": limit, "tableType": "traces"}],
    }
    if desc is not None:
        panel["description"] = desc
    return panel


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
                     "(rate(http_server_requests_seconds_bucket{%s}[5m])))" % APP, "{{application}} {{instance}}",
                     exemplar=True)],
                desc="CLICK A DIAMOND -- this is the metrics -> trace hop, and the most valuable jump on the pane: "
                     "a spike here is where a human NOTICES a problem, and each diamond opens the TRACE THAT "
                     "PRODUCED THAT EXACT SAMPLE (not a similar request -- that one). "
                     "AN EMPTY PANEL IS NOT A BUG. Both the line and the diamonds ride on histogram buckets, and "
                     "buckets are OFF BY DEFAULT: they cost ~2.3x the entire scrape (4,597 -> 10,686 series), paid "
                     "always, whether or not anyone looks at a percentile. So they are opt-in -- set "
                     "esquire.observability.metrics.histograms-enabled=true (ESQ_METRICS_HISTOGRAMS) to light this "
                     "panel AND the diamonds, and turn it back off for steady state. With it off every AVERAGE "
                     "panel stays populated (the timers still record count/sum/max); only the percentile and the "
                     "exemplars go dark, and they go dark TOGETHER."))

    # ---- Logs + Traces: the other two signals, ON the pane (T9-B) ----
    # This row is the whole point of the phase. Everything else on this dashboard is Prometheus; before this the
    # "single pane" carried exactly ONE of the three signals and the other two sat in Explore, where the human was
    # the integration. It sits directly under Overview on purpose: you land on the dashboard and all three signals
    # are in front of you.
    p.append(row("Logs + Traces", 14))
    p.append(logs("Errors + warnings (all services)", 0, 15, 12,
                  '{%s, %s} | detected_level =~ "warn|error"' % (JOB_ANY, ESQ_SERVICES),
                  desc="Live WARN/ERROR across the whole fleet, both targets. Click a line to expand it: the "
                       "correlationId carries a 'View trace' link (the logs -> trace hop) that opens the trace "
                       "that produced it. Empty is the healthy state. NOTE correlationId rides as structured "
                       "METADATA, not a Loki label -- labels would explode cardinality -- which is why it is "
                       "filtered with a | expression and not inside the {} stream selector."))
    p.append(traces("Slowest recent traces (>100ms)", 12, 15, 12,
                    "{ duration > 100ms }",
                    desc="Recent traces over 100ms, newest first. Click one to open the waterfall; from a span "
                         "you can then jump to its logs (trace -> logs) and to the RED metrics of the service it "
                         "ran in (trace -> metrics). The service.name badge is the ROD-ID (enyman.0) -- the "
                         "traces pipeline overwrites it with the instance id so the waterfall shows WHICH replica "
                         "emitted a span; the logical service name rides alongside as esq.app."))

    # ---- JVM ----
    p.append(row("JVM", 24))
    p.append(ts("JVM heap used", 0, 25, 8, "bytes",
                [tgt('sum by (application, instance) (jvm_memory_used_bytes{area="heap", %s})' % APP,
                     "{{application}} {{instance}}")]))
    p.append(ts("Live threads", 8, 25, 8, "short",
                [tgt("jvm_threads_live_threads{%s}" % APP, "{{application}} {{instance}}")]))
    p.append(ts("GC pause rate (s/s)", 16, 25, 8, "s",
                [tgt("sum by (application, instance) (rate(jvm_gc_pause_seconds_sum{%s}[5m]))" % APP,
                     "{{application}} {{instance}}")]))
    # ---- Pool / DB / Logs ----
    p.append(row("Pool / DB / Logs", 33))
    p.append(ts("Hikari DB pool -- total (solid) / in-use (dashed)", 0, 34, 8, "short",
                [tgt("hikaricp_connections{%s}" % APP, "{{application}} {{instance}} total"),
                 tgt("hikaricp_connections_active{%s}" % APP, "{{application}} {{instance}} in-use")]))
    p.append(ts("DB query rate (connection borrows/s)", 8, 34, 8, "ops",
                [tgt("sum by (application, instance) (rate(hikaricp_connections_usage_seconds_count{%s}[1m]))" % APP,
                     "{{application}} {{instance}}")]))
    p.append(ts("Log error / warn rate", 16, 34, 8, "short",
                [tgt('sum by (application, instance, level) '
                     '(rate(logback_events_total{level=~"error|warn", %s}[5m]))' % APP,
                     "{{application}} {{instance}} {{level}}")]))
    # ---- CPU ----
    p.append(row("CPU", 42))
    p.append(ts("CPU usage by replica (process)", 0, 43, 12, "percentunit",
                [tgt("process_cpu_usage{%s}" % APP, "{{application}} {{instance}}")]))
    p.append(ts("Host CPU (system)", 12, 43, 12, "percentunit",
                [tgt("avg(system_cpu_usage{%s})" % APP, "host")]))
    # ---- DB connection detail ----
    p.append(row("DB connection detail", 51))
    p.append(ts("Avg DB connections in use (time-weighted)", 0, 52, 12, "short",
                [tgt("sum by (application, instance) (rate(hikaricp_connections_usage_seconds_sum{%s}[1m]))" % APP,
                     "{{application}} {{instance}}")]))
    p.append(ts("Avg DB connection hold time (ms/borrow)", 12, 52, 12, "ms",
                [tgt("1000 * sum by (application, instance)(rate(hikaricp_connections_usage_seconds_sum{%s}[5m])) / "
                     "sum by (application, instance)(rate(hikaricp_connections_usage_seconds_count{%s}[5m]))"
                     % (APP, APP), "{{application}} {{instance}}")]))
    # ---- BFF (Node.js) ----
    p.append(row("BFF (Node.js)", 60))
    # The BFF runs x2 on k8s -- every panel carries the instance dimension (same convention as the Java panels),
    # so the two replicas are DISTINCT series, never silently summed into one line.
    p.append(ts("BFF request rate by replica", 0, 61, 6, "reqps",
                [tgt("sum by (instance) (rate(bff_http_request_duration_seconds_count[1m]))", "{{instance}}")],
                desc="Per-replica request rate -- shows how the load balances across the x2 BFF pods. "
                     "On docker there is a single instance."))
    p.append(ts("BFF p95 latency by route", 6, 61, 6, "s",
                [tgt("histogram_quantile(0.95, sum by (le, route) (rate(bff_http_request_duration_seconds_bucket[5m])))",
                     "{{route}}")],
                desc="Latency per route, aggregated ACROSS replicas (the question here is which route is slow, "
                     "not which pod). Use the request-rate panel for the per-replica split."))
    p.append(ts("BFF memory by replica (resident + heap used)", 12, 61, 6, "bytes",
                [tgt('process_resident_memory_bytes{application="esq-backend"}', "resident {{instance}}"),
                 tgt('nodejs_heap_size_used_bytes{application="esq-backend"}', "heap used {{instance}}")]))
    p.append(ts("BFF event-loop lag (s) + CPU (cores) by replica", 18, 61, 6, "short",
                [tgt('nodejs_eventloop_lag_seconds{application="esq-backend"}', "lag {{instance}}"),
                 tgt('rate(process_cpu_seconds_total{application="esq-backend"}[1m])', "cpu {{instance}}")]))
    # ---- Postgres (via postgres-exporter) ----
    p.append(row("Postgres", 69))
    p.append(ts("Postgres connections (backends)", 0, 70, 12, "short",
                [tgt('pg_stat_database_numbackends{datname="esq2025"}', "backends {{datname}}"),
                 tgt("pg_settings_max_connections", "max")]))
    p.append(ts("Postgres transactions/s", 12, 70, 12, "ops",
                [tgt('rate(pg_stat_database_xact_commit{datname="esq2025"}[1m])', "commit"),
                 tgt('rate(pg_stat_database_xact_rollback{datname="esq2025"}[1m])', "rollback")]))
    p.append(ts("Postgres cache hit ratio", 0, 78, 12, "percentunit",
                [tgt('rate(pg_stat_database_blks_hit{datname="esq2025"}[5m]) / '
                     '(rate(pg_stat_database_blks_hit{datname="esq2025"}[5m]) + '
                     'rate(pg_stat_database_blks_read{datname="esq2025"}[5m]))', "hit ratio")]))
    p.append(ts("Postgres database size", 12, 78, 12, "bytes",
                [tgt('pg_database_size_bytes{datname="esq2025"}', "{{datname}}")]))
    # ---- KeyCloak (Quarkus mgmt :9000/kc-auth/metrics) ----
    p.append(row("KeyCloak", 86))
    p.append(ts("KeyCloak HTTP request rate", 0, 87, 12, "reqps",
                [tgt('sum(rate(http_server_requests_seconds_count{job="keycloak"}[1m]))', "requests/s")]))
    p.append(ts("KeyCloak avg HTTP latency (ms)", 12, 87, 12, "ms",
                [tgt('1000 * sum(rate(http_server_requests_seconds_sum{job="keycloak"}[1m])) / '
                     'sum(rate(http_server_requests_seconds_count{job="keycloak"}[1m]))', "avg")]))
    p.append(ts("KeyCloak DB pool (agroal)", 0, 95, 12, "short",
                [tgt('agroal_active_count{job="keycloak"}', "active"),
                 tgt('agroal_available_count{job="keycloak"}', "available")]))
    p.append(ts("KeyCloak JVM memory (heap / non-heap)", 12, 95, 12, "bytes",
                [tgt('base_memory_usedHeap_bytes{job="keycloak"}', "heap used"),
                 tgt('base_memory_usedNonHeap_bytes{job="keycloak"}', "non-heap used")]))
    # ---- Messaging bus (x-rod meters emitted by the engine, O1/T5) ----
    p.append(row("Messaging bus", 103))
    p.append(ts("Bus send rate (msg/s)", 0, 104, 8, "ops",
                [tgt("sum by (application, bus_id) (rate(messaging_send_total{%s}[1m]))" % APP,
                     "{{application}} -> {{bus_id}}")]))
    p.append(ts("Bus receive rate (msg/s)", 8, 104, 8, "ops",
                [tgt("sum by (application, bus_id) (rate(messaging_receive_total{%s}[1m]))" % APP,
                     "{{application}} <- {{bus_id}}")]))
    p.append(ts("Bus error rate (msg/s)", 16, 104, 8, "ops",
                [tgt("sum by (application, bus_id, leg) (rate(messaging_error_total{%s}[5m]))" % APP,
                     "{{application}} {{bus_id}} {{leg}}")]))
    p.append(ts("Bus send latency (avg + p95 ms)", 0, 112, 8, "ms",
                [tgt("1000 * sum by (application, bus_id)(rate(messaging_send_duration_seconds_sum{%s}[5m])) / "
                     "sum by (application, bus_id)(rate(messaging_send_duration_seconds_count{%s}[5m]))"
                     % (APP, APP), "avg {{application}} -> {{bus_id}}"),
                 tgt("1000 * histogram_quantile(0.95, sum by (le, application, bus_id) "
                     "(rate(messaging_send_duration_seconds_bucket{%s}[5m])))" % APP,
                     "p95 {{application}} -> {{bus_id}}", exemplar=True)],
                desc="avg is always available. p95 needs the percentile buckets -- turn on the sub-switch "
                     "esquire.observability.metrics.histograms-enabled (ESQ_METRICS_HISTOGRAMS); with it off the "
                     "p95 series are simply absent and the avg still plots."))
    p.append(ts("Feed depth (tx queue)", 8, 112, 8, "short",
                [tgt("messaging_feed_depth{%s}" % APP, "{{application}} {{bus_id}}")]))
    p.append(ts("Send-retry: held + dropped (counts)", 16, 112, 4, "short",
                [tgt("sum by (application, bus_id)(messaging_retry_held{%s})" % APP,
                     "held {{application}} {{bus_id}}"),
                 tgt("(sum by (application, bus_id)(increase(messaging_retry_dropped_total{%s}[5m]))) or vector(0)"
                     % APP, "dropped/5m {{application}} {{bus_id}}")],
                minv=0,
                desc="The send-retry sublayer. FLAT AT ZERO is the healthy state -- these only move when the "
                     "transport is failing sends: held = messages parked awaiting re-dispatch, dropped = given up "
                     "after max attempts. Both are COUNTS, so they share an axis honestly; the backoff duration "
                     "is milliseconds and lives on its own panel to the right."))
    p.append(ts("Send-retry: backoff (avg ms)", 20, 112, 4, "ms",
                [tgt(avg_ms("messaging_retry_backoff_sum{%s}" % APP,
                            "messaging_retry_backoff_count{%s}" % APP, by="application, bus_id"),
                     "{{application}} {{bus_id}}")],
                desc="The backoff ladder step being waited out. A GAP here is the healthy state -- no retries, "
                     "nothing to average. It was previously drawn on the same axis as the held/dropped COUNTS, "
                     "which put milliseconds and message counts on one scale."))
    # ---- Broker (ActiveMQ, T9-A) -- the OTHER end of every hop in the row above ----
    # The Messaging-bus row is what a SERVICE thinks it sent and received. This row is what the BROKER actually
    # holds. They measure the same hops from opposite ends, which is why they sit together: a service that
    # believes it sent 27 and a destination that enqueued 27 agree; a growing gap between them is the finding.
    #
    # RECONCILIATION TRAP (measured 2026-07-12, do not re-discover): the broker counts EVERY message, including
    # the x-rod keep-alive probes; the service-side meters deliberately do not (RodEvent.isSession()). So on any
    # bus with `alive: true` -- esquire.kc on docker -- broker enqueue will ALWAYS run ahead of
    # messaging_send_total, forever, at the heartbeat cadence. That gap is NOT message loss. Reconcile on a bus
    # WITHOUT keep-alive (esquire.entity) if you want the two ends to match to the message.
    BROKER_DESC = ("From the broker's own JMX MBeans (the JMX exporter agent inside the broker JVM), not from any "
                   "service. This is the only place the bus can be seen from the OUTSIDE: what is actually sitting "
                   "in a destination, and whether anyone is still consuming it.")
    p.append(row("Broker (ActiveMQ)", 120))
    p.append(ts("Queue depth (pending messages)", 0, 121, 8, "short",
                [tgt("activemq_queue_depth", "{{destination}}")],
                desc="THE number that says the bus is in trouble. Healthy is flat at (or near) zero: messages are "
                     "consumed as fast as they arrive. A depth that climbs and does not come back down means the "
                     "consumers are gone, wedged, or slower than the producers -- and unlike a service-side meter, "
                     "this keeps being true when the consumer is dead. All four destinations are DECLARED in "
                     "activemq.xml, so they are plotted from broker start at zero traffic rather than appearing "
                     "only after the first message."))
    p.append(ts("Enqueue / dequeue rate (msg/s)", 8, 121, 8, "ops",
                [tgt("sum by (destination) (rate(activemq_queue_enqueue_count[1m]))", "in  {{destination}}"),
                 tgt("sum by (destination) (rate(activemq_queue_dequeue_count[1m]))", "out {{destination}}"),
                 tgt("sum by (destination) (rate(activemq_topic_enqueue_count[1m]))", "in  {{destination}}"),
                 tgt("sum by (destination) (rate(activemq_topic_dispatch_count[1m]))", "out {{destination}}")],
                desc="In vs out per destination. The two lines tracking each other is the healthy shape -- in "
                     "running above out is what fills the queue-depth panel to the left. Topics dispatch once per "
                     "subscriber, so a topic's out legitimately runs at a MULTIPLE of its in (3 consumers on "
                     "esquire.entity.broadcast means out = 3x in), which is not a leak. " + BROKER_DESC))
    p.append(ts("Consumers / producers per destination", 16, 121, 8, "short",
                [tgt("activemq_queue_consumer_count", "consumers {{destination}}"),
                 tgt("activemq_topic_consumer_count", "consumers {{destination}}"),
                 tgt("activemq_queue_producer_count", "producers {{destination}}")],
                desc="A consumer count that DROPS TO ZERO on a live destination is the failure the service-side "
                     "meters cannot report -- the service that should be listening is simply not there, and no "
                     "meter of its own will ever say so because it is not running. Pair this with the depth panel: "
                     "consumers 0 + depth climbing is an outage, in that order."))
    p.append(ts("Broker usage (%)", 0, 129, 8, "percent",
                [tgt("activemq_broker_memory_percent_usage", "memory"),
                 tgt("activemq_broker_temp_percent_usage", "temp")],
                minv=0,
                desc="Percent of the broker's CONFIGURED limits (systemUsage in activemq.xml), not of the disk or "
                     "the pod. MEMORY is the one to watch: the broker is non-persistent, so the bus lives in RAM "
                     "and this is what back-pressures producers when a consumer dies. Temp is the spool memory "
                     "overflows into. There is deliberately NO store line -- with no message store it could only "
                     "ever read 0, and a metric that is always zero reads like a healthy signal."))
    p.append(ts("Broker connections + total messages", 8, 129, 8, "short",
                [tgt("activemq_broker_current_connections_count", "connections"),
                 tgt("activemq_broker_total_message_count", "messages held (all destinations)"),
                 tgt("activemq_broker_total_consumer_count", "consumers"),
                 tgt("activemq_broker_total_producer_count", "producers")],
                desc="Broker-wide totals. Connections dropping is the fleet losing the bus; messages-held is the "
                     "sum of every destination's depth, so it is the single number to alarm on. " + BROKER_DESC))
    p.append(ts("Broker JVM heap (MB) + uptime", 16, 129, 8, "short",
                [tgt('sum(jvm_memory_used_bytes{job="activemq", area="heap"}) / 1024 / 1024', "heap used (MB)"),
                 tgt('activemq_broker_uptime_millis / 1000 / 60', "uptime (min)")],
                desc="The broker's OWN JVM -- free with the agent, since it runs inside that JVM. A broker heap "
                     "climbing to its ceiling is what turns into the memory%% back-pressure on the panel to the "
                     "left. Uptime resetting to zero means the broker restarted, which resets every counter on "
                     "this row (they are broker-lifetime totals, not persisted)."))

    # ---- Latency bands (4-layer request timing decomposed into timers, O1/T5-B) ----
    p.append(row("Latency bands", 137))
    # The RAW timers (each layer as measured), the DERIVED bands (the subtractions those layers imply), and the
    # gateway percentile. Three across, so the Bandwidth row below keeps its y.
    AVG = "sum(rate(esq_%s_seconds_sum[5m])) / sum(rate(esq_%s_seconds_count[5m]))"
    GW_OUTER = AVG % ("gw_outer", "gw_outer")
    GW_INNER = AVG % ("gw_inner", "gw_inner")
    SRV_OUTER = AVG % ("srv_outer", "srv_outer")
    SRV_INNER = AVG % ("srv_inner", "srv_inner")
    # Every band goes through band() -- see the trap note at the top. A subtrahend with no samples (a service not
    # hit yet, a fresh deploy) must make its band read 0, never delete it.
    p.append(ts("Request latency bands -- DERIVED (avg ms)", 8, 138, 8, "ms",
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
    p.append(ts("Request latency bands -- RAW (avg ms by layer)", 0, 138, 8, "ms",
                [tgt("1000 * sum(rate(esq_gw_outer_seconds_sum[5m])) / sum(rate(esq_gw_outer_seconds_count[5m]))",
                     "gw outer (total)"),
                 tgt("1000 * sum(rate(esq_gw_inner_seconds_sum[5m])) / sum(rate(esq_gw_inner_seconds_count[5m]))",
                     "gw inner (proxied)"),
                 tgt("1000 * sum(rate(esq_srv_outer_seconds_sum[5m])) / sum(rate(esq_srv_outer_seconds_count[5m]))",
                     "srv outer (wall)"),
                 tgt("1000 * sum(rate(esq_srv_inner_seconds_sum[5m])) / sum(rate(esq_srv_inner_seconds_count[5m]))",
                     "srv inner (db, capture-gated)")]))
    p.append(ts("Gateway total p95 by route (ms)", 16, 138, 8, "ms",
                [tgt("1000 * histogram_quantile(0.95, sum by (le, route) (rate(esq_gw_outer_seconds_bucket[5m])))",
                     "{{route}}", exemplar=True)],
                desc="Percentile buckets are opt-in: this panel needs "
                     "esquire.observability.metrics.histograms-enabled=true (ESQ_METRICS_HISTOGRAMS). "
                     "With it off the timers still emit count/sum/max, so the avg-by-layer panel beside this one "
                     "stays populated."))
    # ---- Bandwidth (HTTP byte volume off Tomcat's GlobalRequestProcessor, O1/T5-C) ----
    BW_DESC = ("Servlet services only. The gateway runs on Netty (Spring Cloud Gateway), which has no Tomcat "
               "MBean -- so the client-facing bandwidth at the edge is NOT in these panels, only the in-cluster "
               "service traffic. Bytes-in reads 0 for GET-only services (a GET has no request body).")
    p.append(row("Bandwidth", 146))
    p.append(ts("Service HTTP bytes OUT (B/s)", 0, 147, 8, "Bps",
                [tgt("sum by (application, instance) (rate(tomcat_global_sent_bytes_total{%s}[5m]))" % APP,
                     "{{application}} {{instance}}")],
                desc=BW_DESC))
    p.append(ts("Service HTTP bytes IN (B/s)", 8, 147, 8, "Bps",
                [tgt("sum by (application, instance) (rate(tomcat_global_received_bytes_total{%s}[5m]))" % APP,
                     "{{application}} {{instance}}")],
                desc=BW_DESC))
    # The EDGE: the gateway is Netty, so its byte volume comes from reactor-netty, not the Tomcat MBean. This is
    # the client-facing bandwidth. /actuator is excluded -- that traffic is Prometheus scraping the gateway itself
    # (self-monitoring), and it otherwise dwarfs the real client bytes.
    p.append(ts("Gateway EDGE bytes (B/s) -- client-facing", 16, 147, 8, "Bps",
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
    p.append(row("Business -- entity operations", 155))
    p.append(ts("Entity operations (ops/s by op + outcome)", 0, 156, 8, "ops",
                [tgt("sum by (op, outcome) (rate(esq_biz_entity_ops_total{%s}[5m]))" % APP,
                     "{{op}} {{outcome}}")],
                desc="What enyMan actually DID: creates, deletes and moves, by outcome. No free meter can see "
                     "this -- http.server.requests knows the endpoint and the HTTP status, not which KIND of "
                     "entity was acted on nor whether a refusal was an authorization decision. NOTE a MOVE here "
                     "means the command was ACCEPTED (/esq-move answers 202); whether the move SUCCEEDED is the "
                     "next panel, because the work happens off-request on the queue worker."))
    # A queue DEPTH is a count and processed/failed are rates -- two units. On one axis a depth spike to 1000
    # would flatten the rates into the floor and hide exactly the failure you opened the panel for. Two panels.
    p.append(ts("Move queue -- depth (pending)", 8, 156, 4, "short",
                [tgt("sum(esq_biz_move_queue_depth{%s})" % APP, "pending")],
                minv=0,
                desc="The move backlog. /esq-move answers 202 at submit time and the work happens on the queue "
                     "worker, so a rising depth means the worker is not keeping up -- and nothing on the request "
                     "side would tell you."))
    p.append(ts("Move outcome (per s)", 12, 156, 4, "ops",
                [tgt("sum(rate(esq_biz_move_processed_total{%s}[5m]))" % APP, "processed/s"),
                 tgt("(sum(rate(esq_biz_move_failed_total{%s}[5m]))) or vector(0)" % APP, "FAILED/s")],
                minv=0,
                desc="The async half of a move. A move that FAILS on the worker is invisible to the caller (it "
                     "already got its 202) and to every HTTP meter -- this is the only place it shows. FAILED is "
                     "flat at zero on a healthy system; `or vector(0)` keeps it drawing a zero line rather than "
                     "vanishing, because a vanished series is indistinguishable from a broken panel."))
    p.append(ts("Dictionary lookups (by kind)", 16, 156, 8, "ops",
                [tgt("sum by (kind) (rate(esq_biz_dict_lookup_total{%s}[5m]))" % APP, "kind {{kind}}")],
                desc="Which DICTIONARY is being fetched. Not a duplicate of http.server.requests: that meter is "
                     "tagged by URI template (/esq-dict), and the kind is a query param -- so the free meter can "
                     "tell you the endpoint is busy but never which dictionary."))

    # ---- Business: money (pacMan) ----
    p.append(row("Business -- money", 164))
    p.append(ts("Account transactions (tx/s by type + outcome)", 0, 165, 8, "ops",
                [tgt("sum by (type, outcome) (rate(esq_biz_acct_tx_total{%s}[5m]))" % APP,
                     "{{type}} {{outcome}}")],
                desc="The money path: deposits, withdrawals, transfers, by outcome. Both processors report here "
                     "-- the transfer processor OVERRIDES the single one and does not call super, so it needed "
                     "its own meter or every transfer would have been silently missing from this panel."))
    p.append(ts("Transaction latency (avg ms by type)", 8, 165, 8, "ms",
                [tgt(avg_ms("esq_biz_acct_tx_duration_seconds_sum{%s}" % APP,
                           "esq_biz_acct_tx_duration_seconds_count{%s}" % APP, by="type"), "{{type}}")],
                desc="How long a transaction takes end to end inside pacMan, by operation. A transfer is two "
                     "legs and a rate lookup, so it is legitimately dearer than a deposit -- the shape to watch "
                     "is a type getting slower against ITSELF, not one type against another."))
    p.append(ts("FX applied + accounts closed", 16, 165, 8, "ops",
                [tgt("sum(rate(esq_biz_acct_fx_apply_total{%s}[5m]))" % APP, "fx applied/s"),
                 tgt('sum by (purge) (rate(esq_biz_acct_close_total{%s}[5m]))' % APP, "closed ({{purge}})")],
                desc="A conversion rate is only present on the cross-currency leg of a transfer, so a non-null "
                     "convRate IS the FX application. Closures are counted only once the delete has SUCCEEDED "
                     "past the three guards; purge=test-house marks the demo-data path that forces those guards "
                     "open, so a real closure is never confused with a fixture teardown."))

    # ---- Business: identity + token relay (kcMaster, gateway) ----
    p.append(row("Business -- identity + token relay", 173))
    p.append(ts("KeyCloak identity sync (by op + outcome)", 0, 174, 6, "ops",
                [tgt("sum by (op, outcome) (rate(esq_biz_kc_sync_total{%s}[5m]))" % APP,
                     "{{op}} {{outcome}}")],
                desc="Whether Esquire and KeyCloak still AGREE about who exists. The bus meters say a sync "
                     "request arrived; only this says whether the identity was actually brought into line. A "
                     "non-zero error line means the two systems have DRIFTED -- a user Esquire thinks can log "
                     "in, and KeyCloak does not."))
    p.append(ts("KeyCloak sync latency (avg ms)", 6, 174, 6, "ms",
                [tgt(avg_ms("esq_biz_kc_sync_duration_seconds_sum{%s}" % APP,
                           "esq_biz_kc_sync_duration_seconds_count{%s}" % APP), "kc sync")],
                desc="KeyCloak is a SEPARATE SERVER and nothing else times it. Measured around the whole sync "
                     "rather than inside the admin client, so it is the sync's wall time -- but the KC "
                     "round-trip dominates it (the attribute mapping either side is microseconds against "
                     "KeyCloak's milliseconds)."))
    # The hit rate and the acquire cost are TWO UNITS -- a percentage (0..100) and milliseconds. On one axis the
    # percentage owns the scale and the latency line lies flat against zero, unreadable. They are two panels.
    p.append(ts("Token relay -- cache hit rate", 12, 174, 6, "percent",
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
    p.append(ts("Token relay -- KC /token acquire (avg ms)", 18, 174, 6, "ms",
                [tgt(avg_ms("esq_biz_gw_tokenrelay_duration_seconds_sum",
                            "esq_biz_gw_tokenrelay_duration_seconds_count", by="outcome"), "{{outcome}}")],
                desc="What a cache MISS costs: a live round-trip to KeyCloak, an external server, on the "
                     "request's hot path. Multiply it by (100 - hit rate) from the panel on the left to get what "
                     "the relay is actually costing users. Split BY OUTCOME so a failure is still visible without "
                     "putting a second unit on the axis -- an error or a cancelled relay has a duration too, and "
                     "those lines simply do not exist on a healthy system."))

    # ---- Business: cache, keep + permissions (bizTree, dataKeep, cross-cutting) ----
    p.append(row("Business -- cache, keep + permissions", 182))
    p.append(ts("Tree cache -- broadcast dispatch (by outcome)", 0, 183, 8, "ops",
                [tgt("sum by (outcome) (rate(esq_biz_tree_handler_dispatch_total{%s}[5m]))" % APP,
                     "{{outcome}}"),
                 tgt("sum by (outcome) (rate(esq_biz_tree_rebuild_total{%s}[5m]))" % APP,
                     "rebuild ({{outcome}})")],
                desc="What the CACHE did with each broadcast -- applied it, found no handler, found no payload, "
                     "or FAILED. The failed line is the point: the dispatch hub SWALLOWS a handler exception, so "
                     "a handler that blows up leaves the tree silently stale while the bus still counts the "
                     "message as received. Rebuilds should be RARE -- a rising rebuild rate is itself a finding."))
    p.append(ts("Audit keep -- DB writes (by op + outcome)", 8, 183, 8, "ops",
                [tgt("sum by (op, outcome) (rate(esq_biz_keep_write_total{%s}[5m]))" % APP,
                     "{{op}} {{outcome}}")],
                desc="THE DB WRITE at the keep sink -- the one thing the bus meters cannot see. "
                     "messaging.receive.total says the audit event ARRIVED; only this says whether the row was "
                     "actually WRITTEN. An audit event that lands on the bus and then fails to persist is "
                     "exactly the failure that was invisible before. These counts must RECONCILE with the bus "
                     "receive count on the Messaging bus row: a divergence is a real finding."))
    p.append(ts("Permission checks (allow vs DENY)", 16, 183, 8, "ops",
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


# ---------------------------------------------------------------------------------------------------------------
# THE LOGGING DASHBOARD (T9-B). The services dashboard answers "how is the system running". This one answers
# "what did it SAY", which is the question a human actually asks when something has already gone wrong.
#
# Two template variables, and the second is the point of the whole thing: paste a correlationId (== the trace id
# == the Esq-Correlation-ID the gateway settled) and every panel here narrows to that ONE request, across every
# service it touched. That is the query the whole correlation design exists to serve.
# ---------------------------------------------------------------------------------------------------------------

def build_logging_dashboard():
    # TWO TRAPS, both of which made every panel here read "No data" the first time:
    #
    #  1. THE LEVEL IS NOT `level`. The Java services log ECS, where the level is NESTED (log.level), so `| json`
    #     flattens it to log_level -- and the Node BFF logs pino, where `level` is a NUMBER. Neither matches a
    #     `level` filter, so every level-filtered panel came back empty while the unfiltered one worked.
    #     `detected_level` (error/warn/info/unknown) is Loki's own, and the one thing that works across both
    #     formats AND every infra container.
    #  2. BUT detected_level IS STRUCTURED METADATA, NOT AN INDEX LABEL -- so it CANNOT go in the {} stream
    #     selector. `{job=~"esq-.*", detected_level=~"warn|error"}` returns ZERO, silently; it has to be a
    #     PIPELINE filter (`{job=~"esq-.*"} | detected_level =~ "warn|error"`). Same for correlationId.
    #  3. NO `| json` IS NEEDED. correlationId arrives as structured metadata (Alloy extracts it), so it filters
    #     directly. Parsing the line to reach it is both wasted work and the thing that dragged in trap 1.
    #
    # An empty $correlationId must not filter anything out, so the default is a match-everything regex rather
    # than an empty string -- `correlationId = ""` would match NOTHING and the dashboard would look broken.
    cid = '| correlationId =~ "$correlationId"'
    svc = '{%s, container=~"$container", %s}' % (JOB_ANY, ESQ_SERVICES)
    errs = '%s | detected_level =~ "warn|error"' % svc

    p = []
    p.append(row("Volume", 0))
    p.append(ts("Log rate by level (lines/s)", 0, 1, 12, "logs",
                [{"refId": "A", "datasource": DS_LOKI,
                  "expr": 'sum by (detected_level) (rate(%s %s [1m]))' % (svc, cid),
                  "legendFormat": "{{detected_level}}"}],
                desc="The shape to know: error and warn flat at zero, info steady. A step change in error is the "
                     "signal; a step change in info usually means a deploy or a load change, not a fault."))
    p.append(ts("Log rate by service (lines/s)", 12, 1, 12, "logs",
                [{"refId": "A", "datasource": DS_LOKI,
                  "expr": 'sum by (container) (rate(%s %s [1m]))' % (svc, cid),
                  "legendFormat": "{{container}}"}],
                desc="A service that goes SILENT is as interesting as one that gets loud -- a flatline to zero "
                     "here, while the others keep talking, means it stopped doing work (or stopped shipping "
                     "logs), and no error will be raised to tell you."))
    # The Loki panels above are timeseries fed by a Loki datasource -- override the datasource on the panel too,
    # or Grafana renders them against Prometheus and they come back empty.
    for panel in p[-2:]:
        panel["datasource"] = DS_LOKI

    p.append(row("Errors", 10))
    p.append(logs("ERROR + WARN stream", 0, 11, 24,
                  '%s %s' % (errs, cid),
                  h=10,
                  desc="Expand a line: its correlationId carries a 'View trace' link straight into the Tempo "
                       "waterfall (the logs -> trace hop). Empty is healthy."))

    p.append(row("Everything", 21))
    p.append(logs("All log lines", 0, 22, 24,
                  '%s %s' % (svc, cid),
                  h=12,
                  desc="The full stream for the selected services. Paste a correlationId into the variable at the "
                       "top and this becomes the complete story of ONE request across every service it touched -- "
                       "which is the question the whole correlation-id design exists to answer. NOTE what Loki "
                       "actually HAS: the services' per-request INFO/DEBUG goes to the develop/msg FILES (the "
                       "3-tier logging design, additivity=false), so what reaches Loki is the console tier -- "
                       "errors, warnings and startup. A healthy request leaves no service log line here, and "
                       "that is by design, not a gap."))
    return {
        "uid": "esq-logging",
        "title": "Esquire Logging -- volume / errors / one request end-to-end",
        "tags": ["esquire", "o11y", "logs"],
        "timezone": "",
        "schemaVersion": 39,
        "version": 1,
        "refresh": "30s",
        "time": {"from": "now-1h", "to": "now"},
        "templating": {"list": [
            {
                # The picker lists ONLY the Esquire services -- otherwise the dropdown offers Grafana, Tempo and
                # Loki as if they were part of the system being observed.
                "name": "container", "label": "Service", "type": "query", "datasource": DS_LOKI,
                "query": {"label": "container", "stream": "{%s, %s}" % (JOB_ANY, ESQ_SERVICES), "type": 1},
                "refresh": 2, "includeAll": True, "multi": True, "allValue": ".*",
                "current": {"text": "All", "value": "$__all"}, "sort": 1,
            },
            {
                # Free text: paste a correlationId (== traceId == Esq-Correlation-ID) to pin every panel to one
                # request. Default ".*" so an empty box shows everything instead of nothing.
                "name": "correlationId", "label": "correlationId (trace id)", "type": "textbox",
                "query": ".*", "current": {"text": ".*", "value": ".*"},
            },
        ]},
        "panels": p,
    }


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    svc_root = os.path.abspath(os.path.join(here, "..", "..", ".."))   # compose/o11y/grafana -> services
    compose_dir = os.path.join(svc_root, "compose", "o11y", "grafana", "provisioning", "dashboards")
    k8s_dir = os.path.join(svc_root, "k8s", "charts", "infra", "grafana", "dashboards")

    for name, builder in (("esquire-services", build_dashboard),
                          ("esquire-logging", build_logging_dashboard)):
        d = builder()
        for path in (os.path.join(compose_dir, "%s.json" % name), os.path.join(k8s_dir, "%s.json" % name)):
            with open(path, "w") as f:
                json.dump(d, f, indent=1)
            print("wrote", path)


if __name__ == "__main__":
    main()
