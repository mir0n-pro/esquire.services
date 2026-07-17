#!/usr/bin/env python3
# Esquire frameworks (tm) -- Grafana DATASOURCE generator (v1.2.11 observability, T9-B).
#
# Single source of truth for the three datasources, emitted to BOTH deploy targets so they cannot drift:
#   * docker : compose/o11y/grafana/provisioning/datasources/{loki,tempo,prometheus}.yaml
#   * k8s    : k8s/charts/infra/grafana/templates/configmap-datasource.yaml   (helm template)
# Run with no arguments (python gen-datasources.py) after changing a datasource; commit the .py AND both outputs.
#
# WHY THIS FILE EXISTS. The two targets were hand-maintained copies and had already drifted: compose declared a
# `correlationId` derived field on Loki, k8s declared none -- and the compose one was INERT anyway (`url: ""`
# creates no link at all) while a comment above it asserted that it linked. That is the failure mode a second
# hand-written copy produces, and it is why the datasources are generated now.
#
# THE FOUR HOPS. A pipeline is not a pane. Three provisioned datasources give you three Explore tabs and nothing
# else -- Grafana hands Explore to every datasource for free, the moment it exists. What makes it ONE pane is the
# links BETWEEN them, and there are four:
#
#   metrics -> trace   exemplars     a dot on the latency line that opens the trace that produced it
#   trace   -> logs    tracesToLogsV2   "Logs for this span"
#   logs    -> trace   derivedFields    a log line's correlationId opens its trace
#   trace   -> metrics tracesToMetricsV2  a span reaches the RED metrics of the service it ran in
#
# All three signals already carry the same id (traceId == Esq-Correlation-ID), which is what makes this cheap.
#
# A HOP IS NOT DONE UNTIL IT HAS BEEN CLICKED. `url: ""` shipped, looked configured, and did nothing.
#
# THE TRAP THAT EATS EVERY ONE OF THESE LINKS -- read before touching a `$` in this file:
#
#   GRAFANA'S PROVISIONING LOADER EXPANDS `${...}` AS AN ENVIRONMENT VARIABLE. Every interpolation Grafana
#   itself needs at click time -- ${__value.raw}, ${__span.traceId}, ${__span.tags['esq.app']} -- names no env
#   var, so the loader replaces it with the EMPTY STRING before Grafana ever sees it. The datasource then stores
#   a link whose query is blank:
#       tracesToLogsV2:  {job=~"esq-.*"} | correlationId = ``      <- the id is gone
#       tracesToMetrics: ...{application=""}...                    <- the service is gone
#       derivedField:    no url at all                             <- opens an empty query box
#   The link RENDERS. It opens the right datasource. It carries nothing. Which is exactly the `url: ""` failure
#   wearing a different hat -- and it does not show up in any config check, because the config LOOKS right.
#
#   ESCAPE EVERY ONE AS `$$`. DOLLAR is the constant below; use it, never a bare `$`.
#
#   AND VERIFY WHAT GRAFANA STORED, not what this file says: curl the /api/datasources/uid/<uid> endpoint and
#   look at the query strings. Testing the underlying LogQL/PromQL by hand with a real id proves nothing about
#   whether the id ever reaches it.

DOLLAR = "$$"   # survives Grafana's provisioning env-var expansion; a bare "$" does not

import os
import re

UID_LOKI = "esq-loki"
UID_TEMPO = "esq-tempo"
UID_PROM = "esq-prometheus"

# Log stream selector that works on BOTH targets from ONE dashboard/datasource: Alloy labels docker logs
# job="esq-docker" and k8s logs job="esq-k8s".
JOB_ANY = 'job=~"esq-.*"'

# correlationId rides as STRUCTURED METADATA (not a label -- that would explode cardinality), so it is filtered
# with a `|` expression, not inside the stream selector. Note the DOLLAR escape -- see the trap above.
TRACE_TO_LOGS_QUERY = '{%s} | correlationId = `%s{__span.traceId}`' % (JOB_ANY, DOLLAR)


