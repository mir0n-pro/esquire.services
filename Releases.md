<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>


## Release Notes

### v1.2.14 — complete (09/01/2026)

v1.2.14 is the **AWS** sprint. It puts Esquire on a second cloud and, more to the point, on that cloud's own
services — the ones a framework normally gets locked into.

**Esquire runs on Amazon's managed Kubernetes.** The same images and the same charts that run on a laptop,
on Docker Desktop and on Oracle's cloud, in both the seven-program and the four-program shapes.

**The messaging bus speaks Amazon's own message services.** SNS carries the entity broadcast, SQS the
identity request and reply, Kinesis the audit log — through the same seam that already carries ActiveMQ,
Kafka and Redis. Nothing above that seam changed. The two broker drivers also reach Amazon's managed
brokers, Amazon MQ for ActiveMQ and MSK for Kafka, with no code change at all.

**None of it is built in.** No service depends on the AWS modules and no service image holds a byte of the
AWS client. The drivers are attached where a deployment wants them, so a deployment that is not on Amazon
carries none of it.

**The database runs on Amazon's managed ones.** The same Postgres profile runs on RDS for PostgreSQL and on
Aurora PostgreSQL without touching a line of code or SQL — so the choice could be made on measurement.
Esquire keeps its own database in a pod: same PostgreSQL version, 20% fewer writes per second than the
managed one, and a fraction of the price.

**Monitoring runs on Amazon's own tools.** Traces to X-Ray, numbers and logs to CloudWatch — proved by
moving a running deployment onto them without rebuilding a single image. What does not carry over is the
dashboards, because a query language is not a data format.

**Amazon's sign-in service was studied and not taken**, with the reasons written down rather than assumed.

**The sign-in server's admin credential is now required.** Every deployment path refuses to run without it
instead of quietly falling back to the published development value and reporting success.<br>
[Developer Setup](doc/Esquire.DevSetup.md) · [Messaging Bus](doc/Esquire.MessagingBus.md) · [Observability Stack](doc/Esquire.ObservabilityStack.md) · [v1.2.14 Release Notes](doc/release_notes.txt)

### v1.2.13 — complete (08/27/2026)

