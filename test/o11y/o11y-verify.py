#!/usr/bin/env python3
#  Esquire frameworks (tm)
#  observability stack -- end-to-end verify
#
#  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
#  mailto:mir0n.the.programmer@gmail.com
#
#  History:
# 07/15/2026 mir0n  created (v1.2.11 T11/I20): assert the o11y stack is WIRED end to end -- logging, tracing and
#                   metrics, at every service and every gauge. ONE script; the per-environment launcher
#                   (compose/o11y-verify.bat for docker, k8s/o11y-verify.bat for the cluster) sets ONLY the
#                   addresses, because the stack itself is identical everywhere (same meters, gauges, services)
#                   and only Prometheus/Loki/Tempo live at different URLs. Same shape as e2e-test.bat / e2e-k8s.bat.
"""
Verify the Esquire observability stack is wired end to end, LIVE.

The launcher passes these as environment variables:
  ENVNAME   docker | k8s | oke               (report header only)
  PROM_URL  Prometheus base, e.g. http://localhost:9090
  LOKI_URL  Loki base,       e.g. http://localhost:3100
  TEMPO_URL Tempo base,      e.g. http://localhost:3200   (optional -- WARN if unreachable)
  SERVICES  comma list of app service names (the `application` label), e.g. gateway,biztree,...
  LOKI_JOB  the Loki job label for this env, e.g. esq-docker | esq-k8s

Checks the THREE signals:
  METRICS  every declared meter has a live series (failure/condition meters may be legitimately empty -> WARN,
           not FAIL); every GAUGE reports a real number (not missing, not NaN); every service is scraped up.
  TRACING  the collector is accepting spans and NOT refusing them; every declared trace NODE is emitting spans
           into Tempo -- including the BFF's outbound hop to KeyCloak as its own node (a node whose tracing
           silently went off still scrapes metrics, so only Tempo catches it); the metric->trace exemplar hop
           carries a trace id (or histograms are off, which is reported, not failed).
  CHAIN    one id joins the three signals LIVE: a correlationId read from Loki resolves to a Tempo trace, and
           (when histograms are on) an exemplar's trace id resolves too -- the metric->trace->log hops actually
           CARRY the id, which is the T9-D fault ("dead hop") no config check could see.
  LOGGING  each service's log lines reach Loki; a request id is findable.

Datasource WIRING (Grafana): the stored single-pane links are non-empty and resolve (derived-field url, exemplar
  destination, tracesToLogs query id) -- the url:"" / eaten-${} class, verified against what Grafana STORED. The
  generator (gen-datasources.py) self-refuses the same faults at build time; this is the LIVE twin.

Exit 0 when every HARD check passes; non-zero otherwise. WARN lines never fail the run -- they flag a thing that
is empty for a benign reason (no failures yet, histograms off) and are the place to look when a panel is blank.
"""

import base64
import glob
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# This file lives at services/test/o11y/. The grafana boards it reads STAY with the compose stack, so anchor to
# them explicitly rather than beside this script: services/test/o11y -> ../.. = services -> compose/o11y.
_SVC = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
O11Y = os.path.join(_SVC, "compose", "o11y")

ENV = os.environ.get("ENVNAME", "?")
PROM = os.environ.get("PROM_URL", "").rstrip("/")
LOKI = os.environ.get("LOKI_URL", "").rstrip("/")
TEMPO = os.environ.get("TEMPO_URL", "").rstrip("/")
LOKI_JOB = os.environ.get("LOKI_JOB", "")
SERVICES = [s.strip() for s in os.environ.get("SERVICES", "").split(",") if s.strip()]

# The services whose LOG STREAM must reach Loki. SERVICES is the JAVA fleet -- it doubles as the metrics
# `application` label check, and the BFF cannot join it there because its label is `esq-backend`, not `backend`.
# That accident left the BFF's logs asserted by NOTHING (I48): it is a log stream like any other, and Loki labels
# it service_name="backend". So the log sweep gets its own list -- the fleet PLUS the BFF -- rather than borrowing
# a list that exists for a different purpose. LOG_SERVICES overrides it wholesale if an environment differs.
LOG_SERVICES = [s.strip() for s in os.environ.get("LOG_SERVICES", "").split(",") if s.strip()] \
    or (SERVICES + ["backend"])
# Grafana, for the datasource-wiring check (I29 url:"" class): assert what Grafana STORED, live. admin/admin is
# Grafana's own dev default (the o11y sandbox never changes it); override GRAFANA_USER/GRAFANA_PASS for a secured
# instance. Unset GRAFANA_URL -> the wiring check WARN-skips.
GRAFANA = os.environ.get("GRAFANA_URL", "").rstrip("/")
GRAFANA_USER = os.environ.get("GRAFANA_USER", "admin")
GRAFANA_PASS = os.environ.get("GRAFANA_PASS", "admin")

FAILS = []
WARNS = []

