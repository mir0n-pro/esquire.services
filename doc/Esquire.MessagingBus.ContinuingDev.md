<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>

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
FIX-faithful resolutions both map onto the deferred items below: SYMMETRIC heartbeat both directions, and
treating "no peer yet" as FIX's pre-Logon NEUTRAL state -- not DOWN -- until a peer appears.

One constraint the symmetric form has to respect: an R&R SERVER cannot simply emit an unsolicited HeartBeat on
its response node. Routing there is ECHOED from the request, and every CLIENT selects on its own rod-id, so a
beat with nothing to echo matches no selector, is never consumed and never expires. A symmetric heartbeat needs
an addressed form -- per known peer, or a node whose consumers do not select.

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

**The first caller waiting on it: Mesnie's entity leg.** A composed process has two concerns on the one entity
rod -- enyMan wants peer CREATEs for the move-queue reconcile, and the identity gateway wants moves for the path
park. One worker and one selector cannot serve both, so the build widens the base subscription to
`EventType IN ('I','X')` and enyMan **relays** the moves to the gateway, which decides what one is worth holding.
That relay is a stand-in for this item, and it costs something measurable: an enyMan pod now receives peer
X broadcasts it drops on the floor, one per moved entity per pod.

**Half of it has a second exit, needing no framework work at all.** The relay carries two kinds of
broadcast: a PEER copy's, which only the bus can deliver, and this process's OWN, which is fed back in at
publish time. The own half exists solely because the request arm applies-or-skips and never holds a path;
move the park decision into the request arm and that half is redundant -- the request already arrives
in-process carrying the path. Only the peer half genuinely needs this item.

When `addWorker` lands, Mesnie distributes by subscription instead: enyMan keeps `EventType = 'I'`, the gateway
adds `EventType = 'X'`, each gets only what it asked for, and the relay comes out -- `relayTo` on the entity
adapter, `postMessage` on the gateway seam, and the widened base subscription with it. This is the whole reason
to build the item, and it is worth doing properly rather than in pieces: the fuller form above (base subscription,
per-worker filters, in-order dispatch on one thread) including the framework-side subscription language for
transports with no server-side selector.

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

**The same decision at the other end of the leg: what a FEED does when it cannot take the message.** The "on
open" policy above is about the send; the transmit feed has two exits of its own, and they are the identical
question:

- **full past `putAwaitMs`** (default 10s) -- the item is dropped. The audit legs run `feed-capacity: 4096`
  with no `feed-await-ms`, so this is the exit a broker outage arrives at once the feed fills.
- **stopped** -- `MessagingBus.close()` runs on `ContextClosedEvent`, BEFORE bean destruction, so the feed can
  be closed while requests are still committing. `AuditBusBridge` transmits AFTER the business transaction
  commits, so a drop here means the row exists and its audit record does not.

Dropping is not itself the problem -- a fan-out has no acknowledgement by nature
(`Esquire.Messaging.md`, "Delivery -- what the bus holds, and what it does not"), and the alternatives here are
unbounded memory or blocking a request that has already committed. What the policy has to say is WHICH exit
does what, uniformly, and whether "stopped at shutdown" is a drain rather than a drop.

**Why the audit leg is the one still on the shedding default -- and why that is right (2026-08-24, round 3 M2).**
Every other producer leg sets `feed-await-ms: 0` with a comment calling the 10-second default "silent,
unrecoverable loss": the KC leg on enyMan and keySmith, the entity broadcast on enyMan, mesnie and pacMan.
The audit leg does not, and copying them there would be wrong -- `AuditBusBridge` flushes on `afterCommit`, so
holding the producer would stall a request thread and its database connection AFTER the business transaction
committed. Blocking commerce to record history is the greater harm, and that channel already accepts loss by
configuration (`transport.params.persistent` absent = NON_PERSISTENT).

So the asymmetry is deliberate, and what is missing is not the setting but the COUNT. It is the one
destination whose loss cannot be reconstructed, and the only producer leg whose loss nothing records: the drop
reaches a develop-tier warn alone, and `messaging.retry.dropped.total` -- what the shipped `EsqBusRetryDropped`
alert watches -- cannot fire for it, because that leg has no send-retry. That is this item's counter, and it is
the sharpest case for building it.
**Independent of the policy, and true whichever way it lands: neither exit is visible.** Full-past-timeout logs
to the DEVELOP tier -- a file on docker, off on k8s under the o11y arms -- and the stopped exit logs nothing at
all, while the SEND half of the same path carries a meter, a main-tier log and a shipped alert. The rig already
has an `IErrorListener` seam, so `mir0n-utils` need not learn about meters: the rig reports the drop, the
messaging layer logs it on the main tier and counts `messaging.feed.dropped.total{reason=full|stopped}` --
matching the send side so the existing alert story extends rather than being reinvented. The two reasons want
separate tags: "we are overloaded" and "shutdown ate it" are different operational facts.

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
`Esquire.HighAvailability.md` section 5.5) is that virtual threads buy **nothing** for Esquire's messaging pools, because
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

**The composed shape has no rod in the loop.** Since Mesnie, a caller can hold an `IIdentityGateway` that
serves the request in the SAME process: the answer comes back through the gateway's result handler and never
crosses a bus leg. Pending-request tracking built on `XRodRR` alone therefore covers the bus case and leaves
that one open. The tracking belongs where the caller waits, so both implementations of the seam carry it.

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

