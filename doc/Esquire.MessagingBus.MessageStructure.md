<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>

# Esquire Messaging Bus -- Message Structure

Companion to `Esquire.MessagingBus.md` (the framework reference) -- the wire format for every Esquire message
type: **Entity (UE)**, **Request (URQ)**, **Response (URS)**, **Request Reject (URR)**, **Audit (UA)**, and the
**Session** pair (TestRequest / HeartBeat).

Every Esquire message is a FIX-JSON envelope produced by one shared codec (`RodEventCodec`) and carried
by any x-rod bus regardless of transport (ActiveMQ queue, Kafka topic, Redis stream). Identity / audit
fields ride as header properties; the message body rides as the `Text` JSON field. The envelope meta
`ApplMsgID` and `SendingTime` are added by the publisher per send. The canonical field names and the
msg-type / event-type values are defined in `pro.mir0n.esquire.messaging.BusConstants`.

Routing-envelope fields:

- `BusID` — the logical bus.
- `SlotID` — the bus slot (leg) the participant joined. (Renamed from `ServiceID`; "slot" avoids clashing
  with "microservice".)
- `RodID` — the originating instance id; the request/response reply-routing selector. (Renamed from `CtrlID`.)
- `MsgType` — the message type; rides per-message on the event.

Audit header fields (meaningful on `UA`; present in the envelope, otherwise null):

- `SubID` — sub-row discriminator (e.g. `ad_pk`, `par_name`) when `(EntityID, EntityKind)` is not unique.
- `Uid` — the acting user id (the audit actor).
- `ActionTime` — epoch-ms stamped at commit (the audit "when").

`TestReqID` carries the `RequestID` value (the request/response correlation key folded into `RequestID`,
which the producer guarantees non-null); it is kept on the wire for shape.

**Why one wide envelope (the FIX-precedent tradeoff).** All message types -- Request / Response /
RequestReject / Entity / Audit / Session -- ride the SAME envelope (`RodEvent`), so most of its fields are null
on any given message and the type is carried in DATA (`MsgType`) rather than in a Java type. This is a
deliberate choice, modeled on FIX: ONE codec (`RodEventCodec`) and ONE wire shape for every message means a new
message type or field is added WITHOUT a new class, a new codec, or a schema migration -- the same
generic-envelope tradeoff FIX makes (a tag dictionary over typed messages). The cost is the obvious one: no
per-message-type compile-time safety and a wide, mostly-null record. Producers and consumers validate the
fields they actually use; the envelope stays open.

**Timestamps -- ISO-8601, not the FIX UTCTimestamp format (a deliberate deviation).** FIX defines UTCTimestamp
(tag 52) as `YYYYMMDD-HH:MM:SS` or `YYYYMMDD-HH:MM:SS.sss`. Esquire does NOT emit that format. On the wire:

- `SendingTime` (tag 52) is an **ISO-8601 / RFC 3339** string -- the transport driver stamps it per physical send
  as `Instant.now().toString()` (e.g. `2026-03-17T10:15:30.482Z`, fractional seconds as produced).
- `ActionTime` (the audit "when", tag 50013) is **epoch-milliseconds** as a JSON number (e.g. `1718470800000`).

The deviation is deliberate. The bus is JSON-encoded and internal, so timestamps use the JSON-native forms:
ISO-8601 is parsed by every JSON library and by the browser `Date`, sorts lexicographically, and is unambiguous
about the zone (`Z` = UTC); epoch-ms is the cheapest machine form for the commit instant. Neither needs a
FIX-specific parser.

**On FIX-format support -- common practice and intent.** The FIX Trading Community's official *FIX JSON Encoding*
keeps each field value as a JSON string and, for a UTCTimestamp field, RETAINS the FIX `YYYYMMDD-HH:MM:SS.sss`
string -- it does NOT convert to ISO-8601. Pragmatic "FIX-flavored" JSON APIs (the shape Esquire follows) instead
use ISO-8601 / RFC 3339 strings (or epoch-ms numbers) precisely because they interoperate with JSON tooling out
of the box. Esquire is **FIX-INSPIRED, not strict FIX-JSON**: it borrows the tag dictionary and the one-wide-
envelope idea, but encodes timestamps the JSON-native way. The intent was the FIX format; returning `SendingTime`
to the FIX `YYYYMMDD-HH:MM:SS.sss` form is tracked in `Esquire.MessagingBus.ContinuingDev.md` -- we go back to
FIX eventually, not important for the internal bus now. The `Type` column below names each field's FIX semantic
type (`UTCTimestamp`); the ENCODING of that type is the JSON-native form described here.