v1.2.13 is the **compact topology and hardening** sprint: the same framework runs as eight programs, as five
or as four, so a small deployment stops paying for a large one — and the whole of it was then read back,
item by item, fixing what had been left half-done.<br>
[More Details: v1.2.13 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.13?tab=readme-ov-file#project-structure)

### v1.2.12 — complete (08/11/2026)

v1.2.12 is the **Entity change number** sprint: every record carries a count that goes up by one each time
it is written, so a record's history reads back in the order it really happened, a message that arrives
twice or out of turn is recognised and skipped, where a thing sits is counted apart from the thing itself,
and an audit trail recorded by different routes agrees with itself.<br>
[More Details: v1.2.12 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.12?tab=readme-ov-file#project-structure)

### v1.2.11 — complete (07/25/2026)

v1.2.11 is the **Observability** sprint and the capstone of the whole **v1.2.x** line: one consistent way to
watch health, timing, and traffic, built into the framework and provided once for every service — metrics,
traces, and logs tied together by a single correlation id in a single **Grafana** view, off by default and
switched on on demand — together with a run of hardening fixes from a fresh, top-to-bottom review and the
framework's first step-by-step install guides.<br>
[More Details: v1.2.11 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.11?tab=readme-ov-file#project-structure)

### v1.2.10 — complete (07/04/2026)

v1.2.10 is a **Resilience / Durability** sprint: every service can bound how long it waits — on a slow
request, a stuck database call, or a stuck downstream — and fail fast with a clear error instead of hanging;
the messaging bus gains a resend-on-failure step plus a keep-alive so a message survives a brief connection
drop; each messaging worker pool runs on ordinary or virtual threads by one setting; and the framework now
runs as **two copies of each service** in the cloud, spread across machines, so losing one keeps the site up.<br>
[More Details: v1.2.10 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.10?tab=readme-ov-file#project-structure)

### v1.2.9 — complete (06/24/2026)

v1.2.9 is a **hardening** sprint that stabilizes the v1.2.8 Messaging Bus: each service reaches the bus through one entry point with a defined start-up sequence and configured roles, fail-fast on misconfiguration, the bus connection surfaced on each service's health check (split into "ready" and "alive") with a keep-alive, and more defensive handling of incoming messages — plus added timestamp columns and lookup indexes and the wire-format definitions moved out of `common` into the messaging module.<br>
[More Details: v1.2.9 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.9?tab=readme-ov-file#project-structure)

### v1.2.8 — complete (06/19/2026)

v1.2.8 is a **major refactoring** sprint built around the **Messaging Bus** — the audit-specific, ActiveMQ-wired fan-out became a general, vendor-agnostic bus the whole services set shares (entity broadcast and the keySmith ↔ kcMaster identity request/response), shipped as the reusable `esquire-messaging` library plus the pluggable `tp-activemq` / `tp-kafka` / `tp-redis` transport drivers. The keep stack was split into shared `esquire-data-keep` + `esquire-audit` libraries out of `common`, the audit-writer `xx-rod` was renamed **`auKeep`**, and a small **system-entity flag** (protects core entities from deletion) rode along.<br>
[More Details: v1.2.8 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.8?tab=readme-ov-file#project-structure)

### v1.2.7 — complete (06/10/2026)

v1.2.7 is the **audit-logging** sprint: entity-change auditing reframed from in-database triggers into a
**pluggable, decoupled concern** — one audit seam in the entity services with six interchangeable strategies
behind it, from DB triggers to an in-process write to a fully bus-driven pipeline (ActiveMQ / Kafka / Redis),
plus a standalone audit-writer service. It also **established the CI/CD pipeline** — automated build-and-test,
local-cluster deploy, and a human-approved cloud release.<br>
[More Details: v1.2.7 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.7?tab=readme-ov-file#project-structure)

### v1.2.6 — complete (06/02/2026)

v1.2.6 is an **enyMan-redundancy** sprint: instance-aware entity-id minting consolidated into enyMan, account CREATE moved from pacMan to enyMan, an async move queue, the race-8b / race-8c move-path fixes, and bizTree event work-batching.<br>
[More Details: v1.2.6 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.6?tab=readme-ov-file#project-structure)

### v1.2.5 — complete (05/24/2026)

bizTree's cache rebuilt as the **Taijitu** recoverable cache service — a two-monad anti-entropy
double-buffer reconciled by a periodic night-watch that rebuilds a shadow from the database, checksums
both, and self-heals drift, so a non-durable broadcast subscription suffices.<br>
[More Details: v1.2.5 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.5?tab=readme-ov-file#project-structure)

### v1.2.4 — complete (05/14/2026)

v1.2.4 is the **stress / load / race-repro + auth-pattern sprint** — a pure-REST Gatling 3.13
harness (codename "hauberk") becomes the platform's standard testing surface, and the gateway
gains two non-browser auth patterns — **Vanilla Token Relay** and **Phantom Token Relay** —
alongside BFF and plain JWT.<br>
[More Details: v1.2.4 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.4?tab=readme-ov-file#project-structure)

### v1.2.3 — complete (05/08/2026)

v1.2.3 is the **BFF sprint** — Backend-for-Frontend tier introduced between the Angular SPA and
the Spring Cloud Gateway. The browser is now a thin client over an opaque session cookie; the
access token never leaves the cluster.<br>
[More Details: v1.2.3 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.3?tab=readme-ov-file#project-structure)

### v1.2.2 — complete (04/20/2026)

v1.2.2 is the first complete vertical slice of the Esquire framework — schema, services,
UI library, and frontend all built, connected, and tested as one working system.
<br>
[More Details: v1.2.2 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.2?tab=readme-ov-file#project-structure)


## Milestone Reports

| Repository | Milestone Reports |
|---|---|
| **esquire.services**| - [v1.2.14 Milestone Report](doc/reports/report_v1.2.14.md)<br/>- [v1.2.13 Milestone Report](doc/reports/report_v1.2.13.md)<br/>- [v1.2.12 Milestone Report](doc/reports/report_v1.2.12.md)<br/>- [v1.2.11 Milestone Report](doc/reports/report_v1.2.11.md)<br/>- [v1.2.10 Milestone Report](doc/reports/report_v1.2.10.md)<br/>- [v1.2.9 Milestone Report](doc/reports/report_v1.2.9.md)<br/>- [v1.2.8 Milestone Report](doc/reports/report_v1.2.8.md)<br/>- [v1.2.7 Milestone Report](doc/reports/report_v1.2.7.md)<br/>- [v1.2.6 Milestone Report](doc/reports/report_v1.2.6.md)<br/>- [v1.2.5 Milestone Report](doc/reports/report_v1.2.5.md)<br/>- [v1.2.4 Milestone Report](doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](doc/reports/report_v1.2.2.md)                                                                                                                                                                                  |
| **esquire.explorer**| - [v1.2.14 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.14.md)<br/>- [v1.2.13 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.13.md)<br/>- [v1.2.12 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.12.md)<br/>- [v1.2.11 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.11.md)<br/>- [v1.2.10 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.10.md)<br/>- [v1.2.9 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.9.md)<br/>- [v1.2.8 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.8.md)<br/>- [v1.2.7 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.7.md)<br/>- [v1.2.6 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.6.md)<br/>- [v1.2.5 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.5.md)<br/>- [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.2.md) |
| **esquire.ui.lib**| - [v1.2.11 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_v1.2.11.md)<br/> - [v1.2.3 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_v1.2.3.md)<br/> - [v1.2.2 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_2026_04_19_31750f3.md)                                                                                                                     |
| **esquire.db.seed**| - [v1.2.12 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.12.md)<br/> - [v1.2.11 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.11.md)<br/> - [v1.2.10 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.10.md)<br/> - [v1.2.9 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.9.md)<br/> - [v1.2.8 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.8.md)<br/> - [v1.2.7 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.7.md)<br/> - [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.4.md)<br/> - [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.2.md)                                                                                                                          |

