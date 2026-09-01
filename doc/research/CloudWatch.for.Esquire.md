# CloudWatch for Esquire

**What it would take to move Esquire's observability onto AWS-native CloudWatch, what it costs
compared with running our own stack, and where the boundary is.**

Written 2026-09-01, for v1.2.14 T6. Every number here was measured on a live AWS account against
Esquire v1.2.14 running on EKS, or read from the AWS pricing API. Where a figure is an estimate rather
than a measurement, it says so.

---

## 1. The question

Esquire has an observability stack: Grafana over Prometheus, Loki and Tempo, fed through an
OpenTelemetry collector. It runs on docker, on local Kubernetes, on OKE and -- since T5 -- on EKS.

T6 asks whether that can be moved onto what AWS provides instead, and what the move would cost. The
expectation going in was that it is "nothing more than configuring", because Esquire emits open
standards and names no vendor. That expectation is broadly right, and the interesting part of this
study is the two places where it is not: the money, and the questions the boards ask.

## 2. Where Esquire meets a vendor

The whole study turns on this table, which was read out of the code rather than assumed:

| pillar | how it leaves a service | so the swap point is |
|---|---|---|
| traces | OTLP over HTTP, pushed by an explicit `OtlpHttpSpanExporter` bean | the collector's exporter |
| metrics | a Prometheus text page on `/actuator/prometheus`, port 8090, pulled | whatever does the scraping |
| logs | ECS-shaped JSON on stdout | the log shipper |

No service names a backend. `ObservabilityConfig` in `common` declares the exporter endpoint and the
sampler as beans and stops there; the metric registry is a plain Prometheus registry; the log lines are
JSON on a stream. Everything downstream of those three points is deployment, and deployment on AWS
lives in AWS-only files.

**This is the design paying off.** It is also why the answer to "what changes in Esquire's Java code"
is, as far as this study could establish, nothing at all.

## 3. The support matrix

What CloudWatch and X-Ray can carry, against what Esquire actually does. Tested items are marked; the
rest are read from documentation.

### Getting data in

| capability | own stack | CloudWatch / X-Ray | verdict |
|---|---|---|---|
| ingest traces (OTLP) | Tempo | X-Ray via `awsxray` exporter | **works, tested** |
| ingest metrics (Prometheus scrape) | Prometheus | CloudWatch via `awsemf` exporter | **works, tested** |
| ingest logs (JSON on stdout) | Alloy -> Loki | CloudWatch Logs via a shipper | works, not tested here |
| Esquire's trace id accepted as-is | native | accepted, re-rendered | **works, tested** |
| service.name / instance identity | resource attributes | EMF dimensions | works |
| no change to service code | -- | -- | **confirmed** |

### Asking questions afterwards

| capability | own stack (PromQL) | CloudWatch metric math | verdict |
|---|---|---|---|
| rate over a counter | `rate()` | `RATE()` | works |
| arithmetic between two series | yes | yes, positional | works |
| top-N | `topk()` | `SORT` + `SLICE` | works |
| fill gaps, conditionals | `absent`, ternaries | `FILL`, `IF` | works |
| one line per dimension value | `sum by (x)` | `SEARCH()` returns one series per metric | works |
| **aggregate across some dimensions, keep others** | `sum by (service)` over richer series | **nothing** | **no** |
| **percentile from histogram buckets** | `histogram_quantile()` | **nothing** | **no** |
| **rewrite or join on labels** | `label_replace`, `on()/group_left` | **nothing** | **no** |
| **full regex on label values** | `=~` anchored, alternation | `SEARCH` partial matching only | **partial** |

### Showing it

| capability | own stack | CloudWatch | verdict |
|---|---|---|---|
| time-series panels | Grafana | CloudWatch dashboards | works, rebuilt |
| log search by field | Loki / LogQL | Logs Insights, parses JSON natively | works |
| trace waterfall | Tempo in Grafana | X-Ray trace view | works |
| service graph | `servicegraph` connector -> Grafana | **X-Ray Service Map, native** | works |
| log line tied to its trace by id text | yes | **broken by rendering** (see 4.1) | **no** |
| **topology drawn on `ComponentModel.svg`** | Grafana canvas, SVG inlined | **no canvas, no SVG** | **cannot exist** |
| alerting | Prometheus / Grafana rules | CloudWatch alarms | works |

### Operating it

| capability | own stack | CloudWatch | verdict |
|---|---|---|---|
| off means zero cost | node group -> 0, instance terminated | stop emitting; billing stops that hour | both work |
| anything left behind when off | nothing -- charts run on emptyDir | **log groups, dashboards** | CloudWatch needs a sweep |
| retention control | Loki / Tempo config | per log group; metrics fixed at 15 months | works |