## 13. More than one channel on an x-rod leg -- marrying "fast but dummy" with "smart but slow"

**The two requirements are in tension, and today the bus resolves it by choosing.** Async transmission is
asked for two incompatible things: (1) move the data as fast as possible, and (2) guarantee it arrives. Every
leg today is ONE channel with ONE provider, so the provider choice decides where that leg sits between them.
On ActiveMQ the answer is "fast" everywhere -- every leg is `persistent: false` and the broker itself runs
`persistent="false"`, in-memory by design. The guarantee is then rebuilt ABOVE the transport: night-watch
reconciliation, the send-retry / keep-alive session sublayer, idempotent apply with the `*_log` dedup key,
and the per-entity change number.

That works for the entity cache, which has a reconciliation source. **It does not work for audit:** `audit-c`
is non-persistent, so an audit event can be lost, and there is nothing to reconcile against -- the audit log
IS the record.

**The idea, in one sentence: an x-rod leg may define MORE THAN ONE channel.** Today a leg defines exactly
one transport, so it has exactly one set of properties. Let it define several, freely on **different media
and different vendors**, and the leg no longer has to choose.

The primary use is two -- one fast, one durable: for example Aeron (or a native / multicast transport) for
the fast channel, and Kafka disk-first with a deep backlog for the durable one. More than two is a
straightforward extension (see *The ladder* below).

- **Both are published at once. There is NO retransmit** -- the durable channel is a complete parallel
  stream, not a recovery mechanism.
- **The receive leg relays each logical message to the client exactly ONCE**, and where required only the
  **freshest** one, by `SendingTime` + change number.

**It is invisible to the bus client.** The application keeps calling `transmit` / `setWorker` on `IXRod`;
the fan-out on send and the arbitration on receive live inside the x-rod, between `AXRod`,
`RodTransportAdapter` and `ITransportProvider`. `EntityBusAdapter`, `AuditBusBridge` and the rest are
untouched. Only the topology gains a second channel.

**Nothing is removed and nothing is replaced -- this ADDS an option.** Every existing leg keeps working
exactly as it does today; a single channel is simply the degenerate case of the model. What gains a choice
is the **topology**, and the person who gains it is the **network engineer** designing how the messaging-bus
networks are laid out -- not the developer, who sees no difference at all.

**The goal in one line: marry "fast but dummy" with "smart but slow".**

|               | fast but dummy                                        | smart but slow                             |
|---------------|-------------------------------------------------------|--------------------------------------------|
| what it is    | no persistence, no acknowledgements, no broker smarts | durable, acknowledged, ordered, replayable |
| what it gives | lowest latency, cheap, brokerless                     | the delivery guarantee                     |
| what it costs | it can lose messages                                  | latency, disk, machinery to operate        |

Neither alone answers both requirements. Published together and arbitrated at the receive leg, they do --
and that marriage is the whole of this item.

**Why it wins -- and it is NOT about cost.** Cost and network traffic come out about the same; they depend
on the media and vendors chosen, and neither is the argument. Nor is this a retry mechanism: each channel
**sends once**, and there is nothing to re-send.

**The win is that configuration stops being a compromise.** With one channel, every setting is a
negotiation between the two goals -- acknowledgement mode, replication factor, flush / fsync policy,
batching and linger, prefetch, persistent or not. Each knob has to sit somewhere in the middle, so the leg
ends up mediocre at speed *and* mediocre at durability. With two channels each configuration is written for
**one** goal and can be taken to its extreme:

- **the fast channel** -- no persistence, no acknowledgements, no replication, minimal buffering: every
  knob at the speed end, because losing a message is not this channel's problem;
- **the durable channel** -- acknowledge everything, replicate fully, flush to disk, tolerate a deep
  backlog: every knob at the durability end, because arriving late is not this channel's problem.

That is how both extremes are reached at once instead of one compromise being struck. It also explains why
the totals stay level: freed from any latency requirement, the durable channel can batch aggressively,
which *lowers* its per-message cost and offsets much of what the fast channel adds.

### The ladder -- two channels is the minimum, not the limit

Once each channel is configured to an extreme, a more sophisticated setup can define a **middle** channel as
well: an ordered ladder along the same axis rather than just its two ends. For example --

| tier    | example                      | role                                            |
|---------|------------------------------|-------------------------------------------------|
| fast    | Aeron / native UDP           | normal path; lowest latency, may lose           |
| middle  | Redis Streams, memory-backed | catches a fast-channel miss in milliseconds     |
| durable | heavy-duty Kafka             | the record; takes the scene if the middle fails |

**The cascade is emergent, not programmed.** All channels publish at once and nothing retransmits, so no
failover logic is needed -- no health check, no "is Redis up?", no switch. The fastest *working* channel
simply arrives first, and if it did not deliver, the next one does. The tiering falls out of the channels'
latency ordering. This is a direct dividend of the publish-at-once / no-retransmit decision.

Three things follow:

- **Graceful degradation instead of a cliff.** With only two channels, a loss on the fast one waits for the
  durable one -- which may be deliberately backlogged. A middle tier catches most losses in milliseconds
  rather than minutes.
- **Independent failure domains are what make it real.** The tiers help because they are different media
  and different vendors. Two channels on one broker would share a failure and be close to useless -- the
  diversity IS the redundancy.
- **The arbitration state does not grow with the number of channels.** Two or five, the receive leg still
  keeps only the last relayed change number per entity. The design scales in channel count for free.

