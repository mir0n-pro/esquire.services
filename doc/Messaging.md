# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Messaging Architecture

A messaging bus is a logical component — like a wire bus in electronics: a set of individual
channels (topics, queues, streams) that together serve one communication purpose, grouped as a
single unit on architecture diagrams.

Messaging is **transport-agnostic** and unified behind one frontend, the **x-Rod**. A service never
talks to ActiveMQ / Kafka / Redis directly; it asks the **x-Rod manager** for a producer or a consumer
on a logical bus and a role. The buses themselves are defined once, cross-service, in a shared
**bus catalog** (the "topology"). The broker is a deployment choice, expressed entirely in that catalog.

> The vendor-agnostic messaging abstraction promised by the earlier baseline is now delivered. The
> broadcast and request-response patterns remain the contract; the underlying broker is configuration.

---

## Concepts

| Concept | Config key | Meaning |
|---|---|---|
| **bus** | `bus-id` | One logical communication channel (`esquire.entity`, `esquire.kc`, `audit-c`). |
| **slot** | `slot-id` | A leg of the bus a participant joins. (Renamed from "service" to avoid clashing with "microservice".) |
| **node** | `node-id` | A request-response slot splits into a `request` node and a `response` node, each its own destination. Single-node buses (broadcast, audit) have just `destination`. |
| **x-rod** | `x-rod` | The per-slot pod config: `rod-class` + engine knobs (`pool-size`, `feed-capacity`, `virtual-threads`, `publisher-pool-size`, `concurrency`) + the `transport` block. |
| **transport** | `transport` | `provider` + `endpoint` + `destination` + `topic` (true=topic / false=queue) + `params` (opaque per-vendor knobs) + (R&R) `request-node` / `response-node` + a `node` list. |
| **rod-class** | `rod-class` | The pod type (see below). Resolved by name: a bare name is the convention package `pro.mir0n.esquire.messaging.xrod.impl.<name>`; a dotted value is a full class name. |
| **role** | (per ref) | `CLIENT` / `SERVER` / `BROADCAST`. |

### Rod-class — the pod types

| Rod-class | Role in the mesh |
|---|---|
| `XRod` | The standard bus transceiver (a transmit leg, a receive leg, the wire codec). |
| `XRodRR` | Request/response: two nodes, role-routed (the KC bus). |
| `XRodLogDb` | In-process audit pod: writes the `*_log` tables directly (audit option b). FQCN `pro.mir0n.esquire.common.audit.XRodLogDb`. |
| `XRodInfo` | Logs the whole event instead of sending it (the access-violation-log seam). |
| `XRodDisabled` | No-op pod; the default when a bus key is not configured (audit OFF). |

### Transport providers

Per-vendor transport is a pluggable module implementing the `ITransportProvider` SPI, resolved by name
via `TransportProviders` (a bare name maps to `pro.mir0n.esquire.tp.<name>.TransportProvider`; a dotted
value is a full class name):

| Module | Wire form |
|---|---|
| `tp-activemq` | ActiveMQ queue / topic |
| `tp-kafka` | Kafka topic |
| `tp-redis` | Redis stream (producer-only) |

A deployment carries only the transport modules it uses. Each module ships an
`AutoConfigurationImportFilter` (via `META-INF/spring.factories`) that suppresses Spring Boot's matching
auto-config, so a service stays free of any transport coupling. The framework names no vendor: it names
one logical destination, and the provider maps it to its own wire form. Vendor-specific knobs pass through
generically via `transport.params` (e.g. `jms.useAsyncSend`, Kafka `group-id`, Redis `max-len`).

---

## Buses at a Glance

| Bus | Type | Rod-class | Destination(s) | Producers | Consumers |
|---|---|---|---|---|---|
| Entity Broadcast | Broadcast | `XRod` | topic `esquire.entity.broadcast` | enyMan, pacMan | bizTree, kcMaster |
| KC Sync | Request-Response | `XRodRR` | queue `esquire.kc.request` (request) + queue `esquire.kc.response` (response) | enyMan, keySmith (CLIENT) → kcMaster (SERVER) → back | — |
| Audit | Broadcast / stream | `XRod` (bus) / `XRodLogDb` (in-process) | per vendor: queue / topic / stream `esquire.rod.audit` | enyMan, pacMan, keySmith | xxRod (for the bus sinks) |

