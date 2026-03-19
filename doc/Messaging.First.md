# Messaging First: Entity State Synchronization over ActiveMQ

## 1. Purpose

This document defines the first internal messaging pattern for service-to-service entity state synchronization.

The initial goal is to broadcast entity state changes from one producer service to any number of internal consumer services using an ActiveMQ topic.

This document is the baseline contract for the first messaging implementation.

---

## 2. Scope

Initial scope:

- broker: ActiveMQ
- destination type: JMS Topic
- producer service: `enyMan`
- consumers: any internal service that needs to monitor entity state changes
- authorization: not enabled for the internal JMS service in phase 1
- subscriber type: durable

This pattern is intended for internal backend synchronization only.

---

## 3. Destination

### Topic name

`esquire.entity.broadcast`

This topic is the initial shared broadcast channel for entity state events.

---

## 4. Messaging model

When an entity state changes, the producer publishes one event to the topic.

All interested consumers subscribe to the same topic, but process only relevant messages using JMS selectors based on JMS properties.

### Key principles

- routing and filtering are based on JMS properties
- the message body is JSON text
- the protocol uses **FIX-JSON notation** as the canonical envelope notation
- the same canonical field names are used in both JMS properties and FIX-JSON body
- each canonical field is mapped to a FIX field code
- standard FIX tags are reused where appropriate
- custom application tags are used where needed

### Official FIX reference

- FIX Trading Community, *FIX JSON Encoding*:
  https://www.fixtrading.org/standards/fix-json-encoding/

### Important note

This protocol uses FIX-JSON naming and field-code mapping conventions for internal service messaging.
It does not claim full conformance with every FIX application-layer workflow.

---

## 5. Producer and consumers

### Producer

Initial producer:

- `enyMan`

Producer responsibilities:

- detect committed entity state change
- publish one event to topic `esquire.entity.broadcast`
- populate all required JMS properties
- encode body as JSON text using FIX-JSON notation

### Consumers

Any internal service may consume these events if it needs entity state synchronization.

Typical consumer behavior may include:

- local cache update
- refresh trigger
- projection synchronization
- lifecycle reaction
- downstream processing trigger

Consumers must subscribe durably and should use selectors.

---

## 6. Durable subscription policy

All subscribers must be durable.

### Rationale

Durable subscribers are required because consumers must not miss entity update events during temporary service downtime or restart.

### Rules

Each subscriber must use stable identifiers:

- stable `clientId`
- stable durable `subscriptionName`

These identifiers must not be random and must not change on each restart.

### Recommended naming

#### Client ID

Use the consuming service logical name.

Examples:

- `bizTree`
- `keySmith`
- `pacMan`

#### Subscription name

Use a descriptive stable name tied to filtering intent.

Examples:

- `esquire.entity.broadcast.kind.34`
- `esquire.entity.broadcast.kind.34.35`
- `esquire.entity.broadcast.bizTree.primary`

### Important note

`CtrlID` is event metadata and must not automatically be reused as durable subscriber identity unless it is intentionally stable across restarts.

---

## 7. Field Mapping Registry v1

This protocol uses **FIX-JSON notation** as the canonical message field notation.

### 7.1 FIX-JSON notation and mapping

The entity broadcast protocol is defined using FIX-JSON field notation.

This means:

- message body fields use FIX-JSON semantic names
- JMS properties use the same field names as the FIX-JSON body
- each field is mapped to a FIX field code
- standard FIX tags are reused where the semantic match is appropriate
- custom application tags are used for Esquire-specific fields

Accordingly, each protocol field has one canonical definition consisting of:

- canonical field name
- FIX field code
- type
- required/optional status
- allowed values and semantics

### 7.2 Canonical naming rule

The protocol uses one canonical field name for both transport and body.

Rule:

- **JMS property name = FIX-JSON field name**
- field names use FIX-style semantic notation
- FIX numeric field codes are defined separately in the registry

Examples:

- `ApplMsgID`
- `SendingTime`
- `EntityKind`
- `RequestID`
- `CorrelationID`

This rule eliminates name translation between JMS metadata and JSON body fields.

### 7.3 Mapping goals

The field registry exists to guarantee:

- stable routing via JMS selectors
- stable FIX-JSON encoding
- predictable tracing across services
- compatibility between transport metadata and message body
- future protocol evolution without ambiguity