**Cost note:** "about the same" holds while the added tiers are the cheap ones -- a memory-backed Redis
stream, a UDP channel. A second *heavy* channel would not be a wash. The ladder is opt-in and defined by
the network engineer, so that is a choice made with open eyes.

> **This is a limit of the SINGLE-CHANNEL model, not of any provider.** ActiveMQ is simply the first
> transport the bus implemented; Kafka and Redis followed, and the provider set is open by design. With one
> channel per leg, whichever provider is chosen has to sit at ONE point on the axis and be asked to serve
> both requirements. Running ActiveMQ non-persistent is the **correct** setting for a fast leg -- the KC
> leg's own comment explains why, since a persistent JMS send is synchronous and "costs a round-trip per
> send and buys nothing" against an in-memory broker. It simply is not also the durable channel.
> Dual-channel lets each provider be used where it is strong, instead of asking one to be everything.

**What this design does NOT need** (and this is its main advantage over an A/B-feed-plus-retransmit scheme):

- **No gap detection.** Nothing has to notice a missing message, because a message lost on the fast path
  simply arrives on the durable path. No per-stream sequence number is required.
- **No replay / seek.** Nothing seeks backwards, so item 10 stays a separate item rather than a dependency.
- **No large dedup memory.** Arbitration by `SendingTime` + change number needs only **the last relayed
  change number per entity** -- bounded by entity count, not by message rate times channel skew. That
  matters because a deliberately backlogged durable channel can run minutes behind.

**What it needs:**

- **Two transports per slot in the topology.** Today `transport:` is singular -- one provider and endpoint,
  with several `nodes` underneath (the R&R leg already uses request/response nodes). It becomes a LIST of
  channels, each with its own provider and endpoint. Backward compatible: one channel is today's shape.
- **`SendingTime` stamped ONCE by the publisher and carried on both copies** -- never stamped per channel,
  or the two vendors' clocks and queueing become part of the comparison.
- **Freshness filtering per leg, not global.** Freshest-only is right for the entity broadcast. It is WRONG
  for audit, which wants every change recorded -- an "older" audit event is a record to keep, not a stale
  duplicate. Audit gets relay-once with no freshness filter.
- **A contract restatement.** `IRodEventRepo` says de-duplication across redelivery is "the impl's concern".
  With the rod arbitrating across channels, that boundary moves: the rod owns cross-channel identity, the
  repo still owns business idempotency. Both must not assume the other is doing it.

**What it costs, honestly:** two transports to configure, monitor and understand, two sets of health checks
and meters, and a new dependency if the fast side is Aeron. Operational surface, not throughput.

**What it settles elsewhere:** item 7 (broadcast delivery across a full broker restart) stops being a gap --
what the topic loses, the durable channel delivers. And `ApplMsgID` (FIX 1181) finally has a purpose: it is
already a stable identity across resends that no consumer uses (fresh-mind audit finding M3), and
cross-channel identity is exactly what it was for.

**Open point -- shared state with the receiver-side guard.** The per-entity "last relayed change number" this
item needs is the SAME state the entity-broadcast guard already keeps: two numbers per node, entity and path,
held in the bizTree cache (`TREE_ENTITY_CHANGE_NO` / `TREE_PATH_CHANGE_NO`, see `DatabaseDictionary.md`).
One mechanism, two uses -- which argues for
the bus owning that state rather than it living in a consumer's cache. Settle that before this item is built,
because moving it afterwards means touching the guard as well.

**This item REMOVES a premise other things rest on.** Publishing the same change on two channels makes a
duplicate arrival BY DESIGN, on every leg -- including the legs that are plain queues today and are
unguarded precisely because a queue has no by-design duplicate. `Esquire.ContinuingDev.md` CD-13 (the
identity-claim freshness guard) names this item as the thing that would make it required. Re-open CD-13
before this one lands, not after.

---

## 14. `ApplMsgID` receive-side guard -- the primary dedup we publish but never apply

**Today the id is produced and never consumed.** `ApplMsgID` (FIX 1181) is stamped ONCE on the send path
(`AXRod.sendOut`), stays STABLE across every resend of a held event (`SendRetrySublayer` keys its hold map by
it), rides the wire through `RodEventCodec`, and the ActiveMQ provider keeps it stable rather than minting a
new one per physical send. All of that is the producer half of a dedup, done properly.

Nothing compares it. There is no seen-set anywhere in the framework, and the id is not mapped onto a
broker-native dedup facility either -- it rides as an ordinary message property, not as `JMSMessageID` and
not as a Kafka record key. The Javadoc is exact about the state of it: stamped *"so a consumer CAN dedup"*.
It is an affordance we publish, not a check we perform.

**Say what is actually enforced today.** The only dedup the framework applies is on the (sub)entity CHANGE
NUMBER, and it exists in two different shapes:

- **equality** -- the audit log write: 8 unique indexes per dialect on `(row, change number)` plus
  `ON CONFLICT DO NOTHING` (Postgres) / `MERGE ... ON` (Oracle). Answers *"do I already hold exactly this
  record?"*. An OLDER record is a different key, so it is still written -- correct, because the audit log is
  history and a late record is a true record.
- **ordering** (`old < new`) -- the two receivers that keep their own copy of state: bizTree
  (`MessageHandlerHub.dispatch`, against the H2 cache) and kcMaster (`KcIdentityService.updateEntityPath`,
  against the KeyCloak user attribute). Answers *"is this newer than what I hold?"*.

