#!/usr/bin/env bash
# =============================================================================
# Health smoke -- the messaging-bus alive-protocol readiness/liveness chaos check.
# Automates what README.md describes by hand, with ASSERTIONS:
#   1. readiness sweep -- every service reports messagingBus on /actuator/health/readiness = UP (200)
#   2. broker DOWN     -- a producer's readiness flips 503 while liveness stays 200 (depool, never restart)
#   3. broker UP       -- the leg recovers on its own (failover:) and readiness returns to 200
# Records PASS/FAIL to results-<stamp>-<mode>.md; exits NON-ZERO on any failed assertion.
#
# Usage:
#   ./run.sh            docker mode (default) -- the compose stack
#   ./run.sh docker
#   ./run.sh k8s        local-k8s mode (kubectl context MUST be docker-desktop)
#
# Prereqs: the stack up (compose, or local k8s) + curl (+ kubectl for k8s mode).
# NOTE: this validates the bus connection health (the alive protocol). The keepDatasource DB-health
# dimension has its own kill-the-DB capture (see results-*-k8s-db.md); it is not driven here.
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODE="${1:-docker}"
STAMP="$(date +%y%m%d-%H%M)"
RESULTS="${HERE}/results-${STAMP}-${MODE}.md"
FAILS=0

log()  { echo "$@" | tee -a "$RESULTS"; }
pass() { log "PASS -- $*"; }
fail() { log "FAIL -- $*"; FAILS=$((FAILS + 1)); }

ORDER=(enyman pacman keysmith kcmaster biztree aukeep)
CHAOS_SVC="enyman"                      # carries all three buses (audit / kc / entity)
DOWN_WAIT=60                            # seconds to wait for the DOWN edge (bounded by alive-timeout + termination)
UP_WAIT=120                            # seconds to wait for recovery (broker restart + failover reconnect)

# docker stack: actuator ports on localhost
declare -A DPORT=( [enyman]=3003 [pacman]=3004 [keysmith]=3005 [kcmaster]=3006 [biztree]=3002 [aukeep]=3007 )
# k8s: container actuator ports (reached via kubectl port-forward)
declare -A KPORT=( [enyman]=3003 [pacman]=3003 [keysmith]=3002 [kcmaster]=3006 [biztree]=3002 [aukeep]=3007 )

rc() { curl -s --max-time 12 -o /dev/null -w '%{http_code}' "$1" 2>/dev/null; }

log "# Health smoke run -- ${MODE} -- ${STAMP}"
log ""

# ----------------------------------------------------------------------------- docker mode
if [ "$MODE" = "docker" ]; then
  BROKER="esq-activemq"
  url() { echo "http://localhost:${DPORT[$1]}/actuator/health/$2"; }

  log "## 1. readiness sweep (all UP)"
  for s in "${ORDER[@]}"; do
    c=$(rc "$(url "$s" readiness)")
    [ "$c" = "200" ] && pass "readiness $s = 200" || fail "readiness $s = $c (expected 200)"
  done

  log ""; log "## 2. broker DOWN -> readiness 503 / liveness 200 (on ${CHAOS_SVC})"
  docker stop "$BROKER" >/dev/null 2>&1
  t0=$(date +%s); c=200
  while [ $(($(date +%s) - t0)) -lt $DOWN_WAIT ]; do
    c=$(rc "$(url "$CHAOS_SVC" readiness)"); [ "$c" != "200" ] && break; sleep 2
  done
  lv=$(rc "$(url "$CHAOS_SVC" liveness)"); el=$(($(date +%s) - t0))
  [ "$c" = "503" ] && pass "readiness=503 at +${el}s (broker down)" || fail "readiness=$c after ${el}s (expected 503)"
  [ "$lv" = "200" ] && pass "liveness=200 (pod stays up)" || fail "liveness=$lv (expected 200 -- a broker outage must NOT fail liveness)"

  log ""; log "## 3. broker UP -> recovery"
  docker start "$BROKER" >/dev/null 2>&1
  t1=$(date +%s); c=503
  while [ $(($(date +%s) - t1)) -lt $UP_WAIT ]; do
    c=$(rc "$(url "$CHAOS_SVC" readiness)"); [ "$c" = "200" ] && break; sleep 2
  done
  el=$(($(date +%s) - t1))
  [ "$c" = "200" ] && pass "recovered readiness=200 after +${el}s" || fail "readiness=$c after ${el}s (did not recover)"

