@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem oke-o11y-off.bat -- disable observability on OKE and remove the viewing stack,
rem back to OKE defaults. The OKE twin of k8s\o11y-off.bat, and the steady state
rem after every on-demand smoke (T12).
rem
rem OKE deltas vs the local twin: context guard refuses docker-desktop (runs only on
rem the OKE cluster); the app-service loop EXCLUDES aukeep (OKE has no auKeep -- audit
rem = DB triggers); app charts use --reset-then-reuse-values so the OKE overlay/tag/
rem db-password stay put while only the o11y switches move.
rem ===========================================================================
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this is the OKE o11y routine ^(production^).
  echo Refusing. Switch: kubectl config use-context context-czhlwnp27sq
  exit /b 1
)
set CH=..\k8s\charts
echo === oke-o11y-off  (context=%CTX%) ===
echo --- Disabling tracing/metrics on the app services (NO aukeep)...
for %%s in (gateway enyman biztree pacman keysmith kcmaster) do (
  call helm upgrade esquire-%%s %CH%\esquire-%%s --reset-then-reuse-values --set observability.enabled=false --set observability.metricsHistograms=false
  call kubectl rollout restart statefulset esquire-%%s-%%s
)
rem The BFF takes observability.enabled ONLY (no histogram sub-switch; pino default logging).
call helm upgrade esquire-backend %CH%\esquire-backend --reset-then-reuse-values --set observability.enabled=false
call kubectl rollout restart statefulset esquire-backend-backend
rem SKIP_INFRA_ROLL (set by oke-perf-matrix): skip the kc/amq metric rolls. Rolling the broker drops
rem the app pods' messagingBus connection, which does NOT self-heal (needs a pod restart) -- so a
rem toggle-in-place matrix must never roll it. Infra metrics are the broker's/kc's OWN, not app cost.
if not defined SKIP_INFRA_ROLL (
  echo --- Disabling metrics on keycloak...
  call helm upgrade esquire-infra-kc  %CH%\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=false
  call kubectl rollout restart statefulset esquire-infra-kc-keycloak
  echo --- Disabling metrics on activemq ^(JMX exporter agent no longer loaded^)...
  call helm upgrade esquire-infra-amq %CH%\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=false
  call kubectl rollout restart statefulset esquire-infra-amq-activemq
)
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