So describing `ApplMsgID` as "the primary dedup" states its PURPOSE, not its behaviour. Worth keeping those
apart in the docs until this item is taken up.

**What a guard would add that the change number cannot.** The change number is ENTITY-shaped: it needs a row
behind the event. That covers entity broadcasts and audit events, and nothing else. `ApplMsgID` is
MESSAGE-shaped -- it identifies the message itself, so it works for traffic with no row behind it at all:
the R&R request/response legs, and any future message type. It is also the only identity that survives a
message whose body is empty. The two are complements, not alternatives, and a message-id guard would NOT
make the change-number guard redundant: one says "I have seen this MESSAGE", the other says "I already hold
this STATE or something newer".

**Where it would sit.** On the receive leg, in the generic engine (`AXRod`) ahead of the worker, so every rod
and every consumer inherits it rather than each service growing its own. Session messages are excluded --
they are heartbeats, and a repeated heartbeat is not a duplicate to suppress.

**What it costs, honestly.** A bounded seen-set per rod, which is the whole difficulty: the set has to be
kept small enough to hold in memory and long enough to be useful, so it needs a size cap and a time window,
and both are guesses until measured. Per-instance, not shared -- a topic delivers a copy to every instance,
so each instance deduping its own deliveries is the right shape; a shared set would need a store and would
buy nothing. And it changes nothing for a queue leg, where only one consumer receives the message anyway.

**What it does NOT do.** It does not give ordering -- two different messages carrying two different states
both pass a message-id check, and the newer-wins question stays with the change number. It does not remove
the change-number guard, the dedup indexes, or the audit path's equality dedup. It closes the gap between
what the envelope advertises and what the framework enforces.

**Related.** Item 13 needs exactly this identity for cross-channel arbitration (the same message arriving on
two channels IS one message, and `ApplMsgID` is what makes them one). Whichever is taken up first should own
the identity, and the other should use it rather than inventing a second one.

---

## 15. Driver-level send confirmation -- a provider whose failure the bus can see

**Today the bus learns that a send failed only if the provider's `dispatch` throws.** `AXRod.sendOut` takes the
success branch when it returns, and everything downstream follows from that one signal: the send-retry hold is
cleared, the alive session advances, the audit writes its TX line, `messaging_sent_total` counts it, and
`messaging_retry_dropped_total` stays at zero -- so the shipped alert cannot fire either.

The ActiveMQ provider answers that contract: its send is synchronous, so a broker that is not there throws and
the bus holds the message. **The Kafka provider does not.** `tp-kafka` returns a publisher without overriding
`encode` or `dispatch`, so the default routes into an `accept` whose `kafka.send(...).whenComplete(...)` is
asynchronous and whose `catch` logs and returns. `dispatch` cannot throw, so an unreachable broker or a
serialization failure loses the event AND is reported as a success. On `audit-ck` that means the msg-audit
records TX for a change the topic never received.

**What this is.** Not a bug in the bus and not a bug in Kafka: a **driver that does not implement the contract
the seam defines**. The topology treats `audit-ck` and `audit-dk` as first-class, so the gap is reachable by
configuration alone.

**Two ways to close it, and they are not equivalent:**

- **Make the provider honour the existing contract** -- have `tp-kafka` override `dispatch` and wait for the
  broker's acknowledgement before returning, so a failed send throws the way the synchronous providers do. The
  bus is unchanged, and send-retry starts working on Kafka legs. It costs the latency of an ack per send, which
  is what the synchronous providers already pay.
- **Widen the contract itself** -- let a provider report a send asynchronously, so the bus can hold a message
  on a completion that arrives later. That suits a log-shaped transport far better, and it is the same shape
  item 13 needs for a fast channel whose confirmation trails the send.

**Confirm the gap by:** pointing a service at `audit-ck`, stopping kafka, posting a change, restarting, and
reading the topic -- absent, while the msg-audit says TX. The same run on `audit-c` produces TX-ERR and a hold.

**Until it is closed:** a Kafka leg carries no send guarantee, and the `audit-c` / `audit-b` legs are the ones
that do. That is worth knowing when choosing a bus for a sink that must not lose events.

### What the publisher's catch actually guards

`enyMan.publishEntityEvent` wraps its publish in a catch that logs and returns, which reads as a swallowed
send. It is not: `AXRod.transmit` only does `feed.put(ev)`, and the feed/tx worker is the ONLY sender. A broker
failure therefore never reaches that catch -- it happens later, on another thread, where `onSendError` writes
the TX-ERR msg-audit, bumps `messaging.error.total{leg=send}`, and hands the event to the sublayers, so
send-retry holds it and re-dispatches. On every deployed shape that retry is ARMED
(`ENTITY_BROADCAST_SEND_RETRY=true`, backoff `1,2,5,5`, unlimited attempts).

What the caller's catch does guard is the HAND-OFF: a rod with no transmit leg, or a feed that will not take
the event. That second case is the one with no answer yet, and it belongs with the feed-exit question in
**item 5**, not here.

**Raised by:** fresh-mind audit N7 (2026-08-24), which read the catch as the loss point and reported "no
outbox and no retry". The retry exists and is armed; the durability question is item 13.

---
## 16. The session layer under a held send -- the heartbeat gate and the shared sweeper

**Send-retry and keep-alive are both the session sublayer, and under a broker outage they work against each
other.** Two assumptions do not meet:

- `AXRod.send` calls `beforeSend(ev)` ONCE, outside the re-dispatch loop in `sendOut`.
- `AliveSession.tick()` reasons from the opposite premise -- its own comment says *"beforeSend() resets
  lastSendAttempt on EVERY send -- including every send-retry RE-DISPATCH"*.

So while `SendRetrySublayer.onSendError` holds a message in block mode, `lastSendAttempt` is frozen, the
heartbeat gate opens every `heartbeat-interval`, and `tick()` does a BLOCKING `feed.put(ka)` onto a feed that
is not draining. With `feed-await-ms: 0` -- what enyMan sets on both legs -- that put waits with no deadline.

**What turns a stuck rod into a stuck service.** `MessagingBus` runs ONE `messaging-idle` thread for the whole
service and sweeps every rod in a plain loop. A parked put on one rod stops `idle()` being called on ALL of
them: no heartbeats anywhere, and `SendRetrySublayer.tick()` -- documented as *the primary release* -- never
runs, leaving only the 10s safety fallback. A `1,2,5,5` backoff degrades to 10s per attempt, service-wide,
in exactly the outage the resilience work exists for.

**What the session layer needs, in the order the questions arise:**

- **The gate should reason from something it owns.** Either `beforeSend` runs on every dispatch including a
  re-dispatch, or `AliveSession` keeps its own notion of when the leg last tried -- but the comment and the
  code must be describing the same thing.
- **A heartbeat must not be able to park the sweeper.** A keep-alive is the least important message on the
  leg; it should offer with a deadline and give up, never wait forever on a feed that is blocked precisely
  because the transport is down.
- **One thread for every rod is the multiplier.** Even with the two above fixed, one slow rod delays every
  other rod's idle work. Whether the sweep gets a thread per rod, a deadline per rod, or stays as it is, is
  the design question underneath.

**Confirm the gap by:** with `KC_SEND_RETRY=true`, stop activemq and thread-dump enyMan -- expect
`messaging-idle` in `BoundedQueueRig.put -> Condition.await` -- and time the gaps between the "holding" lines
in the msg log: 10s rather than the configured ladder.

**Related.** Item 5 is the wider resilience-pattern set this belongs beside; the send-retry sublayer itself is
the v1.2.10 work, and this is the case its own release path does not survive.

---

## 17. Receiver side -- reporting an event the consumer could not apply

**Not a durability item.** A network failure is not handled at the application level (mir0n, 2026-08-24):
either the channel guarantees delivery or missed messages are accepted. The compromise between speed and
durability is planned as **item 13**, and it is a channel decision -- nothing a consumer should be given
retry, park or DLQ machinery for.

**What is left for the bus is the REPORTING, and it is thinner on the receive side than on the send side.**
Where loss is accepted the counter is the only thing separating "nothing happened" from "everything was
dropped", so it has to carry:

- **the failed apply logs on the DEVELOP tier only** (`AXRod` receive worker), so under the k8s o11y arms it
  leaves no main-tier line -- while the send half has a meter, a main-tier log and a shipped alert;
- **a pool that refuses at shutdown** (`!accepted`) writes `devLog.info` and reaches **no meter at all**, so
  events dropped at shutdown are invisible even in metrics;
- **the msg-audit writes `RX` BEFORE the handler runs**, recording arrival rather than outcome -- an event that
  was received and never applied reads as delivered.

The counters themselves exist: `messaging.error.total{leg=receive}` on the leg, and
`esq.biz.keep.write.total{outcome=error}` on the apply.

**Where it matters most:** bizTree's monad is healed regardless by the night-watch (`taijitu` + `SWAP`,
`interval=600000ms`), so a missed apply costs a bounded window of stale reads. auKeep has no healer -- an
audit row that failed to write is absent, and the counter is the only evidence.

**Raised by:** fresh-mind audit N7 (2026-08-24).

---
## 18. The alive round trip is traced at 100%, whatever `sampling-ratio` says

**Turning `msg-bus-alive-trace` on traces EVERY heartbeat / TestRequest round trip, regardless of the
configured ratio.** The round trip forces its own root: `EsqRodObserver.aliveOutbound` builds a phantom parent
so the trace id equals the correlationId, and `Sampler.parentBased(traceIdRatioBased(r))` consults the ratio
only at the ROOT. Any parent short-circuits it -- `createFromRemoteParent` would not help either, since
`parentBased` defaults `remoteParentSampled` to AlwaysOn as well. The bypass is structural, not a slip.

**Applying the ratio would mean deciding it here** -- hash the correlationId against the ratio and set
`TraceFlags` on the phantom accordingly, so the forced root carries a ratio-consistent decision.

**Not needed yet:** every shape ships `msg-bus-alive-trace=false` and `samplingRatio=1.0`, chart defaults
included, so nothing is over-sampled today. It matters to the first deployment that opts in AND thins its
traces -- and the alive rate is fixed by the cadence rather than by load, so the cost is knowable in advance.

**Raised by:** fresh-mind audit O5 (2026-08-24).

---
## 19. The queue rig's outcome seam

**The rule this serves:** a bad configuration stops, a bad message is recorded and the worker carries on
(`Esquire.Q&A.md`). The rig's part of that rule is narrow: it NOTIFIES the outcome of every item. What an
outcome MEANS is the owner's -- a meter, a REJECT on the bus, a status transition, nothing at all. The rig has
no opinion, and `mir0n-utils` carries no meter dependency to give it one.

