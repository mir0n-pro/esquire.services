#!/usr/bin/env bash
# =============================================================================
# Freshness-guard smoke -- the change-number guard, proved on a running stack.
#
# WHAT THIS PROVES, and why it is the shape it is.
#
# The guard skips an entity-broadcast event whose change number is not newer than the one the bizTree
# cache already holds. It has two failure directions, and they are NOT equally easy to see:
#
#   over-skip  -- a legitimate event is dropped. The cache silently keeps stale data until the
#                 night-watch heals it. NOTHING fails; nothing logs an error. This is the dangerous
#                 direction, and it is what this smoke hunts.
#   under-skip -- a stale event is applied. Self-correcting on the next real event, and covered
#                 deterministically by MessageHandlerHubGuardTest (R1/R2/R6/R7).
#
# The worst over-skip is a MOVE. A move rewrites every DESCENDANT's path row while leaving those
# descendants' entity rows untouched, so a descendant's path event carries the PATH change number
# while the cache may hold a much higher ENTITY number for that same node. Compare the two and every
# descendant's path update is skipped -- the subtree stays half-repathed, and only the night-watch
# notices. So: build a subtree, move it, and check EVERY node, not just the one that was moved.
#
# Cache vs database is read through the two endpoints that already differ in source:
#   /esq-tree      -> served FROM the bizTree H2 cache
#   /esq-cmd-tree  -> served FROM the database (natural FK walk)
# They must agree on every node's entity path after the move settles.
#
# Usage:  ./run.sh            (docker stack, gateway on :7070, KeyCloak on :8081)
#
# Every target is an env override, so the same run drives any stack:
#   GW    gateway base url            KC    KeyCloak token endpoint
#   PSQL  how to reach postgres       e.g. "kubectl exec -i esquire-infra-postgres-0 -n default -- psql"
# k8s / compact example (port-forward the gateway and KeyCloak first):
#   GW=http://localhost:7071 KC=http://localhost:8091/kc-auth/realms/esquire/protocol/openid-connect/token #   PSQL="kubectl exec -i esquire-infra-postgres-0 -n default -- psql" ./run.sh
# =============================================================================
set -uo pipefail

GW="${GW:-http://localhost:7070}"
KC="${KC:-http://localhost:8081/kc-auth/realms/esquire/protocol/openid-connect/token}"
CLIENT="${CLIENT:-esq-hauberk}"
SECRET="${SECRET:-esq-hauberk-dev-secret-rotate-in-prod}"
TEST_HOUSE="${TEST_HOUSE:-14}"
# How to reach postgres. Docker compose by default; k8s / compact pass their own.
PSQL="${PSQL:-docker exec esq-postgres psql}"
STAMP="$(date +%y%m%d-%H%M%S)"

PASS=0
FAIL=0

ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1"; FAIL=$((FAIL+1)); }

tok() {
  curl -s -X POST "$KC" -d 'grant_type=client_credentials' \
       -d "client_id=$CLIENT" -d "client_secret=$SECRET" \
    | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])"
}

TOKEN="$(tok)"
if [ -z "$TOKEN" ]; then echo "no token -- is the stack up?"; exit 2; fi
AUTH=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")

newOrg() {  # $1=parentId $2=name -> id
  curl -s -X POST "${AUTH[@]}" -H "X-Request-ID: fg-$STAMP-$2" \
       "$GW/esq-cmd-new?kind=20&parentId=$1" -d "{\"name\":\"$2\",\"desc\":\"freshness guard\"}" \
    | python -c "import sys,json;print(json.load(sys.stdin)['id'])"
}

newUsr() {  # $1=parentId $2=tag -> id
  curl -s -X POST "${AUTH[@]}" -H "X-Request-ID: fg-$STAMP-$2" \
       "$GW/esq-cmd-new?kind=34&parentId=$1" \
       -d "{\"desc\":\"freshness\",\"person\":{\"firstName\":\"Fg\",\"lastName\":\"$2\",\"email\":\"fg-$STAMP-$2@example.com\"}}" \
    | python -c "import sys,json;print(json.load(sys.stdin)['id'])"
}