Esquire Entity Broadcast Message : UE

| Canonical field name | FIX tag | Type | Required | Example | Notes                                                                                                   |
|---|---:|---|----------|---|---------------------------------------------------------------------------------------------------------|
| `ApplMsgID` | `1181` | String | yes      | `550e8400-e29b-41d4-a716-446655440000` | unique event identifier                                                                                 |
| `SendingTime` | `52` | UTCTimestamp | yes      | `2026-03-17T10:15:30Z` | event creation time                                                                                     |
| `SchemaVersion` | `50001` | Int | yes      | `1` | protocol schema version                                                                                 |
| `BusID` | `50002` | String | yes      | `esquire.entity` | logical event bus                                                                                       |
| `SlotID` | `50003` | String | yes      | `entity` | bus slot (leg) id; stable channel name shared by all producers and consumers of this slot               |
| `RodID` | `50004` | String | yes      | `enyman` | originating instance id (reply-routing selector)                                                        |
| `MsgType` | `35` | String | yes      | `UE` | FIX-style message type: Esquire Entity                                                                  |
| `EventType` | `50005` | String | yes      | `U` | event type (C,D,U,X) X: reserved for only path update                                                   |
| `EntityKind` | `50006` | Int | yes      | `34` | entity kind code                                                                                        |
| `EntityID` | `50007` | String | yes      | `1234` | entity identifier                                                                                       |
| `RequestID` | `50008` | String | no       | `req-789` | request trace id                                                                                        |
| `CorrelationID` | `50009` | String | no       | `4bf92f3577b34da6a3ce929d0e0e4736` | cross-service correlation id                                                                            |
| `MessageEncoding` | `347` | String | yes      | `JSON` | body encoding                                                                                           |
| `Text` | `58` | String (JSON) | yes      | `{"id":"1234","kind":34,"name":"ACME"}` | entity state snapshot; JSON string property; `id` and `kind` always present                             |


Esquire Request Message : URQ

| Canonical field name | FIX tag | Type | Required | Example                                 | Notes                                                                                                                 |
|----------------------|--------:|---|----------|-----------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `ApplMsgID`          |  `1181` | String | yes      | `550e8400-e29b-41d4-a716-446655440000`  | unique message identifier                                                                                             |
| `SendingTime`        |    `52` | UTCTimestamp | yes      | `2026-03-17T10:15:30Z`                  | event creation time                                                                                                   |
| `SchemaVersion`      | `50001` | Int | yes      | `1`                                     | protocol schema version                                                                                               |
| `BusID`              | `50002` | String | yes      | `esquire.kc`                            | logical event bus                                                                                                     |
| `SlotID`             | `50003` | String | yes      | `kc`                                    | bus slot (leg) id; stable channel name shared by all producers and consumers of this slot                             |
| `RodID`              | `50004` | String | yes      | `enyman`                                | originating instance id; the requester's reply-routing selector                                                       |
| `MsgType`            |    `35` | String | yes      | `URQ`                                  | FIX-style message type: Esquire Request                                                                               |
| `EventType`          | `50005` | String | yes      | `U`                                     | Request command code/ operation type                                                                                  |
| `EntityKind`         | `50006` | Int | no       | `34`                                    | entity kind code                                                                                                      |
| `EntityID`           | `50007` | String | no       | `1234`                                  | entity identifier                                                                                                     |
| `RequestID`          | `50008` | String | yes      | `req-789`                               | cross-service request trace id; the request/response correlation key                                                  |
| `CorrelationID`      | `50009` | String | no       | `4bf92f3577b34da6a3ce929d0e0e4736`                              | cross-service correlation id                                                                                          |
| `TestReqID`          |   `112` | String | yes      | `req-789`                               | echo of `RequestID`, retained for wire shape (FIX TestRequest/Heartbeat lineage). The producer guarantees `RequestID` non-null. |
| `MessageEncoding`    |   `347` | String | no       | `JSON`                                  | body encoding                                                                                                         |
| `Text`               | `58` | String (JSON) | no       | `{"id":"1234","kind":34,"name":"ACME"}` | JSON string property; depends on context of request command, can be an entity state or array of name-value parameters |