### 7.4 Tag allocation policy

Standard FIX tags are reused only where the semantic match is clear and stable.

Custom Esquire fields use the reserved application range:

- `50001` to `50099`

This range is reserved for the internal entity broadcast protocol.

### 7.5 Canonical field registry

| Canonical field name | FIX tag | Type | Required | Example | Notes |
|---|---:|---|---|---|---|
| `ApplMsgID` | `1181` | String | yes | `550e8400-e29b-41d4-a716-446655440000` | unique event identifier |
| `SendingTime` | `52` | UTCTimestamp | yes | `2026-03-17T10:15:30Z` | event creation time |
| `SchemaVersion` | `50001` | Int | yes | `1` | protocol schema version |
| `BusID` | `50002` | String | yes | `esquire.entity` | logical event bus |
| `ServiceID` | `50003` | String | yes | `enyMan` | producer service |
| `CtrlID` | `50004` | String | yes | `enyman.instance.id` | producer controller / instance id |
| `MsgType` | `35` | String | yes | `UE` | FIX-style message type |
| `EventType` | `50005` | String | yes | `U` | operation type |
| `EntityKind` | `50006` | Int | yes | `34` | entity kind code |
| `EntityID` | `50007` | String | yes | `1234` | entity identifier |
| `RequestID` | `50008` | String | yes | `req-789` | request trace id |
| `CorrelationID` | `50009` | String | yes | `corr-456` | cross-service correlation id |
| `MessageEncoding` | `347` | String | yes | `JSON` | body encoding |
| `Text` | `58` | Object | yes | `{ "state": "ACTIVE" }` | lightweight payload |

### 7.6 Required baseline values for phase 1

The following values are fixed for the initial implementation:

| Field | Value |
|---|---|
| `SchemaVersion` | `1` |
| `BusID` | `esquire.entity` |
| `ServiceID` | `enyMan` |
| `MsgType` | `UE` |
| `MessageEncoding` | `JSON` |

### 7.7 Authority rule

Some fields exist in both places:

- JMS properties
- FIX-JSON body

This duplication is intentional.

The authority model is:

- JMS properties are authoritative for routing, filtering, and subscriber selectors
- FIX-JSON fields are authoritative for protocol payload completeness
- duplicated values must match exactly

### 7.8 Equality rule

If a field is present both as JMS property and FIX-JSON field, then the values must be identical.

This applies at minimum to:

- `ApplMsgID`
- `SendingTime`
- `SchemaVersion`
- `BusID`
- `ServiceID`
- `CtrlID`
- `MsgType`
- `EventType`
- `EntityKind`
- `EntityID`
- `RequestID`
- `CorrelationID`
- `MessageEncoding`

A mismatch is a contract violation.

### 7.9 Consumer behavior on mismatch

If JMS properties and FIX-JSON values differ, the consumer must treat the message as invalid.

Recommended behavior:

1. log the mismatch
2. reject business processing
3. preserve the message for later recovery or error handling

### 7.10 Tracing fields

Two tracing fields are mandatory.

#### `RequestID`

Purpose:

- identifies the originating request flow
- usually tied to one inbound API request or one unit of work
- may differ across separate requests involving the same entity

Mapping:

- canonical field name: `RequestID`
- FIX tag: `50008`

#### `CorrelationID`

Purpose:

- links related operations and events across services
- may span multiple requests and asynchronous steps
- should survive async boundaries

Mapping:

- canonical field name: `CorrelationID`
- FIX tag: `50009`

### 7.11 Propagation rule

When an event is produced during request processing:

- `RequestID` must be copied from current request context
- `CorrelationID` must be copied from current correlation context

When an event is produced without an inbound request:

- generate a new `RequestID`
- preserve an existing `CorrelationID` if available
- otherwise generate a new `CorrelationID`

### 7.12 FIX-JSON example
```

json { "ApplMsgID": "550e8400-e29b-41d4-a716-446655440000", "SendingTime": "2026-03-17T10:15:30Z", "SchemaVersion": 1, "BusID": "esquire.entity", "ServiceID": "enyMan", "CtrlID": "enyman.instance.id", "MsgType": "UE", "EventType": "U", "EntityKind": 34, "EntityID": 1234, "RequestID": "req-789", "CorrelationID": "corr-456", "MessageEncoding": "JSON", "Text": { "state": "ACTIVE", "shortName": "Sample entity" } }``` 