# ---- the declared meters (kept in ONE place; a new meter is added here so the sweep stays honest) --------------
# Prometheus base names (dots -> underscores). SPLIT by whether an EMPTY series is a problem or benign.
# A meter and its TWIN belong in the SAME list: esq.biz.x.total and esq.biz.x.duration are one measurement seen
# two ways, so if the count is EXPECTED the timer is too. The drift that I48 found was exactly this rule being
# broken by hand -- the .total added, the .duration forgotten, twice independently. O11yMeterDriftTest now fails
# the build on a collected meter that is missing here, so the list cannot silently fall behind the code again.
METERS_EXPECTED = [        # present whenever o11y is on and the fleet has served ANY traffic
    "esq_gw_outer_seconds", "esq_gw_inner_seconds", "esq_srv_outer_seconds", "esq_srv_inner_seconds",
    "messaging_send_total", "messaging_receive_total", "messaging_send_duration_seconds",
    "esq_biz_perm_check_total", "esq_biz_entity_ops_total", "esq_biz_tree_handler_dispatch_total",
    "esq_biz_key_ops_total",                      # keySmith's own throughput -- the sign-in handshake reads an
                                                  # access profile, so any authenticated traffic fires it
    "esq_biz_kc_sync_total", "esq_biz_kc_sync_duration_seconds", "esq_biz_keep_write_total",
    "esq_biz_keep_write_duration_seconds",        # I48: twin of keep_write_total (EXPECTED), was drifting
    "http_server_requests_seconds", "esq_bff_inbound_duration_seconds",
    "esq_bff_outbound_duration_seconds",          # I48: the BFF->gw hop (I42/L8+L9). Present after any /api
                                                  # traffic, which is what "after some activity (an e2e run)"
                                                  # means -- so EXPECTED, like its inbound sibling.
]
METERS_CONDITIONAL = [     # legitimately EMPTY until the condition happens -- WARN, never FAIL
    "esq_biz_move_failed_total", "esq_biz_acct_tx_total", "esq_biz_acct_close_total",
    "esq_biz_acct_tx_duration_seconds",           # I48: twin of acct_tx_total (CONDITIONAL), was drifting
    "esq_biz_acct_fx_apply_total", "esq_biz_dict_lookup_total", "esq_biz_tree_rebuild_total",
    "esq_biz_key_identity_total",                 # only when a save actually asks the identity provider for
                                                  # something -- a read-only run never fires it
    "esq_biz_move_processed_total", "esq_biz_gw_tokenrelay_total", "esq_biz_gw_tokenrelay_acquire_total",
    "esq_biz_gw_tokenrelay_duration_seconds",
    "messaging_error_total", "messaging_retry_backoff", "messaging_retry_dropped_total",
    # ---- I48: the @EsqTraced / EsqTraceMark OPERATION marks. -------------------------------------------------
    # These were verified by NOTHING. An Observation is CROSS-PILLAR (I41): each yields a span AND a timer, so
    # asserting the TIMER here proves the Observation FIRED -- which is precisely the T2 trap ("@EsqTraced is
    # inert when the object is `new`ed rather than proxied"). A dead mark loses its span silently, because the
    # SERVICE keeps emitting other spans and the trace-NODE check stays green. This is the cheap catch: no Tempo
    # needed, and it works even in a metrics-only (mesh) deployment.
    # NOTE the span in Tempo is named by the mark's LABEL ("read tree"), not by this name -- the name is the
    # OBSERVATION, which becomes esq_svc_tree_seconds here. Do not look for "esq.svc.tree" in a waterfall.
    # ALL CONDITIONAL, empirically: each fires only when ITS operation runs. A stack where nobody moved an entity
    # has no esq_svc_move, and that is correct, not a fault -- driving one tree read lit up esq_svc_subtree ALONE.
    # (An e2e-driven pass could promote the always-exercised ones to EXPECTED; that has not been measured, so
    # they are not claimed here.)
    "esq_svc_tree_seconds", "esq_svc_node_seconds", "esq_svc_subtree_seconds", "esq_svc_path_seconds",
    "esq_svc_cache_seconds",
    "esq_svc_read_seconds", "esq_svc_save_seconds", "esq_svc_create_seconds", "esq_svc_delete_seconds",
    "esq_svc_move_seconds",
    "esq_svc_key_read_seconds", "esq_svc_key_save_seconds",
    "esq_svc_acct_read_seconds", "esq_svc_acct_save_seconds", "esq_svc_acct_delete_seconds",
    "esq_svc_acct_tx_seconds",
    "esq_async_seconds", "esq_keep_apply_seconds",
]
# Declared, real, and NOT reachable by the config this environment runs -- so "never driven" is the wrong words:
# no driver CAN drive it. Says WHICH config would. (Kept in step with o11y-inventory.py's CONFIG_CONDITIONAL.)
CONFIG_UNREACHABLE = {
    # BizTreeDirectorLegacy's cache-apply mark. `legacy` is the CODE default and is kept for emergency
    # switch-back (v1.2.5 Taijitu refactor), but compose/k8s run taijitu -- which builds its OWN
    # MessageHandlerHub, so dispatch fires while this mark's class is never instantiated.
    "esq_svc_cache_seconds": "BIZTREE_DIRECTOR=legacy",
    # A drop cannot happen while *_SEND_RETRY_MAX_ATTEMPTS=0 (BLOCK mode: "retry ... until it goes through; the
    # bounded feed back-pressures rather than dropping"). A cap > 0 switches to drop-after-N. Unreachable BY
    # DESIGN here -- reporting it as "never driven" would be a permanent false alarm.
    "messaging_retry_dropped_total": "send-retry-max-attempts>0 (drop-after-N)",
}
GAUGES = [                 # MUST report a real number when o11y is on (the wiring; a NaN/absent gauge is a bug)
    "messaging_feed_depth", "messaging_retry_held", "esq_biz_move_queue_depth",
]

