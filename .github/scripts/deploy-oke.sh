#!/usr/bin/env bash
# ===========================================================================
# Esquire services -- deploy the SUPER-COMPACT stack to OKE.
#
# OKE runs super-compact and nothing else (2026-08-19): four application processes --
# Mesnie (enyMan + keySmith + the identity work), gateWard (the gate + the bizTree cache),
# pacMan and the BFF -- so 8 application pods where classic ran 13. The k8s-oci folder stays
# in place as the classic record; it is no longer maintained.
#
# Bash mirror of k8s-oci-compact/oke-up.bat (the proven manual flow) for a GitHub-HOSTED
# Linux runner: `helm upgrade --install` each chart with the OKE values overlay,
# the freshly-pushed GHCR image tag, and the prod secrets from the Environment.
#
# Reuses (single source of deploy logic, like phase 2):
#   k8s-compact/charts/*          -- the same charts as local compact (image repo/tag/policy overridable)
#   k8s-oci-compact/values/*.yaml -- the OKE overlays (GHCR repo, pullPolicy Always, audit-off)
#   k8s-oci-compact/esquire-topology.yml
#   k8s-oci-compact/cluster/ingress.yaml
#
# Topology note: OKE runs with audit OFF (free-tier demo). The app producers stay
# OFF (the OKE values point the audit ref at audit-off), so there is NO auKeep pod
# and no audit bus traffic. The option-(a) DB-trigger DDL is baked into the
# esquire-postgres image (db.seed/postgres/triggers) but is an OPT-IN overlay -- it
# is NOT applied by this deploy, so OKE writes no *_log rows unless applied by hand
# (\i ../triggers/all.sql). See Esquire.Q&A.md ("no audit on OKE"). Baked = available,
# not active.
#
# Pre: kubectl context already points at the OKE cluster (the workflow runs
#      `oci ce cluster create-kubeconfig` first).
#
# Required env (workflow sets these from Environment secrets):
#   IMAGE_TAG            the stamp pushed to GHCR this run (e.g. v1.2.7-2606.1009)
#   MIR0N_PWD           postgres + Keycloak admin password (single secret)
# Optional (default to the realm-import literals; OVERRIDE in prod via secrets):
#   BFF_KC_SECRET          esq-angular BFF client secret
#   GW_EXCHANGE_SECRET     gateway phantom-token-relay exchange client secret
#   BFF_SESSION_SECRET     BFF session-cookie HMAC secret
#   KCMASTER_ADMIN_SECRET  esq-kcMaster KC admin service-account client secret
# ===========================================================================
set -euo pipefail

IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG not set}"
MIR0N_PWD="${MIR0N_PWD:?MIR0N_PWD not set}"

note_fallback() {   # $1 = name, $2 = value, $3 = published default
  if [ "$2" = "$3" ]; then
    echo "[!] $1 is not configured -- using the PUBLISHED development value"
  else
    echo "[ok] $1 taken from the environment"
  fi
}

BFF_KC_SECRET="${BFF_KC_SECRET:-esq-angular-bff-dev-secret-rotate-in-prod}"
GW_EXCHANGE_SECRET="${GW_EXCHANGE_SECRET:-esq-gw-exchange-dev-secret-rotate-in-prod}"
BFF_SESSION_SECRET="${BFF_SESSION_SECRET:-esq-bff-session-secret}"
KCMASTER_ADMIN_SECRET="${KCMASTER_ADMIN_SECRET:-MHgq0Nu69u2uJ2johaK1wxQLMdakELXN}"