## 4. What had to be tested

Four things could not be reasoned about, and each was measured.

### 4.1 Does X-Ray accept Esquire's trace ids? -- the claim that was overturned

This was the one item that could have forced a change in shared code, and the study began by expecting
it to.

A W3C trace id, which is what the OpenTelemetry SDK generates, is 16 random bytes. **X-Ray's trace id
is also 16 bytes, but it reads the first four as the epoch second the trace began**, and renders the
whole as `1-<8 hex>-<24 hex>`. A random id therefore carries a nonsense timestamp -- anywhere from 1970
to 2106 -- and X-Ray is documented to care about trace age. Had X-Ray rejected those, the fix would
have been an X-Ray-compatible id generator installed into the SDK, which lives in
`common/ObservabilityConfig.java` and is shared by every topology Esquire runs.

**It was tested, and the expectation was wrong.** A collector with the `awsxray` exporter was given
five spans whose trace ids began `00000001`, `40000000`, `80000000`, `ffffffff` and the current epoch
-- 1970, 2004, 2038, 2106 and now. All five were stored. `batch-get-traces` returned every one and
reported nothing unprocessed. The exporter logged no complaint.

```
sent    814ed556fb10beefa1c0d218dcf26c3e
stored  1-814ed556-fb10beefa1c0d218dcf26c3e
```

Note what that shows: the exporter translates nothing. **Same hex, same 128 bits, split by two dashes.**
It is one identifier that two systems render differently, not two identifier schemes.

So traces are an exporter swap. **No code change, no shared file touched.**

**What the two forms do cost is a text match.** The gateway settles the correlation id so that a span's
trace id equals the id carried in the log lines, which is what ties a log line to a trace. X-Ray shows
`1-814ed556-fb10beef...`; the log line carries `814ed556fb10beef...`. A person, or a dashboard link,
searching for one will not find the other. Making them identical is possible -- an X-Ray-shaped id is
*also* a legal W3C id, so generating that shape everywhere would satisfy both and would make the id
self-dating -- but it is a change to the id generator in shared code, and nothing requires it.

### 4.2 How many metrics does Esquire actually have?

`ObservabilityConfig`'s history header records 4,597 series with histograms off, but that was measured
for v1.2.11 on a larger shape -- two replicas, a broker, the browser tier -- and it does not describe
what runs on AWS.

Measured on v1.2.14 on EKS, scraping each service's management port directly:

| service | series | metric names |
|---|---|---|
| gateway | 82 | 46 |
| keySmith | 135 | 94 |
| enyMan | 138 | 95 |
| pacMan | 135 | 94 |
| bizTree | 137 | 95 |
| kcMaster | 105 | 64 |
| auKeep | 145 | 98 |
| **total** | **877** | **108 distinct** |

With histograms on -- the `full` arm -- the same fleet emits **1,774 series**, almost exactly double.

**The distinction between 108 and 877 is the whole billing question**, because they count different
things. 108 is the *inventory*: how many metric names exist. 877 is the number of *series* -- each name
multiplied by every distinct combination of labels it carries. `jvm_memory_used_bytes` is one name and
about ten series per JVM. CloudWatch charges per series, because a CloudWatch metric is one unique name
plus one exact set of dimension values -- the same unit as a Prometheus series. **Counting names
instead of series understates the bill eightfold.**

### 4.3 Does the EMF exporter publish one metric per series?

It does not. It publishes **2.27 times as many**, and the multiplier is a default nobody chooses.

`awsemf`'s `dimension_rollup_option` defaults to `ZeroAndSingleDimensionRollup`: for a series carrying
N labels it publishes the full-dimension metric *and* a rollup per single dimension. That is
documented; what needed the account was what it does to this fleet.

A collector was pointed at all seven live services and exported to CloudWatch with stock settings:

```
Prometheus series scraped     877
CloudWatch metrics created  1,989      2.27x
distinct metric names         100
```

The dimension breakdown shows the rollups directly -- the 85 single-dimension metrics are aggregates
nobody asked for:

| dimensions | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|
| metrics | 85 | 1,158 | 178 | 360 | 166 | 25 | 17 |

Setting `dimension_rollup_option: NoDimensionRollup` returns it to one metric per series. **One line of
exporter configuration more than doubles the bill, and it is invisible unless you count the metrics on
the account afterwards.**

### 4.4 How much log data does Esquire produce?

Measured from the pods, since CloudWatch Logs bills on volume.

