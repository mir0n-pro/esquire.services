
<table style="width: 100%; table-layout: fixed;"">
  <tr>
    <td style="width: 12%"><img src="favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"</td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>


## Release Notes

### v1.2.11 — complete (07/25/2026)

v1.2.11 is the **Observability** sprint — and the capstone of the whole **v1.2.x** line. Seeing what the
running system is doing arrives not as a bolted-on afterthought but as a **common, reusable layer built into
the framework**: one consistent way to watch health, timing, and traffic, provided once for every service
rather than wired up service by service.

**One pane over three pillars.** Metrics, traces, and logs are tied together by a single **correlation id**,
so a log line, its trace, and its numbers are one click apart in a single **Grafana** pane — over Prometheus
(metrics), Tempo + OpenTelemetry (traces), and Loki + Alloy (logs). The whole thing is **off by default** and
switched on on demand — locally, on the test cluster, or transiently on the cloud — so the everyday stack
carries no weight when nobody is looking. Business meters, ready-made alerts for the obvious failures (a
service gone dark, a dropped message, a tripped safety switch, a database out of spare connections), and
purpose-built topology / services / logging dashboards all ship with it.

**Warm-up, hardening, and polish.** A warm-up fix makes the shared entity-field dictionary complete safely
when several creates hit it at once. A run of hardening commits — the product of a fresh-mind audit of the
whole framework — closes real edge cases: parents-before-children move ordering, tenant-scoped parent lookup,
a create-at-the-very-end-of-a-move repair, an id counter that can no longer wrap negative, money rounded to
the ledger's scale before it ever touches the books, a full outgoing queue that waits instead of dropping,
and a cloud broker that reconnects on its own after a blip. The monitoring endpoints moved to an
internal-only port, and more log lines now carry the request's own id. A few schema-definition
corrections landed on both the Oracle and Postgres branches — among them a server-side "now (UTC)"
default so a ledger row is always stamped.

**Docs grew up too.** The design docs were refreshed end to end — the messaging-bus, observability,
high-availability, and authentication suites, with fresh sequence and state diagrams — and, for the first
time, the framework ships **step-by-step installation routines** for docker and local Kubernetes.

**Esquire is mature.** With v1.2.x complete, the framework now has everything the stack promises — a
tree-shaped security model, a self-healing recoverable cache, a vendor-agnostic messaging bus, resilient
cloud redundancy, and now full, single-pane observability — each solved once, correctly, and visible from
database to browser. It is, frankly, in superb shape: quick to stand up, honest about what it is doing under
load, and ready for whatever business domain sits on top. From here the work is support and incremental
hardening, not churn — the hard problems are behind it.<br>
[Observability Stack](doc/Esquire.ObservabilityStack.md) · [Grafana Guide](doc/Esquire.GrafanaGuide.md) · [High Availability](doc/Esquire.HighAvailability.md) · [Install &amp; Run](doc/install/Docker.md) · [v1.2.11 Release Notes](doc/release_notes.txt)<br>

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
| **esquire.services**| - [v1.2.11 Milestone Report](doc/reports/report_v1.2.11.md)<br/>- [v1.2.10 Milestone Report](doc/reports/report_v1.2.10.md)<br/>- [v1.2.9 Milestone Report](doc/reports/report_v1.2.9.md)<br/>- [v1.2.8 Milestone Report](doc/reports/report_v1.2.8.md)<br/>- [v1.2.7 Milestone Report](doc/reports/report_v1.2.7.md)<br/>- [v1.2.6 Milestone Report](doc/reports/report_v1.2.6.md)<br/>- [v1.2.5 Milestone Report](doc/reports/report_v1.2.5.md)<br/>- [v1.2.4 Milestone Report](doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](doc/reports/report_v1.2.2.md)                                                                                                                                                                                  |
| **esquire.explorer**| - [v1.2.11 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.11.md)<br/>- [v1.2.10 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.10.md)<br/>- [v1.2.9 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.9.md)<br/>- [v1.2.8 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.8.md)<br/>- [v1.2.7 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.7.md)<br/>- [v1.2.6 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.6.md)<br/>- [v1.2.5 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.5.md)<br/>- [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.2.md) |
| **esquire.ui.lib**| - [v1.2.11 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_v1.2.11.md)<br/> - [v1.2.3 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_v1.2.3.md)<br/> - [v1.2.2 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_2026_04_19_31750f3.md)                                                                                                                     |
| **esquire.db.seed**| - [v1.2.11 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.11.md)<br/> - [v1.2.10 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.10.md)<br/> - [v1.2.9 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.9.md)<br/> - [v1.2.8 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.8.md)<br/> - [v1.2.7 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.7.md)<br/> - [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.4.md)<br/> - [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.2.md)                                                                                                                          |

