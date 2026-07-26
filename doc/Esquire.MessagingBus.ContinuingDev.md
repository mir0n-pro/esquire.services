# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Messaging Bus -- Continuing Development

> **WORKING doc** -- a living backlog, appended as gaps are noted.

Forward-looking work for the Messaging Bus that is deliberately OUT of the current build, parked here so the
shipped decisions stay honest about what they defer. Companion to `doc/Esquire.MessagingBus.md` (the bus design)
and `doc/Esquire.MessagingBus.MessageStructure.md` (the wire envelope, incl. the session TestRequest / HeartBeat
pair).

These are NOT scheduled tasks -- they are the known, accepted gaps of the shipped design. Each entry states what
the current build does, why it is enough for now, and what the fuller form looks like. An entry is promoted into
a sprint plan when it is actually picked up.

---

## 1. Alive protocol -- the consumer-leg gap

**Current build.** The x-rod alive protocol reads health off the PRODUCER (send) leg ONLY -- the consumer
(receive) `legTimestamp` is ignored. A broadcast CLIENT auto-opens a producer leg in the framework (base `XRod`
always transmits -- no `BOTH` role, no per-service config change), so every broadcast rod has a producer leg to
self-heartbeat. Rationale: both legs share one transport node, so a working send proves the transport is up.

**Why this is acceptable.** A consumer leg's `legTimestamp` only advances when SOMEONE ELSE produces, so a quiet
or producer-less bus would read the leg DOWN even though nothing is broken. Reading the producer leg only
sidesteps that "no peer yet vs peer dead" ambiguity for the first cut.

**FIX precedent (why there is no standard to copy).** FIX defines only the TestRequest -> Heartbeat
collaboration: `HeartBtInt` (tag 108) negotiated at Logon (same value both sides), a send-side timer that emits
a Heartbeat after outbound inactivity (any sent message resets it -- our `lastSendAttempt`), and a receive-side
timer that resets on receipt of ANY message from the peer; if nothing arrives within `HeartBtInt` it sends a
TestRequest, and no Heartbeat reply within ~2x `HeartBtInt` -> DISCONNECT. FIX has NO equivalent of our gap, by
construction: a FIX session is POINT-TO-POINT, established by Logon, so there is exactly ONE counterparty that
is OBLIGATED to heartbeat, and both ends are SYMMETRIC (send + receive on one bidirectional link). "No peer
yet" is simply "not logged on" -- a neutral not-in-session state, not a health reading. And FIX judges liveness
from the RECEIVE direction ("have I heard from the mandated peer"), backed by that heartbeat obligation;
sending successfully tells FIX nothing about peer liveness.

