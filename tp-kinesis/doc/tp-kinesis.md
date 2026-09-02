# tp-kinesis -- the Amazon Kinesis transport provider

What this driver does, and the parts of Kinesis that decide how a bus must be configured on it. The generic
bus design is in [`doc/Esquire.MessagingBus.md`](../../doc/Esquire.MessagingBus.md); this file is the vendor
specifics, the companion to [`tp-sqns/doc/tp-sqns.md`](../../tp-sqns/doc/tp-sqns.md).

---

## 1. What Kinesis is, in bus terms

A **stream** is an append-only log split into **shards**. A producer writes a record with a **partition key**;
the key decides the shard. A consumer takes an **iterator** on a shard and calls `GetRecords` in a loop.

Three consequences, and every configuration rule below comes from one of them:

1. **Order exists only inside a partition key.** Two keys are two shards, read by two threads, applied at the
   same time. There is no stream-wide order.
2. **Nobody keeps your position.** A queue remembers what it handed out; a stream does not. The reader owns
   its place in the log.
3. **Its poll does not wait.** `GetRecords` answers at once, empty or not -- there is no wait-for-a-record
   mode, so the reader paces itself.

Both legs are real: producers write, auKeep reads. Kinesis is a **transport**, the same shape as ActiveMQ or
Kafka carrying the audit bus -- not a store like the Redis stream, where the log itself is the destination.

### The word "partition" moves sides -- Kafka vs Kinesis

The two vendors use it for different things, and this driver sits one section away from `tp-kafka` in these
docs, so it is worth stating plainly:

| | the real log resource | the routing input on a record |
|---|---|---|
| **Kafka** | **partition** | key (`TransportMessage.key`) |
| **Kinesis** | **shard** | **partition key** (`partition-by`) |

**Kafka's partition IS Kinesis's shard.** In Kafka the word names the channel; in Kinesis it names the key
that CHOOSES the channel. A reader who carries the Kafka meaning across will read `partition-by` as "which
channel" and set it expecting isolation -- and get the opposite, because a key only orders records against
each other, it does not separate them.

**A partition key is not a channel.** A shard owns a contiguous range of a 128-bit hash space (four shards,
four ranges, together covering all of it), and a key is MD5-hashed into that space. So the mapping is
many-to-one by construction: thousands of distinct keys land on the same shard, share its capacity, and
interleave in its log. What a key buys is ORDER within itself, never isolation from anything else.

---

## 2. Writing -- and why the default is FIFO

Every record needs a partition key; Kinesis has no "unpartitioned". So FIFO is expressed as **one constant
key for the whole stream**, and that is what the driver does when `partition-by` is absent.

```
partition-by ABSENT  ->  one key   ->  one shard   ->  one ordered sequence      (the default)
partition-by NAMED   ->  the value of that header  ->  spread across shards
```

**Naming a header is opting IN to giving up total order.** It is safe only where records do not depend on one
another. The audit bus is such a case -- rows depend on nothing, so `partition-by: EntityID` keeps each
entity's history ordered and spreads the load. A broadcast that BUILDS something is not, because a parent
must be applied before its child.

That was found by running it, not by reading it:

| partition key | what broke |
|---|---|
| `EntityID` | an office and the user created inside it are different keys, so different shards; the child was applied first and the cache refused it -- `folder not found in cache` |
| `RodID` | fixed within one producer, but enyMan and pacMan are different rod-ids, so a pacMan account update still overtook the enyMan create it depended on |
| *(absent)* | one key, one shard, in order -- correct |

A message that does not carry the named header gets a random key. That spreads it rather than refusing it: a
record is worth more written unordered than not written at all.

---

## 3. FIFO is only half the job

Ordering the wire buys nothing if the far end applies four records at once.

```
   the stream (ordered)          the rod's receive pool
   +---+---+---+---+             size 4  ->  four applied together, order lost
   | 1 | 2 | 3 | 4 |  ------->   size 1  ->  applied one after another, order kept
   +---+---+---+---+
```

`GetRecords` hands over a whole **batch**, so a pool of four starts four of them together. An ordered bus
therefore needs **`receiver-pool.size: 1`** as well as one partition key.

A blocking poll hides this rather than solving it: an SQS receive returns the moment a message arrives, so
messages land one at a time, milliseconds apart, and a multi-worker pool rarely holds two related records at
once. The hazard is the same; only the timing differs.

---

## 4. Reading -- the position nobody keeps

```
  start()
    |
    +-- ListShards                    -> every shard, not just the first
    |
    +-- one poll thread per shard
          |
          +-- GetShardIterator(iterator-type)
          |
          v
        loop:  GetRecords  ->  deliver each record  ->  next iterator
               empty read  ->  wait poll-millis
```