Esquire Request Response : URS

| Canonical field name | FIX tag | Type | Required | Example                                 | Notes                                                                      |
|----------------------|--------:|---|----------|-----------------------------------------|----------------------------------------------------------------------------|
| `ApplMsgID`          |  `1181` | String | yes      | `550e8400-e29b-41d4-a716-446655440000`  | unique message identifier                                                  |
| `SendingTime`        |    `52` | UTCTimestamp | yes      | `2026-03-17T10:15:30Z`                  | message creation time                                                      |
| `SchemaVersion`      | `50001` | Int | yes      | `1`                                     | protocol schema version                                                    |
| `BusID`              | `50002` | String | yes      | `esquire.kc`                            | logical event bus (echo from request)                                      |
| `SlotID`             | `50003` | String | yes      | `kc`                                    | bus slot (leg) id (echo from request)                                      |
| `RodID`              | `50004` | String | yes      | `enyman`                                | the requester's instance id, echoed so its `RodID = '<id>'` selector matches the response |
| `MsgType`            |    `35` | String | yes      | `URS`                                  | FIX-style message type: Esquire Response                                   |
| `EventType`          | `50005` | String | yes      | `U`                                     | Request command code/ operation type  (echo from request)                  |
| `EntityKind`         | `50006` | Int | no       | `34`                                    | entity kind code  (echo from request)                                                         |
| `EntityID`           | `50007` | String | no       | `1234`                                  | entity identifier (echo from request)                                                         |
| `RequestID`          | `50008` | String | yes      | `req-789`                               | cross-service request trace id (echo from request); the correlation key    |
| `CorrelationID`      | `50009` | String | no       | `4bf92f3577b34da6a3ce929d0e0e4736`                              | cross-service correlation id (echo from request)                           |
| `TestReqID`          |   `112` | String | yes      | `req-789`                               | echo of `RequestID`, retained for wire shape; echoed back unchanged from the URQ.            |
| `MessageEncoding`    |   `347` | String | no       | `JSON`                                  | body encoding                                                              |
| `Text`               | `58` | String (JSON) | no       | `{"id":"1234","kind":34,"name":"ACME"}` | response body; JSON string property; optional and command-dependent — absent when the command produces no result (e.g. KC sync U/D); present when the command returns data (e.g. create returning the assigned id). Absence of Text implies the URS is a silent acknowledgement. |

Esquire Request Reject : URR

| Canonical field name | FIX tag | Type | Required | Example                                 | Notes                                                     |
|---------------------|--------:|---|----------|-----------------------------------------|-----------------------------------------------------------|
| `ApplMsgID`         |  `1181` | String | yes      | `550e8400-e29b-41d4-a716-446655440000`  | unique message identifier                                 |
| `SendingTime`       |    `52` | UTCTimestamp | yes      | `2026-03-17T10:15:30Z`                  | message creation time                                     |
| `SchemaVersion`     | `50001` | Int | yes      | `1`                                     | protocol schema version                                   |
| `BusID`             | `50002` | String | yes      | `esquire.kc`                            | logical event bus (echo from request)                     |
| `SlotID`            | `50003` | String | yes      | `kc`                                    | bus slot (leg) id (echo from request)                     |
| `RodID`             | `50004` | String | yes      | `enyman`                                | the requester's instance id, echoed for reply routing     |
| `MsgType`           |    `35` | String | yes      | `URR`                                   | FIX-style message type: Esquire Request Reject            |
| `EventType`         | `50005` | String | yes      | `U`                                     | Request command code/ operation type  (echo from request) |
| `EntityKind`        | `50006` | Int | no       | `34`                                    | entity kind code  (echo from request)                     |
| `EntityID`          | `50007` | String | no       | `1234`                                  | entity identifier (echo from request)                     |
| `RequestID`         | `50008` | String | yes      | `req-789`                               | cross-service request trace id (echo from request)        |
| `CorrelationID`     | `50009` | String | no       | `4bf92f3577b34da6a3ce929d0e0e4736`                              | cross-service correlation id (echo from request)          |
| `TestReqID`         |   `112` | String | yes      | `req-789`                               | echo of `RequestID`, retained for wire shape; echoed back unchanged from the URQ. |
| `MessageEncoding`   |   `347` | String | no       | `JSON`                                  | body encoding (echo from request)                         |
| `Text`              |    `58` | String (JSON) | no       | `{"id":"1234","kind":34,"name":"ACME"}` | request body; JSON string property; (echo from request)   |
| `Error`             | `50010` | String (JSON) | yes      | `{"type":"about:blank","title":"KC_SYNC_ERROR","status":500,"detail":"Connection refused"}` | RFC 9457 Problem Details body; JSON string property;     |

