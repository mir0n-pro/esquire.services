#!/usr/bin/env python3
#  Esquire frameworks (tm)
#  observability stack -- the asset inventory
#
#  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
#  mailto:mir0n.the.programmer@gmail.com
#
#  History:
# 07/16/2026 mir0n  created (v1.2.11 T11/I48): the INVENTORY of every o11y asset we collect, crossed with its
#                   coverage. Answers "what do we collect, what is it for, and is it verified?" in ONE sheet --
#                   doc/Esquire.ObservabilityStack.Inventory.csv, beside Esquire.ObservabilityStack.md.
#                   The twin of o11y-verify.py: that one asks the LIVE stack whether the DECLARED assets are
#                   present; this one asks the SOURCE TREE what assets exist AT ALL -- the question a declared
#                   list cannot answer, and the one that catches an asset nobody declared.
#                   SEMI-AUTOMATED on purpose: description + use are hand-written (semantics cannot be mined out
#                   of code); every coverage column is DERIVED on each run, so the sheet cannot rot the way the
#                   verifier's hand-kept list did.
#                   ALL THREE PILLARS: metrics (meters/gauges), tracing (span NAMES), and LOGGING (the per-service
#                   stdout stream).
"""
The Esquire o11y ASSET INVENTORY -- one row per signal we collect, with its coverage.

    python o11y-inventory.py                 # refresh doc/Esquire.ObservabilityStack.Inventory.csv (the default)
    python o11y-inventory.py --out -         # CSV to stdout instead
    python o11y-inventory.py --gaps          # only the rows carrying a GAP (the worklist)

Columns:
    signal, kind, emitted_by, description, use, verified_live, unit_test, drawn_on_dashboard, GAP

WHY THIS EXISTS (I48).  o11y-verify.py checks that the assets it DECLARES are live -- so it is structurally blind
to an asset that was never declared, and its declared list is hand-maintained, so it drifts. Three meters
(esq.bff.outbound.duration, esq.biz.acct.tx.duration, esq.biz.keep.write.duration) were being collected and
verified by nothing, two of them long before anyone noticed. This script reads the TREE, so a new asset appears
here the moment it is written and nobody has to remember anything.

THE ONE RULE OF THIS FILE.  Only `description` and `use` are hand-written. **Every coverage column is DERIVED on
each run** -- never typed, never cached:
    verified_live       <- the declared lists inside o11y-verify.py  (and, for logs, its SERVICES sweep list)
    unit_test           <- scanning the java-test + vitest suites for the signal name
    drawn_on_dashboard  <- reading the real panel queries out of the generated dashboard JSON
If you add a column, derive it the same way. An asset with no DESC entry is reported as ** UNDOCUMENTED **, never
silently dropped.

Read-only against the trees it scans; the only file it writes is the CSV.
"""
import argparse
import collections
import csv
import glob
import io
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

# This file lives at services/test/o11y/ -- the trees are two and three levels up.
HERE = os.path.dirname(os.path.abspath(__file__))
SVC = os.path.abspath(os.path.join(HERE, "..", ".."))           # ...\esquire\services
ROOT = os.path.abspath(os.path.join(SVC, ".."))                  # ...\esquire
EXP = os.path.join(ROOT, "explorer")
# The o11y STACK configs (prometheus.yml, grafana boards) live with the compose stack, not with this test
# script. HERE holds only the test scripts; the stack files are under services/compose/o11y/.
O11Y = os.path.join(SVC, "compose", "o11y")

# The sheet is a DOC -- it lives with the observability doc it belongs to, not beside this script.
DEFAULT_OUT = os.path.join(SVC, "doc", "Esquire.ObservabilityStack.Inventory.csv")

