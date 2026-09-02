# tp-sqns -- the Amazon SQS and Amazon SNS transport drivers

One module, two transport providers, one receive leg shared between them.

| provider name in a topology | class | carries |
|---|---|---|
| `sqs` | `pro.mir0n.esquire.tp.sqs.TransportProvider` | a point-to-point wire -- the identity request/response bus |
| `sns` | `pro.mir0n.esquire.tp.sns.TransportProvider` | a fan-out wire -- the entity broadcast |

They are one module because the **receiving half is identical**. Only the sending half differs: `sqs`
sends to a queue, `sns` publishes to a topic. Both read from a queue, with the same polling consumer.

---

## 1. What the two AWS services actually do

Read this part carefully, because the shape is NOT the shape of a broker, and describing it as one leads
straight to wrong conclusions.

**SNS does not keep messages, and it does not hand them to applications. It delivers them into another AWS
service.** A subscription names a PROTOCOL, and that protocol is the other service:

```
                          +--> sqs        a queue, which waits to be pulled
                          +--> lambda     a function, invoked with the message
   Publish --> [ topic ] -+--> email      an inbox
                          +--> http(s)    a URL, POSTed to
                          +--> sms        a phone
                          +--> firehose   a delivery stream
```

**There is no receive API.** Nothing an application runs can read from a topic; there is no `Receive` on SNS
at all. A subscription is an ADDRESS that SNS delivers to, never a consumer that connects and waits. So the
question "where does the message go?" always has the same answer: *into whichever AWS service the protocol
names*.

**That makes the protocol choice the design decision**, and it is why this driver pairs the two:

| | SNS | SQS |
|---|---|---|
| what it is | a fan-out that delivers into another AWS service | a queue that holds messages |
| where a message ends up | in the endpoint the subscription names | in the queue, until it is deleted |
| how it moves | SNS **pushes** into that endpoint | the consumer **pulls** -- `ReceiveMessage` |
| what a consumer connects to | nothing; SNS has no receive API | its own queue |
| what we call at runtime | `Publish` | `ReceiveMessage`, `DeleteMessage` |

Of the six protocols, **`sqs` is the one that waits to be pulled** -- so it is the one a rod can consume.
A queue holds a message until someone collects it, which is exactly what a service needs while it is busy or
restarting. So each consuming instance owns a **queue**, and that queue is the address SNS delivers to.

**A consumer therefore ALWAYS pulls.** Nothing is ever pushed into an Esquire service. The push in this
picture is upstream of us -- SNS into the queue -- and it is the only push there is.

**A broadcast bus declares SNS and nothing else.** The whole bus is a provider name and a topic:

```yaml
transport:
  provider: sns
  destination: esquire.entity.broadcast
  params: { region: us-east-1, route-by: RodID, noLocal: true }
```

No queue appears anywhere in a topology. The driver makes one per consuming instance, names it, wires it and
takes it down -- whoever writes or reads the bus definition sees a topic, which is what a broadcast is.

---

## 2. The subscription protocol, and why there is no token

A subscription names a protocol and an endpoint. This driver uses exactly one:

```
Protocol = sqs
Endpoint = arn:aws:sqs:<region>:<account>:esquire-entity-broadcast-enyman-0
TopicArn = arn:aws:sns:<region>:<account>:esquire-entity-broadcast
```

SNS also supports `http`, `https`, `email`, `lambda`, `sms` and others. **The `http`, `https` and `email`
protocols need a confirmation token**: SNS sends a `Token` to the endpoint and the endpoint must call
`ConfirmSubscription` to prove it wants the traffic. That handshake does not exist for `sqs`, and this driver
never sees a token.

What takes its place is the **queue policy**. SNS is a different service, and an SQS queue accepts nothing
from it until the queue's own policy says that topic may write. It is set on every wiring, scoped to the one
topic:

```json
{"Version":"2012-10-17","Statement":[{
  "Effect":"Allow",
  "Principal":{"Service":"sns.amazonaws.com"},
  "Action":"sqs:SendMessage",
  "Resource":"<the queue arn>",
  "Condition":{"ArnEquals":{"aws:SourceArn":"<the topic arn>"}}}]}
```

**LocalStack lets a write through without it; real AWS does not.** Leaving it out is the kind of mistake that
passes every local test and delivers nothing in the cloud.

---

## 3. Wiring, when a leg opens

Every call below is idempotent, so a leg that opens twice -- or a pod that restarts -- lands in the same
place. The publisher and the consumer may start in either order.

```
  the sns consumer leg opens
        |
        |-- CreateQueue              esquire-entity-broadcast-enyman-0
        |-- CreateTopic              esquire-entity-broadcast
        |-- GetQueueAttributes       (the queue arn)
        |-- SetQueueAttributes       Policy: this topic may SendMessage here
        |-- Subscribe                protocol=sqs, endpoint=<queue arn>
        |-- SetSubscriptionAttrs     RawMessageDelivery = true
        |-- SetSubscriptionAttrs     FilterPolicy       = (empty, cleared on purpose)
        |
        +-- install the receive filters:  OwnExcluding( SelectingReceiver( the rod ) )
```

Two of those need a word.

**RawMessageDelivery = true.** Without it SNS wraps what was published in an envelope, and the SQS body is
that envelope rather than the message. With it the body is exactly what was published, so the consumer
decodes it directly and the same consumer serves both providers.

**FilterPolicy is CLEARED, not merely left unset.** `Subscribe` applies attributes only when it *creates* the
subscription; on one that already exists it returns the same arn and changes nothing. A policy some earlier
deployment wrote would therefore stay for good and go on dropping messages the running code never asked it to
drop. Wiring that depends on what ran there before is wiring nobody can reason about.

---

## 4. A broadcast, end to end

One publish, one copy per subscribed queue, and each consumer pulling its own.

```
 enyMan            SNS topic                    SQS queues                 bizTree      kcMaster
   |                   |                             |                        |             |
   |--- Publish ------>|                             |                        |             |
   |   body = the      |                             |                        |             |
   |   header bag      |--- copy --> enyman-0 ------>|                        |             |
   |   (JSON)          |--- copy --> biztree-0 ----->|                        |             |
   |                   |--- copy --> kcmaster-0 ---->|                        |             |
   |                   |                             |<-- ReceiveMessage -----|             |
   |                   |                             |--- message ----------->|             |
   |                   |                             |<-- DeleteMessage ------|             |
   |                   |                             |<-- ReceiveMessage -------------------|
   |                   |                             |--- message ------------------------->|
   |                   |                             |<-- DeleteMessage --------------------|
   |<-- ReceiveMessage-|-----------------------------|                        |             |
   |    its OWN copy: dropped by OwnExcluding, never reaches the rod          |             |
```

**One queue per consuming INSTANCE, not per service.** A single shared queue would make the instances compete
for messages -- one instance would take a message and the others would never see it -- which is the opposite
of a broadcast. The queue name carries the rod-id for exactly that reason (section 6).

**The delete is the acknowledgement.** A message is removed only after the handler returned. One whose
handling failed stays hidden for the visibility timeout and is delivered again; that is the only redelivery
SQS offers.

---

## 5. Request/response over SQS

The identity bus is two nodes, and SQS has **no message selector**. Over a broker, a client takes its own
replies off one shared response queue with a selector on the rod-id. Here the filter becomes a **destination**:

```
 enyMan (CLIENT)                                                       kcMaster (SERVER)
   |                                                                        |
   |-- SendMessage --> esquire-kc-request-kc            <-- ReceiveMessage --|
   |   (routed by the SlotID on the message)                  every server of the slot
   |                                                          competes on this one queue
   |                                                                        |
   |                                                          the reply carries the
   |                                                          REQUESTER's rod-id, because
   |                                                          the server x-rod echoes it
   |                                                                        |
   |<- ReceiveMessage- esquire-kc-response-enyman-0     <-- SendMessage -----|
   |   its own queue; no other client reads it            (routed by the RodID on the reply)
```

