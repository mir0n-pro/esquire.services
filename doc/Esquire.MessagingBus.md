<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>

# Esquire Messaging Bus — Framework

> The Messaging Bus is a shared module that gives services one uniform way
> to do asynchronous messaging. This document is the **complete framework reference** — the bus model,
> the x-rod engine, the parameter model, the transport-driver SPI and every driver's parameters, and the
> wire message structure — described abstractly, free of any one application's use of it. The concrete
> bus catalog a given deployment runs (which buses exist, their bus configuration) is documented
> separately. The framework is today still partly coupled to its host application; a later refactoring
> completes the separation — see [Coupling and the separation roadmap](#coupling-and-the-separation-roadmap).

**The Messaging Bus documents.** This reference is the architecture; four companions complete the family:
- [`Esquire.MessagingBus.Guides.md`](Esquire.MessagingBus.Guides.md) — plain-language how-to guides.
- [`Esquire.MessagingBus.MessageStructure.md`](Esquire.MessagingBus.MessageStructure.md) — the wire message structure (every message type's fields).
- [`Esquire.MessagingBus.Q&A.md`](Esquire.MessagingBus.Q&A.md) — Design Q&A: things that *sound like* bus defects and why each is deliberate.
- [`Esquire.MessagingBus.ContinuingDev.md`](Esquire.MessagingBus.ContinuingDev.md) — forward-looking work and the separation roadmap.

---

## What

The Messaging Bus is the single way a service does asynchronous messaging. A service never opens a JMS
connection, a Kafka producer, or a Redis client; it asks one facade — `MessagingBus` — for the x-rod of a
*logical bus* (built from its declared `role`), and the framework builds the rest from a declared catalog.

Four ideas make it up:

- **A bus catalog (the topology)** — every bus declared once, across services, in one external file.
  A **bus topology**, where buses are first-class declared infrastructure, in place of the point-to-point
  **service mesh** a microservice fleet usually grows.
- **The x-rod** — one x-rod type per bus leg (`IXRod`) with two legs, a transmit leg and a
  receive leg; the role decides producer / consumer / in-process. ("Rod" = *Relay of Data*.)
- **An open transport-driver SPI** — `ITransportProvider`, one drop-in module per vendor. A deployment
  carries only the drivers it uses; the framework names no vendor.
- **The bus patterns** the substrate supports — **broadcast** (many publishers, many subscribers) and **request/response**
  (two-node round-trip), plus an **in-process** x-rod for a leg that applies events locally.

![Messaging Bus runtime path: a service asks the MessagingBus facade for a bus's x-rod; the facade resolves the catalog leg and the x-rod (by rod-class); the x-rod encodes each event through RodEventCodec to a TransportMessage and hands it to the resolved driver, which maps it onto the broker wire.](img/messaging-bus-architecture.svg)

## Why

A microservice fleet normally wires messaging as a **mesh**: each service knows the brokers, queues, and
clients it talks to, in its own config and its own connection beans. That spreads the same knowledge
across every service and couples each one to every transport it might touch. The concrete costs:

- **Image bloat** — a producer image bundles every messaging client even when it uses one.
- **Per-service branching** — `if (transport == activemq) … else …` repeated in each producer.
- **Vendor coupling** — the framework auto-wires a broker the moment its client is on the classpath.
- **N ways to configure N buses** — each channel grows its own bespoke wiring, constants, YAML shape.

A **bus topology** answers all four. The buses live in one catalog, declared once with concrete
per-environment wires; a service references a bus by a logical key and a role and gets a uniform x-rod;
transports are drop-in driver modules pulled in only where used, with their auto-config suppressed.
Adding a transport is a jar on the classpath plus a config value; adding a bus is one entry in the
topology file. One mental model — *bus, slot, node, role, rod-class, transport* — covers messaging
end to end.

## Where

| Piece | Location |
|---|---|
| Public API | `pro.mir0n.esquire.messaging` — `MessagingBus` (the facade), `IXRod`, `RodEvent`, `IRodEventRepo`, `RodEventRepoRegistry`, `BusHealthIndicator`, `TransportHealthIndicator`, `BusConstants` (the FIX-JSON wire constants) |
| Bus config model + catalog | `messaging.catalog` — `MessagingBusCatalog`, `MessagingBus` (a catalog bus record), `BusSlot`, `BusNode`, `BusRef`, `BusTransport`, `XRodParams`, `Role` |
| x-rods | `messaging.xrod` — `RodEventCodec`, `RodPublisher`, `RodTransportAdapter` — and `messaging.xrod.impl` — `AXRod`, `XRod`, `XRodRR`, `XRodInProcess`, `XRodInfo`, `XRodDisabled`, `ISessionSublayer` (the session-sublayer seam), `MsgAudit` (the per-leg message log). The concrete sublayers live in `messaging.xrod.impl.sublayer` — `SessionSublayerFactory`, `AliveSession` / `AliveSessionRR` (the alive protocol), `SendRetrySublayer` (the producer send-retry). The in-process KEEP x-rod, `XRodInProcessKeep`, ships in the dataKeep library (resolved by `rod-class` like any other). |
| Transport SPI | `messaging.transport` — `ITransportProvider`, `TransportProviders`, `TransportMessage`, `TransportPublisher`, `TransportConsumer`, `TransportSettings`, `PublishSettings`, `ConsumeSettings`, `BusIdentity`, `TransportHealth` |
| Transport drivers | one module per vendor — `pro.mir0n.esquire.tp.<name>.TransportProvider` + an `AutoConfigurationImportFilter` |

## Terminology

The messaging bus has a small, deliberate vocabulary. The **bus** and **slot** are abstractions for
describing the topology; the **x-rod** and **network node** are the concrete software and the wire.

| Term | Definition |
|---|---|
| **messaging bus** | A logical aggregation of network elements that provides communication between computing services over a network. Two kinds — **broadcast** and **request-response**. An abstraction: a simplification for describing the network topology. |
| **slot** | A part of one messaging bus — an entry point onto the bus for a specific need. A bus *is* a set of slots that share common infrastructure, behavior, and purpose. Abstract, like the bus. |
| **network node** | The transport abstraction exposed by a vendor's (provider's) API — an ActiveMQ queue, a Kafka topic, a Redis stream. The concrete destination on the wire. |
| **x-rod** (short: **rod**) | The session-level software module *slotted* into a bus slot. It uniformly defines access to the network node(s) and so unifies a vendor's API with the messaging-bus concept — *as a lightning rod is slotted to a castle tower.* In code: an implementation of `IXRod`. |
| **leg** | A bus user — the publish or consume side. A **publisher leg** sends; a **consumer leg** receives. Every communication is a pair of legs. |
| **role** | Which legs an x-rod runs. On a **request-response** bus: **CLIENT** (transmit request / receive response) or **SERVER** (transmit response / receive request). On a **single-node** bus: **CLIENT** = receive, **SERVER** = transmit. |

**Catalog keys** — how the terms appear in the topology / configuration:

| Key | Term it configures |
|---|---|
| `bus-id` | the **bus** |
| `slot-id` | a **slot** on that bus |
| `node-id` | a **network node** (R&R splits into a `request` and a `response` node; a single-node bus uses `destination`) |
| `rod-class` | which **x-rod** implementation runs (`XRod` / `XRodRR` / `XRodInProcess` / `XRodInfo` / `XRodDisabled` / a custom class) |
| `x-rod` | the slot's **x-rod** configuration — engine knobs + the `transport` block |
| `transport` | the **network node** binding: `provider` + `endpoint` + `destination` + `params` (vendor knobs, e.g. ActiveMQ's `pubSubDomain`) + (R&R) `request-node` / `response-node` + a `node` list |
| `role` | the **role** (`CLIENT` / `SERVER`), declared per bus ref; picks which legs the x-rod runs |

## How

The runtime path: a service asks the facade for a bus's x-rod; the facade resolves the leg config from the
catalog and the x-rod by `rod-class`; the x-rod builds its own transport from the resolved driver and runs
its two legs. The rest of this section is that path in detail — the facade, the parameter model, the
x-rod engine, the x-rod types, the transport SPI and drivers, and the per-leg logging.

### The facade — `MessagingBus`

One per service — a singleton (`MessagingBus.getInstance()`), so a publisher or consumer class carries no
lifecycle wiring of its own. A two-phase lifecycle plus a lookup:

```java
void  init(Environment env, String[] busKeys);   // BUILD: construct every named bus's x-rod, PAUSED (no I/O)
void  start();                                    // RUN:   open + run every built x-rod (traffic flows now)
IXRod getXRod(String busKey);                     // hand a built x-rod to a publisher / consumer adapter
void  close();                                    // drain in-flight + shut every x-rod down
```

**Lifecycle — wired once per service, in `main()`.** A small `MessagingBusLifecycleRegistrar` inner class
(one `ApplicationListener`, `LOWEST_PRECEDENCE`) routes the three Spring Boot events; the service names the
buses it uses inline:
- `ApplicationEnvironmentPreparedEvent` → `init(env, {BUS_KEY_…})` — load + validate the catalog, then build
  every named bus's x-rod PAUSED. **No transport I/O.**
- `ApplicationReadyEvent` → `start()` — open each x-rod's transport and run its legs (after the service's own
  ready work, e.g. role load, so it is registered last).
- `ContextClosedEvent` → `close()` — drain in-flight work and close every transport.

`init` builds one bus's x-rod per named key:

1. **Resolve the key to a `BusRef`** — `esquire.<busKey>.messaging-bus → {bus-id, slot-id, role, x-rod?}`.
2. **Resolve the leg params** — `MessagingBusCatalog.find(bus-id, slot-id)` gives the BASE `XRodParams`; a
   service-level `x-rod` on the ref merges over it (see [the parameter model](#configuration-and-the-parameter-model)). A bus a service uses with a `role` but NO leg **fails fast** — the topology must define it.
3. **Default the rod-id** — `withBus(bus-id, slot-id, instanceId)` folds the identity in and defaults an
   unset/blank `rod-id` to the per-instance id `<app>.<instanceNo>` (`spring.application.name` + the
   instance number, parsed from this pod/container's host name — the StatefulSet ordinal in k8s, a
   `hostname: <app>-N` in Docker), so each sharded replica owns a distinct rod-id and a CLIENT's `RodID`
   selector isolates that instance's responses.
4. **Resolve the x-rod by `rod-class`** — a bare name resolves under `messaging.xrod.impl` (a built-in), a
   dotted value is a full class name (any custom `IXRod`). To run a service **without** a bus, name
   `rod-class: XRodDisabled` explicitly — there is no silent fallback: an undeclared / unbuilt bus key
   **throws** at `getXRod` (a wiring bug, not a quietly-disabled slot).
5. **`validate(params)`** — fail-fast: each x-rod checks the leg config IT requires (`XRod` a complete
   transport, `XRodRR` the request/response nodes, `XRodInProcessKeep` its datasource + director). Default is
   no requirement (the OFF / log-only x-rods).
6. **`configure(params, role, objectMapper)`** — the x-rod is now built and PAUSED (no I/O); `start()` opens
   and runs it. The facade tracks every x-rod; `close()` shuts them all down.

**Wiring an adapter.** A publisher / consumer `@Component` (a *BusAdapter*) pulls its x-rod in its constructor
via `getXRod(busKey)`, then — by its role — sets its receive worker with `setWorker(...)` and / or probes its
transmit leg with `transmit(null)`. Both fail fast if the rod's role lacks that leg, so a producer wired to a
non-producing bus (or a consumer to a non-consuming one) is a boot error, not a silent no-op.

The x-rod builds its **own** transport from the leg — the facade re-packs nothing.

### Configuration and the parameter model

#### The catalog

`MessagingBusCatalog` MERGES two property sources BY ID:

- `esquire.messaging-bus` — the shared cross-service topology (imported from the one topology file, or
  defined inline — the import is OPTIONAL);
- `<spring.application.name>.messaging-bus` — a service's OWN legs under its OWN namespace (e.g. a leg
  whose wire or backing store is service-specific). This is the service-side OVERLAY of the global catalog.

The overlay merges onto the shared catalog BY ID: a service bus REPLACES the shared bus with the same
`bus-id` (a service slot replaces the shared slot with the same `slot-id`; a new bus/slot is added).

> They are bound as TWO keys + merged in code, NOT a single `esquire.messaging-bus` key across two sources:
> Spring binds a list by INDEX, so a higher-precedence source would REPLACE the whole list instead of
> merging. The service overlay lives under the service's OWN top-level key, so it stays clear of the global
> topology key and of the `esquire.<bus-key>.messaging-bus` refs.

Each source is validated for INTERNAL uniqueness before the merge: a duplicate `bus-id` (across a list),
`slot-id` (within a bus), or `node-id` (within an x-rod's `transport.nodes`) FAILS FAST at construction —
the list is used as a map, so its keys must be unique.

A leg is named `(bus-id, slot-id)`; the catalog binds it to an `XRodParams`.

#### `XRodParams` — a bound leg

`XRodParams(busId, slotId, raw)`: `raw` is the leg's `x-rod` node **flattened** to dotted keys (so a
nested `transport: { endpoint: … }` reads as `transport.endpoint`). All knobs are read FROM `raw` by name:

- **Scalar knobs** — registered once in `XRodParams.SCALARS`: `rod-id`, `rod-class`, `feed-capacity`,
  `concurrency` (typed getters parse String-or-Number).
- **Pool groups** — `receiver-pool` and `publisher-pool`, each a `{ size, mode }` block read by its dotted
  keys (`receiver-pool.size`, `receiver-pool.mode`, …); `mode` = `platform` | `virtual` | `virtual-per-task`.
  Each merges as a WHOLE group (see below).
- **`transport()`** — binds the `transport.*` group into a `BusTransport`; the `params` map is rebuilt
  straight from `raw` so every `transport.params.*` key survives VERBATIM, including dotted vendor keys
  like `jms.useAsyncSend` (Spring's own `Map<String,String>` binding is unreliable for dotted keys).
- **`sub(key, Class)`** — binds an x-rod-OWNED named sub-block (the x-rod passes its own key) into a record;
  `XRodParams` knows no x-rod type.

#### The merge — per top-level GROUP

`merge(override)` overlays a service-level override onto the catalog base **per top-level group**: for
each group the override sets, the base's WHOLE group is dropped, then the override's keys are put. A
**group** is a scalar (its own one-key group), the `transport` wire, or an x-rod sub-block. So scalars merge
field-wise, while `transport` (and any x-rod block) is replaced **whole** — you provide a group entire,
never field-merged across (so a service can't half-override a vendor wire). The same `overlayGroups`
routine drives the R&R node merge below. `bus-id` / `slot-id` are not in `raw`, so they are never merged.

![Parameter resolution: the catalog merges the shared topology with the service-local overlay by id into the base XRodParams; a service-ref x-rod override merges over it per group; withBus folds in the identity and defaults the rod-id to the per-instance id app.instanceNo (the instance number parsed from the host name). For R&R, XRodRR refines the base transport with the request/response node via overlayGroups, keeping provider and endpoint from the base.](img/messaging-bus-params.svg)

#### The three merge levels

An x-rod's effective wire is resolved across three levels, all by the same per-group overlay:

1. **Leg x-rod params** — the scalar knobs + identity (rod-id default = the per-instance id `<app>.<instanceNo>`).
2. **Transport params** — the `transport` group (provider / endpoint / destination / `params`);
   a service-ref override replaces this group whole.
3. **Node params** (R&R only) — for a two-node leg, `XRodRR` refines the base `transport` with the
   request-or-response NODE: the node owns its `destination` and may override any transport scalar or the
   `params` group **except** `provider` / `endpoint` (the base owns the wire).

### The x-rod engine (`AXRod`) and the two legs

The transceiver engine — the feed (transmit leg), the `Semaphore`-bounded worker pool (receive leg), the
message trace, and their lifecycle — lives in the abstract base **`AXRod`**; every x-rod that has a feed
and/or a pool EXTENDS it (`XRod` adds a transport; the in-process `XRodInProcess` runs a worker that applies
each event to a local sink), rather than wrapping a copy. `XRod` is the default x-rod: a transmitter/receiver.
Lifecycle is five steps — construct (no-arg, reflectively instantiated), `validate(params)` (fail-fast on the
required leg config — see the facade), `configure` (PREPARE), `init(name, devLog)` (CREATE the legs, PAUSED),
`start()` (RUN); `setWorker` sets/resets the receive callback (any time after `configure`), `shutdown` stops it.

- **`configure(params, role, objectMapper)`** reads the identity (`BusIdentity` = bus-id / slot-id / rod-id),
  the engine knobs (`feed-capacity` default 4096, `receiver-pool.size` default 4, `receiver-pool.mode` default
  `platform`), and the `role`.
- **`init(name, devLog)`** builds the engine PAUSED — a transmit leg if the role transmits, a receive leg
  (pool) if the role receives; `transportBacked = transport != null && objectMapper != null` decides the
  shape. `start()` then opens the transport and runs the legs:

| receive leg | transportBacked | shape |
|---|---|---|
| no | yes | **producer** — opens a publisher to the leg destination |
| no | no | a no-op producer (no transmit leg) |
| yes | no | **in-process** — `outbound = this::receive`; the feed loops back to the worker |
| yes | yes | **consumer** — the receive pool applies the worker; opens the transport consumer |

#### Transmit leg

- **`transmit(event)`** — the single entry point: it puts a pre-built `RodEvent` (already carrying its
  `msgType`) on the feed. The x-rod is a pure relay — it does NOT buffer in a transaction or stamp times.
  The producer builds the event and calls `transmit`; a producer that needs transactional ordering (buffer
  inside the transaction, flush after commit, stamp one time) does that on its own side, then `transmit`s.
- The **feed** is a `BoundedQueueRig<RodEvent>` of depth `feed-capacity`; its single worker (the feed / tx
  worker) is the ONLY sender. It stamps the stable `ApplMsgID` once, then OWNS the send: `encode` the event
  to the transport's concrete unit ONCE, then `dispatch` it — driving the **session-sublayer** hooks at each
  step (the alive marks, the send-retry decision) and logging the `TX` / `TX-ERR` message-log line at the OUTCOME
  (see [Session sublayers](#session-sublayers-and-producer-resilience) and [Logging](#logging)). The
  sublayers never send; they only react. A transport failure (a throwing `dispatch`) is the send-retry
  signal; a successful landing marks the leg sent.
- The **`outbound`** is the publisher for a direct producer; with `publisher-pool.size > 0` it becomes
  `this::receive` and the publisher is run on an async pool sized from `publisher-pool` (`{ size, mode }`) — i.e.
  **feed → bounded pool → publish**, asynchronous pooled publishing; for an in-process x-rod it is
  `this::receive` looping back to the receive worker.

#### Receive leg

- **`receive(event)`** logs the `RX` message trace, then hands the event to the leg's **`WorkerPool`**
  (`pro.mir0n.utils.concurrent`) via a bounded `submit`. The `WorkerPool` owns the thread model AND the bound:
  `platform` / `virtual` = a fixed pool of `receiver-pool.size` reused workers (dedicated OS threads, or `size`
  virtual threads), so the pool size IS the concurrency cap; `virtual-per-task` = one virtual thread per event,
  capped by `Semaphore(size)` (uncapped when `size = 0`). The same pool runs the publisher in pooled-async mode
  and the writer for an in-process x-rod; a worker failure is logged and isolated, and a `submit` that cannot be
  admitted (pool shut down) is dropped. A `receive` with no receive leg wired (or before `start`) throws; a late
  `receive` during shutdown is logged and dropped, not thrown.

#### Lifecycle hooks (overridden by `XRodRR`)

- **`legTransport(produce, role)`** — the effective wire for this leg; base `XRod` is single-node (the
  one `transport`). `XRodRR` overrides it to pick the request/response node.
- **`consumeSelector(role, identity)`** — the receive selector; base returns `null` (the whole node).

The per-rod **`idle()`** maintenance step — fired by the one `MessagingBus` idle ticker on every rod — drives
the session-sublayer cadence (the alive heartbeat, the send-retry re-send). The alive keep-alive and the R&R
echo are NOT x-rod hooks; they live in the session sublayers below. (Base `XRod` always opens a transmit
leg, so even a broadcast consumer has a producer leg to self-heartbeat.)

`shutdown()` stops delivery first (closes the inbound transport consumer), winds the feed down, then DRAINS
the worker pool (`awaitTermination`) so in-flight applies / async publishes finish, and closes the outbound
transport publisher last — releasing its broker connection.

#### Session sublayers and producer resilience

The messaging path carries **its own** resilience. Resilience4j — the circuit breaker / timeout / retry the
gateway uses (`Esquire.HighAvailability.md`, gateway resilience) — is **synchronous only**: it wraps a blocking
call and cannot bound an async, fire-and-forget publish. So the bus provides the async-path patterns itself, as
producer **session sublayers** on the x-rod. Because they live in the x-rod ABOVE the transport SPI, they apply
over **whatever provider is bound** (ActiveMQ, Redis, Kafka) — the resilience is the bus's, not a broker feature,
and does not change when the transport is swapped or put into an HA mode.

The producer leg carries a stack of **session sublayers** — event-driven collaborators that sit BESIDE the
send workflow, never in it. The feed worker owns the send (encode + dispatch); it calls the sublayer HOOKS as
a message passes, and a sublayer marks its OWN state and reacts, but never sends. `SessionSublayerFactory`
builds the stack per leg config and hands it to the engine; the engine (`AXRod`) names only the abstraction
`ISessionSublayer`, so a new resilience pattern slots in as another sublayer without touching the worker.

```java
interface ISessionSublayer {
    void beforeSend(RodEvent ev);                               // an attempt is starting
    void onSendSuccess(RodEvent ev);                            // the dispatch landed
    Object onSendError(RodEvent ev, Object enc, Throwable err); // the dispatch threw -> enc to re-dispatch, null to stop
    void onReceiveSessn(RodEvent ev);                           // an arriving session (alive) message
    void tick();                                                // the idle() cadence step (no own thread)
    TransportHealth health();                                   // the leg's session-health contribution
    void start(); void shutdown();                              // lifecycle
}
```

The send loop drives the hooks at the OUTCOME: on a landing it calls `onSendSuccess` on every sublayer; on a
throw it calls `onSendError` on every sublayer (all observe the failure) and re-dispatches the encoded unit a
sublayer hands back, stopping when none does. The stack is ordered **alive first, then send-retry** — so the
alive marks (and fail-fast) fire BEFORE send-retry's blocking hold. Two sublayers ship:

- **`AliveSession`** (opt-in, `alive: true`) — the FIX-style keep-alive + timestamp-age health (see
  [Health](#health)). `beforeSend` resets the cadence gate, `onSendSuccess` marks the producer leg alive,
  `onSendError` flips it DOWN on fail-fast; `tick` PUTs an unsolicited `HeartBeat` on the feed when the leg is
  idle; `health` is the producer-leg timestamp age. **`AliveSessionRR`** specialises it by R&R role: a CLIENT
  keep-alive is a `TestRequest` (its rod-id rides so the SERVER's reply routes back), and a SERVER emits NONE --
  routing on the response node is echoed from the request, and a SERVER sitting idle has no request to echo, so
  an unaddressed HeartBeat would match no consumer selector and stay in the queue. A SERVER answers instead:
  `onReceiveSessn` echoes an arriving `TestRequest` back as a `HeartBeat`, carrying the requester correlation
  and rod-id. The CLIENT drives the liveness.
- **`SendRetrySublayer`** (opt-in, `send-retry: true`) — the one producer messaging-path resilience pattern.
  On a dispatch failure `onSendError` records the message (keyed by its stable `ApplMsgID`) and HOLDS the feed
  worker across a backoff ladder (a monitor wait released by `tick`, NOT a sleep), then hands back the SAME
  encoded unit so the worker re-dispatches it — the same `ApplMsgID` on every resend, so a consumer can dedup.
  Holding the single feed worker IS the back-pressure: it stops dequeuing, the bounded feed fills, producers
  block. **Block mode** (`send-retry-max-attempts: 0`, the default) retries until the broker recovers;
  **fallback mode** (a positive cap) DROPS the message after that many attempts and moves on. The backoff is a
  seconds ladder (`send-retry-backoff-sec`, default `1,2,5,5` — the last step repeats). A SESSION (heartbeat) event
  is skipped — best-effort, never held. The knobs are in `services.configuring.md`.

**Producer resilience — what ships, what is deferred.** Two patterns ship (above); the rest are the DEFERRED
set, each a future sublayer on the same seam (`ISessionSublayer`), so they slot in without touching the worker:

| Pattern | Status | Note |
|---|---|---|
| Keep-alive (liveness + health) | **ships** — `alive` | the alive protocol; `AliveSession` / `AliveSessionRR` |
| Send-retry (hold + re-send on failure) | **ships** — `send-retry` | `SendRetrySublayer`; block or drop-after-N; `health()` DOWN while holding |
| Circuit breaker | deferred | needs an "on open" policy (drop / hold / dead-letter) the bus does not have yet |
| Retry / backoff variants | deferred | shapes beyond `send-retry` |
| Per-message timeout | deferred | async has no request/response deadline today |
| Per-destination bulkhead | deferred | only `receiver-pool.size` bounds concurrency today |
| Metrics | **ships** | Micrometer counters (`EsqRodObserver`) -- send/receive/error/duration + retry backoff/held/dropped, drawn on the bus dashboards; separate from the health signal |

The deferred set and why R4j does not apply are tracked in `Esquire.MessagingBus.ContinuingDev.md` item 5.

### x-rod types (`rod-class`)

`rod-class` selects the x-rod exactly as `transport.provider` selects the driver — a bare name resolves to
`pro.mir0n.esquire.messaging.xrod.impl.<name>`, a dotted value is a full class name; default `XRod`,
disabled `XRodDisabled`. A fresh instance is created per resolve. An application may add its own `IXRod`
x-rod (e.g. an in-process x-rod that applies received events to a local datastore) by naming its class.

#### `XRod`

The standard transceiver — the engine above. Non-final so `XRodRR` can extend it.

#### `XRodRR` — request/response, two nodes

A specialised `XRod` for an R&R leg. The base transceiver is unchanged; only two hooks are overridden:

- **`legTransport(produce, role)`** picks the request or response node and refines the base wire with it:
  - **direction** — `wantRequest = produce == (role == CLIENT)`: produce-CLIENT / consume-SERVER → the
    `request` node; produce-SERVER / consume-CLIENT → the `response` node;
  - the leg's nodes bind to a typed `List<BusNode>` (`transport.nodes[*]`); `legTransport` selects the
    `BusNode` whose `node-id` matches `transport.request-node` / `transport.response-node`, then refines the
    base transport with it via `BusTransport.refinedWith(node)` — the node owns `destination` /
    `params`, the base owns `provider` / `endpoint`. A non-R&R role, or a leg with no such node, falls back
    to the base single transport.
- **`consumeSelector(role, identity)`** — `CLIENT` → `RodID = '<rod-id>'` (an instance consumes only its
  own replies); `SERVER` → `SlotID = '<slot-id>'` (its own service's requests off a possibly-shared node).

#### `XRodInProcess` — generic in-process

A generic in-process x-rod for a leg that applies its events to a LOCAL sink rather than sending them on a
wire. `transmit(event)` feeds the event into the x-rod's OWN worker pool, which runs the configured worker
(an applier) — there is no transport and no codec. It is the piece that STARTS the worker pool a bare
producer leg lacks: a base `XRod` producer is transmit-only and never opens a pool, so a leg that must run a
worker locally selects `XRodInProcess`. `XRodInProcess` itself ships in `messaging.xrod.impl`; a concrete keep
— `XRodInProcessKeep` (dataKeep) — extends it: its `init` opens the engine, then sets its own worker, an
applier built from the leg's `datasource` + `director` blocks, to which the in-process pool loops each
transmitted event. Resolved by `rod-class` like any x-rod.

#### `XRodInfo` — log-only

A non-sending x-rod: it `log.info`s each event's full content to its `msg.<bus-id>.<slot-id>` logger,
led by a directive from its own `x-rod.info` sub-block (`XRodInfoParams.dir`, default `Skipped`), instead
of transmitting. It logs each event directly — no feed, no pool, no transport: `transmit` / `receive` write
one line (the whole `RodEvent`, led by the directive) to the leg's `msg` logger. A dry-run / kill-switch leg.

#### `XRodDisabled` — OFF

A fully inert `IXRod`: every method a no-op, no config, no transport, `isEnabled()` false. Selected ONLY
explicitly — `rod-class: XRodDisabled` — to run a service WITHOUT a bus it would otherwise use (a bus declared
in the catalog but pointed at the disabled rod, so that channel is switched off while another mechanism carries
the concern). There is no
silent fallback: an undeclared / unbuilt bus key **throws** at `getXRod` (a wiring bug), so a disabled bus is
always a deliberate, in-catalog declaration — never a quietly-absent one.

### The transport SPI and drivers

#### The SPI

```java
interface ITransportProvider {
    TransportPublisher openPublisher(String destination, PublishSettings settings);
    TransportConsumer  openConsumer(String destination, ConsumeSettings settings,
                                    Consumer<TransportMessage> handler);
    default TransportConsumer openConsumerOn(TransportPublisher publisher, String destination,
                                             ConsumeSettings settings, Consumer<TransportMessage> handler);
    default boolean supportsConsume()   { return true; }
    default boolean supportsBothLegs()  { return true; }
}
```

Both ends hand back a close handle: `openConsumer` returns a `TransportConsumer` -- created PAUSED, so it is
subscribed but delivers nothing until `start()` -- and `openPublisher` returns a `TransportPublisher` (a
message sink that is also `AutoCloseable`, so closing it releases the provider's own broker connection). An
x-rod closes both on shutdown.

`supportsConsume()` is `false` for a transport with no receive leg; `supportsBothLegs()` is `false` where one
rod cannot run transmit and receive on the same node. The x-rod checks both BEFORE opening anything, so an
impossible role fails fast instead of running and never delivering.

`TransportMessage` is transport-neutral: a header property bag plus an optional routing/partition `key`
(the entity id, so a partitioning transport keeps per-key order). A provider builds its OWN broker client
from `settings.endpoint()` and reads its vendor knobs from `settings.params()`, so the framework holds no
vendor knowledge. Settings:

- `TransportSettings` — `objectMapper`, `endpoint`, `identity`, `params` (never
  null); `param(key, def)` / `paramLong(key, def)` accessors. A vendor setting (a client id, the JMS
  queue-vs-topic `pubSubDomain` flag, etc.) is NOT a typed field — it is a `transport.params.*` entry
  (see Generic vendor parameters).
- `PublishSettings` adds `poolSize` (async publisher threads; `0` = the single feed worker).
- `ConsumeSettings` adds `concurrency` (listener concurrency) and `selector` (a provider message
  selector; `null` = consume everything).

#### Resolution and auto-config suppression

`TransportProviders.resolve(provider)` instantiates and caches the provider — a bare name →
`pro.mir0n.esquire.tp.<name>.TransportProvider`, a dotted value → a full class name. A new transport plugs
in by a jar on the classpath plus a config value, zero framework change.

Each driver module also ships an `AutoConfigurationImportFilter` (registered via `META-INF/spring.factories`)
that switches off the framework's matching Boot auto-config — the suppression is essential because that
auto-config is `@ConditionalOnClass` the very client classes the provider REQUIRES on the classpath, so a
bare-lib presence would otherwise wire the app's shared broker. The x-rod owns its own client instead:

| Module | Filters |
|---|---|
| `tp-activemq` | `ActiveMQAutoConfiguration` + `JmsAutoConfiguration` |
| `tp-kafka` | `KafkaAutoConfiguration` |
| `tp-redis` | `RedisAutoConfiguration` + `RedisReactiveAutoConfiguration` |

`tp-sqns` and `tp-kinesis` ship no such filter: the AWS SDK brings no Boot auto-config, so there is nothing
that could reach for a shared client behind the x-rod's back.

Those two are also **attached at deployment rather than built in**: no service module depends on them, and no
service image carries a byte of the AWS client. A deployment that wants them mounts the driver jar plus its
dependency folder and names both on `loader.path`. So the same image runs with or without AWS, and a
non-AWS deployment carries none of it. The consequence to know: an attached driver may only call framework
API the SHIPPED image already has, since the image bakes its own `esquire-messaging` jar.

#### Generic vendor parameters

Every `transport.params.*` key reaches the driver verbatim (dotted keys preserved). Each driver applies
the opaque map its own native way — there is no per-key framework code. The convention keys a driver does
interpret are noted per driver below.

A vendor **connection** setting — a client id, a timeout, a prefetch — is set the same way: no typed field,
just a `transport.params.*` entry (`jms.clientID` for ActiveMQ, `client.id` for Kafka). A param value may
also reference the leg's **runtime identity** with the tokens `${rod-id}` / `${bus-id}` / `${slot-id}`,
resolved against the leg's `BusIdentity` when the transport settings are built (`BusIdentity.expandTokens`) —
the same way for a single-destination leg and an R&R node. So config can bind a vendor knob to the
per-instance id with no code:

```yaml
transport:
  params:
    jms.clientID: ${rod-id}     # ActiveMQ -> e.g. enyman.0   (client.id: ${rod-id} for Kafka)
```

**Where a vendor has more than one place to put a setting.** ActiveMQ parses one broker URI, Kafka takes one
config map, Lettuce takes one `redis://` URI -- so "hand the whole group over" has a single destination. AWS
does not work that way: a setting belongs to the SDK client, or to CreateQueue, or to CreateTopic, or to
Subscribe, or to the stream. The AWS drivers therefore read a **prefix** that names the call, and the key
after it is the vendor's own name, passed on with no per-key code:

| prefix | goes to | example |
|---|---|---|
| `client.` | the SDK client override | `client.apiCallTimeout: 5000` |
| `queue.` | SQS `CreateQueue` attributes | `queue.VisibilityTimeout: 60` |
| `topic.` | SNS `CreateTopic` attributes | `topic.DisplayName: esquire` |
| `subscription.` | SNS `Subscribe` attributes | `subscription.RawMessageDelivery: true` |
| `stream.` | the Kinesis stream settings | `stream.RetentionPeriodHours: 48` |

A bare key is one the driver owns (`region`, `route-by`, `partition-by`, `iterator-type`, `poll-millis`,
`limit`, `wait-seconds`, `batch-size`, `noLocal`). A key that is neither is **refused when the leg opens**:
it has no call to go to, and dropping it in silence is how a leg ends up running without a setting the
topology says it has.

#### Dual-leg on one connection (broadcast own-exclusion)

A broadcast CLIENT both transmits and receives on the SAME topic, and it can run both legs over ONE broker
connection: the x-rod opens its publisher leg, then ADDs the consumer onto that same connection via
`ITransportProvider.openConsumerOn(publisher, ...)` (the default falls back to a separate `openConsumer`, so a
transport with no shared-connection notion is unaffected; `supportsBothLegs()` advertises the capability). On
one shared connection the broker can drop the connection's OWN publications -- the `noLocal` semantic -- so the
receive leg sees only OTHER instances' messages. It is opted in per leg with the `transport.params.noLocal` key
(a convention param; `tp-activemq` turns it into `pubSubNoLocal` on the listener). With TWO separate connections
the broker cannot see a connection's own sends, so the x-rod excludes its own in code instead (it compares a
received event's `rodId()` to the leg's own `rodId()`). R&R never takes this path: its two legs live on
different nodes (request vs response), so it keeps two connections and never sets `noLocal`.

A receive leg can also carry a broker-side **subscription selector**: `setWorker(subscription, worker)` narrows
what the leg consumes to the caller's predicate (e.g. `EventType = 'I'`) -- the own-exclusion stays the
transport's `noLocal`, NOT folded into the subscription. It is a plain selector (not a durable subscription),
and the consumer is re-opened only when the selector changes. Only the single-node broadcast `XRod` applies it;
an R&R rod (which already selects by rod-id / slot-id) warns and ignores it.

#### When the vendor filters nothing

A broker does both of those jobs itself. SNS, SQS and Kinesis do neither: every reader of a queue or a shard
gets everything on it. So the framework carries the two filters, in `messaging.transport`, and a driver whose
vendor has no equivalent composes them on the receive side:

```java
Consumer<TransportMessage> receiver = SelectingReceiver.wrap(handler, settings.selector());
receiver = OwnExcluding.wrap(receiver, ownRodId, noLocal);
```

| filter | the question it answers | where it comes from |
|---|---|---|
| `OwnExcluding` | did THIS rod publish it? | the topology -- `noLocal: true` -- plus the leg's own rod-id |
| `SelectingReceiver` | did this consumer ask for it? | the application -- `IXRod.setWorker(subscription, worker)` |

They are **two filters, not one**, because they answer two different questions and every consumer brings its
own subscription while own-exclusion is the same question for all of them. `SelectingReceiver` reads the same
selector grammar a broker would (`FIELD = 'v'`, `<>`, `!=`, `IN`, `NOT IN`, joined by `AND`) over the header
bag, and REFUSES a selector it cannot read when the leg opens -- a filter that silently passes everything is
worse than one that will not start.

#### `tp-activemq` (queue / topic)

- **Publisher** — `ActiveMQConnectionFactory(brokerUrl)` where every `params` entry is appended to the
  broker URI verbatim; a `CachingConnectionFactory` (`sessionCacheSize = poolSize` and `useAsyncSend = true`
  when `poolSize > 0`); a `JmsTemplate` whose `pubSubDomain` is set from the `pubSubDomain` param
  (`true` = topic, absent = queue). Each send
  copies the headers, adds `ApplMsgID` (UUID) + `SendingTime` (now), and writes a **properties-only**
  message (`session.createMessage()`, no body; every header → a JMS property). `close()` destroys the
  caching connection factory.
- **Consumer** — a `DefaultMessageListenerContainer` on the destination (`pubSubDomain` from the
  `pubSubDomain` param, `messageSelector = selector` if set, `concurrentConsumers = concurrency` if `> 0`);
  the listener lifts EVERY JMS property back into the header map. `openConsumerOn` reuses the publisher's
  `CachingConnectionFactory` (one connection, both legs) and sets `pubSubNoLocal` when the `noLocal` param is
  on, so the broker drops that connection's own publications.
- **Vendor params** — ANY `transport.params.*` is appended to the broker URI; ActiveMQ parses its own URI
  options: `jms.*` on the factory (e.g. `jms.clientID`, `jms.useAsyncSend`, `jms.prefetchPolicy.queuePrefetch`,
  `jms.redeliveryPolicy.maximumRedeliveries`), `transport.*` on the wire (e.g. `transport.connectTimeout`),
  `nested.*`, `wireFormat.*`. No per-key code. The one exception is `pubSubDomain`: it is a
  `setPubSubDomain(...)` call on the template / listener container, not a URI option, so `tp-activemq`
  reads it and excludes it from the URI append.
- **Acknowledge mode and broker redelivery** — the consumer listener runs `AUTO_ACKNOWLEDGE` (the
  `DefaultMessageListenerContainer` default; not transacted): a message is acknowledged on delivery and an
  apply failure is caught-and-logged, so the framework never NACKs or rolls a message back to the broker.
  ActiveMQ's `jms.redeliveryPolicy.maximumRedeliveries` (a settable URI option, above) is therefore INERT
  under this ack mode — broker-side redelivery only fires for a transacted / `CLIENT_ACKNOWLEDGE` consumer
  that can roll a message back. This is deliberate: the bus carries its OWN, transport-agnostic resilience —
  producer **send-retry** (holds + re-sends the failed publish) and consumer **reconcile** (the bizTree
  night-watch / Taijitu anti-entropy heals cache drift; the DB is the source of truth) — rather than
  coupling delivery guarantees to an ActiveMQ-only broker feature that Kafka (offsets) and Redis Streams (a
  retained log) do not share. Turning broker redelivery ON (a transacted consumer + a DLQ destination) would
  add at-least-once durability for a failed apply, but at the cost of requiring **idempotent consumers**
  (a consumer that dedups on its own key) and vendor-specific config — a trade, not a strict
  improvement. The `ChangeNo` on the header is such a key: it names a version of a row, so a receiver that
  compares it against what it already applied is idempotent whatever the transport does — which is how the
  bizTree cache and the audit log both behave.

#### `tp-kafka` (topic)

- **Publisher** — a `KafkaTemplate<String,String>` to `bootstrap = endpoint`; the record `key` is
  `TransportMessage.key` (per-key partition order), the value is the headers serialized to JSON. The
  producer config is `params` applied VERBATIM (e.g. `acks`, `linger.ms`, `batch.size`, `compression.type`,
  `max.in.flight.requests.per.connection`, `client.id`), then the essentials are applied LAST so they cannot
  be broken: bootstrap, String key/value serializers. `close()` destroys the producer factory.
- **Consumer** — a `ConcurrentMessageListenerContainer` (`setConcurrency(concurrency)`); the config is
  `params` verbatim (e.g. `max.poll.records`, `fetch.min.bytes`, `client.id`) with essentials last:
  bootstrap, `group.id`, String deserializers, `auto.offset.reset = earliest`.
- **Convention key** — `transport.params.group-id` is the consumer group id (a default applies when
  unset); it maps to `group.id` and is removed from the raw producer/consumer config.

#### `tp-redis` (stream — producer-only)

- **`supportsConsume()` is `false`** — the stream IS the append-only log (read with `XRANGE` /
  `XREVRANGE`); `openConsumer` throws. The x-rod's consume leg is skipped.
- **Publisher** — a `StringRedisTemplate` over a Lettuce connection; each event is `XADD`ed as a
  `MapRecord` of the header bag (every header stringified, nulls omitted; `ApplMsgID` + `SendingTime`
  added) keyed by the destination stream. `close()` destroys the Lettuce connection factory.
- **Convention key** — `transport.params.max-len` (a long; `0` = unbounded) caps the stream via
  `XADD … MAXLEN ~ <n>` (approximate trimming). It is an XADD option, NOT a connection param.
- **Connection params** — every `params` entry EXCEPT `max-len` is appended to the `redis://` URI as a
  connection option; Lettuce's `RedisURI` parses them (database / timeout / …); host / port / password
  come from the endpoint authority.

#### `tp-sqs` (queue)

- **Publisher** -- an `SqsClient` built from `region` (absent = the SDK default chain) with the endpoint
  overridden only when the topology gives one, which is the LocalStack case. Credentials always come from the
  SDK default credential chain, so none is ever written into a file. Each send resolves the queue from the
  message, creates it if it is not there, and sends the header bag as the message BODY: SQS allows at most ten
  message attributes and the bag carries about twenty.
- **Consumer** -- a long-poll loop per `concurrency` thread (`wait-seconds`, default 20 = the SQS maximum;
  `batch-size`, default 10 = the maximum per receive). The **delete is the acknowledgement** and happens only
  after the handler returned, so a message whose handling threw stays hidden for the visibility timeout and is
  delivered again -- the only redelivery SQS offers.
- **Convention key -- `route-by`.** SQS has no message selector, and R&R needs one. So the filter becomes a
  DESTINATION: the node names the header that splits the queue, a publisher takes the value off the message it
  is sending, and a consumer takes it off its own identity. `RodID` gives a queue per instance, `SlotID` a
  queue per slot. The server x-rod already echoes the requester's rod-id onto the reply, so a reply carries the
  queue it belongs in and no framework change was needed.
- **A queue that disappears comes back.** Both legs handle `QueueDoesNotExistException`: the consumer clears
  its cached URL and makes the queue again on the next turn, the publisher evicts and retries the send once.

#### `tp-sns` (topic, with a queue per consumer)

- **SNS delivers into ANOTHER AWS service; it is not a place a message sits.** A subscription names a
  protocol -- `sqs`, `lambda`, `email`, `http`/`https`, `sms`, `firehose` -- and on publish SNS hands the
  message to each subscribed endpoint. There is no receive API: nothing an application runs can read from a
  topic, and a subscription is an address, not a consumer that connects and waits.
- **So the choice of protocol IS the design.** `sqs` is the one that waits to be pulled, which is what a rod
  needs, so a consuming leg owns a QUEUE of its own, subscribed to the topic and named from its rod-id -- and
  every instance gets the whole broadcast instead of instances competing for one copy. The topology declares
  the topic and nothing else; the driver makes the queues.
- **Publisher** -- `CreateTopic` (which returns the existing topic for a name already taken), then `Publish`
  with the bag as the body and the rod-id also as a message attribute.
- **Consumer** -- the same long-poll loop `tp-sqs` uses, over the leg's own queue, with the two receive
  filters above in front of the handler.
- **Wiring a queue onto a topic** takes three things, and all three are set every time a queue is made, so a
  queue that had to be re-made comes back WIRED rather than merely present: a **queue policy** allowing that
  topic to write (SNS is a different service and a queue refuses it until told otherwise -- LocalStack lets it
  through, real AWS does not); **raw message delivery**, so the body stays the header bag; and the **filter
  policy cleared**, because `Subscribe` applies attributes only when it CREATES a subscription, so a policy an
  earlier deployment left behind would go on dropping messages nobody asked it to drop.
- No confirmation token is involved: `http`, `https` and `email` subscriptions need one, an `sqs` subscription
  does not -- the queue policy takes its place.

#### `tp-kinesis` (stream -- both legs)

- **Publisher** -- `CreateStream` on-demand (no shard count to choose; an idle stream costs nothing to keep),
  waits for ACTIVE, then one `PutRecord` per event with the header bag as the record data. The stream IS what
  is read back, so what is written is what is read.
- **Consumer** -- shard discovery at `start()`, then ONE poll thread per shard running `GetRecords`. A stream
  keeps nobody's position, so the leg holds its own, in memory. On a restart it begins where `iterator-type`
  says: `TRIM_HORIZON` re-reads the retained window, `LATEST` takes only what arrives next.
- **Read `partition` the Kinesis way, not the Kafka way.** The two vendors use the word for different
  things, and `tp-kafka` is two sections above: Kafka's **partition** is Kinesis's **shard** (the real log
  resource), while Kinesis's **partition key** is Kafka's **key** (the routing input on a record). A shard
  owns a contiguous range of a 128-bit hash space and a key is hashed into it, so the mapping is many-to-one:
  a key buys ORDER within itself, never a channel of its own.
- **Convention key -- `partition-by`, and its ABSENCE is the important case.** Kinesis keeps order only
  WITHIN a partition key and gives each shard its own reader, so any key that varies is a decision to give up
  total order. Absent means **FIFO**: every record under one key, one shard, one ordered sequence. Naming a
  header is opting IN to spreading, and is only safe where records do not depend on one another -- the audit
  log is such a case (`partition-by: EntityID` keeps each entity's history ordered and spreads the load); a
  broadcast that BUILDS something is not, because a parent must be applied before its child.
- **FIFO is only half the job.** Ordering the wire buys nothing if the far end applies four records at once.
  Kinesis hands over a whole `GetRecords` batch, so an ordered bus also needs `receiver-pool.size: 1`.
- **`poll-millis` is the delivery latency.** Every consumer here PULLS -- nothing is pushed into a service --
  but there are two kinds of pull, and the difference is the whole gap. SQS `ReceiveMessage` takes a wait time
  and BLOCKS on the server, returning the moment a message arrives: a pull that costs milliseconds. Kinesis
  `GetRecords` answers at once, empty or not, so the consumer must pace itself and the interval IS the
  latency. `GetRecords` is capped at five calls a second per shard, so **200ms is the floor** and is the
  default. Real consequence: a client that writes and immediately reads back can lose that race.

### Logging

Each message crossing a leg is logged once, by the framework, on that leg's `msg.<bus-id>.<slot-id>`
logger — the **message log**, wrapped in a small `MsgAudit` module built from the leg identity (a leg with no
bus-id — a test / disabled / in-process leg — gets none, and every call is a no-op). The transmit leg logs
`TX` at the send OUTCOME (the message actually went out), the receive leg logs `RX`:

```
<TX|RX> | <msgType> | <op> | <kind> | <entityId> | <subId> | <changeNo> | <rodId> | <requestId>
```

`changeNo` prints as `-` when the message carries none — a session message, or any event with no row behind
it. **Absent is not zero**, and the line says so rather than inventing a number.

A failed dispatch logs a `TX-ERR` line with the CAUSE (the transport exception), one per failed attempt:

```
TX-ERR | <msgType> | <op> | <kind> | <entityId> | <subId> | <rodId> | <requestId> | <error>
```

The producer send-retry sublayer's hold / recover / drop trail rides the SAME `msg` channel, so a broker
outage reads end to end on one logger. **Session (alive-protocol) traffic is gated at `DEBUG`** — an
application message logs at `INFO`, a heartbeat / `TestRequest` only when the `msg` logger is at `DEBUG` — so
the heartbeat noise silences separately from the data trail. The `msg` logback logger is `additivity = false`,
so the trail goes to the per-service msg file only, never stdout; production may set its level OFF.
(`XRodInfo` logs a richer full-event line led by its directive, through the same `MsgAudit`.)

### Health

Each bus reports its connection health to the service's `/actuator/health`, so a broker outage is visible to
k8s probes instead of silently dropping traffic. A bus's health is the **worse of two sources**
(`TransportHealth.worst`), each used when applicable -- the always-on TRANSPORT indicator and the opt-in producer
session sublayers (the alive keep-alive and the send-retry hold signal, both **OFF by default**):

- **The transport indicator (always on, the default source).** Each leg reports the connection health its vendor
  client already exposes -- no extra traffic: ActiveMQ a `TransportListener` (`interrupted -> DOWN` /
  `resumed -> UP`) + send outcome; Redis (Lettuce) and Kafka the send outcome + client keepalive / group
  heartbeat / metadata; the AWS drivers the outcome of a send or a receive, since an HTTP request/response
  client has no connection to listen on. A transport that cannot observe its connection answers `UNKNOWN`
  (benign -- reported, never fails readiness).

  A leg **seeds `UNKNOWN`**, never `UP`. Opening a leg proves that a control-plane call answered -- a topic
  was created, a queue exists -- not that a message can move, and a leg that claims `UP` before anything has
  travelled makes `messaging.transport.up` read 1 on a bus that has never carried a message. Surfaced through `RodPublisher.health()` / `TransportConsumer.health()`, folded across
  the transmit + receive legs. The per-vendor settings + the when-to-enable-alive guidance live in
  `services.configuring.md`.
- **The alive protocol (OPT-IN, `alive: true`, OFF by default)** — the `AliveSession` session sublayer (see
  [Session sublayers](#session-sublayers-and-producer-resilience)), built on the producer leg when enabled. The
  leg marks its last successful send through the send hooks (`onSendSuccess`); when a producing leg is idle its
  `tick` emits a keep-alive — a broadcast leg sends an unsolicited
  `HeartBeat`, a request/response CLIENT sends a `TestRequest` that the SERVER echoes back as a `HeartBeat`. Health
  is the producer leg's timestamp AGE: a send landed within `alive-timeout` -> `UP`, else `DOWN` (and an immediate
  `DOWN` on a send failure when `alive-fail-fast`). Because the keep-alive runs on a cadence, a leg is exercised
  even when the application is quiet — so the signal works the SAME on every transport (ActiveMQ / Kafka / Redis),
  not just where the broker offers a connection callback. The session (`HeartBeat` / `TestRequest`) messages are
  handled internally and never reach the application worker (see Appendix A / `Esquire.MessagingBus.MessageStructure.md`).
- **The send-retry sublayer (OPT-IN, `send-retry: true`)** — its `health()` reads `DOWN` while it is HOLDING a
  stuck send (the broker is down or unreachable), else `UP`. So a leg that runs send-retry WITHOUT the alive
  protocol still reads `DOWN` through a broker outage — a direct "sends are not landing" signal, complementary to
  the transport indicator (which keys off the connection, not whether a send actually lands).
- **`TransportHealth`** (`UP` / `DOWN` / `UNKNOWN`) is the value a leg reports; for a transport-backed x-rod it is
  `worst(transport indicator, the session sublayers' health)` — the sublayer health folds across the stack
  (`AXRod.sessionHealth()`): `AliveSession` contributes the alive metric, `SendRetrySublayer` reads `DOWN` while
  holding a stuck send (else `UP`), and any other sublayer reads `UNKNOWN`-benign.
- **`IXRod.health()`** — an `XRod` reports that worst-of; an in-process / disabled / log-only rod has no broker,
  so it defaults `UP` (an `XRodInProcessKeep` overrides it to its keep-datasource connection — the DB it applies to).
- **`MessagingBus.health()`** is the per-bus map (`busKey -> TransportHealth`) over every built rod.
- **`BusHealthIndicator`** (Actuator) forwards it: **DOWN if any bus is DOWN**; an `UNKNOWN` bus is a detail,
  not a failure (the framework does not fake confidence it lacks). It is registered **programmatically** by the
  per-service lifecycle registrar at `ApplicationReadyEvent` (no `@Bean`; the facade is handed in), and
  contributed to the **readiness** health group — **never liveness**, so a broker outage depools the pod
  (k8s readiness) rather than restarting it.

**The cadence runs from ONE idle ticker per service** (`MessagingBus`, `scheduleWithFixedDelay` — a guaranteed
gap between sweeps), which fires the generic `IXRod.idle()` maintenance step on every rod. The alive heartbeat is
its first tenant (the seam for future per-rod / transport housekeeping), so a service runs one maintenance thread,
not one per rod. The keep-alive `Text` bodies are pre-built (a constant for the unsolicited `HeartBeat`, a filled
template otherwise), so emitting one allocates no map and runs no serializer.

**First-run scope (Quick&Dirty), when alive is enabled:** the alive metric reads the PRODUCER leg only — the
consumer leg's timestamp is ignored, so a broadcast consumer auto-opens a producer leg to self-heartbeat (only
when alive is on; with alive off it is a pure consumer) and a request/response CLIENT's health is "its send went
through", not yet "the reply came back". Detection latency from an actual outage is bounded by
`alive-timeout` (tunable). The round-trip / consumer-leg refinements, and the note that `alive-fail-fast` is a
no-op on ActiveMQ `failover:` (a send queues rather than throwing, so the timeout governs), are recorded in
`Esquire.MessagingBus.ContinuingDev.md`.

The per-vendor transport callback IS the always-on health source (the default): on ActiveMQ a `TransportListener`
flips `transport interrupted -> DOWN` / `resumed -> UP` at the instant of a drop; use the `failover:` endpoint for
auto-reconnect and clean edges. The alive protocol is the OPT-IN active overlay above it -- enable it where the
transport's own signal is not enough (the R&R round-trip, or the idle-no-traffic gap, recommended mainly for
ActiveMQ). `in-process keep` is not a broker — `KeepApplier.health()` pings the keep datasource (a pooled
connection that validates -> UP, else DOWN).

**Role + log-purity guards.** A CLIENT role over a produce-only transport (the XADD-only Redis stream) is a
config error -- `ITransportProvider.supportsBothLegs()` is false for tp-redis, so the rod FAILS FAST at init
(Redis can be a SERVER, never a CLIENT). And when the alive protocol is on over Redis/Kafka, the session messages
are routed to a SEPARATE `<destination>.admin` stream/topic (capped / short-retention), never the data log; on
ActiveMQ the consumer's `isSession()` filter drops them.

A separate single-source indicator (`TransportHealthIndicator`) forwards a standalone `TransportHealth` source
that is not a bus rod -- a consumer service uses it to report an extra downstream (e.g. its own datasource)
beside its broker health.

## Coupling and the separation roadmap

The transport-neutral core is already clean — `MessagingBus` / `BusSlot` / `BusTransport` /
`MessagingBusCatalog`, the `ITransportProvider` SPI and its drivers, the `IXRod` substrate, and
`TransportMessage`. What still carries host-application shape: `RodEvent` is a change-record with
application-specific fields (an operation, a kind, identity, tracing) rather than a fully generic
envelope, and an application producer bridge reads an application source object + request context.
The planned refactoring (later) extracts a transport-neutral relayed-message and substrate and leaves the
application-shaped pieces as adapters on top — the application **on top of** the Messaging Bus, the bus
reusable on its own.

---

## Appendix A — Message Structure

An x-rod relays a `RodEvent`; `RodEventCodec` maps it to/from the wire envelope — header properties plus the
body in a single `Text` JSON field. Per-send envelope meta (`ApplMsgID`, `SendingTime`) is added by the
driver at publish. The relayed event:

```java
record RodEvent(
        // identity: the row this change touched, plus WHICH change of it
        Op op, int kind, String entityId, String subId, Long changeNo,
        // header
        long actionTime, String correlationId, String requestId, String uid, String rodId, String msgType,
        // payload
        Map<String,Object> body,
        // optional tail: stamped by the ENGINE, never by a producer
        String bodyText, String applMsgId, String traceparent
) { enum Op { CREATE, UPDATE, DELETE, UPDATE_PATH } }
```

`op` maps to the wire `EventType` code via `opCode()` (`I` / `U` / `D` / `X`) -- the same letters the `*_log` action column carries (`X` never reaches it).

The tail is filled in `AXRod` on the send path: `traceparent` from the outbound tracer, then `applMsgId` —
a UUID, set only when the event does not already carry one, so every re-send of a held event keeps the same
id. `bodyText` is a ready-made JSON body (a session message rides one); `RodEventCodec` writes it straight
to `Text` when present, and serializes the `body` map otherwise.

### Wire field registry (FIX-JSON)

The codec writes these header properties (the JMS property name = the JSON field name); the body rides as
`Text`. `RodID` is omitted when blank; `TestReqID` echoes `RequestID` to keep the wire shape. The field
names, msg-type and event-type values are defined once in `messaging.BusConstants` (the bus framework's own
wire constants — the non-wire application constants live in `common.EsqConstants`).

| Field | FIX tag | Source | Role |
|---|---:|---|---|
| `SchemaVersion` | `50001` | fixed `1` | protocol version |
| `BusID` | `50002` | leg identity | the bus |
| `SlotID` | `50003` | leg identity | the slot (leg) |
| `RodID` | `50004` | event, else leg | originating instance id; the R&R reply selector |
| `MsgType` | `35` | event | the message type (the application's vocabulary) |
| `MessageEncoding` | `347` | fixed `JSON` | body encoding |
| `EventType` | `50005` | `op` | `I` / `U` / `D` / `X` |
| `EntityKind` | `50006` | event | the change's kind (routing) |
| `EntityID` | `50007` | event | the changed entity id |
| `SubID` | `50011` | event | sub-row discriminator (else null) |
| `ChangeNo` | `50015` | event | which version of the row — C/U/D carry the entity number, X the path number |
| `ActionTime` | `50013` | event | epoch-ms stamped at the producer |
| `CorrelationID` | `50009` | event | cross-service correlation id |
| `RequestID` | `50008` | event | request trace id / correlation key |
| `TestReqID` | `112` | `= RequestID` | echo, retained for wire shape |
| `Uid` | `50012` | event | the acting user id |
| `Text` | `58` | body | the body, JSON |
| `TraceParent` | `50014` | event | W3C trace context, so a consumer's work nests in the originating trace |
| `ApplMsgID` | `1181` | driver | unique per-send message id |
| `SendingTime` | `52` | driver | per-send timestamp |

**`SchemaVersion` stays at `1` while fields are added.** The codec rejects a *different* version but
tolerates an *absent* field, so an older message simply arrives without the newer ones. Bumping the version
would be a hard cut-over with no rolling upgrade — adding an optional field does not need one.

### Per-transport encoding

The same envelope maps differently per wire: **ActiveMQ** — properties-only JMS message (every header a
JMS property, no body); **Kafka** — a record keyed by `TransportMessage.key`, value = the header bag as
JSON; **Redis** — a stream entry whose fields are the (stringified) header bag.

> The complete per-message-type field semantics are in [`Esquire.MessagingBus.MessageStructure.md`](Esquire.MessagingBus.MessageStructure.md);
> this appendix is the envelope as the bus relays it.

## Appendix B — API Definition

### Facade

```java
class MessagingBus implements AutoCloseable {           // a per-service SINGLETON -- getInstance()
    static MessagingBus getInstance();
    void  init(Environment env, String[] busKeys);      // BUILD every named bus's x-rod, PAUSED (no I/O)
    void  start();                                       // RUN every built x-rod (open transport + run legs)
    IXRod getXRod(String busKey);                        // a built x-rod (THROWS for an unbuilt / undeclared key)
    void  close();                                       // drain in-flight + shut every x-rod down
    Map<String,TransportHealth> health();               // busKey -> connection health (the indicator's source)
}
interface IXRod {
    default void validate(XRodParams params);                                     // fail-fast on the required leg config
    void    configure(XRodParams params, Role role, ObjectMapper objectMapper);   // PREPARE
    void    setWorker(Consumer<RodEvent> worker);                                 // set/reset the receive callback
    default void setWorker(String subscription, Consumer<RodEvent> worker);       // + a broker-side subscription selector (XRod only; R&R warns + ignores)
    void    init(String name, Logger devLog);                                     // CREATE the legs (paused)
    void    start();                                                              // RUN (engine threads + delivery)
    void    shutdown();
    default boolean isEnabled();           // default true; only XRodDisabled is false
    default TransportHealth health();      // default UP; XRod -> worst leg; XRodInProcessKeep -> keep datasource
    default String rodId();                // the leg's <app>.<instanceNo>; null for in-process/disabled/info
    void    transmit(RodEvent event);   // send a pre-built event out the transmit leg
    void    receive(RodEvent event);    // apply an arrived event on the bounded receive pool
}
abstract class AXRod implements IXRod { /* the feed + worker-pool engine; XRod and the in-process XRodInProcess extend it */ }
enum Role { CLIENT, SERVER }
enum TransportHealth { UP, DOWN, UNKNOWN }   // a leg's connection health; worst(a,b) folds two legs
// a leg handle reports it; the indicator forwards it (Actuator, readiness group):
//   TransportPublisher.health() / TransportConsumer.health()  (default UNKNOWN; of(.., healthSupplier) to set)
class BusHealthIndicator implements HealthIndicator { static void register(ApplicationContext, MessagingBus); }   // per-bus, no @Bean
class TransportHealthIndicator implements HealthIndicator { static void register(ApplicationContext, String name, Supplier<TransportHealth>); }   // a single source (e.g. a keep datasource)
// the lifecycle is wired per service by a small MessagingBusLifecycleRegistrar inner class:
//   env-prepared -> init(env, {BUS_KEY_...})   ready -> start() + BusHealthIndicator.register(...)   context-closed -> close()
```

### Transport SPI

```java
interface ITransportProvider {
    TransportPublisher openPublisher(String destination, PublishSettings settings);
    TransportConsumer  openConsumer(String destination, ConsumeSettings settings, Consumer<TransportMessage> handler);
    default TransportConsumer openConsumerOn(TransportPublisher pub, String destination,   // ADD the consumer onto the
                                ConsumeSettings settings, Consumer<TransportMessage> handler);  //   publisher's connection (dual leg)
    default boolean supportsConsume();
    default boolean supportsBothLegs();    // a single rod can run both legs on one node/connection (noLocal own-exclusion)
}
interface TransportPublisher extends Consumer<TransportMessage>, AutoCloseable { }
final class TransportProviders { static ITransportProvider resolve(String provider); }
final class TransportMessage { Map<String,Object> headers(); String key(); }
class TransportSettings { ObjectMapper objectMapper(); String endpoint();
                          BusIdentity identity(); Map<String,String> params(); String param(String k, String def); long paramLong(String k, long def); }
final class PublishSettings extends TransportSettings { int poolSize(); }
final class ConsumeSettings extends TransportSettings { int concurrency(); String selector(); }
record BusIdentity(String busId, String slotId, String rodId) {
    Map<String,String> expandTokens(Map<String,String> params);   // resolve ${rod-id}/${bus-id}/${slot-id} in vendor params
}
```

### Catalog, leg config, event

```java
class MessagingBusCatalog {
    XRodParams resolve(String busId, String slotId);   // throws if absent
    XRodParams find(String busId, String slotId);      // null if absent
    ConsumeLeg consumeLeg(String busId, String slotId, ObjectMapper om);   // whole node, no selector
}
record MessagingBus(String busId, List<BusSlot> slots) {}   // config key `slots`
record BusSlot(String slotId, Map<String,Object> xRod) {}
record BusTransport(String provider, String endpoint, String destination, Map<String,String> params) {
    BusTransport refinedWith(BusNode node);   // base wire + an R&R node (node owns destination/params)
}
record BusNode(String nodeId, String destination, Map<String,String> params) {}
record BusRef(String busId, String slotId, Map<String,Object> xRod) {}
record XRodParams(String busId, String slotId, Map<String,Object> raw) {
    static XRodParams from(Map<String,Object> rawNode);
    XRodParams withBus(String busId, String slotId, String rodIdDefault);
    XRodParams merge(XRodParams override);
    static Map<String,Object> overlayGroups(Map<String,Object> base, Map<String,Object> override);
    BusTransport transport();          // transport.params.* carried verbatim (tokens resolved later, in the settings)
    List<BusNode> nodes();             // the R&R nodes (transport.nodes[*]) as a typed list
    <T> T sub(String key, Class<T> type);
    // getters: rodId / rodClassOr / feedCapacityOr / concurrencyOr / receiverPoolSizeOr / receiverPoolMode / publisherPoolSizeOr / publisherPoolMode
    List<String> SCALARS;
}
record RodEvent(RodEvent.Op op, int kind, String entityId, String subId, Long changeNo,       // identity
                long actionTime, String correlationId, String requestId, String uid,
                String rodId, String msgType,                                                // header
                Map<String,Object> body,                                                     // payload
                String bodyText, String applMsgId, String traceparent)                       // engine-stamped tail
                { enum Op { CREATE, UPDATE, DELETE, UPDATE_PATH } }
final class RodEventCodec { static Map<String,Object> toProps(RodEvent e, ObjectMapper om, BusIdentity id);
                            static RodEvent fromProps(Map<String,Object> p, ObjectMapper om); }
interface RodPublisher extends Consumer<RodEvent>, AutoCloseable { }                 // closeable transmit-leg outbound
final class RodTransportAdapter { static RodPublisher publisher(ITransportProvider p, String dest, PublishSettings s);
                                  static Consumer<TransportMessage> handler(Consumer<RodEvent> rodSink, ObjectMapper om); }
interface IRodEventRepo { void apply(RodEvent event); }                 // optional receive-side dispatch
final class RodEventRepoRegistry { void register(int kind, IRodEventRepo r); Consumer<RodEvent> applier(Logger devLog); }
```

## Appendix C — Class Diagram

![Type map across the framework packages: messaging (the MessagingBus facade, IXRod, RodEvent, IRodEventRepo, RodEventRepoRegistry), messaging.catalog (MessagingBusCatalog, the MessagingBus bus record, BusSlot, BusNode, XRodParams, BusTransport, BusRef, Role), messaging.xrod + messaging.xrod.impl (RodEventCodec/RodPublisher/RodTransportAdapter; AXRod, XRod, XRodRR, XRodInProcess, XRodInfo, XRodDisabled; the session sublayers ISessionSublayer / AliveSession / AliveSessionRR / SendRetrySublayer / SessionSublayerFactory and the MsgAudit module), and messaging.transport (ITransportProvider, TransportProviders, TransportMessage, the settings, BusIdentity, the tp-* drivers). The MessagingBus facade resolves the leg from the catalog and the driver from TransportProviders.](img/messaging-bus-classes.svg)

Resolution is class-name-driven on two axes: `rod-class` selects the x-rod (resolved reflectively by the
`MessagingBus` facade — a bare name under `messaging.xrod.impl`, a dotted value a full class name),
`transport.provider` selects the driver (`TransportProviders`) — so a new x-rod or transport plugs in with no
framework change. The send/receive engine lives in the abstract `AXRod` (extended by `XRod` and the in-process
`XRodInProcess`); an R&R leg's two stops are typed `BusNode`s. The producer leg's session sublayers
(`ISessionSublayer` — `AliveSession` / `AliveSessionRR` and `SendRetrySublayer`, built by
`SessionSublayerFactory`) sit BESIDE the send loop, and the `MsgAudit` module carries the per-leg message log.

## Appendix D — Configuring

Every service loads the catalog:

```yaml
spring:
  config:
    import: "${TOPOLOGY_IMPORT:file:/etc/esquire/topology.yml}"
```

The file is per-environment with concrete hostnames; the import is OPTIONAL (a service may define its
catalog inline under `esquire.messaging-bus`, or not use the bus). A service references a bus by a logical
key, supplies its slot, and may override the leg's `x-rod` per group:

```yaml
esquire:
  <bus-key>:
    messaging-bus:
      bus-id:  ${...}            # a catalog bus-id
      slot-id: ${...}            # the slot this service joins
      x-rod:                     # OPTIONAL: replaces the catalog leg's matching group(s)
        receiver-pool:
          size: ${...}
          mode: ${...}           # platform | virtual | virtual-per-task
```

A service may also extend the catalog with its OWN leg under its own namespace,
`<spring.application.name>.messaging-bus` (the catalog merges this service overlay onto the shared
topology BY ID — a same-id bus/slot replaces, a new one is added). x-rod knobs per leg: `rod-class`, `feed-capacity`,
`concurrency`, the `receiver-pool` and `publisher-pool` groups (`{ size, mode }`), plus the `transport` group and any
x-rod-owned sub-block. Vendor knobs ride
`transport.params.*` and pass through verbatim -- with a prefix naming the call for a vendor that has more
than one place to take a setting (see [Generic vendor parameters](#generic-vendor-parameters)). This appendix is the generic shape; the concrete catalog a
deployment runs (which buses exist + the env that drives them) is documented with that deployment's bus
configuration — for Esquire, in `doc/services.configuring.md`.

## Appendix E — Messaging Bus Topology Catalog

A leg — bus → slot → `x-rod` { knobs + transport }. A single-node bus carries one `destination`; a
request/response bus names two nodes:

```yaml
esquire:
  messaging-bus:
    - bus-id: <bus-id>                   # single node (broadcast)
      slots:
        - slot-id: <slot-id>
          x-rod:
            rod-class: XRod
            receiver-pool:
              size: 4
              mode: platform             # platform | virtual | virtual-per-task
            feed-capacity: 4096
            publisher-pool:
              size: 0
              mode: platform
            concurrency: 1
            transport:
              provider: <name>           # -> pro.mir0n.esquire.tp.<name>.TransportProvider
              endpoint: <broker endpoint>
              destination: <name>
              params:                    # opaque, verbatim to the driver
                pubSubDomain: true       # ActiveMQ only: topic vs queue (absent = queue)
                <vendor-key>: <value>
                # AWS: a prefix names the call the key belongs to
                queue.VisibilityTimeout: 60
                client.apiCallTimeout: 5000

    - bus-id: <bus-id>                   # request/response (two nodes)
      slots:
        - slot-id: <slot-id>
          x-rod:
            rod-class: XRodRR
            transport:
              provider: <name>
              endpoint: <broker endpoint>
              request-node: request
              response-node: response
              nodes:
                - node-id: request
                  destination: <request destination>
                - node-id: response
                  destination: <response destination>
```

A bus declared in the catalog but referenced by no service is inert — an x-rod is built only when a service
names that bus key in its `init(env, {…})` (i.e. it declares a `role` for it and an adapter uses it).

## Appendix F — Design Q&A

The bus's Design Q&A now lives in its own doc: [`Esquire.MessagingBus.Q&A.md`](Esquire.MessagingBus.Q&A.md).

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*