---

## 1. Entity Broadcast Bus

Entity state changes are published by **enyMan** (orgs and users) and **pacMan** (accounts) to a shared
topic (slot `entity`, role `BROADCAST`). **bizTree** and **kcMaster** are the subscribers; additional
consumers may be added without changing the publishers.

### Message Type: UE — Entity Update

A FIX-JSON envelope of header properties plus the body in the `Text` JSON field (see
[Message.Structure.md](Message.Structure.md)).

| Property | Type | Fixed value | Description |
|---|---|---|---|
| `MsgType` | String | `UE` | Entity broadcast marker |
| `ApplMsgID` | String | — | Unique message ID (UUID) |
| `SendingTime` | String | — | ISO-8601 timestamp |
| `SchemaVersion` | Int | `1` | Protocol version |
| `BusID` | String | `esquire.entity` | Bus namespace |
| `SlotID` | String | `entity` | Bus slot (leg) id |
| `RodID` | String | — | Producer instance id (defaults to the service name) |
| `MessageEncoding` | String | `JSON` | Encoding of Text property |
| `EventType` | String | `C`/`U`/`D`/`X` | Create / Update / Delete / Move |
| `EntityKind` | Int | — | Entity kind code |
| `EntityID` | String | — | Entity primary key |
| `RequestID` | String | — | Originating HTTP request ID (MDC) |
| `CorrelationID` | String | — | Distributed trace correlation ID (MDC) |
| `Text` | String | — | JSON payload (see below) |

### Text Property — JSON Payload

Published raw entity field values only. No interpretation for consumers.

| JSON field | Source | Publishers |
|---|---|---|
| `id` | entity PK | enyMan, pacMan |
| `kind` | entity kind | enyMan, pacMan |
| `name` | entity name | enyMan, pacMan |
| `desc` | entity description | enyMan, pacMan |
| `status` | raw `acc_status` value | pacMan |
| `deleted` | raw `usr_deleted_flg` value | enyMan |
| `parentId` | parent entity PK | enyMan (on CREATE) |
| `path` | `ep_path` value | enyMan (on CREATE, MOVE) |
| `ccy` | account currency | pacMan (on CREATE) |

Fields are omitted when not applicable. Consumers must treat absent fields as no-op. The consumer
receives the body already decoded into a `Map` (the codec parses `Text` once at the bus edge).

### Event Types

| EventType | Trigger |
|---|---|
| `C` | Entity created |
| `U` | Name, description, status, or deleted flag changed |
| `D` | Entity deleted |
| `X` | Entity moved (path changed) |

### bizTree Dispatch

bizTree dispatches incoming UE messages via a handler map keyed by `(EventType, kindBits)`:

| EventType | Kind | Handler |
|---|---|---|
| `U` | org/usr/acct | `UpdateEntityHandler` |
| `D` | org/usr/acct | `DeleteEntityHandler` |
| `X` | org | `MoveOrgHandler` |
| `X` | usr | `MoveUsrHandler` |
| `X` | acct | `MoveAcctHandler` |
| `C` | org | `CreateOrgHandler` |
| `C` | usr | `CreateUsrHandler` |
| `C` | acct | `CreateAcctHandler` |

---

## 2. KC Request/Response Bus

**enyMan** and **keySmith** publish identity commands to kcMaster and consume the reply.
**kcMaster** is the only service that writes to Keycloak directly. The bus is one `XRodRR` slot (`kc`)
with two nodes — `request` and `response`. enyMan / keySmith are `CLIENT` (publish URQ on the request
node, consume URS/URR on the response node); kcMaster is `SERVER` (the inverse).

### Message Type: URQ — Identity Request

Published by a CLIENT to the `request` node (`esquire.kc.request`).