def loki(url):
    return {
        "name": "Loki",
        "type": "loki",
        "access": "proxy",
        "uid": UID_LOKI,
        "url": url,
        "isDefault": True,
        "jsonData": {
            "maxLines": 1000,
            # HOP: logs -> trace. The correlationId on a log line IS the trace id, so this turns it into a link
            # straight into the Tempo waterfall. The previous entry declared `url: ""`, which creates NO link --
            # it rendered a field and stopped. datasourceUid is what actually makes it a jump.
            "derivedFields": [
                {
                    "name": "TraceID",
                    "matcherType": "label",
                    "matcherRegex": "correlationId",
                    "datasourceUid": UID_TEMPO,
                    # `url` IS THE QUERY when datasourceUid is set -- it is not an external address. Omit it and
                    # the link opens Tempo with an EMPTY query box, so the user has to copy the id out of the log
                    # line and paste it in by hand. ${__value.raw} is the matched correlationId; the DOLLAR
                    # escape is what stops Grafana's provisioning loader from eating it.
                    "url": "%s{__value.raw}" % DOLLAR,
                    "urlDisplayLabel": "View trace",
                }
            ],
        },
    }


def tempo(url):
    return {
        "name": "Tempo",
        "type": "tempo",
        "access": "proxy",
        "uid": UID_TEMPO,
        "url": url,
        "jsonData": {
            "httpMethod": "GET",
            # HOP: trace -> logs. A custom query, not the tag-matching default: correlationId is structured
            # metadata rather than a Loki label, so the default (which builds a label matcher) would find nothing.
            "tracesToLogsV2": {
                "datasourceUid": UID_LOKI,
                "spanStartTimeShift": "-5m",
                "spanEndTimeShift": "5m",
                "filterByTraceID": False,
                "filterBySpanID": False,
                "customQuery": True,
                "query": TRACE_TO_LOGS_QUERY,
            },
            # HOP: trace -> metrics. Keyed on esq.app, NOT service.name: the traces pipeline deliberately
            # overwrites service.name with the rod-id (enyman.0) so the waterfall can badge the replica, while
            # metrics carry application=enyman. The collector now preserves the logical name as esq.app precisely
            # so these two signals have a key to join on.
            "tracesToMetricsV2": {
                "datasourceUid": UID_PROM,
                "spanStartTimeShift": "-5m",
                "spanEndTimeShift": "5m",
                "queries": [
                    {
                        "name": "Request rate (req/s)",
                        "query": 'sum(rate(http_server_requests_seconds_count'
                                 '{application="%s{__span.tags[\'esq.app\']}"}[5m]))' % DOLLAR,
                    },
                    {
                        "name": "Error rate (5xx/s)",
                        "query": 'sum(rate(http_server_requests_seconds_count'
                                 '{application="%s{__span.tags[\'esq.app\']}",status=~"5.."}[5m]))' % DOLLAR,
                    },
                ],
            },
            "nodeGraph": {"enabled": True},
            # The SERVICE MAP (T9-C). The Collector's servicegraph connector derives caller->callee edges from the
            # spans and writes them to Prometheus as traces_service_graph_request_* ; this is what tells Grafana
            # WHERE to read them. Without it the node-graph panel renders an empty box -- the connector can be
            # emitting perfectly and the picture is still blank, because nothing told the datasource where to look.
            "serviceMap": {"datasourceUid": UID_PROM},
        },
    }


def prometheus(url):
    return {
        "name": "Prometheus",
        "type": "prometheus",
        "uid": UID_PROM,
        "access": "proxy",
        "url": url,
        "isDefault": False,
        "jsonData": {
            "httpMethod": "POST",
            # HOP: metrics -> trace. THE one that matters most: the place a human actually NOTICES a problem is a
            # latency spike on the dashboard, and this is what turns that spike into the trace that caused it.
            # The services already attach trace_id to every histogram bucket (Boot wires the exemplar
            # SpanContext -- no framework code needed). Two things must also be true or the dot never appears:
            #   1. Prometheus runs with --enable-feature=exemplar-storage, or it silently discards them.
            #   2. There are BUCKETS to carry them: exemplars live only on histogram buckets, so this hop is
            #      INERT until ESQ_METRICS_HISTOGRAMS=true (off by default -- it costs 2.3x the scrape).
            "exemplarTraceIdDestinations": [
                {"name": "trace_id", "datasourceUid": UID_TEMPO}
            ],
        },
    }


KNOWN_UIDS = (UID_LOKI, UID_TEMPO, UID_PROM)


