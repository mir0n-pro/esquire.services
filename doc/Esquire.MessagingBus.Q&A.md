<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>

# Esquire Messaging Bus -- Design Q&A

> **WORKING doc** -- a living log, appended as questions come up. Things that *sound like* issues on a first read
> of the bus, and why each is a deliberate choice rather than a defect. This is the GENERIC bus subframework's
> Q&A; questions about ESQUIRE's application of the bus (its channels, its deployment) live in `Esquire.Q&A.md`.

**Q1. `rod-class` / `director` are class-name strings resolved by `Class.forName` -- no compile-time safety; rename a class and the YAML silently breaks.**

A. That openness is the point. Naming an x-rod or director by class name is an OPEN extension point: any
implementation -- built-in or third-party -- plugs in by naming it in config, with zero framework change and
nothing to register (built-ins get short bare names; only out-of-tree classes need the dotted name). An in-code
`code -> class` registry would force every new implementation to also extend the map -- coupling the framework to
the full set and making third-party x-rods second-class. A rename is just the cost of naming a class in config,
and the reaction is the right one: **fail-fast** (`no x-rod class X on the classpath`), not a silent no-op.

---

**Q2. The bus builds once at `ApplicationEnvironmentPreparedEvent` -- live config refresh (Spring Cloud Config / `@RefreshScope`) won't reach the rods.**

A. By design -- the contract is **restart-over-refresh**. The transport layer is vendor code (ActiveMQ / Kafka /
Redis clients); even re-reading config live cannot guarantee a change reaches already-open connections, so a
hot-refresh would be a half-truth. Making the bus refreshable field-by-field is large cost for no real gain. A
config change is applied by restarting; on a redundant deployment a rolling restart is zero-downtime, and a
clean start from a fresh snapshot always beats a partially-refreshed live state.

---

**Q3. `RodEvent` is a wide, stringly-typed envelope -- ~12 fields (most null per message) plus an untyped `Map` body. No per-message-type safety.**

A. It is a GENERIC envelope on purpose -- the normal shape of a generic wire protocol (the FIX precedent: ~1000
tags, any one message uses a handful, and that is accepted). A typed-per-kind body / sealed hierarchy would
CLOSE the open structure -- a new (or third-party) message type would then need a framework-side subtype instead
of just riding the envelope. A generic envelope is decoded tolerantly by design; the codec's NFE guards +
schema-version gate are the correct, proportionate robustness, not a symptom.

---

**Q4. A leg's mode (in-process vs over a transport) is selected across several config knobs (the `bus-id` it points at + the leg's `role` / `rod-class` / params) that must agree -- an inconsistent combo could misbehave.**

A. It is explicit per-service config, the same kind as the service database (each service's YAML sets
url + driver + dialect, which also must agree) -- not a defect wanting a single derived selector. The knobs are
layered, not independent: the `bus-id` picks a leg, the leg's `rod-class` + params define the behavior, and an
INCOMPLETE combo **fails fast** (an in-process rod that needs a datasource + handler refuses to boot without
them; `XRod` requires a complete transport) rather than booting and misbehaving.

---

**Q5. `setWorker(subscription, worker)` re-opens the receive consumer with the new broker selector but never
`start()`s it. If it is called AFTER the rod has already started, wouldn't the rod silently stop receiving?**

A. It would -- and that is why the API contract is "set the worker (and subscription) BEFORE `start()`". The x-rod
deliberately does NOT support changing a broker subscription at runtime. The consumer is created once at `init()`
(paused); `start()` runs the engine threads and begins delivery; `setWorker(subscription, ...)` is a wiring call
the bus owner makes while the rod is still paused, before the bus is started. It is stated at the switch in code
(`XRod.setWorker(String,...)`: "You need setWorker() before start(). The x-rod does not support changing the
subscription on the fly."), and every caller obeys it -- the one place that uses the subscription variant
(enyMan's `EntityBusAdapter.onPeerCreate`, the peer-CREATE `EventType='I'` selector) wires it during init, before
`MessagingBus.start()`; all other consumers use the plain `setWorker(worker)`, also before start. So the
"called after start" case does not arise in Esquire; there is no live subscription-swap path.

Changing a broker-side selector at runtime is intentionally out of scope: the current need is a single, fixed
narrowing per consumer, decided at wiring time. The planned direction, when a use case actually needs it, is a
CONSUMER-side filter (the worker inspects the event and skips what it does not want) rather than a live re-open of
the broker subscription -- but current Esquire does not require it, so it is not built. If a runtime subscription
change were ever wanted, the correct shape would be an explicit stop / re-open / start, not an in-place
`setWorker`.

---

**Q6. The messaging-bus catalog does no config validation at load -- a typo in a slot's `rod-class` or
`transport.provider` is not caught at `catalog.load()`; it only throws later "at resolve." Shouldn't the catalog
dry-resolve every slot at load so a bad config fails fast?**

A. It already fails fast for everything that matters, and dry-resolving EVERY slot would be actively wrong.

1. **Resolution is eager at startup, not lazy.** `MessagingBus.init()` runs `catalog.load()` -> `buildRods()` ->
   `initRods()` in ONE call. `buildRods` calls `resolveRod(rod-class)` + `rod.validate(params)` for every bus the
   service USES, so a rod-class / provider typo on a used bus fails at boot with a clear message ("no x-rod class
   ... on the classpath") -- moments after `load()`, not lazily at first message. "Throws at resolve, not at
   load" is technically true, but they are the same startup step.

2. **A universal dry-resolve is unsafe.** The SHARED topology catalog holds ALL buses, and each slot's rod-class /
   provider class is on the classpath ONLY of the services that USE it -- `XRodInProcessKeep` ships only in
   dataKeep services; `tp-redis` / `tp-kafka` only where those brokers are used, and `tp-sqns` / `tp-kinesis`
   only where a deployment mounts them. A service correctly bundles only
   its own drivers. Dry-resolving EVERY slot would `Class.forName` a driver / rod the service does not have and
   CRASH at boot for a slot it never touches. The "validate every slot" advice assumes ONE process owns
   every driver; Esquire's per-service classpath does not.

3. The only residual gap -- a typo in a slot NO service uses -- is harmless (nobody builds it) and impossible to
   validate universally (its driver is on no single service's classpath).

So the catalog validates STRUCTURE at load (unique bus / slot / node ids) and defers rod-class / provider
resolution to the per-service build, where it is both correct (only the drivers that service actually has) and
still fail-fast at startup.