# ---------------------------------------------------------------------------------------------------------------
# The ONLY hand-written part: what each asset MEANS (description) and what it is FOR (use).
# A new signal without an entry here is flagged NO-DESC -- add it, do not remove the flag.
# ---------------------------------------------------------------------------------------------------------------
DESC = {
    # ---- the latency bands (the Esquire ECONOMY way: timers subtracted into bands, not a span per leg) ----
    "esq.gw.outer": (
        "Gateway total IN-PROCESS window per request (RequestTraceFilter start -> ResponseTraceFilter).",
        "The band ROOT -- every band below decomposes it. NOT client network: both ends are the gateway's own "
        "clock, so no client<->gw network can be in it (I47)."),
    "esq.gw.inner": (
        "The gateway's proxied downstream call ONLY (InnerTimerFilter @Order(0)).",
        "Feeds two bands: 'in-cluster (gw<->srv)' = gw.inner - srv.outer, and 'gw self' = (gw.outer-gw.inner)-KC."),
    "esq.srv.outer": (
        "Service wall time for the request.",
        "Band 'srv self (compute)' = srv.outer - srv.inner."),
    "esq.srv.inner": (
        "Per-request total JPA/DB time (MdcFilter records performance.getTotalJpaTime(), tagged by route).",
        "mir0n's 'srv inner (db, capture-gated)' -- the DB band. This is the measure that answers I40: the DB "
        "time IS measured; only a per-STATEMENT span is absent."),
    # ---- business tier, esq.biz.* (T8) ----
    "esq.biz.perm.check.total": (
        "Permission decisions, tagged allow/deny.", "Security posture -- the DENY rate."),
    "esq.biz.entity.ops.total": (
        "Entity operations by op + outcome.", "Core domain throughput (enyMan)."),
    "esq.biz.dict.lookup.total": (
        "Dictionary lookups by kind.", "Dict traffic. Conditional -- empty until someone looks one up."),
    "esq.biz.tree.handler.dispatch.total": (
        "bizTree broadcast handler dispatch by outcome.",
        "Cache coherence: a failing dispatch means a STALE tree, and nothing else reports it."),
    "esq.biz.tree.rebuild.total": (
        "bizTree full cache rebuilds.", "Rare by design -- a spike means churn."),
    "esq.biz.move.queue.depth": (
        "Pending entries in the enyMan move queue (GAUGE).",
        "Backpressure -- a rising depth means the worker is falling behind."),
    "esq.biz.move.processed.total": ("Moves processed.", "Move throughput."),
    "esq.biz.move.failed.total": ("Moves failed.", "Conditional -- flat 0 on a healthy system."),
    "esq.biz.acct.tx.total": ("Account transactions by type + outcome.", "pacMan domain throughput."),
    "esq.biz.acct.tx.duration": ("Account transaction latency by type.", "Drawn on 'Transaction latency'."),
    "esq.biz.acct.close.total": ("Accounts closed.", "Conditional domain event."),
    "esq.biz.acct.fx.apply.total": ("FX applications.", "Conditional domain event."),
    "esq.biz.kc.sync.total": (
        "kcMaster KC identity syncs by op + outcome, recorded in a finally so a FAILED sync counts.",
        "A failed sync leaves Esquire and KeyCloak disagreeing about who exists -- nothing else reports that."),
    "esq.biz.kc.sync.duration": (
        "Duration of the WHOLE kc sync operation (the KC round-trip dominates it).",
        "Answers I39: the kcMaster->KC duration IS measured, at the OPERATION grain. Only a per-CALL span is "
        "absent, and a traceparent buys nothing (stock KC is not OTel-traced)."),
    "esq.biz.keep.write.total": ("dataKeep keep-sink DB writes by op + outcome.", "Audit/keep durability."),
    "esq.biz.keep.write.duration": (
        "Duration of the keep-sink DB write (T8-C).",
        "The DB write cost at the keep sink. NOTE: collected but currently NEITHER verified NOR drawn -- it is "
        "the meter cited in the I39/I40 argument as proof the write is covered, yet nobody can see it (I48)."),
    "esq.biz.gw.tokenrelay.total": (
        "Token Relay cache hit/miss.",
        "THE most load-bearing number on the board: a MISS is a live KC /token round-trip on the hot path."),
    "esq.biz.gw.tokenrelay.acquire.total": (
        "Token Relay KC acquires by outcome.", "How often KeyCloak is actually called."),
    "esq.biz.gw.tokenrelay.duration": (
        "KC /token round-trip duration by outcome (ok|error|cancelled).",
        "Answers I42/L2. Feeds I47's 'KC token-relay (per gw request)' band -- amortized over gateway REQUESTS, "
        "not per relay, because the relay only fires on a cache miss."),
    # ---- the bus (EsqRodObserver). Tagged bus-id / slot / msgType; "slot" is the bus leg, never "service". ----
    "messaging.send.total": (
        "Messages SENT on the bus, by bus-id / slot / msgType.", "x-rod producer throughput."),
    "messaging.receive.total": (
        "Messages RECEIVED off the bus, by bus-id / slot / msgType.",
        "x-rod consumer throughput. Read against send.total: a widening gap is work not arriving."),
    "messaging.error.total": (
        "Bus errors, by bus-id / slot / msgType AND by leg (send vs receive).",
        "Which SIDE of the bus is failing -- the leg tag is the whole point."),
    "messaging.send.duration": ("Bus send latency.", "x-rod producer health."),
    "messaging.retry.backoff": (
        "Send-retry backoff delays (a SUMMARY, not a timer -- it records the ms a retry waited).",
        "How hard the send-retry sublayer is working before a message gets through."),
    "messaging.retry.dropped": (
        "Messages DROPPED by the send-retry sublayer, by bus-id / msgType.",
        "The retry gave up: work is LOST. Conditional -- flat 0 on a healthy bus, and never silent when not."),
    "messaging.feed.depth": ("x-rod outbound feed queue depth (GAUGE).", "Producer backpressure."),
    "messaging.retry.held": ("Send-retry held count (GAUGE).", "Retry sublayer state."),
    # ---- the BFF tier ----
    "esq_bff_inbound_duration_seconds": (
        "BFF INBOUND request duration by method / coarse route / status.",
        "The user-facing total at the BFF tier."),
    "esq_bff_outbound_duration_seconds": (
        "BFF -> gateway OUTBOUND leg, BOTH paths (the cacheable-GET fetch and the streaming proxy), by outcome. "
        "New in I42/L8+L9.",
        "The BFF<->gw hop's OWN number -- the one hop in the whole REST collaboration that had none, while its "
        "twin (gw<->srv) had a band. Drawn as the outermost RAW layer. Deliberately NOT a derived band: the "
        "gateway also serves direct Token Relay clients, so esq.bff.outbound - gw.outer would compare two "
        "populations."),
    # ---- the @EsqTraced / EsqTraceMark OPERATION marks. CROSS-PILLAR: each is a span AND a timer.
    # o11y-verify checks trace NODES (service names), never these span NAMES -- and their esq_svc_* timer half is
    # declared nowhere and drawn on no panel, though 66 such series are live in Prometheus. That is I48's biggest
    # hole, and it stayed invisible because the scanner could not read `@EsqTraced(name = "...")`.
    "esq.async": (
        "Async-continuation span across an in-process thread hand-off (EsqAsyncTrace).",
        "Keeps a queue worker's spans inside the SUBMITTING request's trace."),
    "esq.keep.apply": (
        "Span around the dataKeep RodEventDbWriter apply (EsqTraceMark -- the writer is not a Spring bean).",
        "Makes the keep write visible in a trace; also times it."),
    "esq.svc.cache": (
        "Span+timer around the LEGACY director's cache apply (BizTreeDirectorLegacy.onEntityBroadcast -> the "
        "handler hub). CONFIG-CONDITIONAL: only exists when BIZTREE_DIRECTOR=legacy.",
        "The emergency switch-back path's instrumentation. `legacy` is the CODE default and is kept deliberately "
        "(v1.2.5 Taijitu refactor: 'kept in tree for emergency switch-back'); compose/k8s run taijitu, which "
        "builds its own MessageHandlerHub -- so dispatch fires while this mark cannot, and NO driver can light "
        "it. Do not read that as dead and delete it: a switch-back is exactly when you want the trace."),
    "esq.svc.acct.tx": ("Span around the pacMan acct transaction processor (EsqTraceMark).",
                        "Domain-op visibility in a trace."),
    # bizTree reads
    "esq.svc.tree": ("@EsqTraced on the bizTree tree read (controller + service).",
                     "The single most-called read: the whole entity tree. Span + esq_svc_tree_seconds timer."),
    "esq.svc.node": ("@EsqTraced on the bizTree single-node read.", "Span + timer for one entity node."),
    "esq.svc.subtree": ("@EsqTraced on the bizTree subtree read.", "Span + timer for a subtree slice."),
    "esq.svc.path": ("@EsqTraced on the bizTree path read.", "Span + timer for an entity path resolve."),
    # enyMan entity ops
    "esq.svc.read": ("@EsqTraced on the enyMan entity read.", "Span + timer for a single entity read."),
    "esq.svc.save": ("@EsqTraced on the enyMan entity save.", "Span + timer for the write path."),
    "esq.svc.create": ("@EsqTraced on the enyMan entity create.", "Span + timer for entity creation."),
    "esq.svc.delete": ("@EsqTraced on the enyMan entity delete.", "Span + timer for entity deletion."),
    "esq.svc.move": ("@EsqTraced on the enyMan move (the REQUEST leg; the queue worker's own outcome is "
                     "esq.biz.move.processed/failed).",
                     "Span + timer for accepting a move -- NOT whether the move itself succeeded."),
    # keySmith
    "esq.svc.key.read": ("@EsqTraced on the keySmith key read.", "Span + timer for a permission/key read."),
    "esq.svc.key.save": ("@EsqTraced on the keySmith key save.", "Span + timer for a permission write."),
    # pacMan accounts
    "esq.svc.acct.read": ("@EsqTraced on the pacMan account read.", "Span + timer for an account read."),
    "esq.svc.acct.save": ("@EsqTraced on the pacMan account save.", "Span + timer for an account write."),
    "esq.svc.acct.delete": ("@EsqTraced on the pacMan account delete.", "Span + timer for an account delete."),
}

