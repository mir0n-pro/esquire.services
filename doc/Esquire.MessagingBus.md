# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Messaging Bus — Framework

> **Status: DRAFTED (v1.2.8).** The Messaging Bus is a shared module that gives services one uniform way
> to do asynchronous messaging. This document is the **complete framework reference** — the bus model,
> the x-rod engine, the parameter model, the transport-driver SPI and every driver's parameters, and the
> wire message structure — described abstractly, free of any one application's use of it. The concrete
> bus catalog a given deployment runs (which buses exist, their bus configuration) is documented
> separately. The framework is today still partly coupled to its host application; a later refactoring
> completes the separation — see [Coupling and the separation roadmap](#coupling-and-the-separation-roadmap).

---

## What

The Messaging Bus is the single way a service does asynchronous messaging. A service never opens a JMS
connection, a Kafka producer, or a Redis client; it asks one frontend — `XRodManager` — for a producer
or a consumer on a *logical bus* and a *role*, and the framework builds the rest from a declared catalog.

Four ideas make it up:

- **A bus catalog (the topology)** — every bus declared once, across services, in one external file.
  A **bus topology**, where buses are first-class declared infrastructure, in place of the point-to-point
  **service mesh** a microservice fleet usually grows.
- **The x-Rod frontend** — one x-rod type per bus leg (`IXRod`) with two legs, a transmit leg and a
  receive leg; the wiring decides producer / consumer / in-process. ("Rod" = *Relay of Data*.)
- **An open transport-driver SPI** — `ITransportProvider`, one drop-in module per vendor. A deployment
  carries only the drivers it uses; the framework names no vendor.
- **The bus patterns** the substrate supports — **broadcast** (one-to-many) and **request/response**
  (two-node round-trip), plus an **in-process** x-rod for a leg that applies events locally.

![Messaging Bus runtime path: a service asks XRodManager for a producer/consumer; the manager resolves the catalog leg and the x-rod (by rod-class); the x-rod encodes each event through RodEventCodec to a TransportMessage and hands it to the resolved driver, which maps it onto the broker wire.](img/messaging-bus-architecture.svg)

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
| Bus model + catalog | `pro.mir0n.esquire.messaging` — `MessagingBus`, `BusSlot`, `BusTransport`, `BusRef`, `MessagingBusCatalog`, `XRodParams`, `Role` |
| x-rod frontend + x-rods | `messaging.xrod` — `XRodManager`, `IXRod`, `XRods`, `RodEvent`, `RodEventCodec`, `RodPublisher`, `RodTransportAdapter`, `XRodAutoConfiguration` — and `messaging.xrod.impl` — `XRod`, `XRodRR`, `XRodInfo`, `XRodDisabled` |
| Transport SPI | `messaging.transport` — `ITransportProvider`, `TransportProviders`, `TransportMessage`, `TransportPublisher`, `TransportSettings`, `PublishSettings`, `ConsumeSettings`, `BusIdentity` |
| Transport drivers | one module per vendor — `pro.mir0n.esquire.tp.<name>.TransportProvider` + an `AutoConfigurationImportFilter` |

## Terminology

The messaging bus has a small, deliberate vocabulary. The **bus** and **slot** are abstractions for
describing the topology; the **x-rod** and **network node** are the concrete software and the wire.

| Term | Definition |
|---|---|
| **messaging bus** | A logical aggregation of network elements that provides communication between computing services over a network. Two kinds — **broadcast** and **request-response**. An abstraction: a simplification for describing the network topology. |
| **slot** | A part of one messaging bus — an entry point onto the bus for a specific need. A bus *is* a set of slots that share common infrastructure, behavior, and purpose. Abstract, like the bus. |
| **network node** | The transport abstraction exposed by a vendor's (provider's) API — an ActiveMQ queue, a Kafka topic, a Redis stream. The concrete destination on the wire. |
| **x-rod** (short: **rod**) | The transport-level software module *slotted* into a bus slot. It uniformly defines access to the network node(s) and so unifies a vendor's API with the messaging-bus concept — *as a lightning rod is slotted to a castle tower.* In code: an implementation of `IXRod`. |
| **leg** | A bus user — the publish or consume side. A **publisher leg** sends; a **consumer leg** receives. Every communication is a pair of legs. |
| **role** | On a **request-response** bus: **client** (sends requests, receives responses) or **server** (receives requests, sends responses). A **broadcast** bus has no role differentiation — every participant may both send and receive on the same network node. |