That is the sharp point for us: our approach reads the PRODUCER (send) leg -- the OPPOSITE axis from FIX. FIX can
trust the receive side because a mandated counterparty keeps it fed; we cannot -- a broadcast CLIENT has no
single obligated peer, and an R&R SERVER has no client until one "logs on". So we fall back to "can I send"
(local transport-up) as a weaker proxy precisely because FIX's precondition (a mandated heartbeating peer) is
absent on our asymmetric pub/sub legs. There is no FIX recipe to borrow; the gap is genuinely ours. The two
FIX-faithful resolutions both map onto the deferred items below: SYMMETRIC heartbeat both directions (the R&R
SERVER's unsolicited HB on inactivity is exactly this), and treating "no peer yet" as FIX's pre-Logon NEUTRAL
state -- not DOWN -- until a peer appears.

**What it defers.**
- **R&R CLIENT round-trip health.** Today = "I can send a TestRequest" (local transport up), NOT "the SERVER
  answered." So a reachable-but-dead R&R server is not detected yet. The fuller form tracks the
  TestRequest -> HeartBeat round trip (pending-request + reply within `alive-timeout`) so CLIENT health truly
  reflects "server alive" -- which is the correct CLIENT dependency.
- **Per-leg "no peer yet" vs "peer dead".** The real fix for the SERVER consumer leg: hold the consumer-leg
  indicator NEUTRAL until the alive protocol has seen at least one peer (a CLIENT arrives), then treat silence
  as DOWN. Same idea for a broadcast CLIENT once consumer-leg health is brought back in.
- **Broadcast consumer heartbeat chatter.** Because a broadcast CLIENT auto-opens a producer leg, broadcast
  consumers (bizTree / kcMaster) publish heartbeats onto `esquire.entity`; every other consumer receives and
  ignores them (minor at a 10s cadence). When consumer-leg health returns, the producer leg on a pure consumer
  can be dropped.

**A HARD broker failure is NOT detected.** The producer-leg health catches a CLEAN broker shutdown (a graceful
restart -> DOWN in tens of seconds), but NOT a HARD failure -- a crashed pod / node loss / partition that leaves
the client socket HALF-OPEN (no FIN/RST). On a half-open socket a WRITE still succeeds (buffered), and with `jms.useAsyncSend: true` the JMS send returns
immediately while `failover:` BUFFERS sends across the reconnect -- so the heartbeat "send" succeeds even though
the broker is gone, `producerTs` stays fresh, and the leg reads UP. This is the same half-open-socket family as
the keep-datasource `isValid` hang, and it is the exact producer-leg weakness: "can I send" answers YES (the
bytes left the process) without proving the broker took them. The ROUND-TRIP health (above) is the real fix -- a
CLIENT that does not get its `HeartBeat` reply reads DOWN regardless of send semantics. Lower urgency, though: a
hard broker loss is rare, and with a SHARED broker depooling would not help (no healthier pod to route to) -- so
this is health ACCURACY on a rare event, not a functional failure. (A transport-config alternative --
`jms.useAsyncSend: false` or a sync-send/transport timeout so a disconnected send throws -- would trade send
throughput for detection; not taken.)

---

## 2. Session-message exposure toggle (`x-rod.admin.expose`)

**Current build.** Session (TestRequest / HeartBeat) messages are handled INTERNALLY by the x-rod and never
reach the application worker. There is no exposure path.

**Fuller form.** A per-leg toggle (`x-rod.admin.expose`, default false) ALSO surfaces session messages to the
x-rod listener -- for an admin / monitor tool -- the way a FIX engine lets session messages reach the app only
when explicitly hooked. Not in today's build.

---

## 3. Alive-protocol tuning

**Current build.** Knobs live on the x-rod and the operator keeps them in sync across all x-rods on a slot:
`heartbeat-interval` (default 10s) and `alive-timeout` (default 3 x heartbeat-interval = 30s).

**Open.**
- **Send-failure handling** is CONFIGURABLE: a send exception may flip the leg DOWN IMMEDIATELY (fast path) or
  be left to `alive-timeout` (pure timeout). The best DEFAULT is to be learned from live behavior.
- **Per-transport send semantics.** A transport vendor may itself act differently on a send (the message gets
  consumed, never consumed, or it is configurable). This must be learned per transport API employed
  (ActiveMQ / Kafka / Redis) and may change the right cadence / timeout per transport.
- **Defaults** (`heartbeat-interval`, `alive-timeout`) may want per-transport or per-slot tuning once there is
  real operational data.

---

## 4. Multi-worker dispatch on one rod -- `addWorker` over a base subscription

**Current build.** A receive leg carries exactly ONE worker. `setWorker(String subscription, Consumer<RodEvent>
worker)` opens the consumer with `subscription` as its selector and binds that single worker. On ActiveMQ the
selector IS the broker's server-side subscription; a transport with no server-side selector (Kafka / Redis are
fan-out-only) has no subscription narrowing at all yet.

**Fuller form.** Let one rod fan a single base subscription out to MANY workers, each with its own finer filter,
processed in order on one thread.