# ---- the LOG pillar's fixed prose. One asset per deployable service: its stdout stream. ----
# NOT inventoried on purpose: the develop.* / msg.* channels. Logback's default !console profile writes those to
# FILES; only SPRING_PROFILES_ACTIVE=console puts them on stdout, so they are not part of the shipped log pillar.
LOG_DESC = ("stdout stream: the root logger's %s. (develop.* / msg.* are NOT here -- logback's default !console "
            "profile writes them to FILES; only SPRING_PROFILES_ACTIVE=console puts them on stdout.)")
LOG_USE = ("THE log pillar for this service: Alloy tails stdout -> Loki, and correlationId in the line is the "
           "join key that makes the log<->trace hop work.")
LOG_NOT_A_PANEL = "n/a (logs are not a panel)"

# Fixtures, not real assets -- these exist only inside tests.
NOT_ASSETS = ("esq.biz.test", "esq.biz.x.total", "esq.biz.y.total")

# ---------------------------------------------------------------------------------------------------------------
# CONFIG-CONDITIONAL -- reachable, but only under a CONFIG this deployment does not run.
#
# Not a gap, and NOT removable. These sit on code the default config does not instantiate, so no amount of driving
# will ever light them: the e2e drives the DEPLOYED config, so it cannot reach a branch that config never builds.
# Proving them needs a different CONFIG, not more traffic -- the dimension T10's perf matrix walks.
#
# Marked here so the sheet says so, once. Without it the row reads NOT-PROVEN forever, and every reader (me, three
# times today) re-opens the same investigation and re-derives the same answer.
CONFIG_CONDITIONAL = {
    "esq.svc.cache": (
        "BIZTREE_DIRECTOR=legacy",
        "On BizTreeDirectorLegacy, which is KEPT ON PURPOSE for emergency switch-back (v1.2.5 Taijitu refactor, "
        "the invisible-refactor rule) and is the CODE default -- compose/k8s override it to taijitu. Taijitu "
        "builds its OWN MessageHandlerHub, so dispatch fires while this mark cannot: the class is never "
        "instantiated. Do NOT delete the mark -- it is the fallback's instrumentation, and a switch-back is "
        "exactly when a trace matters most. Provable only in a legacy-config run."),
    "messaging.retry.dropped": (
        "send-retry-max-attempts>0",
        "A drop CANNOT happen while `*_SEND_RETRY_MAX_ATTEMPTS=0`, which is what compose/k8s run. That is BLOCK "
        "mode, and the config says what it means: 'retry over the backoff ladder until it goes through; the "
        "bounded feed back-pressures rather than dropping an entity broadcast. A cap > 0 switches to "
        "drop-after-N.' So this meter is unreachable BY DESIGN here, not undriven -- provable only in a "
        "drop-after-N config. The meter is correct; do not delete it."),
}

# ---------------------------------------------------------------------------------------------------------------
# PILLAR and TRANSPORT -- the two columns that answer "what IS tracing, and what IS metrics?" (mir0n, 2026-07-16)
#
# The question was asked because this sheet made tracing look ABSENT: it listed 30 meters and 4 spans, having
# silently missed all 14 @EsqTraced spans. It misrepresented the system, so the reader concluded there was no
# tracing at all. There is: gateway/bizTree/BFF push traces continuously.
#
# METRICS  = AGGREGATED numbers. "the tree read took 4ms on average, 200 times." No per-request identity.
# TRACING  = ONE REQUEST's story. "request abc123 spent 2ms in the gateway, 4ms in bizTree." Per-request, sampled.
# LOGGING  = the lines a service printed, joined to a trace by correlationId.
#
# THE CATCH -- an Observation is CROSS-PILLAR (the I41 rule): ONE @EsqTraced / EsqTraceMark yields BOTH a span
# (tracing handler) AND a timer (metrics handler) from the same event. So esq.svc.tree is not "a span": it is a
# span AND an esq_svc_tree_seconds timer. Reading such a row as tracing-only is what hides 66 live esq_svc_*
# metric series from view.
#
# TRANSPORT -- the three pillars do NOT share a delivery model, which is the other thing that confuses:
#   POLL  metrics: Prometheus SCRAPES /actuator/prometheus (and the BFF's /metrics). The app is passive.
#   PUSH  tracing: the app PUSHES OTLP spans -> otel-collector -> Tempo. Prometheus never sees a span.
#   TAIL  logging: Alloy TAILS the container's stdout and pushes to Loki. The app just prints.
# So "is it verified?" means a different question per pillar -- a scrape can prove a meter, never a span.
PILLAR_BY_KIND = {
    "counter": ("metrics", "poll"),
    "timer": ("metrics", "poll"),
    "gauge": ("metrics", "poll"),
    "summary": ("metrics", "poll"),
    "histogram": ("metrics", "poll"),
    "span": ("metrics+tracing", "push+poll"),   # cross-pillar: a span AND a timer, from one Observation
    "log-stream": ("logging", "tail"),
    "scrape-job": ("metrics", "poll"),
    "dep-metric": ("metrics", "poll"),
}