note_fallback BFF_KC_SECRET         "$BFF_KC_SECRET"         "esq-angular-bff-dev-secret-rotate-in-prod"
note_fallback GW_EXCHANGE_SECRET    "$GW_EXCHANGE_SECRET"    "esq-gw-exchange-dev-secret-rotate-in-prod"
note_fallback BFF_SESSION_SECRET    "$BFF_SESSION_SECRET"    "esq-bff-session-secret"
note_fallback KCMASTER_ADMIN_SECRET "$KCMASTER_ADMIN_SECRET" "MHgq0Nu69u2uJ2johaK1wxQLMdakELXN"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHARTS="$(cd "${HERE}/../../k8s-compact/charts" && pwd)"
OCIVALS="$(cd "${HERE}/../../k8s-oci-compact/values" && pwd)"
INGRESS="${HERE}/../../k8s-oci-compact/cluster/ingress.yaml"

# --- Context safety guard: never deploy to a local cluster ---
CTX="$(kubectl config current-context)"
case "${CTX}" in
  *docker-desktop*|*minikube*|*kind*)
    echo "ERROR: kubectl context '${CTX}' looks LOCAL -- refusing to run the OKE deploy." >&2
    exit 1 ;;
esac
echo "=== deploying to context: ${CTX}   image tag: ${IMAGE_TAG} ==="

# --- Drop the shape this one replaces. One cluster, one shape: a classic release left running is how
#     a cluster ends up serving from two shapes at once, and the Always-Free tier has no room for both.
#     "not found" is the normal case on a cluster already running super-compact. ---
for rel in esquire-gateway esquire-biztree esquire-enyman esquire-keysmith esquire-kcmaster esquire-aukeep; do
  if helm status "${rel}" >/dev/null 2>&1; then
    echo "--- removing classic release ${rel}"
    helm uninstall "${rel}" || true
  fi
done

# --- Infra (postgres / activemq / keycloak). All three are built + pushed per release by oke-build-push.sh
#     (postgres bakes the db.seed schema; keycloak bakes the esquire theme + realm import; activemq bakes
#     activemq.xml + the JMX agent), so passing the per-release IMAGE_TAG changes their spec EVERY run and
#     helm re-rolls them. RE-ROLLING THE BROKER IS WHAT BREAKS DEPLOYS: a broker bounce drops every app pod's
#     messaging bus, and the services do NOT self-heal from it -- so a mid-deploy broker roll leaves the
#     not-yet-rolled services wedged (readiness 503), and an unready current-revision pod STALLS its
#     StatefulSet rollout (the "biztree 1/2 context deadline exceeded" failure).
#     So: INSTALL infra when absent (first deploy), but do NOT re-roll long-lived infra on a routine app
#     deploy. Set DEPLOY_INFRA=true (workflow input) ONLY when the postgres / activemq / keycloak image
#     itself changed (schema, realm, broker config) -- then expect to kick the app pods afterward
#     (kubectl delete the wedged pods; see the OKE runbook). ---
DEPLOY_INFRA="${DEPLOY_INFRA:-false}"
infra() {  # release  chart  [helm args...]
  local rel="$1" chart="$2"; shift 2
  if [ "${DEPLOY_INFRA}" != "true" ] && helm status "${rel}" >/dev/null 2>&1; then
    echo "--- infra: ${rel} left running (DEPLOY_INFRA=false -- not re-rolled, keeps the app buses up)"
  else
    echo "--- infra: ${rel} (install/upgrade)"
# --force-conflicts on every upgrade: helm 4 applies SERVER-SIDE, and anything that scales OUTSIDE helm
# (the perf matrix does, per cell and again in its restore) takes ownership of .spec.replicas -- an upgrade
# that then sets replicas from the values overlay is REFUSED. That is how OKE deploys failed on 2026-08-19.
    helm upgrade --install "${rel}" "${chart}" "$@" --force-conflicts
  fi
}

infra esquire-infra "${CHARTS}/infra/postgres" \
  -f "${OCIVALS}/postgres.yaml" \
  --set image.tag="${IMAGE_TAG}" \
  --set db.password="${MIR0N_PWD}" --wait --timeout 5m

infra esquire-infra-amq "${CHARTS}/infra/activemq" \
  -f "${OCIVALS}/activemq.yaml" \
  --set image.tag="${IMAGE_TAG}" --wait --timeout 5m