```
idle          15,378 bytes/min across all pods   =  0.62 GB / month
under load     7,269 bytes per request           =  $0.0034 per 1,000 requests
```

The per-request figure includes the idle baseline, so it is an upper bound. At 0.62 GB a month the idle
stream sits **inside the 5 GB free tier**: logging into CloudWatch is free until traffic becomes real.

## 5. The budget, item by item

Prices from the AWS pricing API on 2026-09-01, us-east-1. The comparison is like for like: the same
seven Java services, the same 877 series, the same 0.62 GB of logs.

### Our own stack

Everything runs on one dedicated node, `esq-o11y`, a `t4g.medium`. That single cost carries all seven
components, every series, every log line and every dashboard.

| item | what it costs |
|---|---|
| Prometheus, Loki, Tempo, Alloy, OTel Collector, postgres-exporter, Grafana | share one node |
| storage | `emptyDir` -- no volumes, nothing outlives the pods |
| **compute: 1 x `t4g.medium`** | **$0.0336 / hour = $24.53 / month** |
| **total, running** | **~$25 / month** |
| **total, switched off** | **$0** -- the node group scales to zero and the instance terminates |

### CloudWatch and X-Ray

| item | measured input | what it costs |
|---|---|---|
| metrics, stock exporter settings | 1,989 metrics @ $0.30 | **$597 / month** |
| metrics, `NoDimensionRollup` | 877 metrics @ $0.30 | **$263 / month** |
| metrics with histograms on | ~1,774 metrics @ $0.30 | ~$532 / month (extrapolated) |
| logs | 0.62 GB/month, 5 GB free | **$0** |
| traces | demo volume, $5 per million stored | **cents** |
| dashboards | 1 built, first 3 free | **$0** -- see below |
| collectors / shipper | still run on our own nodes | unchanged |
| **total, running (best case)** | | **~$263 / month** |
| **total, switched off** | | **~$0**, after deleting log groups and dashboards |

### Side by side

| | own P/G stack | CloudWatch / X-Ray |
|---|---|---|
| running, per hour | **$0.034** | **$0.36** (best case) / $0.82 (stock settings) |
| running, per month | **~$25** | **~$263** / $597 |
| switched off | **$0** | **~$0**, plus a sweep |
| a 3-hour look | **$0.10** | **$1.08** / $2.45 |
| what drives the cost | one node, fixed | every series, every GB, every dashboard |

**CloudWatch costs about ten times more to run, and the reason is structural.** Our stack is a single
fixed cost that carries any number of series; CloudWatch is priced per unit. The crossover is easy to
state: at $0.30 per metric-month, CloudWatch is cheaper than a `t4g.medium` **only below about 80
series**. Esquire emits 877.

**FIXED against METERED is the whole comparison, and it is not only about the money.**

CloudWatch is metered on four separate dimensions -- per series, per gigabyte of logs, per million
traces, per dashboard. The freeware stack has no per-unit pricing of any kind: dashboards are free at
any number, series at any count, logs by the gigabyte, traces by the million. Its bill is the node,
and that number does not move whether it carries one dashboard or three hundred, 877 series or 8,770.

So the freeware stack is not *free*, it is **fixed** -- and the two shapes fail in opposite directions:

| | metered (CloudWatch) | fixed (our own node) |
|---|---|---|
| can you run out of capacity | no | **yes** |
| can you run up a bill unnoticed | **yes** | no |
| what growth costs | a slope -- every new series bills | a step -- until a bigger node is needed |
| what to watch | the series count | the node's headroom |

Neither risk is theoretical. The metered one is the `dimension_rollup_option` default in section 4.3,
which more than doubled the metric count with nobody choosing it. The fixed one is T5: the seven
components landing on the app nodes took CPU requests to 95% and 99%, and the scheduler moved keySmith
onto a node that could not reach the bus -- which surfaced as one failed request in twenty-four.

For completeness, Amazon Managed Prometheus prices the same 877 series at roughly **$72 a month** --
between the two, because it bills for samples ingested rather than for a series existing.

### What a dashboard costs

CloudWatch charges **$3.00 per custom dashboard per month, prorated by the hour, with the first three
free.** The starter dashboard built for this study -- seven panels: JVM heap and live threads per
service, entity operations, HTTP requests, GC pause, async work, and a live log table -- therefore
costs **nothing**, and so would two more.

**What it costs by the number of dashboards**, which is the only variable -- panels inside a dashboard
are free, so a board with forty widgets costs exactly what an empty one costs:

| dashboards | $ / month | note |
|---|---|---|
| 1 | **$0** | what this study built |
| 3 | **$0** | the free tier, exactly |
| **4** | **$3** | the first one that costs anything |
| 6 | $9 | |
| 10 | $21 | |
| 20 | $51 | |

Set against the metrics bill on the same fleet -- $263 to $597 a month -- **dashboards are noise.**
Twenty of them cost less than a tenth of what the metrics cost, and Esquire would never have twenty.
The number to watch is series, not boards.

On our own stack the column does not exist: Grafana serves any number of dashboards for the price of
the node, so 3 or 300 costs the same $25 a month.

Three things about the price have a practical edge:

- **It is charged whether or not anyone opens it.** A dashboard is not like a metric, which bills only
  in the hours it receives data; a dashboard bills for existing. Prorated hourly, so one deleted the
  same day costs pennies -- and one forgotten costs $3 every month, quietly.
- **It survives the teardown.** A dashboard is an ACCOUNT-level object, not part of any helm release,
  so `aws-o11y-cw-off.bat` removing every chart and deleting both log groups leaves it standing.
  Convenient here, since it is the visible record of the port -- and exactly how a fourth dashboard
  one day starts billing without anyone deciding to add a cost.
- **The free three is per account, not per set.** Esquire's own boards are three dashboards. Ported
  one for one, they fit the free tier exactly, with nothing to spare.

The other side of the fence has no equivalent: Grafana on our own node is free at any number of
dashboards, because the cost is the node. Amazon Managed Grafana charges **$9 per editor per month**
and **$5 per viewer**, flat, signed in or not -- the one place the managed option is worse than both
alternatives for a single-developer project.

### Turning it off

CloudWatch has no resource to delete, and that is the real difference from the rest of the AWS estate.
RDS, EKS and Amazon MQ bill for a thing that exists, so the only way off is deletion and forgetting
costs money every month. CloudWatch bills for what is sent to it:

> "All custom metrics and Detailed Monitoring charges are prorated by the hour and charges are incurred
> only when metrics are sent to CloudWatch in a given hour."

A metric stops billing in the first hour it receives nothing. There is no monthly minimum, and a
CloudWatch metric cannot even be deleted -- it goes quiet and drops out of listings after fifteen
months. But two artifacts persist, and they are the CloudWatch equivalent of an instance left running:

| artifact | when you stop sending | what it needs |
|---|---|---|
| metrics | billing stops that hour | nothing |
| X-Ray traces | fixed 30-day expiry, no storage charge | nothing |
| **log groups** | **keep billing $0.03/GB-month; retention defaults to never expire** | delete, or set retention at creation |
| **dashboards** | **$3/month each beyond the free three** | delete |

The danger is inverted compared with EKS. There the risk is a large instance somebody forgot. Here
everything expensive stops by itself, and what is left is small, cheap and **silent** -- a log group
holding data at three cents a gigabyte, which nobody notices because it never looks like a running
thing. Not hypothetical: the probe in section 4.3 created `/esquire/emf-probe`, which would have sat
there indefinitely. It was deleted at the end of the study.

## 6. Where the boundary is

Everything above says the pipes fit. The limit is not getting data into CloudWatch; it is what can be
asked of it afterwards.

Esquire's boards are three dashboards, 77 panels and **168 PromQL expressions**. CloudWatch metric math
is a different language with a smaller vocabulary -- the support matrix in section 3 lists what it has.
What it lacks, in the order that matters here:

- **Partial aggregation.** `SUM` over an array collapses it to one series. There is no way to sum across
  some dimensions while keeping others -- `sum by (service)` over series that also carry uri and status
  has no expression. 48 of the 168 group this way.
- **Quantiles from histogram buckets.** Nothing computes a percentile from `_bucket` series. CloudWatch
  has percentile statistics, but on values it collected itself. The 4 `histogram_quantile` panels need
  a different design, not a translation.
- **Label rewriting or joining.** No `label_replace`, no `label_join`, no matching two metrics on their
  labels. 9 expressions use the first, 2 the second.
- **Full regex on label values.** `SEARCH` does partial matching, not anchored alternation. 76
  expressions use a regex matcher; the simple ones survive.

Counting expressions using at least one construct with no metric-math equivalent: **84 of 168.** That
is a census of PromQL features against a documented function list, not 168 attempted translations, and
should be read as scale rather than a precise verdict.

One board does not port at any price. **The topology view is a Grafana canvas with `ComponentModel.svg`
inlined into it** -- the live service graph drawn over the architecture diagram. CloudWatch dashboards
have no canvas widget and no SVG. That panel cannot exist in CloudWatch. It can only exist in a Grafana
that reads CloudWatch.