# ---- the trace NODES (Tempo). Every participant in the distributed trace must appear as a span emitter once
# o11y is on and the fleet served traffic. The trace service.name (<app>.<instance>, the collector rewrites it)
# is NOT the metric `application` label, so the nodes are declared separately. This is the only check that
# catches a node whose tracing went off while its metrics keep scraping (a BFF whose observability.enabled reset
# on a redeploy, say) -- Prometheus stays green, Tempo goes dark. ----
TRACE_NODES_EXPECTED = [     # on the login+tree-load path -- traced by ANY Esquire activity
    "gateway", "biztree", "esq-backend",
]
TRACE_NODES_CONDITIONAL = [  # traced only on their own op (a KC sync, a permission write, an audit keep) -> WARN when quiet
    "enyman", "pacman", "keysmith", "kcmaster", "aukeep",
]

# A topology-specific REPLACEMENT (v1.2.13): a trace node is a PROCESS -- the collector rewrites service.name to
# <app>.<instance> -- so a profile that composes services into fewer processes has different nodes, not fewer.
# On compact there is no gateway node and no bizTree node; there is a gateward node that is both. The launcher
# states its own fleet, the same way SERVICES already does for the metric side.
_NODES = [s.strip() for s in os.environ.get("TRACE_NODES", "").split(",") if s.strip()]
_NODES_COND = [s.strip() for s in os.environ.get("TRACE_NODES_CONDITIONAL", "").split(",") if s.strip()]
if _NODES:
    TRACE_NODES_EXPECTED = _NODES
if _NODES_COND:
    TRACE_NODES_CONDITIONAL = _NODES_COND

# A topology-specific exclusion (T12): OKE has NO auKeep (audit = DB triggers), so its keep-write meters never
# exist and its auKeep trace node never emits -- asserting them there is a permanent false FAIL. The OKE launcher
# sets EXCLUDE_METERS / EXCLUDE_TRACE_NODES so the same shared script validates the OKE fleet honestly.
_EXCL_M = set(s.strip() for s in os.environ.get("EXCLUDE_METERS", "").split(",") if s.strip())
_EXCL_T = set(s.strip() for s in os.environ.get("EXCLUDE_TRACE_NODES", "").split(",") if s.strip())
if _EXCL_M:
    METERS_EXPECTED = [m for m in METERS_EXPECTED if m not in _EXCL_M]
    METERS_CONDITIONAL = [m for m in METERS_CONDITIONAL if m not in _EXCL_M]
    GAUGES = [g for g in GAUGES if g not in _EXCL_M]
if _EXCL_T:
    TRACE_NODES_EXPECTED = [n for n in TRACE_NODES_EXPECTED if n not in _EXCL_T]
    TRACE_NODES_CONDITIONAL = [n for n in TRACE_NODES_CONDITIONAL if n not in _EXCL_T]
# The BFF's outbound hop to KeyCloak is its OWN node in the /auth waterfall (I27): the BFF opens a CLIENT span
# around each KC round-trip -- issuer discovery, the login token exchange, a token refresh. Discovery runs at BFF
# BOOT (a warm), so at least one KC span is present whenever the BFF started with tracing on -> an EXPECTED node,
# not a login-driven one. This is the node that goes dark when the BFF's observability.enabled quietly resets.
KC_HOP_NAME_RE = "KC .*"


def _get(url, timeout=15):
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.load(r)


def promq(expr):
    url = PROM + "/api/v1/query?query=" + urllib.parse.quote(expr)
    return _get(url)["data"]["result"]


def tempo_traces(traceql, window_s=3600, timeout=15):
    # Recent traces matching a TraceQL selector. Tempo's /api/search is a GET -- the selector goes in the `q`
    # QUERY parameter (a POST body is silently ignored and the default recent set comes back). Raises on a
    # transport error so the caller can tell "Tempo unreachable" (skip) from "reachable, node absent" (fail).
    end = int(time.time())
    start = end - window_s
    url = (TEMPO + "/api/search?limit=20&start=%d&end=%d&q=" % (start, end) +
           urllib.parse.quote(traceql))
    return _get(url, timeout=timeout).get("traces", [])


def tempo_span_count(trace_id):
    # spans in the trace with this id, 0 if Tempo has no such trace (404). Raises on any other transport error.
    try:
        d = _get(TEMPO + "/api/traces/" + urllib.parse.quote(trace_id))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return 0
        raise
    n = 0
    for b in d.get("batches", []):
        for ss in b.get("scopeSpans", []):
            n += len(ss.get("spans", []))
    return n


