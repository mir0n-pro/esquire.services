#!/usr/bin/env python3
# Esquire frameworks (tm) -- Grafana TOPOLOGY dashboard generator (v1.2.11 observability, T9-C).
#
# THE CLASSIC topology generator: single source of truth for the "Esquire Topology" board on the classic
# profile. Emits the SAME JSON to all THREE classic targets so they never drift:
#   * docker : compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
#   * k8s    : k8s/charts/infra/grafana/dashboards/esquire-topology.json
#   * OKE    : k8s-oci/grafana/esquire-topology.json
# The compact generator is its own file under compose-compact/o11y/grafana; neither writes the other's tree.
# Run with no arguments (python gen-topology.py) after changing a panel; commit the .py AND all three .json.
#
# ===============================================================================================================
#  THE PICTURE IS LOCKED  (mir0n, 2026-07-12).
#
#  Do not restyle it, re-lay it out, or "improve" it. Every decision below was made deliberately, several of them
#  after getting it wrong first, and each one is load-bearing:
#
#    * ELEVEN COMPONENTS. Redis, Kafka and the browser came OFF. Redis/Kafka are the ALTERNATE audit sinks and sit
#      idle on this deployment; the frontend is not tracked. A permanently-idle box on a LIVE board teaches the
#      reader to ignore idle boxes -- the one habit a topology view must never build. They remain in
#      doc/media/ComponentModel.png, which is where an unwired alternate belongs.
#    * THE OBSERVABILITY STACK IS NOT ON IT. The viewer is not the system.
#    * THREE VERTICAL BUS LANES, not one bar and not a mesh. A mesh would assert the very coupling the bus exists
#      to remove; one bar would hide the only thing the picture is FOR -- which services share a medium and which
#      never meet. bizTree and keySmith never touch. auKeep hears everyone and answers no one. enyMan is on all three.
#    * DIRECTION ON EVERY TAP. publish = head on the lane; consume = head on the service; the IAM bus is
#      request/reply so every participant is DOUBLE-headed; the broker has NO head -- it IS the medium.
#    * THREE LINE COLOURS: lane colour = async bus, RED = database, BLACK = REST. A DB call and a REST call both
#      block, but they fail differently and are owned differently. Weights: a bus lane is 2 (a shared medium), a
#      point-to-point call is 1.25 (one wire between two components).
#    * GLUE POINTS ARE SPREAD ALONG EACH EDGE, counting bus taps and p2p lines TOGETHER, and ORDERED BY THE
#      DIRECTION EACH LINE HEADS -- lines going up take the top slots, lines going down the bottom, bus taps
#      (horizontal) the middle. That is geometry, not a rule about "kinds": a kind-rule got keySmith wrong,
#      because Esq2025 sits ABOVE keySmith while it sits below the other three.
#    * BUS TAPS ARE HORIZONTAL (0 degrees). Both ends of a tap move together, so it slides rather than tilts.
#
#  THE K8S STACK IS LOCKED TOO  (mir0n, 2026-07-12):
#
#    * ONE CARD PER INSTANCE, each with its OWN health colour, its OWN name ("auKeep 0" / "auKeep 1") and its OWN
#      three vitals. An aggregate cannot see a half-dead pair (count > 0 is still 1) and cannot see an UNBALANCED
#      one -- and an unbalanced pair is a pair about to become a single.
#    * ONE ICON and ONE SET OF ARROWS per component, both on the FRONT card. The lines belong to the COMPONENT; an
#      arrow leaving the lower card would read as though the gateway routed to that one instance.
#    * INSTANCE 0 IS THE FRONT CARD ON EVERY STACK -- it carries the icon, the label and every arrow, so "the
#      front card" means the same thing everywhere. auKeep is MIRRORED because all of its wiring leaves to the
#      right and below: its cards behind fan out to the LEFT, so the stack opens away from its own lines rather
#      than across them. The mirror flips the GEOMETRY, never the identity (auKeep alone reads 1,0 left to right,
#      which is harmless: every card is labelled with its own instance number).
#    * `or vector(0)` ON THE VITALS, not just the health. A counter that has never been incremented HAS NO SERIES,
#      and Grafana prints the words "Field not found" across the card. An absent value is a zero, not an error.
#    * THE IDLE TWIN goes AMBER: the replicas are active-active, so an instance at zero while its twin serves is
#      half the capacity missing, not a spare doing its job. It is a WARNING, not a symbol on the drawing -- there
#      is no doubled arrowhead in canvas, and a static glyph would claim "this reaches both" on a day when it did
#      not. Set IDLE_TWIN = False to remove it from every card; nothing else to undo.
#
#  If the ARCHITECTURE changes, change this file. If the picture merely looks wrong to you, look again.
# ===============================================================================================================
#
# Single source of truth for the "Esquire Topology" dashboard. Emits the SAME JSON to both deploy targets:
#   * docker : compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
#   * k8s    : k8s/charts/infra/grafana/dashboards/esquire-topology.json
#
# ---------------------------------------------------------------------------------------------------------------
# WHAT THIS VIEW IS -- and why the SHAPE of the drawing is an assertion about the architecture.
#
# THE ASYNC MESSAGING IS DRAWN AS BUSES -- THREE OF THEM, one bar each. Not a mesh, and not one bar.
#
#   * Not a mesh: enyMan does not call bizTree. It publishes to a destination and walks away, and who is listening
#     is not its business. Service-to-service arrows would assert exactly the coupling the bus exists to remove,
#     and a picture that implies the wrong architecture is worse than no picture at all.
#   * Not ONE bar: this file drew a single bar first, and that was wrong (mir0n caught it). There are THREE buses
#     -- esquire.entity, esquire.kc, audit-c -- and that is the entire point of a bus CATALOG. One bar throws away
#     the only thing the picture is FOR: which services share a medium and which never meet. bizTree and keySmith
#     never touch. auKeep hears everyone and answers no one. enyMan sits on all three. A single bar hides all of it.
#
# REST and DB calls ARE point-to-point -- something called and WAITED for an answer -- so those get ARROWS. The
# shape carries the meaning: an arrow is a call with an answer; a drop onto a bar is a publish with no answer.
#
# EVERY BOX CARRIES A LIVE NUMBER, not just a colour (mir0n). A traffic light tells you a thing is RUNNING; it
# does not tell you it has stopped doing any WORK, which is the failure that actually happens. Every box also
# LINKS THROUGH to the detail for that component -- the board is the way in, not the whole story.
#
# The OBSERVABILITY STACK is deliberately NOT on this board (mir0n): the viewer is not the system.
# ---------------------------------------------------------------------------------------------------------------

import re
import json
import os

DS = {"type": "prometheus", "uid": "esq-prometheus"}
DS_TEMPO = {"type": "tempo", "uid": "esq-tempo"}

# name -> (label, health expr, headline expr, unit, drill-down link)
#
# Each health signal comes from somewhere different (Actuator / prom-client / a JMX agent / postgres-exporter /
# a Quarkus mgmt port / a TCP probe / native /metrics) and the board deliberately does not care: every box is
# coloured from the same field.
#
# NOTE the job filter on the Java services: KeyCloak is Quarkus and ALSO emits process_uptime_seconds, with NO
# application tag -- without the filter it arrives as a NAMELESS series and lands on the board as a blank box.
# EVERY health expression ends in `or vector(0)`. THIS IS THE WHOLE POINT OF THE COLOUR, and without it the
# board lies in the one moment it exists for:
#
#   A component that is DOWN does not report 0. It reports NOTHING -- the series VANISHES, because the thing that
#   would have reported is the thing that died. count() over zero series yields an EMPTY VECTOR, not a zero. The
#   canvas then finds no value for the field and falls back to the element's FIXED colour -- GREY. So the box for
#   a dead service sat there GREY, looking like "no data yet", while the service was on the floor.
#
#   `or vector(0)` turns absence into an explicit 0, which trips the threshold and paints it RED.
#
# Verified by stopping esq-pacman: without the guard its health query returned EMPTY; with it, 0.
SVC = 'job="esquire-services"' 
C = {}


# ---------------------------------------------------------------------------------------------------------------
# ONE CARD PER INSTANCE -- and every card carries its OWN numbers (mir0n).
#
# Docker runs one of everything. Local k8s runs the seven Java services and the BFF x2, and there a SINGLE box per
# component is a lie twice over:
#
#   1. HEALTH cannot see a half-dead pair. `count(...) > 0` is still 1 when one of the two is gone, so the box
#      stays GREEN. "Running on one leg" -- the one thing an HA board exists to say -- was invisible.
#   2. The NUMBERS hide the thing you look at a pair of replicas FOR. An aggregate says the component is doing
#      40 req/s at 45% CPU. It cannot say that ONE replica is doing all of it at 90% while its twin sits idle --
#      and an unbalanced pair is not a healthy pair, it is a pair that is about to become a single.
#
# So on k8s a component is drawn as a STACK: one card per instance, each with its own health colour, its own name
# ("auKeep 0" / "auKeep 1") and its own three vitals. The cards behind are offset RIGHT and DOWN, so each one
# keeps a clear strip in which to show its numbers; only the FRONT card carries the icon, and every arrow is drawn
# ONCE, against the stack (mir0n).
#
# Every query therefore comes in two flavours: aggregate (docker, one instance) and per-instance. The ONLY
# difference is a label matcher on `instance`, which the
# k8s scrape sets to the POD NAME -- and every app component is a StatefulSet, so the pod name ends in its ordinal
# (esquire-enyman-enyman-0). That ordinal IS the replica number, stable across restarts.
# ---------------------------------------------------------------------------------------------------------------

def AND(r):
    """The instance matcher, to ADD to an existing label set: `{application="x"` + AND(r) + `}`."""
    return "" if r is None else ', instance=~".*-%d$"' % r


def ONLY(r):
    """The instance matcher as a WHOLE label set, for a metric that carries none: `metric` + ONLY(r)."""
    return "" if r is None else '{instance=~".*-%d$"}' % r


def SSEL(name, r):
    """The Java-service selector: job + application (+ instance)."""
    return 'job="esquire-services", application="%s"%s' % (name, AND(r))


def ASEL(name, r):
    """The application-only selector, for meters that carry no job label."""
    return 'application="%s"%s' % (name, AND(r))


def comp(name, label, health, vitals, link, left, top, probe=False, shape="rectangle", alarm=None):
    """A component: a health colour and THREE LIVE VITALS -- per instance.

    One number was not enough (mir0n). A colour says the process is RUNNING; a single req/s says it is serving.
    Neither says it is about to fall over. The third row is the one that gives warning -- heap climbing, CPU
    pinned, the pool exhausted, the broker filling -- and warning is the only thing a live board is FOR.

    `health` and `vitals` are FUNCTIONS of a replica ordinal r (None = aggregate / single instance), because the
    same component is drawn once on docker and twice on k8s and the two boards must not drift into two tables.
    `vitals(r)` -> [(expr, unit, short-name), ...] -- max 3; the unit is applied per FIELD via fieldConfig, so the
    card renders "108 MB" / "1.2%" / "0.3 req/s" and not three naked numbers.
    """
    C[name] = dict(label=label, health_of=health, vitals_of=vitals, link=link,
                   left=left, top=top, probe=probe, shape=shape,
                   alarm_of=alarm if alarm is not None else (lambda r: "vector(0)"))



# ---------------------------------------------------------------------------------------------------------------
# HEALTH IS THREE-STATE, NOT TWO.  0 = DOWN (red) | 1 = TROUBLE (amber) | 2 = OK (green)
#
# UP/DOWN IS NOT MONITORING. A service pinned at 100% CPU, a heap about to OOM, a queue backing up, 5xx errors
# pouring out -- every one of those is "up", and a two-state board paints them all GREEN. Green would then mean
# "the process is alive", which is the least interesting thing anyone can say about it, and the board would be at
# its most reassuring exactly when it should be shouting.
#
#   score = alive * (2 - trouble)
#
#     alive   : 1 if it is reporting at all, else 0     -- `or vector(0)`, because a DEAD component reports
#               NOTHING, not zero: the series vanishes, and an absent value paints the box GREY, not red.
#     trouble : 1 if ANY warning condition is firing    -- clamped, so three problems are still one amber box;
#               the box says "look here", the TROUBLE banner says what.
#
# The thresholds that follow are deliberately BLUNT (CPU 80%, heap 85%, pool 90%, depth 100, consumers 0). A
# monitoring board is not an alerting rule engine: its job is to point, not to adjudicate.
# ---------------------------------------------------------------------------------------------------------------

