<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>

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

## The compact stack (the same framework in fewer programs)

Everything above brings up the **classic** shape: every service in its own container. The **compact** shape
runs the same code and the same configuration with services grouped into fewer programs — **mesnie** holds
enyMan, keySmith and kcMaster; **gateWard** holds the gateway and the entity-tree cache. pacMan, auKeep and
the browser tier stay as they are. Nothing is removed: the same URLs, the same seeded data, the same sign-in.

It lives in `services\compose-compact\` and uses the same script names:

```
compose-rebuild.bat                build every image, then recreate the containers
compose-rebuild.bat <target>       <target> = mesnie | gateward | pacman | aukeep | backend | frontend
docker-compose-up.bat              start what is already built
docker-compose-down.bat            stop + remove the containers
o11y-on.bat / o11y-off.bat         the single pane, same as classic
```

> **Run one stack at a time.** Both use the same host ports — 4200, 8081, 8161, 5433, 3009 — so the second
> one to start fails to bind. Bring the other down first (`docker-compose-down.bat` in its own folder). The
> two keep separate containers and separate data: compact names its containers `esqc-*` and keeps its own
> `data\` and `logs\`, so switching shapes does not disturb the other stack's database or realm.

---

## Stop / reset

```
docker-compose-down.bat            stop + remove the containers
```

To **reseed from scratch** (seed re-runs only on an empty store): bring the stack down, remove the
`postgres-data` volume, wipe `compose\data\keycloak`, then `compose-rebuild.bat` again.

> Using a host database instead (including a host **Oracle** for the Oracle branch) is optional —
> see [`Esquire.DevSetup.md`](../Esquire.DevSetup.md) §5.