def loki_has_correlation_id(trace_id):
    # does Loki carry a log line whose correlationId equals this id? (the log<->trace shared-id join). Seeded from
    # a real trace id, so a hit proves the SAME id reached both signals. correlationId is structured metadata, so
    # it is filtered with a `|` expression, not the stream selector.
    end = int(time.time() * 1e9)
    start = end - 6 * 3600 * int(1e9)
    sel = ('job="%s"' % LOKI_JOB) if LOKI_JOB else 'service_name=~".+"'
    q = '{%s} | json | correlationId = `%s`' % (sel, trace_id)
    url = (LOKI + "/loki/api/v1/query_range?limit=1&start=%d&end=%d&query=" % (start, end) +
           urllib.parse.quote(q))
    try:
        return any(s.get("values") for s in _get(url)["data"]["result"])
    except Exception:
        return False


def prom_exemplar_trace_ids(bucket, window_s=3600):
    # trace ids carried by the exemplars on a histogram bucket (the metric->trace hop). Empty when histograms are
    # off (no buckets) or nothing was sampled yet.
    start = int(time.time()) - window_s
    url = (PROM + "/api/v1/query_exemplars?query=" + urllib.parse.quote(bucket) +
           "&start=%d&end=%d" % (start, int(time.time())))
    ids = []
    try:
        for series in _get(url).get("data", []):
            for ex in series.get("exemplars", []):
                tid = ex.get("labels", {}).get("trace_id") or ex.get("labels", {}).get("traceID")
                if tid and tid not in ids:
                    ids.append(tid)
    except Exception:
        pass
    return ids


def _get_auth(url, user, password, timeout=15):
    tok = base64.b64encode(("%s:%s" % (user, password)).encode()).decode()
    req = urllib.request.Request(url, headers={"Accept": "application/json", "Authorization": "Basic " + tok})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.load(r)


def series_count(name):
    # count distinct series whose __name__ starts with `name` (covers _total/_sum/_count/_bucket suffixes)
    r = promq('count(count by (__name__, application, bus_id, instance) ({__name__=~"%s.*"}))' % name)
    return int(float(r[0]["value"][1])) if r else 0


def ok(msg):
    print("  PASS  " + msg)


def warn(msg):
    WARNS.append(msg)
    print("  WARN  " + msg)


def fail(msg):
    FAILS.append(msg)
    print("  FAIL  " + msg)


def head(title):
    print("\n=== %s ===" % title)


def check_scrape():
    head("METRICS -- scrape health (every target up)")
    # EVERY job in prometheus.yml belongs here. otel-servicegraph was scraped but asserted by nothing (I48) --
    # and it is the one whose absence is invisible: it carries the trace-derived service graph, so when it is
    # empty a topology panel just draws nothing and no other check notices.
    for job in ("esquire-services", "esquire-bff", "keycloak", "postgres", "activemq", "otel-collector",
                "otel-servicegraph"):
        r = promq('sum(up{job="%s"})' % job)
        n = int(float(r[0]["value"][1])) if r else 0
        (ok if n > 0 else fail)("scrape job %-18s %d target(s) up" % (job, n))
    for svc in SERVICES:
        present = len(promq('group by (application) ({application="%s"})' % svc)) > 0
        (ok if present else fail)("service reporting metrics: application=%s" % svc)


def check_meters():
    head("METRICS -- every declared meter has a live series")
    for m in METERS_EXPECTED:
        n = series_count(m)
        (ok if n > 0 else fail)("meter %-42s %d series" % (m, n))
    for m in METERS_CONDITIONAL:
        n = series_count(m)
        if n > 0:
            # PROVEN: it has data. Say so plainly and say NOTHING about conditions -- the caveat used to be glued
            # into the format string, so a meter with 3 series PASSED while printing "empty is OK: condition not
            # met". That reads as "nobody actually checked this", which is the opposite of what happened, and it
            # made a verified fleet look unverified.
            ok("meter %-42s %d series  PROVEN" % (m, n))
        elif m in CONFIG_UNREACHABLE:
            # Reachable, but not by THIS config -- so "never driven" would be a lie: no driver CAN drive it.
            # Say which config, once, or the next reader re-opens the investigation (this one cost three rounds).
            warn("meter %-42s 0 series -- n/a in this config (needs %s)" % (m, CONFIG_UNREACHABLE[m]))
        else:
            # NOT proven. Nothing has driven this meter's condition -- which is legitimate (a stack where nobody
            # moved an entity has no esq.biz.move.*), but it is NOT a pass: nothing here was tested. Say that.
            warn("meter %-42s 0 series -- NOT PROVEN (its op was never driven; o11y-test.bat drives what it can)"
                 % m)


def check_gauges():
    head("METRICS -- every gauge reports a real number (not missing, not NaN)")
    for g in GAUGES:
        r = promq(g)
        if not r:
            fail("gauge %-30s ABSENT (never registered?)" % g)
        else:
            nan = any(v[1] in ("NaN", "+Inf", "-Inf") for v in [s["value"] for s in r])
            (fail if nan else ok)("gauge %-30s %d series, %s" % (g, len(r), "has NaN!" if nan else "all numeric"))


def _base(name):
    for suf in ("_seconds", "_total"):
        if name.endswith(suf):
            return name[:-len(suf)]
    return name


