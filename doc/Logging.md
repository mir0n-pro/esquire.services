# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Services — Logging Strategy

## Three-Tier Architecture

Every service uses three distinct logging tiers. Each tier has a dedicated destination and a specific purpose.

| Tier | Logger instance | Logback logger name | Destination | Purpose |
|---|---|---|---|---|
| Console | `log` (Lombok `@Slf4j`) | class path | stdout (ECS structured) | Observability — what ops watches |
| Develop | `devLog` | `develop.<classname>` | rolling file (7 days) | Debug trace — internal detail |
| Msg audit | `msgLog` | `msg.<bus-id>.<slot-id>` | rolling file (30 days) | Bus traffic audit (emitted on the x-rod legs) |

---

## Logger Declarations

```java
// Provided by @Slf4j — no declaration needed:
// private static final Logger log = LoggerFactory.getLogger(ClassName.class);

// Develop logger — add when the class has devLog.debug / devLog.error calls:
private static final org.slf4j.Logger devLog =
        LoggerFactory.getLogger("develop." + ClassName.class.getName());
```

The msg-audit logger is no longer declared per class. The x-rod resolves it per leg as
`msg.<bus-id>.<slot-id>` from the leg's `BusIdentity`, and the framework — not business code — writes
to it on every message crossing a leg. Service-layer classes (business logic, KC API calls, etc.) never
use `msgLog`.

---

## Console — What Goes There

Only events that operations uses to observe the system in production:

- **HTTP traffic** — `INCOMING` / `OUTGOING` per request with method, URI, status, timing
- **KC operation state** — `KC | CREATE/UPDATE/DELETE | state=STARTED/SUCCESS`
- **JMS traffic echo** — compact single-line per message (see Msg Audit Pattern below)
- **Error summaries** — short message + context fields (no stacktrace — see Error Pattern)
- **Startup confirmations** — storage/cache loaded successfully

Everything else belongs in `devLog`.

---

## Develop Logger — What Goes There

- All `devLog.debug(...)` — internal processing steps, verbose state
- All `devLog.error(...)` — full stacktraces (the stacktrace twin of every `log.error`)
- Non-exception warnings demoted from `log.warn`

---

## Msg Audit Logger — The x-rod Legs

Every message that crosses an x-rod leg is logged once, by the framework, on that leg's
`msg.<bus-id>.<slot-id>` logger — the transmit leg logs `TX`, the receive leg logs `RX`. Business code
writes nothing here.

### Line format (every bus)

```
<TX|RX> | <msgType> | <op> | <kind> | <entityId> | <subId> | <rodId> | <requestId>
```

`msgType` is the per-message type (`UE` / `URQ` / `URS` / `URR` / `UA`); `op` is the event-type code
(`C`/`U`/`D`/`X`). One round-trip therefore reads end-to-end across the per-service msg files — e.g. a
user move shows enyMan `TX|URQ` + `RX|URS` and kcMaster `RX|URQ` + `TX|URS`, plus the broadcast `TX/RX|UE`
and the audit `TX|UA`.

The `msg` logback logger is `additivity=false`, so the msg-audit trail goes ONLY to the per-service msg
file, never to stdout. Production may set the msg level OFF — it is lossless, as every endpoint also
app-logs its operation on the console tier (e.g. the `KC | CREATE | state=...` lines, which are NOT
`msgLog`).

---

## Error Pattern

Every error is logged twice — short on console, full on develop:

```java
// Console — message + context, NO exception object
log.error("service: what failed: field={}, requestId={}, correlationId={}", value, requestId, correlationId);

// Develop — identical message + exception as last arg (triggers stacktrace)
devLog.error("service: what failed: field={}, requestId={}, correlationId={}", value, requestId, correlationId, e);
```

Rules:
- Console error message must include `requestId` and `correlationId` where they are in scope
- Never pass the exception as the last arg to `log.error` — Slf4j treats a trailing `Throwable` as the stacktrace trigger, which would print the stack on console
- `log.warn` on a caught exception → promote to dual `log.error` / `devLog.error`
- `log.warn` not on an exception (unexpected value, protocol mismatch) → `devLog.debug`
- No `log.warn` calls anywhere

---

## Debug Rule

All internal trace / verbose detail uses `devLog.debug`. No `log.debug` calls anywhere — they would reach the console if root level is set to DEBUG.