def _strings(obj):
    # every string scalar anywhere in the structure (for the eaten-${} scan)
    if isinstance(obj, dict):
        for v in obj.values():
            for s in _strings(v):
                yield s
    elif isinstance(obj, list):
        for v in obj:
            for s in _strings(v):
                yield s
    elif isinstance(obj, str):
        yield obj


def verify_wiring(datasources):
    """REFUSE to emit a datasource whose single-pane HOPS are inert -- the url:"" / eaten-${} class (T11-I29).

    A hop that renders but carries nothing (a derived field with no url, a tracesToLogs query whose id got
    expanded to empty) does NOT show up in a config diff -- the config LOOKS right. It is caught HERE, at
    generation, before it can ship. Sibling of gen-dashboard.py refusing a naked PromQL subtraction; the LIVE
    twin is o11y-verify.py, which checks what Grafana actually STORED."""
    problems = []
    by_type = {d.get("type"): d for d in datasources}

    def uid_ok(u):
        return u in KNOWN_UIDS

    # logs -> trace : the Loki derived field must MATCH a field, JUMP to a resolvable Tempo uid, and CARRY the id
    # (url is the query when datasourceUid is set; empty url = the original url:"" bug -- an empty query box).
    dfs = by_type.get("loki", {}).get("jsonData", {}).get("derivedFields", [])
    if not dfs:
        problems.append("Loki has NO derivedFields -- the logs->trace hop does not exist")
    for f in dfs:
        if not f.get("matcherRegex"):
            problems.append("Loki derivedField %r matcherRegex is empty -- it matches no log field" % f.get("name"))
        if not uid_ok(f.get("datasourceUid", "")):
            problems.append("Loki derivedField %r datasourceUid %r does not resolve" % (f.get("name"), f.get("datasourceUid")))
        if "{__value.raw}" not in f.get("url", ""):
            problems.append("Loki derivedField %r url %r carries no matched id -- the link opens an EMPTY query "
                            "(the url:'' class)" % (f.get("name"), f.get("url", "")))

    # trace -> logs / metrics / service-map : each Tempo hop must resolve to a uid and keep its runtime id token.
    tj = by_type.get("tempo", {}).get("jsonData", {})
    ttl = tj.get("tracesToLogsV2", {})
    if not uid_ok(ttl.get("datasourceUid", "")):
        problems.append("Tempo tracesToLogsV2 datasourceUid %r does not resolve" % ttl.get("datasourceUid"))
    if "{__span.traceId}" not in ttl.get("query", ""):
        problems.append("Tempo tracesToLogsV2 query carries no ${__span.traceId} -- 'Logs for this span' loses the id")
    ttm = tj.get("tracesToMetricsV2", {})
    if not uid_ok(ttm.get("datasourceUid", "")):
        problems.append("Tempo tracesToMetricsV2 datasourceUid %r does not resolve" % ttm.get("datasourceUid"))
    for q in ttm.get("queries", []):
        if "{__span.tags['esq.app']}" not in q.get("query", ""):
            problems.append("Tempo tracesToMetricsV2 query %r carries no esq.app tag -- it reaches the WRONG "
                            "service's metrics" % q.get("name"))
    if not uid_ok(tj.get("serviceMap", {}).get("datasourceUid", "")):
        problems.append("Tempo serviceMap datasourceUid %r does not resolve -- the node graph reads from nowhere"
                        % tj.get("serviceMap", {}).get("datasourceUid"))

    # metrics -> trace : the Prometheus exemplar destination must resolve to Tempo (the dot that opens the trace).
    dests = by_type.get("prometheus", {}).get("jsonData", {}).get("exemplarTraceIdDestinations", [])
    if not dests:
        problems.append("Prometheus has NO exemplarTraceIdDestinations -- the metrics->trace exemplar hop does not exist")
    for d in dests:
        if not uid_ok(d.get("datasourceUid", "")):
            problems.append("Prometheus exemplar destination %r datasourceUid %r does not resolve" % (d.get("name"), d.get("datasourceUid")))

    # THE EATEN-${} TRAP: a runtime interpolation must be escaped $$ ; a BARE ${ is expanded to "" by Grafana's
    # provisioning loader and the carrier silently empties. Flag any bare ${ not written as $${ (use DOLLAR).
    for ds in datasources:
        for s in _strings(ds):
            if re.search(r"(?<!\$)\$\{", s):
                problems.append("%s datasource has a BARE ${...} in %r -- Grafana expands it to empty; escape it "
                                "as $$ (use DOLLAR)" % (ds.get("type"), s))

    if problems:
        raise SystemExit("gen-datasources: REFUSING to emit -- inert single-pane hop(s):\n  - " +
                         "\n  - ".join(problems))


