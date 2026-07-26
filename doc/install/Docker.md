# <img src="../../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Install & Run — Docker Sandbox

A step-by-step routine to bring the **whole** Esquire framework up on your machine with Docker:
the seven services, the browser tier, the messaging bus, a seeded demo database, and identity.
Follow the steps in order. For the *why* behind each piece, see
[`Esquire.DevSetup.md`](../Esquire.DevSetup.md).

---

## Prerequisites (install once)

- **JDK 25** and **Maven 3.9+** on `PATH`.
- **Docker Desktop** — installed and running.
- **Node.js LTS** + npm (for the SPA image).
- **git**.

No host database is needed — the sandbox ships its own seeded Postgres container.

---

## Steps

1. **Clone the Esquire repositories from git** as siblings under one parent folder (the build reaches
   across them by relative path, so the folder names must be exactly these):

   ```
   git clone https://github.com/mir0n-pro/esquire.services  services
   git clone https://github.com/mir0n-pro/esquire.explorer  explorer
   git clone https://github.com/mir0n-pro/esquire.db.seed   db.seed
   ```

   These three are all you clone. The shared Angular UI library **`esquire.ui.lib`** is **not** cloned
   — the explorer build installs it directly from git (an npm dependency pinned to the published
   package on the library's `develop` branch).

2. **Build every image and start the stack** — from `services\compose\`:

   ```
   compose-rebuild.bat
   ```

   This builds all service images, then recreates the containers. On first start Postgres seeds
   itself (database `esq2025`) and KeyCloak imports the `esquire` realm.

3. **Confirm everything is up:**

   ```
   docker compose ps
   ```

   Wait until all containers show `running` / healthy.

4. **Open the app:** <http://localhost:4200> — sign in via KeyCloak as the seeded demo administrator
   **`mainadmin`**, password **`q`** (the KeyCloak seed sets `q` as the password for every demo user).

---

## What to expect

- The **Esquire Explorer** — the entity tree, server-driven forms, and the demo **accounting**
  domain (deposit / withdrawal / transfer) on a pre-seeded organization tree.
- Other endpoints once up:

  | What | URL | Notes |
  |---|---|---|
  | Explorer SPA + BFF | <http://localhost:4200> | the `/api/*` proxy sits behind it |
  | KeyCloak | <http://localhost:8081/kc-auth> | identity |
  | ActiveMQ console | <http://localhost:8161> | `admin` / `admin` |
  | Postgres (`esq2025`) | `localhost:5433` | host tools; container maps `5433:5432` |

---

## Observability (optional, on demand)

Off by default. From `services\compose\`:

```
o11y-on.bat        turn the single pane ON (metrics + traces + logs)
o11y-off.bat       turn it back OFF
```

Grafana: <http://localhost:3009> (`admin` / `admin`). Note `:3000` is the BFF, not Grafana.

---

## Rebuild one service

```
compose-rebuild.bat <target>
```

`<target>` = `gateway` | `biztree` | `enyman` | `pacman` | `keysmith` | `kcmaster` | `aukeep` |
`backend` | `frontend`.

---

## Stop / reset

```
docker-compose-down.bat            stop + remove the containers
```

To **reseed from scratch** (seed re-runs only on an empty store): bring the stack down, remove the
`postgres-data` volume, wipe `compose\data\keycloak`, then `compose-rebuild.bat` again.

> Using a host database instead (including a host **Oracle** for the Oracle branch) is optional —
> see [`Esquire.DevSetup.md`](../Esquire.DevSetup.md) §5.
