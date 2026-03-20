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
- **no message body** — all 14 canonical fields are transmitted as JMS properties
- `Text` is a JMS string property carrying a JSON-encoded entity state snapshot
- the protocol uses **FIX-JSON notation** as the canonical field naming convention
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

Initial producers:

- `enyMan`
- `pacMan`

Producer responsibilities:

- detect committed entity state change
- publish one event to topic `esquire.entity.broadcast`
- populate all 14 required JMS properties
- serialize entity state snapshot as JSON string and set as `Text` property
- include in `Text` only the fields that triggered the update, using their original field names and raw values

### Producer decoupling rule

Producers are fully decoupled from consumers.

A producer publishes raw entity field values without any transformation, encoding, or interpretation intended for a specific consumer. Each consumer is responsible for interpreting the values it receives according to its own domain logic.

Trigger fields per producer:

| Producer | Trigger fields |
|---|---|
| `enyMan` | `name`, `desc`, `deleted` (`usr_deleted_flg`) |
| `pacMan` | `name`, `desc`, `status` (`acc_status`) |

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

All 14 canonical fields are transmitted exclusively as JMS message properties. There is no message body.

This means:

- all protocol fields use FIX-JSON semantic names as JMS property names
- each field is mapped to a FIX field code
- standard FIX tags are reused where the semantic match is appropriate
- custom application tags are used for Esquire-specific fields
- `Text` carries a JSON string property (entity state snapshot), not a nested object

Accordingly, each protocol field has one canonical definition consisting of:

- canonical field name
- FIX field code
- type
- required/optional status
- allowed values and semantics

### 7.2 Canonical naming rule

The protocol uses one canonical field name for each field. All fields are JMS properties.

Rule:

- all 14 canonical fields are set as JMS properties
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
- stable FIX-JSON field naming
- predictable tracing across services
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
| `ServiceID` | `50003` | String | yes | `entity-update-broadcast` | messaging service identifier; stable channel name shared by all producers and consumers of this channel |
| `CtrlID` | `50004` | String | yes | `enyman.instance.id` | producer controller / instance id |
| `MsgType` | `35` | String | yes | `UE` | FIX-style message type |
| `EventType` | `50005` | String | yes | `U` | operation type |
| `EntityKind` | `50006` | Int | yes | `34` | entity kind code |
| `EntityID` | `50007` | String | yes | `1234` | entity identifier |
| `RequestID` | `50008` | String | yes | `req-789` | request trace id |
| `CorrelationID` | `50009` | String | yes | `corr-456` | cross-service correlation id |
| `MessageEncoding` | `347` | String | yes | `JSON` | body encoding |
| `Text` | `58` | String (JSON) | yes | `{"id":"1234","kind":34,"name":"ACME"}` | entity state snapshot; JSON string property; `id` and `kind` always present |

### 7.6 Required baseline values for phase 1

The following values are fixed for the initial implementation:

| Field | Value |
|---|---|
| `SchemaVersion` | `1` |
| `BusID` | `esquire.entity` |
| `ServiceID` | `entity-update-broadcast` (stable messaging service id; short name TBD) |
| `MsgType` | `UE` |
| `MessageEncoding` | `JSON` |

### 7.7 Authority rule

All 14 canonical fields are authoritative as JMS properties.

JMS properties are the single source of truth for:

- routing and filtering via selectors
- envelope metadata consumed by listeners
- tracing and idempotency (`ApplMsgID`, `RequestID`, `CorrelationID`)

`Text` is a JMS string property carrying a JSON-encoded entity state snapshot. It is not a routing field; it is consumed as payload by business logic only.

### 7.8 Text property content rule

The `Text` JSON must be self-identifying. A consumer processing only the `Text` value (without access to other JMS properties) must be able to fully identify the entity.

Required fields inside `Text`:

- `id` — entity identifier
- `kind` — entity kind code

Optional fields (present only when they were part of the triggering update):

- `name` — entity display name
- `desc` — entity description (may be explicitly `null` to clear the field)
- `deleted` — raw `usr_deleted_flg` value; enyMan/USR entities only (e.g. `"Y"`, `"C"`, `null`)
- `status` — raw `acc_status` value; pacMan/ACCT entities only (e.g. `"O"`, `"L"`, `"C"`, `null`)

All optional field values are the raw entity field values as stored. Consumers interpret them according to their own domain logic.

Example (name update):

```json
{"id": "1234", "kind": 34, "name": "ACME Corp"}
```

Example (USR status update):

```json
{"id": "1234", "kind": 34, "deleted": "Y"}
```

Example (ACCT status update):

```json
{"id": "5678", "kind": 50, "status": "L"}
```

### 7.9 Consumer validation rule

