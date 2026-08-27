#!/usr/bin/env python3
# Esquire frameworks (tm) -- Grafana dashboard generator (v1.2.11 observability).
#
# THE COMPACT generator: single source of truth for the "Esquire Services" board on the compact profile.
# Emits the SAME JSON to all THREE compact targets so they never drift:
#   * docker : compose-compact/o11y/grafana/provisioning/dashboards/esquire-services.json
#   * k8s    : k8s-compact/charts/infra/grafana/dashboards/esquire-services.json
#   * OKE    : k8s-oci-compact/grafana/esquire-services.json  (the auKeep-less fork)
# The classic generator is its own file under compose/o11y/grafana; neither writes the other's tree.
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
ESQ_SERVICES = ('container=~"(esqc?-)?(gateward|mesnie|pacman|aukeep|backend|frontend)"')


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


def zero_line(expr, key, val):
    """Draw an affirmative FLAT-ZERO line for a failure outcome that may not have happened yet.

    A failure series (outcome=error|failed, result=deny) has NO samples on a healthy system, so a
    `sum by (...)` breakdown simply omits it -- and an omitted line is indistinguishable from a reassuring
    zero. `or vector(0)` cannot fix this: vector(0) carries NO labels, so it cannot BE the missing failure
    series. label_replace() stamps the label onto a zero vector, so the failure line is always drawn -- flat
    at zero until a real failure lands on it, at which point the real labelled series draws alongside (the
    synthesized zero line is harmless). This is what lets a healthy zero read as zero, not as "No data".
    """
    return '(%s) or label_replace(vector(0), "%s", "%s", "", "")' % (expr, key, val)


def avg_s(sum_metric, count_metric, by=None, extra="", window="5m"):
    """Average in SECONDS = rate(sum) / rate(count) -- the ONE way to average a timer. avg_ms() scales it to ms;
    a band() subtracts two of these. Nothing hand-writes rate(sum)/rate(count). `window` is the rate window
    (default 5m; pass "1m" for a more responsive panel).

    NO clamp_min on the denominator. `clamp_min(rate(count), 1)` looks like a safe divide-by-zero guard, and it
    is -- for HIGH-rate meters. But it DIVIDES BY ONE whenever the event rate is below 1/s, which is the normal
    case for a business meter: one KeyCloak sync in five minutes is a count rate of 0.0067/s, so the clamp turns
    a real 130 ms average into 0.3 ms. Off by a factor of 400, silently, and it LOOKS plausible.

    Dividing by the true rate yields NaN when there is no traffic, which Grafana draws as a gap -- honest, and
    the correct reading of "nothing happened". A gap is not a bug; a fabricated 0.3 ms is.
    check_no_clamped_rate_denominator() enforces this against the whole board, so the trap stays removed.
    """
    grp = ("sum by (%s) " % by) if by else "sum"
    return "(%s(rate(%s[%s])%s) / %s(rate(%s[%s])%s))" % (
        grp, sum_metric, window, extra, grp, count_metric, window, extra)


def avg_ms(sum_metric, count_metric, by=None, extra="", window="5m"):
    """Average latency in MILLISECONDS -- avg_s() scaled to ms. Use on a TIMER, whose Prometheus name ends
    _seconds_sum. On a summary that already records ms this multiplies by 1000 and is wrong by that much;
    use avg_raw(). check_avg_scales_only_seconds() enforces the distinction."""
    return "1000 * %s" % avg_s(sum_metric, count_metric, by=by, extra=extra, window=window)


def avg_raw(sum_metric, count_metric, by=None, extra="", window="5m"):
    """Average of a DistributionSummary in WHATEVER unit it recorded -- rate(sum)/rate(count), no scaling.

    A Micrometer Timer carries a base unit, so its Prometheus name says _seconds and avg_ms() can scale it.
    A plain summary carries none: `messaging.retry.backoff` records raw milliseconds and is exposed as
    messaging_retry_backoff_sum, with nothing in the name to say so. Scaling that as if it were seconds
    renders a 500 ms ladder step as 500,000 ms, and the panel is read during a broker outage -- exactly when
    a wrong order of magnitude misleads whoever is diagnosing it. Set the panel unit to match the meter."""
    return avg_s(sum_metric, count_metric, by=by, extra=extra, window=window)


def ratio(numerator, denominator, scale=""):
    """A rate-windowed proportion numerator/denominator -- the ONE way to write a ratio (cache hit rate, ...).

    Both terms are rate() windows, so it is a LIVE proportion, never a lifetime average off cumulative counters
    (a raw-counter ratio can never move once it has run a while -- exactly why the PG topology WARN was inert,
    see I30). NO clamp_min: same trap as avg_s -- a small denominator is honest as a gap, not fabricated as a
    plausible-but-wrong number. `scale` is "100 * " for a percent panel, "" for a 0..1 ratio.
    """
    return "%s(%s) / (%s)" % (scale, numerator, denominator)


def _balanced(expr, i):
    """The parenthesised group starting at expr[i] == '(', both parentheses included."""
    depth = 0
    j = i
    while j < len(expr):
        if expr[j] == "(":
            depth += 1
        elif expr[j] == ")":
            depth -= 1
            if depth == 0:
                return expr[i:j + 1]
        j += 1
    return expr[i:]


def _scan(expr):
    """Walk expr once, yielding (index, char, depth) outside quotes. One place knows about strings."""
    in_str = None
    depth = 0
    for i, ch in enumerate(expr):
        if in_str:
            if ch == in_str:
                in_str = None
            continue
        if ch in "\"'`":
            in_str = ch
            continue
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        yield i, ch, depth


def _binary_addsub_positions(expr):
    """Every index holding a BINARY + or -: arithmetic, not a sign, not a hyphen in a label value.

    BOTH operators matter. PromQL vector arithmetic matches series, so an EMPTY operand empties the whole
    result on either side of a + or a -. `a - ((b) or vector(0)) + (c)` is `(a - b) + c`: guarding b and
    leaving c bare still deletes the panel.

    A minus is binary when the previous non-space character can END a term. `offset -5m` and `[5m:1m]`
    therefore do not count -- `offset` ends in a letter but the minus there follows a keyword, so the
    keyword is excluded explicitly.
    """
    ret = []
    prev = ""
    prev_word = ""
    word = ""
    for i, ch, _ in _scan(expr):
        if ch.isalnum() or ch == "_":
            word += ch
        elif not ch.isspace():
            if word:
                prev_word = word
            word = ""
        elif word:
            prev_word = word
            word = ""
        if ch in "+-" and prev and (prev.isalnum() or prev in ")}]_\"") and prev_word != "offset":
            ret.append(i)
        if not ch.isspace():
            prev = ch
    return ret


def _additive_term(expr, start):
    """The text of one additive term beginning at expr[start]: up to the next same-depth + or -, the end of the
    enclosing group, or a same-depth COMMA -- a comma ends an argument, and a term that ran past one used to
    swallow the `, 1` of a clamp and then fail to recognise the operand it had just mangled."""
    base = None
    end = len(expr)
    for i, ch, depth in _scan(expr[start:]):
        if base is None:
            base = depth
        if i > 0 and depth == base and ch in "+-":
            prev = expr[start:start + i].rstrip()
            if prev and (prev[-1].isalnum() or prev[-1] in ")}]_\""):
                end = start + i
                break
        if depth == base and ch == ",":
            end = start + i
            break
        if depth < base:
            end = start + i
            break
    return expr[start:end].strip()


def _is_number(text):
    try:
        float(text)
        return True
    except ValueError:
        return False