Each node declares the header its queue splits on:

```yaml
nodes:
  - node-id: request
    destination: esquire.kc.request
    params: { route-by: SlotID }      # -> esquire-kc-request-kc
  - node-id: response
    destination: esquire.kc.response
    params: { route-by: RodID }       # -> esquire-kc-response-enyman-0
```

A publisher takes the value off the message it is sending; a consumer takes it off its own identity. Both
sides arrive at the same name without either having to know the other.

**`noLocal` has no part in this.** There are two queues: a client reads only the response queue and a server
only the request queue, so a client cannot receive its own request.

---

## 6. Queue and topic names

An SQS queue name takes letters, digits, hyphen and underscore, up to 80 characters -- **a dot is not legal**.
Bus destinations and rod-ids are dotted, so every other character becomes a hyphen.

| what | becomes |
|---|---|
| topic `esquire.entity.broadcast` | `esquire-entity-broadcast` |
| its queue for rod `enyman.0` | `esquire-entity-broadcast-enyman-0` |
| `esquire.kc.request` + `route-by: SlotID` = `kc` | `esquire-kc-request-kc` |
| `esquire.kc.response` + `route-by: RodID` = `enyman.0` | `esquire-kc-response-enyman-0` |

**A queue is made when a leg opens and is never deleted.** A queue that outlives its pod holds the messages
published while that pod was restarting; deleting it on shutdown would lose exactly those. Rod-ids are
deterministic, so the set of queues is bounded by the replica count rather than growing. A permanent
scale-down does leave an orphan queue still subscribed -- bounded by the SQS retention period, but worth a
cleanup.

**A queue that disappears is made again.** If a call fails because the queue is gone, the leg forgets the URL
and re-creates it on the next turn -- and on the SNS side re-subscribes it, so it comes back wired rather than
merely present. A leg that cannot re-establish its own queue is dead for good, which is worse than losing any
one message.

---

## 7. The two receive filters

A broker applies a subscription selector and `noLocal` itself. Neither exists here, so both are applied on the
receive side -- and they are **two separate filters**, because they answer two different questions and every
consumer brings its own subscription.

Both live in the **messaging framework**, `pro.mir0n.esquire.messaging.transport`, not in this module: the
question "did this consumer ask for it?" has nothing to do with SQS, and any driver whose vendor filters
nothing needs the same two. `tp-kinesis` composes them exactly as the code below does.

```
  a message off the queue
        |
        v
  OwnExcluding        did THIS rod publish it?          -> drop      (noLocal)
        |
        v
  SelectingReceiver   did this consumer ask for it?     -> drop      (the subscription)
        |
        v
  the rod
```

| filter | question | set by | reaches the driver as |
|---|---|---|---|
| `OwnExcluding` | is this my own publication? | the **topology** -- `noLocal: true` on the transport | a transport param, plus the leg's rod-id |
| `SelectingReceiver` | did this consumer ask for it? | the **application** -- `IXRod.setWorker(subscription, worker)` | `ConsumeSettings.selector()` |

```java
Consumer<TransportMessage> receiver = SelectingReceiver.wrap(handler, s.selector());
receiver = OwnExcluding.wrap(receiver, ownRodId, noLocal);
```

`noLocal` is a property of the WIRE -- this leg both publishes and consumes here -- so it belongs in the
topology. The selector is a property of the CONSUMER -- what this particular listener wants -- so it comes
from the application. Different sources, different lifetimes.

**The selector grammar** is the useful part of a message selector, not one caller's shape:

```
FIELD = 'v'            FIELD <> 'v'            FIELD != 'v'
FIELD IN ('a','b')     FIELD NOT IN ('a','b')
any of the above, joined by AND
```