def alive(expr):
    """1 when reporting, 0 when absent. NEVER let an absent series stay absent -- see above."""
    return "clamp_max((%s) or vector(0), 1)" % expr


def health(alive_expr, *warnings):
    """alive * (2 - any-warning)  ->  0 DOWN / 1 TROUBLE / 2 OK."""
    if warnings:
        w = " + ".join("((%s) or vector(0))" % x for x in warnings)
    else:
        w = "vector(0)"
    return "(%s) * (2 - clamp_max((%s), 1))" % (alive(alive_expr), w)


# ---- ONE place for every blunt threshold + the alarm window (mir0n: UNIFIED, so nothing can drift) --------------
# The FILL-health thresholds. keycloak and the BFF re-express these INLINE (they select by job=, not application=),
# and they read the SAME constants -- a retune here moves every card, none is left behind.
CPU_FRAC = 0.80        # process CPU as a fraction of the pod's 1-core limit -> amber fill
HEAP_PCT = 85          # % of max heap -> amber fill
POOL_PCT = 90          # % of the DB connection pool -> amber fill
# The ALARM (border) window. NOT a latch: an event stays counted for this long, so the border holds ~this long then
# self-clears -- no alerting engine, no "for" duration ("the card IS the alert"). ONE dial for EVERY alarm term
# (5xx / error-log / failure-counter), so they all hold for the SAME time. mir0n: default 1m.
ALERT_HOLD = "1m"

# The FILL (health) warning conditions. `> bool` yields 1/0 and keeps the expression aggregatable. Each takes a
# full SELECTOR, so the same condition serves docker (whole component) or one k8s instance with a label added.
def w_cpu(sel):        return "(max(process_cpu_usage{%s}) > bool %g)" % (sel, CPU_FRAC)
def w_heap(sel):       return ("(100 * sum(jvm_memory_used_bytes{%s, area=\"heap\"}) / "
                               "sum(jvm_memory_max_bytes{%s, area=\"heap\"}) > bool %d)" % (sel, sel, HEAP_PCT))
def w_pool(sel):       return ("(100 * sum(hikaricp_connections_active{%s}) / "
                               "sum(hikaricp_connections_max{%s}) > bool %d)" % (sel, sel, POOL_PCT))

# The BORDER (alarm) terms -- ALL share the ALERT_HOLD window (mir0n: unified). 5xx is an alarm (a service SHEDDING
# errors), NOT its own health, so it rides the border and holds for the SAME window as the rest -- was a stray
# rate([5m]) before, which lingered 5x longer than every other alarm.
def w_5xx(sel):        return ("(sum(increase(http_server_requests_seconds_count{%s, "
                               "status=~\"5..\"}[%s])) > bool 0)" % (sel, ALERT_HOLD))
# An ERROR log in the last ALERT_HOLD. logback_events_total is Boot's per-service log-event counter -- same
# datasource as the card, so the colour needs no Loki query.
def w_errlog(sel):     return "(sum(increase(logback_events_total{%s, level=\"error\"}[%s])) > bool 0)" % (sel, ALERT_HOLD)
# A resilience4j breaker OPEN -- the component has started SHEDDING calls to a downstream. Every breaker lives on
# the gateway (it wraps each downstream), so this term is the gateway's alone. (A state gauge, so no window.)
def w_breaker(sel):    return "(max(resilience4j_circuitbreaker_state{%s, state=\"open\"}) > bool 0)" % sel
# A failure counter that TICKED in the last ALERT_HOLD. `or vector(0)` is mandatory (I9): these counters have NO
# series until the first failure, and an absent series must read 0, not vanish -- a vanished term can never fire.
def w_fail(sel, metric): return "((sum(increase(%s{%s}[%s])) or vector(0)) > bool 0)" % (metric, sel, ALERT_HOLD)


# --- TWO CHANNELS (mir0n) ---------------------------------------------------------------------------------------
# The card's FILL is HEALTH (up/down + CPU/heap/pool/5xx) -- how the component ITSELF is doing. The card's BORDER
# is a separate ALARM -- an error log / a tripped breaker / a failure counter, which is usually a DEPENDENCY going
# wrong, not the component itself. So when Postgres dies, its own card fills RED, and every service that talks to it
# stays GREEN (its process is fine) but rims RED (it is logging DB errors). One fill colour cannot say both.
# The alarm carries the SAME 1m tail (ALERT_HOLD) as its terms, so a one-off error stays visible for a minute.
def alarm(*terms):
    """1 when ANY alarm term fired, else 0 -- the BORDER channel. Absent series read 0 (I9); no terms -> vector(0),
    a clean 0 for an infra card that has no alarm of its own."""
    if not terms:
        return "vector(0)"
    s = " + ".join("((%s) or vector(0))" % t for t in terms)
    return "clamp_max((%s), 1)" % s


# ---------------------------------------------------------------------------------------------------------------
# THE IDLE TWIN  --  k8s only, and the reason the per-instance cards exist at all.
#
# The replicas are ACTIVE-ACTIVE. They are competing consumers on the bus and round-robin targets behind a Service;
# neither is a standby, and nothing fails over to anything. So an instance sitting at ZERO while its twin serves is
# not "the spare doing its job" -- it is HALF THE CAPACITY MISSING, and the pair is one bad pod away from being a
# single. An aggregate board cannot see it at all: it would report the component as healthy and serving, which is
# true of the PAIR and false of half of it.
#
# It is a WARNING, not a line on the drawing. mir0n asked whether a doubled arrowhead could show the fan-out;
# Grafana's canvas has no such head, and a static glyph would assert "this reaches both" even at a moment when it
# does not. Colour says it only WHEN IT IS TRUE, and stays silent otherwise -- which is what it does today: the
# traffic across all eight pairs is even, and every card is green.
#
#   twin is working (> MIN)  AND  I am at exactly zero   ->  amber
#
# The window is LONG (15m) and the twin's floor is not zero: over five minutes a quiet pair can trade a single
# request and paint each other amber in turn, which would be noise dressed as a finding. Fifteen minutes of the
# twin doing real work while this one does literally none is not a sampling artifact.
#
# ONE SWITCH: set IDLE_TWIN = False and the term is gone from every card -- nothing else to undo.
# ---------------------------------------------------------------------------------------------------------------
IDLE_TWIN = True
IDLE_TWIN_WINDOW = "15m"
IDLE_TWIN_MIN = 0.01          # the twin must be doing REAL work (~9 events in the window), not one stray request


def w_idle_twin(metric, all_sel, mine_sel):
    """Amber when this instance is doing NOTHING and its twin is doing the work. See above."""
    everyone = '(sum(rate(%s{%s}[%s])) or vector(0))' % (metric, all_sel, IDLE_TWIN_WINDOW)
    mine = '(sum(rate(%s{%s}[%s])) or vector(0))' % (metric, mine_sel, IDLE_TWIN_WINDOW)
    return "((%s - %s > bool %s) * (%s == bool 0))" % (everyone, mine, IDLE_TWIN_MIN, mine)


def w_idle(metric, name, r, sel=ASEL):
    """The idle-twin term for card r -- empty (no warning) on a single-instance board, where there is no twin."""
    return [] if (not IDLE_TWIN or r is None) else [w_idle_twin(metric, sel(name, None), sel(name, r))]


REQ = 'sum(rate(http_server_requests_seconds_count{%s}[5m]))'
SERVICES_D = "/d/esq-services/?var-application=%s"

# ---------------------------------------------------------------------------------------------------------------
# LAYOUT: this board is deliberately THE SAME PICTURE as doc/media/ComponentModel.png -- the component model that
# already ships in the docs. Not a second, differently-arranged drawing of the same system.
#
# That matters more than it sounds. A monitoring board and an architecture diagram that disagree about WHERE
# things are force the reader to re-learn the system twice and to hold two mental maps at once; and when they
# disagree about WHAT connects to WHAT, one of them is lying and nobody knows which. Same arrangement, same bus
# names, same left-to-right flow -- so the live board reads as the documented model, with the numbers switched on.
#
# From the model, left to right:
#   auKeep + the audit store   |   Esq2025 (DB)   |   Redis / Kafka   |   THE THREE VERTICAL BUS LANES   |
#   the services, stacked      |   gateway        |   KeyCloak / BFF  |   the browser
#
# The lanes are VERTICAL (as in the model, and as mir0n asked): a bus is a line you TAP, anywhere along it.
# auKeep sits on the LEFT of the lanes because it only ever DRAINS -- it publishes nothing. That asymmetry is in
# the model, and it is worth keeping: it is the shape of "hears everyone, answers no one".
# ---------------------------------------------------------------------------------------------------------------

# AVG, not sum: a PERCENTAGE DOES NOT SUM. Aggregated over a k8s pair, sum(cpu)*100 would read up to 200% for a
# perfectly healthy one. Per instance the avg is over a single series, so the same expression serves both.
CPU = 'avg(process_cpu_usage{%s}) * 100'
HEAP = 'sum(jvm_memory_used_bytes{%s, area="heap"})'
MSG = 'sum(rate(messaging_receive_total{%s}[5m]))'

# ---- LEFT of the buses: the audit sink, the store ----
comp("aukeep", "auKeep",
     lambda r: health('count(process_uptime_seconds{%s})' % SSEL("aukeep", r),
                      w_cpu(SSEL("aukeep", r)),
                      w_heap(ASEL("aukeep", r)),
                      w_pool(ASEL("aukeep", r)),
                      *w_idle("messaging_receive_total", "aukeep", r)),
     lambda r: [(MSG % ASEL("aukeep", r), "ops", "msg/s"),
                (CPU % SSEL("aukeep", r), "percent", "cpu"),
                (HEAP % ASEL("aukeep", r), "bytes", "heap")],
     SERVICES_D % "aukeep", 30, 40,
     alarm=lambda r: alarm(w_errlog(ASEL("aukeep", r)),
                           w_fail(ASEL("aukeep", r), "messaging_error_total")))
comp("postgres", "Esq2025",
     lambda r: health('max(pg_up)',
                      # connections near the server limit -- the failure that takes EVERY service down at once
                      '(100 * sum(pg_stat_database_numbackends) / max(pg_settings_max_connections) > bool 80)',
                      # a cache hit-rate collapse means it has started going to disk for everything. I17: the ratio
                      # MUST be a LIVE signal -- rate() over a window, NOT the cumulative counters, whose lifetime
                      # ratio barely moves and so a recent collapse cannot trip it (a dead threshold). The trailing
                      # `and reads>0` makes it EMPTY when the DB is idle (no reads -> 0/0 = NaN), so health()'s
                      # `or vector(0)` reads it as healthy rather than a NaN poisoning the whole warning sum.
                      '((100 * sum(rate(pg_stat_database_blks_hit[5m])) / '
                      '(sum(rate(pg_stat_database_blks_hit[5m])) + sum(rate(pg_stat_database_blks_read[5m]))) '
                      '< bool 90) and (sum(rate(pg_stat_database_blks_read[5m])) > 0))'),
     lambda r: [('sum(pg_stat_database_numbackends)', "short", "conns"),
                ('sum(rate(pg_stat_database_xact_commit[5m]))', "ops", "tx/s"),
                # LIVE, like the WARN term above it: a cumulative ratio barely moves after a few hours, so
                # the card read a flat ~99% while the amber warning beside it tripped on the real collapse.
                ('100 * sum(rate(pg_stat_database_blks_hit[5m])) / '
                 '(sum(rate(pg_stat_database_blks_hit[5m])) + sum(rate(pg_stat_database_blks_read[5m])))',
                 "percent", "cache")],
     "/d/esq-services/", 30, 250)

