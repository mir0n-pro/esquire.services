# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Messaging Architecture

A messaging bus is a logical component — like a wire bus in electronics: a set of individual
channels (topics, queues) that together serve one communication purpose, grouped as a single
unit on architecture diagrams.

Esquire uses two buses over ActiveMQ, each of a different type:

**Broadcast bus** — one-to-many; a topic is the natural wire. Publishers send without knowing
who listens. Consumers subscribe independently and durably.

**Request-Response bus** — point-to-point round-trip; requires at least two wires: one queue
carries requests from caller to handler, a second queue carries responses back. The caller
correlates responses by a token sent with the request.

All messages use **properties-only transport** — `session.createMessage()`, no body.
All fields are JMS string properties.

> The current implementation is built on ActiveMQ and the JMS API. A future Esquire milestone
> will introduce a vendor-agnostic messaging bus abstraction — the broadcast and request-response
> patterns defined here will remain the contract; the underlying broker will become a deployment choice.

---

## Buses at a Glance

| Bus | Type | JMS Resources | Producers | Consumers |
|---|---|---|---|---|
| Entity Broadcast | Broadcast | topic `esquire.entity.broadcast` | enyMan, pacMan | bizTree |
| IAM Sync | Request-Response | queue `esquire.kc.request` (requests) + queue `esquire.kc.response` (responses) | keySmith → kcMaster → keySmith | — |

---

## 1. Entity Broadcast Bus

Entity state changes are published by **enyMan** (orgs and users) and **pacMan** (accounts)
to a shared topic. **bizTree** is the primary subscriber; additional consumers may be added
without changing the publishers.

### Message Type: UE — Entity Update

All 14 canonical fields are JMS properties. No message body.

| Property | Type | Fixed value | Description |
|---|---|---|---|
| `MsgType` | String | `UE` | Entity broadcast marker |
| `ApplMsgID` | String | — | Unique message ID (UUID) |
| `SendingTime` | String | — | ISO-8601 timestamp |
| `SchemaVersion` | Int | `1` | Protocol version |
| `BusID` | String | `esquire.entity` | Bus namespace |
| `ServiceID` | String | `entity-update-broadcast` | Producer channel name |
| `CtrlID` | String | — | Producer instance ID (from config) |
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

Fields are omitted when not applicable. Consumers must treat absent fields as no-op.

### Event Types

| EventType | Trigger |
|---|---|
| `C` | Entity created |
| `U` | Name, description, status, or deleted flag changed |
| `D` | Entity deleted |
| `X` | Entity moved (path changed) |

### Subscription Rules

Durable subscriptions are required on the topic. Each consumer must have:
- A stable **clientId** set on `CachingConnectionFactory` directly (`ccf.setClientId()`)
- A stable **subscriptionName** — never changed after first deployment

| Consumer | clientId | subscriptionName |
|---|---|---|
| bizTree | `biztree` | `esquire.entity.broadcast.biztree.primary` |
| enyMan (future) | `enyman` | `esquire.entity.broadcast.enyman.primary` |

### Selector

```
BusID = 'esquire.entity' AND MsgType = 'UE'
```

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

## 2. IAM Request/Response Bus

**keySmith** publishes identity commands to kcMaster asynchronously.
**kcMaster** is the only service that writes to Keycloak directly.

### Message Type: URQ — Identity Request

Published by keySmith to `esquire.kc.request`.

| Property | Description |
|---|---|
| `MsgType` | `URQ` |
| `ApplMsgID` | Unique message ID (UUID) |
| `SendingTime` | ISO-8601 timestamp |
| `SchemaVersion` | `1` |
| `BusID` | `esquire.kc` |
| `CtrlID` | keySmith instance ID (from config) — echoed in response |
| `EventType` | `C` / `U` / `D` / `X` (create / update / delete / move) |
| `EntityKind` | Entity kind code |
| `EntityID` | Entity primary key |
| `RequestID` | MDC request ID |
| `CorrelationID` | MDC correlation ID |
| `TestReqID` | Unique request token for response correlation |
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

Published by kcMaster to `esquire.kc.response` on success.

| Property | Description |
|---|---|
| `MsgType` | `URS` |
| `ApplMsgID` | New UUID for this response |
| `SendingTime` | ISO-8601 timestamp |
| `EventType` | Echoed from URQ |
| `EntityKind` | Echoed from URQ |
| `EntityID` | Echoed from URQ |
| `CtrlID` | Echoed from URQ — used by keySmith selector to filter own responses |
| `RequestID` | Echoed from URQ |
| `CorrelationID` | Echoed from URQ |
| `TestReqID` | Echoed from URQ — used for request/response correlation |

### Message Type: URR — Identity Request Reject (failure)

Published by kcMaster to `esquire.kc.response` on failure.

| Property | Description |
|---|---|
| `MsgType` | `URR` |
| `ApplMsgID` | New UUID |
| `CtrlID` | Echoed from URQ |
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

### keySmith Response Listener Selector

```
CtrlID = '<keysmith.messaging.ctrl-id>'
```

Each keySmith instance filters responses using its own CtrlID — multiple instances
on the same queue do not interfere.

---

## Configuration Reference

```yaml
spring:
  activemq:
    broker-url: tcp://localhost:61616

# enyMan
enyman.messaging.service-id: entity-update-broadcast
enyman.messaging.ctrl-id:    enyman.default
enyman.messaging.consumer.enabled: false   # entity broadcast consumer, off by default

# pacMan
pacman.messaging.service-id: entity-update-broadcast
pacman.messaging.ctrl-id:    pacman.default

# bizTree
biztree.messaging.client-id: biztree       # stable — never change after first deploy
biztree.messaging.consumer.enabled: true

# keySmith
keysmith.messaging.ctrl-id:  keysmith.default   # must be unique per instance

# kcMaster
kcmaster.messaging.client-id: kcmaster     # stable
kcmaster.messaging.ctrl-id:   kcmaster.default
```

---

## Logging Pattern

All JMS publishers and consumers follow a three-tier logging pattern:

- **`msgLog`** (`msg.<class>`) — compact property summary on every send/receive
- **`devLog`** (`develop.<class>`) — full property map on debug; stacktrace on error
- **`log`** (console) — one-line summary on info; message on error (no stacktrace)

MDC (`RequestID`, `CorrelationID`) is set from message properties in every consumer
and cleared in `finally`.

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*
