#!/usr/bin/env bash
#
# Esquire services -- CI build + test (phase 1).
# Called by .github/workflows/ci.yml. Also runnable locally (needs JDK 21, Maven, Docker).
# Design: doc/Esquire.GitHubActions.md.
#
set -euo pipefail

# The xxRod RodBusIntegrationTest is @Testcontainers(disabledWithoutDocker=true): it RUNS wherever
# Docker is present (i.e. CI), and starts an esquire-activemq:6.1.4 container. That image is local
# (built from activemq/Dockerfile), so it must exist before the reactor reaches xxRod's test phase
# or the test fails on a missing image. Build it first. (Postgres is pulled by Testcontainers.)
echo "--- building esquire-activemq:6.1.4 (Testcontainers integration-test dependency)"
docker build -t esquire-activemq:6.1.4 activemq

echo "--- mvn clean verify (reactor: all modules, unit tests + integration test)"
mvn -B -ntp clean verify