# ---- RIGHT of the buses: the services, in the component model's order ----
# Failure counters that belong to a SPECIFIC service (I30). Generic messaging errors alarm every card below; these
# are the ones with a single home -- the move pipeline is enyMan's. They ride the BORDER (alarm), not the fill.
SVC_FAIL = {"enyman": ["esq_biz_move_failed_total"]}


def _svc(n):
    """The four REST services differ only by name -- bind it, or every lambda would close over the loop variable
    and all four would end up describing whichever service happened to be last. FILL = health (cpu/heap/5xx/pool);
    BORDER = alarm (error log + messaging errors + this service's own failure counters)."""
    def h(r):
        warns = [w_cpu(SSEL(n, r)), w_heap(ASEL(n, r)), w_pool(ASEL(n, r))]
        warns += w_idle("http_server_requests_seconds_count", n, r)
        return health('count(process_uptime_seconds{%s})' % SSEL(n, r), *warns)

    def a(r):
        # 5xx rides the BORDER too (mir0n): a service returning errors is SHEDDING, an alarm, not its own health --
        # so its fill stays green (it is up, resources fine) and the border alarms.
        terms = [w_5xx(ASEL(n, r)), w_errlog(ASEL(n, r)), w_fail(ASEL(n, r), "messaging_error_total")]
        terms += [w_fail(ASEL(n, r), m) for m in SVC_FAIL.get(n, ())]
        return alarm(*terms)
    return (h, a,
            lambda r: [(REQ % ASEL(n, r), "reqps", "req/s"),
                       (CPU % SSEL(n, r), "percent", "cpu"),
                       (HEAP % ASEL(n, r), "bytes", "heap")])


for _n, _lbl, _top in (("pacman", "pacMan", 30), ("biztree", "bizTree", 130),
                       ("enyman", "enyMan", 230), ("keysmith", "keySmith", 330)):
    _h, _a, _v = _svc(_n)
    comp(_n, _lbl, _h, _v, SERVICES_D % _n, 560, _top, alarm=_a)

# kcMaster has NO REST door and NO database -- it is reached only over the bus and owns no state, so its work
# shows as MESSAGES, not requests. Putting req/s on it would be a permanently-zero number pretending to be a vital.
comp("kcmaster", "kcMaster",
     lambda r: health('count(process_uptime_seconds{%s})' % SSEL("kcmaster", r),
                      w_cpu(SSEL("kcmaster", r)),
                      w_heap(ASEL("kcmaster", r)),
                      *w_idle("messaging_receive_total", "kcmaster", r)),
     lambda r: [(MSG % ASEL("kcmaster", r), "ops", "msg/s"),
                (CPU % SSEL("kcmaster", r), "percent", "cpu"),
                (HEAP % ASEL("kcmaster", r), "bytes", "heap")],
     SERVICES_D % "kcmaster", 560, 430,
     alarm=lambda r: alarm(w_errlog(ASEL("kcmaster", r)),
                           w_fail(ASEL("kcmaster", r), "messaging_error_total")))

# ---- the broker: beneath the lanes, CENTRED ON THE MIDDLE ONE ----
# MEMORY is the one that matters here: the broker is NON-PERSISTENT, so the bus lives in RAM and memory% is what
# back-pressures producers when a consumer dies. There is no store line because there is no store.
comp("activemq", "ActiveMQ",
     lambda r: health('max(activemq_broker_uptime_millis)',
                      # memory is the one that matters: the broker is NON-PERSISTENT, so the bus lives in RAM and
                      # THIS is what back-pressures producers when a consumer dies
                      '(max(activemq_broker_memory_percent_usage) > bool 70)',
                      # a backlog anywhere
                      '(max(activemq_queue_depth) > bool 100)',
                      # a destination with NO consumer -- the process can be alive and have stopped listening, and
                      # nothing on the service side can report that. Only the broker can see it.
                      '(min(activemq_queue_consumer_count) < bool 1)'),
     lambda r: [('max(activemq_broker_total_message_count)', "short", "held"),
                ('max(activemq_broker_current_connections_count)', "short", "conns"),
                ('max(activemq_broker_memory_percent_usage)', "percent", "memory")],
     "/d/esq-services/", 333, 596)

# ---- the edge: gateway -> Explorer (BFF), and KeyCloak ----
# The gateway is NETTY, not Tomcat, so its bandwidth comes from reactor-netty; /actuator is excluded or the
# Prometheus scrape of the gateway itself would dwarf the real client traffic.
comp("gateway", "gateway",
     lambda r: health('count(process_uptime_seconds{%s})' % SSEL("gateway", r),
                      w_cpu(SSEL("gateway", r)),
                      w_heap(ASEL("gateway", r)),
                      *w_idle("http_server_requests_seconds_count", "gateway", r)),
     lambda r: [(REQ % ASEL("gateway", r), "reqps", "req/s"),
                (CPU % SSEL("gateway", r), "percent", "cpu"),
                ('sum(rate(reactor_netty_http_server_data_sent_bytes_sum{uri!="/actuator"%s}[5m]))' % AND(r),
                 "Bps", "net out")],
     SERVICES_D % "gateway", 745, 180,
     # ALL of the gateway's alarms ride the border: 5xx (it is shedding), errors, and a resilience4j breaker OPEN
     # (every breaker lives here -- the gateway wraps each downstream). These are dependency problems, not the
     # gateway's own health, so the fill stays green and the border alarms.
     alarm=lambda r: alarm(w_5xx(ASEL("gateway", r)),
                           w_errlog(ASEL("gateway", r)),
                           w_fail(ASEL("gateway", r), "messaging_error_total"),
                           w_breaker(SSEL("gateway", r))))
comp("keycloak", "KEYCLOAK",
     lambda r: health('max(up{job="keycloak"})',
                      # inline (job= selector, not application=) but reads the SAME constants as w_cpu/w_heap
                      '(max(process_cpu_usage{job="keycloak"}) > bool %g)' % CPU_FRAC,
                      '(100 * sum(jvm_memory_used_bytes{job="keycloak", area="heap"}) / '
                      'sum(jvm_memory_max_bytes{job="keycloak", area="heap"}) > bool %d)' % HEAP_PCT),
     lambda r: [('sum(rate(http_server_requests_seconds_count{job="keycloak"}[5m]))', "reqps", "req/s"),
                ('avg(process_cpu_usage{job="keycloak"}) * 100', "percent", "cpu"),
                ('sum(jvm_memory_used_bytes{job="keycloak", area="heap"})', "bytes", "heap")],
     "/d/esq-services/", 745, 430)
# The BFF is a StatefulSet too, so its pods carry the same -0 / -1 ordinal and its cards split like the rest.
comp("backend", "Explorer",
     lambda r: health('max(up{job="esquire-bff"%s})' % AND(r),
                      # Node CPU is process_cpu_seconds_total-rate*100 (a percentage), so the threshold is
                      # CPU_FRAC*100 -- the SAME dial as the JVM cards, expressed for this metric.
                      '(sum(rate(process_cpu_seconds_total{job="esquire-bff"%s}[5m])) * 100 > bool %d)'
                      % (AND(r), int(CPU_FRAC * 100)),
                      *w_idle("esq_bff_inbound_duration_seconds_count", "backend", r,
                              sel=lambda _n, i: 'instance=~".*"' if i is None else 'instance=~".*-%d$"' % i)),
     lambda r: [('sum(rate(esq_bff_inbound_duration_seconds_count%s[5m]))' % ONLY(r), "reqps", "req/s"),
                ('sum(rate(process_cpu_seconds_total{job="esquire-bff"%s}[5m])) * 100' % AND(r), "percent", "cpu"),
                ('sum(process_resident_memory_bytes{job="esquire-bff"%s})' % AND(r), "bytes", "rss")],
     "/d/esq-services/", 920, 180)

# ---- THE OBSERVABILITY STACK IS *NOT* ON THIS BOARD -- with ONE exception, the Collector (mir0n) ----
# Prometheus / Loki / Tempo / Grafana / Alloy were on it briefly and came straight back off: this board answers
# "what is the SYSTEM doing", and boxes of tooling watching the tooling drown that question in exactly the noise
# T9-B spent the day removing from the log panels. The viewer is not the system.
#
# The Collector is the ONE piece that earns a box (I31, mir0n 2026-07-14 -- it was a detail-row panel before). It
# is the hub EVERY trace in the fleet passes through, and if it starts DROPPING spans then traces go quietly
# missing and NOTHING else on any dashboard would say so. So it is drawn as a component -- health (up, and not
# refusing) + accepted / queue / refused. It is deliberately the one box ComponentModel.png does NOT carry: it is
# about the integrity of OUR DATA, not the health of the tool. No arrows -- a health indicator, not a data-flow
# node -- and no esq icon, which is itself the tell that this box is the exception, not a system component.
comp("collector", "COLLECTOR",
     lambda r: health('max(up{job="otel-collector"})',
                      # refusing spans = the hub is DROPPING traces; this is the whole reason it earns a box
                      '(sum(rate(otelcol_receiver_refused_spans_total[%s])) > bool 0)' % ALERT_HOLD),
     lambda r: [('sum(rate(otelcol_receiver_accepted_spans_total[5m]))', "ops", "spans/s"),
                ('sum(otelcol_exporter_queue_size)', "short", "queue"),
                ('sum(increase(otelcol_receiver_refused_spans_total[%s]))' % ALERT_HOLD, "short", "refused")],
     "/d/esq-services/", 745, 596, shape="ellipse")

ORDER = list(C.keys())

# ---------------------------------------------------------------------------------------------------------------
# THE CARDS -- one per INSTANCE, each with its own numbers (mir0n).  See "ONE CARD PER INSTANCE" above for WHY.
#
# Geometry: card r sits at (left + r*PEEK_X, top + r*PEEK_Y) and the cards are painted BACK TO FRONT, so card 0 --
# the one with the icon -- covers the LEFT part of every card behind it and each of those keeps a clear strip of
# PEEK_X on its right, which is where its name and its three numbers go.
#
# ARROWS ARE DRAWN ONCE, against the STACK, never once per card (mir0n): a second set of arrows would say the
# replicas are wired differently, which is exactly the thing that is not true. The stack's LEFT and TOP edges
# belong to the front card and its RIGHT and BOTTOM edges to the last one, so a line always leaves from the
# outside of the whole stack and never crosses a card it does not belong to.
# ---------------------------------------------------------------------------------------------------------------

# Local k8s (k8s/values/*.yaml): every app component is replicaCount: 2. Infra -- Postgres, ActiveMQ, KeyCloak --
# is x1 and stays a single card, which is itself worth seeing: the board then SHOWS what is redundant and what is
# not, and the three components with nothing behind them are the three single points of failure.
K8S_CARDS = {n: 2 for n in ("aukeep", "pacman", "biztree", "enyman", "keysmith", "kcmaster", "gateway", "backend")}

# The k8s board needs ROOM: a stack is PEEK_X wider than a single card, and the two right-hand columns would
# otherwise sit ON TOP of the stack in front of them. These three numbers are the ONLY coordinates that differ
# between the boards -- same arrangement, same lanes, same arrows, same picture.
K8S_LEFT = {"gateway": 830, "keycloak": 830, "backend": 1080}

# k8s-only FLOW overrides -- what REDUNDANCY changes about the arrows, which the single-instance picture cannot
# show. On ONE instance enyMan only PUBLISHES entity CREATEs; with TWO instances a PEER enyMan's CREATE comes
# back to the other over the entity broadcast bus -- the reconcile intake (MoveQueueManager.onPeerCreate, its own
# publications filtered out) -- so on the entity lane enyMan both publishes AND receives: a double-ended arrow.
# {bus id -> {component -> flow}}, applied only when a redundant (x2) board is drawn.
K8S_FLOW = {"esquire.entity": {"enyman": "both"}}

