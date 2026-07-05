# Esquire — Developer Setup (from zero)

DRAFT (v1.2.10). How to go from a clean machine to a running Esquire stack you can build, run,
and test — on the two local targets the project uses: the **docker sandbox** and **local
Kubernetes** (Docker Desktop). This is the developer-facing companion to the front-door
`README.md` (which stays plain prose); setup detail and jargon live here.

---

## 1. What you are setting up

Esquire is a set of cooperating pieces, not a single app:

- **Seven Spring services** — `gateway`, `bizTree`, `enyMan`, `pacMan`, `keySmith`, `kcMaster`,
  `auKeep` — plus the **three shared libraries** (`common`, `messaging`, `dataKeep`) and the
  library companions (`audit`, `tp-activemq` / `tp-redis` / `tp-kafka`).
- The **Esquire Messaging Bus** connecting the services (an ActiveMQ broker by default; Kafka and
  Redis transport providers also exist).
- **Identity**: a KeyCloak realm (`esquire`) the services trust.
- **BFF + SPA**: the Angular Explorer served through a Node BFF (the `/api/*` proxy that injects
  the session bearer).
- **Data**: PostgreSQL (`esq2025`). See the DB note in each run target below — it differs between
  docker and k8s.

Two run targets share the same images/config:
- **docker sandbox** — the fast local stack under `services/compose/`.
- **local k8s** — Docker Desktop Kubernetes + ingress-nginx + MetalLB, which mirrors the shape of
  the cloud (OKE) deployment.

---

## 2. Prerequisites

Install and put on `PATH`:

- **JDK 25** — the runtime. (Source/bytecode target is Java 24; the build runs on JDK 25.)
- **Maven 3.9+**.
- **Docker Desktop** — with the built-in **Kubernetes** enabled (for the k8s target) and enough
  memory (the full stack plus a redundant k8s deploy is heavy).
- **Node.js LTS** + npm — for the Angular SPA and the Playwright e2e suite.
- **PostgreSQL 18** on the host (`localhost:5432`) — the docker sandbox reads this host DB (see
  below). Have `psql` available.
- **git**.

Optional / target-specific:
- `kubectl` + `helm` — for the local k8s target (bundled scripts wrap them).
- OCI CLI — only for the cloud (OKE) target, not needed for local work.

---

## 3. Repositories and workspace layout

The working copy ("dev tree") is a set of SIBLING repos under one folder:

```
C:\MyProjects\esquire\
  services\        (this repo — the seven services + shared libs + compose + k8s)
  explorer\        (Angular SPA "Explorer", the Node BFF, the e2e suite, the hauberk load harness)
  db.seed\         (the database schema + seed data — Postgres and Oracle branches)
  esquire.ui.lib\  (the reusable Angular UI library the Explorer consumes)
```

Two rules that matter:
- **The dev tree is a working copy, NOT a git tree.** Git lives in a separate mirror
  (`C:\mir0n-git\esquire.*`) and the CI runner has its own checkout again. Never mix the three.
- Changes are promoted dev-tree → git mirror by hand as a deliberate step (reviewed at commit).

---

## 4. Build

From `services/`:

```
mvn -q -DskipTests clean package        # all services + libs, skip tests
mvn -q -pl enyMan -am package           # one service and everything it needs
mvn -q -pl messaging -am test           # run one module's tests (see section 7)
```

The compose rebuild script (section 5) runs the Maven build for you.

---

## 5. Run — the docker sandbox

**Database (read this first):** the docker sandbox does NOT run a Postgres container. It uses the
**host PostgreSQL 18** on `localhost:5432`, database **`esq2025`**, reached from the containers as
`host.docker.internal:5432` (the `DB_*_HOST` defaults). So before the first run you must create that
host database and seed it from the sibling **`db.seed/postgres`** tree. Once you have a `esq2025`
role and database, seed it from `db.seed/postgres/`:

```
psql -v ON_ERROR_STOP=1 -U esq2025 -d esq2025 -f all.sql      # run from db.seed/postgres/
```

`all.sql` runs the schema creation (`create/all.sql`) then the data fill (`fill/all.sql`) — the same
sequence the baked Postgres image applies via `services/postgres/initdb/init.sh`. (That baked-seed
image is a k8s-only concern — section 6.)

**KeyCloak** IS a container here (`esq-keycloak`), started with `start-dev --import-realm`, its data
bind-mounted at `compose/data/keycloak`.