| Property | Description |
|---|---|
| `MsgType` | `URQ` |
| `ApplMsgID` | Unique message ID (UUID) |
| `SendingTime` | ISO-8601 timestamp |
| `SchemaVersion` | `1` |
| `BusID` | `esquire.kc` |
| `SlotID` | `kc` |
| `RodID` | the requester's instance id — echoed in the response, the reply-routing selector |
| `EventType` | `C` / `U` / `D` / `X` (create / update / delete / move) |
| `EntityKind` | Entity kind code |
| `EntityID` | Entity primary key |
| `RequestID` | the request/response correlation key |
| `CorrelationID` | MDC correlation ID |
| `TestReqID` | echo of `RequestID` (retained for wire shape) |
| `MessageEncoding` | `JSON` |
| `Text` | JSON — `KcSyncRequest` payload (see below) |

### URQ Text — KcSyncRequest JSON

```json
{
  "id":             "entity PK",
  "kind":           20,
  "loginId":        "Keycloak username (= loginId)",
  "newLoginId":     "new username on rename (UPDATE only)",
  "email":          "user email",
  "pwdChangeForced": true,
  "tfaMethod":      "totp",
  "connectFlg":     "Y",
  "path":           "/root/org/user",
  "roles":          ["ROLE_A", "ROLE_B"]
}
```

Fields not relevant to the command are omitted or null.

### Message Type: URS — Identity Response (success)

Published by kcMaster (SERVER) to the `response` node (`esquire.kc.response`) on success.

| Property | Description |
|---|---|
| `MsgType` | `URS` |
| `ApplMsgID` | New UUID for this response |
| `SendingTime` | ISO-8601 timestamp |
| `EventType` | Echoed from URQ |
| `EntityKind` | Echoed from URQ |
| `EntityID` | Echoed from URQ |
| `RodID` | Echoed from URQ — the CLIENT selector filters on it |
| `RequestID` | Echoed from URQ |
| `CorrelationID` | Echoed from URQ |
| `TestReqID` | Echoed from URQ |

### Message Type: URR — Identity Request Reject (failure)

Published by kcMaster to the `response` node on failure. The reject is told from the response by its
`MsgType`, not by inspecting the body.

| Property | Description |
|---|---|
| `MsgType` | `URR` |
| `ApplMsgID` | New UUID |
| `RodID` | Echoed from URQ |
| `RequestID` | Echoed from URQ |
| `CorrelationID` | Echoed from URQ |
| `TestReqID` | Echoed from URQ |
| `Error` | RFC 9457 Problem Details — JSON: `{type, title, status, detail}` |

### kcMaster Command Dispatch

| URQ EventType | Handler | Keycloak Operation |
|---|---|---|
| `C` | `handleCreate()` | Create user; set `esq_uid` and `esq_rootpath` JWT attributes |
| `U` | `handleUpdate()` | Update auth state (connectFlg, TFA, password reset) |
| `D` | `handleDelete()` | Delete user by loginId |
| `X` | `handleUpdatePath()` | Update `esq_rootpath` JWT attribute |

### Reply routing (selectors)

`XRodRR` derives the consume selector from the role:

| Role | Selector | Meaning |
|---|---|---|
| `CLIENT` | `RodID = '<rod-id>'` | a CLIENT instance consumes only its own responses |
| `SERVER` | `SlotID = '<slot-id>'` | a SERVER consumes its slot's requests |
| `BROADCAST` | (none) | the whole node |

`rod-id` defaults to the per-instance id `<app>.<instanceNo>` (`spring.application.name` +
`EsqUtils.instanceNo()`, the instance number parsed from the host name — the StatefulSet ordinal in k8s,
a `hostname: <app>-N` in Docker) when unset/blank, so each sharded replica owns a distinct rod-id.

---

## 3. Audit Bus

The entity producers (**enyMan**, **pacMan**, **keySmith**) post **UA** audit events after commit, off
the request thread, to the audit slot (`audit`). The sink is chosen by `bus-id` — one env var,
`ESQUIRE_AUDIT_BUS_ID`, flips it (docker / k8s default `audit-c`, code default `audit-b`):