- `setWorker(subscription, worker)` establishes the **base subscription** -- the connection-level subscription
  that decides which messages the rod receives at all. On a transport WITH a server-side selector (ActiveMQ) the
  base subscription is the broker selector. On a transport WITHOUT one, the framework filters incoming messages
  against the base subscription in code -- a limited, framework-interpreted subscription language (only a subset
  of the selector syntax need be supported).
- `addWorker(subscription, worker)` adds another worker whose `subscription` is an ADDITIONAL filter applied ON
  TOP of the base subscription: that worker runs only for messages that pass BOTH the base subscription and its
  own filter.
- All workers on a rod run in ONE thread, in the SEQUENCE they were added: each received message is offered to
  every matching worker's `action()` in registration order. No concurrency between a rod's workers; ordering is
  deterministic.
- A fluent chain expresses the set: `setWorker(base, w0).addWorker(f1, w1).addWorker(f2, w2)...` (or a similar
  builder shape).
- `worker` on `setWorker` is OPTIONAL (may be null): `setWorker(subscription, null)` establishes ONLY the base
  subscription, expecting `addWorker(...)` calls to follow. (A base subscription with no workers receives but
  dispatches to nothing.)

**Why it is deferred.** Today each consuming concern gets its own rod/ref with one worker, which is enough for
the current consumers. Multi-worker dispatch matters when several concerns share ONE physical
subscription/connection and want to split that stream by finer filters without each opening its own consumer --
and on a non-selector transport it depends on the client-side subscription language to be meaningful.

---

## 5. Full set of messaging-path resilience patterns (beyond `send-retry`)

The bus ships ONLY the producer **`send-retry`** sublayer as its messaging-path resilience pattern
(design in `doc/Esquire.MessagingBus.md`). The producer **extension point** is the `ISessionSublayer` stack --
event-driven hooks the feed (tx) worker drives at each send outcome, built by `SessionSublayerFactory` beside
the `AliveSession` alive protocol (never mixed with it). The remaining patterns are added LATER, each as an
**additional sublayer** on that stack:

- **Circuit breaker** -- needs an "on open" policy the bus does not have today (no fallback destination): DLQ /
  drop / local buffer must be decided first.
- **Retry / backoff variants** beyond `send-retry`.
- **Per-message timeout** -- async has no request/response deadline today.
- **Per-destination bulkhead** -- today only `receiver-pool.size` bounds concurrency; no per-destination isolation.

Resilience4j does not apply here -- it is synchronous-only; the bus carries its own, transport-agnostic
resilience in the producer leg (see `doc/Esquire.MessagingBus.md` and `doc/Esquire.HighAvailability.md`).

## 6. Async protocol with continuing processing (FIX-like)

A request/response (R&R) client sends a request **with OR without a subscription**:

- **WITH subscription** -- the client receives **processing-status updates at each status change** (a progress
  stream as the request advances).
- **WITHOUT subscription** -- the client receives an **ACK**, then **asks (polls) for the completion status**.

Modeled on the FIX protocol's order-status flow. Builds on the R&R rod (`XRodRR`) + the existing request/reply
rod-id correlation -- a defined continuing-processing protocol layered on top of it.

---

## 7. Broadcast topic delivery across a full broker restart -- the resubscribe race

**Current build.** The entity-change broadcast rides a NON-DURABLE topic: the broker delivers each publish only
to the consumers subscribed AT THAT INSTANT. On a FULL broker restart -- a k8s broker-pod replacement
(`scale 0->1`) or a cloud failover to a fresh broker -- the producer (enyMan, with `send-retry` holding a change)
and the consumer (bizTree) each reconnect INDEPENDENTLY through `failover:`. Send-retry re-sends the held change
on producer reconnect; if that re-send lands BEFORE bizTree has resubscribed, the topic has no subscriber for it
and the broker drops it -- bizTree misses that one broadcast. The **night-watch anti-entropy sweep** is the
backstop: it reloads bizTree from the DB and heals the drift (the `MessageLossSimulation` scenario).