def _guarded_spans(expr):
    """Every parenthesised group that is immediately followed by `or vector(0)`.

    Such a group yields a value whatever happens inside it, so an empty operand nested within one can never
    reach the panel. Without this the guard flagged a correct shipped canvas: a ratio whose denominator is a
    bare `a + b` is genuinely fragile on its own, and harmless once the whole comparison is `(...) or vector(0)`.
    """
    ret = []
    opens = []
    for i, ch, _ in _scan(expr):
        if ch == "(":
            opens.append(i)
        elif ch == ")" and opens:
            start = opens.pop()
            if expr[i + 1:].lstrip().startswith("or vector(0)"):
                ret.append((start, i))
    return ret


def _absorbed(spans, i):
    """Is this operator inside a group whose emptiness is already caught?"""
    ret = False
    for start, end in spans:
        if start < i < end:
            ret = True
            break
    return ret


def _top_addsub_positions(expr):
    """The binary + and - at the OUTERMOST depth of expr only.

    _nonempty asks whether the terms of THIS expression are each safe; a nested operator belongs to a
    sub-expression and splitting on it tears an inner ratio into fragments that are unguarded on their own.
    """
    ret = []
    inner = set(_binary_addsub_positions(expr))
    depths = []
    for i, ch, depth in _scan(expr):
        depths.append((i, depth))
    # The outermost level is the SHALLOWEST depth reached, not the first character's -- `(a) + (b)` opens on a
    # parenthesis, so reading the baseline off character zero puts the top-level + one level too deep and the
    # split finds nothing.
    base = min(d for _, d in depths) if depths else 0
    for i, depth in depths:
        if i in inner and depth == base:
            ret.append(i)
    return ret


def _nonempty_term(term):
    """Can this ONE additive term never be an empty vector?

    Only `or vector(0)` makes a value out of nothing. clamp_max/clamp_min PRESERVE emptiness -- clamp_max of an
    empty vector is still empty -- so a clamped subtrahend is safe exactly when what it clamps is safe, which
    makes the rule recursive rather than a shape match.
    """
    ret = False
    term = term.strip()
    if _is_number(term):
        ret = True                                             # a constant cannot be empty
    elif term.rstrip().endswith("or vector(0)"):
        ret = True                                             # guarded, parenthesised or not
    elif term.startswith("(") and _balanced(term, 0) == term:
        inner = term[1:-1].strip()
        ret = inner.rstrip().endswith("or vector(0)") or _nonempty(inner)
    else:
        for fn in ("clamp_max(", "clamp_min("):
            if term.startswith(fn) and _balanced(term, len(fn) - 1) == term[len(fn) - 1:]:
                ret = _nonempty(_top_args(_balanced(term, len(fn) - 1))[0])
    return ret


def _nonempty(expr):
    """Can this whole expression never be empty? Every additive term must hold on its own -- an empty operand
    empties the sum, so one bare term is enough to lose the lot."""
    expr = expr.strip()
    ret = _nonempty_term(expr)
    if not ret:
        cuts = _top_addsub_positions(expr)
        if cuts:
            ret = True
            start = 0
            for i in cuts + [len(expr)]:
                if not _nonempty_term(expr[start:i].strip().lstrip("+-").strip()):
                    ret = False
                    break
                start = i
    return ret

def check_no_naked_subtraction(panels):
    """Refuse a subtraction whose SUBTRAHEND can be empty -- build-enforced.

    An empty vector deletes the whole expression it is subtracted from, so a band drawn from a metric that is
    legitimately absent does not read low -- it VANISHES. band() emits ((minuend) - ((subtrahend) or vector(0)));
    the topology canvas writes (2 - clamp_max(<guarded sum>, 1)). Both are safe and the rule accepts both,
    because what matters is not the shape but whether the subtrahend can come back empty.

    BOTH operators matter. PromQL matches series on `+` as well, so `a - ((b) or vector(0)) + (c)` still dies
    with c: + and - are equal precedence and left-associative, and the subtrahend is the whole additive term.

    Four cold reads found this guard wanting, each time because it pattern-matched where it needed to parse:
    it counted `or vector(0)` globally, then required spaces around the minus, then stopped at the first group,
    then recognised only one of the two safe idioms and refused a correct board.
    """
    for p in panels:
        for t in p.get("targets", []):
            expr = t.get("expr", "")
            spans = _guarded_spans(expr)
            for i in _binary_addsub_positions(expr):
                term = _additive_term(expr, i + 1)
                if term and not _absorbed(spans, i) and not _nonempty_term(term):
                    raise SystemExit(
                        "naked subtraction in panel %r:\n  %s\n"
                        "  subtrahend: %s\n"
                        "The subtrahend must be unable to come back EMPTY -- ((x) or vector(0)), or a clamp of\n"
                        "one -- because an empty vector deletes the band SILENTLY rather than drawing it low."
                        % (p.get("title"), expr, term))


def check_avg_scales_only_seconds(panels):
    """Refuse a x1000 applied to a metric that is not a timer -- the unit-scale lie, build-enforced.

    avg_ms() means "this metric is in seconds, draw it in ms", and Micrometer says so in the NAME: a Timer
    is *_seconds_sum, a plain DistributionSummary is *_sum with no unit. Scaling the latter draws it a
    thousand times too large, and reading the query does not catch it because the mistake is in a name that
    is NOT there. Use avg_raw() for a summary.

    Only the SCALED OPERAND is inspected, not the whole expression: a band may legitimately combine an
    avg_ms() timer with an avg_raw() summary, and condemning the second because the first is scaled is a
    false positive -- which costs as much as a hole, because a guard that refuses correct work gets
    weakened. The scale also needs a left boundary, or `21000` matches.
    """
    for p in panels:
        for t in p.get("targets", []):
            expr = t.get("expr", "")
            operands = []
            for m in re.finditer(r"(?<![\d.\w])(?:1000|1e3)\s*\*\s*", expr):
                rest = expr[m.end():]
                operands.append(_balanced(rest, 0) if rest.startswith("(") else _additive_term(rest, 0))
            for m in re.finditer(r"\*\s*(?:1000|1e3)(?![\d.\w])", expr):
                head = expr[:m.start()].rstrip()
                if head.endswith(")"):
                    depth = 0
                    for k in range(len(head) - 1, -1, -1):
                        if head[k] == ")":
                            depth += 1
                        elif head[k] == "(":
                            depth -= 1
                            if depth == 0:
                                operands.append(head[k:])
                                break
                else:
                    operands.append(head)
            for operand in operands:
                for name in re.findall(r"(?:rate|irate|increase)\(\s*([A-Za-z_:][A-Za-z0-9_:]*)", operand):
                    if name.endswith("_sum") and not name.endswith("_seconds_sum"):
                        raise SystemExit(
                            "avg_ms() on a non-timer in panel %r:\n  %s\n"
                            "%s has no unit in its name, so it is NOT seconds -- scaling it draws the value a\n"
                            "thousand times too large. Use avg_raw()." % (p.get("title"), expr, name))


def _top_args(call):
    """The top-level arguments of a call like clamp_min(a, b) -- `call` starts at its '('."""
    inner = call[1:-1]
    args = []
    start = 0
    for i, ch, depth in _scan(inner):
        if ch == "," and depth == 0:
            args.append(inner[start:i].strip())
            start = i + 1
    args.append(inner[start:].strip())
    return args