# Read one number straight from the PRIMARY database. The stack runs on either dialect, so try the
# Postgres container first and fall back to the host Oracle instance -- whichever is actually serving
# the entities answers, the other returns nothing. Without this the trap-arming check silently reports
# empty on Oracle, and the smoke would pass while proving less than it claims.
ORA_CONN="${ORA_CONN:-esq2025/q@//localhost:1521/MIR0N}"
SQLPLUS="${SQLPLUS:-/c/ora19/bin/sqlplus.exe}"

dbnum() {  # $1 = a SELECT returning ONE number
  local q="$1" v=""
  v="$($PSQL -U esq2025 -d esq2025 -t -A -c "$q" 2>/dev/null | tr -dc '0-9')"
  if [ -z "$v" ] && [ -x "$SQLPLUS" ]; then
    v="$(printf 'set heading off feedback off pagesize 0
%s;
exit
' "$q"          | "$SQLPLUS" -S "$ORA_CONN" 2>/dev/null | tr -dc '0-9
' | grep -E '^[0-9]+$' | head -1)"
  fi
  echo "$v"
}

# entity paths as the CACHE sees them, for the subtree under $1 -> "id=path" lines
cachePaths() {
  curl -s "${AUTH[@]}" "$GW/esq-tree?id=$1" \
    | python -c "
import sys,json
for n in json.load(sys.stdin):
    if n.get('entityId'):                       # real entities only; folder nodes have none
        print(str(n['entityId'])+'='+str(n.get('entityPath')))
" | sort
}

# entity paths as the DATABASE sees them, for the subtree under $1
dbPaths() {
  curl -s "${AUTH[@]}" "$GW/esq-cmd-tree?kind=20&id=$1" \
    | python -c "
import sys,json
for n in json.load(sys.stdin):
    if n.get('entityId'):
        print(str(n['entityId'])+'='+str(n.get('entityPath')))
" | sort
}

echo "=== freshness-guard smoke -- $STAMP ==="

# Wait for the bizTree CACHE to be serving before building anything. Run this smoke seconds after a deploy
# and the fixture's CREATE broadcasts race bizTree's bootstrap load: the cache legitimately misses them, the
# cache-vs-database check then fails, and it reads as a guard bug when it is a warm-up race.
# A seeded read is the signal -- Test House is in every seed, so the cache is loaded once it answers.
READY=0
for _ in $(seq 1 40); do
  READY="$(curl -s "${AUTH[@]}" "$GW/esq-tree?id=$TEST_HOUSE" | python -c "
import sys,json
try: print(len(json.load(sys.stdin)))
except Exception: print(0)" 2>/dev/null)"
  [ "${READY:-0}" -gt 0 ] && break
  sleep 3
done
if [ "${READY:-0}" -gt 0 ]; then
  ok "bizTree cache is serving ($READY nodes under Test House) -- safe to build the fixture"
else
  bad "bizTree cache never became ready; aborting rather than reporting a misleading result"
  echo; echo "=== 0 passed, 1 failed ==="; exit 1
fi

# ---------------------------------------------------------------------------
# Build a subtree deep enough that the move produces path events for nodes whose
# ENTITY rows are never touched -- that is the case the guard can wrongly skip.
#   HOME_A / HOME_B : two parents to move between
#   TOP             : the org that moves
#   MID             : a child org of TOP        (path event only)
#   USR             : a user under MID          (path event only)
# ---------------------------------------------------------------------------
HOME_A="$(newOrg "$TEST_HOUSE" "FgHomeA$STAMP")"
HOME_B="$(newOrg "$TEST_HOUSE" "FgHomeB$STAMP")"
TOP="$(newOrg "$HOME_A" "FgTop$STAMP")"
MID="$(newOrg "$TOP" "FgMid$STAMP")"
USR="$(newUsr "$MID" "u1")"
echo "homeA=$HOME_A homeB=$HOME_B top=$TOP mid=$MID usr=$USR"
for v in "$HOME_A" "$HOME_B" "$TOP" "$MID" "$USR"; do
  if [ -z "$v" ]; then
    echo "  FAIL  fixture was not built -- one or more creates returned no id (is enyMan up?)"
    echo; echo "=== 0 passed, 1 failed ==="; exit 1
  fi
