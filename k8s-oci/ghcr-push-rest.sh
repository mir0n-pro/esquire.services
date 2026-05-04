#!/bin/bash
# Sequential multi-arch buildx + push for the remaining 9 images.
# biztree was already pushed via the canary build.
set -e

SVC=/c/MyProjects/esquire/services
ESQ=/c/MyProjects/esquire
REG=ghcr.io/mir0n-pro
TAG=v1.2.2
P=linux/amd64,linux/arm64

build() {
    local name="$1"
    local img="$2"
    local dockerfile="$3"
    local context="$4"
    echo ""
    echo "================================================================"
    echo "=== [$(date +%H:%M:%S)] Building $name -> $img"
    echo "================================================================"
    docker buildx build --platform "$P" \
        -t "$REG/$img:$TAG" \
        --push \
        -f "$dockerfile" \
        "$context" 2>&1 | tail -8
}

build enyman    esquire.enyman    "$SVC/enyMan/Dockerfile"    "$SVC/enyMan"
build pacman    esquire.pacman    "$SVC/pacMan/Dockerfile"    "$SVC/pacMan"
build keysmith  esquire.keysmith  "$SVC/keySmith/Dockerfile"  "$SVC/keySmith"
build kcmaster  esquire.kcmaster  "$SVC/kcMaster/Dockerfile"  "$SVC/kcMaster"
build gateway   esquire.gateway   "$SVC/gateway/Dockerfile"   "$SVC/gateway"

build frontend  esquire.frontend  "$ESQ/explorer/frontend/Dockerfile.k8s"  "$ESQ/explorer/frontend"

build postgres  esquire-postgres  "$SVC/postgres/Dockerfile"  "$ESQ"
build keycloak  esquire-keycloak  "$SVC/keycloak/Dockerfile.keycloak"  "$SVC/keycloak"
build activemq  esquire-activemq  "$SVC/activemq/Dockerfile"  "$SVC/activemq"

echo ""
echo "================================================================"
echo "=== [$(date +%H:%M:%S)] All 9 remaining images pushed."
echo "================================================================"
