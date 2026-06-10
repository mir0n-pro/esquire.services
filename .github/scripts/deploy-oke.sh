#!/usr/bin/env bash
# ===========================================================================
# Esquire services -- deploy the stack to OKE (phase 3).
#
# Bash mirror of k8s-oci/oke-up.bat (the proven manual flow) for a GitHub-HOSTED
# Linux runner: `helm upgrade --install` each chart with the OKE values overlay,
# the freshly-pushed GHCR image tag, and the prod secrets from the Environment.
#
# Reuses (single source of deploy logic, like phase 2):
#   k8s/charts/*           -- the same charts as local (image repo/tag/policy overridable)
#   k8s-oci/values/*.yaml  -- the OKE overlays (GHCR repo, pullPolicy Always,
#                             audit.enabled=false -> option (a) DB triggers)
#   k8s-oci/cluster/ingress.yaml
#
# Topology note: OKE audits via DB TRIGGERS (option a) -- the app producers stay
# OFF (the OKE values set audit.enabled=false), so there is NO xxRod pod and no
# audit bus traffic. The trigger DDL ships baked into the esquire-postgres image
# (db.seed/postgres/triggers).
#
# Pre: kubectl context already points at the OKE cluster (the workflow runs
#      `oci ce cluster create-kubeconfig` first).
#
# Required env (workflow sets these from Environment secrets):
#   IMAGE_TAG            the stamp pushed to GHCR this run (e.g. v1.2.7-2606.1009)
#   MIR0N_PWD           postgres + Keycloak admin password (single secret)
# Optional (default to the realm-import literals; OVERRIDE in prod via secrets):
#   BFF_KC_SECRET       esq-angular BFF client secret
#   GW_EXCHANGE_SECRET  gateway phantom-token-relay exchange client secret
#   BFF_SESSION_SECRET  BFF session-cookie HMAC secret
# ===========================================================================
set -euo pipefail

IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG not set}"
MIR0N_PWD="${MIR0N_PWD:?MIR0N_PWD not set}"
BFF_KC_SECRET="${BFF_KC_SECRET:-esq-angular-bff-dev-secret-rotate-in-prod}"
GW_EXCHANGE_SECRET="${GW_EXCHANGE_SECRET:-esq-gw-exchange-dev-secret-rotate-in-prod}"
BFF_SESSION_SECRET="${BFF_SESSION_SECRET:-esq-bff-session-secret}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHARTS="$(cd "${HERE}/../../k8s/charts" && pwd)"
OCIVALS="$(cd "${HERE}/../../k8s-oci/values" && pwd)"
INGRESS="${HERE}/../../k8s-oci/cluster/ingress.yaml"

# --- Context safety guard: never deploy to a local cluster ---
CTX="$(kubectl config current-context)"
case "${CTX}" in
  *docker-desktop*|*minikube*|*kind*)
    echo "ERROR: kubectl context '${CTX}' looks LOCAL -- refusing to run the OKE deploy." >&2
    exit 1 ;;
esac
echo "=== deploying to context: ${CTX}   image tag: ${IMAGE_TAG} ==="

# --- Infra (idempotent install-if-absent). Only postgres is rebuilt per release
#     (db.seed change) so only it takes the new IMAGE_TAG; activemq + keycloak keep
#     their pinned values tag (stock-stable, pushed by hand when they change). ---
echo "--- infra: postgres"
helm upgrade --install esquire-infra "${CHARTS}/infra/postgres" \
  -f "${OCIVALS}/postgres.yaml" \
  --set image.tag="${IMAGE_TAG}" \
  --set db.password="${MIR0N_PWD}" --wait --timeout 5m

echo "--- infra: activemq"
helm upgrade --install esquire-infra-amq "${CHARTS}/infra/activemq" \
  -f "${OCIVALS}/activemq.yaml" --wait --timeout 5m

echo "--- infra: keycloak"
helm upgrade --install esquire-infra-kc "${CHARTS}/infra/keycloak" \
  -f "${OCIVALS}/keycloak.yaml" \
  --set keycloak.adminPassword="${MIR0N_PWD}" --wait --timeout 6m

# --- Services (new release tag) ---
echo "--- biztree"
helm upgrade --install esquire-biztree "${CHARTS}/esquire-biztree" \
  -f "${OCIVALS}/biztree.yaml" \
  --set image.tag="${IMAGE_TAG}" --set db.password="${MIR0N_PWD}" --wait --timeout 5m

echo "--- enyman"
helm upgrade --install esquire-enyman "${CHARTS}/esquire-enyman" \
  -f "${OCIVALS}/enyman.yaml" \
  --set image.tag="${IMAGE_TAG}" --set db.password="${MIR0N_PWD}" --wait --timeout 5m

echo "--- pacman"
helm upgrade --install esquire-pacman "${CHARTS}/esquire-pacman" \
  -f "${OCIVALS}/pacman.yaml" \
  --set image.tag="${IMAGE_TAG}" --set db.password="${MIR0N_PWD}" --wait --timeout 5m

echo "--- keysmith"
helm upgrade --install esquire-keysmith "${CHARTS}/esquire-keysmith" \
  -f "${OCIVALS}/keysmith.yaml" \
  --set image.tag="${IMAGE_TAG}" --set db.password="${MIR0N_PWD}" --wait --timeout 5m

# --- KC-dependent ---
echo "--- kcmaster"
helm upgrade --install esquire-kcmaster "${CHARTS}/esquire-kcmaster" \
  -f "${OCIVALS}/kcmaster.yaml" \
  --set image.tag="${IMAGE_TAG}" --wait --timeout 5m

echo "--- gateway"
helm upgrade --install esquire-gateway "${CHARTS}/esquire-gateway" \
  -f "${OCIVALS}/gateway.yaml" \
  --set image.tag="${IMAGE_TAG}" \
  --set tokenRelay.phantom.exchangeClientSecret="${GW_EXCHANGE_SECRET}" --wait --timeout 5m

# --- Backend / BFF (serves SPA on /, owns /auth/* + /api/*) ---
echo "--- backend (BFF)"
helm upgrade --install esquire-backend "${CHARTS}/esquire-backend" \
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
