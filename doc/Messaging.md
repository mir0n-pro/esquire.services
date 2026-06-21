# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Messaging Bus topology

This page describes the **message buses that run in Esquire today** — what each one carries, who publishes
and who consumes, and over which transport — together with the **current limitations** of the running
topology.

It is the *instance* view. For how the bus works underneath — the catalog, the x-rod frontend, rod-classes,
and the transport-provider SPI — see [Esquire Messaging Bus](Esquire.MessagingBus.md). For the wire format
of each message, see [Message.Structure.md](Message.Structure.md).

A bus is one logical conversation: a set of channels (a topic, a queue, or a stream) grouped to serve a
single purpose. A service never talks to ActiveMQ / Kafka / Redis directly — it joins a named bus through
the x-rod frontend, and the broker is a per-deployment choice. ActiveMQ is the transport deployed in the
cloud today; the local docker stack also runs Kafka and Redis.

---

## The buses at a glance

| Bus | Pattern | Destination(s) | Producers | Consumers |
|---|---|---|---|---|
| **Entity Broadcast** | broadcast (many-to-many) | topic `esquire.entity.broadcast` | enyMan, pacMan | bizTree, kcMaster |
| **IAM Request/Response** | request / response | queues `esquire.kc.request` + `esquire.kc.response` | enyMan, keySmith (client) → kcMaster (server) → back | — |
| **Audit** | broadcast / stream | `esquire.rod.audit` (queue / topic / stream, per sink) | enyMan, pacMan, keySmith | auKeep (bus sinks only) |

---

## Entity Broadcast Bus

**enyMan** (organizations and users) and **pacMan** (accounts) publish an **entity-update (`UE`)** event on
every committed mutation — create, update, delete, move — to one shared topic. **bizTree** and **kcMaster**
subscribe; more consumers can be added without touching the publishers.

- **bizTree** keeps its in-memory entity tree current, dispatching each `UE` by `(event type, kind)` to a
  create / update / delete / move handler.
- **kcMaster** watches for moves, so a relocated entity's Keycloak path stays in sync.

The event carries raw entity field values only (id, kind, name, path, status, …); each consumer interprets
them, and a field absent from a message is a no-op. The `UE` wire format is in
[Message.Structure.md](Message.Structure.md).

---

## IAM Request/Response Bus

**enyMan** and **keySmith** send identity commands to **kcMaster** — the only service that writes to
Keycloak — and wait for the reply. The bus has two destinations: a **request** queue and a **response**
queue. The requester is the *client* (publishes the request, reads its own reply); kcMaster is the *server*
(the inverse).

- **Request (`URQ`)** — create / update / delete / move a Keycloak user, carrying the login id, email,
  path, roles, and auth state.
- **Response** — **`URS`** on success, **`URR`** on failure (RFC 9457 problem details). The reply is told
  apart by its message type, not by inspecting the body.
- **Reply routing** — each client instance reads only its *own* responses (filtered by its instance id),
  so sharded replicas never steal each other's replies.

kcMaster dispatches each request to a create / update / delete / update-path Keycloak operation. The `URQ` /
`URS` / `URR` wire formats are in [Message.Structure.md](Message.Structure.md).

---

## Audit Bus

The entity producers (**enyMan**, **pacMan**, **keySmith**) post an **audit (`UA`)** event after commit,
off the request thread. Where the audit lands is a per-deployment choice flipped by one environment
variable (`AUDIT_BUS_ID`); the framework default is **off** — a fresh deploy audits nothing.

| Sink (`bus-id`) | Where the audit lands |
|---|---|
| in-process (`audit-b`) | the producing service writes the `*_log` tables itself — no broker |
| ActiveMQ (`audit-c`) | queue → **auKeep** consumes → `*_log` |
| Kafka (`audit-ck`) | topic → **auKeep** consumes → `*_log` |
| Redis stream (`audit-d`) | the stream itself is the append-only log (no consumer) |
| Kafka stream (`audit-dk`) | the topic itself is the log (no consumer) |

Audit is a thin layer on a generic "keep" engine (`esquire-dataKeep`) that applies incoming changes to a
database; **auKeep** is the standalone consumer service for the bus sinks. The full audit model, the `*_log`
schema, and the delivery analysis are in [Esquire.AuditLoggingStack.md](Esquire.AuditLoggingStack.md).

---

## Current limitations

- **ActiveMQ is the only transport in the cloud deployment.** The Kafka and Redis providers are implemented
  and validated on the local docker stack (every audit sink runs there), but the OKE / k8s deployment ships
  ActiveMQ only — the Kafka / Redis sinks (`audit-ck` / `audit-d` / `audit-dk`) are validated options, not
  part of the cloud topology. On k8s a bus pointed at an absent broker resolves to a disabled no-op, not a
  crash.

- **The stream sinks are producer-only.** `audit-d` (Redis) and `audit-dk` (Kafka) write the log but have
  no consumer back into the `*_log` tables — the stream itself *is* the record. Feeding it onward (e.g. into
  a Redis document store) needs an external component such as a Kafka Connect sink, not part of the framework.

- **A misconfigured bus key fails silent, not loud.** A bus key that is not in the catalog resolves to the
  disabled no-op x-rod (`XRodDisabled`) — intended for "audit off", but it also means a *typo'd* key produces
  no error, just silence.

- **Delivery is best-effort per transport; the bus adds no replay or de-duplication.** Idempotency where it
  matters (the audit `*_log`) rests on a unique key in the database, not on the bus. A consumer that is down
  misses broadcasts while it is gone — bizTree's recoverable cache reconciles that on its own (its periodic
  night-watch rebuild), but the bus itself does not redeliver.

- **One broker per bus.** A bus names a single transport endpoint; there is no built-in multi-broker
  failover or partitioned routing at the bus layer.

- **The transport SPI is shaped around the queue / topic / stream model.** Adding a provider that fits that
  shape is a drop-in; a transport with a fundamentally different model may need the SPI to grow.

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*