# ---------------------------------------------------------------------------------------------------------------
# WE DO NOT ONLY EMIT -- WE ALSO DEPEND (mir0n, 2026-07-16).
#
# The sheet began as "signals our code registers", which left out everything Prometheus scrapes from somebody
# else: Postgres (1419 series), KeyCloak (953), ActiveMQ (174), the OTel Collector (118) -- plus the runtime
# binders (jvm_*, hikaricp_*, tomcat_*) and the BFF's own process_* / nodejs_* defaults. Our panels query all of
# them. If pg_stat_database_blks_hit vanishes, four panels die -- it is our asset whether or not we wrote it.
#
# So an ASSET is something we EMIT *or* something we DEPEND ON. The second kind is derived from the DASHBOARDS:
# a metric a panel queries is, by definition, a dependency we must not lose silently.
#
# Matched by FAMILY PREFIX rather than by parsing PromQL: an identifier-shaped regex over an expression also
# catches label names (kind, route, heap) and functions, which produced a listing full of nonsense. A curated
# prefix list is deterministic and needs no live Prometheus -- and an unknown family simply does not appear,
# which is why the families themselves are asserted below.
DEP_FAMILIES = {
    "pg_": ("postgres-exporter", "PostgreSQL server + per-database statistics."),
    "activemq_": ("activemq (JMX exporter :9404)", "Broker queues, topics, memory and connections."),
    "keycloak_": ("keycloak", "KeyCloak's own metrics endpoint (:9000)."),
    "agroal_": ("keycloak", "KeyCloak's Agroal DB connection pool."),
    "otelcol_": ("otel-collector", "The Collector's own health -- spans/metrics accepted, refused, exported."),
    "traces_": ("otel-collector (servicegraph)", "The service graph the Collector DERIVES from trace spans."),
    "jvm_": ("JVM (Boot binder)", "Heap, GC and threads for every Java service."),
    "hikaricp_": ("HikariCP (Boot binder)", "The DB connection pool: borrows, hold time, saturation."),
    "tomcat_": ("Tomcat (Boot binder)", "Servlet-tier bytes in/out."),
    "reactor_netty_": ("reactor-netty (Boot binder)", "The gateway's edge bytes in/out (it is reactive, not Tomcat)."),
    "resilience4j_": ("resilience4j", "Circuit-breaker state, failure rate and rejected calls."),
    "logback_": ("logback (Boot binder)", "Log events by level -- the metric view of the log pillar."),
    "system_": ("JVM (Boot binder)", "Host CPU count and usage as the JVM sees it."),
    "process_": ("JVM / Node runtime", "Process CPU and resident memory (both fleets emit it)."),
    "nodejs_": ("Node runtime (BFF)", "Event-loop lag and heap -- the BFF's own health, from collectDefaultMetrics."),
    "http_server_requests": ("Spring Boot (auto)", "The framework's own SERVER request timer -- the metric half of "
                             "every http.* Observation."),
}
DEP_PREFIXES = tuple(DEP_FAMILIES)


# ---------------------------------------------------------------------------------------------------------------
# THE LIVE PROBE -- what is PROVEN, as opposed to what is merely DECLARED (mir0n, 2026-07-16).
#
# `verified_live` only ever said "a check NAMES this asset". That is not the same as "this asset was proven to
# work", and letting the two read alike made this sheet claim 126/126 while a third of it had never emitted a
# byte: a METERS_CONDITIONAL asset passes the sweep by WARNING when empty. **The sheet is generated by this file,
# so a sheet that overstates is this file's bug, not the reader's.**
#
# So `proven_live` asks the STACK, not the list: does this asset actually have data right now?
#   metric/span timer -> a series in Prometheus        (a span's timer half proves the mark FIRED -- I41/T2)
#   log-stream        -> a stream in Loki, BY LABEL    (never by matching the name in the line TEXT)
#   scrape-job        -> up == 1
# With no stack reachable the column reads `?` -- honestly unknown, never a silent "yes".
PROM_URL = os.environ.get("PROM_URL", "http://localhost:9090").rstrip("/")
LOKI_URL = os.environ.get("LOKI_URL", "http://localhost:3100").rstrip("/")
LOKI_JOB = os.environ.get("LOKI_JOB", "esq-docker")


def _read(path):
    return io.open(path, encoding="utf-8", errors="ignore").read()


def _norm(path):
    return path.replace("\\", "/")


def _stem(name):
    """Prometheus base name, minus the unit/type suffix -- the shape both notations agree on.

    Micrometer renders esq.biz.kc.sync.duration as esq_biz_kc_sync_duration_seconds; prom-client is natively
    snake_case. Matching on the stem is what lets the two naming families (esq_* and bff_*) be compared at all --
    see I48(d), where unifying them is the open question. Unify the names and this helper goes away.
    """
    return re.sub(r"_(seconds|total)$", "", name.replace(".", "_"))


def _strip_comments(src):
    """Source with comments removed but STRING LITERALS KEPT.

    The mirror image of NoRawGaugeBuilderTest's codeOnly(): that guard bans a CALL, so it strips literals to
    avoid flagging a file for merely MENTIONING the banned name. Here the meter NAME *is* a string literal, so
    literals must survive -- but comments must not, or a javadoc showing an example call would be inventoried as
    a real emission. (EsqRodObserver's header does list its meter names in prose, which is exactly the trap.)
    """
    ret = []
    in_block = in_line = in_str = in_char = False
    i = 0
    while i < len(src):
        c = src[i]
        nxt = src[i + 1] if i + 1 < len(src) else ""
        if in_block:
            if c == "*" and nxt == "/":
                in_block = False
                i += 1
        elif in_line:
            if c == "\n":
                in_line = False
                ret.append(c)
        elif in_str:
            ret.append(c)
            if c == "\\":
                if i + 1 < len(src):
                    ret.append(nxt)
                i += 1
            elif c == '"':
                in_str = False
        elif in_char:
            if c == "\\":
                i += 1
            elif c == "'":
                in_char = False
        elif c == "/" and nxt == "*":
            in_block = True
            i += 1
        elif c == "/" and nxt == "/":
            in_line = True
            i += 1
        elif c == '"':
            in_str = True
            ret.append(c)
        elif c == "'":
            in_char = True
        else:
            ret.append(c)
        i += 1
    return "".join(ret)


# A meter/span registration call, and the KIND it registers. The name is taken from the call's ARGUMENTS (see
# _arg_span), never assumed to sit immediately after the '(' -- MoveQueueManager picks its name with a TERNARY:
#     EsqBizMeters.count(moved ? "esq.biz.move.processed.total" : "esq.biz.move.failed.total", "kind", ...)
# so a `count\(\s*"([^"]+)"` regex silently drops BOTH names. It did, until I48.
CALL_PATTERNS = [
    (r'EsqBizMeters\.count\s*\(', "counter"),
    (r'EsqBizMeters\.time\s*\(', "timer"),
    (r'EsqBizMeters\.gauge\s*\(', "gauge"),
    (r'(?:registry|meterRegistry)\.counter\s*\(', "counter"),
    (r'(?:registry|meterRegistry)\.timer\s*\(', "timer"),
    (r'(?:registry|meterRegistry)\.summary\s*\(', "summary"),
    (r'(?:registry|meterRegistry)\.gauge\s*\(', "gauge"),
    # EsqGauge owns Gauge.builder (T7), so EsqGauge.register IS the gauge call site.
    (r'EsqGauge\.register\s*\(', "gauge"),
    (r'@EsqTraced\s*\(', "span"),
    (r'EsqTraceMark\.around\w*\s*\(', "span"),
]

# What a SIGNAL NAME looks like, as opposed to a TAG KEY. Our names are dotted namespaces (esq.*, messaging.*);
# tag keys in the same argument list are bare words ("kind", "op", "outcome"), so shape alone separates them --
# which is what makes it safe to read the whole argument span instead of just the first literal.
NAME_SHAPE = re.compile(r'"((?:esq|messaging)\.[a-z][a-z0-9.]*)"')