**Catalog keys** — how the terms appear in the topology / configuration:

| Key | Term it configures |
|---|---|
| `bus-id` | the **bus** |
| `slot-id` | a **slot** on that bus |
| `node-id` | a **network node** (R&R splits into a `request` and a `response` node; a single-node bus uses `destination`) |
| `rod-class` | which **x-rod** implementation runs (`XRod` / `XRodRR` / `XRodInfo` / `XRodDisabled` / a custom class) |
| `x-rod` | the slot's **x-rod** configuration — engine knobs + the `transport` block |
| `transport` | the **network node** binding: `provider` + `endpoint` + `destination` + `topic` + `params` (vendor knobs) + (R&R) `request-node` / `response-node` + a `node` list |
| `role` | the **role** (`CLIENT` / `SERVER` / `BROADCAST`), passed per `producer()` / `consumer()` call |

## How

The runtime path: a service asks the frontend for a leg; the frontend resolves the leg config from the
catalog and the x-rod by `rod-class`; the x-rod builds its own transport from the resolved driver and runs
its two legs. The rest of this section is that path in detail — the frontend, the parameter model, the
x-rod engine, the x-rod types, the transport SPI and drivers, and the per-leg logging.

### The frontend — `XRodManager`

One bean per service (registered by `XRodAutoConfiguration`, `destroyMethod = close`), so a producer or
consumer class carries no lifecycle wiring. Two calls:

```java
IXRod producer(String busKey, Role role);
IXRod consumer(String busKey, Role role, Consumer<RodEvent> worker);
```

Each call resolves and starts an x-rod:

1. **Resolve the key to a `BusRef`** — `esquire.<busKey>.messaging-bus → {bus-id, slot-id, x-rod?}`.
   A `busKey` that already contains a dot is taken as a catalog `bus-id` directly (no ref).
2. **Resolve the leg params** — `MessagingBusCatalog.find(bus-id, slot-id)` gives the BASE `XRodParams`;
   a service-level `x-rod` on the ref is merged over it (see [the parameter model](#configuration-and-the-parameter-model)). Either source alone works; if NEITHER defines the leg the result is `null`.
3. **Default the rod-id** — `withBus(bus-id, slot-id, instanceId)` folds the identity in and defaults an
   unset/blank `rod-id` to the per-instance id `<app>.<instanceNo>` (`spring.application.name` + the
   instance number, parsed from this pod/container's host name — the StatefulSet ordinal in k8s, a
   `hostname: <app>-N` in Docker), so each sharded replica owns a distinct rod-id and a CLIENT's `RodID`
   selector isolates that instance's responses.
4. **Resolve the x-rod by `rod-class`** — via `XRods.resolve(...)`; a `null` leg (step 2) yields the OFF x-rod
   `XRodDisabled` (a missing leg is a disabled slot, never an error).
5. **`validate(params)`** — fail-fast: each x-rod checks the leg config IT requires (`XRod` a complete
   transport, `XRodRR` the request/response nodes), so a misconfiguration is reported here, not as a late
   no-op. Default is no requirement (the OFF / log-only x-rods).
6. **`configure(params, role, objectMapper)` then `start(name, devLog, worker)`** — `worker == null` →
   producer, non-null → consumer. The x-rod is tracked; `XRodManager.close()` shuts every one down.

The x-rod builds its **own** transport from the leg — the manager re-packs nothing.

### Configuration and the parameter model

#### The catalog

`MessagingBusCatalog` is the UNION of two property sources, concatenated in code:

- `esquire.messaging-bus` — the shared cross-service topology (imported from the one topology file);
- `esquire.<spring.application.name>-messaging-bus` — a service's OWN legs (e.g. a leg whose wire or
  backing store is service-specific).

> They are unioned in code, NOT a single `esquire.messaging-bus` key across two sources: Spring binds a
> list by INDEX, so a higher-precedence source would REPLACE the whole list instead of appending. The
> service-own key carries the app name to stay clear of the `esquire.<bus-key>.messaging-bus` refs.

A leg is named `(bus-id, slot-id)`; the catalog binds it to an `XRodParams`.

#### `XRodParams` — a bound leg

`XRodParams(busId, slotId, raw)`: `raw` is the leg's `x-rod` node **flattened** to dotted keys (so a
nested `transport: { endpoint: … }` reads as `transport.endpoint`). All knobs are read FROM `raw` by name:

- **Scalar knobs** — registered once in `XRodParams.SCALARS`: `rod-id`, `rod-class`, `pool-size`,
  `feed-capacity`, `virtual-threads`, `publisher-pool-size`, `concurrency` (typed getters parse
  String-or-Number).
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

![Parameter resolution: the catalog unions the shared topology with the service-local legs into the base XRodParams; a service-ref x-rod override merges over it per group; withBus folds in the identity and defaults the rod-id to the per-instance id app.instanceNo (the instance number parsed from the host name). For R&R, XRodRR refines the base transport with the request/response node via overlayGroups, keeping provider and endpoint from the base.](img/messaging-bus-params.svg)

#### The three merge levels

An x-rod's effective wire is resolved across three levels, all by the same per-group overlay:

1. **Leg x-rod params** — the scalar knobs + identity (rod-id default = the per-instance id `<app>.<instanceNo>`).
2. **Transport params** — the `transport` group (provider / endpoint / destination / topic / `params`);
   a service-ref override replaces this group whole.
3. **Node params** (R&R only) — for a two-node leg, `XRodRR` refines the base `transport` with the
   request-or-response NODE: the node owns its `destination` and may override any transport scalar or the
   `params` group **except** `provider` / `endpoint` (the base owns the wire).

### The x-rod engine (`AXRod`) and the two legs

The transceiver engine — the feed (transmit leg), the `Semaphore`-bounded worker pool (receive leg), the
message trace, and their lifecycle — lives in the abstract base **`AXRod`**; every x-rod that has a feed
and/or a pool EXTENDS it (`XRod` adds a transport; an in-process sink adds its own writer), rather than
wrapping a copy. `XRod` is the default x-rod: a transmitter/receiver. Lifecycle is four steps — construct
(no-arg, reflectively instantiated), `validate(params)` (fail-fast on the required leg config — see the
frontend), `configure` (PREPARE), `start` (RUN); `shutdown` stops it.

- **`configure(params, role, objectMapper)`** reads the identity (`BusIdentity` = bus-id / slot-id /
  rod-id) and the engine knobs (`feed-capacity` default 4096, `pool-size` default 4, `virtual-threads`).
- **`start(name, devLog, worker)`** wires the legs and opens the transport. `transportBacked = transport
  != null && objectMapper != null` decides the shape:

| worker | transportBacked | shape |
|---|---|---|
| `null` | yes | **producer** — opens a publisher to the leg destination |
| `null` | no | a no-op producer (no transmit leg) |
| set | no | **in-process** — `outbound = this::receive`; the feed loops back to `worker` |
| set | yes | **consumer** — the receive pool applies `worker`; opens the transport consumer |

#### Transmit leg

- **`transmit(event)`** — the single entry point: it puts a pre-built `RodEvent` (already carrying its
  `msgType`) on the feed. The x-rod is a pure relay — it does NOT buffer in a transaction or stamp times.
  The producer builds the event and calls `transmit`; a producer that needs transactional ordering (buffer
  inside the transaction, flush after commit, stamp one time) does that on its own side, then `transmit`s.
- The **feed** is a `BoundedQueueRig<RodEvent>` of depth `feed-capacity`; its single worker `sendOut`
  logs the `TX` message trace then hands the event to the `outbound`.
- The **`outbound`** is the publisher for a direct producer; with `publisher-pool-size > 0` it becomes
  `this::receive` and the publisher is run on the x-rod's own pool (`pool-size = publisher-pool-size`) — i.e.
  **feed → bounded pool → publish**, asynchronous pooled publishing; for an in-process x-rod it is
  `this::receive` looping back to the receive worker.

#### Receive leg

