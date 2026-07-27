# <img src="../../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Install & Run — Local Kubernetes (Docker Desktop)

A step-by-step routine to deploy the **whole** Esquire framework on the Kubernetes built into Docker
Desktop. This target mirrors the cloud shape — every service runs **x2** (StatefulSets) — so it is
the deploy-shape rehearsal, not just a run. Follow the steps in order. For the *why*, see
[`Esquire.DevSetup.md`](../Esquire.DevSetup.md).

---

## Prerequisites (install once)

- Everything from the [Docker sandbox](Docker.md) prerequisites (JDK 25, Maven, Docker Desktop,
  Node.js, git).
- **Docker Desktop → Settings → Kubernetes → Enable Kubernetes.**
- **`kubectl`** and **`helm`** on `PATH`.
- **Hosts file** — map the ingress hostnames to localhost (add to
  `C:\Windows\System32\drivers\etc\hosts`):

  ```
  127.0.0.1   esquire.localhost   api.esquire.localhost
  ```

---

## Steps

1. **Clone the Esquire repositories from git** as siblings — same as the [Docker guide](Docker.md),
   step 1 (`services`, `explorer`, `db.seed`; **`esquire.ui.lib`** is pulled from git by the build, not
   cloned).

2. **Select the cluster context** — every k8s script refuses to run on any other context:

   ```
   kubectl config use-context docker-desktop
   kubectl config current-context          (must print: docker-desktop)
   ```

3. **Install the one-time cluster add-ons** — from `services\k8s\` (they survive teardown, run once):

   ```
   addIngressNginx.bat        ingress controller (binds localhost:80)
   addMetalLB.bat             LoadBalancer IP allocator
   ```

4. **Build the images** — from `services\k8s\`:

   ```
   k8s-rebuild.bat
   ```

   Builds every service image plus the infra images (`esquire-postgres:17`, `esquire-activemq`) and
   stamps their tags.

5. **Deploy the stack:**

   ```
   k8s-up.bat
   ```

   helm-installs the topology, the infrastructure, the services, the gateway, and the BFF; waits for
   each rollout; then applies the public ingress.

6. **Confirm everything is Ready:**

   ```
   show.them.all.bat
   ```

7. **Open the app:** <http://esquire.localhost/> — sign in via KeyCloak as the seeded demo
   administrator **`mainadmin`**, password **`q`** (the KeyCloak seed sets `q` as the password for
   every demo user).

---

## What to expect

- The same **Esquire Explorer** and demo **accounting** domain as the Docker sandbox, but running the
  **redundant deploy shape**: each service is a StatefulSet at **x2**, exercising rolling updates and
  peer reconcile (on one Docker Desktop node — correctness rehearsal, not a real failure domain).
- The database is a baked-seed `esquire-infra-postgres` StatefulSet; it seeds on an empty volume.
- Public entry: `http://esquire.localhost/` (SPA + BFF) and `http://api.esquire.localhost/` (the
  gateway REST API), served on port 80 via ingress-nginx + MetalLB.

---

## Observability (optional, on demand)

Off by default; run **after** `k8s-up.bat`, from `services\k8s\`:

```
o11y-on.bat          turn the single pane ON  (o11y-full-on.bat for all three pillars)
o11y-forward.bat     port-forward to reach the pane   (o11y-forward-stop.bat to stop)
o11y-off.bat         turn it back OFF
```

Grafana: <http://grafana.localhost> (`admin` / `admin`).

---

## Update one service

```
k8s-rebuild.bat <target>
```

`<target>` = `gateway` | `biztree` | `enyman` | `pacman` | `keysmith` | `kcmaster` | `aukeep` |
`backend`. Rebuilds the image, stamps a fresh tag, and rolls the deployment.

---

## Stop / reset

```
k8s-down.bat           uninstall the Esquire releases (MetalLB + ingress-nginx stay)
```

To **reseed**: wipe the Postgres PVC, then `k8s-up.bat` again.

> **Safety.** Never run a `k8s-*.bat` while the context is anything other than `docker-desktop` — the
> scripts guard against it because a wrong context has torn down the real cloud cluster once.
