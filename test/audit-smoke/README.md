# Audit smoke matrix — the "whole smoke test"

Reproducible smoke + audit-log validation across **every audit variant**, on docker (Postgres primary,
Oracle primary) and local k8s (Postgres primary). Companion to the perf matrix
(`doc/research/audit-perf-matrix-*.md`) — that one measures throughput; **this one proves correctness**: for
each cell it drives a small entity + parameter workload and asserts the audit landed where it should.

Driver: [`run.sh`](run.sh). Per-cell logic: configure env → recreate the audit-path containers/pods →
smoke → validate → record PASS/FAIL into a results table.

---

## What a cell does

1. **Configure** the audit env for the cell (the vars below).
2. **Recreate** the affected pieces: the producers (enyMan / pacMan / keySmith) always; `auKeep` for the
   bus-consumer sinks (c / ck); the broker is already up. For a primary-DB switch, the whole stack.
3. **Smoke** (option 1 + param check): one authenticated lifecycle through the gateway —
   **create office → update a custom parameter → move → delete** — plus an account op (deposit). This
   exercises the entity `*_log` AND the parameter `*_par_log` audit paths.
4. **Validate** the audit landed, by vendor:
   - **(a) triggers / (b) / (c) / (ck):** the `esq_*_log` AND `esq_*_par_log` row counts in the **audit DB**
     grew (entity create/update/delete/move + the param update).
   - **(d) Redis / (dk) Kafka stream:** the stream/topic `esquire.rod.audit` grew; `*_log` does NOT (no
     consumer). (Stop `auKeep` for d/dk so a leftover subscription can't write `*_log` — see the perf-matrix
     dk note.)
5. **e2e once per environment:** after the per-cell smokes, run the full Playwright suite
   (`explorer/e2e-test`, 32 specs) once against the running stack in its default audit mode.

---

## The audit dimensions (all env-driven)

| Dimension | Env var(s) | Values |
|---|---|---|
| Sink | `ESQUIRE_AUDIT_BUS_ID` | `audit-b` (in-process) / `audit-c` (AMQ) / `audit-ck` (Kafka) / `audit-d` (Redis) / `audit-dk` (Kafka stream) |
| (a) triggers | `ESQUIRE_AUDIT_BUS_ID=` *(blank → audit off)* + apply the trigger DDL to the primary DB | DB writes the `*_log` in-transaction |
| (b) shared vs dedicated | `ESQUIRE_AUDIT_LOG_DB_SHARED` | `true` = reuse the service pool / `false` = own pool |
| Audit DB (b-dedicated / c / ck) | producers: `ESQUIRE_AUDIT_LOG_DB_*`; `auKeep`: `DB_DATAKEEP_*` | `dev-postgres` (`postgres:5432/esq2025`) / `dev-oracle` (`host.docker.internal:1521/MIR0N`) |
| Primary DB | `DB_<SVC>_VENDOR` / `_HOST` / `_PORT` / `_NAME` (enyman/pacman/keysmith/biztree) | `dev-postgres` (`postgres:5432/esq2025`) / `dev-oracle` (`host.docker.internal:1521/MIR0N`) |

Creds everywhere: `esq2025` / `q`. Oracle service name `MIR0N`; Postgres db `esq2025`.

---

## The matrix

### Docker — Postgres primary (`DB_*_VENDOR=dev-postgres`, host `postgres`)

| # | Cell | `ESQUIRE_AUDIT_BUS_ID` | shared | audit DB | validate |
|---|---|---|---|---|---|
| 1 | (a) triggers | *(blank)* + trigger DDL on `postgres` | — | primary (postgres) | `*_log` + `*_par_log` grow (in-tx) |
| 2 | (b) shared | `audit-b` | true | = primary (postgres) | `*_log` + `*_par_log` grow |
| 3 | (b) dedicated · pg | `audit-b` | false | postgres | `*_log` + `*_par_log` grow |
| 4 | (b) dedicated · ora | `audit-b` | false | oracle | `*_log` + `*_par_log` grow (oracle) |
| 5 | (c) AMQ · pg | `audit-c` | — | postgres (auKeep) | `*_log` + `*_par_log` grow |
| 6 | (c) AMQ · ora | `audit-c` | — | oracle (auKeep) | `*_log` + `*_par_log` grow (oracle) |
| 7 | (ck) Kafka · pg | `audit-ck` | — | postgres (auKeep) | `*_log` + `*_par_log` grow |
| 8 | (ck) Kafka · ora | `audit-ck` | — | oracle (auKeep) | `*_log` + `*_par_log` grow (oracle) |
| 9 | (d) Redis | `audit-d` | — | — (stream) | Redis stream grows; `*_log` +0 |
| 10 | (dk) Kafka stream | `audit-dk` | — | — (stream) | Kafka topic grows; `*_log` +0 |

### Docker — Oracle primary (`DB_*_VENDOR=dev-oracle`, host `host.docker.internal`, name `MIR0N`)

Same 10 cells, with the **primary** DB = Oracle. The audit-DB column is independent (so e.g. cell 5 = oracle
primary, postgres audit DB). Entity `*_log` for the in-tx / shared cases lands in the **Oracle primary**.

### Local k8s — Postgres primary only

| # | Cell | `ESQUIRE_AUDIT_BUS_ID` | shared | audit DB | notes |
|---|---|---|---|---|---|
| 1 | (a) triggers | *(blank)* + trigger DDL | — | primary (pg) | set `audit.enabled=false` on the producer values |
| 2 | (b) shared | `audit-b` | true | = primary (pg) | service-level leg |
| 3 | (b) dedicated · pg | `audit-b` | false | postgres | |
| 4 | (c) AMQ · pg | `audit-c` | — | postgres | auKeep deployed |
| 5 | (ck) Kafka · pg | `audit-ck` | — | postgres | needs the kafka infra chart (now shipped) |
| 6 | (d) Redis | `audit-d` | — | — | needs the redis infra chart (now shipped) |
| 7 | (dk) Kafka stream | `audit-dk` | — | — | needs the kafka infra chart |

(Oracle is not run on k8s — the cluster Postgres is the only DB.)

---

## Validation queries

**Postgres** (container `esq-postgres` on docker; `esquire-infra-postgres-0` on k8s):
```sql
SELECT
  (SELECT count(*) FROM esq_org_log)     AS org_log,
  (SELECT count(*) FROM esq_user_log)    AS usr_log,
  (SELECT count(*) FROM esq_account_log) AS acc_log,
  (SELECT count(*) FROM esq_org_par_log) AS org_par_log,
  (SELECT count(*) FROM esq_usr_par_log) AS usr_par_log;
```
**Oracle** (`host.docker.internal:1521/MIR0N`, `esq2025/q`): same five counts (`SELECT count(*) FROM ESQ_*_LOG`).

A cell PASSES when the post-smoke counts exceed the pre-smoke counts on every table the smoke touched
(entity create/update/delete/move + the param update), or — for d / dk — the stream/topic grew and `*_log`
did not.

---

## Run

```bash
cd services/test/audit-smoke
./run.sh docker-pg            # all Postgres-primary docker cells
./run.sh docker-ora          # all Oracle-primary docker cells
./run.sh k8s                 # all local-k8s cells
./run.sh docker-pg c-ora     # a single cell
./run.sh all                 # the whole matrix (docker-pg, docker-ora, k8s) + e2e per env
```
Results are written to `results-<stamp>.md`. Bind-mount reseed rules apply (a primary-DB switch wipes
`compose/data/postgres` + re-seeds; see the project deploy notes).