done
ok "fixture built: 2 homes, a 2-deep org chain and a user"
sleep 3

# ---------------------------------------------------------------------------
# 1. Raise the ENTITY numbers of the descendants WITHOUT moving anything.
#    This is what makes the test bite: after these renames MID and USR carry entity
#    numbers well above the path number the coming move will send. A guard that
#    compared a path event against the entity number would now skip them.
# ---------------------------------------------------------------------------
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -X POST "${AUTH[@]}" -H "X-Request-ID: fg-$STAMP-r$i" \
       "$GW/esq-cmd-save?kind=20&id=$MID" -d "{\"desc\":\"rename $i\"}"
done
sleep 3
MIDCN="$(dbnum "SELECT org_change_no FROM esq_org WHERE org_pk=$MID")"
MIDEP="$(dbnum "SELECT ep_change_no FROM esq_entity_path WHERE ep_pk=$MID")"
if [ "${MIDCN:-0}" -gt "${MIDEP:-0}" ]; then
  ok "descendant entity number ($MIDCN) is now ABOVE its path number ($MIDEP) -- the trap is armed"
else
  bad "could not arm the trap: entity=$MIDCN path=$MIDEP (need entity > path)"
fi

# ---------------------------------------------------------------------------
# 2. Move TOP from HOME_A to HOME_B. Every node under TOP gets a path event.
# ---------------------------------------------------------------------------
MV="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${AUTH[@]}" -H "X-Request-ID: fg-$STAMP-mv" \
        "$GW/esq-move?kind=20&id=$TOP&dist_id=$HOME_B")"
if [ "$MV" = "200" ] || [ "$MV" = "202" ]; then ok "move accepted ($MV)"; else bad "move returned $MV"; fi

# ---------------------------------------------------------------------------
# 3. THE CHECK. Cache and database must agree on EVERY node of the moved subtree.
#    A guard that over-skips shows up here as a descendant still on its old path.
#
# /esq-move is async-ack -- the move queue worker repaths and publishes after the answer -- so this waits
# rather than assuming a duration. A fixed sleep was wrong in BOTH directions: too short and a slow stack
# reads as a skipped path event (seen once: the deepest node still missing at +8s), too long and every run
# pays for the worst case. Worse, it can only ever be a guess, and a guess in the one check that hunts a
# SILENT failure hides the real thing behind "it passed this time".
#
# The condition is deliberately not "cache == database". Before the worker runs they already agree -- on
# the OLD paths -- so that alone would pass instantly on the pre-move state and prove nothing. The move
# must have LANDED first: the database subtree has to be under HOME_B, with nothing left under HOME_A.
# Only then does cache-vs-database mean what this smoke says it means.
#
# Polling for completion is the right shape, and the framework's own answer to it is
# `doc/Esquire.MessagingBus.ContinuingDev.md` item 6 -- the FIX-like continuing-processing protocol, where an
# async command either streams status updates to a subscriber or returns an ACK the caller then asks about.
# That gives a completion status to wait ON. This smoke has no such status to ask for, so it waits on the
# DATA instead and infers the move has landed from the paths themselves. When item 6 exists, this loop asks
# the request about itself and stops inferring.
# ---------------------------------------------------------------------------
SETTLE_TIMEOUT="${SETTLE_TIMEOUT:-30}"      # seconds; override for a slow or loaded stack
C=""; D=""; WAITED=0
for _ in $(seq 1 "$SETTLE_TIMEOUT"); do
  D="$(dbPaths "$TOP")"
  if [ -n "$D" ] \
  && [ "$(echo "$D" | grep -c "=1\.${TEST_HOUSE}\.${HOME_A}\." || true)" -eq 0 ] \
  && [ "$(echo "$D" | grep -c "=1\.${TEST_HOUSE}\.${HOME_B}\." || true)" -gt 0 ]; then
    C="$(cachePaths "$TOP")"
    [ "$C" = "$D" ] && break
  fi
  sleep 1; WAITED=$((WAITED+1))