**The position lives in memory.** Keeping it properly means the Kinesis Client Library and its **DynamoDB
lease table** -- a second AWS service, a table per application, and its own bill. That is refused here: the
audit bus already accepts the same loss over ActiveMQ and SNS, where a broker restart drops whatever auKeep
has not yet drained.

So on a restart a leg begins where `iterator-type` says:

| `iterator-type` | on restart | use it when |
|---|---|---|
| `TRIM_HORIZON` | re-reads the whole retained window | a record must not be missed; repeats are dropped by the dedup key. The audit default. |
| `LATEST` | takes only what arrives next | a repeat is harmful -- replaying old entity events into a cache tells it about changes that already happened |

A shard whose iterator comes back `null` was closed by a reshard; that thread ends. A failed read logs, waits,
and takes a fresh iterator -- a stale iterator is the likeliest cause.

---

## 5. Latency -- and WHO does the waiting

Both transports are read by a consumer that asks for messages. What differs is **who waits**, and the request
objects say it outright:

```
  ReceiveMessageRequest :  maxNumberOfMessages, visibilityTimeout, waitTimeSeconds   <- a wait knob
  GetRecordsRequest     :  shardIterator, limit, streamARN, streamId                 <- no wait knob at all
```

**`GetRecords` has no wait parameter, because Kinesis has no blocking read.** It answers at once with
whatever is in the shard at that instant; an empty shard returns an empty list immediately, not a held
connection.

So the waiting is OURS, in our own JVM -- `Thread.sleep(poll-millis)` on the shard's poll thread, between
calls. AWS is not holding anything, and nothing can wake that sleep early.

| | a record arrives 1ms after an empty read |
|---|---|
| SQS | the request is held OPEN by AWS and returns at once -- latency in milliseconds |
| Kinesis | the reader is asleep; the record waits for the next `GetRecords` -- up to `poll-millis` |

**`GetRecords` is capped at five calls a second per shard, so 200ms is the floor** -- and it is the default.
Sleeping less earns `ProvisionedThroughputExceededException`, not lower latency.

### Every transport we carry, and who does the waiting

| transport | protocol | who waits |
|---|---|---|
| ActiveMQ | OpenWire over TCP (61616), one persistent connection | the BROKER -- it pushes to a registered listener |
| Kafka | its own binary protocol over TCP (9092, 9093 with TLS), persistent | the BROKER -- it holds the fetch until `fetch.min.bytes` or `fetch.max.wait.ms` |
| SQS | HTTPS/1.1, AWS JSON, one call per receive | AWS -- it holds the request until a message lands or `waitTimeSeconds` |
| **Kinesis `GetRecords`** | HTTPS/1.1, AWS JSON, one call per read | **NOBODY -- the reader sleeps in its own JVM** |
| Kinesis `SubscribeToShard` | HTTP/2, AWS event-stream framing, one long-lived stream | nobody waits -- AWS PUSHES (enhanced fan-out; declined, see below) |
| Redis Streams | RESP over TCP (6379), persistent | n/a -- `tp-redis` is producer-only, so it has no receive leg |

**Kinesis's standard read is the only one where the waiting is the client's.** Every other transport we carry
either pushes or holds the request open, which is why every other driver gets its arrival latency for free and
this one has a `poll-millis`. It is also why `tp-activemq` has a `TransportListener` (a broker pushes, so
there is a connection state to listen to) and the AWS drivers have nothing of the kind.

The protocol column explains the shape of each driver as much as the latency does: a persistent TCP
connection is what lets ActiveMQ and Kafka observe their own connection health, while an HTTPS call per read
leaves the AWS drivers with only the outcome of the last call to go on.

That is the honest limit of this transport for a broadcast: a client that writes and immediately reads the
result back can lose the race by 200ms. Nothing is lost or misordered; it arrives slightly later. It is also
why the entity broadcast sits on SNS, whose queue-backed receive returns in milliseconds, while the audit bus
-- where 200ms to write a log row matters to nobody -- sits here.

(The push in the SNS path is upstream of all this: SNS pushes into the QUEUE. The consumer still pulls from
that queue; it simply gets to pull in a way that waits.)

### Kinesis CAN push -- we declined it

`SubscribeToShard` (enhanced fan-out) is a genuinely pushed read: HTTP/2, records pushed to the consumer,
about 70ms, and a dedicated 2 MB/s per consumer per shard rather than a share of the shard's read budget.

It is not a setting on this driver, and could not be one:

- `subscribeToShard` exists only on `KinesisAsyncClient`; this driver is built on the sync `KinesisClient`
- it is an HTTP/2 event stream driven by a response handler -- callbacks, not a request/response loop
- `SubscribeToShardRequest` needs a `consumerARN`, so a consumer must be REGISTERED (and reach ACTIVE, and be
  deregistered on shutdown); twenty per stream is the limit
- a subscription lasts five minutes and must then be renewed from the last sequence number, which makes the
  position this driver keeps only in memory load-bearing
