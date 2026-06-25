# Health smoke — messaging-bus readiness / liveness validation

Manual validation of the **bus health indicator** (sprint v1.2.9 commit 7). Proves that every service
forwards its bus connection health to `/actuator/health`, that the indicator sits in the **readiness** group
only (a broker outage depools the pod but never restarts it), and that an ActiveMQ leg **recovers on its own**
through the `failover:` transport.

There are two ways to run it: the AUTOMATED `run.sh` (the broker-chaos smoke with assertions, like audit-smoke)
and the MANUAL procedure below (curl + `docker stop/start`). The unit-level logic (`TransportHealth.worst`,
`BusHealthIndicator`, `TransportHealthIndicator`, `AliveSession`) is covered by the messaging module's unit
tests; this harness covers the live end-to-end behavior they cannot.

## Automated -- `run.sh`

```bash
./run.sh            # docker mode (default) -- the compose stack
./run.sh k8s        # local-k8s mode (kubectl context must be docker-desktop)
```

Asserts: (1) a readiness sweep of all six services = UP; (2) the broker down -> liveness stays `200` (the pod is
depooled, never restarted); (3) the broker back -> readiness recovers to `200` on its own (`failover:`). Records
PASS/FAIL to `results-<stamp>-<mode>.md` and exits non-zero on any failed assertion.

The **readiness DOWN** edge is asserted in **docker** mode (`docker stop` is a clean drop -> readiness `503` in
~20-30s) but only **OBSERVED** in **k8s** mode: a HARD k8s failure (crashed pod / node loss -> a half-open
socket) is NOT caught by the producer-leg health, because with `jms.useAsyncSend` + `failover:` buffering the
heartbeat send still "succeeds" so `producerTs` stays fresh. That is the documented producer-leg Q&D limit
(`doc/Esquire.MessagingBus.ContinuingDev.md`); the round-trip health is its fix. A CLEAN k8s shutdown (graceful
rolling-restart) IS detected, so k8s mode uses a graceful `scale --replicas=0` and records whether the drop was
caught.

The keepDatasource DB-health dimension has its own kill-the-DB capture (`results-*-k8s-db.md`) and is not driven
by `run.sh`. The active health SOURCE is the x-rod alive protocol (the `HeartBeat`/`TestRequest` heartbeat, #8).

---

## What it validates

| Claim | How |
|---|---|
| Every service forwards bus health | each `/actuator/health/readiness` lists a `messagingBus` component with one detail per bus it uses |
| Health is in READINESS, not liveness | broker down -> readiness `503 DOWN`, liveness stays `200 UP` (pod depooled, not killed) |
| ActiveMQ observes its connection | a stopped broker flips the connected leg to `DOWN` within ~1s (passive transport listener) |
| Failover auto-recovery | broker back up -> the leg returns to `UP` on its own (no restart), readiness back to `200` |
| auKeep reports its keep DB | aukeep readiness lists a `keepDatasource` component (the `*_log` apply side) |

---

## The services (docker stack ports)

| Service | Port | Buses it reports |
|---|---|---|
| enyman   | 3003 | audit-bus, kc-bus, entity-bus |
| pacman   | 3004 | audit-bus, entity-bus |
| keysmith | 3005 | audit-bus, kc-bus |
| kcmaster | 3006 | kc-bus, entity-bus |
| biztree  | 3002 | entity-bus (+ its own `cacheReadiness`) |
| aukeep   | 3007 | audit-bus (+ `keepDatasource`, the keep `*_log` DB) |

Endpoints per service: `/actuator/health/readiness` and `/actuator/health/liveness`.

---

## Run

### 1. Readiness sweep (all 6 services UP)

```bash
declare -A SVC=( [enyman]=3003 [pacman]=3004 [keysmith]=3005 [kcmaster]=3006 [biztree]=3002 [aukeep]=3007 )
for s in enyman pacman keysmith kcmaster biztree aukeep; do
  p=${SVC[$s]}
  code=$(curl -s -o /tmp/r.json -w '%{http_code}' "http://localhost:$p/actuator/health/readiness")
  echo "--- $s (:$p) HTTP $code"; cat /tmp/r.json; echo
done
```
PASS = every service `HTTP 200` with `messagingBus.status: UP` and every bus detail `UP`.

### 2. Broker-down / recovery (the readiness-liveness split)

Run against enyman (it carries all three buses incl. two ActiveMQ legs). Poll, do not sleep-wait:

```bash
rd(){ curl -s -o /tmp/r.json -w '%{http_code}' "http://localhost:3003/actuator/health/readiness"; }
lv(){ curl -s -o /tmp/l.json -w '%{http_code}' "http://localhost:3003/actuator/health/liveness"; }

docker stop esq-activemq                     # broker down
for i in $(seq 1 25); do c=$(rd); [ "$c" != "200" ] && break; read -t 1 _ </dev/null; done
echo "down: readiness HTTP $c $(cat /tmp/r.json) | liveness HTTP $(lv) $(cat /tmp/l.json)"

docker start esq-activemq                     # broker up
for i in $(seq 1 90); do c=$(rd); [ "$c" = "200" ] && break; read -t 1 _ </dev/null; done
echo "recovered after ${i}s: readiness HTTP $c | liveness HTTP $(lv)"
```
PASS = broker down -> readiness `503 DOWN` + liveness `200 UP`; broker up -> readiness back to `200 UP` on its
own.

**Local k8s:** check the context is `docker-desktop` first; reach each pod with `kubectl port-forward
pod/<pod> <local>:<containerPort>` (ports: enyman/pacman 3003, keysmith/biztree 3002, kcmaster 3006, aukeep
3007 — aukeep has no Service). Drop the broker by scaling its StatefulSet: `kubectl scale statefulset
esquire-infra-amq-activemq --replicas=0` (then `--replicas=1`). The DOWN window is SHORT on k8s — the
StatefulSet recreates the broker in seconds and `failover:` reconnects fast — so an HTTP-only poll can miss
the flip; the reliable evidence is the service's transport-listener log (`kubectl logs <pod> | grep -i
"transport interrupted\|transport resumed"`), which shows each leg going DOWN on the connection drop and back
UP on reconnect. See `results-260623-0059-k8s.md`.

---

## Known gap (by design, superseded by #8)

When the broker drops, only an ACTIVELY-used leg (the kc R&R leg, which has a consumer) flips `DOWN` at once;
an idle transmit-only leg (broadcast / audit) reads `UP` until its next send (lazy publisher connection), and
on Kafka / Redis a leg reads `UNKNOWN` until a send outcome. The active health source that closes this gap on
EVERY transport is the x-rod heartbeat (#8). Capture this faithfully in the results — do not pretend every leg
flips together.

Results are written to `results-<YYMMDD-HHMM>.md`.