done
# One last read, so a timeout reports the CURRENT disagreement rather than a stale one.
D="$(dbPaths "$TOP")"; C="$(cachePaths "$TOP")"
echo "    settled after ${WAITED}s (timeout ${SETTLE_TIMEOUT}s)"
if [ -z "$D" ]; then
  bad "database returned no subtree for $TOP -- the move itself failed"
else
  if [ "$C" = "$D" ]; then
    ok "cache and database agree on every node of the moved subtree ($(echo "$D" | wc -l) nodes)"
  else
    bad "cache and database DISAGREE -- a path event was skipped or lost"
    echo "    --- cache ---"; echo "$C" | sed 's/^/      /'
    echo "    --- database ---"; echo "$D" | sed 's/^/      /'
    echo "    --- diff ---"; diff <(echo "$C") <(echo "$D") | sed 's/^/      /'
  fi
fi

# every moved node must sit under HOME_B now
if [ -z "$D" ]; then
  bad "no subtree to check for left-behind nodes"
else
  STILL_OLD="$(echo "$D" | grep -c "=1\.${TEST_HOUSE}\.${HOME_A}\." || true)"
  MOVED="$(echo "$D" | grep -c "=1\.${TEST_HOUSE}\.${HOME_B}\." || true)"
  if [ "${STILL_OLD:-0}" -eq 0 ] && [ "${MOVED:-0}" -gt 0 ]; then
    ok "all $MOVED node(s) repathed under the new home, none left behind"
  else
    bad "left behind=$STILL_OLD, repathed=$MOVED (expected 0 left behind and at least 1 repathed)"
  fi
fi

# ---------------------------------------------------------------------------
# 4. The entity stream still applies after all that: rename MID once more and
#    check the cache picks it up (the guard must not have wedged the entity side).
# ---------------------------------------------------------------------------
FINAL="fg-final-$STAMP"
curl -s -o /dev/null -X POST "${AUTH[@]}" -H "X-Request-ID: fg-$STAMP-fin" \
     "$GW/esq-cmd-save?kind=20&id=$MID" -d "{\"desc\":\"$FINAL\"}"
sleep 4
SEEN="$(curl -s "${AUTH[@]}" "$GW/esq-tree?id=$TOP" \
        | python -c "
import sys,json
for n in json.load(sys.stdin):
    if str(n.get('entityId')) == '$MID': print(n.get('desc'))
")"
if [ "$SEEN" = "$FINAL" ]; then
  ok "entity update still applies after the move (cache shows the new description)"
else
  bad "entity update did not reach the cache (want '$FINAL', cache has '$SEEN')"
fi

# ---------------------------------------------------------------------------
# Teardown: delete the two homes (cascades the subtree).
# ---------------------------------------------------------------------------
for id in "$TOP" "$MID" "$HOME_A" "$HOME_B"; do
  curl -s -o /dev/null -X POST -H "Authorization: Bearer $TOKEN" -H "X-Request-ID: fg-$STAMP-del-$id" \
       "$GW/esq-cmd-del?kind=20&id=$id" 2>/dev/null
done

echo
echo "=== $PASS passed, $FAIL failed ==="
echo "NOTE: the stale-skip direction (redelivery, out-of-order) is proven deterministically by"
echo "      bizTree MessageHandlerHubGuardTest -- forcing a broker redelivery live is not attempted here."
[ "$FAIL" -eq 0 ]