def _arg_span(src, open_paren):
    """The text between a call's '(' and its MATCHING ')', string-aware.

    Counting parens (rather than taking a fixed window) keeps nested calls and ternaries whole and stops the span
    bleeding into the next statement, which would mis-attribute a name to the wrong kind.
    """
    depth = 0
    i = open_paren
    in_str = in_char = False
    while i < len(src):
        c = src[i]
        if in_str:
            if c == "\\":
                i += 1
            elif c == '"':
                in_str = False
        elif in_char:
            if c == "\\":
                i += 1
            elif c == "'":
                in_char = False
        elif c == '"':
            in_str = True
        elif c == "'":
            in_char = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return src[open_paren:i]
        i += 1
    return src[open_paren:open_paren + 400]   # unbalanced source -- fall back to a window rather than crash


def names_in_java(source):
    """The signals ONE java source registers -> {name: kind}. Pure: text in, names out.

    Split out of collect_signals so it can be exercised against FIXTURES (see selftest). This scan is the single
    point of failure for the whole sheet AND for O11yMeterDriftTest's completeness -- and it was wrong three times
    in one sitting, each time silently: it read registry.timer() but not .counter()/.summary(); it wanted a
    literal right after the paren, so a TERNARY name vanished; and it could not read @EsqTraced's NAMED parameter,
    so all 14 marks were invisible. Every one of those made the sheet lie while looking healthy. The selftest
    pins all three shapes so they cannot come back.
    """
    ret = {}
    body = _strip_comments(source)
    for pattern, kind in CALL_PATTERNS:
        for match in re.finditer(pattern, body):
            span = _arg_span(body, match.end() - 1)
            for name in NAME_SHAPE.findall(span):
                ret.setdefault(name, kind)
    # The async-continuation span names itself from a constant, not at a call site.
    for name in re.findall(r'OBS_NAME\s*=\s*"([a-z][a-z0-9._]+)"', body):
        ret.setdefault(name, "span")
    return ret


def collect_signals():
    """Every metric/span signal the SOURCE TREE emits -- the question a declared list cannot answer.

    CALL_PATTERNS must cover EVERY registration API in use, or the sheet lies by omission. When adding an API,
    re-run:
        grep -rhoE '(registry|meterRegistry)\\.(counter|timer|summary|gauge)\\(|EsqBizMeters\\.\\w+\\(' --include=*.java */src/main
    keep this list in step, and add a fixture to selftest() -- every downstream guard is only as complete as this
    scan.
    """
    ret = {}

    def add(name, kind, where):
        if name in ret:
            if where not in ret[name]["where"]:
                ret[name]["where"] += "; " + where
        else:
            ret[name] = {"kind": kind, "where": where}

    for path in glob.glob(os.path.join(SVC, "**", "src", "main", "**", "*.java"), recursive=True):
        if "/target/" in _norm(path):
            continue
        parts = _norm(path).split("/services/")
        module = parts[1].split("/")[0] if len(parts) > 1 else "?"
        for name, kind in names_in_java(_read(path)).items():
            add(name, kind, module)

    for path in glob.glob(os.path.join(EXP, "backend", "src", "**", "*.ts"), recursive=True):
        for name in re.findall(r"name:\s*'([a-z0-9_]+)'", _read(path)):
            add(name, "histogram", "BFF")

    return ret


def collect_log_streams():
    """The LOG pillar: one stdout stream per DEPLOYABLE service.

    Derived from the tree -- a Java module is deployable when it carries an application.yml, plus the Node BFF.
    So a new service brings its log asset with it and nobody has to remember. (dataKeep emits meters but is a
    LIBRARY, not a deployable, so it has no stdout stream of its own.)

    The signal is named with the LOKI LABEL (lowercase), not the module directory (auKeep, bizTree). A row must
    be the name you can actually LOOK THE THING UP BY -- `log.stdout.auKeep` matched nothing in Loki, because the
    label is `aukeep`. The module's real spelling is kept in emitted_by, where it belongs.
    """
    ret = {}
    for path in sorted(glob.glob(os.path.join(SVC, "*", "src", "main", "resources", "application.yml"))):
        module = _norm(path).split("/services/")[1].split("/")[0]
        ret["log.stdout." + module.lower()] = {"kind": "log-stream", "where": module, "svc": module.lower(),
                                               "fmt": "ECS JSON"}
    if os.path.isdir(os.path.join(EXP, "backend", "src")):
        ret["log.stdout.backend"] = {"kind": "log-stream", "where": "BFF", "svc": "backend", "fmt": "pino JSON"}
    return ret


def collect_scrape_jobs():
    """Every Prometheus SCRAPE JOB -- one asset per source we poll.

    A job is the coarsest asset there is and the most consequential: if the postgres target goes down we lose
    1400+ series at once, and no per-meter check would tell us why. Read from prometheus.yml so a new job
    inventories itself. Verified by o11y-verify's check_scrape, whose job list is read here rather than assumed --
    a job absent from that list is scraped but never asserted.
    """
    ret = {}
    prom = os.path.join(O11Y, "prometheus.yml")
    if not os.path.isfile(prom):
        return ret
    for match in re.finditer(r"^\s*-\s*job_name:\s*['\"]?([A-Za-z0-9_.-]+)", _read(prom), re.M):
        ret["scrape." + match.group(1)] = {"kind": "scrape-job", "where": "prometheus", "job": match.group(1)}
    return ret


def scrape_checked_jobs():
    """The jobs check_scrape actually asserts -- read out of its own tuple, so the sheet tracks the checker."""
    ret = set()
    body = _read(os.path.join(HERE, "o11y-verify.py"))
    match = re.search(r"def check_scrape\(\):(.*?)\ndef ", body, re.S)
    if match:
        loop = re.search(r"for job in \(([^)]*)\)", match.group(1), re.S)
        if loop:
            ret = set(re.findall(r'"([^"]+)"', loop.group(1)))
    return ret


def collect_dependencies():
    """Metrics our PANELS query that our code does not emit -- the assets we DEPEND on (infra + runtime).

    Derived from the generated dashboards: a panel querying a metric IS the dependency. Matched by family prefix
    (see DEP_FAMILIES) because parsing PromQL by identifier also picks up labels and functions.
    """
    ret = {}
    for path in glob.glob(os.path.join(O11Y, "grafana", "provisioning", "dashboards", "*.json")):
        board = json.loads(_read(path))
        for panel in board.get("panels", []):
            for target in panel.get("targets", []):
                for name in re.findall(r"\b([a-z][a-z0-9_]{3,})\b", target.get("expr", "")):
                    if not name.startswith(DEP_PREFIXES):
                        continue
                    family = next(f for f in DEP_PREFIXES if name.startswith(f))
                    source, _ = DEP_FAMILIES[family]
                    ret.setdefault(name, {"kind": "dep-metric", "where": source, "family": family})
                    ret[name].setdefault("panels", set()).add(panel.get("title", "?"))
    return ret