Against that, one thing arrives for free: **X-Ray's Service Map is a native equivalent of the
`servicegraph` connector**, discovering the same caller-to-callee edges without the collector doing it.

## 7. What has to change in Esquire's code

Nothing this study could find.

| pillar | change in Java |
|---|---|
| traces | none -- the collector's exporter is swapped |
| metrics | none -- something scrapes the page that already exists |
| logs | none -- something ships the JSON already written |

The work is a log-shipper chart, a collector configuration, and IAM permissions on the node role, all
in AWS-only deployment files. The one code-shaped item in the whole study -- the X-Ray id generator --
was removed by the test in section 4.1.

## 8. What it would take

By Esquire's own cycle: write it, test it, run it on the cluster, verify it live, document it.

| item | days | is it configuration? |
|---|---|---|
| logs: shipper chart, IAM, log groups, retention | 1 | yes |
| traces: collector exporter to X-Ray, IAM | 0.5 | yes |
| metrics: prometheus receiver, `awsemf`, rollup set deliberately | 0.5 | yes |
| choosing which of 877 series are worth publishing, and on what dimensions | 1.5 | **no -- judgment** |
| boards: what survives in CloudWatch, and what is redesigned | 3-4 | **no -- rebuild** |
| the on/off arms, and the sweep that deletes log groups and dashboards | 1 | mixed |
| verification: e2e, hauberk, panel by panel, a cold off/on cycle | 1 | -- |
| documentation | 0.5 | -- |

**Total 9-10 days**, of which about 2 are configuring and the rest is deciding and rebuilding. The range
narrowed from an earlier 8-12 because the trace-id risk was tested away.

One judgement worth stating plainly: **the boards are the project.** The pipes took an afternoon to
prove. Deciding which of 168 questions can still be asked in a language that cannot group by a subset
of dimensions is the actual work, and it cannot be estimated precisely until attempted.

## 9. Claims this study overturned in its own writing

Recorded rather than edited away, because the corrections are the evidence that the testing was real.

1. **"X-Ray will reject Esquire's trace ids, so the SDK id generator must change."** Tested: five ids
   spanning 1970 to 2106, all accepted. The only claim that would have forced a shared-code change,
   and it was wrong.
2. **"The fleet emits 4,597 series, so CloudWatch costs $1,379 a month."** A v1.2.11 number for a
   different shape, quoted without re-measuring. Measured for v1.2.14 on AWS: 877 series, $263 a month.
   Wrong by a factor of five, and corrected by one question: where did the number come from?
3. **"A metric might be billed for a whole month once it receives any data."** Raised as an unverified
   risk with a 200x spread on the cost of a test. AWS's pricing page settles it: prorated hourly,
   charged only in hours when data is sent.
4. **"The exporter's metric count cannot be reasoned about and must be measured."** Half wrong. The
   multiplication is a *documented default* and reading the documentation would have predicted it. Only
   the multiplier for this fleet needed the account.

The pattern in all four: what a vendor *accepts* has to be tested, and what a vendor *charges or
configures* is written down and should be read. Guessing at the first is how three earlier AWS claims
went wrong in this sprint; testing the second is slow.

## 10. What was not tested

So the limits of the above are visible:

- **The full pipeline has not been run on the cluster.** Traces were proven through a collector to
  X-Ray, metrics through a collector to CloudWatch, both from a workstation against the live services.
  No log shipper was deployed and no board was rebuilt.
- **The 84-of-168 figure is a feature census**, not 168 attempted translations.
- **The histograms-on CloudWatch count is extrapolated** from the measured 2.27x, not observed.
- **Only the seven Java services were counted.** The browser tier, KeyCloak, the database exporter and
  the collector's own metrics are not in the 877.
- **The dashboard price** comes from the pricing page, not the pricing API, which would not
  return it under any usagetype tried. Everything else here is API-sourced or measured.

## 11. The answer

**Everything Esquire emits, CloudWatch will take -- with no change to Esquire's code. It costs about
ten times more to run, and it cannot answer everything the boards currently ask.**

The seam was built at the standard rather than at a product, so traces, metrics and logs all reach
CloudWatch as configuration. The bill on the measured fleet is $263 a month against $25 for the same
data on our own node, a dollar an hour to experiment with, and nothing when switched off -- with two
small artifacts to sweep so that off stays off.

The boundary is on the reading side, not the writing side: no grouping by a subset of dimensions, no
percentiles from buckets, no label rewriting, and no canvas to draw the topology on.

**So the port is worth taking to the edge of what CloudWatch supports, and the edge is the dashboards.**
