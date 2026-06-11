# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Services -- GitHub Actions (CI/CD) Design

Status: DRAFT / design notes. Captures the agreed pipeline shape. The two environment decisions are
resolved from current usage (Docker Desktop locally, OKE via OCI CLI + GHCR + helm); ready to write
the workflow YAML when we choose to, starting with CI.

All Esquire repositories are **public**, so GitHub-hosted runner minutes are free and unlimited.
The only target that needs special handling is the local cluster (see the core rule below).

---

## 1. The development loop this pipeline serves

The day-to-day loop (unchanged -- GitHub Actions only automates the deploy edges of it):

1. **Develop on Docker** (compose) -- fast inner loop.
2. **Reach a minor milestone** -> an **intermediate commit** on the sprint branch (`pending-vX.Y.Z`)
   with what was developed.
3. **Prepare a release** -> test everything in **local k8s**; collect the sprint's changes; improve
   documentation (README, version / release notes, landing page, etc.).
4. **PR + merge** the sprint branch into `develop`.
5. **Deploy to OKE** and validate that everything is correct and in place.
6. **Fix the sprint release**, then plan the next sprint.

What we want GitHub Actions to do:

- **Every intermediate commit on the `pending-**` branch -> deploy to local k8s.**
- **PR merge into `develop` -> deploy everything to OKE.**

---

## 2. Core rule: a job can only deploy to a cluster its runner can reach

This single constraint drives the whole design.

- **Local k8s runs on the dev machine**, behind a home network with no inbound access. GitHub's
  cloud-hosted runners cannot reach it. -> the local-deploy job **must run on a self-hosted runner
  installed on the dev box** (the same machine that has Docker + the local cluster and does the
  deploy by hand today). The push simply triggers it.
- **OKE is cloud-reachable** (public API endpoint). -> its deploy job can run on a **GitHub-hosted
  runner** (with OCI credentials) *or* on a **self-hosted runner inside OKE** (in-cluster
  `helm upgrade`, no credentials to export).

---

## 3. Branch model and trigger map

- `pending-vX.Y.Z` -- active sprint; intermediate commits land here.
- `develop` -- integration; the sprint PR merges here. Stable-enough for OKE.
- `main` -- reserved for the completed, stable `v1.2.xx`. **Nothing is wired to it yet**; a
  stable-release / tag flow is added once all of v1.2 is finished.

| Trigger | Runner | Job |
|---|---|---|
| PR opened / push (any branch) | GitHub-hosted | **CI** -- `mvn -B verify` (unit tests + Testcontainers IT) |
| push to `pending-**` (intermediate commit) | **self-hosted, on the dev box** | build images -> load into local k8s -> `helm upgrade` (local) -> smoke |
| `pending-*` PR **merged** into `develop` | GitHub-hosted (OCI creds) | build -> **GHCR push** -> `helm upgrade` (OKE) -> validate |
| `main` | -- | reserved; stable-release / tag flow added when v1.2 is complete |

Net effect: intermediate commit -> local k8s; sprint PR merge into `develop` -> OKE; `main`
untouched until v1.2 stable.

---

## 4. The workflows

### 4.1 CI (GitHub-hosted) -- `ci.yml`
- **On:** `pull_request` + `push` (all branches).
- **Steps:** checkout -> `setup-java` (21, Temurin) with Maven `~/.m2` cache -> build the
  `esquire-activemq` image first (the `RodBusIntegrationTest` Testcontainers IT needs it, else it
  silently skips) -> `mvn -B verify` over the reactor.
- **Why first:** zero secrets, no runner, no deploy. Lowest risk, immediate value -- a broken
  reactor never reaches a deploy. Build this one before anything else.