### 7.13 Equivalent JMS properties example
```

text ApplMsgID=550e8400-e29b-41d4-a716-446655440000 SendingTime=2026-03-17T10:15:30Z SchemaVersion=1 BusID=esquire.entity ServiceID=enyMan CtrlID=enyman.instance.id MsgType=UE EventType=U EntityKind=34 EntityID=1234 RequestID=req-789 CorrelationID=corr-456 MessageEncoding=JSON``` 

### 7.14 Selector rule reminder

Selectors must use JMS properties only.

Because JMS property names are identical to FIX-JSON field names, selectors also use FIX-JSON notation.

Examples:
```

text EntityKind = 34
text BusID = 'esquire.entity' AND EntityKind = 34
text BusID = 'esquire.entity' AND MsgType = 'UE' AND EntityKind = 34``` 

JSON body fields must not be used directly for selector logic.

### 7.15 Contract freeze for v1

The field registry in this section is the canonical v1 field registry.

Any change to:

- canonical field name
- FIX field code
- field type
- required/optional status
- meaning or allowed values

must be treated as a protocol change and reviewed explicitly.

---

## 8. Selector policy

Consumers must use JMS selectors based on JMS properties.

### Primary selector field

`EntityKind`

### Example selectors

Single kind:
```

text EntityKind = 34``` 

Multiple kinds:
```

text EntityKind IN (34, 35, 36)``` 

By bus and kind:
```

text BusID = 'esquire.entity' AND EntityKind = 34``` 

By bus, message type, and kind:
```

text BusID = 'esquire.entity' AND MsgType = 'UE' AND EntityKind = 34``` 

### Selector rules

- selectors must reference JMS properties, not JSON body content
- `EntityKind` is the primary routing property
- `BusID` should be included in selectors where useful for safety and clarity

---

## 9. FIX-style semantic conventions

This messaging pattern follows FIX-style semantics where practical.

### Current message semantics

| Field | Value | Meaning |
|---|---|---|
| `MsgType` | `UE` | entity update event |
| `EventType` | `U` | update operation |
| `MessageEncoding` | `JSON` | body uses JSON encoding |

### Event type vocabulary

Initial controlled values:

| `EventType` | Meaning |
|---|---|
| `C` | Create |
| `U` | Update |
| `D` | Delete |

Possible future values:

| `EventType` | Meaning |
|---|---|
| `S` | Snapshot |
| `R` | Refresh / republish |

### Note on FIX reuse

Existing FIX field semantics should be reused where appropriate.
Custom fields may be introduced where existing FIX fields do not cover the required domain semantics.

A mapping table between business semantics and FIX/custom field definitions should be maintained as the protocol evolves.

---

## 10. Message body format

The JMS message body must be text and encoded as JSON.

### Encoding

- JMS body type: text
- `MessageEncoding=JSON`
- structure: FIX-JSON envelope with optional lightweight business payload

### Body purpose

The body carries business event content.
JMS properties carry transport and routing metadata.

### Recommended minimal body shape
```

json { "ApplMsgID": "550e8400-e29b-41d4-a716-446655440000", "SendingTime": "2026-03-17T10:15:30Z", "SchemaVersion": 1, "BusID": "esquire.entity", "ServiceID": "enyMan", "CtrlID": "enyman.instance.id", "MsgType": "UE", "EventType": "U", "EntityKind": 34, "EntityID": 1234, "RequestID": "req-789", "CorrelationID": "corr-456", "MessageEncoding": "JSON", "Text": { "state": "ACTIVE" } }``` 

### Body guidelines

- body must be lightweight
- body may include a small optional payload
- payload should include only fields required for synchronization or fast reaction
- large entity graphs should be avoided in phase 1

---

## 11. Payload policy

### Preferred approach

Light event with optional small payload.

### Good payload examples

- status
- code
- short name
- version
- changed field summary
- parent reference
- effective dates

### Avoid in phase 1

- full object graph replication
- very large nested JSON
- binary content
- payloads requiring heavy transformation to consume

### Reasoning

The initial pattern is for synchronization signaling first, not bulk data transfer.

---

## 12. Producer rules

The producer must publish only after a successful state change.

### Required behavior