A keyword inside a quoted value is left alone, and a field whose name contains a keyword is not mistaken for
one. **Anything outside the grammar is refused when the leg opens** -- `>`, `LIKE`, `OR`. A selector quietly
treated as "take everything" turns a narrowing into a no-op that nothing reports, which is the worst way for a
filter to fail.

**Why not an SNS filter policy.** SNS can drop messages at the subscription, and that was the first shape.
It was dropped: an SNS subscription is an ADDRESS it delivers to, not a consumer with a view of its own, so
there is nothing natural to hang a
per-consumer predicate on, and a rule living in AWS is a rule no reader of the code can see -- as the stale
policy in section 3 demonstrated.

---

## 8. What rides on the wire

**The whole header bag is the message BODY, as JSON.** SQS allows at most **ten** message attributes and the
bag carries about twenty, so attributes are not used for it.

The `sns` publisher additionally puts the publishing rod-id on as a message attribute. Nothing reads it --
the bag in the body already carries it, and that is what the receive filter reads -- but it lets anything
looking at the topic from outside tell publishers apart without opening the body.

---

## 9. Configuration

Every value below is a transport `params` entry in the messaging-bus topology.

| param | applies to | default | meaning |
|---|---|---|---|
| `region` | both | the SDK default chain | the AWS region the clients work in |
| `route-by` | both | `RodID` for `sns`, none for `sqs` | the header whose value splits a destination into one queue per value |
| `noLocal` | `sns` | `false` | drop what this rod itself published |
| `wait-seconds` | both | `20` | long-poll seconds on a receive; 20 is the SQS maximum |
| `batch-size` | both | `10` | messages per receive; 10 is the SQS maximum |

**Anything else is a PREFIXED key naming the AWS call it belongs to**, and inside the prefix the key is the
vendor's own name, passed on with no per-key code here:

| prefix | goes to | example |
|---|---|---|
| `client.` | the SDK client override | `client.apiCallTimeout: 5000` |
| `queue.` | `CreateQueue` attributes | `queue.VisibilityTimeout: 60` |
| `topic.` | `CreateTopic` attributes | `topic.DisplayName: esquire` |
| `subscription.` | `Subscribe` attributes | `subscription.RawMessageDelivery: true` |

The other drivers need no such convention because their vendor has ONE place to take a setting -- a broker
URI, a config map. AWS has four, so the prefix says which. A key that is neither a prefix nor one of the
bare params above is **refused when the leg opens**: it has nowhere to go, and dropping it in silence is how
a leg ends up running without a setting the topology says it has.

**A node that declares `params` REPLACES the transport's group whole**, so every param an R&R node needs is
declared on that node rather than above it.

**The endpoint** is set only for LocalStack. Against real AWS it is left out and the SDK builds the endpoint
from the region.

**Credentials are never in a file.** The SDK default credential chain supplies them everywhere: dummy values
from the container environment against LocalStack, and a role attached to the service account on a cluster.

---

## 10. Health

A leg reports on both sides -- the publisher and the consumer each carry their own indicator, and the bus
takes the worse of the two.

- A leg **seeds UNKNOWN**. Opening one makes real calls -- `CreateQueue`, `CreateTopic`, `Subscribe` -- but
  those prove the control plane answered, not that a message can move, and a leg that claimed UP before
  anything travelled would make `messaging.transport.up` read 1 on a bus that has never carried a message.
  This is the same rule every other driver follows.
- A send or a receive that succeeds turns it **UP**; one that fails turns it **DOWN**.
- The consumer long-polls, so an outage shows within one poll cycle even on a bus with no traffic, and
  recovery is automatic.

Everything else observability needs -- the counters, the timers, the trace hop, the message log -- lives above
the transport seam and is the same for every driver. What this driver owes is the health above, its `develop.`
log lines, and returning the header bag intact so `traceparent` and `correlationId` survive the hop.