| bus-id | Rod-class | Sink |
|---|---|---|
| `audit-b` | `XRodLogDb` | in-process write to the `*_log` tables (service-level leg; its log-db datasource is service-specific) |
| `audit-c` | `XRod` | ActiveMQ queue → **xxRod** consumes → `*_log` |
| `audit-ck` | `XRod` | Kafka topic → **xxRod** consumes → `*_log` |
| `audit-d` | `XRod` | Redis stream IS the append-only log (producer-only, no consumer) |
| `audit-dk` | `XRod` | Kafka topic IS the log (producer-only, no consumer) |

See [Esquire.AuditLoggingStack.md](Esquire.AuditLoggingStack.md) for the full audit model, the `*_log`
schema, and the delivery-semantics analysis. The UA wire message is in
[Message.Structure.md](Message.Structure.md).

---

## Configuration Reference

The bus catalog (the topology) is defined once and loaded by every service:

```yaml
spring:
  config:
    import: "${ESQUIRE_TOPOLOGY_IMPORT:file:/etc/esquire/topology.yml}"
```

Docker bind-mounts `compose/topology/esquire-topology.yml`; k8s mounts the `esquire-topology` ConfigMap
(chart `k8s/charts/esquire-topology`). The file is per-environment with concrete hostnames (no `${}`);
the import is required (fail-fast). A bus in the catalog (an example slot):

```yaml
esquire:
  messaging-bus:
    - bus-id: esquire.kc
      slot:
        - slot-id: kc
          x-rod:
            rod-class: XRodRR
            pool-size: 4
            transport:
              provider: activemq
              endpoint: tcp://activemq:61616
              topic: false
              request-node: request
              response-node: response
              node:
                - node-id: request
                  destination: esquire.kc.request
                - node-id: response
                  destination: esquire.kc.response
```

A service references a bus by a logical key, supplying its slot, role (implicit per call) and any
per-service knob overrides:

```yaml
esquire:
  kc-bus:
    messaging-bus:
      bus-id:  ${KC_BUS_ID:esquire.kc}
      slot-id: ${KC_SERVICE_ID:kc}
      x-rod:
        pool-size: ${ENYMAN_KC_RESPONSE_POOL_SIZE:2}
  entity-bus:
    messaging-bus:
      bus-id:  ${ENTITY_BUS_ID:esquire.entity}
      slot-id: ${ENTITY_SERVICE_ID:entity}
  audit-bus:
    messaging-bus:
      bus-id:  ${ESQUIRE_AUDIT_BUS_ID:audit-b}
      slot-id: ${ESQUIRE_AUDIT_SERVICE_ID:audit}
```

The logical keys are `entity-bus`, `kc-bus`, `audit-bus`; the `bus-id` / `slot-id` values are
env-overridable. A service may also extend the catalog with its OWN leg under
`esquire.<spring.application.name>-messaging-bus` (the catalog unions the shared topology with this
service-local key) — this is how a producer carries the audit-(b) in-process leg whose log-db datasource
is service-specific. Per-service config is in [services.configuring.md](services.configuring.md).

---

## Logging Pattern

The message-audit trail is emitted on the x-Rod legs, not in per-class code:

- **`msgLog`** (`msg.<bus-id>.<slot-id>`) — one line per message, on the transmit leg (`TX`) and the
  receive leg (`RX`):
  `<TX|RX> | <msgType> | <op> | <kind> | <entityId> | <subId> | <rodId> | <requestId>`.
  The `msg` logger is `additivity=false` → it goes to the per-service msg file only, not stdout.
- **`devLog`** (`develop.<class>`) — full diagnostics on debug; stacktrace on error.
- **`log`** (console) — one-line operational echo (e.g. the `KC | CREATE | state=...` lines, which are
  not msgLog) on info; message on error (no stacktrace).

MDC (`RequestID`, `CorrelationID`) is set from the event on the receive side and cleared in `finally`.
See [Logging.md](Logging.md).

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*
