# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire — Public Demo Deployment Options

Notes on container hosting options for the first public demo of the Esquire framework.
Stack: gateway, keySmith, enyMan, pacMan, bizTree, kcMaster, ActiveMQ, Keycloak, Postgres, nginx.
Estimated resource floor: ~2 vCPU, 8–10 GB RAM.

---

## Container Registry

**GitHub Container Registry (`ghcr.io/mir0n-pro/`)** — natural fit.
Already on GitHub; images attach to existing repos; free for public images;
`mir0n-pro` namespace carries over automatically.

---

## Option 1 — Single VM with Docker Compose

Simplest deployment path: one VM, Docker Compose, no orchestration overhead.

| Platform | Cost | RAM | Notes |
|---|---|---|---|
| **Oracle Cloud Free Tier (A1)** | **$0 forever** | **24 GB** | 4 Ampere OCPU; always free (not 12-month trial); comfortably fits full stack |
| AWS ECS Fargate | ~$70–90/mo | Pay per GB/hr | Free tier expires after 12 months; ~2 vCPU + 10 GB = significant monthly cost |
| AWS EC2 t3.large | ~$60/mo | 8 GB | Same ops burden as Oracle VM but paid |
| DigitalOcean Droplet | ~$12/mo | 2 GB (tight) | Simple; needs a larger droplet (~$24) for comfortable fit |

**Recommendation:** Oracle Cloud Free Tier A1 — the only option where 24 GB RAM is permanently
free. AWS makes sense if you are already paying for other AWS services or need AWS-native
integrations (ALB, Route 53, RDS). For a standalone public demo it is not cost-competitive.

**Database:** Postgres for the demo — no license overhead, simpler image, fully supported.

---

## Option 2 — Kubernetes

### Decision

Three requirements drive the choice:

1. Must be publicly accessible
2. Local setup for everyday development
3. No tool switching — same experience local and public

**The plan:**

```
Docker Desktop K8s  ──helm install──►  Oracle OKE (A1 free nodes)
     local                                    public
  same kubectl                            same manifests
  same Helm                               same Helm
```

Docker Desktop K8s runs standard upstream Kubernetes — the same engine as OKE.
One set of Helm charts, one set of skills, deployed identically in both environments.

### Tooling

| Tool | Role |
|---|---|
| **Docker Desktop K8s** | Local cluster — standard upstream K8s, no extra VM, shares Docker daemon |
| **Helm** | Package manager — templates manifests; manages config differences between local and public via `values.yaml` |
| **Lens** | K8s IDE — visual dashboard, pod logs, exec into containers; best troubleshooting tool |
| Skaffold | Dev inner loop — rebuild + redeploy on code change; optional |
| ArgoCD | GitOps CD — auto-deploy on git push; later, when pipeline matures |

### Oracle OKE cost

OKE control plane is **free**. Worker nodes = Oracle A1 Always Free compute (4 OCPU, 24 GB RAM).
OKE costs exactly the same as the plain VM option — **$0** — with Kubernetes on top instead of
Docker Compose. No penalty for the upgrade.

### Kubernetes options

**Free**

| Option | Notes |
|---|---|
| **Docker Desktop K8s** (local) | Standard upstream K8s; one checkbox; recommended local setup |
| **OKE (Oracle Kubernetes Engine)** | Managed control plane free; worker nodes = Oracle A1 free compute; standard upstream K8s — direct match to local |
| k3s on Oracle Free Tier A1 | Self-managed; lighter; closer to Civo in production |

**Cheap paid**

| Option | Cost | Notes |
|---|---|---|
| **Civo** | ~$5–15/mo | K3s-based managed Kubernetes; simplest on-ramp if not using Oracle |
| **DigitalOcean DOKS** | ~$24/mo (2 nodes) | Standard upstream K8s; well documented |
| **GKE Autopilot** | Pay per pod | Free control plane; $300 new-account credit; scales cleanly |

**Expensive**

| Option | Cost | Notes |
|---|---|---|
| AWS EKS | $72/mo control plane + node costs | Only justified if already AWS-committed |
| Azure AKS | Free control plane + VM cost | $200 credit for new accounts |

### Workload fit

| Service | Now | K8s fit | Notes |
|---|---|---|---|
| Postgres | yes | excellent | StatefulSet + PVC; Bitnami Helm chart |
| ActiveMQ | yes | good | StatefulSet; community chart |
| Keycloak | yes | excellent | Official Helm chart |
| MySQL | future | excellent | Same as Postgres |
| RabbitMQ | future | excellent | Official Helm chart |
| Kafka | future | good | Bitnami chart; needs more RAM |
| Aeron | future | complex | Low-latency UDP + shared memory; conflicts with K8s networking abstractions; needs host networking / DaemonSet — revisit when needed |

---

## Node Assignment — OKE Free Tier (3 nodes)

Oracle A1 free pool (4 OCPU + 24GB) split into 3 nodes:

| Node | Shape | OCPU | RAM | Role | Runs |
|---|---|---|---|---|---|
| infra | A1.Flex | 2 | 12GB | stateful infrastructure | Postgres, Keycloak, ActiveMQ, nginx |
| app-a | A1.Flex | 1 | 6GB | application tier | gateway, keySmith, enyMan, bizTree |
| app-b | A1.Flex | 1 | 6GB | application tier | pacMan, kcMaster + redundant replicas |

**Why 3 nodes:**
- Infra node isolated — stateful workloads do not compete with microservices for CPU bursts
- 2 app nodes enable pod anti-affinity — when a microservice is doubled, replica 1 lands on
  app-a, replica 2 on app-b automatically