# Set by main() per target. Docker = {} -> one card per component, and nothing about that board moves.
CARDS = {}

# How far a card behind peeks out: RIGHT and DOWN (mir0n's sketch). PEEK_X is the width of the strip a card behind
# gets to ITSELF -- it has to hold "auKeep 1" and three numbers, which is what makes it 72 and not 12. PEEK_Y stays
# small: 16px already reads as a stack, and much more would push a card into the component below (the service rows
# are 100px apart and a card is 80 tall).
PEEK_X, PEEK_Y = 96, 20

# The rim that keeps a stack from fusing into one blob. White, because this Grafana defaults to the LIGHT theme
# (GF_USERS_DEFAULT_THEME), so it reads as a GAP between two cards rather than as a drawn line. The card border is
# now the ALARM channel (mir0n): white = no alarm (the normal rim); ALARM_COLOR = an alarm fired in the last 1m.
# Bright magenta-red, deliberately OUTSIDE the health palette (red/amber/green fills) so it stands out on ANY fill.
CARD_RIM = "#FFFFFF"
ALARM_COLOR = "#FF2D95"


# auKeep is the only replicated component LEFT of the lanes, and it must stack the OTHER WAY (mir0n).
#
# Everywhere else the stack grows right-and-down, so the front card's right-hand arrows head away from the cards
# behind it. auKeep faces the other way: every line it has -- three bus taps and the DB call -- leaves to its RIGHT
# and BELOW, straight across the strip a card behind it would occupy. Mirrored, the stack opens away from its own
# wiring instead of into it.
#
# WHAT THE MIRROR FLIPS IS THE GEOMETRY, NOT THE IDENTITY (mir0n). Instance 0 is the FRONT card everywhere on the
# board -- it carries the icon, the label and every arrow -- and the mirror only decides which SIDE the cards
# behind it fan out to. Two invariants competed here and only one survives a mirrored stack:
#
#   * "instance 0 is always the front card"      -- KEPT. The front card is what a reader points at and calls
#                                                   "auKeep"; it means the same thing on every stack.
#   * "instances read 0,1 left to right"         -- given up on auKeep alone, where they read 1,0. Harmless: every
#                                                   card is LABELLED with its own instance number.
MIRROR = {"aukeep"}


def cards(name):
    """How many cards this component gets on the current target -- one per instance."""
    return CARDS.get(name, 1)


def card_left(name, r):
    # Card 0 is the FRONT one, always. Mirrored, the cards behind fan out to the LEFT instead of to the right.
    depth = (cards(name) - 1 - r) if name in MIRROR else r
    return C[name]["left"] + PEEK_X * depth


def card_top(name, r):
    # The front card sits highest; each card behind steps DOWN, mirrored or not.
    return C[name]["top"] + PEEK_Y * r


def card_field(name, r):
    """The field a card is coloured by -- and the card's element name.

    On docker it is just the component ("enyman"). On k8s it names the INSTANCE ("enyman 0"): two cards that both
    said "enyman" would be two answers to a question nobody could ask.
    """
    return name if cards(name) == 1 else "%s %d" % (name, r)


def alarm_field(name, r):
    """The field a card's BORDER is coloured by -- the ALARM channel, distinct from the health FILL field."""
    return "%s alarm" % card_field(name, r)


def vital_field(name, r, vname):
    """The field a vital is published under -- it must say WHAT it measures, and WHOSE.

    These were once "aukeep 0" / "aukeep 1" / "aukeep 2" meaning the FIRST, SECOND and THIRD vital, which was worse
    than uninformative: it read as a replica number (mir0n). Now the replica number is real, and both parts are
    spelled out -- "aukeep 1 cpu" is instance 1's CPU and cannot be read as anything else.
    """
    return "%s %s" % (card_field(name, r), vname)


def ordinal(name, r):
    """The instance ordinal to QUERY for card r -- None (aggregate) when the component runs alone."""
    return None if cards(name) == 1 else r

# ---------------------------------------------------------------------------------------------------------------
# THE THREE BUSES. There is not "a bus" -- there are THREE, and that is the entire point of a bus CATALOG.
# Collapsing them onto one bar (which this file did, briefly, and wrongly) throws away the only thing the picture
# is for: WHICH services share a medium, and which do not. bizTree and keySmith never meet. auKeep hears everyone
# and answers no one. That is the architecture, and it is invisible on a single bar.
#
# Membership below is the DECLARED topology (each service's application.yml `bus-id` / `role`), NOT what the live
# meters happen to show. A meter only appears once a message has flowed, so a declared participant that has been
# idle would silently vanish from the picture -- and a component missing from an architecture diagram because it
# was quiet is precisely the bug this board exists to prevent. The live view is the service graph below; this one
# is the design.
#
# (Checked both: the live meters agree, except enyMan on esquire.kc -- declared CLIENT, no traffic in the window.
#  It belongs on the board. That discrepancy is exactly why the board reads the config and not the metrics.)
# ---------------------------------------------------------------------------------------------------------------
# Lanes, left to right: Audit -- IAM Request-Response -- Entity Broadcast.
# THE SAME ORDER AS doc/media/ComponentModel.png, and it must stay that way: a monitoring board and an
# architecture diagram that disagree about WHERE things are make the reader hold two maps at once, and
# a reader who spots the difference has no way to know which picture to trust. They carry the model's
# own names and colours for the same reason.
BUSES = [
    # `flow` is the DIRECTION OF THE MESSAGES, per participant -- which is what the arrowheads must say:
    #   pub  : the service PUBLISHES into the bus            (arrow points INTO the lane)
    #   sub  : the service CONSUMES from the bus             (arrow points OUT of the lane, at the service)
    #   both : request/reply -- it does BOTH                 (double-headed)
    #
    # The IAM bus is request/reply, so EVERY participant is `both`: enyMan and keySmith send a request and wait
    # for the reply; kcMaster receives the request and sends the reply back. A single arrowhead on that bus would
    # be a lie in one direction or the other -- and the component model draws it with double arrows for exactly
    # this reason.
    dict(id="audit-c", name="Audit Broadcast Bus", kind="audit-c",
         color="#8FA8C8", left=300,
         flow={"enyman": "pub", "keysmith": "pub", "pacman": "pub", "aukeep": "sub"}),
    dict(id="esquire.kc", name="IAM Request-Response Bus", kind="esquire.kc",
         color="#D9C48A", left=380,
         flow={"enyman": "both", "keysmith": "both", "kcmaster": "both"}),
    dict(id="esquire.entity", name="Entity Broadcast Bus", kind="esquire.entity",
         color="#9DC08B", left=460,
         flow={"enyman": "pub", "pacman": "pub", "biztree": "sub", "kcmaster": "sub"}),
]

# The lanes run the full height of the service column, so any service can tap any of them at its own height.
SPINE_TOP, SPINE_H, SPINE_W = 25, 500, 46

# ---------------------------------------------------------------------------------------------------------------
# POINT-TO-POINT calls -- these get ARROWS, because something called and WAITED for an answer.
#
# Every edge below is READ OUT OF THE CONFIG, not remembered. A false edge on an architecture diagram is worse
# than a missing one: it sends the next reader looking for a coupling that does not exist.
#
#   gateway routes  : gateway/src/main/resources/application.yml `routes[].uri` -> keySmith, bizTree, pacMan,
#                     enyMan. FOUR services.
#                     NOT kcMaster -- there is no route to it. kcMaster is reached ONLY over the bus, which is
#                     exactly the kind of fact this picture exists to make obvious. (This file drew that arrow
#                     once; it was invented.)
#   DB              : the services with a datasource -- enyMan, bizTree, pacMan, keySmith, auKeep.
#                     kcMaster has NO DB (it owns no state; KeyCloak is its store) and neither does the gateway.
#   kcMaster -> KC  : the admin sync, REST.
#   gateway  -> KC  : jwk-set-uri / token-uri -- the gateway fetches the JWK set to validate every JWT. A real
#                     dependency: if KeyCloak is unreachable, no request authenticates.
#   Explorer -> KC  : the BFF's OWN server-to-server call, and it was missing from this board. backend config.ts
#                     takes KC_ISSUER and KC_ISSUER_INTERNAL -- "the URL the BFF discovers KC through
#                     server-to-server" -- so the BFF discovers the realm, exchanges the code for tokens and
#                     fetches the JWKS ITSELF. It does NOT reach KeyCloak through the gateway. Without this line
#                     the board said the BFF's only outbound call was to the gateway, and a reader would conclude
#                     that KeyCloak going down cannot break login at the Explorer -- which is the opposite of true.
# ---------------------------------------------------------------------------------------------------------------
ARROWS = [
    ("backend", "gateway"),            # BFF proxies /api/* (and relays the session bearer)
    ("backend", "keycloak"),           # the BFF's own server-to-server calls: discovery, token exchange, JWKS
    ("gateway", "enyman"),
    ("gateway", "biztree"),
    ("gateway", "pacman"),
    ("gateway", "keysmith"),
    ("gateway", "keycloak"),           # JWK set -- every JWT is validated against it
    ("enyman", "postgres"),
    ("biztree", "postgres"),
    ("pacman", "postgres"),
    ("keysmith", "postgres"),
    ("aukeep", "postgres"),
    ("kcmaster", "keycloak"),          # the KC admin sync
]

# Where an arrow MUST leave / land, when the generic "whichever axis is further apart wins" rule gets it wrong.
# Keyed by (from, to) -- the box named first is the one whose edge is being pinned. See edge_anchor().
#
# Explorer -> KeyCloak: it leaves the CENTRE OF THE BOTTOM EDGE (mir0n). The rule would send it out of the LEFT
# edge on the k8s board -- the two boxes land exactly 250px apart on both axes there, the tie goes to horizontal,
# and the same arrow then leaves a different edge on each board.
PINNED_EDGE = {
    ("backend", "keycloak"): {"x": 0, "y": -1},    # leave Explorer's BOTTOM edge  (canvas y is UP)
    ("keycloak", "backend"): {"x": 0, "y": 1},     # land on KeyCloak's TOP edge
}

# Line colours. The lanes carry their own (the bus colours, from the component model); these two are
# for the point-to-point calls -- RED for the database, as ComponentModel.png draws it, grey for REST.
DB_RED = "#C0392B"
# BLACK, not grey (mir0n). REST is the busiest kind of line on the board and grey made it the FAINTEST -- the
# reader had to hunt for the one coupling that carries every request. Black is the ink of the drawing; the bus
# lanes keep their own colours and the DB keeps its red, so the three kinds stay tellable apart at a glance.
REST_BLACK = "#000000"

W, H = 140, 80
BUS_H = 24
BUS_LEFT, BUS_WIDTH = 180, 817   # the bars run beside the broker, spanning the service row above them


# ---------------------------------------------------------------------------------------------------------------
# THE COMPONENT LAYERS -- drawn BACK TO FRONT (mir0n):
#
#     DB + auKeep  ->  buses  ->  services  ->  gateway + KeyCloak  ->  BFF
#        (back)                                                        (front)
#
# This is the PAINT ORDER of the whole picture, not a build sequence. Element order IS z-order in a canvas
# frame -- later elements paint over earlier ones -- so walking the layers back-to-front gives the stacking
# for free: the store sits behind the buses, the buses behind the services, the BFF on top.
# ---------------------------------------------------------------------------------------------------------------
LAYERS = [
    ["postgres", "aukeep"],                                    # 1 -- back:  the store, and the audit sink
    ["activemq", "collector"],                                 # 2 --        the buses (lanes) + the broker; the
                                                               #             Collector rides here too (bottom-row
                                                               #             infra hub, the I31 exception box)
    ["pacman", "biztree", "enyman", "keysmith", "kcmaster"],   # 3 --        the services
    ["gateway", "keycloak"],                                   # 4 --        the edge + identity
    ["backend"],                                               # 5 -- front: the BFF
]
LAYERS_ON = len(LAYERS)   # ALL layers are drawn. LAYERS is the BACK-TO-FRONT Z-ORDER of the whole
                          # picture (element order IS paint order in a canvas frame), NOT a switch
                          # for revealing one layer at a time.