def check_no_clamped_rate_denominator(panels):
    """Refuse a DENOMINATOR that floors its divisor -- the plausible-lie trap, build-enforced.

    clamp_min(x, N) divides by N whenever the true value is below N, rendering a real 130 ms average as
    0.3 ms SILENTLY -- and it looks plausible, so reading the query never catches it. avg_s() / avg_ms() /
    ratio() divide by the TRUE rate on purpose: a gap when idle is the honest reading.

    The FLOOR is the last top-level argument, so `max(topk(5, x))` -- a comma and a digit, but not a floor
    -- passes, and `clamp_min(band, 0)` (flooring a band, not a divisor) stays legal. Leading parentheses
    after the / are stripped, because one pair used to defeat the whole check.
    """
    for p in panels:
        for t in p.get("targets", []):
            expr = t.get("expr", "")
            for m in re.finditer(r"/\s*", expr):
                rest = expr[m.end():].lstrip()
                while rest.startswith("(("):
                    rest = rest[1:].lstrip()
                if rest.startswith("("):
                    inner = _balanced(rest, 0)[1:-1].strip()
                    if inner.startswith(("clamp_min(", "max(", "min(")):
                        rest = inner
                for fn in ("clamp_min(", "max(", "min("):
                    if rest.startswith(fn):
                        args = _top_args(_balanced(rest, len(fn) - 1))
                        floor = args[-1] if len(args) > 1 else ""
                        mm = re.match(r"^vector\(\s*([0-9.]+)\s*\)$|^([0-9.]+)$", floor)
                        if mm and float(mm.group(1) or mm.group(2)) > 0:
                            raise SystemExit(
                                "floored denominator in panel %r:\n  %s\n"
                                "Flooring the divisor divides by that floor whenever the true value is below\n"
                                "it, rendering a real average as a fraction of itself. Divide by the TRUE rate."
                                % (p.get("title"), expr))
                if re.match(r"\(?[^/]*?>\s*0\s+or\s+vector\(\s*[1-9]", rest):
                    raise SystemExit(
                        "floored denominator in panel %r:\n  %s\n"
                        "`> 0 or vector(1)` is the same divide-by-one lie in another idiom."
                        % (p.get("title"), expr))


def check_no_panel_overlap(panels):
    """Refuse two TOP-LEVEL panels whose grid rectangles intersect -- build-enforced.

    check_rows_do_not_share_y compares one panel's y against another's. That catches a panel placed AT a row
    header's y and nothing else: it has no idea that a panel of height h occupies y .. y+h-1. So a canvas
    declared h=27 at y=1 ran straight under the row header at y=25 and the three panels below it, on all five
    topology boards, and no guard could see it. Grafana resolves an overlap by pushing panels down, so the board
    still renders -- just not the layout the generator declared, which is the whole point of generating it.

    Only TOP-LEVEL panels are compared. A panel nested inside a collapsed row carries coordinates relative to
    that row, so mixing the two levels would invent overlaps that do not exist.
    """
    placed = []
    for p in panels:
        if p.get("type") == "row":
            continue
        g = p.get("gridPos") or {}
        x, y = g.get("x", 0), g.get("y", 0)
        w, h = g.get("w", 0), g.get("h", 0)
        for (px, py, pw, ph, title) in placed:
            if x < px + pw and px < x + w and y < py + ph and py < y + h:
                raise SystemExit(
                    "panel overlap: %r (x=%d y=%d w=%d h=%d) intersects %r (x=%d y=%d w=%d h=%d).\n"
                    "A panel occupies y .. y+h-1; check the HEIGHT, not just the y. Grafana would push one of\n"
                    "them down, so the board renders -- but not the layout this generator declares."
                    % (p.get("title"), x, y, w, h, title, px, py, pw, ph))
        placed.append((x, y, w, h, p.get("title")))


def check_rows_do_not_share_y(panels):
    """Refuse two panels -- of ANY kind -- placed at the same y when one of them is a ROW header.

    Grafana sorts by (y, x) and then assigns row membership by POSITION IN THAT SORTED ARRAY. A panel
    sharing a row header's y lands on whichever side of it the sort happens to put it, so a panel declared
    under one row renders inside the NEXT one and collapsing the wrong row hides it. TWO ROW HEADERS at one
    y is the same ambiguity in its purest form -- both have x=0, so which owns the panels below is decided
    by list order alone.
    """
    seen = {}
    for p in panels:
        y = p.get("gridPos", {}).get("y")
        is_row = p.get("type") == "row"
        if y in seen and (is_row or seen[y][1]):
            raise SystemExit(
                "panel %r and %r both sit at y=%s, and one is a ROW header.\n"
                "Grafana sorts by (y, x) and assigns row membership by the sorted position, so this\n"
                "renders in the wrong row. Give the row its own y." % (p.get("title"), seen[y][0], y))
        if y not in seen or is_row:
            seen[y] = (p.get("title"), is_row)


# allValue is `.+`, never `.*`. In Prometheus a matcher that matches the EMPTY STRING also selects series
# that do not carry the label at all -- and KeyCloak and the broker carry neither `application` nor
# `service`, because Esquire stamps those on its own registries only. With `.*` the landing state of the
# board (both pickers on All) drew KeyCloak's requests, heap, threads and CPU onto panels declared
# Esquire-only, each with a blank legend token, and summed its cores into "Cores in use -- TOTAL".
# Picking any explicit value hid it again, which is why it survived every manual look.
#
# TWO identities, TWO pickers -- the compact profile is why (T3.1/T3.2).
#
#   application = which PROCESS. It has a JVM, a connection pool, a CPU and a log stream. On this profile
#                 `mesnie` is ONE process holding enyMan, keySmith and kcMaster, and `gateward` is ONE holding
#                 the gateway and the tree cache. There is exactly one heap to draw for each.
#   service     = which ESQUIRE SERVICE did the work. It is what a business meter, a request on a route, or a
#                 bus leg belongs to, and it stays enyMan / keySmith / kcMaster / gateway / bizTree however few
#                 processes they run in.
#
# So the MACHINE rows (JVM, pool, CPU, bandwidth, broker, Postgres, KeyCloak, BFF, capacity) filter by
# $application, and the WORK rows (overview, messaging, the business rows, the breakers) filter by $service.
#
# THE LATENCY BANDS ARE NEITHER, deliberately. They carry no matcher at all, so no picker narrows them.
# Adding $service would be WRONG: the decomposition subtracts ACROSS services -- esq.gw.* is owned by the
# gate and esq.srv.* by the target service -- so filtering would subtract two different populations and
# break the closure check_bands enforces. They read fleet-wide whatever the pickers say. On a classic deployment the two are equal and every panel reads exactly as it always did.
# allValue is `.+`, never `.*`. In Prometheus a matcher that matches the EMPTY STRING also selects series that
# do not carry the label at all -- and KeyCloak and the broker carry neither `application` nor `service`,
# because Esquire stamps those on its own registries only. With `.*` the landing state of the board (both
# pickers on All) drew KeyCloak's requests, heap, threads and CPU onto panels declared Esquire-only, each with
# a blank legend token, and summed its cores into "Cores in use -- TOTAL". Picking any explicit value hid it
# again, which is why it survived every manual look. (cold read, 2026-08-25)
#
APP = 'application=~"$application"'
SVC = 'service=~"$service"'