# A business / bus meter groups by SMALL enums (op / outcome / kind / type / result / bus-id / slot) times the
# fleet (application x instance). The one thing that would blow that up is an unbounded VALUE -- an entity id, an
# account id, a correlationId -- leaking into a tag, which turns a meter into a series explosion (I21 / I6). The
# enum SHAPE (series ignoring the histogram `le` buckets and the per-replica labels) stays small; measured, the
# widest is a few dozen. Anything past this bound means an id got into a label.
CARD_LIMIT = 64


def check_cardinality():
    head("METRICS -- label cardinality is BOUNDED (no id leaked into a tag) [I21]")
    bases = sorted(set(_base(m) for m in (METERS_EXPECTED + METERS_CONDITIONAL + GAUGES)
                       if m.startswith(("esq_biz", "esq_gw", "esq_srv", "messaging"))))
    for b in bases:
        # enum shape: distinct series with the histogram le buckets and the per-replica labels collapsed away
        r = promq('count(count without (le, instance, pod, job) ({__name__=~"%s.*"}))' % b)
        n = int(float(r[0]["value"][1])) if r else 0
        if n == 0:
            continue
        if n > CARD_LIMIT:
            fail("meter %-42s enum-shape cardinality %d > %d -- an id in a tag?" % (b, n, CARD_LIMIT))
        else:
            ok("meter %-42s enum-shape cardinality %d" % (b, n))
    tot = promq('count({__name__=~"esq_biz_.*"})')
    if tot:
        print("  INFO  total esq.biz.* head series: %s (the histogram le buckets are the bulk when "
              "histograms-enabled=true)" % tot[0]["value"][1])


def check_tracing():
    head("TRACING -- collector accepting (not refusing) + the metric->trace exemplar hop")
    acc = promq("sum(otelcol_receiver_accepted_spans_total)")
    ref = promq("sum(otelcol_receiver_refused_spans_total)")
    accepted = float(acc[0]["value"][1]) if acc else 0
    refused = float(ref[0]["value"][1]) if ref else 0
    (ok if accepted > 0 else warn)("collector accepted spans: %d (0 = no traces flowed since start)" % accepted)
    (ok if refused == 0 else fail)("collector refused spans: %d (any > 0 = the hub is DROPPING traces)" % refused)
    # every declared trace NODE must be emitting spans into Tempo. A node whose tracing went off still scrapes
    # metrics (check_scrape stays green), so this Tempo sweep is the ONLY place that catch is made -- including
    # the BFF's outbound KeyCloak hop as its own node (I27).
    if not TEMPO:
        warn("TEMPO_URL not set -- skipping the per-node trace checks")
    else:
        reachable = True
        try:
            tempo_traces("{ }")
        except Exception as e:
            reachable = False
            warn("Tempo unreachable at %s (%s) -- skipping the per-node trace checks" % (TEMPO, e))
        if reachable:
            for node in TRACE_NODES_EXPECTED:
                n = len(tempo_traces('{ resource.service.name =~ "%s.*" }' % node))
                (ok if n > 0 else fail)("trace node %-12s %d recent trace(s)" % (node, n))
            for node in TRACE_NODES_CONDITIONAL:
                n = len(tempo_traces('{ resource.service.name =~ "%s.*" }' % node))
                # Same fix as check_meters: the caveat belongs on the QUIET case only. Printed on a node that HAS
                # traces, "quiet is OK: op not exercised" reads as "this was not really checked" -- while it was.
                if n > 0:
                    ok("trace node %-12s %d recent trace(s)  PROVEN" % (node, n))
                else:
                    warn("trace node %-12s 0 recent trace(s) -- NOT PROVEN (its op was never driven)" % node)
            kc = len(tempo_traces('{ name =~ "%s" }' % KC_HOP_NAME_RE))
            (ok if kc > 0 else fail)("trace node %-12s %d recent trace(s) (BFF->KeyCloak CLIENT span; boot warm "
                                     "guarantees >=1 when the BFF traces)" % ("BFF->KC hop", kc))
    # exemplar hop: histograms carry a trace id on their bucket samples. Off histograms -> no exemplars, reported.
    hist = series_count("esq_gw_outer_seconds_bucket") + series_count("http_server_requests_seconds_bucket")
    if hist == 0:
        warn("no histogram buckets -> exemplars are OFF (esquire.observability.metrics.histograms-enabled=false). "
             "The metric->trace jump and p95 panels are dark by design until it is turned on (see I15).")
    else:
        try:
            start = int(time.time()) - 3600
            url = (PROM + "/api/v1/query_exemplars?query=" +
                   urllib.parse.quote("esq_gw_outer_seconds_bucket") +
                   "&start=%d&end=%d" % (start, int(time.time())))
            ex = _get(url).get("data", [])
            n = sum(len(e.get("exemplars", [])) for e in ex)
            (ok if n > 0 else warn)("exemplars on esq_gw_outer buckets: %d (0 = on, but no request sampled one yet)" % n)
        except Exception as e:
            warn("could not read exemplars: %s" % e)