VISIBLE = [n for layer in LAYERS[:LAYERS_ON] for n in layer]
BUSES_ON = LAYERS_ON >= 2      # the lanes appear with layer 2


def targets():
    """One refId per number on the board -- deliberately, not one clever query with a transform.

    With a transform you cannot tell a component that is DOWN from one whose label the transform quietly dropped.
    One refId per card means a grey card has exactly one possible cause.
    """
    out = []
    for i, name in enumerate(VISIBLE):
        for r in range(cards(name)):
            o = ordinal(name, r)
            # The instance suffix appears ONLY where there IS an instance to name. A single-card component keeps
            # the refIds it has always had, so the docker board stays byte-for-byte the artifact mir0n locked.
            sfx = "" if cards(name) == 1 else "_%d" % r
            out.append({"refId": "h%d%s" % (i, sfx), "datasource": DS, "expr": C[name]["health_of"](o),
                        "legendFormat": card_field(name, r), "instant": True})
            # the BORDER's ALARM field (mir0n): a separate channel from the fill. `or vector(0)` so an infra card
            # (vector(0)) and a never-fired counter both read a clean 0 = no alarm = the normal white rim.
            out.append({"refId": "a%d%s" % (i, sfx), "datasource": DS,
                        "expr": "(%s) or vector(0)" % C[name]["alarm_of"](o),
                        "legendFormat": alarm_field(name, r), "instant": True})
            for k, (expr, _unit, vname) in enumerate(C[name]["vitals_of"](o)):
                # `or vector(0)` -- for EXACTLY the reason the health colour needs it. A counter that has never
                # been incremented HAS NO SERIES (Micrometer materialises it on first use), and an instance that
                # is DOWN reports nothing at all. Prometheus then returns an empty vector, Grafana finds no field,
                # and the canvas prints the words "Field not found" ACROSS THE CARD -- three of them on a dead
                # instance. An absent value is not an error message; on this board it is a zero.
                out.append({"refId": "v%d_%d%s" % (i, k, sfx), "datasource": DS,
                            "expr": "(%s) or vector(0)" % expr,
                            "legendFormat": vital_field(name, r, vname), "instant": True})
    return out


def unit_overrides():
    """Per-FIELD units, so a vital renders as "108 MB" / "1.2%" / "0.3 req/s" and not as three naked numbers.

    A number with no unit is not a vital; it is a riddle. 108000000 and 0.012 tell a reader nothing until they know
    what they are looking at, and on a board meant to be read in a glance, that is the same as telling them nothing.
    """
    ov = []
    for name in VISIBLE:
        for r in range(cards(name)):
            for _expr, unit, vname in C[name]["vitals_of"](ordinal(name, r)):
                ov.append({
                    "matcher": {"id": "byName", "options": vital_field(name, r, vname)},
                    "properties": [{"id": "unit", "value": unit},
                                   {"id": "decimals", "value": 0 if unit in ("bytes", "short") else 1}],
                })
    return ov


def alarm_overrides():
    """The ALARM field maps DIFFERENTLY from the health field: 0 = no alarm -> WHITE (the normal rim), >=1 = an
    alarm fired -> ALARM_COLOR. Without this override an alarm field would fall under the panel's health thresholds
    (0 -> red) and every card would rim red. One override per card."""
    ov = []
    for name in VISIBLE:
        for r in range(cards(name)):
            ov.append({
                "matcher": {"id": "byName", "options": alarm_field(name, r)},
                "properties": [{"id": "thresholds", "value": {"mode": "absolute", "steps": [
                    {"color": CARD_RIM, "value": None},
                    {"color": ALARM_COLOR, "value": 1}]}}],
            })
    return ov


def card(name, r):
    """ONE INSTANCE: a health colour, its name, and (on the front card) the icon.

    Every card of a stack links to the same component detail -- a card that answered nothing when clicked would be
    a small betrayal of the reader.
    """
    c = C[name]
    label = c["label"] if cards(name) == 1 else "%s %d" % (c["label"], r)
    return {
        "name": card_field(name, r),
        # Shape carries KIND: services and infra are rectangles; the Collector is an ELLIPSE -- not a service, not
        # infra, the telemetry hub (I31). A reader sees a different shape and knows before reading that this box is
        # a different kind of thing. Health still colours it exactly the same way.
        "type": c.get("shape", "rectangle"),
        "background": {"color": {"field": card_field(name, r), "fixed": "#D9D9D9"}},
        # The border is the ALARM channel (mir0n), SEPARATE from the health fill: its colour is the alarm FIELD --
        # white when 0 (no alarm = the normal rim) and ALARM_COLOR when an alarm fired in the last 1m. A constant
        # width so the rim shows on EVERY card, which on a stack also keeps the two cards from fusing into one blob
        # (the job the old fixed white rim did).
        "border": {"color": {"field": alarm_field(name, r)}, "width": 3},
        "config": {
            "align": "center", "valign": "top",
            "color": {"fixed": "#FFFFFF"},
            "size": 12,
            # A card BEHIND shows its name in its own strip (see card_texts): the rectangle's own centred text
            # would be printed under the card in front of it, where nobody can read it.
            "text": {"fixed": (label + ("  (liveness)" if c["probe"] else "")) if r == 0 else "",
                     "mode": "fixed"},
            "links": [{"title": "details", "url": c["link"], "targetBlank": False}],
        },
        "constraint": {"horizontal": "left", "vertical": "top"},
        "placement": {"left": card_left(name, r), "top": card_top(name, r),
                      "width": W, "height": H, "rotation": 0},
        "links": [{"title": "details for %s" % name, "url": c["link"], "targetBlank": False}],
        "connections": [],
    }


# ---------------------------------------------------------------------------------------------------------------
# AN ICON PER COMPONENT, top-left of its FRONT card (mir0n) -- and they are OURS: doc/logo/*, the same set the
# README's Component Model legend and ComponentModel.png use. Generic icons from someone else's pack would have
# made this board a THIRD picture of the same system, with a third visual vocabulary to learn.
#
# ONE icon per component, never one per card (mir0n): the icon says WHAT this is, and the second instance of a
# thing is not a different thing.
#
# Built into compose/o11y/grafana/icons/ from doc/logo/ and mounted into Grafana's public tree.
# backend <- esquire.png: the BFF IS the Esquire Explorer, so it wears the Esquire mark rather than a Node.js logo
# -- the board names the COMPONENT, not the runtime it happens to be written in. The canvas icon element loads SVG
# ONLY, so the PNG logos are wrapped in an SVG that embeds them -- our logo, unchanged, not a near-enough stand-in.
#
# A reader scanning eleven identical grey rectangles has to read eleven labels; a face is recognised before it is
# read.
#
# The icon does NOT carry the health colour -- the CARD does. An icon that also went red would be saying the same
# thing twice and leaving nothing to say "this is a database".
# ---------------------------------------------------------------------------------------------------------------
ICON = {n: "img/icons/esq/%s.svg" % n for n in
        ("pacman", "activemq", "postgres")}
ICON["enyman"] = "img/icons/esq/enyman.svg?v=2"
ICON["keysmith"] = "img/icons/esq/keysmith.svg?v=2"
ICON["kcmaster"] = "img/icons/esq/kcmaster.svg?v=2"
ICON["keycloak"] = "img/icons/esq/keycloak.svg?v=2"
# A drawing that CHANGES keeps its filename and carries `?v=N`. The canvas fetches its icons from script at
# runtime, so a browser holding the old drawing under the old URL keeps showing it through any refresh --
# the query makes the URL new without making the FILE new. Bump N whenever the drawing changes.
ICON["biztree"] = "img/icons/esq/biztree.svg?v=2"
ICON["gateway"] = "img/icons/esq/gateway.svg?v=3"
# A drawing that CHANGES keeps its filename and carries `?v=N`. The canvas fetches its icons from script at
# runtime, so a browser holding the old drawing under the old URL keeps showing it through any refresh --
# the query makes the URL new without making the FILE new. Bump N whenever the drawing changes.
ICON["aukeep"] = "img/icons/esq/aukeep.svg?v=2"
# The FILE is `explorer.svg`, not `backend.svg`, deliberately: backend.svg previously held the Node.js logo, and a
# browser that had cached that URL kept serving it forever even though the file on disk had changed. A new path is
# the only cache-bust that always works.
ICON["backend"] = "img/icons/esq/explorer.svg?v=3"


def icon(name):
    """The component's icon, tucked into the top-left of its FRONT card -- card 0, on every stack (see MIRROR)."""
    return {
        "name": name + " icon",
        "type": "icon",
        "background": {"color": {"fixed": "transparent"}},
        "border": {"color": {"fixed": "transparent"}, "width": 0},
        "config": {
            "path": {"fixed": ICON[name], "mode": "fixed"},
            # NO fill override: these are OUR logos and they carry their own colours. The CARD carries the health
            # colour; an icon that also went red would say the same thing twice and stop saying "this is pacMan".
            "fill": {"fixed": "transparent"},
        },
        "constraint": {"horizontal": "left", "vertical": "top"},
        # Big enough to be RECOGNISED, not just noticed. The whole reason our own logos are here is that a face is
        # read faster than a label -- at 16px it was neither.
        "placement": {"left": card_left(name, 0) + 5, "top": card_top(name, 0) + 4,
                      "width": 28, "height": 28, "rotation": 0},
        "connections": [],
    }


def card_texts(name, r):
    """The card's LIVE NUMBERS -- and, for a card behind, its name as well.

    A colour says the process is RUNNING. It does not say it has stopped doing WORK, and it does not say it is
    about to fall over -- the two failures that actually happen. These do.

    WHERE the text goes is the whole trick of the stack. Card r is covered on its left by the card in front of it,
    so its text is laid out in the strip it keeps to ITSELF: the PEEK_X-wide column immediately right of card r-1.
    The front card has its full width. This is why the board could not simply be "drawn twice" for k8s -- the
    second instance's numbers had to be given somewhere to live.
    """
    c = C[name]
    if r == 0:
        left, width, size = card_left(name, 0), W, 12
    elif name in MIRROR:
        # MIRRORED: the card in front sits to the RIGHT, so the strip this one keeps to itself is on its LEFT.
        left, width, size = card_left(name, r), PEEK_X, 11
    else:
        # the card in front sits to the LEFT, so the strip is the column just right of it
        left, width, size = card_left(name, r - 1) + W, PEEK_X, 11

    out = []
    if r > 0:
        out.append({
            "name": "%s name" % card_field(name, r),
            "type": "text",
            "background": {"color": {"fixed": "transparent"}},
            "border": {"color": {"fixed": "transparent"}, "width": 0},
            "config": {"align": "center", "valign": "middle", "color": {"fixed": "#FFFFFF"}, "size": size,
                       "text": {"fixed": "%s %d" % (c["label"], r), "mode": "fixed"}},
            "constraint": {"horizontal": "left", "vertical": "top"},
            "placement": {"left": left, "top": card_top(name, r) + 2, "width": width, "height": 18, "rotation": 0},
            "connections": [],
        })

    for k, (_expr, _unit, vname) in enumerate(C[name]["vitals_of"](ordinal(name, r))):
        out.append({
            "name": vital_field(name, r, vname),
            "type": "metric-value",
            "background": {"color": {"fixed": "transparent"}},
            "border": {"color": {"fixed": "transparent"}, "width": 0},
            "config": {
                "align": "center", "valign": "middle",
                "color": {"fixed": "#FFFFFF"},
                "size": size,
                "text": {"field": vital_field(name, r, vname), "mode": "field", "fixed": ""},
            },
            "constraint": {"horizontal": "left", "vertical": "top"},
            "placement": {"left": left, "top": card_top(name, r) + 22 + k * 19,
                          "width": width, "height": 19, "rotation": 0},
            "connections": [],
        })
    return out


