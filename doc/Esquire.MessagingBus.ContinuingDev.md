# Esquire Messaging Bus -- Continuing Development

Forward-looking work for the Messaging Bus that is deliberately OUT of the current build, parked here so the
first-run decisions stay honest about what they defer. Companion to `doc/Esquire.MessagingBus.md` (the bus
design) and `doc/Message.Structure.md` (the wire envelope, incl. the session TestRequest / HeartBeat pair).

These are NOT scheduled tasks -- they are the known, accepted gaps of the shipped design. Each entry states
what the first run does, why it is enough for now, and what the fuller form looks like. Promote an entry into a
sprint plan (`doc/plans/*`) when it is actually picked up.

---

## 1. Alive protocol -- the consumer-leg gap (Q&D first run)

**First run.** The x-rod alive protocol reads health off the PRODUCER (send) leg ONLY -- the consumer (receive)
`legTimestamp` is ignored. A broadcast CLIENT auto-opens a producer leg in the framework (base `XRod` always
transmits -- no `BOTH` role, no per-service config change), so every broadcast rod has a producer leg to
self-heartbeat. Rationale: both legs share one transport node, so a working send proves the transport is up.

**Why Q&D is acceptable.** A consumer leg's `legTimestamp` only advances when SOMEONE ELSE produces, so a quiet
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

That is the sharp point for us: our Q&D reads the PRODUCER (send) leg -- the OPPOSITE axis from FIX. FIX can
trust the receive side because a mandated counterparty keeps it fed; we cannot -- a broadcast CLIENT has no
single obligated peer, and an R&R SERVER has no client until one "logs on". So we fall back to "can I send"
(local transport-up) as a weaker proxy precisely because FIX's precondition (a mandated heartbeating peer) is
absent on our asymmetric pub/sub legs. There is no FIX recipe to borrow; the gap is genuinely ours. The two
FIX-faithful resolutions both map onto the deferred items below: SYMMETRIC heartbeat both directions (the R&R
SERVER's unsolicited HB on inactivity is exactly this), and treating "no peer yet" as FIX's pre-Logon NEUTRAL
state -- not DOWN -- until a peer appears.

**What it defers.**
- **R&R CLIENT round-trip health.** First run = "I can send a TestRequest" (local transport up), NOT "the
  SERVER answered." So a reachable-but-dead R&R server is not detected yet. The fuller form tracks the
  TestRequest -> HeartBeat round trip (pending-request + reply within `alive-timeout`) so CLIENT health truly
  reflects "server alive" -- which is the correct CLIENT dependency.
- **Per-leg "no peer yet" vs "peer dead".** The real fix for the SERVER consumer leg: hold the consumer-leg
  indicator NEUTRAL until the alive protocol has seen at least one peer (a CLIENT arrives), then treat silence
  as DOWN. Same idea for a broadcast CLIENT once consumer-leg health is brought back in.
- **Broadcast consumer heartbeat chatter.** Because a broadcast CLIENT auto-opens a producer leg, broadcast
  consumers (bizTree / kcMaster) publish heartbeats onto `esquire.entity`; every other consumer receives and
  ignores them (minor at a 10s cadence). When consumer-leg health returns, the producer leg on a pure consumer
  can be dropped.

> CONFIRMED (mir0n 2026-06-23): ignore-consumer-leg + producer-leg-only health is the chosen first-run approach;
> a broadcast CLIENT auto-opens both legs in the framework (no `BOTH` role).

---

## 2. Session-message exposure toggle (`x-rod.admin.expose`)

**First run.** Session (TestRequest / HeartBeat) messages are handled INTERNALLY by the x-rod and never reach
the application worker. There is no exposure path.

**Fuller form.** A per-leg toggle (`x-rod.admin.expose`, default false) ALSO surfaces session messages to the
x-rod listener -- for an admin / monitor tool -- the way a FIX engine lets session messages reach the app only
when explicitly hooked. TBD/TODO; not in today's build.

---

## 3. Alive-protocol tuning

**First run.** Knobs live on the x-rod and the operator keeps them in sync across all x-rods on a slot:
`heartbeat-interval` (default 10s) and `alive-timeout` (default 3 x heartbeat-interval = 30s).

**Open.**
- **Send-failure handling** is CONFIGURABLE: a send exception may flip the leg DOWN IMMEDIATELY (fast path) or
  be left to `alive-timeout` (pure timeout). The DEFAULT is to be LEARNED during the build / from live behavior.
- **Per-transport send semantics.** A transport vendor may itself act differently on a send (the message gets
  consumed, never consumed, or it is configurable). This must be LEARNED per transport API employed
  (ActiveMQ / Kafka / Redis) and may change the right cadence / timeout per transport.
- **Defaults** (`heartbeat-interval`, `alive-timeout`) may want per-transport or per-slot tuning once there is
  real operational data.

---

## Related parked items (already tracked elsewhere -- not re-listed here)

- Transport SPI is ActiveMQ-shaped; selector / `key()` / durability differ per driver -- `tasks129.md` item 13
  (v1.2.10) and the selector design direction recorded there.
- R&R reply timeout + rod-id uniqueness -- `tasks129.md` item 14 (v1.2.10).
- Loss-visibility drop counters (the other half of commit 7) -- `tasks129.md` commit 7 / item 1.
- DB-pool durability (pgjdbc `socketTimeout` / `tcpKeepAlive`; the `isValid()`-on-a-half-open-socket gap that
  makes `XRodInProcessKeep` / `keepDatasource` health blind on k8s) -- memory
  `reference_k8s_db_pool_isvalid_socket_timeout`; belongs to the #8 k8s durability gate.
