# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Services — Logging Strategy

## Three-Tier Architecture

Every service uses three distinct logging tiers. Each tier has a dedicated destination and a specific purpose.

| Tier | Logger instance | Logback logger name | Destination | Purpose |
|---|---|---|---|---|
| Console | `log` (Lombok `@Slf4j`) | class path | stdout (ECS structured) | Observability — what ops watches |
| Develop | `devLog` | `develop.<classname>` | rolling file (7 days) | Debug trace — internal detail |
| Msg audit | `msgLog` | `msg.<classname>` | rolling file (30 days) | JMS traffic audit |

---

## Logger Declarations

```java
// Provided by @Slf4j — no declaration needed:
// private static final Logger log = LoggerFactory.getLogger(ClassName.class);

// Develop logger — add when the class has devLog.debug / devLog.error calls:
private static final org.slf4j.Logger devLog =
        LoggerFactory.getLogger("develop." + ClassName.class.getName());

// Msg audit logger — add ONLY in JMS publishers and listeners:
private static final org.slf4j.Logger msgLog =
        LoggerFactory.getLogger("msg." + ClassName.class.getName());
```

`msgLog` is exclusively for JMS send/receive events. Service-layer classes (business logic, KC API calls, etc.) must NOT use `msgLog`.

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

## Msg Audit Logger — JMS Publishers and Listeners Only

### Dual-mode pattern (publishers — have a props `Map`)

```java
if (msgLog.isDebugEnabled()) {
    msgLog.info("KC | URS | {}", Utils.formatProps(props));
} else {
    msgLog.info("KC | URS | {} | {} | {} | {} | {} | {} | {} | {}",
            mid, command, kind, entityId, ctrlId, requestId, correlationId, testReqId);
}
log.info("KC | URS | {} | {} | {} | {} | {} | {} | {} | {}",
        mid, command, kind, entityId, ctrlId, requestId, correlationId, testReqId);
```

- Debug level: full props map via `Utils.formatProps(props)` — all fields, insertion order
- Normal level: compact ordered fields
- `log.info` mirrors the compact line to console for observability

### Dual-mode pattern (listeners — have a JMS `Message`)

```java
if (msgLog.isDebugEnabled()) {
    msgLog.info("KC | URQ | {}", Utils.formatProps(message));
} else {
    msgLog.info("KC | URQ | {} | {} | {} | {} | {} | {} | {} | {}",
            applMsgId, command, kind, entityId, ctrlId, requestId, correlationId, testReqId);
}
log.info("KC | URQ | {} | {} | {} | {} | {} | {} | {} | {}",
        applMsgId, command, kind, entityId, ctrlId, requestId, correlationId, testReqId);
```

- Debug level: full props via `Utils.formatProps(message)` — alphabetically sorted
- Normal level: compact ordered fields

### Field order — KC messages (URQ / URS / URR)

```
KC | <TYPE> | applMsgId | command | kind | entityId | ctrlId | requestId | correlationId | testReqId
```

### Field order — Entity broadcast messages

No `testReqId`. Fields:

```
ENTITY | applMsgId | eventType | entityKind | entityId | requestId | correlationId
```

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

## MDC in JMS Consumers

HTTP services get `requestId` and `correlationId` in MDC automatically from `MdcFilter`. JMS listener threads are not HTTP — they get nothing unless explicitly set.

Every JMS `@JmsListener` method must populate MDC from the message properties and clear it in `finally`:

```java
public void onMessage(Message message) {
    try {
        String requestId     = message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID);
        String correlationId = message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID);
        // ... other fields ...

        MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
        MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);

        // ... process message ...

    } catch (Exception e) {
        log.error("...: requestId={}, correlationId={}, error={}", requestId, correlationId, e.getMessage());
        devLog.error("...", requestId, correlationId, e.getMessage(), e);
    } finally {
        MDC.clear();
    }
}
```

Once MDC is set, any `log.*` call inside the handler automatically includes the correlation context in the log pattern without explicit parameter threading.

Services and consumers covered: `KcRequestConsumer`, `KcEntityBroadcastConsumer` (kcMaster), `KcSyncResponseListener` (keySmith), `EsqEntityBroadcastConsumer` (bizTree, enyMan).

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