def bus_spine(b):
    """One VERTICAL SPINE per bus -- a line with its participants branching off it, which is how a messaging bus
    is actually drawn. The spine is STRUCTURE, so it is never coloured by state: a bus has no health. The broker
    that hosts it does, and the services on it do."""
    return {
        "name": "bus:" + b["id"],
        "type": "rectangle",
        "background": {"color": {"fixed": b["color"]}},
        "border": {"color": {"fixed": "transparent"}, "width": 0},
        "config": {
            "align": "center", "valign": "middle",
            "color": {"fixed": "#FFFFFF"}, "size": 10,
            # No text ON the spine: it is 22px wide, and horizontal text in a vertical bar is unreadable. The
            # name goes on a label ABOVE it.
            "text": {"fixed": "", "mode": "fixed"},
        },
        "constraint": {"horizontal": "left", "vertical": "top"},
        "placement": {"left": b["left"], "top": SPINE_TOP, "width": SPINE_W, "height": SPINE_H, "rotation": 0},
        "connections": [],
    }


def bus_label(b):
    """The lane's name, ROTATED and running up the lane -- exactly as doc/media/ComponentModel.png draws it.

    Rotation, not a horizontal caption: the lane is 46px wide and 500px tall, so horizontal text simply does not
    fit, and a caption parked above or below it detaches the name from the thing it names. The model already
    solved this; the board copies it.
    """
    lane_cx = b["left"] + SPINE_W / 2.0
    lane_cy = SPINE_TOP + SPINE_H / 2.0
    tw, th = 260, 22           # pre-rotation box; rotation is about the element's centre
    return {
        "name": "buslabel:" + b["id"],
        "type": "text",
        "background": {"color": {"fixed": "transparent"}},
        "border": {"color": {"fixed": "transparent"}, "width": 0},
        "config": {
            "align": "center", "valign": "middle",
            "color": {"fixed": "#FFFFFF"}, "size": 12,
            "text": {"fixed": b["name"] + "    (" + b["kind"] + ")", "mode": "fixed"},
        },
        "constraint": {"horizontal": "left", "vertical": "top"},
        "placement": {"left": lane_cx - tw / 2.0, "top": lane_cy - th / 2.0,
                      "width": tw, "height": th, "rotation": -90},
        "connections": [],
    }


def legend():
    """A legend ON the picture -- because a colour nobody can decode is decoration, not information.

    The board now says three different things with colour (health state) and three more with line colour (kind of
    coupling), and none of it is guessable. Amber in particular is the one that must be readable at a glance: it
    is the state a monitoring screen exists for -- the component is UP and in TROUBLE, which is precisely the
    condition a plain up/down board reports as "fine".
    """
    L, T = 30, 420
    out = []

    def txt(text, left, top, width, color, size=11, bold=False):
        out.append({
            "name": "legend:%s:%d" % (text[:12], top),
            "type": "text",
            "background": {"color": {"fixed": "transparent"}},
            "border": {"color": {"fixed": "transparent"}, "width": 0},
            "config": {"align": "left", "valign": "middle", "color": {"fixed": color}, "size": size,
                       "text": {"fixed": text, "mode": "fixed"}},
            "constraint": {"horizontal": "left", "vertical": "top"},
            "placement": {"left": left, "top": top, "width": width, "height": 18, "rotation": 0},
            "connections": [],
        })

    def swatch(text, left, top, color):
        out.append({
            "name": "legend:sw:%s" % text,
            "type": "rectangle",
            "background": {"color": {"fixed": color}},
            "border": {"color": {"fixed": "transparent"}, "width": 0},
            "config": {"align": "center", "valign": "middle", "color": {"fixed": "#FFFFFF"}, "size": 10,
                       "text": {"fixed": text, "mode": "fixed"}},
            "constraint": {"horizontal": "left", "vertical": "top"},
            "placement": {"left": left, "top": top, "width": 62, "height": 18, "rotation": 0},
            "connections": [],
        })

    txt("HEALTH", L, T, 80, "#B0B0B0", 11)
    swatch("OK", L, T + 20, "#37872D")
    swatch("TROUBLE", L + 66, T + 20, "#E0752D")     # UP, and in trouble -- the state a live board exists for
    swatch("DOWN", L + 132, T + 20, "#C4162A")

    txt("LINES", L, T + 48, 80, "#B0B0B0", 11)
    txt("--  bus   (publish / consume)", L, T + 68, 230, "#9DC08B", 11)
    txt("--  database", L, T + 86, 230, DB_RED, 11)
    txt("--  REST/HTTP", L, T + 104, 230, REST_BLACK, 11)

    # Only where there IS a stack to explain. A card peeking out from behind another is not self-evident, and an
    # unexplained shape on a monitoring board is read as a rendering glitch.
    if CARDS:
        txt("REDUNDANCY", L, T + 132, 100, "#B0B0B0", 11)

        def chip(left, top, color):
            out.append({
                "name": "legend:card:%d" % left,
                "type": "rectangle",
                "background": {"color": {"fixed": color}},
                "border": {"color": {"fixed": CARD_RIM}, "width": 2},
                "config": {"align": "center", "valign": "middle", "color": {"fixed": "#FFFFFF"}, "size": 10,
                           "text": {"fixed": "", "mode": "fixed"}},
                "constraint": {"horizontal": "left", "vertical": "top"},
                "placement": {"left": left, "top": top, "width": 26, "height": 18, "rotation": 0},
                "connections": [],
            })

        chip(L + 10, T + 158, "#37872D")     # the card behind -- instance 1
        chip(L, T + 152, "#37872D")          # instance 0, on top
        # The stack is not saying "there are two of these". It is saying REDUNDANCY -- and what redundancy actually
        # buys, which is the part a reader has to be told: both replicas carry load AT THE SAME TIME (they are not
        # a primary and a standby), and either one can carry the whole thing if the other dies. That is why each
        # card has to hold its OWN number: a pair where one card is idle has quietly become a single.
        # A component with NO card behind it has no such cover -- which is the other half of what this row says.
        txt("--  one card per parallel replica, its OWN number", L + 46, T + 152, 300, "#B0B0B0", 11)
        txt("    both serve; either can carry alone", L + 46, T + 168, 300, "#B0B0B0", 11)

    # SHAPE says KIND. Everything on this board is a rectangle EXCEPT one oval -- and an unexplained shape reads as
    # a rendering glitch, so it is spelled out. Sits below the redundancy block (k8s) / below the lines (docker).
    return out


def hub_caption():
    """The Collector's WHAT-IS-THIS -- 3 lines, LEFT-ALIGNED, to the RIGHT of its oval (mir0n) -- a caption on the
    card itself, not a legend entry. The oval is its own header, so the words just explain it."""
    c = C["collector"]
    box = 330
    left = c["left"] + W + 14                    # to the RIGHT of the oval, small gap
    top = c["top"] + (H - 3 * 16) / 2.0          # vertically centred against the oval
    lines = ["the telemetry hub: not a service, not infra.",
             "every trace passes through it to be stored --",
             "if it stops, traces are lost with no warning."]
    out = []
    for i, ln in enumerate(lines):
        out.append({
            "name": "hubcap:%d" % i,
            "type": "text",
            "background": {"color": {"fixed": "transparent"}},
            "border": {"color": {"fixed": "transparent"}, "width": 0},
            "config": {"align": "left", "valign": "middle", "color": {"fixed": "#B0B0B0"}, "size": 11,
                       "text": {"fixed": ln, "mode": "fixed"}},
            "constraint": {"horizontal": "left", "vertical": "top"},
            "placement": {"left": left, "top": top + i * 16, "width": box, "height": 16, "rotation": 0},
            "connections": [],
        })
    return out


