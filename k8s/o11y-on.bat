@echo off
rem ===========================================================================
rem o11y-on.bat -- enable the OPT-IN observability stack (logs T1: Loki + Alloy;
rem traces T2: Tempo + OTel Collector; Grafana single pane) on local Docker Desktop
rem k8s: deploy the viewing stack AND set tracing ON across the app services. Run
rem AFTER k8s-up.bat. Mirror of compose\o11y-on.bat -- it does NOT burden the base
rem stack (the base default is tracing OFF).
rem
rem   Open:   http://grafana.localhost   (admin/admin)  ->  Explore
rem   Logs:   {job="esq-k8s"} | json | correlationId = "<id>"
rem   Traces: Tempo -> Search by TraceID = the same correlationId
rem ===========================================================================
setlocal
cd /d "%~dp0"

for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop". Refusing to run.
  exit /b 1
)

echo --- Installing loki...
call helm upgrade --install esquire-infra-loki    charts\infra\loki    || exit /b 1
echo --- Installing alloy...
call helm upgrade --install esquire-infra-alloy   charts\infra\alloy   || exit /b 1
echo --- Installing tempo...
call helm upgrade --install esquire-infra-tempo   charts\infra\tempo   || exit /b 1
echo --- Installing otel-collector...
call helm upgrade --install esquire-infra-otel-collector charts\infra\otel-collector || exit /b 1
echo --- Installing grafana...
call helm upgrade --install esquire-infra-grafana charts\infra\grafana || exit /b 1
rem Grafana provisions datasources only at boot -- force a restart so a newly added source (Tempo)
rem is picked up even when the config change alone doesn't roll the pod.
call kubectl rollout restart deployment/esquire-infra-grafana

echo Waiting for loki...
kubectl rollout status deployment/esquire-infra-loki    -n default --timeout=150s
echo Waiting for tempo...
kubectl rollout status deployment/esquire-infra-tempo   -n default --timeout=150s
echo Waiting for grafana...
kubectl rollout status deployment/esquire-infra-grafana -n default --timeout=150s

echo --- Enabling tracing on the app services (mirrors docker ESQ_TRACING_ENABLED=true)...
for %%s in (gateway enyman biztree pacman keysmith kcmaster aukeep backend) do (
  echo   esquire-%%s tracing ON
  call helm upgrade esquire-%%s charts\esquire-%%s --reset-then-reuse-values --set tracing.enabled=true
  call kubectl rollout restart statefulset esquire-%%s-%%s
)

echo.
echo o11y stack up. Grafana: http://grafana.localhost  (admin/admin)
echo   Explore -^> Loki -^> {job="esq-k8s"} ^| json ^| correlationId = "..."
endlocal