- Kill app-b → redundant pods survive on app-a → cluster reschedules → resilience test passes
- Kill infra node → everything stops — expected; single Postgres is the free tier ceiling

**Resilience testing (doubling 5 microservices):**

| Microservice | Replica 1 | Replica 2 |
|---|---|---|
| gateway | app-a | app-b |
| keySmith | app-a | app-b |
| enyMan | app-a | app-b |
| pacMan | app-a | app-b |
| bizTree | app-a | app-b |

kcMaster stays single (Keycloak sync; safe to run one instance).
Infrastructure (Postgres, Keycloak, ActiveMQ) stays single on infra node.

**Memory headroom with doubled microservices:**
- 5 doubled services × 400MB = 4GB across app-a + app-b (2GB each node — fits in 6GB)
- Infra node: Postgres 384MB + Keycloak 768MB + ActiveMQ 384MB + nginx 64MB = ~1.6GB (fits in 12GB)
- Total cluster: ~6.5GB used of 24GB — comfortable

**To stay free:** all nodes must use shape `VM.Standard.A1.Flex` — any other shape is charged immediately.

---

## Chosen Path

1. **Registry:** `ghcr.io/mir0n-pro/` — publish all service images here
2. **Local:** Docker Desktop K8s + Helm + Lens
3. **Public:** Oracle OKE with A1 free worker nodes
4. **Charts:** `k8s/` folder in the services repo; one chart per service + umbrella chart for full stack
5. **Config:** `values.local.yaml` and `values.prod.yaml` — the only difference between environments

---

---

## Deployment Plan

### Phase 1 — Local Foundation
*Goal: full stack running on Docker Desktop K8s*

1. **Dockerfiles** — one per service
   - 6 Spring Boot services: gateway, keySmith, enyMan, pacMan, bizTree, kcMaster
   - Angular frontend: nginx
   - Keycloak: with realm export baked in
   - ActiveMQ: with pre-configured queues and topics

2. **Verify with docker-compose first** — confirm all services start and communicate
   before introducing K8s complexity

3. **Enable Docker Desktop K8s** — install Helm and Lens

---

### Phase 2 — Helm Charts
*Goal: full stack deployable with one command*

```
k8s/
  charts/
    esquire-gateway/
    esquire-keysmith/
    esquire-enyman/
    esquire-pacman/
    esquire-biztree/
    esquire-kcmaster/
    esquire-frontend/
    infra/
      postgres/
      activemq/
      keycloak/
  esquire/                    <- umbrella chart; pulls all charts together
    Chart.yaml
    values.yaml
    values.local.yaml
    values.prod.yaml
```

Each chart contains: `Deployment`, `Service`, `ConfigMap`, `Secret`.
Umbrella chart: `helm install esquire ./k8s/esquire` deploys the full stack.

---

### Phase 3 — Registry
*Goal: images published and pullable from anywhere*

- Tag and push all images to `ghcr.io/mir0n-pro/`
- One image per service, versioned by release tag
- GitHub Actions workflow to build and push on tag (optional but clean)

---

### Phase 4 — Oracle OKE
*Goal: public demo live*

1. Create Oracle Cloud account
2. Provision OKE cluster with A1 Always Free worker node pool
3. Configure `kubectl` context for OKE
4. `helm install esquire ./k8s/esquire --values values.prod.yaml`
5. Ingress + TLS — OCI Load Balancer + Let's Encrypt

---

### What changes between local and prod

Only `values.local.yaml` vs `values.prod.yaml`:

| Setting | Local | Prod |
|---|---|---|
| Image registry | local build | `ghcr.io/mir0n-pro/` |
| Ingress host | `localhost` | `esquire.mir0n.pro` |
| TLS | off | Let's Encrypt + cert-manager |
| DB storage | `hostPath` (desktop filesystem) | PersistentVolumeClaim |
| Log output | files on desktop filesystem | stdout only |
| devLog level | DEBUG (file) | OFF |
| msgLog level | DEBUG (file) | INFO (console) |
| Keycloak mode | `start-dev` | `start` |
| Passwords | seed defaults | rotated secrets |
| Replica count | 1 | 1 (demo) |

### Resolved Decisions

| Piece | Decision |
|---|---|
| Registry | `ghcr.io/mir0n-pro/` |
| Local K8s | Docker Desktop K8s |
| Public K8s | Oracle OKE, A1 Always Free nodes |
| Domain | `esquire.mir0n.pro` (owned) |
| DNS | one `A` record → OCI Load Balancer public IP |
| TLS | cert-manager + Let's Encrypt (automated renewal) |
| Ingress path | `esquire.mir0n.pro` → OCI LB → K8s Ingress → gateway |
| Orchestration | Helm |
| Charts location | `k8s/` folder in esquire.services repo |

---

### Pre-Helm Work (code changes required)

| Item | What | Where |
|---|---|---|
| Logback switch | Add `LOG_OUTPUT=console` env var to route file appenders to stdout | all services |
| Frontend Dockerfile | Add nginx production stage: Node build → nginx serve | explorer/frontend/Dockerfile |

Locally neither change is needed — local setup keeps file logging and `ng serve` as-is.

---

### Sequence

```
Dockerfiles  ->  docker-compose verify  ->  Helm charts local
    ->  ghcr.io push  ->  OKE deploy  ->  domain + TLS
```

### Next Session Starting Point

1. Enable Docker Desktop K8s (one checkbox in Docker Desktop settings)
2. Install Helm + Lens
3. Write first Helm chart: `k8s/charts/infra/postgres` — simplest, everything depends on it

---

See [OCI.Pricing.md](OCI.Pricing.md) for full pricing detail — compute, storage, load balancer,
production configuration costs, ARM build pipeline notes, and cost scenarios.

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*