infra esquire-infra-kc "${CHARTS}/infra/keycloak" \
  -f "${OCIVALS}/keycloak.yaml" \
  --set image.tag="${IMAGE_TAG}" \
  --set keycloak.adminPassword="${MIR0N_PWD}" --wait --timeout 6m

# --- Redis: the BFF shared session store, pinned to the infra node. It is what lets the BFF run TWO
#     replicas here -- without a shared store the in-memory one would round-robin-split logins. Not an
#     audit sink on this profile: audit is option (a). Treated as infra, so a routine app deploy does
#     not re-roll it and drop every session. ---
infra esquire-infra-redis "${CHARTS}/infra/redis" \
  -f "${OCIVALS}/redis.yaml" --wait --timeout 3m

# --- Shared messaging-bus topology (the one ConfigMap every service mounts at
#     /etc/esquire/topology.yml). Must exist BEFORE the services -- their pods mount
#     it as a volume and won't start without it. OKE feeds its own topology
#     (k8s-oci-compact/esquire-topology.yml) into the chart via --set-file: TWO buses, because
#     super-compact references exactly two -- esquire.entity, and audit-off for the audit ref.
#     No esquire.kc (Mesnie serves identity in process) and no audit-c/ck/d/dk (no auKeep). ---
echo "--- topology"
helm upgrade --install esquire-topology "${CHARTS}/esquire-topology" --force-conflicts \
  --set-file topologyContent="${HERE}/../../k8s-oci-compact/esquire-topology.yml" --wait --timeout 2m

# --- Services (new release tag) ---
# No biztree and no enyman/keysmith/kcmaster: gateWard answers the tree routes from its own cache,
# and Mesnie answers for enyMan, keySmith and the identity work in one workload.
echo "--- mesnie"
helm upgrade --install esquire-mesnie "${CHARTS}/esquire-mesnie" --force-conflicts \
  -f "${OCIVALS}/mesnie.yaml" \
  --set image.tag="${IMAGE_TAG}" --set db.password="${MIR0N_PWD}" \
  --set keycloak.adminClientSecret="${KCMASTER_ADMIN_SECRET}" --wait --timeout 5m

echo "--- pacman"
helm upgrade --install esquire-pacman "${CHARTS}/esquire-pacman" --force-conflicts \
  -f "${OCIVALS}/pacman.yaml" \
  --set image.tag="${IMAGE_TAG}" --set db.password="${MIR0N_PWD}" --wait --timeout 5m

# --- The gate. gateWard loads the whole tree from postgres before it reports ready, so it takes a
#     longer timeout than the standalone gateway did: its readiness gate is the defence against a
#     cold cache answering. ---
echo "--- gateward"
helm upgrade --install esquire-gateward "${CHARTS}/esquire-gateward" --force-conflicts \
  -f "${OCIVALS}/gateward.yaml" \
  --set image.tag="${IMAGE_TAG}" --set db.password="${MIR0N_PWD}" \
  --set tokenRelay.phantom.exchangeClientSecret="${GW_EXCHANGE_SECRET}" --wait --timeout 6m

# --- Backend / BFF (serves SPA on /, owns /auth/* + /api/*) ---
echo "--- backend (BFF)"
helm upgrade --install esquire-backend "${CHARTS}/esquire-backend" --force-conflicts \
  -f "${OCIVALS}/backend.yaml" \
  --set image.tag="${IMAGE_TAG}" \
  --set keycloak.clientSecret="${BFF_KC_SECRET}" \
  --set session.secret="${BFF_SESSION_SECRET}" --wait --timeout 5m

# --- Public ingress (applied last; routes / to the now-ready BFF) ---
echo "--- ingress"
kubectl apply -f "${INGRESS}"

echo "=== OKE deploy complete -- https://esquire.mir0n.pro ==="
kubectl get pods -n default
kubectl get ingress -A