def canvas():
    """Drawn BACK TO FRONT, one component layer at a time -- see LAYERS above.

    Element order IS z-order in a canvas frame (later elements paint over earlier ones), so iterating the layers
    in order gives the back-to-front stacking for free: the store sits behind the buses, the buses behind the
    services, and the BFF on top.
    """
    elements, by_name, by_card = [], {}, {}
    for layer in LAYERS[:LAYERS_ON]:
        for name in layer:
            # BACK TO FRONT -- element order IS z-order -- so the LAST instance goes in first and card 0, the
            # one with the icon, is painted over it. That is what leaves every card behind a clear strip to print
            # its numbers in. A mirrored stack needs no special case here: card 0 is the front one either way, the
            # cards behind simply fan out to the other side.
            for r in range(cards(name) - 1, -1, -1):
                e = card(name, r)
                elements.append(e)
                by_card[(name, r)] = e
            by_name[name] = by_card[(name, 0)]
    for name in VISIBLE:
        if name in ICON:                       # the Collector is the one box with no esq icon -- see its comp() note
            elements.append(icon(name))
        for r in range(cards(name)):
            elements.extend(card_texts(name, r))

    # EVERY LINE HANGS OFF THE FRONT CARD (mir0n: "glue gateway arrows to upper box").
    #
    # The alternative -- hanging the right-hand and bottom lines off the LAST card, so they leave from the outside
    # of the whole stack -- is geometrically tidier but reads wrong: the arrow then appears to come out of instance
    # 1, as though the gateway routed to that one replica. It does not; it routes to the COMPONENT. The lines
    # belong to the component, so they leave from the card that IS the component: the upper one.
    def elem(name, _side):
        return by_card[(name, 0)]

    def elem_top(name, _side):
        return card_top(name, 0)

    def elem_left(name, _side):
        return card_left(name, 0)

    # ---- ASYNC: each service BRANCHES SIDEWAYS onto every spine it is on -- HORIZONTALLY, at 0 degrees.
    #
    # THE ANCHOR MATH, and why it is not just {x:-1, y:0}. A canvas connection anchor is NORMALISED to the target
    # element, so y=0 is the element's MIDDLE -- and the spine is 500px tall. Anchoring every branch at y=0 would
    # funnel all six services into the spine's centre point, producing a fan of diagonals: a picture that says
    # "everything converges here", which is not what a bus does. A bus is a line you TAP, anywhere along it.
    #
    # So the anchor is computed per service: the point on the spine at that service's OWN height. Grafana's canvas
    # axis is Y-UP (y=1 top, y=-1 bottom), hence the (centre - y) / half-height form rather than the reverse.
    # Result: every branch is a clean horizontal tap, and the drawing says what the architecture does.
    def edge_anchor(me, other):
        """Which EDGE of `me` faces `other` -- so an arrow leaves and lands on a box's SIDE, not its centre.

        {x:0, y:0} is the element's CENTRE, and an arrow anchored there is drawn from the middle of the box: the
        line runs UNDER the box and out the far side, so it looks as if it starts somewhere behind the label.
        Picking the facing edge is what makes an arrow read as "leaves here, arrives there".

        Whichever axis the two boxes are further apart on wins -- a mostly-horizontal pair meets left/right, a
        mostly-vertical pair meets top/bottom. Grafana's canvas axis is Y-UP (y=1 top, y=-1 bottom).

        PINNED_EDGE overrides that rule for a pair that the rule gets wrong. The distance test is a heuristic, and
        it BREAKS ON A TIE: Explorer -> KeyCloak sits 175px across and 250px down on docker (vertical -- bottom
        edge, right), but the k8s board shifts both boxes and the gap becomes 250 x 250. Exactly equal, `>=` picks
        the horizontal branch, and the SAME arrow leaves the LEFT edge on one board and the BOTTOM edge on the
        other. Two boards that are meant to be the same picture then disagree about the shape of the system, which
        is the one thing this drawing must never do. Pinning the pair makes it leave the bottom edge on both.
        """
        pin = PINNED_EDGE.get((me, other))
        if pin is not None:
            return dict(pin)
        a, b = C[me], C[other]
        dx = (b["left"] + W / 2.0) - (a["left"] + W / 2.0)
        dy = (b["top"] + H / 2.0) - (a["top"] + H / 2.0)
        if abs(dx) >= abs(dy):
            return {"x": 1 if dx > 0 else -1, "y": 0}          # right / left edge
        return {"x": 0, "y": -1 if dy > 0 else 1}              # bottom / top edge  (y is UP)

    spine_mid = SPINE_TOP + SPINE_H / 2.0
    spine_half = SPINE_H / 2.0

    def anchor_on_spine(comp_name):
        """The point on the lane at THAT COMPONENT'S OWN HEIGHT, so the tap is horizontal (0 degrees).

        Not {x:.., y:0}. A canvas anchor is NORMALISED to the target, so y=0 is the lane's MIDDLE -- and the lane
        is 500px tall. Anchoring every tap at y=0 funnels all six services into one point and draws a fan of
        diagonals: a picture that says "everything converges here", which is not what a bus does. A bus is a line
        you TAP, anywhere along it. Grafana's canvas axis is Y-UP (y=1 top, y=-1 bottom), hence (mid - y)/half.
        """
        c = C[comp_name]
        comp_mid = c["top"] + H / 2.0
        return (spine_mid - comp_mid) / spine_half

    # Every connection below is drawn FROM the service TO the lane, and the ARROWHEAD is placed by `direction`:
    #   forward = the head lands on the LANE      -> the service PUBLISHES into the bus
    #   reverse = the head lands on the SERVICE   -> the service CONSUMES from the bus
    #   both    = request/reply
    # So the drawing now states the direction of the MESSAGES, not merely who is attached to what. auKeep, for
    # instance, gets an arrow pointing AT IT from every audit publisher -- which is the shape of "hears everyone,
    # answers no one", and a plain line could never have said it.
    ARROWHEAD = {"pub": "forward", "sub": "reverse", "both": "both"}

    # ---------------------------------------------------------------------------------------------------------
    # ONE DISTRIBUTION PER EDGE, COUNTING EVERY LINE ON IT -- bus taps AND point-to-point arrows together.
    #
    # Spreading the two KINDS separately is not spreading at all: each group centres itself on the same edge and
    # they land on top of one another. enyMan's left edge carries three bus taps AND its Esq2025 arrow -- with two
    # independent spreads, the taps sat at -0.62 / 0 / +0.62 and the DB arrow, alone in its own group, took 0 and
    # collided head-on with the middle tap. The edge does not care what KIND a line is; it only knows how many
    # lines want it.
    #
    # So every endpoint -- whichever kind -- is registered against (component, edge) first, and only then are the
    # slots handed out.
    #
    # THE BUS TAPS STAY HORIZONTAL. Their service-side glue point is now chosen by the shared spread, so the LANE
    # end is recomputed from whatever pixel row that lands on: both ends move by the same amount, and the tap
    # slides up or down as a whole instead of tilting.
    # ---------------------------------------------------------------------------------------------------------
    SPREAD = 0.66      # how much of an edge to use, -SPREAD..+SPREAD (leaves the corners alone)

    def slots(n):
        """n evenly spaced positions across an edge, in canvas anchor units. A lone line keeps the middle."""
        if n <= 1:
            return [0.0]
        return [(-SPREAD + 2.0 * SPREAD * k / (n - 1.0)) for k in range(n)]

    def side_of(anchor):
        return "R" if anchor["x"] == 1 else "L" if anchor["x"] == -1 else "T" if anchor["y"] == 1 else "B"

    # ---- 1. register EVERY endpoint against the edge it uses ------------------------------------------------
    #   a bus tap contributes ONE endpoint (on the service; the lane end is derived from it)
    #   a p2p arrow contributes TWO (one on each box)
    ends = {}      # (component, edge) -> [ endpoint ]
    order = []     # keeps emission deterministic

    for b in (BUSES if BUSES_ON else []):
        flow_map = dict(b["flow"])
        if CARDS:   # a redundant (x2) board -- apply the k8s-only flow overrides (e.g. enyMan <-> entity = both)
            flow_map.update(K8S_FLOW.get(b["id"], {}))
        for name, flow in sorted(flow_map.items()):
            if name not in by_name:
                continue
            on_right = C[name]["left"] > b["left"]
            e = {"x": -1 if on_right else 1, "y": 0}
            side = side_of(e)
            key = (name, side)
            # The other end of a bus tap is the LANE, at the card's own height (the tap is horizontal), so its
            # vertical offset is ZERO -- it heads straight sideways, neither up nor down.
            item = {"kind": "bus", "comp": name, "bus": b, "flow": flow, "side": side,
                    "ox": b["left"] + SPINE_W / 2.0,
                    "oy": elem_top(name, side) + H / 2.0}
            ends.setdefault(key, []).append(item)
            order.append(item)

    if BUSES_ON:
        for b in BUSES:      # the broker joins every lane from below -- spread along its TOP edge
            item = {"kind": "broker", "comp": "activemq", "bus": b, "side": "T",
                    "ox": b["left"] + SPINE_W / 2.0, "oy": SPINE_TOP + SPINE_H}
            ends.setdefault(("activemq", "T"), []).append(item)
            order.append(item)

    live = [(a, z) for a, z in ARROWS if a in by_name and z in by_name]
    for a, z in live:
        pair = {"kind": "p2p", "src": a, "dst": z}
        for me, other, role in ((a, z, "src"), (z, a, "dst")):
            e = edge_anchor(me, other)
            item = {"kind": "p2p-end", "comp": me, "pair": pair, "role": role, "side": side_of(e),
                    "ox": C[other]["left"] + W / 2.0, "oy": C[other]["top"] + H / 2.0}
            ends.setdefault((me, side_of(e)), []).append(item)
        order.append(pair)

    # ---- 2. hand out the slots: every line on an edge gets its own ------------------------------------------
    for (comp_name, side), group in ends.items():
        vertical = side in ("L", "R")
        cx = elem_left(comp_name, side) + W / 2.0
        cy = elem_top(comp_name, side) + H / 2.0

        # ORDER BY WHERE THE LINE IS ACTUALLY HEADING -- purely geometric, no rule about "kinds".
        #
        # On a vertical edge, sort by the line's VERTICAL offset: lines heading UP take the top slots, lines
        # heading DOWN take the bottom ones, and a horizontal line (every bus tap -- it leaves sideways at the
        # component's own height) sits in the middle. So a line leaves the box already pointing at the thing it
        # is going to, and lines on the same edge cannot cross each other.
        #
        # TWO EARLIER ATTEMPTS AT THIS WERE WRONG, and both wrongnesses came from not asking the geometry:
        #   1. Sorting bus taps by their LANE'S X against p2p arrows by the other BOX'S Y -- comparing an x with
        #      a y. Meaningless comparison, meaningless picture.
        #   2. "Rank buses above point-to-point." That gets pacMan / bizTree / enyMan right BY ACCIDENT -- the DB
        #      happens to sit below them -- and gets keySmith WRONG, because Esq2025 is ABOVE keySmith, so its
        #      arrow belongs at the TOP of that edge. A rule about kinds cannot know that; the geometry does.
        group.sort(key=lambda it: ((it["oy"] - cy, it["ox"]) if vertical else (it["ox"] - cx, it["oy"])))
        for pos, item in zip(slots(len(group)), group):
            if vertical:
                item["anchor"] = {"x": 1 if side == "R" else -1, "y": -pos}
            else:
                item["anchor"] = {"x": pos, "y": 1 if side == "T" else -1}

    # ---- 3. emit ------------------------------------------------------------------------------------------
    for item in order:
        if item["kind"] == "bus":
            b, name, side = item["bus"], item["comp"], item["side"]
            a = item["anchor"]
            # the pixel row this tap runs along, and the point on the lane at that SAME row -> still 0 degrees
            pixel_y = (elem_top(name, side) + H / 2.0) - a["y"] * (H / 2.0)
            elem(name, side)["connections"].append({
                "targetName": "bus:" + b["id"], "path": "straight",
                "source": a,
                "target": {"x": -a["x"], "y": (spine_mid - pixel_y) / spine_half},
                "color": {"fixed": b["color"]}, "size": {"fixed": 2},
                "direction": ARROWHEAD[item["flow"]],
            })
        elif item["kind"] == "broker":
            b = item["bus"]
            by_name["activemq"]["connections"].append({
                "targetName": "bus:" + b["id"], "path": "straight",
                "source": item["anchor"],
                "target": {"x": 0, "y": -1},       # the bottom end of the lane
                "color": {"fixed": b["color"]}, "size": {"fixed": 2},
                # NO arrowhead: nothing flows "to" or "from" the broker -- it IS the medium the flow happens in.
                "direction": "none",
            })
        else:      # a p2p pair -- its two endpoints were each given a slot on their own edge
            src, dst = item["src"], item["dst"]
            se = next(x for g in ends.values() for x in g
                      if x.get("pair") is item and x["role"] == "src")
            de = next(x for g in ends.values() for x in g
                      if x.get("pair") is item and x["role"] == "dst")
            sa, da = se["anchor"], de["anchor"]
            # THREE KINDS OF LINE, THREE COLOURS -- so the KIND of coupling is readable without following the wire:
            #   coloured lane colour : the async bus (a publish; nobody waits)
            #   RED                  : a DB call  -- the component model draws these red, and the board matches it
            #   grey                 : a REST call
            # A DB call and a REST call are both point-to-point and both block, but they fail differently and are
            # owned differently; drawing them identically was making the reader trace every line to find out which
            # was which.
            is_db = (dst == "postgres" or src == "postgres")
            elem(src, se["side"])["connections"].append({
                "targetName": elem(dst, de["side"])["name"], "path": "angled",
                "source": sa, "target": da,
                # THIN (mir0n): a point-to-point call is a hairline next to a bus lane. Weight on this board means
                # "how much of the system rides on this" -- the lanes are the shared medium and get 2; a single
                # call between two components gets the thinnest line that still reads as a line.
                "color": {"fixed": DB_RED if is_db else REST_BLACK}, "size": {"fixed": 1.25},
                "direction": "forward",     # something called, and waited: the head lands on the callee
            })

    for b in (BUSES if BUSES_ON else []):
        elements.append(bus_spine(b))
        elements.append(bus_label(b))

    elements.extend(legend())
    elements.extend(hub_caption())

    return {
        "type": "canvas",
        "title": "The system -- every component, live",
        "datasource": DS,
        # h=24, NOT 27: the canvas occupies y .. y+h-1, so at 27 it ran from y=1 to y=27 -- under the row
        # header at y=25 and the three bus panels at y=26. The drawing needs 676px and 24 rows give ~950,
        # so nothing is clipped; check_no_panel_overlap refuses the taller form now.
        "gridPos": {"h": 24, "w": 24, "x": 0, "y": 1},
        "description": (
            "Every component of the system, live. Each box carries a LIVE NUMBER, not just a colour -- a traffic "
            "light tells you a thing is RUNNING, not that it has stopped doing any WORK, which is the failure "
            "that actually happens. CLICK A BOX for its detail. "
            "GREEN = reporting, RED = down, GREY = no data (which here almost always means observability is OFF "
            "-- ESQ_OBSERVABILITY_ENABLED, off by default -- not that the component is broken). "
            "THE SHAPE IS AN ASSERTION ABOUT THE ARCHITECTURE. An ARROW is a point-to-point call (REST / DB): "
            "something called and WAITED for an answer. A DROP onto a BAR is the async bus -- a shared medium, "
            "where a service publishes to a destination and walks away, and who is listening is not its business. "
            + (("THERE ARE TWO BARS, not one, because there are TWO BUSES -- and which services share a medium "
                "(and which never meet) is the whole thing this picture is for: bizTree and keySmith never touch; "
                "enyMan is on both. One bar would hide that. ")
               if "aukeep" not in VISIBLE else
               ("THERE ARE THREE BARS, not one, because there are THREE BUSES -- and which services share a medium "
                "(and which never meet) is the whole thing this picture is for: bizTree and keySmith never touch; "
                "auKeep hears everyone and answers no one; enyMan sits on all three. One bar would hide all of that. "))
            +
            "The observability stack is deliberately NOT on this board: the viewer is not the system, and six "
            "boxes of tooling watching the tooling would drown the question this board exists to answer."),
        "targets": targets(),
        "fieldConfig": {
            "defaults": {
                "color": {"mode": "thresholds"},
                # 0 = DOWN (red) | 1 = TROUBLE (amber) | 2 = OK (green). See health() above.
                "thresholds": {"mode": "absolute",
                               "steps": [{"color": "red", "value": None},
                                         {"color": "orange", "value": 1},
                                         {"color": "green", "value": 2}]},
                "decimals": 2,
                "mappings": [],
            },
            "overrides": unit_overrides() + alarm_overrides(),
        },
        "options": {
            "inlineEditing": False,
            "showAdvancedTypes": True,
            "panZoom": False,
            "infinitePan": False,
            "root": {
                "name": "Esquire",
                "type": "frame",
                "elements": elements,
                "background": {"color": {"fixed": "transparent"}},
                "border": {"color": {"fixed": "transparent"}},
                "constraint": {"horizontal": "left", "vertical": "top"},
                "placement": {"left": 0, "top": 0, "width": 100, "height": 100},
            },
        },
    }