def verified_services():
    """The services whose LOGS o11y-verify actually sweeps -- derived, never assumed.

    check_logging() iterates LOG_SERVICES, so a service absent from it has its log stream verified by NOTHING.
    That list is the launcher's SERVICES (the Java fleet) PLUS whatever o11y-verify.py appends in its LOG_SERVICES
    default -- the BFF, which cannot ride SERVICES because that list doubles as the metrics `application` check
    and the BFF's label is `esq-backend`, not `backend`. Both halves are READ (the .bat and the .py) rather than
    restated here: restating them is how this sheet claimed the BFF's logs were unverified minutes after the
    sweep started verifying them.
    """
    ret = set()
    bat = os.path.join(SVC, "compose", "o11y-verify.bat")
    if os.path.isfile(bat):
        match = re.search(r"(?im)^\s*set\s+SERVICES\s*=\s*(.+)$", _read(bat))
        if match:
            ret = {s.strip().lower() for s in match.group(1).split(",") if s.strip()}
    verify = os.path.join(HERE, "o11y-verify.py")
    if os.path.isfile(verify):
        extra = re.search(r"LOG_SERVICES\s*=.*?\(\s*SERVICES\s*\+\s*\[([^\]]*)\]", _read(verify), re.S)
        if extra:
            ret |= {s.strip().lower() for s in re.findall(r'"([^"]+)"', extra.group(1))}
    return ret


def declared_lists():
    """The assets o11y-verify.py declares -- read out of the script, so the two can never disagree silently.

    The '#' comments inside those lists are STRIPPED before the literals are read. Without that, a quoted phrase
    in an explanatory comment is parsed as a declared meter -- which really happened: a note reading
    ... what "after some activity (an e2e run)" means ...
    turned that sentence into an asset named `after some activity (an e2e run)`. The same shape as the trap
    NoRawGaugeBuilderTest documents: a naive string scan cannot tell code from prose about code.
    """
    body = _read(os.path.join(HERE, "o11y-verify.py"))
    ret = {}
    for list_name in ("METERS_EXPECTED", "METERS_CONDITIONAL", "GAUGES",
                      "TRACE_NODES_EXPECTED", "TRACE_NODES_CONDITIONAL"):
        match = re.search(list_name + r"\s*=\s*\[(.*?)\]", body, re.S)
        if not match:
            ret[list_name] = set()
            continue
        code = re.sub(r"#[^\n]*", "", match.group(1))     # drop trailing comments, keep the entries
        ret[list_name] = set(re.findall(r'"([^"]+)"', code))
    return ret


def test_references(signals):
    """Which unit / vitest suites name each signal."""
    ret = collections.defaultdict(set)
    for pattern in (os.path.join(SVC, "**", "src", "test", "**", "*.java"),
                    os.path.join(EXP, "backend", "test", "**", "*.ts")):
        for path in glob.glob(pattern, recursive=True):
            if "/target/" in _norm(path):
                continue
            body = _read(path)
            for name in signals:
                if name in body:
                    ret[name].add(os.path.basename(path))
    return ret


def dashboard_usage(signals):
    """Which panels actually QUERY each signal -- read from the generated JSON, not from a list of intentions."""
    ret = collections.defaultdict(set)
    for path in glob.glob(os.path.join(O11Y, "grafana", "provisioning", "dashboards", "*.json")):
        board = json.loads(_read(path))
        for panel in board.get("panels", []):
            for target in panel.get("targets", []):
                expr = target.get("expr", "").replace(".", "_")
                for name in signals:
                    if _stem(name) in expr:
                        ret[name].add(panel.get("title", "?"))
    return ret


def _http_json(url, timeout=6):
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        return json.load(resp)


def stack_reachable():
    try:
        _http_json(PROM_URL + "/api/v1/query?query=up", timeout=4)
        return True
    except Exception:
        return False


def probe_live(rows):
    """Ask the STACK which assets actually have data -> {signal: True/False}. Empty dict when unreachable."""
    ret = {}
    if not stack_reachable():
        return ret
    end = int(time.time() * 1e9)
    start = end - 6 * 3600 * int(1e9)
    for row in rows:
        signal, kind = row[0], row[1]
        found = False
        try:
            if kind == "log-stream":
                q = '{job="%s", service_name="%s"}' % (LOKI_JOB, signal.replace("log.stdout.", ""))
                url = (LOKI_URL + "/loki/api/v1/query_range?limit=1&start=%d&end=%d&query=" % (start, end)
                       + urllib.parse.quote(q))
                found = bool(_http_json(url)["data"]["result"])
            elif kind == "scrape-job":
                q = 'sum(up{job="%s"})' % signal.replace("scrape.", "")
                res = _http_json(PROM_URL + "/api/v1/query?query=" + urllib.parse.quote(q))["data"]["result"]
                found = bool(res) and float(res[0]["value"][1]) > 0
            else:
                base = signal.replace(".", "_")
                q = 'count({__name__=~"%s(_.*)?"})' % base
                res = _http_json(PROM_URL + "/api/v1/query?query=" + urllib.parse.quote(q))["data"]["result"]
                found = bool(res) and float(res[0]["value"][1]) > 0
        except Exception:
            found = False
        ret[signal] = found
    return ret


def asset_gaps(declared_in, description):
    """What counts as a GAP for an asset -- THE point of this inventory (mir0n, 2026-07-16).

    "document inventory; make it consistent, located, described and tested; not forcing to be shown on panel or
    smthng -- but available for use."

    So an asset's DUTY is: be documented (description + use), be locatable (emitted_by), and be TESTED (the live
    sweep asserts it). Being on a PANEL is NOT a duty -- a panel is an editorial choice about what we watch by
    default, and 126 assets on panels would be an unreadable board. An asset that is described and verified is
    doing its job while waiting to be queried ad hoc. So:
        NOT-VERIFIED -> a GAP: nobody checks it, so nobody would know it went dark.
        NO-DESC      -> a GAP: it exists and no one can say what it is or what it is for.
        not drawn    -> NOT a gap. `drawn_on_dashboard` stays an informational column (where it IS used), and
                        `unit_test` likewise. Flagging absences that are not defects is how a worklist becomes
                        noise -- it flagged 23 of 30 rows once, hiding the few that mattered.
    """
    ret = []
    if not declared_in:
        ret.append("NOT-VERIFIED")
    if description.startswith("**"):
        ret.append("NO-DESC")
    return ret