def check_chain():
    head("CHAIN -- one id joins metric -> trace -> log, LIVE (the single-pane hops actually carry) [I28/I29]")
    if not TEMPO:
        warn("TEMPO_URL not set -- skipping the chain-join checks")
        return
    # log <-> trace: seed from a GATEWAY trace (it has both a span AND a request log line), then confirm Loki
    # carries that trace id as a correlationId. Seeding from a real trace -- not from arbitrary newest log lines --
    # avoids the ids that every request settles on an UNTRACED path (which log but never become a trace). Always
    # runnable (needs no histograms).
    if not LOKI:
        warn("LOKI_URL not set -- skipping the log<->trace join")
    else:
        # FLUSH RACE: a trace becomes Tempo-SEARCHABLE before its log line is Loki-queryable (Alloy ships on its
        # own interval, and Tempo's ingester answers search ahead of the by-id read). Seeding from the very newest
        # traces can therefore find a trace whose log has not landed yet and read a WORKING join as broken. Re-seed
        # and retry a few times with a short wait so the check reflects the join, not the flush timing (T12). Each
        # pass re-fetches -- newer traces AND newer logs both settle -- so a hit only needs ONE aligned pair.
        seeds = []
        hit = None
        for _attempt in range(5):
            seeds = tempo_traces('{ resource.service.name =~ "gateway.*" }') or tempo_traces("{ }")
            hit = next((t["traceID"] for t in seeds if loki_has_correlation_id(t["traceID"])), None)
            if hit or not seeds:
                break
            time.sleep(3)
        if not seeds:
            warn("no trace in Tempo to seed the log<->trace join (no traffic yet?) -- unproven")
        elif hit:
            ok("log<->trace: trace id %s... is carried by a Loki log line's correlationId -- logs and traces "
               "share the id" % hit[:16])
        else:
            fail("log<->trace: none of %d gateway trace id(s) appear as a Loki correlationId after retries -- logs "
                 "and traces are NOT sharing the id (the join is broken, not a flush lag)" % len(seeds))
    # metric -> trace (the exemplar leg): gated on histograms. When on, an exemplar's trace_id MUST resolve in
    # Tempo -- this is the exact hop that was DEAD on k8s for a sprint (T9-D) with nothing to catch it.
    if not PROM:
        return
    hist = series_count("esq_gw_outer_seconds_bucket") + series_count("http_server_requests_seconds_bucket")
    if hist == 0:
        warn("histogram buckets OFF -> the metric->trace exemplar leg cannot be exercised (I15: histograms-enabled "
             "is off, costs 2.3x scrape). This IS the T9-D blind spot when histograms are MEANT to be on -- the "
             "guard runs the moment they are.")
    else:
        tids = (prom_exemplar_trace_ids("esq_gw_outer_seconds_bucket") or
                prom_exemplar_trace_ids("http_server_requests_seconds_bucket"))
        if not tids:
            warn("histograms on but no exemplar carries a trace_id yet (no request sampled one) -- metric->trace "
                 "leg unproven this run")
        else:
            hit = next(((t, c) for t in tids for c in [tempo_span_count(t)] if c > 0), None)
            if hit:
                ok("metric->trace: exemplar trace_id %s... resolves to a Tempo trace (%d spans)" % (hit[0][:16], hit[1]))
            else:
                fail("metric->trace: %d exemplar trace_id(s) but NONE resolve in Tempo -- the exemplar hop is DEAD "
                     "(the T9-D fault)" % len(tids))


def check_datasources():
    head("DATASOURCE WIRING -- what Grafana STORED is non-empty + resolves (I29 url:'' class, LIVE)")
    if not GRAFANA:
        warn("GRAFANA_URL not set -- skipping the datasource wiring checks (the generator self-lint still guards "
             "the source; see gen-datasources.py verify_wiring)")
        return
    try:
        ds = _get_auth(GRAFANA + "/api/datasources", GRAFANA_USER, GRAFANA_PASS)
    except Exception as e:
        warn("Grafana unreachable/unauthorized at %s (%s) -- skipping datasource wiring checks" % (GRAFANA, e))
        return
    uids = set(d.get("uid") for d in ds)
    by_type = {}
    for d in ds:
        by_type[d.get("type")] = d

    def resolves(u):
        return u in uids

    # logs -> trace
    loki_ds = by_type.get("loki")
    if not loki_ds:
        fail("no Loki datasource provisioned")
    else:
        dfs = loki_ds.get("jsonData", {}).get("derivedFields", [])
        if not dfs:
            fail("Loki has no derivedFields stored -- the logs->trace link is absent")
        for f in dfs:
            good = bool(f.get("url")) and "{__value.raw}" in f.get("url", "") and resolves(f.get("datasourceUid"))
            (ok if good else fail)("logs->trace: Loki derivedField %r url=%r uid=%r"
                                   % (f.get("name"), f.get("url"), f.get("datasourceUid")))
    # trace -> logs (the eaten-${} lands here as an empty-token query)
    tempo_ds = by_type.get("tempo")
    if not tempo_ds:
        fail("no Tempo datasource provisioned")
    else:
        ttl = tempo_ds.get("jsonData", {}).get("tracesToLogsV2", {})
        good = resolves(ttl.get("datasourceUid")) and "{__span.traceId}" in ttl.get("query", "")
        (ok if good else fail)("trace->logs: Tempo tracesToLogsV2 uid=%r query carries the span id: %s"
                               % (ttl.get("datasourceUid"), "{__span.traceId}" in ttl.get("query", "")))
        smap = tempo_ds.get("jsonData", {}).get("serviceMap", {})
        (ok if resolves(smap.get("datasourceUid")) else fail)("service-map: Tempo serviceMap uid=%r"
                                                              % smap.get("datasourceUid"))
    # metrics -> trace
    prom_ds = by_type.get("prometheus")
    if not prom_ds:
        fail("no Prometheus datasource provisioned")
    else:
        dests = prom_ds.get("jsonData", {}).get("exemplarTraceIdDestinations", [])
        good = bool(dests) and all(resolves(d.get("datasourceUid")) for d in dests)
        (ok if good else fail)("metrics->trace: Prometheus exemplarTraceIdDestinations=%r" % dests)