def build_panels():
    p = []
    # ---- Overview ----
    p.append(row("Overview", 0))
    # sum(up) is EVERY scrape target -- the Esquire services AND the infra (keycloak / postgres / activemq) AND
    # the o11y tooling (otel-collector / otel-servicegraph). The title says "all monitored" on purpose so it is not
    # read as "Esquire services only" (which would contradict the ESQ_SERVICES allow-list the rest of the board uses).
    p.append(stat("All monitored targets (up)", 0, 1, 6, [tgt("sum(up)")]))
    p.append(stat("Total request rate (req/s)", 6, 1, 6,
                  [tgt("sum(rate(http_server_requests_seconds_count{%s}[1m]))" % SVC)], unit="reqps"))
    p.append(ts("HTTP request rate by replica", 12, 1, 12, "reqps",
                [tgt("sum by (service, instance) (rate(http_server_requests_seconds_count{%s}[1m]))" % SVC,
                     "{{service}} {{instance}}")]))
    p.append(ts("HTTP p95 latency by replica", 0, 6, 12, "s",
                [tgt("histogram_quantile(0.95, sum by (le, service, instance) "
                     "(rate(http_server_requests_seconds_bucket{%s}[5m])))" % SVC, "{{service}} {{instance}}",
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
                [tgt("process_cpu_usage{%s}" % APP, "{{application}} {{instance}}")],
                desc="A FRACTION OF THAT JVM'S OWN EFFECTIVE CPUs -- not of the machine. The same 1.0 means "
                     "'using its one allotted core' on k8s and 'using all 24' on docker, so this line is NOT "
                     "comparable across targets and cannot answer 'are we using the machine'. The Capacity row "
                     "converts it to CORES, which is."))
    p.append(ts("Host CPU (system)", 12, 43, 12, "percentunit",
                [tgt("avg(system_cpu_usage{%s})" % APP, "host")]))
    # ---- DB connection detail ----
    p.append(row("DB connection detail", 51))
    p.append(ts("Avg DB connections in use (time-weighted)", 0, 52, 12, "short",
                [tgt("sum by (application, instance) (rate(hikaricp_connections_usage_seconds_sum{%s}[1m]))" % APP,
                     "{{application}} {{instance}}")]))
    p.append(ts("Avg DB connection hold time (ms/borrow)", 12, 52, 12, "ms",
                [tgt(avg_ms("hikaricp_connections_usage_seconds_sum{%s}" % APP,
                            "hikaricp_connections_usage_seconds_count{%s}" % APP, by="application, instance"),
                     "{{application}} {{instance}}")]))
    # ---- BFF (Node.js) ----
    p.append(row("BFF (Node.js)", 60))
    # The BFF runs x2 on k8s -- every panel carries the instance dimension (same convention as the Java panels),
    # so the two replicas are DISTINCT series, never silently summed into one line.
    p.append(ts("BFF request rate by replica", 0, 61, 6, "reqps",
                [tgt("sum by (instance) (rate(esq_bff_inbound_duration_seconds_count[1m]))", "{{instance}}")],
                desc="Per-replica request rate -- shows how the load balances across the x2 BFF pods. "
                     "On docker there is a single instance."))
    p.append(ts("BFF p95 latency by route", 6, 61, 6, "s",
                [tgt("histogram_quantile(0.95, sum by (le, route) (rate(esq_bff_inbound_duration_seconds_bucket[5m])))",
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
                # safe() on the READ term: an empty blks_read empties the whole DENOMINATOR and the panel
                # vanishes rather than reading 100%. Guarded, it reads 1.0 -- no reads means every block
                # was a hit, which is the truth. Caught by check_no_naked_subtraction once it learned that
                # a + is as fatal as a - (cold read, 2026-08-25).
                [tgt(ratio('rate(pg_stat_database_blks_hit{datname="esq2025"}[5m])',
                           'rate(pg_stat_database_blks_hit{datname="esq2025"}[5m]) + '
                           '((rate(pg_stat_database_blks_read{datname="esq2025"}[5m])) or vector(0))'),
                     "hit ratio")]))
    p.append(ts("Postgres database size", 12, 78, 12, "bytes",
                [tgt('pg_database_size_bytes{datname="esq2025"}', "{{datname}}")]))
    # ---- KeyCloak (Quarkus mgmt :9000/kc-auth/metrics) ----
    p.append(row("KeyCloak", 86))
    p.append(ts("KeyCloak HTTP request rate", 0, 87, 12, "reqps",
                [tgt('sum(rate(http_server_requests_seconds_count{job="keycloak"}[1m]))', "requests/s")]))
    p.append(ts("KeyCloak avg HTTP latency (ms)", 12, 87, 12, "ms",
                [tgt(avg_ms('http_server_requests_seconds_sum{job="keycloak"}',
                            'http_server_requests_seconds_count{job="keycloak"}', window="1m"), "avg")]))
    p.append(ts("KeyCloak DB pool (agroal)", 0, 95, 12, "short",
                [tgt('agroal_active_count{job="keycloak"}', "active"),
                 tgt('agroal_available_count{job="keycloak"}', "available")]))
    p.append(ts("KeyCloak JVM memory (heap / non-heap)", 12, 95, 12, "bytes",
                [tgt('sum(jvm_memory_used_bytes{job="keycloak", area="heap"})', "heap used"),
                 tgt('sum(jvm_memory_used_bytes{job="keycloak", area="nonheap"})', "non-heap used")],
                desc="MICROMETER names, not MicroProfile. This KeyCloak is Quarkus-based and publishes "
                     "jvm_memory_used_bytes; the base_memory_used*Heap_bytes namespace does not exist on "
                     "it, so this panel drew NOTHING at all until 2026-08-25. It went unnoticed because "
                     "base_ is outside check_dependencies' family tuple, so the sweep reported every "
                     "dependency present while the panel was dead -- while the agroal panel beside it, a "
                     "Micrometer name that IS in the tuple, worked and made the row look healthy."))
    # ---- Messaging bus (x-rod meters emitted by the engine, O1/T5) ----
    p.append(row("Messaging bus", 103))
    p.append(ts("Bus send rate (msg/s)", 0, 104, 8, "ops",
                [tgt("sum by (service, bus_id) (rate(messaging_send_total{%s}[1m]))" % SVC,
                     "{{service}} -> {{bus_id}}")]))
    p.append(ts("Bus receive rate (msg/s)", 8, 104, 8, "ops",
                [tgt("sum by (service, bus_id) (rate(messaging_receive_total{%s}[1m]))" % SVC,
                     "{{service}} <- {{bus_id}}")]))
    p.append(ts("Bus error rate (msg/s)", 16, 104, 8, "ops",
                [tgt(zero_line("sum by (service, bus_id, leg) (rate(messaging_error_total{%s}[5m]))" % SVC,
                               "leg", "send"),
                     "{{service}} {{bus_id}} {{leg}}")],
                desc="A healthy bus has NO error series, which reads as 'No data' -- indistinguishable from a "
                     "broken meter. The zero-line draws a flat 0 until a real error lands (I20, same fix as I9 for "
                     "the business failure panels)."))
    p.append(ts("Bus send latency (avg + p95 ms)", 0, 112, 8, "ms",
                [tgt(avg_ms("messaging_send_duration_seconds_sum{%s}" % SVC,
                            "messaging_send_duration_seconds_count{%s}" % SVC, by="service, bus_id"),
                     "avg {{service}} -> {{bus_id}}"),
                 tgt("1000 * histogram_quantile(0.95, sum by (le, service, bus_id) "
                     "(rate(messaging_send_duration_seconds_bucket{%s}[5m])))" % SVC,
                     "p95 {{service}} -> {{bus_id}}", exemplar=True)],
                desc="avg is always available. p95 needs the percentile buckets -- turn on the sub-switch "
                     "esquire.observability.metrics.histograms-enabled (ESQ_METRICS_HISTOGRAMS); with it off the "
                     "p95 series are simply absent and the avg still plots."))
    p.append(ts("Feed depth (tx queue)", 8, 112, 8, "short",
                [tgt("messaging_feed_depth{%s}" % SVC, "{{service}} {{bus_id}}")]))
    p.append(ts("Send-retry: held + dropped (counts)", 16, 112, 4, "short",
                [tgt("sum by (service, bus_id)(messaging_retry_held{%s})" % SVC,
                     "held {{service}} {{bus_id}}"),
                 tgt("(sum by (service, bus_id)(increase(messaging_retry_dropped_total{%s}[5m]))) or vector(0)"
                     % SVC, "dropped/5m {{service}} {{bus_id}}")],
                minv=0,
                desc="The send-retry sublayer. FLAT AT ZERO is the healthy state -- these only move when the "
                     "transport is failing sends: held = messages parked awaiting re-dispatch, dropped = given up "
                     "after max attempts. Both are COUNTS, so they share an axis honestly; the backoff duration "
                     "is milliseconds and lives on its own panel to the right."))
    p.append(ts("Send-retry: backoff (avg ms)", 20, 112, 4, "ms",
                [tgt(avg_raw("messaging_retry_backoff_sum{%s}" % SVC,
                             "messaging_retry_backoff_count{%s}" % SVC, by="service, bus_id"),
                     "{{service}} {{bus_id}}")],
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
    GW_OUTER = avg_s("esq_gw_outer_seconds_sum", "esq_gw_outer_seconds_count")
    GW_INNER = avg_s("esq_gw_inner_seconds_sum", "esq_gw_inner_seconds_count")
    SRV_OUTER = avg_s("esq_srv_outer_seconds_sum", "esq_srv_outer_seconds_count")
    SRV_INNER = avg_s("esq_srv_inner_seconds_sum", "esq_srv_inner_seconds_count")
    # I42/L8+L9: the BFF's own view of its outbound leg to the gateway -- the OUTERMOST measured layer, and the
    # only one recorded on the Node side. Covers BOTH BFF outbound paths (the cacheable-GET fetch and the streaming
    # proxy). Drawn RAW only, NEVER as a band: see the RAW panel's desc for why subtracting it from gw.outer is
    # wrong no matter how many BFF paths are timed.
    BFF_UPSTREAM = avg_s("esq_bff_outbound_duration_seconds_sum", "esq_bff_outbound_duration_seconds_count")
    # Every band goes through band() -- see the trap note at the top. A subtrahend with no samples (a service not
    # hit yet, a fresh deploy) must make its band read 0, never delete it.
    # I47: the gw.outer-minus-gw.inner bucket, split. It was drawn as "net (client <-> gw)" -- a name the code
    # cannot support: ESQ_START_TIME is stamped by the GATEWAY's own clock (RequestTraceFilter, a WebFilter at
    # HIGHEST_PRECEDENCE, so it runs only once the request has ARRIVED) and closed by ResponseTraceFilter before
    # the response leaves. Both ends are in-process: the bucket is the gateway's SELF overhead, and NO client
    # network can be in it. InnerTimerFilter's javadoc always said so; only this board disagreed.
    # KC is amortized over GATEWAY REQUESTS, not over relay calls: the relay fires only on a cache MISS, so
    # avg(tokenrelay.duration) is the cost of A RELAY, not the cost carried by an average request. Dividing the
    # total relay seconds by the gateway's request count gives what each request actually pays. Valid because both
    # terms are gateway-side over the SAME population -- unlike the BFF upstream timer (see the RAW panel).
    KC_PER_REQ = ratio("sum(rate(esq_biz_gw_tokenrelay_duration_seconds_sum[5m]))",
                       "sum(rate(esq_gw_outer_seconds_count[5m]))")
    p.append(ts("Request latency bands -- DERIVED (avg ms)", 8, 138, 8, "ms",
                [tgt("1000 * %s" % band(band(GW_OUTER, GW_INNER), KC_PER_REQ), "gw self (auth + routing + assembly)"),
                 tgt("1000 * %s" % safe(KC_PER_REQ), "KC token-relay (per gw request)"),
                 tgt("1000 * %s" % band(GW_INNER, SRV_OUTER), "in-cluster (gw <-> srv)"),
                 tgt("1000 * %s" % band(SRV_OUTER, SRV_INNER), "srv self (compute)"),
                 tgt("1000 * %s" % safe(SRV_INNER), "srv inner (db)")],
                minv=None,   # a band can dip slightly negative on clock/rounding skew -- do not clamp it away
                desc="The raw timers SUBTRACTED into the bands they imply. Together they account for gw.outer "
                     "end to end: gw self + KC token-relay + in-cluster + srv self + srv inner. "
                     "gw self = (gw.outer - gw.inner) - KC; KC = total relay seconds / gateway requests; "
                     "in-cluster = gw.inner - srv.outer; srv self = srv.outer - srv.inner; srv inner = DB time. "
                     "THERE IS NO client<->gateway NETWORK BAND, and there cannot be one: every timer here is "
                     "stamped by the gateway's own clock AFTER the request arrived, so a server-side metric can "
                     "never see its own client's network. (This band was once drawn as 'net (client <-> gw)' -- "
                     "I47. It never was: it is the gateway's SELF overhead, and the KC token-relay call sits "
                     "INSIDE it, so a slow KeyCloak used to read as a slow NETWORK. KC is now its own line.) "
                     "READ THE KC LINE AS PER-REQUEST, NOT PER-RELAY: the relay only runs on a cache MISS, so "
                     "this is what an average request pays for KeyCloak -- use the 'Token relay -- KC /token "
                     "acquire' panel for the cost of one relay, and the hit-rate panel for how often it is paid. "
                     "The line sits flat at zero when the relay is dormant (a plain-JWT workload never relays) -- "
                     "correct, not a broken panel. "
                     "Fully aggregated (scalars) on purpose: the gw timers are tagged application=gateway and the "
                     "srv timers application=<service>, so they cannot be subtracted label-wise. The DB band is "
                     "STEADY-STATE: the JPA time is collected on every request while observability is on, so it "
                     "no longer depends on the X-Capture-Metrics load-test header."))
    p.append(ts("Request latency bands -- RAW (avg ms by layer)", 0, 138, 8, "ms",
                [tgt("1000 * %s" % BFF_UPSTREAM, "bff -> gw upstream (BFF traffic only)"),
                 tgt("1000 * %s" % GW_OUTER, "gw outer (total)"),
                 tgt("1000 * %s" % GW_INNER, "gw inner (proxied)"),
                 tgt("1000 * %s" % SRV_OUTER, "srv outer (wall)"),
                 tgt("1000 * %s" % SRV_INNER, "srv inner (db, capture-gated)")],
                desc="The raw timers, each nested inside the one above it: the BFF's outbound call wraps the "
                     "gateway's whole window, which wraps the proxied downstream call, which wraps the service's "
                     "wall time, which wraps its DB time. "
                     "READ THE TOP LINE AGAINST A DIFFERENT POPULATION -- 'bff -> gw upstream' (I42/L8+L9) times "
                     "the BFF's outbound leg on BOTH its paths, so it covers all traffic THE BFF sends. But the "
                     "gateway is NOT only the BFF's callee: Token Relay clients (the hauberk load-test IAS "
                     "clients, any direct API consumer) call it straight, and gw.outer counts them too. So "
                     "'bff upstream - gw outer' is NOT a valid band -- under a load test it would compare two "
                     "different populations and can even go negative. That is why the BFF<->gw network has no "
                     "band on the DERIVED panel while every other hop does. Deliberate: a wrong band is worse "
                     "than a missing one. Read this line on its own, as the BFF's view of its own hop."))
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
                [tgt(zero_line("sum by (op, outcome) (rate(esq_biz_entity_ops_total{%s}[5m]))" % SVC,
                               "outcome", "error"),
                     "{{op}} {{outcome}}")],
                desc="What enyMan actually DID: creates, deletes and moves, by outcome. No free meter can see "
                     "this -- http.server.requests knows the endpoint and the HTTP status, not which KIND of "
                     "entity was acted on nor whether a refusal was an authorization decision. NOTE a MOVE here "
                     "means the command was ACCEPTED (/esq-move answers 202); whether the move SUCCEEDED is the "
                     "next panel, because the work happens off-request on the queue worker."))
    # A queue DEPTH is a count and processed/failed are rates -- two units. On one axis a depth spike to 1000
    # would flatten the rates into the floor and hide exactly the failure you opened the panel for. Two panels.
    p.append(ts("Move queue -- depth (pending)", 8, 156, 4, "short",
                [tgt("sum(esq_biz_move_queue_depth{%s})" % SVC, "pending")],
                minv=0,
                desc="The move backlog. /esq-move answers 202 at submit time and the work happens on the queue "
                     "worker, so a rising depth means the worker is not keeping up -- and nothing on the request "
                     "side would tell you."))
    p.append(ts("Move outcome (per s)", 12, 156, 4, "ops",
                [tgt("sum(rate(esq_biz_move_processed_total{%s}[5m]))" % SVC, "processed/s"),
                 tgt("(sum(rate(esq_biz_move_failed_total{%s}[5m]))) or vector(0)" % SVC, "FAILED/s")],
                minv=0,
                desc="The async half of a move. A move that FAILS on the worker is invisible to the caller (it "
                     "already got its 202) and to every HTTP meter -- this is the only place it shows. FAILED is "
                     "flat at zero on a healthy system; `or vector(0)` keeps it drawing a zero line rather than "
                     "vanishing, because a vanished series is indistinguishable from a broken panel."))
    p.append(ts("Dictionary lookups (by kind)", 16, 156, 8, "ops",
                [tgt("sum by (kind) (rate(esq_biz_dict_lookup_total{%s}[5m]))" % SVC, "kind {{kind}}")],
                desc="Which DICTIONARY is being fetched. Not a duplicate of http.server.requests: that meter is "
                     "tagged by URI template (/esq-dict), and the kind is a query param -- so the free meter can "
                     "tell you the endpoint is busy but never which dictionary."))

    # ---- Business: money (pacMan) ----
    p.append(row("Business -- money", 164))
    p.append(ts("Account transactions (tx/s by type + outcome)", 0, 165, 8, "ops",
                [tgt(zero_line("sum by (type, outcome) (rate(esq_biz_acct_tx_total{%s}[5m]))" % SVC,
                               "outcome", "error"),
                     "{{type}} {{outcome}}")],
                desc="The money path: deposits, withdrawals, transfers, by outcome. Both processors report here "
                     "-- the transfer processor OVERRIDES the single one and does not call super, so it needed "
                     "its own meter or every transfer would have been silently missing from this panel."))
    p.append(ts("Transaction latency (avg ms by type)", 8, 165, 8, "ms",
                [tgt(avg_ms("esq_biz_acct_tx_duration_seconds_sum{%s}" % SVC,
                           "esq_biz_acct_tx_duration_seconds_count{%s}" % SVC, by="type"), "{{type}}")],
                desc="How long a transaction takes end to end inside pacMan, by operation. A transfer is two "
                     "legs and a rate lookup, so it is legitimately dearer than a deposit -- the shape to watch "
                     "is a type getting slower against ITSELF, not one type against another."))
    p.append(ts("FX applied + accounts closed", 16, 165, 8, "ops",
                [tgt("sum(rate(esq_biz_acct_fx_apply_total{%s}[5m]))" % SVC, "fx applied/s"),
                 tgt('sum by (purge) (rate(esq_biz_acct_close_total{%s}[5m]))' % SVC, "closed ({{purge}})")],
                desc="A conversion rate is only present on the cross-currency leg of a transfer, so a non-null "
                     "convRate IS the FX application. Closures are counted only once the delete has SUCCEEDED "
                     "past the three guards; purge=test-house marks the demo-data path that forces those guards "
                     "open, so a real closure is never confused with a fixture teardown."))

    # ---- Business: identity + token relay (kcMaster, gateway) ----
    p.append(row("Business -- identity + token relay", 173))
    p.append(ts("Access profile reads + saves (by op + outcome)", 0, 182, 6, "ops",
                [tgt(zero_line("sum by (op, outcome) (rate(esq_biz_key_ops_total{%s}[5m]))" % SVC,
                               "outcome", "error"),
                     "{{op}} {{outcome}}")],
                desc="What keySmith actually did. A read is the sign-in handshake, so this line is the closest thing to a login rate the fleet emits; a save is a permission or profile change, which is the rarer and more consequential one. The error line is a save that threw -- the caller saw a 4xx/5xx, and this says which operation it was."))
    p.append(ts("Identity commands asked for (by command)", 6, 182, 6, "ops",
                [tgt("sum by (op) (rate(esq_biz_key_identity_total{%s}[5m]))" % SVC, "{{op}}")],
                desc="What keySmith ASKED the identity provider to do. Read it against KeyCloak identity sync above, which is what was DONE: the two should track each other, and a gap between them is a request that never landed. Nothing else compares the two sides."))
    p.append(ts("KeyCloak identity sync (by op + outcome)", 0, 174, 6, "ops",
                [tgt(zero_line("sum by (op, outcome) (rate(esq_biz_kc_sync_total{%s}[5m]))" % SVC,
                               "outcome", "error"),
                     "{{op}} {{outcome}}")],
                desc="Whether Esquire and KeyCloak still AGREE about who exists. The bus meters say a sync "
                     "request arrived; only this says whether the identity was actually brought into line. A "
                     "non-zero error line means the two systems have DRIFTED -- a user Esquire thinks can log "
                     "in, and KeyCloak does not."))
    p.append(ts("KeyCloak sync latency (avg ms)", 6, 174, 6, "ms",
                [tgt(avg_ms("esq_biz_kc_sync_duration_seconds_sum{%s}" % SVC,
                           "esq_biz_kc_sync_duration_seconds_count{%s}" % SVC), "kc sync")],
                desc="KeyCloak is a SEPARATE SERVER and nothing else times it. Measured around the whole sync "
                     "rather than inside the admin client, so it is the sync's wall time -- but the KC "
                     "round-trip dominates it (the attribute mapping either side is microseconds against "
                     "KeyCloak's milliseconds)."))
    # The hit rate and the acquire cost are TWO UNITS -- a percentage (0..100) and milliseconds. On one axis the
    # percentage owns the scale and the latency line lies flat against zero, unreadable. They are two panels.
    p.append(ts("Token relay -- cache hit rate", 12, 174, 6, "percent",
                [tgt(ratio('sum(rate(esq_biz_gw_tokenrelay_total{result="hit"}[5m]))',
                           'sum(rate(esq_biz_gw_tokenrelay_total[5m]))', scale="100 * "), "cache hit rate")],
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
    p.append(row("Business -- cache, keep + permissions", 190))
    p.append(ts("Tree cache -- broadcast dispatch (by outcome)", 0, 191, 8, "ops",
                [tgt(zero_line("sum by (outcome) (rate(esq_biz_tree_handler_dispatch_total{%s}[5m]))" % SVC,
                               "outcome", "failed"),
                     "{{outcome}}"),
                 tgt(zero_line("sum by (outcome) (rate(esq_biz_tree_rebuild_total{%s}[5m]))" % SVC,
                               "outcome", "error"),
                     "rebuild ({{outcome}})")],
                desc="What the CACHE did with each broadcast -- applied it, found no handler, found no payload, "
                     "or FAILED. The failed line is the point: the dispatch hub SWALLOWS a handler exception, so "
                     "a handler that blows up leaves the tree silently stale while the bus still counts the "
                     "message as received. Rebuilds should be RARE -- a rising rebuild rate is itself a finding."))
    # auKeep is absent on OKE (audit = DB triggers, no keep sink) -- drop this panel there. The OKE pass removes
    # `aukeep` from ESQ_SERVICES, which is the signal (T12).
    ("aukeep" in ESQ_SERVICES) and p.append(ts("Audit keep -- DB writes (by op + outcome)", 8, 191, 8, "ops",
                [tgt(zero_line("sum by (op, outcome) (rate(esq_biz_keep_write_total{%s}[5m]))" % SVC,
                               "outcome", "error"),
                     "{{op}} {{outcome}}")],
                desc="THE DB WRITE at the keep sink -- the one thing the bus meters cannot see. "
                     "messaging.receive.total says the audit event ARRIVED; only this says whether the row was "
                     "actually WRITTEN. An audit event that lands on the bus and then fails to persist is "
                     "exactly the failure that was invisible before. These counts must RECONCILE with the bus "
                     "receive count on the Messaging bus row: a divergence is a real finding."))
    p.append(ts("Permission checks (allow vs DENY)", 16, 191, 8, "ops",
                [tgt(zero_line("sum by (cmd, result) (rate(esq_biz_perm_check_total{%s}[5m]))" % SVC,
                               "result", "deny"),
                     "{{cmd}} {{result}}")],
                desc="The authorization decision itself, counted at the one gate every service goes through. A "
                     "rising DENY rate is either a misconfigured role or someone probing. NOTE the gate sees "
                     "allow and deny only: a self-update BYPASSES it entirely (a user editing their own profile "
                     "never asks permission), which is why there is no third value here."))

    # ---- Resilience -- the circuit breakers (T10) ----
    # These meters existed and were SCRAPED from the day the gateway got its breakers -- and nothing rendered
    # them. A breaker could open, shed a route, and close again, and the only trace was a 503 in the logs with
    # no way to tell WHOSE 503 it was. That is the distinction this row exists to draw:
    #
    #   a 503 because the BACKEND failed        -> the backend is the problem
    #   a 503 because the BREAKER refused       -> the BREAKER is the problem
    #
    # They look identical from the client. Under load they are opposite findings, and tuning against the wrong
    # one makes things worse: a breaker that opens on a backend that is merely SLOW converts a degradation into
    # an outage, and the retry ladder then hands the struggling backend 3x the load. "Calls REFUSED" below is
    # the panel that tells them apart.
    p.append(row("Resilience -- circuit breakers", 199))
    p.append(ts("Breaker state -- OPEN / half-open (1 = yes)", 0, 200, 8, "short",
                [tgt('sum by (name) (resilience4j_circuitbreaker_state{%s, state="open"})' % SVC,
                     "{{name}} OPEN"),
                 tgt('sum by (name) (resilience4j_circuitbreaker_state{%s, state="half_open"})' % SVC,
                     "{{name}} half-open")],
                desc="Flat at zero is the healthy shape. A line stepping to 1 is a breaker that has STOPPED "
                     "calling its backend -- every request on that route now fails fast with a 503 without ever "
                     "reaching the service. Half-open is the probe state: the breaker is letting a few calls "
                     "through to decide whether to close again."))
    p.append(ts("Slow-call rate (%) -- the threshold that OPENS the breaker on a healthy backend", 8, 200, 8,
                "percent",
                [tgt('max by (name) (resilience4j_circuitbreaker_slow_call_rate{%s})' % SVC, "{{name}}")],
                minv=-1,
                desc="THE number to watch under load, and it carries a trap: Resilience4j reports -1, NOT 0, "
                     "while the breaker has seen fewer than minimum-calls -- it means NO VERDICT YET, not "
                     "'0% slow'. The axis starts at -1 so that reads honestly; clamping it to 0 would paint "
                     "'healthy' over 'no data'. A call counts as slow past slow-call-seconds even when it "
                     "SUCCEEDS, so this rate -- not the failure rate -- is what opens a breaker on a backend "
                     "that is working perfectly and merely slow."))
    p.append(ts("Failure rate (%) -- the threshold that opens on a backend that is actually failing", 16, 200, 8,
                "percent",
                [tgt('max by (name) (resilience4j_circuitbreaker_failure_rate{%s})' % SVC, "{{name}}")],
                minv=-1,
                desc="Same -1 = not-enough-calls convention as the slow-call rate. This one rises only on real "
                     "errors, so a breaker opening HERE is the breaker doing its job. A breaker opening on the "
                     "slow-call rate while this stays flat is the failure mode T10 exists to prevent."))
    p.append(ts("Calls through the breaker (by outcome)", 0, 208, 8, "ops",
                [tgt('sum by (name, kind) (rate(resilience4j_circuitbreaker_calls_seconds_count{%s}[1m]))' % SVC,
                     "{{name}} {{kind}}")],
                desc="What the breaker actually saw: successful / failed / ignored. Read together with the rate "
                     "panels above -- these are the calls the rates are computed FROM, so a rate that looks "
                     "alarming on a handful of calls is not yet a signal."))
    p.append(ts("Calls REFUSED by the breaker (the 503s the breaker itself caused)", 8, 208, 8, "ops",
                [tgt(safe('sum by (name) '
                          '(rate(resilience4j_circuitbreaker_not_permitted_calls_total{%s}[1m]))' % SVC),
                     "{{name}} refused")],
                desc="The smoking gun, and the reason this row was built. Every call counted here NEVER REACHED "
                     "the backend -- the breaker rejected it at the gateway. If a load run collapses while this "
                     "line is up, the collapse is the BREAKER's doing, not the service's, and the fix is to tune "
                     "the breaker rather than to chase a backend that was never even called. Guarded with "
                     "or vector(0) so it reads a flat zero instead of vanishing when nothing is refused."))
    p.append(ts("Per-route deadline -- TimeLimiter outcomes", 16, 208, 8, "ops",
                [tgt(safe('sum by (name, kind) (rate(resilience4j_timelimiter_calls_total{%s}[1m]))' % SVC),
                     "{{name}} {{kind}}")],
                desc="The REAL per-route deadline is the breaker's TimeLimiter, not the Netty response-timeout. "
                     "A rising 'timeout' line means calls are being cancelled at the deadline -- those count as "
                     "FAILURES at the breaker, so a timeout storm drives the failure rate up and opens the "
                     "breaker on top of the slowness that caused it."))

    # ---- Capacity: cores, not percentages ----
    # "We have 24 cores -- are we using them?" was unanswerable from this dashboard, and the CPU row above is
    # why: process_cpu_usage is a fraction of the JVM's OWN effective CPUs. Multiply it by system_cpu_count and
    # the fraction becomes CORES, which is the same unit on every target and can be summed and compared against
    # the machine.
    #
    # The trap this row exists to expose: a JVM is container-aware. It reads the cgroup quota, NOT nproc, and
    # sizes GC threads / the ForkJoin common pool / Reactor's event loops from what it finds. Under the local-k8s
    # R4 budget (limits.cpu=1) `nproc` inside the pod still says 24 while the JVM reports an Effective CPU Count
    # of 1 -- so the JVM BUILDS ITSELF for a single-core machine, on a 24-core host. That is not throttling at the
    # margin; it is a different JVM. Measured 2026-07-13: raising the limit from 1 to 6, changing nothing else,
    # DOUBLED throughput (76,748 -> 151,825 requests). The "effective CPUs" panel is where you SEE it -- a flat
    # line at 1 across every replica while the host has 24 idle cores.
    # The on(instance) group_left join needs system_cpu_count PRESENT for a replica to appear in these panels: a
    # replica with process_cpu_usage but no system_cpu_count cannot be converted to cores (the multiplier is
    # unknown) and DROPS from the join. That is honest -- fabricating a count would be the same plausible-lie this
    # dashboard exists to refuse -- and it is not silent: the drop shows as a GAP on the "Effective CPUs" panel
    # below, which reads system_cpu_count directly. Measured live 2026-07-15: 14 = 14 = 14 (process_cpu_usage,
    # the join, and system_cpu_count all carry the same instances), so the two are co-present in practice and a
    # gap here means a scrape gap on that replica, not a wrong number.
    # Capacity is a MACHINE row (see the identity note near the top): it filters by $application, like the
    # two panels beside it. On $service it emptied for every composed sub-service while they stayed full.
    CORES = "(process_cpu_usage{%s} * on(instance) group_left system_cpu_count{%s})" % (APP, APP)
    p.append(row("Capacity -- cores in use (are we using the machine?)", 216))
    p.append(ts("Cores in use -- TOTAL across all services", 0, 217, 12, "short",
                [tgt("sum(%s)" % CORES, "cores in use")],
                desc="The headline number: how many CPU cores the Esquire services are actually burning, right "
                     "now, added up. Compare it against the host's core count. If this plateaus well BELOW the "
                     "machine while latency climbs, CPU is not the limit and tuning the CPU budget will not help "
                     "-- look at a pool, a lock, or a serialized path instead."))
    p.append(ts("Cores in use by replica", 12, 217, 12, "short",
                [tgt(CORES, "{{application}} {{instance}}")],
                desc="Cores, per replica -- process_cpu_usage re-expressed in the one unit that means the same "
                     "thing on docker and on k8s. A replica pinned flat against its ceiling is CPU-bound; the "
                     "next panel says what that ceiling is."))
    p.append(ts("Effective CPUs the JVM sized itself for", 0, 225, 12, "short",
                [tgt("system_cpu_count{%s}" % APP, "{{application}} {{instance}}")],
                desc="What each JVM believes it is running on -- Runtime.availableProcessors(), which on a "
                     "container is the CGROUP QUOTA, not the host's core count. This is the panel that catches "
                     "the trap: under the local-k8s R4 budget it reads 1 on every replica while the host has 24 "
                     "cores sitting idle, and the JVM has already sized its GC, ForkJoin and event-loop threads "
                     "for that 1. A number here that surprises you is a capacity bug, not a display quirk."))
    p.append(ts("Host CPU -- the whole machine", 12, 225, 12, "percentunit",
                [tgt("max(system_cpu_usage{%s})" % APP, "host"),
                 tgt("avg(system_cpu_usage{%s})" % APP, "host (avg of reporters)")],
                desc="Whole-machine CPU load. Read it WITH 'Cores in use': the load generator and the "
                     "observability stack live on this same host, so a busy machine is not the same thing as a "
                     "busy Esquire."))
    return p


def build_dashboard():
    panels = build_panels()
    check_no_naked_subtraction(panels)
    check_rows_do_not_share_y(panels)   # refuse a panel that shares a row header's y
    check_no_panel_overlap(panels)   # refuse two panels whose RECTANGLES intersect
    check_no_clamped_rate_denominator(panels)   # refuse a clamp_min(rate(count),1) denominator -- the plausible-lie trap
    check_avg_scales_only_seconds(panels)   # refuse a x1000 on a metric whose name does not say _seconds
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
            # up{} carries NO `application` label (only job/instance), so label_values(up, application) returned
            # nothing and the picker collapsed to just "All". `application` is a Micrometer common tag on the Java
            # services; jvm_memory_used_bytes is a reliable Java-only source of it, so the picker lists exactly the
            # services the $application panels filter on. The Node BFF has no jvm_* and stays out, matching its
            # own panels that hardcode application="esq-backend".
            "query": "label_values(jvm_memory_used_bytes, application)", "refresh": 2,
            "includeAll": True, "multi": True, "allValue": ".+",   # .+ NOT .* -- see the note at the picker definitions
            "current": {"text": "All", "value": "$__all"}, "sort": 1,
        }, {
            "name": "service", "label": "Esquire service", "type": "query", "datasource": DS,
            # The WORK picker, and it reads the BARE label rather than one metric's. Every candidate metric
            # leaves someone out: jvm_memory_used_bytes only ever names PROCESSES (one heap each), so it would
            # list `mesnie` and never `keysmith`; http_server_requests names only the services with a REST door,
            # so kcMaster -- reached over the bus alone -- would never appear. The bare label is the union of
            # everything that has reported.
            #
            # KNOWN GAP, and it is not this picker's doing: kcMaster's only meters are esq.biz.kc.sync.*, which
            # Micrometer materialises on FIRST USE. Until an identity operation runs, kcMaster has no series at
            # all and cannot be listed by any query. It appears as soon as one does.
            "query": "label_values(service)", "refresh": 2,
            "includeAll": True, "multi": True, "allValue": ".+",   # .+ NOT .* -- see the note at the picker definitions
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
                       "which is the question the whole correlation-id design exists to answer. WHAT LOKI HOLDS "
                       "DIFFERS BY TARGET, because the 3-tier design routes on the Spring profile. On docker no "
                       "profile is set, so develop and msg go to rolling FILES and Loki holds the console tier "
                       "alone -- errors, warnings, startup -- and a healthy request leaves no line here. On k8s "
                       "and OKE the pods run SPRING_PROFILES_ACTIVE=console, so develop and msg go to stdout too "
                       "and Alloy ships all three tiers: there a healthy request DOES leave its per-request trail."))
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
                "refresh": 2, "includeAll": True, "multi": True, "allValue": ".+",   # .+ NOT .* -- see the note at the picker definitions
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
    global ESQ_SERVICES
    here = os.path.dirname(os.path.abspath(__file__))
    svc_root = os.path.abspath(os.path.join(here, "..", "..", ".."))   # compose-compact/o11y/grafana -> services
    compose_dir = os.path.join(svc_root, "compose-compact", "o11y", "grafana", "provisioning", "dashboards")
    k8s_dir = os.path.join(svc_root, "k8s-compact", "charts", "infra", "grafana", "dashboards")
    oke_dir = os.path.join(svc_root, "k8s-oci-compact", "grafana")

    # This is the COMPACT generator: it writes ONLY into the compact trees. The classic generator is its own file
    # under compose/o11y/grafana and owns the classic boards; neither may write the other's artifact.
    #
    # docker + k8s draw the full fleet; OKE super-compact has NO auKeep (audit = DB triggers), so its boards drop
    # auKeep from the service set -- which also skips the 'Audit keep -- DB writes' panel (guarded on
    # `"aukeep" in ESQ_SERVICES`). Same fork the classic generator makes, for the same reason. The OKE board is
    # fed to helm with --set-file, so it is a committed artifact: generating it is what keeps it from drifting.
    services_full = ESQ_SERVICES
    services_oke = ESQ_SERVICES.replace("aukeep|", "")
    for target_dir, svcs in ((compose_dir, services_full), (k8s_dir, services_full), (oke_dir, services_oke)):
        ESQ_SERVICES = svcs
        os.makedirs(target_dir, exist_ok=True)
        for name, builder in (("esquire-services", build_dashboard),
                              ("esquire-logging", build_logging_dashboard)):
            path = os.path.join(target_dir, "%s.json" % name)
            with open(path, "w") as f:
                json.dump(builder(), f, indent=1)
            print("wrote", path, "(OKE -- no auKeep)" if svcs is services_oke else "")


if __name__ == "__main__":
    main()
