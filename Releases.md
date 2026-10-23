<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>


## Release Notes

### v2.0.1 -- complete (10/23/2026)

v2.0.1 opens the **event-driven business service**. The gateway used to call pacMan, the account service,
over HTTP. Now it sends the account commands over [Herald](https://github.com/mir0n-pro/herald) to a new
service, paacMan. paacMan runs pacMan's code unchanged and answers on the same bus. The client calls the same
REST API, and the screens do not change.

Processing a business request has to be fast. Every HTTP call between services costs time: a connection,
the servlet chain, a second token check. The bus skips that work. To get there, the services read the
caller's roles from the request instead of the token, so one set of commands serves both faces.

Herald 2.0.1 ships with it. The bus settings are read under Herald's own name, `herald`, and a command that
waits in the broker longer than the gateway waits is dropped, so it never runs late. The rename has a
price: a configuration written for v2.0.0 moves its bus settings under `herald`.

paacMan runs on docker and on local Kubernetes, classic and compact, and on OKE in place of pacMan.

### v2.0.0 -- complete (10/18/2026)

v2.0.0 is the **split** sprint: the parts of Esquire that any system could use become products of their own,
Armoury and Herald, and Esquire takes both from GitHub Packages.<br>
[More Details: v2.0.0 README](https://github.com/mir0n-pro/esquire.services/tree/release/v2.0.0?tab=readme-ov-file#project-structure)

### v1.2.16 -- complete (10/11/2026)

v1.2.16 is a small sprint: the **landing page on a phone**, and the token relay's cached sign-in tokens kept
in the framework's shared expiring cache.<br>
[More Details: v1.2.16 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.16?tab=readme-ov-file#project-structure)

### v1.2.15 -- complete (09/06/2026)

v1.2.15 is a small sprint: several minor fixes in the **user activation** routine -- the parts that were
awkward to work through or left the operator guessing.<br>
[More Details: v1.2.15 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.15?tab=readme-ov-file#project-structure)

### v1.2.14 -- complete (09/01/2026)

v1.2.14 is the **AWS** sprint: Esquire runs on Amazon's managed Kubernetes and on that cloud's own messaging,
database and monitoring services -- none of it built in, all of it attached where a deployment asks for it,
so a deployment that is not on Amazon carries none of it.<br>
[More Details: v1.2.14 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.14?tab=readme-ov-file#project-structure)

### v1.2.13 -- complete (08/27/2026)

v1.2.13 is the **compact topology and hardening** sprint: the same framework runs as eight programs, as five
or as four, so a small deployment stops paying for a large one -- and the whole of it was then read back,
item by item, fixing what had been left half-done.<br>
[More Details: v1.2.13 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.13?tab=readme-ov-file#project-structure)

### v1.2.12 -- complete (08/11/2026)

v1.2.12 is the **Entity change number** sprint: every record carries a count that goes up by one each time
it is written, so a record's history reads back in the order it really happened, a message that arrives
twice or out of turn is recognised and skipped, where a thing sits is counted apart from the thing itself,
and an audit trail recorded by different routes agrees with itself.<br>
[More Details: v1.2.12 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.12?tab=readme-ov-file#project-structure)

### v1.2.11 -- complete (07/25/2026)

v1.2.11 is the **Observability** sprint and the capstone of the whole **v1.2.x** line: one consistent way to
watch health, timing, and traffic, built into the framework and provided once for every service -- metrics,
traces, and logs tied together by a single correlation id in a single **Grafana** view, off by default and
switched on on demand -- together with a run of hardening fixes from a fresh, top-to-bottom review and the
framework's first step-by-step install guides.<br>
[More Details: v1.2.11 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.11?tab=readme-ov-file#project-structure)

### v1.2.10 -- complete (07/04/2026)

v1.2.10 is a **Resilience / Durability** sprint: every service can bound how long it waits -- on a slow
request, a stuck database call, or a stuck downstream -- and fail fast with a clear error instead of hanging;
the messaging bus gains a resend-on-failure step plus a keep-alive so a message survives a brief connection
drop; each messaging worker pool runs on ordinary or virtual threads by one setting; and the framework now
runs as **two copies of each service** in the cloud, spread across machines, so losing one keeps the site up.<br>
[More Details: v1.2.10 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.10?tab=readme-ov-file#project-structure)

### v1.2.9 -- complete (06/24/2026)

v1.2.9 is a **hardening** sprint that stabilizes the v1.2.8 Messaging Bus: each service reaches the bus through one entry point with a defined start-up sequence and configured roles, fail-fast on misconfiguration, the bus connection surfaced on each service's health check (split into "ready" and "alive") with a keep-alive, and more defensive handling of incoming messages -- plus added timestamp columns and lookup indexes and the wire-format definitions moved out of `common` into the messaging module.<br>
[More Details: v1.2.9 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.9?tab=readme-ov-file#project-structure)

### v1.2.8 -- complete (06/19/2026)

v1.2.8 is a **major refactoring** sprint built around the **Messaging Bus** -- the audit-specific, ActiveMQ-wired fan-out became a general, vendor-agnostic bus the whole services set shares (entity broadcast and the keySmith <-> kcMaster identity request/response), shipped as the reusable `esquire-messaging` library plus the pluggable `tp-activemq` / `tp-kafka` / `tp-redis` transport drivers. The keep stack was split into shared `esquire-data-keep` + `esquire-audit` libraries out of `common`, the audit-writer `xx-rod` was renamed **`auKeep`**, and a small **system-entity flag** (protects core entities from deletion) rode along.<br>
[More Details: v1.2.8 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.8?tab=readme-ov-file#project-structure)

### v1.2.7 -- complete (06/10/2026)

v1.2.7 is the **audit-logging** sprint: entity-change auditing reframed from in-database triggers into a
**pluggable, decoupled concern** -- one audit seam in the entity services with six interchangeable strategies
behind it, from DB triggers to an in-process write to a fully bus-driven pipeline (ActiveMQ / Kafka / Redis),
plus a standalone audit-writer service. It also **established the CI/CD pipeline** -- automated build-and-test,
local-cluster deploy, and a human-approved cloud release.<br>
[More Details: v1.2.7 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.7?tab=readme-ov-file#project-structure)

### v1.2.6 -- complete (06/02/2026)

v1.2.6 is an **enyMan-redundancy** sprint: instance-aware entity-id minting consolidated into enyMan, account CREATE moved from pacMan to enyMan, an async move queue, the race-8b / race-8c move-path fixes, and bizTree event work-batching.<br>
[More Details: v1.2.6 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.6?tab=readme-ov-file#project-structure)

### v1.2.5 -- complete (05/24/2026)

bizTree's cache rebuilt as the **Taijitu** recoverable cache service -- a two-monad anti-entropy
double-buffer reconciled by a periodic night-watch that rebuilds a shadow from the database, checksums
both, and self-heals drift, so a non-durable broadcast subscription suffices.<br>
[More Details: v1.2.5 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.5?tab=readme-ov-file#project-structure)

### v1.2.4 -- complete (05/14/2026)

v1.2.4 is the **stress / load / race-repro + auth-pattern sprint** -- a pure-REST Gatling 3.13
harness (codename "hauberk") becomes the platform's standard testing surface, and the gateway
gains two non-browser auth patterns -- **Vanilla Token Relay** and **Phantom Token Relay** --
alongside BFF and plain JWT.<br>
[More Details: v1.2.4 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.4?tab=readme-ov-file#project-structure)

### v1.2.3 -- complete (05/08/2026)

v1.2.3 is the **BFF sprint** -- Backend-for-Frontend tier introduced between the Angular SPA and
the Spring Cloud Gateway. The browser is now a thin client over an opaque session cookie; the
access token never leaves the cluster.<br>
[More Details: v1.2.3 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.3?tab=readme-ov-file#project-structure)

### v1.2.2 -- complete (04/20/2026)

v1.2.2 is the first complete vertical slice of the Esquire framework -- schema, services,
UI library, and frontend all built, connected, and tested as one working system.
<br>
[More Details: v1.2.2 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.2?tab=readme-ov-file#project-structure)


## Milestone Reports

| Repository | Milestone Reports |
|---|---|
| **esquire.services**| - [v2.0.1 Milestone Report](doc/reports/report_v2.0.1.md)<br/>- [v2.0.0 Milestone Report](doc/reports/report_v2.0.0.md)<br/>- [v1.2.16 Milestone Report](doc/reports/report_v1.2.16.md)<br/>- [v1.2.15 Milestone Report](doc/reports/report_v1.2.15.md)<br/>- [v1.2.14 Milestone Report](doc/reports/report_v1.2.14.md)<br/>- [v1.2.13 Milestone Report](doc/reports/report_v1.2.13.md)<br/>- [v1.2.12 Milestone Report](doc/reports/report_v1.2.12.md)<br/>- [v1.2.11 Milestone Report](doc/reports/report_v1.2.11.md)<br/>- [v1.2.10 Milestone Report](doc/reports/report_v1.2.10.md)<br/>- [v1.2.9 Milestone Report](doc/reports/report_v1.2.9.md)<br/>- [v1.2.8 Milestone Report](doc/reports/report_v1.2.8.md)<br/>- [v1.2.7 Milestone Report](doc/reports/report_v1.2.7.md)<br/>- [v1.2.6 Milestone Report](doc/reports/report_v1.2.6.md)<br/>- [v1.2.5 Milestone Report](doc/reports/report_v1.2.5.md)<br/>- [v1.2.4 Milestone Report](doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](doc/reports/report_v1.2.2.md)                                                                                                                                                                                  |
| **esquire.explorer**| - [v2.0.0 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop2/doc/reports/report_v2.0.0.md)<br/>- [v1.2.16 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.16.md)<br/>- [v1.2.15 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.15.md)<br/>- [v1.2.14 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.14.md)<br/>- [v1.2.13 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.13.md)<br/>- [v1.2.12 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.12.md)<br/>- [v1.2.11 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.11.md)<br/>- [v1.2.10 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.10.md)<br/>- [v1.2.9 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.9.md)<br/>- [v1.2.8 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.8.md)<br/>- [v1.2.7 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.7.md)<br/>- [v1.2.6 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.6.md)<br/>- [v1.2.5 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.5.md)<br/>- [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.2.md) |
| **esquire.ui.lib**| - [v1.2.15 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_v1.2.15.md)<br/> - [v1.2.11 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_v1.2.11.md)<br/> - [v1.2.3 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_v1.2.3.md)<br/> - [v1.2.2 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_2026_04_19_31750f3.md)                                                                                                                     |
| **esquire.db.seed**| - [v1.2.15 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.15.md)<br/> - [v1.2.12 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.12.md)<br/> - [v1.2.11 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.11.md)<br/> - [v1.2.10 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.10.md)<br/> - [v1.2.9 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.9.md)<br/> - [v1.2.8 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.8.md)<br/> - [v1.2.7 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.7.md)<br/> - [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.4.md)<br/> - [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.2.md)                                                                                                                          |

