#!/bin/bash
# Re-push all 6 Spring services with freshly built JARs that include the
# springProfile-based logback config (and gateway's KEYCLOAK_PATH support).
set -e

SVC=/c/MyProjects/esquire/services
REG=ghcr.io/mir0n-pro
TAG=v1.2.2
P=linux/amd64,linux/arm64

build() {
    local name="$1"
    local img="$2"
    local dir="$3"
    echo ""
    echo "================================================================"
    echo "=== [$(date +%H:%M:%S)] Re-pushing $name -> $img"
    echo "================================================================"
    docker buildx build --platform "$P" \
        -t "$REG/$img:$TAG" \
        --push \
        -f "$SVC/$dir/Dockerfile" \
        "$SVC/$dir" 2>&1 | tail -8
}

build biztree   esquire.biztree   bizTree
build enyman    esquire.enyman    enyMan
build pacman    esquire.pacman    pacMan
build keysmith  esquire.keysmith  keySmith
build kcmaster  esquire.kcmaster  kcMaster
build gateway   esquire.gateway   gateway

echo ""
echo "================================================================"
echo "=== [$(date +%H:%M:%S)] All 6 Spring services re-pushed with fresh JARs."
echo "================================================================"
