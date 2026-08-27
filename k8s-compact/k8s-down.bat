@echo off
cd /d "%~dp0"

rem === Context safety guard ===
rem Refuses to run if kubectl context is anything other than docker-desktop.
rem Prevents the 2026-05-06 disaster where helm uninstall hit OKE production.
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop".
  echo Refusing to run k8s-down.bat -- this script targets local Docker Desktop k8s only.
  echo Switch with: kubectl config use-context docker-desktop
  exit /b 1
)

rem Drop the public ingress first so traffic stops being routed to the
rem soon-to-be-deleted backends. Idempotent: ignore "not found".
echo --- Deleting public ingress...
kubectl delete -f cluster\ingress.yaml --ignore-not-found=true

call helm uninstall esquire-backend
call helm uninstall esquire-gateward

call helm uninstall esquire-mesnie
call helm uninstall esquire-pacman
rem auKeep is part of THIS fleet -- compact runs it (audit option b, the audit-c bus drained in its own
rem process). It was missing here, so every teardown left it running: the pods then outlived a PVC drop,
rem pointed at a database that had been reseeded underneath them, and never became Ready again -- which
rem made the caller wait out its full drain budget on every cycle.
call helm uninstall esquire-aukeep

rem The classic pair this compact stack replaces. Uninstalled too, and deliberately: a machine that ran
rem classic before still holds them, and leaving either behind is how a cluster ends up serving from two
rem shapes at once -- the same way a missing mesnie line once left the old identity trio running under a
rem compact deploy. "not found" here is the normal case and is ignored.
call helm uninstall esquire-gateway
call helm uninstall esquire-biztree
call helm uninstall esquire-enyman
call helm uninstall esquire-keysmith
call helm uninstall esquire-kcmaster

rem The viewing stack. o11y-on.bat installs these seven; a teardown that leaves them behind leaves
rem Grafana and Prometheus running against a stack that is gone, and the next bring-up comes back at
rem the chart default -- viewers up, every scrape target down. Same order o11y-off.bat uses.
call helm uninstall esquire-infra-grafana
call helm uninstall esquire-infra-postgres-exporter
call helm uninstall esquire-infra-prometheus
call helm uninstall esquire-infra-otel-collector
call helm uninstall esquire-infra-tempo
call helm uninstall esquire-infra-alloy
call helm uninstall esquire-infra-loki

call helm uninstall esquire-infra-kc
call helm uninstall esquire-infra-amq
call helm uninstall esquire-infra
call helm uninstall esquire-infra-redis

rem The bus catalog. It is a release like any other, so a teardown that leaves it behind leaves the cluster
rem holding a topology for a stack that is gone -- and the next bring-up reads it before installing its own.
call helm uninstall esquire-topology

rem kafka backs the audit (ck)/(dk) sinks, which belong to the classic local stacks. A machine that brought
rem this profile up earlier still runs the release, and nothing else would ever drop it. "not found" is the
rem normal case here.
call helm uninstall esquire-infra-kafka

rem redis holds the BFF's shared sessions on this profile -- which is why it is INSTALLED, not a reason to
rem leave it running once the stack that used it is gone. It goes with the rest; sessions do not outlive a
rem teardown anyway.

rem Note: MetalLB + ingress-nginx are NOT uninstalled. They're cluster-wide
rem prerequisites that survive Esquire teardown (managed by addMetalLB.bat
rem + addIngressNginx.bat one-time installs).