def check_dependencies():
    """Every INFRA/RUNTIME metric our panels query must actually exist (I48).

    We do not own these names -- pg_*, activemq_*, jvm_*, hikaricp_*, otelcol_*, nodejs_* come from exporters and
    binders. That is exactly why they need asserting: an exporter upgrade renames one, and the ONLY symptom is a
    panel that quietly draws nothing. Nobody reads a blank panel as a defect.

    The list is DERIVED from the generated dashboards -- the panels ARE the dependency declaration, so a new panel
    brings its dependency into this check with nobody maintaining a list. (Deriving it from a hand-kept list is
    the very drift I48 exists to remove.)

    Most are infra: when the target is up, the series exist -- a missing one FAILS, because unlike our own
    conditional meters there is no "it has not happened yet".

    TRAFFIC_DERIVED is the exception, and it caught me out: traces_service_graph_* is not scraped from a running
    thing, it is CONNECTED from spans -- the Collector's servicegraph builds it out of client/server span PAIRS.
    On an idle stack it is legitimately empty, and FAILing on that is crying wolf about a healthy system. (It read
    "NO series" for an hour and was filed as a dead panel; one e2e run produced 15 series -- user->esq-backend,
    gateway->biztree, enyman->aukeep -- the whole topology. The stack was idle, not broken.)
    """
    head("DEPENDENCIES -- infra/runtime metrics our panels rely on still exist [I48]")
    families = ("pg_", "activemq_", "keycloak_", "agroal_", "otelcol_", "traces_", "jvm_", "hikaricp_",
                "tomcat_", "reactor_netty_", "resilience4j_", "logback_", "system_", "process_", "nodejs_",
                "http_server_requests")
    boards = os.path.join(O11Y, "grafana", "provisioning", "dashboards")
    if not os.path.isdir(boards):
        warn("dashboards not found at %s -- skipping the dependency checks" % boards)
        return
    wanted = set()
    for path in glob.glob(os.path.join(boards, "*.json")):
        try:
            board = json.load(io.open(path, encoding="utf-8"))
        except Exception as e:
            warn("could not read %s: %s" % (os.path.basename(path), e))
            continue
        for panel in board.get("panels", []):
            for target in panel.get("targets", []):
                for name in re.findall(r"\b([a-z][a-z0-9_]{3,})\b", target.get("expr", "")):
                    if name.startswith(families):
                        wanted.add(name)
    if not wanted:
        warn("no panel dependencies found -- the extraction is broken, not the stack")
        return
    # DERIVED from traffic, not scraped from a target -> empty is legitimate on an idle stack. WARN, never FAIL.
    traffic_derived = ("traces_",)
    missing = sorted(n for n in wanted if series_count(n) == 0)
    for n in missing:
        if n.startswith(traffic_derived):
            warn("panel dependency %s has no series -- it is DERIVED from spans (the Collector's servicegraph "
                 "connector builds it from client/server span pairs), so an idle stack is empty and that is "
                 "correct. Drive traffic (an e2e run) before reading anything into this." % n)
        else:
            fail("panel dependency has NO series: %s (renamed by an exporter upgrade? target down?)" % n)
    hard = [n for n in missing if not n.startswith(traffic_derived)]
    ok("panel dependencies present: %d of %d" % (len(wanted) - len(hard), len(wanted)))


def _panel_targets(title_prefix):
    """(legend, expr) of every target on the first panel whose title starts with title_prefix."""
    ret = []
    boards = os.path.join(O11Y, "grafana", "provisioning", "dashboards")
    for path in glob.glob(os.path.join(boards, "*.json")):
        try:
            board = json.load(io.open(path, encoding="utf-8"))
        except Exception:
            continue
        for panel in board.get("panels", []):
            if panel.get("title", "").startswith(title_prefix):
                for t in panel.get("targets", []):
                    ret.append((t.get("legendFormat", "?"), t.get("expr", "")))
                if ret:
                    return ret
    return ret


def _scalar(expr):
    """The single value of a scalar PromQL expression, or None when it has no data (idle stack)."""
    ret = None
    try:
        res = promq(expr)
        if res:
            v = float(res[0]["value"][1])
            ret = None if v != v else v          # NaN -> None: honestly "no data", not a number
    except Exception:
        ret = None
    return ret


