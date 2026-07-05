# Esquire Messaging Bus -- Guides (DRAFT)

> DRAFT (2026-07-02). Plain-language companions to the reference spec (`Esquire.MessagingBus.md`), which is dense
> on purpose. These guides say the same things in simpler words -- a friendly on-ramp, not a second spec. Final
> home / structure is settled at sprint end (drafted here per the v1.2.10 Messaging-Bus assessment, `plans/tasks1210.md` 3.6).

Three short guides:
1. **Getting started** -- send and receive your first message in five minutes.
2. **Moving off raw JMS / Kafka** -- retire a `JmsTemplate` / `KafkaTemplate` caller onto the bus.
3. **Tuning & sizing** -- the handful of knobs, in plain terms, and when to touch them.

---

## 1. Getting started (five minutes)

The Messaging Bus is how an Esquire service sends and receives messages **without ever touching a broker**
(ActiveMQ, Kafka, Redis) directly. You describe your buses in configuration, ask the framework for one, and then
send on it or attach a handler. That is the whole idea.

### Step 1 -- describe the bus (config)

Two small pieces of config. First, the SHARED catalog (one file every service imports) says what each bus is on
the wire:

```yaml
esquire:
  messaging-bus:
    - bus-id: esquire.entity
      slots:
        - slot-id: entity
          x-rod:
            transport:
              provider: activemq                 # or kafka / redis
              endpoint: tcp://broker:61616
              destination: esquire.entity.broadcast
              params:
                pubSubDomain: true               # a topic (broadcast); omit for a queue
```

Second, YOUR service says which buses it uses and what part it plays:

```yaml
esquire:
  entity:                                        # a logical KEY you pick
    messaging-bus:
      bus-id: esquire.entity
      slot-id: entity
      role: CLIENT                               # declare a role for a bus you RECEIVE on
```

### Step 2 -- send a message (producer)

Ask the facade for the bus and hand it an event:

```java
IXRod rod = MessagingBus.getInstance().getXRod("esquire.entity");
rod.transmit(event);                             // event is a RodEvent you built
```

### Step 3 -- receive messages (consumer)

Attach a handler; the framework runs it for every message that arrives:

```java
IXRod rod = MessagingBus.getInstance().getXRod("esquire.entity");
rod.setWorker(this::onEvent);                    // onEvent(RodEvent) runs once per message
```

### Step 4 -- run

At startup the framework BUILDS every bus your service declared a role for (paused), then opens them all at once.
If a bus is mis-configured it fails at boot with a clear message -- not hours later on the first message. You
never wrote a JMS / Kafka / Redis line.

That is it. Swap `provider: activemq` for `kafka` or `redis` and the SAME code runs over a different broker.

---

## 2. Moving an existing JMS / Kafka caller onto the bus

If you have code that talks to a broker directly -- a Spring `JmsTemplate`, a `KafkaTemplate`, a `@JmsListener` --
here is how to retire it onto the bus. The bus replaces the client entirely; you stop owning the connection.

**Before (raw JMS):**
```java
jmsTemplate.convertAndSend("esquire.entity.broadcast", payload);
```

**After (bus):**
```java
MessagingBus.getInstance().getXRod("esquire.entity").transmit(event);
```

Step by step:

1. **Delete the raw client wiring.** Drop the `JmsTemplate` / `KafkaTemplate` bean and any `@JmsListener` /
   `@KafkaListener`. The bus's driver module (`tp-activemq` / `tp-kafka` / `tp-redis`) already switches OFF
   Spring's matching auto-config, so a leftover bean would only fight the bus -- remove it.
2. **Describe the destination as a bus** (Step 1 above): the broker URL becomes `transport.endpoint`, the
   queue / topic name becomes `transport.destination`, and any vendor tweak (client id, prefetch, topic-vs-queue)
   becomes a `transport.params.*` entry -- verbatim, no code.
3. **Send through the facade** instead of the template -- the two-line swap above.
4. **Receive with `setWorker`** instead of a `@JmsListener` method: declare a `role`, get the rod, attach your
   handler. The handler is given a `RodEvent` (the one envelope for every message) instead of a raw JMS `Message`.
5. **Keep your payload** -- put it in the event body; the framework carries it as JSON on the wire.

What you GAIN: the same code over any broker, built-in health reporting, the alive keep-alive, and producer
send-retry -- all by configuration, none by hand.

---

## 3. Tuning & sizing

The bus runs on safe defaults; you touch these only when load, or a slow keep (database), tells you to. They are
all per-leg `x-rod` knobs -- `services.configuring.md` has the full list; here is the plain version.

| Knob | Default | What it does | When to change |
|---|---|---|---|
| `feed-capacity` | 4096 | how many outgoing messages can queue before a send blocks the producer | raise if bursty producers hit the cap; lower to push back-pressure sooner |
| `receiver-pool.size` | 4 | how many messages are APPLIED at once (the concurrency cap on the receive side) | keep it **<= the keep's DB pool**; more appliers than DB connections just queue |
| `receiver-pool.mode` | platform | the thread type for that pool (`platform` / `virtual`) | leave `platform`; virtual threads buy nothing for these small DB-capped pools |
| `concurrency` | 1 | the transport listener's own threads (how fast messages are pulled off the wire) | raise to pull faster once appliers are keeping up |
| `publisher-pool.size` | 0 | `0` = send on the single feed worker; `>0` = a parallel async publish pool | raise only if one slow destination is starving others on the single feed |
| `send-retry-backoff-sec` | 1,2,5,5 (seconds) | the wait ladder between resend attempts while the broker is down | lengthen for a flaky broker; the last step repeats |

Two rules worth keeping in your head:

1. **The apply pool is capped by the database, not the CPU.** A `receiver-pool.size` bigger than the keep's DB
   connection pool just makes appliers wait on a connection -- size the two together.
2. **Virtual threads are not a free speed-up here.** These pools are small and database-bound, so `platform` is
   the right default. Virtual threads only help a leg that would otherwise hold MANY blocked threads at once
   (the full reasoning is in `Esquire.HighAvailability.md` section 5.5).