Esquire Audit Message : UA

Posted by the entity producers (enyMan / pacMan / keySmith) after commit, off the request thread, to the
audit bus. Carries one committed (sub)entity change with the full audit triple, self-contained for the
consumer (auKeep) or the sink stream to apply with no request context.

| Canonical field name | FIX tag | Type | Required | Example                                 | Notes                                                                      |
|----------------------|--------:|---|----------|-----------------------------------------|----------------------------------------------------------------------------|
| `ApplMsgID`          |  `1181` | String | yes      | `550e8400-e29b-41d4-a716-446655440000`  | unique message identifier                                                  |
| `SendingTime`        |    `52` | UTCTimestamp | yes      | `2026-03-17T10:15:30Z`                  | message creation time                                                      |
| `SchemaVersion`      | `50001` | Int | yes      | `1`                                     | protocol schema version                                                    |
| `BusID`              | `50002` | String | yes      | `audit-c`                               | the configured audit bus (audit-b / audit-c / audit-ck / audit-d / audit-dk) |
| `SlotID`             | `50003` | String | yes      | `audit`                                 | bus slot (leg) id                                                          |
| `RodID`              | `50004` | String | yes      | `enyman`                                | originating instance id                                                    |
| `MsgType`            |    `35` | String | yes      | `UA`                                    | FIX-style message type: Esquire Audit                                      |
| `EventType`          | `50005` | String | yes      | `U`                                     | the committed op (C / U / D); a move is coalesced into U — audit never emits X |
| `EntityKind`         | `50006` | Int | yes      | `36`                                    | the (sub)asset kind; routes the event to its `*_log` table                 |
| `EntityID`           | `50007` | String | yes      | `8`                                     | the owning entity id (usr_pk / org_pk / acct)                              |
| `SubID`              | `50011` | String | no       | `12`                                    | sub-row discriminator (ad_pk / par_name) when `(EntityID, EntityKind)` is not unique; else null |
| `ActionTime`         | `50013` | Long | yes      | `1718470800000`                         | epoch-ms stamped at commit (the audit "when")                              |
| `Uid`                | `50012` | String | no       | `4`                                     | the acting user id (the audit actor)                                       |
| `RequestID`          | `50008` | String | no       | `req-789`                               | request trace id (snapshotted from the request context at post time)       |
| `CorrelationID`      | `50009` | String | no       | `4bf92f3577b34da6a3ce929d0e0e4736`                              | cross-service correlation id (snapshotted at post time)                    |
| `MessageEncoding`    |   `347` | String | yes      | `JSON`                                  | body encoding                                                              |
| `Text`               |    `58` | String (JSON) | no       | `{"id":"8","kind":36,"name":"Mer Chant"}` | the full committed row (CREATE/UPDATE); empty on DELETE (id + kind are in the header) |

Esquire Session (Alive) Messages : TestRequest / HeartBeat

The x-rod runs a FIX-style ALIVE PROTOCOL on a TestRequest / HeartBeat pair, handled INTERNALLY by the x-rod
session layer and NEVER bypassed to the application worker. There are no dedicated session message classes --
the pair rides the SAME envelope as every other message, with a reduced field set. The protocol gives each leg
a transport-agnostic health signal by TIMESTAMP AGE (see `doc/Esquire.MessagingBus.md`): each leg records the
time of its last successful action; a leg with no successful action within `alive-timeout` reads DOWN. When a
producing leg is idle longer than `heartbeat-interval` it emits a session message to keep the leg (and its
health signal) live -- real traffic suppresses it.

