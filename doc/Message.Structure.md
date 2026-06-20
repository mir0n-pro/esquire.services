
Esquire Request/Response/RequestReject/Entity/Audit Message Structure

Every Esquire message is a FIX-JSON envelope produced by one shared codec (`RodEventCodec`) and carried
by any x-rod bus regardless of transport (ActiveMQ queue, Kafka topic, Redis stream). Identity / audit
fields ride as header properties; the message body rides as the `Text` JSON field. The envelope meta
`ApplMsgID` and `SendingTime` are added by the publisher per send.

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
| `CorrelationID` | `50009` | String | no       | `corr-456` | cross-service correlation id                                                                            |
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
| `CorrelationID`      | `50009` | String | no       | `corr-456`                              | cross-service correlation id                                                                                          |
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
| `CorrelationID`      | `50009` | String | no       | `corr-456`                              | cross-service correlation id (echo from request)                           |
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
| `CorrelationID`     | `50009` | String | no       | `corr-456`                              | cross-service correlation id (echo from request)          |
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
| `CorrelationID`      | `50009` | String | no       | `corr-456`                              | cross-service correlation id (snapshotted at post time)                    |
| `MessageEncoding`    |   `347` | String | yes      | `JSON`                                  | body encoding                                                              |
| `Text`               |    `58` | String (JSON) | no       | `{"id":"8","kind":36,"name":"Mer Chant"}` | the full committed row (CREATE/UPDATE); empty on DELETE (id + kind are in the header) |
