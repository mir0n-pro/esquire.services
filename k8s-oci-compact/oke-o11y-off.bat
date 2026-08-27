@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem oke-o11y-off.bat -- disable observability on the OKE SUPER-COMPACT stack and remove
rem the viewing stack,
rem back to OKE defaults. The OKE twin of k8s-compact\o11y-off.bat, and the steady state
rem after every on-demand smoke (T12).
rem
rem OKE deltas vs the local twin: context guard refuses docker-desktop (runs only on
rem the OKE cluster); the app-service loop is the THREE compact processes and has no
rem aukeep line (this profile audits by DB triggers, option (a) -- there is no auKeep to switch); app charts use --reset-then-reuse-values so the OKE overlay/tag/
rem db-password stay put while only the o11y switches move.
rem ===========================================================================
rem --force-conflicts on every upgrade: helm 4 applies SERVER-SIDE, and `kubectl scale` (the perf matrix,
rem and any hand scaling) takes ownership of .spec.replicas. An upgrade that then touches that field is
rem REFUSED -- and these calls are `call helm ... *> nul` with no exit-code check, so the arm would be
rem silently NOT applied and the run would measure the previous arm. This is what voided the first pass.
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this is the OKE o11y routine ^(production^).
  echo Refusing. Switch: kubectl config use-context context-czhlwnp27sq
  exit /b 1
)
set CH=..\k8s-compact\charts
echo === oke-o11y-off  (context=%CTX%) ===
rem INFRA FIRST, APPS AFTER -- the order stated just below. A broker roll INTERRUPTS every app pod's
rem messagingBus: the transport goes DOWN and the failover: wrapper reconnects it on its own, about 20s on
rem local k8s. The cost is a bus WINDOW, not a dead bus. Rolling the broker first puts that window beside the
rem app restart below rather than after it, and costs no extra restarts.
rem SKIP_INFRA_ROLL (set by oke-perf-matrix): skip the kc/amq metric rolls. Rolling the broker costs a bus
rem window -- about 20s while the failover: transport reconnects -- which lands INSIDE a toggle-in-place
rem measurement and reads as app cost. Infra metrics are the broker's/kc's OWN, not app cost.
if not defined SKIP_INFRA_ROLL (
  echo --- Disabling metrics on keycloak...
  call helm upgrade esquire-infra-kc  %CH%\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
  call kubectl rollout restart statefulset esquire-infra-kc-keycloak
  echo --- Disabling metrics on activemq ^(JMX exporter agent no longer loaded^)...
  call helm upgrade esquire-infra-amq %CH%\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
  call kubectl rollout restart statefulset esquire-infra-amq-activemq
)
rem THE OVERLAY MUST BE RE-APPLIED, not just the observability switch. --reset-then-reuse-values keeps
rem what the release stored, and oke-o11y-on set logging.levelMir0n=INFO there. Without -f values\<svc>.yaml
rem the level stays INFO after a disarm: the fleet keeps logging at INFO to stdout with no Loki to read it,
rem and the ERROR default in the overlay is never reasserted. The infra lines above already pass -f.
echo --- Disabling tracing/metrics on the app services ^(gateWard, Mesnie, pacMan^)...
for %%s in (gateward mesnie pacman) do (
  call helm upgrade esquire-%%s %CH%\esquire-%%s -f values\%%s.yaml --reset-then-reuse-values --set observability.enabled=false --set observability.metricsHistograms=false --force-conflicts
  call kubectl rollout restart statefulset esquire-%%s
)
rem The BFF takes observability.enabled ONLY (no histogram sub-switch; pino default logging).
call helm upgrade esquire-backend %CH%\esquire-backend -f values\backend.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-backend
echo --- Removing the viewing stack...
call helm uninstall esquire-infra-grafana           2>nul
call helm uninstall esquire-infra-postgres-exporter 2>nul
call helm uninstall esquire-infra-prometheus        2>nul
call helm uninstall esquire-infra-otel-collector    2>nul
call helm uninstall esquire-infra-tempo             2>nul
call helm uninstall esquire-infra-alloy             2>nul
call helm uninstall esquire-infra-loki              2>nul
echo.
echo o11y OFF and the viewing stack removed -- OKE back to defaults.
endlocal