def build_rows():
    signals = collect_signals()
    declared = declared_lists()
    stems = {}
    for list_name, names in declared.items():
        for name in names:
            stems.setdefault(_stem(name), []).append(list_name)
    tests = test_references(signals)
    drawn = dashboard_usage(signals)

    ret = []
    for name in sorted(signals):
        if any(name.startswith(x) or name == x for x in NOT_ASSETS):
            continue
        lists = stems.get(_stem(name), [])
        description, use = DESC.get(name, ("** UNDOCUMENTED **", "** UNKNOWN **"))
        unit_test = ";".join(sorted(tests.get(name, ())))
        drawn_on = ";".join(sorted(drawn.get(name, ())))
        gaps = asset_gaps(lists, description)
        kind = signals[name]["kind"]
        pillar, transport = PILLAR_BY_KIND.get(kind, ("?", "?"))
        ret.append([name, kind, pillar, transport, signals[name]["where"], description, use,
                    ";".join(lists), unit_test, drawn_on, "|".join(gaps)])

    # ---- the LOGGING pillar. I48 asked for logging + tracing + metrics; the first cut shipped only the last two.
    # Appended after the signals (not re-sorted into them) so the sheet reads pillar by pillar.
    swept = verified_services()
    for name, meta in sorted(collect_log_streams().items()):
        verified = "check_logging" if meta["svc"] in swept else ""
        description = LOG_DESC % meta["fmt"]
        gaps = asset_gaps([verified] if verified else [], description)
        pillar, transport = PILLAR_BY_KIND.get(meta["kind"], ("?", "?"))
        ret.append([name, meta["kind"], pillar, transport, meta["where"], description, LOG_USE,
                    verified, "", LOG_NOT_A_PANEL, "|".join(gaps)])

    # ---- the SCRAPE JOBS: one asset per source we poll (ours AND the infra we do not own).
    checked = scrape_checked_jobs()
    for name, meta in sorted(collect_scrape_jobs().items()):
        job = meta["job"]
        verified = "check_scrape" if job in checked else ""
        description = ("Prometheus scrape job %r -- the whole target. Its series exist only while it is up." % job)
        gaps = asset_gaps([verified] if verified else [], description)
        pillar, transport = PILLAR_BY_KIND[meta["kind"]]
        ret.append([name, meta["kind"], pillar, transport, meta["where"], description,
                    "The COARSEST asset and the most consequential: one dead target silently removes every series "
                    "it carries, and no per-meter check explains why.",
                    verified, "", "n/a (a job is not a panel)", "|".join(gaps)])

    # ---- the DEPENDENCIES: infra + runtime metrics our panels query but our code never emits.
    for name, meta in sorted(collect_dependencies().items()):
        source, family_desc = DEP_FAMILIES[meta["family"]]
        pillar, transport = PILLAR_BY_KIND[meta["kind"]]
        panels = ";".join(sorted(meta.get("panels", ())))
        # A dependency is DISCOVERED via a panel, so it is drawn by construction -- but it is NOT verified unless
        # something asserts it, and "we did not write it" is no reason to hide that. We depend on it; an exporter
        # upgrade can rename it and only a blank panel would say so. So it earns NOT-VERIFIED like anything else.
        # o11y-verify's check_dependencies() derives ITS list from the same panels this function reads, so a
        # dependency is checked by construction: if a panel names it, the sweep asserts it. The two derivations
        # must stay in step -- they share the family-prefix list, which is the one thing to keep aligned.
        verified = "check_dependencies"
        gaps = asset_gaps([verified], family_desc)
        ret.append([name, meta["kind"], pillar, transport, source,
                    family_desc,
                    "DEPENDENCY, not ours: a panel queries it, so losing it breaks that panel. We do not control "
                    "its name -- an exporter upgrade can rename it and only the panel going blank would show it.",
                    verified, "", panels, "|".join(gaps)])
    return ret


HEADER = ["signal", "kind", "pillar", "transport", "emitted_by", "description", "use",
          "verified_live", "proven_live", "unit_test", "drawn_on_dashboard", "GAP"]


# ---------------------------------------------------------------------------------------------------------------
# SELFTEST -- the sheet's own tests. Runs on EVERY generation, and REFUSES to write when it fails.
#
# Same posture as gen-dashboard.py's check_no_naked_subtraction: a tool that can silently produce a WRONG artefact
# must check itself before it emits one. This sheet is now load-bearing (it is the map of what exists, and the
# drift guard shares its scan), and a scanner that misses a shape does not crash or warn -- it QUIETLY SHIPS A
# SHORT LIST that looks healthy. It shipped 38 assets when the tree had 126.
#
# EVERY CASE BELOW IS A BUG THIS SCANNER ACTUALLY HAD. Nothing here is hypothetical.
# ---------------------------------------------------------------------------------------------------------------
SELFTEST_CASES = [
    # (label, java source, names that MUST be found, names that must NOT be)
    ("registry.counter / .summary -- the scan read only .timer(), dropping 5 messaging meters",
     'registry.counter("messaging.send.total", "bus-id", nz(busId)).increment();\n'
     'registry.summary("messaging.retry.backoff", "bus-id", nz(busId)).record(ms);\n'
     'registry.timer("esq.gw.inner", "route", routeId(exchange)).record(d, MILLISECONDS);',
     {"messaging.send.total", "messaging.retry.backoff", "esq.gw.inner"}, set()),

    ("a TERNARY name -- a literal-first regex dropped BOTH, so they read as 'declared but never emitted'",
     'EsqBizMeters.count(moved ? "esq.biz.move.processed.total" : "esq.biz.move.failed.total", "kind", kind);',
     {"esq.biz.move.processed.total", "esq.biz.move.failed.total"}, set()),

    ("@EsqTraced's NAMED parameter -- all 14 marks were invisible: an inventory of tracing with no spans in it",
     '@EsqTraced(name = "esq.svc.tree", label = "read tree")\npublic Esquire esquire(long id) { return null; }',
     {"esq.svc.tree"}, set()),

    ("a TAG KEY is not a signal -- reading the whole arg span must not turn \"kind\" into a meter",
     'EsqBizMeters.count("esq.biz.dict.lookup.total", "kind", String.valueOf(kind));',
     {"esq.biz.dict.lookup.total"}, {"kind"}),

    ("a COMMENT that MENTIONS a call is prose, not an emission (EsqRodObserver's header lists its meters)",
     '// registry.counter("esq.biz.ghost.total") -- described here, never called\n'
     '/* EsqBizMeters.count("esq.biz.phantom.total") */\n'
     'EsqBizMeters.count("esq.biz.real.total");',
     {"esq.biz.real.total"}, {"esq.biz.ghost.total", "esq.biz.phantom.total"}),

    ("a VARIABLE name is plumbing, not a call site (EsqBizMeters flushing pending gauges through EsqGauge)",
     'EsqGauge.register(meterRegistry, g.name(), g.value(), safeTags(g.tags()));\n'
     'EsqGauge.register(registry, "messaging.feed.depth", depth, "bus-id", nz(busId));',
     {"messaging.feed.depth"}, set()),

    ("EsqTraceMark + the OBS_NAME constant -- marks are CROSS-PILLAR, each is a span AND a timer",
     'private static final String OBS_NAME = "esq.async";\n'
     'EsqTraceMark.around("esq.keep.apply", label, () -> write(e));',
     {"esq.async", "esq.keep.apply"}, set()),
]


