# Health smoke — messaging-bus readiness / liveness validation

Manual validation of the **bus health indicator** (sprint v1.2.9 commit 7). Proves that every service
forwards its bus connection health to `/actuator/health`, that the indicator sits in the **readiness** group
only (a broker outage depools the pod but never restarts it), and that an ActiveMQ leg **recovers on its own**
through the `failover:` transport.

There are two ways to run it: the AUTOMATED `run.sh` (the broker-chaos smoke with assertions, like audit-smoke)
and the MANUAL procedure below (an in-container `/dev/tcp` probe + `docker stop/start`). The unit-level logic (`TransportHealth.worst`,
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

## The services

Actuator listens on a SEPARATE, internal-only management port **8090** (`management.server.port`) in every
service — NOT the published app port, and NOT reachable from the host or the public ingress. Probe it INSIDE
the container/pod with `docker exec` / `kubectl exec` (the service images carry no curl/wget, so the request
goes through the bash `/dev/tcp` builtin); `run.sh` does this for you.

| Service | Buses it reports |
|---|---|
| enyman   | audit-bus, kc-bus, entity-bus |
| pacman   | audit-bus, entity-bus |
| keysmith | audit-bus, kc-bus |
| kcmaster | kc-bus, entity-bus |
| biztree  | entity-bus (+ its own `cacheReadiness`) |
| aukeep   | audit-bus (+ `keepDatasource`, the keep `*_log` DB) |

Endpoints per service (on `:8090`): `/actuator/health/readiness` and `/actuator/health/liveness`.

---

## Run (manual)

Actuator is internal-only (`:8090`), so probe it inside the container — the same `/dev/tcp` request `run.sh`
uses (the images have no curl):

```bash
# raw actuator GET inside a service container: hc <esq-container> <readiness|liveness>
hc(){ docker exec "$1" bash -c 'exec 3<>/dev/tcp/127.0.0.1/8090; printf "GET /actuator/health/'"$2"' HTTP/1.0\r\nConnection: close\r\n\r\n" >&3; cat <&3'; }
```

### 1. Readiness sweep (all 6 services UP)

```bash
for s in enyman pacman keysmith kcmaster biztree aukeep; do
  echo "--- $s"; hc "esq-$s" readiness | tail -1; echo
done
```
PASS = every service `HTTP 200` with `messagingBus.status: UP` and every bus detail `UP`.

### 2. Broker-down / recovery (the readiness-liveness split)

Run against enyman (it carries all three buses incl. two ActiveMQ legs). Poll, do not sleep-wait:

```bash
st(){ hc "esq-enyman" "$1" | head -1; }            # first line = "HTTP/1.1 <code> ..."

docker stop esq-activemq                            # broker down
for i in $(seq 1 25); do st readiness | grep -q ' 200' || break; sleep 1; done
echo "down: readiness [$(st readiness)] | liveness [$(st liveness)]"

docker start esq-activemq                            # broker up
for i in $(seq 1 90); do st readiness | grep -q ' 200' && break; sleep 1; done
echo "recovered after ${i}s: readiness [$(st readiness)] | liveness [$(st liveness)]"
```
PASS = broker down -> readiness `503 DOWN` + liveness `200 UP`; broker up -> readiness back to `200 UP` on its
own.

**Local k8s:** check the context is `docker-desktop` first; reach each pod's actuator the same way with
`kubectl exec pod/<pod> -- bash -c 'exec 3<>/dev/tcp/127.0.0.1/8090; ...'` (`:8090` in every pod). Drop the
broker by scaling its StatefulSet: `kubectl scale statefulset esquire-infra-amq-activemq --replicas=0` (then
`--replicas=1`). The DOWN window is SHORT on k8s — the StatefulSet recreates the broker in seconds and
`failover:` reconnects fast — so an HTTP-only poll can miss the flip; the reliable evidence is the service's
transport-listener log (`kubectl logs <pod> | grep -i "transport interrupted\|transport resumed"`), which
shows each leg going DOWN on the connection drop and back UP on reconnect. See `results-260623-0059-k8s.md`.

---

## Known gap (by design, superseded by #8)

When the broker drops, only an ACTIVELY-used leg (the kc R&R leg, which has a consumer) flips `DOWN` at once;
an idle transmit-only leg (broadcast / audit) reads `UP` until its next send (lazy publisher connection), and
on Kafka / Redis a leg reads `UNKNOWN` until a send outcome. The active health source that closes this gap on
EVERY transport is the x-rod heartbeat (#8). Capture this faithfully in the results — do not pretend every leg
flips together.

Results are written to `results-<YYMMDD-HHMM>.md`.