- it bills per consumer-shard-hour plus per GB retrieved, on top of the stream

So it is a second consumer class beside this one, chosen by a leg param -- written up as **messaging CD item
21**, not built.

---

## 6. What rides on the wire

**The whole header bag is the record data, as JSON.** The stream is what is read back, so what is written is
what is read. `traceparent` and `correlationId` ride in the bag like every other field and survive the hop
untouched, which is what keeps one trace whole across the bus.

`ApplMsgID` is minted once and kept stable across a resend, so a held event that is sent again can be
recognised as the same record. `SendingTime` is stamped per physical send.

---

## 7. Configuration

Every value below is a transport `params` entry in the messaging-bus topology.

| param | side | default | meaning |
|---|---|---|---|
| `region` | both | the SDK default chain | the AWS region the client works in |
| `partition-by` | publish | *absent* = FIFO | the header whose value shards the stream; absent = one key, in order |
| `iterator-type` | receive | `TRIM_HORIZON` | where a leg begins when it has no position |
| `poll-millis` | receive | `200` | wait after an empty read; 200 is the Kinesis rate cap |
| `limit` | receive | `500` | records asked for per `GetRecords` |
| `noLocal` | receive | `false` | drop what this rod itself published |

Prefixed keys name the AWS call they belong to, and the key after the prefix is the vendor's own name:

| prefix | goes to | example |
|---|---|---|
| `client.` | the SDK client override | `client.apiCallTimeout: 5000` |
| `stream.` | the stream settings | `stream.RetentionPeriodHours: 48` |

The `stream.` keys the driver knows:

| key | default | meaning |
|---|---|---|
| `stream.Mode` | `PROVISIONED` | capacity mode -- `PROVISIONED` or `ON_DEMAND` |
| `stream.ShardCount` | `1` | shards to create with, under `PROVISIONED`; ignored for `ON_DEMAND` |
| `stream.RetentionPeriodHours` | the AWS default | how long records stay readable |

A key that is neither a prefix nor one of the bare params above is **refused when the leg opens**. It has
nowhere to go, and dropping it in silence is how a leg ends up running without a setting the topology says it
has.

### The stream is created PROVISIONED with one shard

A stream that already exists is left exactly as it is, capacity mode included. A new one is created
**provisioned at a single shard**, and that default is a cost decision as much as a capacity one:

| mode | what AWS charges | 1 stream, us-east-1, per month |
|---|---|---|
| `PROVISIONED` | **per SHARD-hour** -- $0.015 -- plus PUT payload units | **$10.95** |
| `ON_DEMAND` | **per STREAM-hour** -- $0.040 -- plus $0.08/GB in, $0.04/GB out | **$29.20** |

**An idle stream is not free in either mode.** On-demand needs no shard count and grows by itself, but it
bills every hour it exists whether or not a record moves -- 2.7x the standing rate of a single shard. And
elasticity is not what this transport is shaped for: absent `partition-by` the stream is FIFO, which is one
key and therefore one shard however many are provisioned. Naming `stream.Mode: ON_DEMAND` is opting IN to
growth, and to its hourly cost.

Raise `stream.ShardCount` when `partition-by` is set and the load genuinely needs the throughput -- one
shard carries 1 MB/s and 1000 records/s.

**The endpoint** is set only for LocalStack. Against real AWS it is left out and the SDK builds the endpoint
from the region. **Credentials are never in a file:** the SDK default credential chain supplies them
everywhere.

---

## 8. The two receive filters

Kinesis filters nothing on the server -- every reader of a shard gets every record on it. So the subscription
the consumer asked for and `noLocal` are both applied in code, by the same two filters `tp-sqns` uses:

```java
Consumer<TransportMessage> receiver = SelectingReceiver.wrap(handler, s.selector());
receiver = OwnExcluding.wrap(receiver, ownRodId, noLocal);
```

They live in `pro.mir0n.esquire.messaging.transport`, not in this module: neither question has anything to do
with Kinesis. Their grammar and their refuse-at-open behaviour are described in
[`tp-sqns/doc/tp-sqns.md` section 7](../../tp-sqns/doc/tp-sqns.md).

---

## 9. Health

The publisher and the consumer each carry an indicator, and the bus takes the worse of the two.

- A leg **seeds UNKNOWN**. `CreateStream` answering proves the control plane worked, not that a record can
  move. This is the rule every driver follows.
- A `PutRecord` or a `GetRecords` that succeeds turns it **UP**; one that fails turns it **DOWN**.
- The consumer polls continuously, so an outage shows within one poll cycle even on a bus with no traffic,
  and recovery is automatic.

Everything else observability needs -- the counters, the timers, the trace hop, the message log -- lives above
the transport seam and is the same for every driver. What this driver owes is the health above, its
`develop.` log lines, and returning the header bag intact.