def row(title, y, collapsed=False, panels=None):
    r = {"type": "row", "title": title, "gridPos": {"h": 1, "w": 24, "x": 0, "y": y}, "collapsed": collapsed}
    if panels is not None:
        r["panels"] = panels
    return r


def tgt(expr, legend=None):
    return {"refId": "A", "datasource": DS, "expr": expr, "legendFormat": legend}


def ts(title, x, y, w, unit, tl, h=8, desc=None, ds=None):
    for i, t in enumerate(tl):
        t["refId"] = chr(65 + i)
    p = {"type": "timeseries", "title": title, "datasource": ds or DS,
         "gridPos": {"h": h, "w": w, "x": x, "y": y},
         "fieldConfig": {"defaults": {"custom": {"drawStyle": "line", "fillOpacity": 10}, "unit": unit, "min": 0},
                         "overrides": []},
         "targets": tl}
    if desc:
        p["description"] = desc
    return p



def flat_panels(panels):
    """Every panel, including the ones nested inside collapsed rows."""
    ret = []
    for p in panels or []:
        ret.append(p)
        ret.extend(flat_panels(p.get("panels")))
    return ret


def build():
    """THE DRAWN TOPOLOGY -- the picture. This is the dashboard called "Esquire Topology".

    It is the same arrangement as doc/media/ComponentModel.png: three VERTICAL bus lanes (Audit / IAM
    Request-Response / Entity Broadcast, in that order), auKeep and the stores to their LEFT, the services to
    their RIGHT, the gateway / Explorer / browser beyond them, and the broker beneath the lanes. The board and the
    document are ONE picture, with the numbers switched on.

    A tile-grid "layers" view was tried here and REVERTED (mir0n): tiles answer "how is each tier doing", and
    swapping them in threw away the only view that shows the SHAPE -- which services share a bus, and which never
    meet. No tile grid can say that. The generator emits ONE dashboard.
    """
    p = [row("The system", 0), canvas()]

    # ---- the BUS, from both ends ----
    p.append(row("The bus -- both ends of every hop", 25))
    p.append(ts("Traffic per BUS (msg/s)", 0, 26, 8, "ops",
                [tgt("sum by (bus_id) (rate(messaging_send_total[1m]))", "sent -> {{bus_id}}"),
                 tgt("sum by (bus_id) (rate(messaging_receive_total[1m]))", "recv <- {{bus_id}}")],
                desc=("The two lanes above, as traffic. esquire.kc and esquire.entity are SEPARATE MEDIA with "
                      "different participants -- which is what the two lanes in the picture say, and what a single "
                      "bar (or a mesh of arrows) would hide."
                      if "aukeep" not in VISIBLE else
                      "The three lanes above, as traffic. audit-c, esquire.kc and esquire.entity are SEPARATE "
                      "MEDIA with different participants -- which is what the three lanes in the picture say, and "
                      "what a single bar (or a mesh of arrows) would hide.")))
    p.append(ts("Queue depth per destination  (what the BROKER holds)", 8, 26, 8, "short",
                [tgt("activemq_queue_depth", "{{destination}}")],
                desc="Flat at zero is healthy. Climbing = consumers gone, wedged, or slower than the producers -- "
                     "and unlike any service-side meter, this STAYS TRUE WHEN THE CONSUMER IS DEAD."))
    p.append(ts("Consumers per destination  (the broker's view)", 16, 26, 8, "short",
                [tgt("activemq_queue_consumer_count", "{{destination}}"),
                 tgt("activemq_topic_consumer_count", "{{destination}}")],
                desc="A destination whose consumer count drops to ZERO while its box stays GREEN in the picture: "
                     "the process is alive and has STOPPED LISTENING. Only the broker can see that."))

    # ---- the LIVE flow: not drawn, DISCOVERED ----
    p.append(row("Live request flow -- discovered from real traces", 34))
    p.append({
        "type": "nodeGraph", "title": "Service graph -- who ACTUALLY calls whom",
        "datasource": DS_TEMPO,
        "gridPos": {"h": 12, "w": 12, "x": 0, "y": 35},
        "targets": [{"refId": "A", "datasource": DS_TEMPO, "queryType": "serviceMap"}],
        "description": (
            "The picture above is the architecture as DESIGNED -- always-on, every component, even at zero "
            "traffic. THIS is the architecture as it is actually RUNNING, derived by the Collector's servicegraph "
            "connector from real spans: it stays true when the code changes and a hand-laid picture would quietly "
            "go stale. It includes the R&R BUS HOPS, because those are real PRODUCER/CONSUMER spans. "
            "It shows only what TRAFFIC has touched -- a component nobody called is simply ABSENT, which is "
            "exactly why the drawn board exists alongside it. Empty at rest is CORRECT."),
    })
    p.append(ts("Edge throughput -- caller -> callee (req/s)", 12, 35, 12, "reqps",
                [tgt("sum by (client, server) (rate(traces_service_graph_request_total[5m]))",
                     "{{client}} -> {{server}}")],
                h=12,
                desc="THE INTERESTING FAILURE: an edge that VANISHES here while both of its boxes stay GREEN in "
                     "the picture above. Both ends are alive and the CALL BETWEEN THEM has stopped -- and no "
                     "per-component health check anywhere would ever tell you that."))

    # The four silent classes, checked before anything is written. flat() is needed because the topology board
    # nests its timeseries inside collapsed rows, and a guard that only saw the top level would pass a board
    # whose defects all live one level down.
    checked = flat_panels(p)
    check_no_naked_subtraction(checked)
    check_rows_do_not_share_y(checked)
    check_no_panel_overlap(checked)   # refuse two panels whose RECTANGLES intersect
    check_no_clamped_rate_denominator(checked)
    check_avg_scales_only_seconds(checked)

    return {
        "uid": "esq-topology",
        "title": "Esquire Topology -- the system, live",
        # LOCKED: the layout is generated, so a UI edit would be silently overwritten on the next
        # provisioning reload -- and worse, would look like it had stuck.
        "editable": False,
        "tags": ["esquire", "o11y", "topology"],
        "timezone": "",
        "schemaVersion": 39,
        "version": 1,
        "refresh": "30s",
        "time": {"from": "now-1h", "to": "now"},
        "panels": p,
    }


# ---------------------------------------------------------------------------------------------------------
# BUILD-TIME GUARDS -- the same set the dashboard generator carries, and for the same reason: these four
# defects are all SILENT. An empty operand deletes a band instead of drawing it low, a x1000 on a summary
# draws a plausible number that is a thousand times wrong, a floored divisor renders a real average as a
# fraction of itself, and a panel sharing a row header's y renders inside the wrong row. None of them looks
# wrong on the board, so none is caught by looking.
#
# The canvas cells rest on an invariant worth stating, because nothing else states it: every operand of a
# `((x) or vector(0))` sum is a full sum()/max()/min() WITHOUT `by`, so it carries no labels. `or vector(0)`
# yields a label-less series and `+` matches on identical label sets -- a labelled operand beside an empty
# one would match nothing and blank the cell.
# ---------------------------------------------------------------------------------------------------------


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


def _is_number(text):
    try:
        float(text)
        return True
    except ValueError:
        return False


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


def main():
    """ONE source, THREE boards -- docker (x1), local-k8s (x2), and OKE (x2 minus the cloud deltas).

    They were the same file until k8s: docker runs one of everything, k8s runs the app tier x2, and a board that
    draws the same single box for both is telling one of them a lie. The difference is the replica map -- nothing
    else forks -- so the two stay the same drawing with the k8s one showing what is doubled.

    OKE is a THIRD board (T12). It runs the app tier x2 too, but the cloud topology genuinely differs -- and the
    generator header's own rule applies: if the ARCHITECTURE changes, the picture changes. The OKE deltas:
      * NO auKeep and NO audit lane -- OKE audits via DB TRIGGERS; the audit-off bus is a disabled no-op
        (k8s-oci/esquire-topology.yml), so there is no audit medium to draw and no drain to draw it to.
      * BFF x1 -- the OKE BFF uses an in-memory session store (no redis), so it is a single card, not a pair.
    Everything else is the k8s board. The two dropped names (component `aukeep`, bus `audit-c`) filter out of
    VISIBLE / BUSES / ARROWS for the OKE pass; the replica map is OKE_CARDS. FIRST-CUT: the geometry is NOT
    re-tuned for the two gaps this leaves (auKeep's empty slot left of the lanes, the missing audit lane at
    left=300) -- the topology is correct, the hand-laid spacing is mir0n's step, diff-locked to its OWN OKE model.
    """
    global CARDS, LAYERS, VISIBLE, BUSES, ARROWS
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.abspath(os.path.join(here, "..", "..", ".."))
    base = {n: C[n]["left"] for n in C}          # the docker x-coordinates, to restore between targets
    layers0 = [list(layer) for layer in LAYERS]  # pristine; a target filters dropped components out of the Z-walk
    buses0 = [dict(b) for b in BUSES]            # pristine COPIES -- the OKE target slides a lane's `left`
    arrows0 = list(ARROWS)
    # OKE app tier x2 (the six Java services). BFF (backend) is NOT listed -> one card. auKeep is dropped below.
    OKE_CARDS = {n: 2 for n in ("pacman", "biztree", "enyman", "keysmith", "kcmaster", "gateway")}
    # (drop_comp, drop_bus, bus_left) per target. OKE: drop auKeep + the audit lane, and slide the IAM (kc) lane
    # into the vacated audit slot (300) -- with only two lanes left, that puts the broker (which does NOT move)
    # centred under BOTH of them instead of under the left one (mir0n's alternative to moving the broker).
    for path, model, lefts, drop_comp, drop_bus, bus_left in (
            (os.path.join(root, "compose", "o11y", "grafana", "provisioning", "dashboards",
                          "esquire-topology.json"), {}, {}, set(), set(), {}),
            (os.path.join(root, "k8s", "charts", "infra", "grafana", "dashboards",
                          "esquire-topology.json"), K8S_CARDS, K8S_LEFT, set(), set(), {}),
            (os.path.join(root, "k8s-oci", "grafana", "esquire-topology.json"),
                          OKE_CARDS, K8S_LEFT, {"aukeep"}, {"audit-c"}, {"esquire.kc": 300})):
        CARDS = model
        LAYERS = [[n for n in layer if n not in drop_comp] for layer in layers0]
        VISIBLE = [n for layer in LAYERS[:LAYERS_ON] for n in layer]
        BUSES = [dict(b) for b in buses0 if b["id"] not in drop_bus]
        for b in BUSES:
            if b["id"] in bus_left:
                b["left"] = bus_left[b["id"]]
        ARROWS = [a for a in arrows0 if not (set(a) & drop_comp)]
        for n in C:
            C[n]["left"] = lefts.get(n, base[n])
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            json.dump(build(), f, indent=1)
        tag = ("OKE -- x2, BFF x1, no auKeep, IAM lane in the audit slot (broker centred under both)" if drop_comp
               else "k8s -- x2, one card per instance" if model else "docker -- single instance")
        print("wrote", path, "(%s)" % tag)


if __name__ == "__main__":
    main()