**The seam is symmetric** (`IQueueRig`):

```java
interface IErrorListener   <E> { E    onError   (Throwable error, E element); IErrorListener   NOOP = ...; }
interface ISuccessListener <E> { void onSuccess (E element);                  ISuccessListener NOOP = ...; }
```

`onSuccess` fires once per item the worker processed without throwing, on both drain paths. There is no list
counterpart: a bulk worker returns the items it did NOT handle, so the rig already knows the success set and
fires per handled item -- a second interface would duplicate what the return value says.

Both contracts carry a `NOOP`, and the rig's fields are never null. Success DEFAULTS to the NOOP -- a line per
processed item is the cost a rig exists to avoid, and the rig compares against that instance to skip the bulk
path's bookkeeping when nobody listens. Error keeps the LOGGING default instead: a worker error nobody writes
down is what the seam exists to prevent, so silence has to be asked for by name.

**No error handling lives inside a queue worker.** A worker runs the happy path; a throw reaches the rig and
the owner's `onError` decides. Each owner implements the listener interfaces on the class that owns the work,
so `process()` and `onError()` sit together and the wiring is `rig.setErrorListener(this)`.

### What each rig does with its outcome

| rig | on success | on failure |
|---|---|---|
| `AXRod.feed` | the send loop's own `onSendSuccess` | `onError` -> `recordSendFailure`: msg-audit `TX-ERR` + `messaging.error.total{leg=send}`. A DISPATCH failure never reaches it -- `sendOut` ends its own retry loop |
| `MoveQueueManager` | `onSuccess` -> `esq.biz.move.processed.total` | `onError` -> `esq.biz.move.failed.total`. A reconcile shares the rig and counts as neither |
| `KcIdentityGateway` | `serve` answers `RESPONSE` | `onError` -> answers `REJECT`. No counter: the outcome IS the answer |
| `AMonadY` -> bizTree `Monad` | `handleCommand` sets the status and notifies the gate | `onError` -> `failed()`: status + gate. No counter: the outcome IS the caller being released |

Two of the four count nothing, which is the point: the same notification means a meter in one place and a
message in another.

**What moved with it:**

- **`AXRod.feed`** -- `encode` no longer swallows. It was the one send failure that left no trace: it caught,
  returned null, wrote a warn, and `sendOut` skipped on the null. Its catch now exists only to un-check
  `publisher.encode` (the rig worker signature cannot throw checked). `sendInProcess` stopped rethrowing after
  `onSendError`, which had counted the same failure twice.
- **`KcIdentityGateway`** -- `serve()` keeps only `finally { EsqContextHolder.clear(); }`. `onError` re-stamps
  the message ids before it answers, because serve's finally has already cleared them by the time a listener
  runs. `failureBody` takes a `Throwable`: the old `catch (Exception)` left an `Error` unanswered.
- **`AMonadY`** -- `handleCommand` runs the happy path and does not catch. `onError` resolves the in-flight
  command and notifies the right result, which is not optional: `resultCommand` waits on the gate with NO
  timeout, so a command that is never answered hangs its caller for good. A failing EVENT must not touch the
  command gate, which is what the in-flight tracking is for.

### TBD -- the outcomes that count nothing

Two rigs answer instead of counting, which is right for the seam but leaves a question: is the outcome
recorded ANYWHERE? Mostly yes, one layer down -- the work each rig delegates to counts itself. Enumerated so
the gaps are the short list, not the whole surface:

| outcome | counted today by | gap |
|---|---|---|
| KC identity request, ok or failed | `esq.biz.kc.sync.total{op,outcome}` -- `KcRequestHandler.handle()` counts it in a `finally` | none |
| Monad LOAD, ok or failed | `esq.biz.tree.rebuild.total{outcome}` -- `BizTreeCacheLoader.load()` counts it in a `finally` | none |
| entity apply on the cache | `esq.biz.tree.handler.dispatch.total{outcome}` | none |
| **a REJECT answered BEFORE `handle()` runs** | nothing | `objectMapper.convertValue(body, AuthSyncRequest)` throws on a malformed body, so the request never reaches the handler and `kc.sync.total` never fires. The caller is answered REJECT and no meter says a sync was refused |
| **`serveBroadcast` -- the race-8c safety net** | nothing | BUFFERED / NOT BUFFERED / applied are log lines only. The park is the mechanism that fixes a move the KC user has not caught up with; how often it engages is not measurable |
| **Monad CLEAR, ok or failed** | nothing | LOAD is covered by the rebuild counter, CLEAR is not. A CLEAR that fails still ends IDLE, so a wipe that did not happen looks the same as one that did |

**Worth deciding, not worth guessing.** The first two are real blind spots on paths that already exist to
handle a failure -- a refused sync and a park are exactly the events someone asks about after an incident. The
third is thinner: CLEAR runs at bootstrap and is followed by a LOAD whose outcome is counted, so a failed wipe
is visible one step later.

Whatever is added belongs to the OWNER, not the rig: `AMonadY` lives in `mir0n-utils` and must keep its zero
meter dependency, so a Monad counter would be raised by the bizTree subclass, not by the monad itself.