- **`receive(event)`** acquires a permit from a `Semaphore(pool-size)`, logs the `RX` message trace, then
  runs `worker.accept(event)` on the x-rod's **reused worker pool** — a fixed platform pool of `pool-size`
  threads, or one virtual thread per task when `virtual-threads` — releasing the permit when done.
  Concurrency is bounded by `pool-size` (the same pool also runs the publisher in pooled-async mode and the
  writer for an in-process x-rod); a worker failure is logged and isolated. A `receive` with no receive leg
  wired (or before `start`) throws; a late `receive` during shutdown is logged and dropped, not thrown.

#### Lifecycle hooks (overridden by `XRodRR`)

- **`legTransport(produce, role)`** — the effective wire for this leg; base `XRod` is single-node (the
  one `transport`). `XRodRR` overrides it to pick the request/response node.
- **`consumeSelector(role, identity)`** — the receive selector; base returns `null` (the whole node).

`shutdown()` stops delivery first (closes the inbound transport consumer), winds the feed down, then DRAINS
the worker pool (`awaitTermination`) so in-flight applies / async publishes finish, and closes the outbound
transport publisher last — releasing its broker connection.

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
  - the leg's nodes bind to a typed `List<BusNode>` (`transport.node[*]`); `legTransport` selects the
    `BusNode` whose `node-id` matches `transport.request-node` / `transport.response-node`, then refines the
    base transport with it via `BusTransport.refinedWith(node)` — the node owns `destination` / `topic` /
    `params`, the base owns `provider` / `endpoint`. A non-R&R role, or a leg with no such node, falls back
    to the base single transport.