### 4.2 Local k8s deploy (self-hosted, dev box) -- `deploy-local.yml`  [BUILT]
- **On:** `push` to `pending-**` **only** (never `pull_request` -- see security note).
- **Runner:** `runs-on: [self-hosted, windows]` -- a runner registered on the Windows dev box (the
  `.bat` + Docker Desktop + local kube context force Windows; a cloud/Linux runner cannot reach Docker
  Desktop's image daemon). Matches the built-in `self-hosted` + `windows` labels -- with a single
  runner no custom label is needed (add one only when more than one self-hosted runner exists).
  Installed with its **own isolated work folder** -- never the dev tree or the git mirror (see 9a).
- **Scope (chosen):** **full stack, brought up from scratch.** The 6 Java services **and** the explorer
  backend/BFF. Because the backend is a different repo, the workflow checks out **both** repos as
  siblings in the runner workspace (`<ws>\services` + `<ws>\explorer`) so `k8s-rebuild` can build
  `..\..\explorer\backend`. Explorer is checked out at the **same branch name as the triggering
  services branch** (`ref: ${{ github.ref_name }}`, e.g. `pending-v1.2.7`) so both repos' sprint work
  deploys in lockstep -- explorer must have a branch of that name (its `package-lock.json` lives there,
  not necessarily on its default branch).
- **Steps:** checkout services (`path: services`) -> checkout `mir0n-pro/esquire.explorer`
  (`path: explorer`, public -> default `GITHUB_TOKEN` reads it) -> run
  `services\.github\scripts\deploy-local.cmd`, which reuses the proven `k8s\*.bat` (single source):
  `k8s-rebuild.bat all` (mvn package + docker build + stamp every image) then `k8s-up.bat`
  (`helm upgrade --install` infra + all services + gateway + backend, wait for readiness). Both `.bat`
  guard on `kubectl context == docker-desktop`.
- **Serialization:** `concurrency: deploy-local`, `cancel-in-progress: false` -- never interrupt a
  helm/k8s rollout already in flight; queue the next.
- **One-time prerequisites on the dev box** (see section 10): Docker Desktop k8s, kubectl, helm,
  JDK 21 + Maven, PowerShell; MetalLB + ingress-nginx installed once (`addMetalLB.bat`,
  `addIngressNginx.bat`); the runner registered on the box (built-in `self-hosted` + `windows` labels).

### 4.2a Verify -- NOT in the local scope (lives in the OKE release chain)
- The local scope (`deploy-local`) is **deploy only**. e2e + load validation against the local stack
  stays a **manual** dev activity via the explorer `.bat` tools (`e2e-test\e2e-k8s.bat`,
  `hauberk\k8s-smoke.bat`) -- deliberately **not** a GitHub Actions workflow here (mir0n 2026-06-09).
- Automated e2e + load belong to the **OKE release chain** (see 4.3): the release is validated, not
  every intermediate local deploy.

### 4.3 OKE deploy (`develop`) -- `deploy-oke.yml`  [BUILT -- awaiting first run]
- **On:** a `pending-*` PR **merged** into `develop` -- `pull_request` (`types: [closed]`,
  `branches: [develop]`) gated by an `if:` on `merged == true` **and** head branch `pending-*`; plus
  `workflow_dispatch` for a manual re-deploy. Chosen over `push: develop` so that a direct push or a
  close-without-merge never deploys -- and because `pull_request` evaluates the workflow file from the
  **base** branch (`develop`), the very PR that first introduces this file does **not** self-trigger; it
  simply lands on `develop`, after which `workflow_dispatch` can dry-run the chain before the next real
  merge. (`develop` is the repo's default branch, so once landed the **Run workflow** button is available.)
- **Runner:** **GitHub-hosted with OCI credentials** (mirrors today's manual flow:
  `k8s-oci/oke-login.bat` fetches the kubeconfig via the OCI CLI, then `helm upgrade` per
  `k8s-oci/oke-up.bat`).
- **Steps (3 jobs):** **build-push** -- checkout services (the merge commit) + explorer + db.seed as
  siblings -> `setup-java` 21 -> `mvn -B package -DskipTests` (CI already ran full verify on the PR) ->
  multi-arch `buildx` (`linux/amd64,linux/arm64` -- the OKE nodes are Ampere A1.Flex / arm64) -> **push 8
  images to GHCR** (`GITHUB_TOKEN`, no PAT). **deploy** (behind the Environment gate) -- configure the OCI
  CLI from the Environment secrets -> fetch the OKE kubeconfig (`oci ce cluster create-kubeconfig`) ->
  `deploy-oke.sh` = `helm upgrade --install` each chart with the GHCR tag + the `k8s-oci/values` overlay
  (audit option **(a)** DB triggers -- no xx-rod on OKE). A context guard refuses any
  `docker-desktop/minikube/kind` context. **validate** -- e2e + load (below).
- **Validate = the e2e + load chain** (the part deliberately kept OUT of the local scope, see 4.2a):
  after the rollout, run the explorer **e2e** (Playwright, `BASE_URL=https://esquire.mir0n.pro`) and the
  **hauberk load** (`hauberk-oke.properties`) against OKE as the release gate. Both target the public OKE
  domain, so this runs on the **GitHub-hosted** runner (install Node + Playwright; no hosts-file needed --
  a real domain). Load is currently `continue-on-error` until `hauberk-oke.properties` + the sim name are
  confirmed committed on explorer's `develop`, then it becomes a hard gate.
- **Environment gate (DONE):** the `deploy` job pins the **`oke-production`** Environment with a required
  reviewer -- a manual approval before the deploy step, where automation bites hardest. The Environment
  holds the OCI api-key secrets (`OCI_CLI_USER` / `OCI_CLI_TENANCY` / `OCI_CLI_FINGERPRINT` /
  `OCI_CLI_KEY_CONTENT`), `MIR0N_PWD`, and the `OKE_CLUSTER_OCID` / `OKE_REGION` variables; nothing can use
  them until the deploy is approved. GHCR push needs no Environment -- it uses the built-in `GITHUB_TOKEN`.

### 4.4 `main` -- reserved
- No workflow yet. When v1.2 is complete: a tag / release flow on `main` that publishes the stable
  `v1.2.xx` images and GitHub Release (from `release_notes.txt`).

---

## 5. Self-hosted runner security on PUBLIC repos (must-follow)

A self-hosted runner on a public repo is a security footgun: anyone can open a PR, and if a
`pull_request` event runs on the self-hosted runner, that PR's code executes on the dev machine.

Rules:
- The **local-deploy workflow triggers only on `push` to our own branches, never on
  `pull_request`.** Fork PRs cannot push to our branches, so they can never reach the runner.
- Prefer an **ephemeral** runner (fresh per job) and/or run the job in a container.
- Keep the runner **online only while developing** (it's the dev box; that is the normal state).

---

## 6. Loading images into local k8s -- Docker Desktop (RESOLVED)

The local cluster is **Docker Desktop Kubernetes** (context `docker-desktop`; every `k8s/*.bat`
enforces it). Docker Desktop's kubelet shares the local docker daemon, so a `docker build` image is
**immediately usable -- no load step** (`kind load` / `minikube image load` are not needed).

The one wrinkle, already handled by `k8s/k8s-rebuild.bat`: a chart pinned to `image.tag=latest` with
`imagePullPolicy: IfNotPresent` makes the kubelet keep the **old** digest cached under `:latest`. The
fix is to tag each build with a fresh per-build stamp and deploy that tag:

```
docker build -t esquire.<svc>:latest ...
docker tag  esquire.<svc>:latest esquire.<svc>:<YYMM.DDHHmm>
helm upgrade --install ... --set image.tag=<YYMM.DDHHmm>   # imagePullPolicy: IfNotPresent
```

The local-deploy job reproduces exactly this (build -> timestamp-tag -> `helm upgrade --set
image.tag`); no registry, no pull.

OKE pulls from **GHCR** (`ghcr.io/<owner>/esquire.<svc>:<tag>`), so its charts use the GHCR image
repo + `imagePullPolicy: IfNotPresent`/`Always`. The charts must allow image repo + tag + pull policy
to be overridden per environment (values file or `--set`) -- local uses local refs, OKE uses GHCR
refs.

---

## 7. Secrets

- **GHCR push:** the built-in `GITHUB_TOKEN` with `packages: write` -- no PAT needed.
- **OKE (if GitHub-hosted runner):** `KUBECONFIG_OKE` (+ any `OCI_*`) as repo / Environment secrets.
- **OKE (if self-hosted-on-OKE runner):** none exported -- the runner uses the in-cluster service
  account. This is the main reason to consider that option.
- **Local deploy:** none -- the self-hosted runner already has the local kube context + docker.

---

## 8. Phased adoption (do not do it all at once)

1. **CI** (`ci.yml`) -- hosted, no secrets, no runner. Get the build green per commit.
2. **Local deploy** (`deploy-local.yml`) -- after registering the self-hosted runner; `push` to
   `pending-**` only.
3. **OKE deploy** (`deploy-oke.yml`) -- last, behind a manual-approval Environment.
4. **`main` release flow** -- when v1.2 is complete.

---

## 8a. Repository layout for the workflows

The workflow `.yml` files **must** live in `.github/workflows/` -- that is the only place GitHub
discovers workflows; it is not configurable. The actual logic, however, lives in a dedicated scripts
folder, and the workflow files are thin wrappers that call it. Everything below is committed to the
repo (CI/CD config is part of the curated source of truth) and runs on the runner's fresh checkout.

```
.github/
  workflows/          <- REQUIRED location; thin YAML (triggers + calls scripts)
    ci.yml
    deploy-local.yml
    deploy-oke.yml
  scripts/            <- the real logic ("special folder")
    build-images.sh
    deploy-local.sh
    deploy-oke.sh
  actions/            <- optional: composite actions for shared steps
```

A workflow stays tiny -- triggers + a call into the scripts folder:

```yaml
# .github/workflows/deploy-local.yml
on: { push: { branches: ['pending-**'] } }
jobs:
  deploy:
    runs-on: [self-hosted, local-k8s]
    steps:
      - uses: actions/checkout@v4
      - run: .github/scripts/deploy-local.sh
```

Notes:
- This mirrors the existing split: `k8s/*.bat` / `k8s-oci/*.bat` are the **manual** scripts;
  `.github/scripts/*.sh` are the **automated** equivalents. They can call **shared** deploy logic so
  there is one source of truth for the deploy steps.
- Keeping the logic in scripts (not inline YAML) makes the steps runnable / debuggable outside GHA
  and keeps the workflow files readable.

---

## 9a. Repository / workspace separation (HARD principle -- never mix)

The local git repository is kept **separate from development and from deployment**. Three distinct
spaces, never sharing a directory:

1. **Development working tree** -- `C:\MyProjects\esquire\services`. Where code is written and run on
   Docker (the inner loop). It is **not** a git repo.
2. **Local git repository** -- `C:\mir0n-git\esquire.services`. The clean source of truth; commits
   are made here and pushed to GitHub. Kept apart from the dev tree on purpose.
3. **GitHub Actions workspace** -- the runner's own checkout from GitHub (`_work`). For hosted
   runners this is an ephemeral cloud workspace; for the **self-hosted (local) runner it is an
   isolated runner work folder on the dev box** (e.g. `C:\actions-runner\_work`), **NOT** the
   development tree and **NOT** the local git mirror.

Why this matters for GHA: every runner -- hosted or self-hosted -- builds and deploys from a **fresh
checkout of the GitHub remote**, so CI/CD can never read or write the development working tree or the
local git repository. The pipeline reinforces the separation instead of eroding it. Concretely:

- The self-hosted runner is installed with its own work folder, isolated from both other spaces.
- Deploy artifacts (images, helm releases) are produced from the runner's checkout, never from
  `C:\MyProjects\...` or `C:\mir0n-git\...`.
- Local `k8s/*.bat` / `k8s-oci/*.bat` remain the **manual** path from the dev tree; the GHA jobs are
  a **parallel, separated** path from the remote. They do not share state.

**Deploy-time debugging vs commit.** Deployment is also a correction phase: while deploying we may
modify / correct / debug settings (config, env, helm values, `application.yml`, chart values) in the
**dev / deploy space**. Only files that are **confirmed** (validated as correct in the target) are
then committed to the git repository -- the repo stays a curated source of truth, never a dump of
deploy-time experiments. Implications for GHA:

- The deploy jobs run what is **committed** (the confirmed state on the remote), not whatever is
  being debugged locally.
- Ad-hoc deploy fixes are made and tried in the working / deploy space first; once confirmed, mir0n
  promotes them into `C:\mir0n-git\...` and commits. The pipeline never auto-commits deploy tweaks
  back to git.
- This is the same discipline as the documentation rule: edits live in the working tree; git
  receives only the confirmed, commit-ready state under mir0n's control.

---

## 9. Decisions (RESOLVED from current usage)

1. **Local k8s flavor = Docker Desktop Kubernetes** (context `docker-desktop`). Image-load step =
   none; use the per-build timestamp tag from section 6.
2. **OKE deploy runner = GitHub-hosted with OCI credentials** -- mirrors the current
   `oke-login` + `helm upgrade` flow; images from GHCR. (A self-hosted runner on OKE stays a future
   option if we want to stop exporting OCI creds.)

Both were read off what the project does today (`k8s/*.bat` -> docker-desktop; `k8s-oci/*.bat` ->
GHCR + OCI CLI + helm), so the workflows match the existing manual steps rather than introducing a
new toolchain.

---

## 10. Self-hosted runner setup (one-time, Windows dev box)

`deploy-local.yml` needs a self-hosted runner on the Windows dev box. One-time:

**Prerequisites on the box** (the runner shells out to these):
- Docker Desktop with **Kubernetes enabled**; `kubectl config current-context` == `docker-desktop`.
- `kubectl`, `helm`, **JDK 21**, **Maven**, PowerShell (built in).
- MetalLB + ingress-nginx installed once: `k8s\addMetalLB.bat`, `k8s\addIngressNginx.bat`
  (they survive `k8s-down`; `k8s-up.bat` only warns if missing).

**Register the runner** (GitHub -> the `esquire.services` repo -> Settings -> Actions -> Runners ->
New self-hosted runner -> Windows x64). Install it **outside** the dev tree and the git mirror, e.g.:

```
C:\actions-runner>  config.cmd --url https://github.com/mir0n-pro/esquire.services ^
                               --token <TOKEN-FROM-THE-PAGE>
```

`self-hosted` + `windows` (+ `X64`) labels are added automatically, and that is all the workflow
matches (`runs-on: [self-hosted, windows]`) -- with a single runner no custom label is needed. Its
`_work` folder is the isolated GHA workspace from 9a -- never the dev tree or the git mirror. (Add a
custom `--labels` only once there is more than one self-hosted runner to target between.)

**Run it** -- on demand while developing (`run.cmd`) or install as a service
(`svc.cmd install` + `svc.cmd start`) so a `pending-**` push always finds it online.

**Security (public repo):** the runner only ever runs `deploy-local.yml`, which triggers on
`push` to `pending-**` -- never `pull_request` -- so a fork PR can never execute on the box (see 5).

**First-run note:** the explorer repo is fetched fresh into the runner workspace at the **same branch
name as the triggering services branch** (`ref: ${{ github.ref_name }}`, e.g. `pending-v1.2.7`) -- NOT
its default branch. The deployed backend is therefore explorer's committed *pending-branch* state
(where the sprint work + its `package-lock.json` live), not the dev box's local working copy. Explorer
must have a branch of the same name as the services branch being deployed.
