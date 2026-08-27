#!/usr/bin/env bash
# ===========================================================================
# Esquire services -- build + push the SUPER-COMPACT app images to GHCR for OKE.
#
# SEVEN images, not ten (mir0n, 2026-08-19): OKE runs super-compact and nothing else.
#
# Mirrors k8s-oci-compact/ghcr-push.bat (+ the mvn pass of oke-rebuild.bat) but for a
# GitHub-HOSTED Linux runner. Builds MULTI-ARCH (linux/amd64,linux/arm64) because
# OKE runs on Ampere A1.Flex (arm64) nodes; amd64 is kept so the same tags pull
# on an x86 box too.
#
# Reuses the real per-service Dockerfiles -- no build logic is duplicated here.
#
# Pre (done by the workflow before calling this):
#   - QEMU + docker buildx set up (docker/setup-qemu-action + setup-buildx-action)
#   - `docker login ghcr.io` done with GITHUB_TOKEN (packages: write) -- no PAT
#   - the three repos checked out as SIBLINGS in the runner workspace:
#       <ws>/services   (this repo)
#       <ws>/explorer   (backend / BFF image is built from here)
#       <ws>/db.seed    (postgres image build input)
#
# Env:
#   IMAGE_TAG   the stamp to push, e.g. v1.2.7-2606.1009  (computed by the workflow)
#   OWNER       GHCR owner / namespace (default: mir0n-pro)
#   RUN_VERIFY  "true" to run full `mvn verify` (ITs need the activemq image);
#               default "false" = `package -DskipTests` since CI already ran
#               verify on the PR that produced this develop push.
# ===========================================================================
set -euo pipefail

IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG not set}"
OWNER="${OWNER:-mir0n-pro}"
RUN_VERIFY="${RUN_VERIFY:-false}"
REG="ghcr.io/${OWNER}"
PLATFORMS="linux/amd64,linux/arm64"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICES="$(cd "${HERE}/../.." && pwd)"     # <ws>/services
WS="$(cd "${SERVICES}/.." && pwd)"          # <ws> (services + explorer + db.seed siblings)

echo "=== build context: services=${SERVICES}  workspace=${WS}  tag=${IMAGE_TAG} ==="

# --- Java build: produce every service jar the Spring Dockerfiles COPY ---
if [ "${RUN_VERIFY}" = "true" ]; then
  echo "=== mvn -B verify (full, incl. integration tests) ==="
  mvn -B -ntp -f "${SERVICES}/pom.xml" clean verify
else
  echo "=== mvn -B package -DskipTests (CI already ran verify on the PR) ==="
  mvn -B -ntp -f "${SERVICES}/pom.xml" clean package -DskipTests
fi

bx() {  # image  context  dockerfile
  echo "--- buildx push ${REG}/$1:${IMAGE_TAG}  (${PLATFORMS})"
  docker buildx build --platform "${PLATFORMS}" \
    -t "${REG}/$1:${IMAGE_TAG}" --push \
    -f "$3" "$2"
}

# 3 Spring services -- context = the service dir; its Dockerfile COPYs its own named jar.
# Three, not six: Mesnie is the one image for enyMan, keySmith and the identity work, and
# gateWard is the one image for the gate and the bizTree cache.
bx esquire.mesnie   "${SERVICES}/mesnie"   "${SERVICES}/mesnie/Dockerfile"
bx esquire.pacman   "${SERVICES}/pacMan"   "${SERVICES}/pacMan/Dockerfile"
bx esquire.gateward "${SERVICES}/gateWard" "${SERVICES}/gateWard/Dockerfile"

# backend / BFF -- multi-stage; context = explorer/ so it reaches backend/ + frontend/
bx esquire.backend  "${WS}/explorer"       "${WS}/explorer/backend/Dockerfile"

# postgres -- context = workspace root so the Dockerfile can COPY db.seed/postgres/*
# (incl. create.log -> seeds the *_log tables; the OKE audit option (a) triggers live here too)
bx esquire-postgres "${WS}"                "${SERVICES}/postgres/Dockerfile"

# keycloak -- context = services/keycloak/; Dockerfile.keycloak bakes the esquire theme
# (login Cancel link etc.) + the realm import, then runs kc.sh build. Built here (NOT
# hand-pushed) so a sprint's theme/realm change ships with the release tag automatically.
bx esquire-keycloak "${SERVICES}/keycloak" "${SERVICES}/keycloak/Dockerfile.keycloak"

# activemq -- custom broker image (bakes activemq.xml + the JMX exporter agent). Built here per release so the
# release-tagged image EXISTS in GHCR: deploy-oke.sh --set image.tag=${IMAGE_TAG} for activemq would otherwise
# ImagePullBackOff (the GHA build formerly skipped it -> re-run 2026-07-23 finding V1). A broker-config change
# now ships with the release tag automatically, same as keycloak/postgres.
bx esquire-activemq "${SERVICES}/activemq" "${SERVICES}/activemq/Dockerfile"

echo "=== pushed 7 images to ${REG} at ${IMAGE_TAG} ==="
echo "    (3 Spring services + backend/BFF + postgres + keycloak + activemq)"