- A broadcast SERVER, idle, emits an UNSOLICITED HeartBeat (not correlated to any request).
- An R&R CLIENT, idle, emits a TestRequest; the R&R SERVER ECHOES it back as a HeartBeat (routing fields
  echoed -- the URS reply path), so the CLIENT observes a round trip.
- `MsgType` is FIX-canonical here: `TestRequest = "1"`, `HeartBeat = "0"` (distinct from the U-prefixed
  application types UE / URQ / URS / URR / UA).

`TestReqID` (FIX tag `112`) for a session message lives INSIDE the `Text` body, optional, and equals
`RequestID` -- it is NOT carried as an envelope property on the session pair.

Esquire TestRequest Message : MsgType "1"

| Canonical field name | FIX tag | Type | Required | Example | Notes |
|----------------------|--------:|---|----------|---------|-------|
| `ApplMsgID`          |  `1181` | String | yes | `550e8400-e29b-41d4-a716-446655440000` | unique message identifier |
| `SendingTime`        |    `52` | UTCTimestamp | yes | `2026-03-17T10:15:30Z` | send time |
| `SchemaVersion`      | `50001` | Int | yes | `1` | protocol schema version |
| `BusID`              | `50002` | String | yes | `esquire.kc` | logical bus (from the x-rod identity) |
| `SlotID`             | `50003` | String | yes | `kc` | bus slot (leg) id (from the x-rod identity) |
| `RodID`              | `50004` | String | yes | `enyman` | originating instance id; the reply-routing selector |
| `MsgType`            |    `35` | String | yes | `1` | FIX-canonical TestRequest |
| `CorrelationID`      | `50009` | String | yes | `4bf92f3577b34da6a3ce929d0e0e4736` | freshly generated per TestRequest |
| `RequestID`          | `50008` | String | yes | `4bf92f3577b34da6a3ce929d0e0e4736` | = `CorrelationID` |
| `MessageEncoding`    |   `347` | String | yes | `JSON` | body encoding |
| `Text`               |    `58` | String (JSON) | yes | `{"MsgType":"1","TestReqID":"4bf92f3577b34da6a3ce929d0e0e4736"}` | session body: `MsgType` + `TestReqID` (= `RequestID`) |

Esquire HeartBeat Message : MsgType "0"

Two variants: UNSOLICITED (a broadcast SERVER / R&R SERVER keepalive) and a RESPONSE (an R&R SERVER answering a
received TestRequest). The response echoes the TestRequest's routing + correlation so it returns to the
requester via the `RodID` selector; the unsolicited form carries a fresh `CorrelationID` and no request echo.

| Canonical field name | FIX tag | Type | Required | Example | Notes |
|----------------------|--------:|---|----------|---------|-------|
| `ApplMsgID`          |  `1181` | String | yes | `550e8400-e29b-41d4-a716-446655440000` | unique message identifier |
| `SendingTime`        |    `52` | UTCTimestamp | yes | `2026-03-17T10:15:30Z` | send time |
| `SchemaVersion`      | `50001` | Int | yes | `1` | protocol schema version |
| `BusID`              | `50002` | String | yes | `esquire.kc` | x-rod identity (unsolicited) OR echoed from the TestRequest (response) |
| `SlotID`             | `50003` | String | yes | `kc` | x-rod identity (unsolicited) OR echoed from the TestRequest (response) |
| `RodID`              | `50004` | String | yes | `enyman` | x-rod identity (unsolicited) OR echoed from the TestRequest, so the response routes to the requester |
| `MsgType`            |    `35` | String | yes | `0` | FIX-canonical HeartBeat |
| `CorrelationID`      | `50009` | String | yes | `4bf92f3577b34da6a3ce929d0e0e4736` | freshly generated (unsolicited) OR echoed from the TestRequest (response) |
| `RequestID`          | `50008` | String | no | `4bf92f3577b34da6a3ce929d0e0e4736` | ABSENT (unsolicited) OR echoed from the TestRequest (response) |
| `MessageEncoding`    |   `347` | String | yes | `JSON` | body encoding |
| `Text`               |    `58` | String (JSON) | yes | `{"MsgType":"0","TestReqID":"4bf92f3577b34da6a3ce929d0e0e4736"}` | session body: `MsgType`; plus `TestReqID` (echoed) on a response, omitted when unsolicited |