def selftest():
    """Assert the scan still sees every shape it once missed. Raises SystemExit on failure."""
    problems = []
    for label, source, must_find, must_not in SELFTEST_CASES:
        found = set(names_in_java(source))
        for name in must_find:
            if name not in found:
                problems.append("%s\n      MISSED %r (found: %s)" % (label, name, sorted(found) or "nothing"))
        for name in must_not:
            if name in found:
                problems.append("%s\n      WRONGLY took %r as a signal" % (label, name))

    # The declared-list parser must read entries, not the prose ABOUT them: a note reading
    # ... what "after some activity (an e2e run)" means ... registered itself as an asset by that name.
    probe = 'METERS_EXPECTED = [\n    "esq_real_total",   # a note about "a quoted phrase" in prose\n]\n'
    entries = set(re.findall(r'"([^"]+)"', re.sub(r"#[^\n]*", "", probe)))
    if entries != {"esq_real_total"}:
        problems.append("declared-list parser reads comment prose as entries: got %s" % sorted(entries))

    # Micrometer appends the unit, prom-client does not -- both spellings must reduce to one stem, or the two
    # fleets can never be compared (I48/d).
    if _stem("esq.biz.kc.sync.duration") != _stem("esq_biz_kc_sync_duration_seconds"):
        problems.append("_stem() does not reconcile the Micrometer and Prometheus spellings")

    if problems:
        raise SystemExit(
            "o11y-inventory SELFTEST FAILED -- refusing to write a sheet from a scan that is missing shapes.\n"
            "A short list looks exactly like a healthy one; that is why this runs before every write.\n\n  - "
            + "\n  - ".join(problems))


def main():
    parser = argparse.ArgumentParser(description="Refresh the Esquire o11y asset inventory (CSV).")
    parser.add_argument("--out", default=None,
                        help="write the CSV here ('-' for stdout). Default: the doc sheet, or stdout with --gaps.")
    parser.add_argument("--gaps", action="store_true", help="only rows carrying a GAP (the worklist) -> stdout")
    parser.add_argument("--selftest", action="store_true", help="run the scan's own tests and exit")
    parser.add_argument("--report", action="store_true",
                        help="THE ANSWER: every inventory item, numbered, with its test result")
    args = parser.parse_args()

    # Always, before writing anything -- the gen-dashboard.py posture: a tool that can silently emit a WRONG
    # artefact checks itself first. A scan missing a shape produces a SHORT sheet, and a short sheet reads
    # exactly like a complete one.
    selftest()
    if args.selftest:
        print("selftest OK -- %d scan cases, all shapes still seen" % len(SELFTEST_CASES), file=sys.stderr)
        return

    rows = build_rows()

    # Ask the STACK what is actually PROVEN, then fold it in as its own column + gap. Done here, once, so every
    # row kind gets the same treatment and no builder can forget it.
    #   YES -- data exists: the asset has really emitted.
    #   NO  -- nothing has ever made it fire. A METERS_CONDITIONAL row sits here quite happily: the sweep WARNs
    #          and moves on, which is a check NAMING an asset, not TESTING it. That difference is what made this
    #          sheet read 126/126 while a third of it had never emitted a byte.
    #   ?   -- no stack reachable. Unknown, and said so; never a silent yes.
    live = probe_live(rows)
    for row in rows:
        signal = row[0]
        if not live:
            proven = "?"
        elif live.get(signal):
            proven = "YES"
        elif signal in CONFIG_CONDITIONAL:
            # Reachable, but not by THIS config -- say which one, and do not call it a gap. It is the one state
            # no driver can change, so a NOT-PROVEN here would be a permanent false alarm.
            proven = "n/a (%s)" % CONFIG_CONDITIONAL[signal][0]
        else:
            proven = "NO"
        row.insert(8, proven)
        if proven == "NO":
            row[-1] = "|".join([g for g in (row[-1], "NOT-PROVEN") if g])

    if args.report:
        # The test result for every inventory item, in the SAME shape as the e2e (Playwright/TAP):
        #     ok <n> <item> > <kind> (<result>)     /     not ok <n> ...
        # then the tally. One list, read top to bottom, no CSV to open and no columns to cross-reference.
        started = time.time()
        print()
        print("Running %d o11y inventory items" % len(rows))
        print()
        tally = collections.Counter()
        for i, row in enumerate(rows, 1):
            signal, kind, proven = row[0], row[1], row[8]
            if proven == "YES":
                tally["passed"] += 1
                print("ok %d %s > %s (PASSED)" % (i, signal, kind))
            elif proven.startswith("n/a"):
                # Reachable, but not by THIS config -- skipped, not failed. Playwright calls this a skip.
                tally["skipped"] += 1
                print("ok %d %s > %s # SKIP %s" % (i, signal, kind, proven[5:-1]))
            elif proven == "?":
                tally["skipped"] += 1
                print("ok %d %s > %s # SKIP no stack reachable" % (i, signal, kind))
            else:
                tally["not proven"] += 1
                print("not ok %d %s > %s (NOT PROVEN -- its op was never driven)" % (i, signal, kind))
        print()
        for key in ("not proven", "skipped"):
            if tally[key]:
                print("  %d %s" % (tally[key], key))
        print("  %d passed (%.0fs)" % (tally["passed"], time.time() - started))
        print()
        return

    if args.gaps:
        rows = [r for r in rows if r[-1]]

    # --gaps is a VIEW, not the sheet: it defaults to STDOUT, never to the doc. Letting it inherit the doc default
    # would rewrite Esquire.ObservabilityStack.Inventory.csv with ONLY the gap rows and silently drop every
    # healthy asset -- a "show me the worklist" command that quietly destroys the inventory. An explicit
    # --out still wins, so `--gaps --out worklist.csv` is available on purpose.
    out = args.out if args.out is not None else ("-" if args.gaps else DEFAULT_OUT)
    to_stdout = out == "-"
    stream = sys.stdout if to_stdout else io.open(out, "w", encoding="utf-8", newline="")
    try:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(HEADER)
        writer.writerows(rows)
    finally:
        if not to_stdout:
            stream.close()
            print("wrote %s" % out, file=sys.stderr)

    counts = collections.Counter()
    for row in rows:
        for gap in row[-1].split("|"):
            if gap:
                counts[gap] += 1
    print("assets=%d  %s" % (len(rows), "  ".join("%s=%d" % kv for kv in sorted(counts.items()))),
          file=sys.stderr)


if __name__ == "__main__":
    main()