Consumers must validate that all required JMS properties are present before processing.

Required minimum check:

- `ApplMsgID`, `SchemaVersion`, `BusID`, `MsgType`, `EventType`, `EntityKind`, `EntityID`, `MessageEncoding`, `Text`

If any required property is absent, the consumer must:

1. log the missing property
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

### 7.12 JMS properties example (all 14 fields)

```
ApplMsgID    = 550e8400-e29b-41d4-a716-446655440000
SendingTime  = 2026-03-17T10:15:30Z
SchemaVersion= 1
BusID        = esquire.entity
ServiceID    = entity-update-broadcast
CtrlID       = enyman.instance.id
MsgType      = UE
EventType    = U
EntityKind   = 34
EntityID     = 1234
RequestID    = req-789
CorrelationID= corr-456
MessageEncoding= JSON
Text         = {"id":"1234","kind":34,"name":"ACME Corp","desc":"sample"}
```

No message body is set.

### 7.13 Text property content

`Text` is a JSON string property. The above example expands to:

```json
{
  "id": "1234",
  "kind": 34,
  "name": "ACME Corp",
  "desc": "sample"
}
```

`name`, `desc`, `deleted`, `status` are present only when they were included in the triggering update request. Values are raw entity field values.

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

## 10. Transport format

Messages are properties-only. No message body is set.

### Transport

- JMS message type: `jakarta.jms.Message` (base, no body)
- all 14 canonical fields are set as JMS properties
- `MessageEncoding=JSON` refers to the encoding of the `Text` property value

### Text property

`Text` is the only field that carries a JSON-encoded value. It is a JMS string property whose value is a compact JSON object representing the entity state snapshot.

### Text property guidelines

- must include `id` and `kind` (minimum required for self-identification)
- include `name`, `desc`, `deleted`, `status` only when those fields were part of the triggering update
- all values are raw entity field values — no consumer-specific encoding or transformation
- no large nested graphs
- no fields unrelated to the triggering change

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
- all 14 required JMS properties are present
- `Text` property is valid JSON containing at minimum `id` and `kind`
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

### Messaging service ID (`ServiceID`)
`entity-update-broadcast` (short name TBD; shared by all producers and consumers of this channel)

### Message type
`UE`

### Message encoding
`JSON`

### Canonical notation
FIX-JSON notation

---

## 18. Example event

All 14 canonical fields are set as JMS properties. No message body.

### JMS properties

```
ApplMsgID     = 550e8400-e29b-41d4-a716-446655440000
SendingTime   = 2026-03-17T10:15:30Z
SchemaVersion = 1
BusID         = esquire.entity
ServiceID     = entity-update-broadcast
CtrlID        = enyman.instance.id
MsgType       = UE
EventType     = U
EntityKind    = 34
EntityID      = 1234
RequestID     = req-789
CorrelationID = corr-456
MessageEncoding = JSON
Text          = {"id":"1234","kind":34,"name":"ACME Corp","deleted":"Y"}
```

### Text property expanded

```json
{
  "id": "1234",
  "kind": 34,
  "name": "ACME Corp",
  "deleted": "Y"
}
```

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

### Phase 2 (delivered 2026-03-20)

1. bizTree consumer applies UPDATE events to the in-memory H2 cache (`IBizTreeCacheRepository.updateNode`)
2. single CASE-based SQL update covers name, desc, and status in one query
3. status fields broadcast: enyMan sends `deleted` (usr_deleted_flg), pacMan sends `status` (acc_status)
4. bizTree decodes raw status values to cache integer codes (0=ok, 1=deleted, 2=locked)

### Phase 3

0. make messaging infrastructure vendor-agnostic — support RabbitMQ as an alternative to ActiveMQ without changing the defined protocol, field registry, producer/consumer contracts, or selector semantics; broker selection via configuration only
1. standardize event publishing helper
2. define shared schema objects
3. maintain FIX/custom field mapping reference
4. add monitoring and replay support
5. add dead-letter and retry policies
6. review broker authentication and authorization

---

## 20. Open points for later refinement

The following items are intentionally deferred:

- **broker vendor abstraction** — RabbitMQ as alternative to ActiveMQ; JMS selector semantics map to AMQP routing keys / header exchanges; durable subscription maps to durable queues bound to a fanout/topic exchange
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
- producers: `enyMan`, `pacMan`
- durable subscribers
- selector-based routing using JMS properties
- FIX-JSON notation as canonical field naming convention
- **properties-only transport** — all 14 canonical fields are JMS properties; no message body
- `Text` is a JMS string property containing a JSON entity state snapshot
- FIX field-code mapping with standard and custom tags
- lightweight synchronization event payload
- no broker authorization in phase 1
```