def check_bands():
    """The latency BANDS resolve, stay non-negative, and ACCOUNT FOR gw.outer end to end (I48 phase c).

    Nothing checked the bands before, and that is exactly where I47 hid: a band drawn as "net (client <-> gw)"
    that was really the gateway's self overhead WITH the KeyCloak call inside it -- so a slow KC read as a slow
    NETWORK, for months, with every test green. A NAME cannot be asserted by a machine. But three things can:

      1. the band RESOLVES -- an empty subtrahend silently deletes a band (the trap band()/safe() exist for);
      2. the band is not meaningfully NEGATIVE -- small dips are clock/rounding skew and are expected (the panel
         sets minv=None on purpose), but a real negative means two DIFFERENT POPULATIONS are being subtracted;
      3. the bands SUM TO gw.outer -- the decomposition CLOSES.

    (3) is the load-bearing one. It is what makes a wrong band impossible to add quietly: the BFF<->gw network
    band was deliberately NOT added because esq.bff.outbound covers only what the BFF sends while gw.outer counts
    direct Token Relay clients too -- had it been added, this check would have failed. The arithmetic enforces the
    rule that the comment can only state.
    """
    head("BANDS -- the latency decomposition resolves and closes on gw.outer [I48/I47]")
    targets = _panel_targets("Request latency bands -- DERIVED")
    if not targets:
        fail("the DERIVED latency-bands panel was not found -- renamed? Then this check guards nothing.")
        return

    outer = _scalar("1000 * (sum(rate(esq_gw_outer_seconds_sum[5m])) / sum(rate(esq_gw_outer_seconds_count[5m])))")
    if outer is None:
        warn("no gateway traffic in the rate window -- skipping the band checks (they need live requests, the "
             "same contract as the rest of this sweep)")
        return

    total = 0.0
    resolved = 0
    for legend, expr in targets:
        v = _scalar(expr)
        if v is None:
            fail("band %-38s does NOT resolve (empty) -- a band deleted by an empty subtrahend is invisible on "
                 "the panel, not loud" % legend)
            continue
        resolved += 1
        total += v
        # -0.5ms of tolerance: the timers are stamped by different clocks in different processes.
        if v < -0.5:
            fail("band %-38s is NEGATIVE (%.3f ms) -- two different populations are being subtracted, or the "
                 "layers are not nested the way the panel claims" % (legend, v))
        else:
            ok("band %-38s %8.3f ms" % (legend, v))

    if resolved == len(targets):
        drift = abs(outer - total)
        if drift <= max(0.05, outer * 0.01):
            ok("bands CLOSE on gw.outer: sum %.3f ms vs %.3f ms (drift %.4f)" % (total, outer, drift))
        else:
            fail("bands do NOT close: sum %.3f ms vs gw.outer %.3f ms (drift %.3f ms). Every millisecond of the "
                 "gateway's window must be accounted for by exactly one band -- a band was added that does not "
                 "belong to this decomposition, or one was removed." % (total, outer, drift))


def check_logging():
    head("LOGGING -- log lines reach Loki, per service, and a request id is findable")
    if not LOKI:
        warn("LOKI_URL not set -- skipping the logging checks")
        return
    end = int(time.time() * 1e9)
    start = end - 6 * 3600 * int(1e9)
    # Select by the service_name LABEL, not by matching the name in the line TEXT (I48).
    # `{job="esq-docker"} |= "gateway"` proves only that SOME line CONTAINS the word "gateway" -- a bizTree line
    # reading "connecting to gateway" passes the gateway check while the gateway's own logs are missing entirely.
    # It asserts a coincidence. Loki already labels every stream service_name=<service>, so ask for the stream.
    for svc in LOG_SERVICES:
        try:
            sel = ('job="%s", ' % LOKI_JOB) if LOKI_JOB else ""
            q = '{%sservice_name="%s"}' % (sel, svc)
            url = (LOKI + "/loki/api/v1/query_range?limit=1&start=%d&end=%d&query=" % (start, end) +
                   urllib.parse.quote(q))
            res = _get(url)["data"]["result"]
            (ok if res else fail)("Loki has the log STREAM of %s" % svc)
        except Exception as e:
            warn("Loki query for %s failed: %s" % (svc, e))
    # a request id (correlationId) must be findable -- the log<->trace join key
    try:
        q = '{%s} | json | correlationId != ``' % (('job="%s"' % LOKI_JOB) if LOKI_JOB else 'service_name=~".+"')
        url = (LOKI + "/loki/api/v1/query_range?limit=1&start=%d&end=%d&query=" % (start, end) +
               urllib.parse.quote(q))
        res = _get(url)["data"]["result"]
        (ok if res else warn)("a log line carries a correlationId (the log<->trace join key)")
    except Exception as e:
        warn("Loki correlationId query failed: %s" % e)


def main():
    print("Esquire o11y-verify  [env=%s]  Prometheus=%s" % (ENV, PROM or "<unset>"))
    if not PROM:
        print("PROM_URL is not set -- nothing to check. Run me through a launcher.")
        sys.exit(2)
    check_scrape()
    check_meters()
    check_gauges()
    check_dependencies()
    check_bands()
    check_cardinality()
    check_tracing()
    check_chain()
    check_datasources()
    check_logging()
    print("\n=== SUMMARY [env=%s] ===" % ENV)
    print("  %d FAIL, %d WARN" % (len(FAILS), len(WARNS)))
    if FAILS:
        print("  FAILURES:")
        for f in FAILS:
            print("    - " + f)
    sys.exit(1 if FAILS else 0)


if __name__ == "__main__":
    main()
