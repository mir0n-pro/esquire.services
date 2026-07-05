| ![Alt text](../favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|

# k8s-oci — Oracle Kubernetes Engine deployment

All scripts, manifests, and value overrides required to deploy Esquire to
Oracle OKE at `https://esquire.mir0n.pro`. The local Docker Desktop K8s setup
in `../k8s/` is left untouched.

See `../doc/TodoCloud.md` for full plan and `../doc/WhereToGo.md` for
architectural rationale.

---

## Layout

```
k8s-oci/
  README.md                     this file
  ghcr-push.bat                 multi-arch (amd64+arm64) build and push to GHCR
  oke-login.bat                 fetch kubeconfig + switch context to OKE
  oke-bootstrap.bat             one-time cluster prep (ingress + cert-manager)
  oke-up.bat                    deploy all charts with prod values
  oke-down.bat                  uninstall everything
  show.them.all.bat             pods + pvc + ingress + cert status
  values/                       per-chart values overrides (passed via -f)
    postgres.yaml
    activemq.yaml
    keycloak.yaml
    biztree.yaml  enyman.yaml  pacman.yaml  keysmith.yaml  kcmaster.yaml
    gateway.yaml  backend.yaml
  cluster/
    letsencrypt-prod.yaml       cert-manager ClusterIssuer
    ingress.yaml                public Ingress for frontend/gateway/keycloak
    node-labels.bat             label nodes for nodeSelector
```

---

## Prerequisites

- OCI account with OKE cluster (4 A1.Flex nodes: 1 infra + 3 app, each 1 OCPU / 6 GB =
  the full 4 OCPU / 24 GB Always-Free envelope — see `../doc/TodoCloud.md` Phase 3)
- OCI CLI installed and configured (`oci setup config`)
- Domain `esquire.mir0n.pro` A record pointing to OCI LB IP (see step 3)
- GHCR PAT with `write:packages` (set as `GHCR_TOKEN` env var)
- Local Docker Desktop with `buildx` enabled (default in current versions)

---

## Run order

1. **GHCR push (multi-arch)**
   ```
   set GHCR_TOKEN=<your-PAT>
   ghcr-push.bat
   ```
   Pushes 10 images as `linux/amd64,linux/arm64` to `ghcr.io/mir0n-pro/`.

2. **OKE login**
   ```
   oke-login.bat
   ```
   Refreshes kubeconfig and switches kubectl context to OKE.

3. **Cluster bootstrap (one-time)**
   ```
   oke-bootstrap.bat
   ```
   Installs ingress-nginx (creates the public LB), cert-manager, and the
   Let's Encrypt ClusterIssuer. Prints the LB external IP at the end —
   **set the `esquire.mir0n.pro` A record to that IP before running step 5.**

4. **Label nodes**
   ```
   cluster\node-labels.bat
   ```
   Tags nodes with `tier=infra` / `tier=app` for nodeSelector targeting.
   Set `INFRA_NODE` to your infra node from `kubectl get nodes`; every other
   worker is labelled `app` automatically (the 3 app nodes carry the x2 fleet).

5. **DNS check (manual)**
   ```
   nslookup esquire.mir0n.pro
   ```
   Must resolve to the LB IP from step 3. Wait for propagation if needed.
   Without DNS, Let's Encrypt HTTP-01 challenge will fail.

6. **Deploy stack**
   ```
   oke-up.bat
   ```
   Same order as `../k8s/k8s-up.bat` but with prod values overrides.
   Pass DB and KC admin passwords via `--set` (do NOT bake into values.yaml).

7. **Verify**
   ```
   show.them.all.bat
   ```
   All 16 pods Ready (6 services x2 + BFF + 3 infra), certificate Ready,
   ingress with TLS bound. The two replicas of each service should sit on
   different app-tier nodes (`kubectl get pods -o wide`).

8. **Open** `https://esquire.mir0n.pro`

---

## Teardown

```
oke-down.bat
```

Uninstalls all releases (reverse order). Cluster, ingress, cert-manager,
PVCs, and OCI LB stay — re-install is fast.

To wipe completely: also run `kubectl delete pvc --all -n default` and
`helm uninstall ingress-nginx -n ingress-nginx`.

---

## Secrets

Two secrets must be passed at install (not stored in values files):

```
oke-up.bat <pgPassword> <kcAdminPassword>
```

Recommend using a password manager. Rotated quarterly.

---

## DNS layout — single A record, path-based ingress

```
esquire.mir0n.pro   A   <OCI LB IP>
```

Ingress routes by path:
- `/`        -> frontend
- `/api/`    -> gateway (path stripped)
- `/auth/`   -> keycloak (path preserved — KC needs `/auth` prefix or rewrite via `KC_HTTP_RELATIVE_PATH`)

Frontend's `apiBasePath` and `keycloakUrl` reset to `https://esquire.mir0n.pro/api`
and `https://esquire.mir0n.pro/auth` in `values/frontend.yaml`.

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*