# ----------------------------------------------------------------------------- k8s mode
elif [ "$MODE" = "k8s" ]; then
  CTX="$(kubectl config current-context 2>/dev/null)"
  if [ "$CTX" != "docker-desktop" ]; then
    log "ERROR: kubectl context is '${CTX}', not 'docker-desktop' -- refusing to run k8s chaos."; exit 2
  fi
  BROKER_STS="esquire-infra-amq-activemq"
  podof() { kubectl get pods -o name 2>/dev/null | grep "$1" | head -1 | sed 's|pod/||'; }

  # port-forward helper: forward <pod> <containerPort> on a local port, curl readiness/liveness, kill.
  LP=18200
  fw_rc() { # args: pod cport path
    kubectl port-forward "pod/$1" "$LP:$2" >/dev/null 2>&1 & local pf=$!; sleep 2
    local code; code=$(rc "http://localhost:$LP/actuator/health/$3")
    kill "$pf" >/dev/null 2>&1; wait "$pf" 2>/dev/null; LP=$((LP + 1)); echo "$code"
  }

  log "## 1. readiness sweep (all UP)"
  for s in "${ORDER[@]}"; do
    pod=$(podof "$s")
    if [ -z "$pod" ]; then fail "readiness $s -- no pod"; continue; fi
    c=$(fw_rc "$pod" "${KPORT[$s]}" readiness)
    [ "$c" = "200" ] && pass "readiness $s = 200" || fail "readiness $s = $c (expected 200)"
  done

  log ""; log "## 2. broker DOWN -> liveness 200 (assert) + readiness DOWN (OBSERVE) on ${CHAOS_SVC}"
  ENY=$(podof "$CHAOS_SVC")
  kubectl port-forward "pod/$ENY" 18250:${KPORT[$CHAOS_SVC]} >/dev/null 2>&1 & PF=$!; sleep 3
  rdy() { rc "http://localhost:18250/actuator/health/readiness"; }
  liv() { rc "http://localhost:18250/actuator/health/liveness"; }
  # GRACEFUL shutdown -- the realistic k8s rolling-restart case. activemq closes its connections cleanly (FIN),
  # which the producer-leg health CAN detect (~tens of seconds). The readiness DOWN is OBSERVED, not asserted on
  # k8s: a HARD failure (crashed pod / node loss -> half-open socket) is NOT detected, because with
  # jms.useAsyncSend + failover buffering the heartbeat send still "succeeds" so producerTs stays fresh -- the
  # producer-leg Q&D limit (the round-trip health is its fix; see doc/Esquire.MessagingBus.ContinuingDev.md).
  # What IS asserted on k8s: the readiness sweep, liveness-stays-200, and recovery.
  log "   scaling broker StatefulSet to 0 (graceful) + observing readiness..."
  kubectl scale statefulset "$BROKER_STS" --replicas=0 >/dev/null 2>&1
  t0=$(date +%s); c=200
  while [ $(($(date +%s) - t0)) -lt $DOWN_WAIT ]; do
    c=$(rdy); [ "$c" != "200" ] && break; sleep 3
  done
  lv=$(liv); el=$(($(date +%s) - t0))
  if [ "$c" = "503" ]; then
    pass "readiness flipped 503 at +${el}s (clean shutdown detected)"
  else
    log "OBSERVE -- readiness stayed 200 after ${el}s (a k8s broker drop is not always caught by the producer-leg Q&D; round-trip health is the fix)"
  fi
  [ "$lv" = "200" ] && pass "liveness=200 (pod stays up)" || fail "liveness=$lv (expected 200)"

  log ""; log "## 3. broker UP -> recovery"
  kubectl scale statefulset "$BROKER_STS" --replicas=1 >/dev/null 2>&1
  t1=$(date +%s); c=503
  while [ $(($(date +%s) - t1)) -lt $UP_WAIT ]; do
    c=$(rdy); [ "$c" = "200" ] && break; sleep 3
  done
  el=$(($(date +%s) - t1))
  [ "$c" = "200" ] && pass "recovered readiness=200 after +${el}s" || fail "readiness=$c after ${el}s (did not recover)"
  kill "$PF" >/dev/null 2>&1; wait "$PF" 2>/dev/null

else
  echo "unknown mode '$MODE' -- use: docker | k8s"; exit 2
fi

log ""
if [ "$FAILS" -eq 0 ]; then
  log "RESULT: PASS (all assertions)"; echo "results -> $RESULTS"; exit 0
else
  log "RESULT: FAIL (${FAILS} assertion(s))"; echo "results -> $RESULTS"; exit 1
fi