**Why the current build is acceptable.** No data is lost -- the change is committed in the DB and the night-watch
reconciles the cache; only a WINDOW of cache staleness remains, until the next sweep. It bites ONLY on a full
broker restart (rare), not a brief blip -- a `docker stop/start` (same broker process, fast reconnect) does not
hit it. `send-retry` is producer-side: its contract is "the send LANDS on the broker", which it meets; it cannot
make a non-durable-topic consumer be subscribed at the re-send instant. And send-retry already IMPROVES the prior
behavior -- the change is held + re-sent instead of dropped outright during the outage.

**Fuller form.** Close the CONSUMER side so a full broker restart needs no sweep to reconcile: a DURABLE topic
subscription (the broker retains messages for a named subscriber while it is reconnecting), so the re-sent
broadcast is delivered once bizTree returns. Pairs with the consumer-leg / round-trip health refinements in item
1 and the drop-visibility counters. Transport-dependent: durable subscriptions are an ActiveMQ concept; Kafka
(offset retention) and Redis Streams (the log IS retained) survive a restart differently -- part of the per-driver
durability work.

## 8. Virtual-threads throughput budget on the cloud (correctness done; perf A/B open)

The pool thread model is a first-class per-leg setting -- `receiver-pool.mode` / `publisher-pool.mode` =
`platform` | `virtual` | `virtual-per-task`, backed by the common `WorkerPool` (`pro.mir0n.utils.concurrent`); the
request path is wired via `spring.threads.virtual.enabled` (`ESQ_VIRTUAL_THREADS`). The stack runs on the **JDK 25
LTS** runtime (compiled `--release 24`), so JEP 491 removes the old `synchronized`-monitor pinning that our x-rod
engine + send-retry lock + JDBC would have hit. **CORRECTNESS is validated:** `platform` and `virtual` behave
identically -- a service boots healthy and processes events the same either way, with no pinning stall. Default
stays `platform`.