```java
// Wrong:
log.debug("processing entity id={}", id);

// Correct:
devLog.debug("processing entity id={}", id);
```

---

## MDC in Bus Consumers

HTTP services get `requestId` and `correlationId` in MDC automatically from `MdcFilter`. The x-rod
receive worker runs off an HTTP thread — it gets nothing unless explicitly set.

Every consumer's receive worker takes a decoded `RodEvent`; it must populate MDC from the event and
clear it in `finally`:

```java
public void onRodEvent(RodEvent event) {
    try {
        MDC.put(EsqConstants.PD_REQUEST_ID, event.requestId());
        MDC.put(EsqConstants.PD_CORRELATION_ID, event.correlationId());

        // ... process event ...

    } catch (Exception e) {
        log.error("...: requestId={}, correlationId={}, error={}", event.requestId(), event.correlationId(), e.getMessage());
        devLog.error("...", event.requestId(), event.correlationId(), e.getMessage(), e);
    } finally {
        MDC.clear();
    }
}
```

Once MDC is set, any `log.*` call inside the handler automatically includes the correlation context in the log pattern without explicit parameter threading.

Consumers covered: `KcRequestConsumer`, `KcEntityBroadcastConsumer` (kcMaster), `KcResponseListener` (enyMan), `KcSyncResponseListener` (keySmith), `BizTreeBroadcastConsumer` (bizTree), and the audit director on `auKeep`.

---

## Actuator Probe Filtering

`MdcFilter` short-circuits on `/actuator/**` before any logging, MDC setup, or performance tracking. This prevents health-check probes (every 10s from Docker/k8s) from polluting the console with `INCOMING / OUTGOING` noise:

```java
if (givenRequest.getRequestURI().startsWith("/actuator")) {
    filterChain.doFilter(givenRequest, givenResponse);
    return;
}
```

---

## Test Configuration — logback-test.xml

Each service with tests has `src/test/resources/logback-test.xml`. Without it, tests either inherit the production logback config (creating rolling log files during test runs) or fall back to Logback's default output.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="WARN">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

WARN level keeps test output clean — only genuine warnings and errors surface. Raise to `INFO` temporarily when debugging a specific test.

---

## Configuration

### application.yml — standard logging section

```yaml
logging:
  level:
    root:                       ${LOG_LEVEL_ROOT:ERROR}
    org:
      springframework:          ${LOG_LEVEL_SF:ERROR}
      springframework.jms:      ${LOG_LEVEL_JMS:ERROR}
      apache:
        activemq:               ${LOG_LEVEL_AMQ:ERROR}
    pro:
      mir0n:                    ${LOG_LEVEL_MIR0N:ERROR}
    develop:                    ${LOG_LEVEL_DEVELOP:DEBUG}
    msg:                        ${LOG_LEVEL_MSG:INFO}
  develop:
    log-path: ${DEVELOP_LOG_PATH:logs/<service>-develop.log}
  msg:
    log-path: ${MSG_LOG_PATH:logs/<service>-msg.log}
```

Gateway has no JMS and no msg tier — omit `springframework.jms`, `apache.activemq`, `msg` level, and `msg.log-path`.

### Environment variables per service (compose.yaml)

| Variable | Typical value | Purpose |
|---|---|---|
| `LOG_LEVEL_ROOT` | `ERROR` | Third-party library noise floor |
| `LOG_LEVEL_SF` | `ERROR` | Spring Framework |
| `LOG_LEVEL_MIR0N` | `DEBUG` | All `pro.mir0n` classes |
| `LOG_LEVEL_DEVELOP` | `DEBUG` | Develop file logger |
| `LOG_LEVEL_MSG` | `INFO` | Msg audit logger |
| `DEVELOP_LOG_PATH` | `logs/<svc>-develop.log` | Develop rolling file path |
| `MSG_LOG_PATH` | `logs/<svc>-msg.log` | Msg audit rolling file path |

### logback-spring.xml — appenders

| Appender | Logger binding | Retention |
|---|---|---|
| `CONSOLE` | root | — |
| `DEVELOP_FILE` | `<logger name="develop">` additivity=false | 7 days |
| `MSG_FILE` | `<logger name="msg">` additivity=false | 30 days |

The `develop` and `msg` parent loggers catch all `develop.*` and `msg.*` child loggers respectively.