- **`consumeSelector(role, identity)`** — `CLIENT` → `RodID = '<rod-id>'` (an instance consumes only its
  own replies); `SERVER` → `SlotID = '<slot-id>'` (its own service's requests off a possibly-shared node).

#### `XRodInfo` — log-only

A non-sending x-rod: it `log.info`s each event's full content to its `msg.<bus-id>.<slot-id>` logger,
led by a directive from its own `x-rod.info` sub-block (`XRodInfoParams.dir`, default `Skipped`), instead
of transmitting. It logs each event directly — no feed, no pool, no transport: `transmit` / `receive` write
one line (the whole `RodEvent`, led by the directive) to the leg's `msg` logger. A dry-run / kill-switch leg.

#### `XRodDisabled` — OFF

A fully inert `IXRod`: every method a no-op, no config, no transport, `isEnabled()` false. The default
when a bus key resolves to no leg, or set explicitly (`rod-class: XRodDisabled`) to disable a slot. So an
injected `IXRod` is never null.

### The transport SPI and drivers

#### The SPI

```java
interface ITransportProvider {
    TransportPublisher openPublisher(String destination, PublishSettings settings);
    AutoCloseable      openConsumer(String destination, ConsumeSettings settings,
                                    Consumer<TransportMessage> handler);
    default boolean supportsConsume() { return true; }
}
```

Both ends hand back a close handle: `openConsumer` returns an `AutoCloseable` (stop the listener);
`openPublisher` returns a `TransportPublisher` (a message sink that is also `AutoCloseable`, so closing it
releases the provider's own broker connection). An x-rod closes both on shutdown.

`TransportMessage` is transport-neutral: a header property bag plus an optional routing/partition `key`
(the entity id, so a partitioning transport keeps per-key order). A provider builds its OWN broker client
from `settings.endpoint()` and reads its vendor knobs from `settings.params()`, so the framework holds no
vendor knowledge. Settings:

- `TransportSettings` — `objectMapper`, `endpoint`, `topic` (queue vs topic), `identity`, `params` (never
  null); `param(key, def)` / `paramLong(key, def)` accessors. A vendor *connection* setting (a client id,
  etc.) is NOT a typed field — it is a `transport.params.*` entry (see Generic vendor parameters).
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

#### `tp-activemq` (queue / topic)

- **Publisher** — `ActiveMQConnectionFactory(brokerUrl)` where every `params` entry is appended to the
  broker URI verbatim; a `CachingConnectionFactory` (`sessionCacheSize = poolSize` and `useAsyncSend = true`
  when `poolSize > 0`); a `JmsTemplate` with `pubSubDomain = topic`. Each send
  copies the headers, adds `ApplMsgID` (UUID) + `SendingTime` (now), and writes a **properties-only**
  message (`session.createMessage()`, no body; every header → a JMS property). `close()` destroys the
  caching connection factory.
- **Consumer** — a `DefaultMessageListenerContainer` on the destination (`pubSubDomain = topic`,
  `messageSelector = selector` if set, `concurrentConsumers = concurrency` if `> 0`); the listener lifts
  EVERY JMS property back into the header map.
- **Vendor params** — ANY `transport.params.*` is appended to the broker URI; ActiveMQ parses its own URI
  options: `jms.*` on the factory (e.g. `jms.clientID`, `jms.useAsyncSend`, `jms.prefetchPolicy.queuePrefetch`,
  `jms.redeliveryPolicy.maximumRedeliveries`), `transport.*` on the wire (e.g. `transport.connectTimeout`),
  `nested.*`, `wireFormat.*`. No per-key code.

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

### Logging

Each message crossing a leg is logged once, by the framework, on that leg's `msg.<bus-id>.<slot-id>`
logger — the transmit leg logs `TX`, the receive leg logs `RX`:

```
<TX|RX> | <msgType> | <op> | <kind> | <entityId> | <subId> | <rodId> | <requestId>
```

The `msg` logback logger is `additivity = false`, so the trail goes to the per-service msg file only,
never stdout; production may set its level OFF. (`XRodInfo` logs a richer full-event line led by its
directive.)

## Coupling and the separation roadmap

The transport-neutral core is already clean — `MessagingBus` / `BusSlot` / `BusTransport` /
`MessagingBusCatalog`, the `ITransportProvider` SPI and its drivers, the `IXRod` substrate, and
`TransportMessage`. What still carries host-application shape: `RodEvent` is a change-record with
application-specific fields (an operation, a kind, identity, tracing) rather than a fully generic
envelope, and the audit producer `AuditBusBridge` reads an application source object + request context.
The planned refactoring (later) extracts a transport-neutral relayed-message and substrate and leaves the
application-shaped pieces as adapters on top — the application **on top of** the Messaging Bus, the bus
reusable on its own.

---

## Appendix A — Message Structure

An x-rod relays a `RodEvent`; `RodEventCodec` maps it to/from the wire envelope — header properties plus the
body in a single `Text` JSON field. Per-send envelope meta (`ApplMsgID`, `SendingTime`) is added by the
driver at publish. The relayed event:

```java
record RodEvent(Op op, int kind, String entityId, String subId, long actionTime,
                String correlationId, String requestId, String uid, String rodId, String msgType,
                Map<String,Object> body) { enum Op { CREATE, UPDATE, DELETE, UPDATE_PATH } }
```

`op` maps to the wire `EventType` code via `opCode()` (`C` / `U` / `D` / `X`).

### Wire field registry (FIX-JSON)

The codec writes these header properties (the JMS property name = the JSON field name); the body rides as
`Text`. `RodID` is omitted when blank; `TestReqID` echoes `RequestID` to keep the wire shape.

| Field | FIX tag | Source | Role |
|---|---:|---|---|
| `SchemaVersion` | `50001` | fixed `1` | protocol version |
| `BusID` | `50002` | leg identity | the bus |
| `SlotID` | `50003` | leg identity | the slot (leg) |
| `RodID` | `50004` | event, else leg | originating instance id; the R&R reply selector |
| `MsgType` | `35` | event | the message type (the application's vocabulary) |
| `MessageEncoding` | `347` | fixed `JSON` | body encoding |
| `EventType` | `50005` | `op` | `C` / `U` / `D` / `X` |
| `EntityKind` | `50006` | event | the change's kind (routing) |
| `EntityID` | `50007` | event | the changed entity id |
| `SubID` | `50011` | event | sub-row discriminator (else null) |
| `ActionTime` | `50013` | event | epoch-ms stamped at the producer |
| `CorrelationID` | `50009` | event | cross-service correlation id |
| `RequestID` | `50008` | event | request trace id / correlation key |
| `TestReqID` | `112` | `= RequestID` | echo, retained for wire shape |
| `Uid` | `50012` | event | the acting user id |
| `Text` | `58` | body | the body, JSON |
| `ApplMsgID` | `1181` | driver | unique per-send message id |
| `SendingTime` | `52` | driver | per-send timestamp |

### Per-transport encoding

The same envelope maps differently per wire: **ActiveMQ** — properties-only JMS message (every header a
JMS property, no body); **Kafka** — a record keyed by `TransportMessage.key`, value = the header bag as
JSON; **Redis** — a stream entry whose fields are the (stringified) header bag.

> The complete per-message-type field semantics (Request / Response / RequestReject / Entity / Audit) are
> in [`Message.Structure.md`](Message.Structure.md); this appendix is the envelope as the bus relays it.

## Appendix B — API Definition

### Frontend

```java
class XRodManager implements AutoCloseable {        // one bean per service (XRodAutoConfiguration)
    IXRod producer(String busKey, Role role);
    IXRod consumer(String busKey, Role role, Consumer<RodEvent> worker);
    void  close();
}
interface IXRod {
    default void validate(XRodParams params);                                     // fail-fast on the required leg config
    void    configure(XRodParams params, Role role, ObjectMapper objectMapper);   // PREPARE
    void    start(String name, Logger devLog, Consumer<RodEvent> worker);         // RUN
    void    shutdown();
    default boolean isEnabled();        // default true; only XRodDisabled is false
    void    transmit(RodEvent event);   // send a pre-built event out the transmit leg
    void    receive(RodEvent event);    // apply an arrived event on the bounded receive pool
}
abstract class AXRod implements IXRod { /* the feed + worker-pool engine; XRod and an in-process sink extend it */ }
final class XRods { static IXRod resolve(String rodClass); String DEFAULT="XRod"; String DISABLED="XRodDisabled"; }
enum Role { CLIENT, SERVER, BROADCAST }
```

### Transport SPI

```java
interface ITransportProvider {
    TransportPublisher openPublisher(String destination, PublishSettings settings);
    AutoCloseable      openConsumer(String destination, ConsumeSettings settings, Consumer<TransportMessage> handler);
    default boolean supportsConsume();
}
interface TransportPublisher extends Consumer<TransportMessage>, AutoCloseable { }
final class TransportProviders { static ITransportProvider resolve(String provider); }
final class TransportMessage { Map<String,Object> headers(); String key(); }
class TransportSettings { ObjectMapper objectMapper(); String endpoint(); boolean topic();
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
record MessagingBus(String busId, @Name("slot") List<BusSlot> slots) {}   // config key stays `slot`
record BusSlot(String slotId, Map<String,Object> xRod) {}
record BusTransport(String provider, String endpoint, String destination, Boolean topic, Map<String,String> params) {
    BusTransport refinedWith(BusNode node);   // base wire + an R&R node (node owns destination/topic/params)
}
record BusNode(String nodeId, String destination, Boolean topic, Map<String,String> params) {}
record BusRef(String busId, String slotId, Map<String,Object> xRod) {}
record XRodParams(String busId, String slotId, Map<String,Object> raw) {
    static XRodParams from(Map<String,Object> rawNode);
    XRodParams withBus(String busId, String slotId, String rodIdDefault);
    XRodParams merge(XRodParams override);
    static Map<String,Object> overlayGroups(Map<String,Object> base, Map<String,Object> override);
    BusTransport transport();          // transport.params.* carried verbatim (tokens resolved later, in the settings)
    List<BusNode> nodes();             // the R&R nodes (transport.node[*]) as a typed list
    <T> T sub(String key, Class<T> type);
    // scalar getters: rodId / rodClassOr / poolSizeOr / feedCapacityOr / virtualThreadsOrFalse / publisherPoolSizeOr / concurrencyOr
    List<String> SCALARS;
}
record RodEvent(RodEvent.Op op, int kind, String entityId, String subId, long actionTime,
                String correlationId, String requestId, String uid, String rodId, String msgType,
                Map<String,Object> body) { enum Op { CREATE, UPDATE, DELETE, UPDATE_PATH } }
final class RodEventCodec { static Map<String,Object> toProps(RodEvent e, ObjectMapper om, BusIdentity id);
                            static RodEvent fromProps(Map<String,Object> p, ObjectMapper om); }
interface RodPublisher extends Consumer<RodEvent>, AutoCloseable { }                 // closeable transmit-leg outbound
final class RodTransportAdapter { static RodPublisher publisher(ITransportProvider p, String dest, PublishSettings s);
                                  static Consumer<TransportMessage> handler(Consumer<RodEvent> rodSink, ObjectMapper om); }
interface IRodEventRepo { void apply(RodEvent event); }                 // optional receive-side dispatch
final class RodEventRepoRegistry { void register(int kind, IRodEventRepo r); Consumer<RodEvent> applier(Logger devLog); }
```

## Appendix C — Class Diagram

![Type map across the three framework packages: messaging.xrod (XRodManager, IXRod and its x-rods, XRods, RodEvent/RodEventCodec, RodTransportAdapter), messaging (MessagingBusCatalog, MessagingBus, BusSlot, XRodParams, BusTransport, BusRef, Role), and messaging.transport (ITransportProvider, TransportProviders, TransportMessage, the settings, BusIdentity, the tp-* drivers). XRodManager resolves the leg from the catalog and the driver from TransportProviders.](img/messaging-bus-classes.svg)

Resolution is class-name-driven on two axes: `rod-class` selects the x-rod (`XRods`),
`transport.provider` selects the driver (`TransportProviders`) — both by a convention name or a full
class name, so a new x-rod or transport plugs in with no framework change. The send/receive engine lives in
the abstract `AXRod` (extended by `XRod` and any in-process sink); an R&R leg's two stops are typed `BusNode`s.

## Appendix D — Configuring

Every service loads the catalog:

```yaml
spring:
  config:
    import: "${ESQUIRE_TOPOLOGY_IMPORT:file:/etc/esquire/topology.yml}"
```

The file is per-environment with concrete hostnames; the import is required (fail-fast). A service
references a bus by a logical key, supplies its slot, and may override the leg's `x-rod` per group:

```yaml
esquire:
  <bus-key>:
    messaging-bus:
      bus-id:  ${...}            # a catalog bus-id
      slot-id: ${...}            # the slot this service joins
      x-rod:                     # OPTIONAL: replaces the catalog leg's matching group(s)
        pool-size: ${...}
```

A service may also extend the catalog with its OWN leg under
`esquire.<spring.application.name>-messaging-bus` (the catalog unions it with the shared topology). x-rod
knobs per leg: `rod-class`, `pool-size`, `feed-capacity`, `virtual-threads`, `publisher-pool-size`,
`concurrency`, plus the `transport` group and any x-rod-owned sub-block. Vendor knobs ride
`transport.params.*` and pass through verbatim. This appendix is the generic shape; the concrete catalog a
deployment runs (which buses exist + the env that drives them) is documented with that deployment's bus
configuration — for Esquire, in `doc/services.configuring.md`.

## Appendix E — Messaging Bus Topology Catalog

A leg — bus → slot → `x-rod` { knobs + transport }. A single-node bus carries one `destination`; a
request/response bus names two nodes:

```yaml
esquire:
  messaging-bus:
    - bus-id: <bus-id>                   # single node (broadcast)
      slot:
        - slot-id: <slot-id>
          x-rod:
            rod-class: XRod
            pool-size: 4
            feed-capacity: 4096
            virtual-threads: false
            publisher-pool-size: 0
            concurrency: 1
            transport:
              provider: <name>           # -> pro.mir0n.esquire.tp.<name>.TransportProvider
              endpoint: <broker endpoint>
              destination: <name>
              topic: true                # topic vs queue
              params:                    # opaque, verbatim to the driver
                <vendor-key>: <value>

    - bus-id: <bus-id>                   # request/response (two nodes)
      slot:
        - slot-id: <slot-id>
          x-rod:
            rod-class: XRodRR
            transport:
              provider: <name>
              endpoint: <broker endpoint>
              topic: false
              request-node: request
              response-node: response
              node:
                - node-id: request
                  destination: <request destination>
                - node-id: response
                  destination: <response destination>
```

A bus declared in the catalog but referenced by no service is inert — an x-rod is built only when a service
`producer()`s or `consumer()`s a bus key.

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*
