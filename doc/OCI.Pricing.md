| ![Alt text](../favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|

# Oracle Cloud Infrastructure — Pricing Reference

Pricing notes relevant to Esquire K8s deployment on OCI.
Free tier baseline: 4 OCPU + 24GB RAM (A1), 200GB block storage, 1 load balancer, 10TB egress/month.

---

## Compute — Worker Nodes

### A1 Flex (ARM64) — recommended for Esquire
Cheapest option; same architecture as free tier nodes; all Esquire base images have ARM builds.

| Resource | Price |
|---|---|
| OCPU | $0.01/hour |
| RAM | $0.0015/GB/hour |

Example — 1 extra app node (2 OCPU, 12GB):
`2 × $0.01 + 12 × $0.0015 = $0.038/hr = ~$27/month`

Example — 1 extra infra node (4 OCPU, 24GB):
`4 × $0.01 + 24 × $0.0015 = $0.076/hr = ~$55/month`

### E4 Flex (AMD x86) — universal image compatibility
More expensive; use if ARM build pipeline is not feasible.

| Resource | Price |
|---|---|
| OCPU | $0.025/hour |
| RAM | $0.0015/GB/hour |

Example — 1 node (2 OCPU, 12GB):
`2 × $0.025 + 12 × $0.0015 = $0.068/hr = ~$49/month`

### Node count pricing examples

| Config | Nodes | OCPU | RAM | Est. cost/month |
|---|---|---|---|---|
| Free tier | 3 A1 | 4 | 24GB | $0 |
| +1 app node | 4 A1 | 5 OCPU | 30GB | ~$22 |
| Double app tier | 5 A1 | 6 OCPU | 36GB | ~$43 |
| Full HA (infra x2 + app x3) | 6 A1 | 8 OCPU | 48GB | ~$87 |

---

## Storage — Block Volumes (PVCs)

| Tier | Price |
|---|---|
| Standard (balanced) | $0.0255/GB/month |
| Higher Performance | $0.0680/GB/month |
| Ultra High Performance | $0.136/GB/month |

Standard is correct for Postgres data, Keycloak, ActiveMQ.

Example — Esquire demo volumes:
- Postgres: 20GB = $0.51/month
- Keycloak: 5GB = $0.13/month
- ActiveMQ: 5GB = $0.13/month
- Total: ~$0.77/month (well within 200GB free tier = $0)

When you exceed 200GB free quota, full volume size is charged (not just the excess).

---

## Load Balancer

| Resource | Always Free | Paid |
|---|---|---|
| Flexible LB instance | 1 free | $0.008/LB/hour (~$5.76/month) |
| Bandwidth | 10Mbps free | scales to 8Gbps; charged per Mbps/hour |

For demo at `esquire.mir0n.pro`: 1 LB at 10Mbps — free.
For production with real traffic: likely still free (demo load rarely saturates 10Mbps).

---

## Egress Bandwidth

| Tier | Price |
|---|---|
| First 10TB/month | free |
| Beyond 10TB | $0.0085/GB |

10TB/month = ~3.3 Gbps sustained average. Esquire demo will not approach this.

---

## Production Configuration — What Changes and What It Costs

### Frontend (nginx production build)
- Current: `ng serve` dev server — heavy Node.js process
- Production: `ng build` → nginx serving static files
- RAM: Node dev server ~300MB → nginx ~32MB
- **Cost impact:** reduces RAM requirement; fits in same free node

### Keycloak production mode
- Current: `start-dev` — embedded H2 DB, no HTTPS enforcement, permissive config
- Production: `start` — requires external DB, HTTPS, `KC_HOSTNAME` set to real domain
- Uses same Postgres instance (add a `keycloak` database to the Postgres pod)
- No extra infrastructure needed — config change only
- **Cost impact:** none — same container, same RAM (~768MB)

### Postgres HA (primary + replica)
- Free tier: single Postgres pod, no replication
- HA: primary + 1 read replica = double compute + double storage
- Requires a second infra node or larger infra node
- **Cost impact:** ~$27–55/month for the extra node

### ActiveMQ HA
- Production HA: network of brokers (2 instances, shared storage)
- Requires second broker pod + shared block volume
- **Cost impact:** extra PVC ~$0.13/month; broker pod fits on existing app nodes

### Cert-manager + Let's Encrypt (TLS)
- cert-manager: open source, runs as pods in K8s — no OCI cost
- Let's Encrypt certificates: free, auto-renewed by cert-manager
- **Cost impact:** $0

---

## ARM64 Build Pipeline

All free and paid A1 nodes are ARM64. Build pipeline must produce ARM images.

Options:
- `docker buildx build --platform linux/arm64` on local Apple Silicon or ARM machine
- GitHub Actions: `ubuntu-24.04-arm` runner (billed at $0.008/minute vs $0.004 for x86)
- Multi-arch build: `--platform linux/amd64,linux/arm64` — works everywhere, larger pipeline

For Esquire base images, ARM64 variants exist for all:
- `eclipse-temurin:21-jre-jammy` — multi-arch
- `quay.io/keycloak/keycloak:26+` — multi-arch
- `apache/activemq-classic` — multi-arch
- `postgres:17` — multi-arch
- `nginx:alpine` — multi-arch

---

## Cost Summary — Scenarios

| Scenario | Monthly cost |
|---|---|
| Demo (free tier, 3 nodes) | **$0** |
| Demo + 1 extra app node (resilience testing) | ~$22 |
| Demo + Postgres replica (HA storage) | ~$28 |
| Small production (5 nodes, HA Postgres) | ~$80–100 |
| Full production HA (6 nodes, all redundant) | ~$130–160 |

---

## Key Rules to Stay Free

1. Worker node shape must be `VM.Standard.A1.Flex` — any other shape is paid immediately
2. Total A1 compute across all compartments must stay within 4 OCPU + 24GB
3. Block storage must stay within 200GB total
4. Only 1 load balancer instance on free tier
5. OKE control plane is always free regardless of node count or shape

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*