**Still open -- the receive leg.** It is a `WorkerPool`, not a rig, so it has no seam: its `catch (Throwable)`
bumps `messaging.error.total{leg=receive}` and writes a develop line, but nothing reaches the msg-audit channel
-- `MsgAudit.err` hardcodes the `TX-ERR` literal and is send-only by construction. The receive leg needs its
`RX-ERR` counterpart, or the channel that records what the bus did stays silent on half of it. It also carries
a consequence today: `KcIdentityGateway.serve()` is called from two paths, and only the composed one is a rig,
so on standalone kcMaster a failed identity request records but no longer answers `REJECT`.

**Reachability of the encode case, for scale:** none today. Every RodEvent body across the six body-building
sites carries Jackson-native values only, and the `fields` map is always a controller `@RequestBody`, so it is
Jackson-parsed by construction. The wire mapper is a bare `new ObjectMapper()` with no modules, so the first
`java.time` value or property-less object put in a body map would be the first live route.

**Raised by:** fresh-mind audit M5 (2026-08-24); the sweep of every rig came after it.

---
## 20. Driver-internal telemetry -- what the transport equipment inside a service reports

**What the current build does.** The bus meter set is emitted entirely ABOVE the transport, by `AXRod`:
`messaging.sent`, `messaging.send.duration`, `messaging.received`, `messaging.error`, `messaging.retry.*`,
and the registered gauges `messaging.feed.depth`, `messaging.retry.held`, `messaging.transport.up`. Every one
is tagged `bus-id` / `slot-id` / `msgType`, and the driver contributes nothing to any of them.

That is deliberate, and it is why a bus reads the same whichever driver carries it. It is also the whole of
what exists: **no driver emits a meter of its own.** Not one of `tp-activemq`, `tp-kafka`, `tp-redis`,
`tp-sqs`, `tp-sns`, `tp-kinesis` touches `IRodMeters` or `RodObserverHolder`.

**Why it is enough for now.** The bus meters answer the questions the bus is asked: is the leg connected, is
traffic moving, how long does a send take, is the feed backing up, is anything being retried or dropped. A
transport fault shows up in them -- as a send that takes longer, an error counted on the leg, or the transport
gauge going to zero.

**What is missing.** Everything about the equipment the driver itself runs inside the service. Two layers,
and they are not the same work:

*(a) What the vendor client already measures about itself.* An adapter, not a measurement.

| driver | the in-process client offers | adapter |
|---|---|---|
| `tp-sqs` / `tp-sns` / `tp-kinesis` | AWS SDK `MetricPublisher`: API call duration, service call duration, call successful, retry count, backoff delay, signing duration, time to first byte -- tagged by operation | one class to write |
| `tp-kafka` | the Kafka client metric set (send rate, request latency, consumer lag) | Micrometer `KafkaClientMetrics` exists |
| `tp-redis` | Lettuce command latency | `MicrometerCommandLatencyRecorder` exists |
| `tp-activemq` | nothing -- the ActiveMQ client publishes no metric set | would have to be built |

*(b) What only the driver can measure -- its own machinery.* Poll threads alive, empty-poll rate, batch
actually returned against batch asked for, a queue re-created under a leg, a subscription re-wired, a delete
that failed after the handler ran. And, on `tp-kinesis`, the one number a drained stream is judged by:
`GetRecords` returns `millisBehindLatest` on every poll -- how far behind the consumer is, the Kinesis
equivalent of Kafka consumer lag -- and the driver currently discards it.

**Why this is its own work and not a sprint fix.** These meters are VENDOR-SPECIFIC by nature: each set has
its own names, its own units and its own idea of what is worth counting, so doing it honestly means learning
six vendors' metric vocabularies, not writing six adapters. Half-doing it is worse than not doing it -- a
board where one driver reports its client and five do not invites exactly the wrong comparison. And every
series added has to land in the observability inventory and on a board, or it becomes the loose end the O set
took seven runs to close. `AWS_REQUEST_ID` as a tag is unbounded cardinality and must never be one.

**The fuller form.** One decision on what a driver is expected to report REGARDLESS of vendor -- the (b) list
above is the candidate, because it is the same shape for every transport -- expressed as an addition to
`IRodMeters` so it stays the bus's vocabulary rather than the vendor's. Vendor-native client metrics (a) ride
behind an explicit per-driver opt-in, off by default, and are named and inventoried per vendor.

**Raised by:** mir0n, 2026-08-29, reviewing the AWS drivers in v1.2.14 -- "we have to do this for all
vendors". Held at the level `tp-activemq` sets, which is none, so no driver is ahead of another.

---
## 21. A pushed receive leg on Kinesis -- enhanced fan-out

**What the current build does.** `tp-kinesis` reads with `GetRecords`, one poll thread per shard.
`GetRecords` has NO wait parameter: it answers at once whether or not there is anything in the shard, so the
driver sleeps `poll-millis` between empty reads and that interval IS the delivery latency. The floor is
200ms, because `GetRecords` is capped at five calls a second per shard -- sleeping less earns
`ProvisionedThroughputExceededException`, not lower latency.

This is the one place where the AWS transports are not interchangeable. An SQS receive carries
`waitTimeSeconds` and is held OPEN by AWS, returning the instant a message lands; a Kinesis reader waits in
its own JVM and nothing can wake it early.

**Why it is enough for now.** Kinesis carries the AUDIT bus, and 200ms to write a `*_log` row matters to
nobody. The one place it showed at all was a read-after-write: a client that creates an entity and
immediately asks bizTree for it can lose that race -- which is why the entity broadcast is on SNS, whose
queue-backed receive returns in milliseconds.