HEADER = ("# GENERATED by compose/o11y/grafana/gen-datasources.py -- DO NOT EDIT BY HAND.\n"
          "# Edit the generator and re-run it; it emits docker AND k8s so the two cannot drift.\n")


def yaml_dump(obj, indent=0):
    """Minimal YAML writer -- keeps the generator dependency-free (no pyyaml on the build box)."""
    pad = " " * indent
    out = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            if isinstance(v, (dict, list)):
                out.append("%s%s:" % (pad, k))
                out.append(yaml_dump(v, indent + 2))
            else:
                out.append("%s%s: %s" % (pad, k, scalar(v)))
    elif isinstance(obj, list):
        for item in obj:
            if isinstance(item, dict):
                first = True
                for k, v in item.items():
                    lead = "%s- " % pad if first else "%s  " % pad
                    first = False
                    if isinstance(v, (dict, list)):
                        out.append("%s%s:" % (lead, k))
                        out.append(yaml_dump(v, indent + 4))
                    else:
                        out.append("%s%s: %s" % (lead, k, scalar(v)))
            else:
                out.append("%s- %s" % (pad, scalar(item)))
    return "\n".join(x for x in out if x)


def scalar(v):
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return str(v)
    s = str(v)
    # Quote anything a YAML parser could misread (and anything carrying a Grafana ${...} interpolation).
    if any(c in s for c in ":{}[]&*#?|-<>=!%@`\"'") or s == "":
        return '"%s"' % s.replace("\\", "\\\\").replace('"', '\\"')
    return s


def compose_file(ds):
    return HEADER + "apiVersion: 1\n\ndatasources:\n" + yaml_dump([ds], 2) + "\n"


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    svc_root = os.path.abspath(os.path.join(here, "..", "..", ".."))

    # Refuse before writing: an inert single-pane hop (empty url, eaten ${...}, unresolved uid) must not ship.
    # The wiring lives in jsonData and is url-independent, so one representative build covers both targets.
    verify_wiring([loki("x"), tempo("x"), prometheus("x")])

    # --- docker: three provisioning files, real URLs on the compose network ---
    dsdir = os.path.join(svc_root, "compose", "o11y", "grafana", "provisioning", "datasources")
    for name, ds in (("loki", loki("http://loki:3100")),
                     ("tempo", tempo("http://tempo:3200")),
                     ("prometheus", prometheus("http://prometheus:9090"))):
        p = os.path.join(dsdir, "%s.yaml" % name)
        with open(p, "w", newline="\n") as f:
            f.write(compose_file(ds))
        print("wrote %s" % p)

    # --- k8s: one ConfigMap template; the URLs stay helm values ---
    cm = [
        "{{- /* GENERATED by compose/o11y/grafana/gen-datasources.py -- DO NOT EDIT BY HAND. */}}",
        "{{- /* Edit the generator and re-run it; it emits docker AND k8s so the two cannot drift. */}}",
        "apiVersion: v1",
        "kind: ConfigMap",
        "metadata:",
        "  name: {{ .Release.Name }}-datasources",
        "  labels:",
        "    app: {{ .Release.Name }}",
        "data:",
    ]
    for name, ds, urlref in (("loki", loki("__URL__"), "{{ .Values.loki.url }}"),
                             ("tempo", tempo("__URL__"), "{{ .Values.tempo.url }}"),
                             ("prometheus", prometheus("__URL__"), "{{ .Values.prometheus.url }}")):
        body = "apiVersion: 1\ndatasources:\n" + yaml_dump([ds], 2)
        body = body.replace('"__URL__"', urlref).replace("__URL__", urlref)
        cm.append("  %s.yaml: |" % name)
        cm.extend("    " + line for line in body.splitlines())
    p = os.path.join(svc_root, "k8s", "charts", "infra", "grafana", "templates", "configmap-datasource.yaml")
    with open(p, "w", newline="\n") as f:
        f.write("\n".join(cm) + "\n")
    print("wrote %s" % p)


if __name__ == "__main__":
    main()