1. state change is persisted
2. transaction is committed
3. event is published to `esquire.entity.broadcast`

### Required producer guarantees

- every event has a unique `ApplMsgID`
- every event has `SendingTime`
- all required JMS properties are present
- body is valid JSON text
- body and JMS properties are consistent
- `RequestID` and `CorrelationID` are propagated correctly

---

## 13. Consumer rules

Consumers are responsible for safe and repeatable processing.

### Required consumer behavior

- subscribe durably
- use JMS selector(s)
- validate required metadata
- process only relevant entity kinds
- tolerate duplicate delivery

### Idempotency

Consumers should be idempotent.

Recommended approaches:

- deduplicate by `ApplMsgID`
- or process using latest entity version/timestamp semantics
- or safely reapply update logic

Durable messaging reduces loss risk, but duplicate handling is still required.

---

## 14. Operational assumptions

Phase 1 operational assumptions:

- ActiveMQ is internal-only
- no broker authorization is enabled initially
- external exposure should still be minimized by deployment topology
- topic traffic is expected to be moderate in initial rollout

This policy may be tightened later with authentication, authorization, encryption, and dead-letter routing.

---

## 15. Error handling and retry

Phase 1 keeps this simple.

### Initial policy

- listener failure should not silently lose the event
- durable subscription must allow later recovery
- consumer processing must log contract failures clearly

### Future enhancements

- dead-letter queue strategy
- poison message handling
- retry backoff policy
- replay tooling
- event audit trail

---

## 16. Versioning policy

### Current version

`SchemaVersion = 1`

### Rules

- schema version must be present on every message
- backward-compatible additions should keep the same version where possible
- breaking changes should increment schema version
- consumers should log unsupported schema versions

---

## 17. Naming summary

### Topic
`esquire.entity.broadcast`

### Bus ID
`esquire.entity`

### Producer service ID
`enyMan`

### Message type
`UE`

### Message encoding
`JSON`

### Canonical notation
FIX-JSON notation

---

## 18. Example event

### JMS properties
```

text ApplMsgID=550e8400-e29b-41d4-a716-446655440000 SendingTime=2026-03-17T10:15:30Z SchemaVersion=1 BusID=esquire.entity ServiceID=enyMan CtrlID=enyman.instance.id MsgType=UE EventType=U EntityKind=34 EntityID=1234 RequestID=req-789 CorrelationID=corr-456 MessageEncoding=JSON``` 

### FIX-JSON body
```

json { "ApplMsgID": "550e8400-e29b-41d4-a716-446655440000", "SendingTime": "2026-03-17T10:15:30Z", "SchemaVersion": 1, "BusID": "esquire.entity", "ServiceID": "enyMan", "CtrlID": "enyman.instance.id", "MsgType": "UE", "EventType": "U", "EntityKind": 34, "EntityID": 1234, "RequestID": "req-789", "CorrelationID": "corr-456", "MessageEncoding": "JSON", "Text": { "state": "ACTIVE", "shortName": "Sample entity" } }``` 

---

## 19. Initial implementation plan

### Phase 1

1. deploy internal ActiveMQ broker
2. define topic `esquire.entity.broadcast`
3. implement producer in `enyMan`
4. implement durable subscriber template in one consumer service
5. apply selector by `EntityKind`
6. validate end-to-end event flow
7. confirm duplicate-safe consumer processing

### Phase 2

1. standardize event publishing helper
2. define shared schema objects
3. maintain FIX/custom field mapping reference
4. add monitoring and replay support
5. add dead-letter and retry policies
6. review broker authentication and authorization

---

## 20. Open points for later refinement

The following items are intentionally deferred:

- formal FIX/custom tag registry governance
- exact payload schema per entity kind
- dead-letter routing
- replay tools
- event signing or integrity checks
- broker authentication and authorization
- retention and audit policy
- exact `CtrlID` generation rules

---

## 21. Final baseline decision

The first internal messaging pattern is defined as:

- ActiveMQ topic
- topic name `esquire.entity.broadcast`
- producer `enyMan`
- durable subscribers
- selector-based routing using JMS properties
- FIX-JSON notation as canonical envelope format
- same canonical field names in JMS properties and FIX-JSON body
- FIX field-code mapping with standard and custom tags
- lightweight synchronization event payload
- no broker authorization in phase 1
```