**What is missing.** Kinesis does offer a genuinely PUSHED read, and the driver does not use it:
`SubscribeToShard` -- enhanced fan-out. HTTP/2, records pushed to the consumer, about 70ms, and a dedicated
2 MB/s per consumer per shard instead of sharing the shard's read budget.

**Why it is a rewrite and not a switch.** Four things, all visible in the API:

| | |
|---|---|
| a different client | `subscribeToShard` exists only on `KinesisAsyncClient`; this driver is built on the sync `KinesisClient` |
| a different shape | an HTTP/2 event stream driven by a `SubscribeToShardResponseHandler` -- callbacks, not a request/response loop, so it is its own class beside `KinesisConsumer` rather than a branch inside it |
| a registration lifecycle | `SubscribeToShardRequest` needs a `consumerARN`, which comes from `registerStreamConsumer` and must reach ACTIVE first, and be deregistered on shutdown. Twenty registered consumers per stream is the limit |
| a subscription that expires | a subscribe runs five minutes, then ends. Re-subscribing means continuing from the last sequence number, so the position this driver deliberately keeps only in memory becomes load-bearing |

It also BILLS per consumer-shard-hour plus per GB retrieved, on top of the stream -- which is why it was not
taken for a lab whose sibling task is "lowest possible cost".

**The fuller form.** A `KinesisFanOutConsumer` beside the polled one (roughly 200-300 lines, plus the
registration lifecycle and re-subscribe handling), chosen by a leg param -- `read-mode: poll | fan-out`,
defaulting to `poll` -- so only a bus that needs the lower latency pays for it. The dependency is already
there: `aws-lib` ships Netty, so the async client's HTTP/2 needs are covered.

**Raised by:** mir0n, 2026-08-29, reading how the Kinesis receive leg waits -- "where does it sleep?". The
answer (in our own JVM, not on the server) is what makes this its own item rather than a tuning knob.

---
## 22. A doc of its own for every transport provider

**What the current build does.** Two of the five drivers carry their own description; three do not.

| driver | its own doc |
|---|---|
| `tp-activemq` | none -- only the `#### tp-activemq` subsection of `Esquire.MessagingBus.md` |
| `tp-kafka` | none -- same |
| `tp-redis` | none -- same |
| `tp-sqns` | `tp-sqns/doc/tp-sqns.md` |
| `tp-kinesis` | `tp-kinesis/doc/tp-kinesis.md` |

The split is historical, not a decision: the AWS pair were written after the rule that vendor specifics belong
beside the driver, and the three older ones were not moved.

**Why it is enough for now.** Nothing is undocumented. The framework doc carries a subsection per driver, and
for ActiveMQ, Kafka and Redis those subsections are complete -- publisher, consumer, vendor params, the
convention keys. A reader looking for how a driver behaves finds it; they just find it in one shared file
rather than beside the code.

**What is missing, and it is not only tidiness.** A per-driver doc is where a VENDOR'S OWN vocabulary and
traps get stated, and the framework doc is the wrong place for them because it must stay vendor-neutral. Two
concrete cases already exist:

- **The word `partition` means different things in Kafka and Kinesis.** Kafka's *partition* is Kinesis's
  *shard* (the real log resource); Kinesis's *partition key* is Kafka's *key* (the routing input). Carrying
  the Kafka meaning across leads straight to a wrong `partition-by`. That warning is written in
  `tp-kinesis/doc/tp-kinesis.md` and in the tp-kinesis subsection of the framework doc -- so it reaches a
  Kinesis reader, and says nothing to the Kafka reader who is the one holding the other meaning.
- **Who waits for a message differs per vendor**, and the shape of each driver follows from it: a persistent
  TCP connection is what lets ActiveMQ and Kafka observe their own connection health, while an HTTPS call per
  read leaves the AWS drivers with only the outcome of the last call -- which is why their health seeds
  UNKNOWN. That comparison lives in `tp-kinesis/doc/tp-kinesis.md` for want of anywhere better.

So the facts that are ABOUT a vendor end up filed under whichever driver happened to need them first.

**The fuller form.** `tp-activemq/doc/tp-activemq.md`, `tp-kafka/doc/tp-kafka.md`, `tp-redis/doc/tp-redis.md`,
each in the shape the two AWS ones already use: what the vendor actually is in bus terms, the traps its
vocabulary sets, what rides on the wire, configuration, and health. The framework doc then keeps the
driver-neutral view and stops carrying vendor detail. The cross-vendor comparisons (the `partition`
vocabulary, the who-waits table) move to wherever both sides can see them rather than sitting in the newest
driver's file.

**Raised by:** mir0n, 2026-08-29 -- "we had to update tp-kafka.md: do we have one?". We do not, and the
answer to why is this item. Writing the Kafka doc was deliberately NOT done as part of v1.2.14: that module
is outside the sprint, and a stub created only to hold one cross-reference would be worse than the
cross-reference living where it is.

---
## Related parked items

- Transport SPI is ActiveMQ-shaped; selector / `key()` / durability differ per driver -- the per-driver selector
  design direction.
- Loss-visibility drop counters -- make a dropped broadcast observable rather than silent.
- DB-pool durability (pgjdbc `socketTimeout` / `tcpKeepAlive`; the `isValid()`-on-a-half-open-socket gap that
  makes an in-process keep rod's datasource health blind on k8s) -- belongs to the item-8 k8s durability gate.