**What is still open -- the throughput / budget A/B, and it is cloud-only.** The matrix proved correctness, not
performance: on a single saturated host the timings are noise. The finding so far (see
`Esquire.HighAvailability.md` §5.5) is that virtual threads buy **nothing** for Esquire's messaging pools, because
they are small FIXED pools whose real ceiling is the keep's DB connection pool (`receiver-pool.size <= keep DB
pool`), not thread count -- and VT only pays off when a pod would otherwise exhaust its per-pod OS file-handle /
thread budget holding MANY concurrently-blocked waits. The open task is to confirm that no-benefit conclusion
under real load on the cloud (more cores, no CPU cap), and to check the one candidate that could differ -- a
blocking-I/O consumer like kcMaster -> KeyCloak, or the Tomcat request path under high concurrent-slow-request
load. Compare a thread dump / latency / memory against the platform baseline; the result decides whether VT is
ever worth turning on for a specific leg, or stays an unused-by-default lever.

---

## 9. R&R reply-timeout / reply-tracking resilience

`XRodRR` fires a request and waits for a reply with **no per-request timeout, no pending-request map, and no
replier-down detection**; and a failed R&R apply under `AUTO_ACKNOWLEDGE` is acked-and-lost with no redelivery.
Today this is **bounded, not an active hang**: a missing reply surfaces as the caller's outer request timing out
(the gateway / BFF timeout ladder), and R&R runs single-instance. The improvement -- a real R&R-level reply
timeout + pending-request tracking + replier-down detection, and not losing a failed apply -- is
continuing-development: its full value is the multi-instance reply-routing case (kcMaster multi-instance), and the
outer timeout already caps the single-instance wait.

**Companion work (already posed):** the **async long-running-command protocol** -- item 6 above (FIX-like: a
request WITH a subscription gets processing-status updates at each change; WITHOUT one gets an ACK, then polls for
completion). A long-running R&R command's status / progress / completion-and-timeout semantics belong with that
protocol -- so R&R reply-timeout resilience (this item) and the async-command protocol (item 6) are the SAME body
of R&R continuing-dev work and should be designed together.

(The rod-id **uniqueness** direction was REJECTED -- rod-id is unique by default via StatefulSet ordinals, a
manual override is a deliberate expert choice; see `doc/Esquire.Q&A.md`.)

---

## 10. Replay / seek API for retained-log transports (Kafka / Redis Streams)

The bus has no API to REPLAY a leg from an earlier point: `IXRod` exposes send + set-worker, but no
`replayFrom(offset)` / `seekToTimestamp(ts)`. On a **retained-log** transport this is a real, cheap capability the
wire already supports -- Kafka keeps offsets, Redis Streams IS an append-only log (`XRANGE` / `XREVRANGE` from any
id) -- so a consumer could re-read from a chosen point after a gap or for a rebuild. On ActiveMQ there is nothing
to seek (a non-durable topic retains nothing; a queue is consume-once), so replay is inherently a **per-driver**
capability, not a universal one. Belongs with the per-driver durability / selector work and pairs with the
durable-topic direction in item 7. When added, `IXRod` gains an OPTIONAL seek/replay method that a non-retaining
driver rejects (the same shape as `tp-redis`'s `supportsConsume() == false`).

---

## 11. Javadoc quality gate for the bus public API

The generation infrastructure exists -- the `-Pjavadoc` profile + `make-javadoc.bat` publish per-module API docs
under `doc/java-doc/`, with doclint OFF so it never blocks the build. What is NOT yet enforced is the QUALITY of
that Javadoc on the bus's public surface: the extension SPI an adopter codes against -- `IXRod`,
`ISessionSublayer`, the transport SPI, the catalog / `XRodParams` config keys, `RodEvent` / codec. For the PUBLIC
API, complete and meaningful Javadoc is REQUIRED (every public / extendable type + method: purpose, params,
return, contract, threading / lifecycle notes -- not a stub echo of the signature). Candidate mechanism: re-enable
doclint SCOPED to the exported packages only (kept separate from the build-wide OFF setting) plus a doc-coverage
check that fails if a public type / member is undocumented. Stays within the "mechanism-only, never
where-applied" rule for framework docs.

---

## 12. Timestamp format -- return `SendingTime` to the FIX UTCTimestamp format

The envelope is FIX-INSPIRED, and `SendingTime` (tag 52) is a FIX **UTCTimestamp** field -- but it is emitted as an
ISO-8601 / RFC 3339 string (`Instant.now().toString()`), NOT the FIX `YYYYMMDD-HH:MM:SS[.sss]` format. The
intent was the FIX format; the ISO-8601 form is the JSON-native shortcut taken during the build (every JSON
library and the browser `Date` parse it out of the box, it sorts lexicographically, and the zone is
unambiguous). The gap is FIX conformance: the official FIX JSON encoding keeps the FIX timestamp STRING, so a
strict external FIX consumer would not accept our ISO-8601 value.

**Fuller form.** A strict-FIX-JSON compatibility mode that emits `SendingTime` (and any UTCTimestamp field) in the
FIX `YYYYMMDD-HH:MM:SS.sss` format -- turned on when the wire faces a real FIX consumer. Eventual alignment, low
priority: the bus is internal, so the JSON-native form is fine for now. (`ActionTime` is epoch-milliseconds by
design -- a machine timestamp, not a FIX UTCTimestamp field -- and stays numeric.)

---

## Related parked items

- Transport SPI is ActiveMQ-shaped; selector / `key()` / durability differ per driver -- the per-driver selector
  design direction.
- Loss-visibility drop counters -- make a dropped broadcast observable rather than silent.
- DB-pool durability (pgjdbc `socketTimeout` / `tcpKeepAlive`; the `isValid()`-on-a-half-open-socket gap that
  makes an in-process keep rod's datasource health blind on k8s) -- belongs to the item-8 k8s durability gate.
