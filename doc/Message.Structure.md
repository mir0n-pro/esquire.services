
Esquire Request/Response/RequestReject/Entity Message Structure

Esquire Entity Broadcast Message : UE

| Canonical field name | FIX tag | Type | Required | Example | Notes                                                                                                   |
|---|---:|---|----------|---|---------------------------------------------------------------------------------------------------------|
| `ApplMsgID` | `1181` | String | yes      | `550e8400-e29b-41d4-a716-446655440000` | unique event identifier                                                                                 |
| `SendingTime` | `52` | UTCTimestamp | yes      | `2026-03-17T10:15:30Z` | event creation time                                                                                     |
| `SchemaVersion` | `50001` | Int | yes      | `1` | protocol schema version                                                                                 |
| `BusID` | `50002` | String | yes      | `esquire.entity` | logical event bus                                                                                       |
| `ServiceID` | `50003` | String | yes      | `entity-update-broadcast` | messaging service identifier; stable channel name shared by all producers and consumers of this channel |
| `CtrlID` | `50004` | String | yes      | `enyman.instance.id` | producer controller / instance id                                                                       |
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
| `BusID`              | `50002` | String | yes      | `esquire.entity`                        | logical event bus                                                                                                     |
| `ServiceID`          | `50003` | String | yes      | `entity-update-broadcast`               | messaging service identifier; stable channel name shared by all producers and consumers of this channel               |
| `CtrlID`             | `50004` | String | yes      | `enyman.instance.id`                    | producer controller / instance id                                                                                     |
| `MsgType`            |    `35` | String | yes      | `URQ`                                  | FIX-style message type: Esquire Request                                                                               |
| `EventType`          | `50005` | String | yes      | `U`                                     | Request command code/ operation type                                                                                  |
| `EntityKind`         | `50006` | Int | no       | `34`                                    | entity kind code                                                                                                      |
| `EntityID`           | `50007` | String | no       | `1234`                                  | entity identifier                                                                                                     |
| `RequestID`          | `50008` | String | no       | `req-789`                               | cross-service request trace id                                                                                        |
| `CorrelationID`      | `50009` | String | no       | `corr-456`                              | cross-service correlation id                                                                                          |
| `TestReqID`          |   `112` | String | yes      | `test-request-1`                        | Unique correlation key for this request/response exchange (FIX TestRequest/Heartbeat pattern). Set by the requester: use RequestID when present; otherwise generate a new UUID. |
| `MessageEncoding`    |   `347` | String | no       | `JSON`                                  | body encoding                                                                                                         |
| `Text`               | `58` | String (JSON) | no       | `{"id":"1234","kind":34,"name":"ACME"}` | JSON string property; depends on context of request command, can be an entity state or array of name-value parameters |

Esquire Request Response : URS

| Canonical field name | FIX tag | Type | Required | Example                                 | Notes                                                                      |
|----------------------|--------:|---|----------|-----------------------------------------|----------------------------------------------------------------------------|
| `ApplMsgID`          |  `1181` | String | yes      | `550e8400-e29b-41d4-a716-446655440000`  | unique message identifier                                                  |
| `SendingTime`        |    `52` | UTCTimestamp | yes      | `2026-03-17T10:15:30Z`                  | message creation time                                                      |
| `SchemaVersion`      | `50001` | Int | yes      | `1`                                     | protocol schema version                                                    |
| `BusID`              | `50002` | String | yes      | `esquire.entity`                        | logical event bus (echo from request)                                      |
| `ServiceID`          | `50003` | String | yes      | `entity-update-broadcast`               | messaging service identifier (echo from request)                           |
| `CtrlID`             | `50004` | String | yes      | `enyman.instance.id`                    | producer controller / instance id  (echo from request)                     |
| `MsgType`            |    `35` | String | yes      | `URS`                                  | FIX-style message type: Esquire Response                                   |
| `EventType`          | `50005` | String | yes      | `U`                                     | Request command code/ operation type  (echo from request)                  |
| `EntityKind`         | `50006` | Int | no       | `34`                                    | entity kind code  (echo from request)                                                         |
| `EntityID`           | `50007` | String | no       | `1234`                                  | entity identifier (echo from request)                                                         |
| `RequestID`          | `50008` | String | no       | `req-789`                               | cross-service request trace id (echo from request)                         |
| `CorrelationID`      | `50009` | String | no       | `corr-456`                              | cross-service correlation id (echo from request)                           |
| `TestReqID`          |   `112` | String | yes      | `test-request-1`                        | Unique correlation key for this request/response exchange. Echoed back unchanged from the URQ.                        |
| `MessageEncoding`    |   `347` | String | no       | `JSON`                                  | body encoding                                                              |
| `Text`               | `58` | String (JSON) | no       | `{"id":"1234","kind":34,"name":"ACME"}` | response body; JSON string property; optional and command-dependent — absent when the command produces no result (e.g. KC sync U/D); present when the command returns data (e.g. create returning the assigned id). Absence of Text implies the URS is a silent acknowledgement. |

Esquire Request Reject : URR

| Canonical field name | FIX tag | Type | Required | Example                                 | Notes                                                     |
|---------------------|--------:|---|----------|-----------------------------------------|-----------------------------------------------------------|
| `ApplMsgID`         |  `1181` | String | yes      | `550e8400-e29b-41d4-a716-446655440000`  | unique message identifier                                 |
| `SendingTime`       |    `52` | UTCTimestamp | yes      | `2026-03-17T10:15:30Z`                  | message creation time                                     |
| `SchemaVersion`     | `50001` | Int | yes      | `1`                                     | protocol schema version                                   |
| `BusID`             | `50002` | String | yes      | `esquire.entity`                        | logical event bus (echo from request)                     |
| `ServiceID`         | `50003` | String | yes      | `entity-update-broadcast`               | messaging service identifier (echo from request)          |
| `CtrlID`            | `50004` | String | yes      | `enyman.instance.id`                    | producer controller / instance id  (echo from request)    |
| `MsgType`           |    `35` | String | yes      | `URR`                                   | FIX-style message type: Esquire Request Reject            |
| `EventType`         | `50005` | String | yes      | `U`                                     | Request command code/ operation type  (echo from request) |
| `EntityKind`        | `50006` | Int | no       | `34`                                    | entity kind code  (echo from request)                     |
| `EntityID`          | `50007` | String | no       | `1234`                                  | entity identifier (echo from request)                     |
| `RequestID`         | `50008` | String | no       | `req-789`                               | cross-service request trace id (echo from request)        |
| `CorrelationID`     | `50009` | String | no       | `corr-456`                              | cross-service correlation id (echo from request)          |
| `TestReqID`         |   `112` | String | yes      | `test-request-1`                        | Unique correlation key for this request/response exchange. Echoed back unchanged from the URQ. |
| `MessageEncoding`   |   `347` | String | no       | `JSON`                                  | body encoding (echo from request)                         |
| `Text`              |    `58` | String (JSON) | no       | `{"id":"1234","kind":34,"name":"ACME"}` | request body; JSON string property; (echo from request) — not yet implemented in KC sync |
| `Error`             | `50010` | String (JSON) | yes      | `{"type":"about:blank","title":"KC_SYNC_ERROR","status":500,"detail":"Connection refused"}` | RFC 9457 Problem Details body; JSON string property;     |