Bring the stack up (from `services/compose/`):

```
compose-rebuild.bat                 # build ALL service images from each service's own dir, recreate
compose-rebuild.bat enyman          # rebuild + recreate ONE service (gateway|biztree|enyman|pacman|
                                    #   keysmith|kcmaster|aukeep|backend|frontend)
docker compose up -d                # (re)create containers from already-built images
docker-compose-down.bat             # stop + remove
```

Handy endpoints once up:
- Explorer SPA + BFF — `http://localhost:4200` (the ng-serve container; the BFF `/api/*` proxy is
  behind it).
- KeyCloak — `http://localhost:8081/kc-auth` (relative path `/kc-auth`).
- ActiveMQ console — `http://localhost:8161` (admin/admin).

**Reseed gotchas (they bite):**
- KeyCloak's `--import-realm` is SKIPPED if the realm already exists. To re-import an edited realm
  (`keycloak/import/esquire.json`): stop, wipe `compose/data/keycloak`, recreate.
- If the stack is in a bad state, **rebuild from scratch** rather than firefighting partial
  restarts: down, wipe the relevant `compose/data/*` dirs (and re-seed the host DB if needed), up.

---

## 6. Run — local Kubernetes (Docker Desktop)

This target mirrors the cloud shape: the SPA + BFF answer on `http://esquire.localhost/` (port 80
via ingress-nginx + MetalLB).

**Safety first:** always confirm the kube context before any up/down/helm — a wrong context has
destroyed the real cloud cluster once.

```
kubectl config current-context      # must be: docker-desktop
```

**Database:** unlike docker, k8s DOES run a Postgres container — a **baked-seed image**
(`esquire-infra-postgres` StatefulSet) that seeds only on an empty volume. Reseed locally by
wiping the PVC and redeploying. (Never reseed the cloud DB — that wipes production.)

One-time cluster add-ons, then bring up (from `services/k8s/`):

```
addIngressNginx.bat                 # one-time: ingress-nginx
addMetalLB.bat                      # one-time: MetalLB (serves esquire.localhost on :80)
k8s-up.bat                          # helm install/upgrade the whole stack
k8s-rebuild.bat                     # rebuild images + roll the deploys
k8s-down.bat                        # tear down
show.them.all.bat                   # status of everything
```

Host file prerequisite: map `esquire.localhost` (and `api.esquire.localhost`) to `127.0.0.1`.

---

## 7. Tests

- **Unit + integration** — `mvn test` (module-scoped: `mvn -q -pl <svc> -am test`). The
  integration tests (`*IntegrationTest`, Testcontainers + `@SpringBootTest`) need **Docker running**
  (they start real Postgres/KeyCloak/broker containers) but run the app code in-JVM.
  Coverage report (JaCoCo, unit + in-JVM ITs combined): `target/site/jacoco/index.html` per module.
- **End-to-end** (Playwright) — in `explorer/e2e-test/`:
  - `e2e-test.bat` — against the docker sandbox (`http://localhost:4200`).
  - `e2e-k8s.bat` — against local k8s (`http://esquire.localhost`).
  - Credentials in `e2e-test/.env` (default `mainadmin` / `q`). The mutating specs build and tear
    down their own working data under the seeded Test House (they do not touch the shared seed tree).
- **Load / stress** (Gatling) — the `explorer/hauberk` harness (its own `.bat` launchers).

---

## 8. Generated API docs

From `services/`:

```
make-javadoc.bat                    # API reference for the reusable library modules
```

Output is published OUTSIDE `target`, under `doc/java-doc/<module>/` — a browsable reference for
extenders (common, messaging, dataKeep, audit, tp-*).

---

## 9. Gotchas worth memorizing

- **Rebuild from scratch** beats firefighting a half-broken local stack (down + wipe data + up).
- **Seed/realm changes apply only on a fresh init** — the DB seed runs on an empty data dir; KC
  `--import-realm` is skipped if the realm exists. Wipe the data dir to re-apply.
- **kubectl context** — check it before every k8s command.
- **`:latest` digest trap** — Docker Desktop can hold a stale `:latest`; rebuild pulls the new
  digest. The same applies to infra images.
- **docker vs k8s Postgres** — docker uses the host DB; k8s uses the baked-seed container. Do not
  assume one when working on the other.
