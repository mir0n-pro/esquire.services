@echo off
rem ===========================================================================
rem o11y-on.bat -- enable the OPT-IN observability stack (logs T1: Loki + Alloy;
rem traces T2: Tempo + OTel Collector; metrics O1: Prometheus; Grafana single pane) on local
rem Docker Desktop k8s: deploy the viewing stack AND set observability ON across the app services. Run
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
echo --- Installing prometheus...
call helm upgrade --install esquire-infra-prometheus charts\infra\prometheus || exit /b 1
echo --- Installing postgres-exporter...
call helm upgrade --install esquire-infra-postgres-exporter charts\infra\postgres-exporter || exit /b 1
echo --- Installing grafana...
call helm upgrade --install esquire-infra-grafana charts\infra\grafana || exit /b 1
rem Grafana provisions datasources only at boot -- force a restart so a newly added source (Tempo)
rem and dashboards are picked up even when the config change alone doesn't roll the pod.
call kubectl rollout restart deployment/esquire-infra-grafana

echo Waiting for loki...
kubectl rollout status deployment/esquire-infra-loki    -n default --timeout=150s
echo Waiting for tempo...
kubectl rollout status deployment/esquire-infra-tempo   -n default --timeout=150s
echo Waiting for prometheus...
kubectl rollout status deployment/esquire-infra-prometheus -n default --timeout=150s
echo Waiting for grafana...
kubectl rollout status deployment/esquire-infra-grafana -n default --timeout=150s

echo --- Enabling observability on the app services (mirrors docker ESQ_OBSERVABILITY_ENABLED=true)...
for %%s in (gateward mesnie pacman) do (
  echo   esquire-%%s observability ON
  call helm upgrade esquire-%%s charts\esquire-%%s --reset-then-reuse-values --set observability.enabled=true --set observability.metricsHistograms=true
  call kubectl rollout restart statefulset esquire-%%s-%%s
)
rem The BFF (Node) is out of the loop: it has NO histogram sub-switch (its one HTTP histogram has fixed
rem buckets), so its chart never reads observability.metricsHistograms. It takes observability.enabled ONLY.
echo   esquire-backend observability ON
call helm upgrade esquire-backend charts\esquire-backend --reset-then-reuse-values --set observability.enabled=true
call kubectl rollout restart statefulset esquire-backend-backend

echo --- Enabling metrics on keycloak (KC_METRICS_ENABLED via the umbrella)...
call helm upgrade esquire-infra-kc charts\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=true
call kubectl rollout restart statefulset esquire-infra-kc-keycloak

echo --- Enabling metrics on activemq (the JMX exporter agent on :9404 via the umbrella)...
call helm upgrade esquire-infra-amq charts\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=true
call kubectl rollout restart statefulset esquire-infra-amq-activemq
kubectl rollout status statefulset/esquire-infra-amq-activemq -n default --timeout=150s

echo.
echo o11y stack up (logs + traces + metrics). Grafana: http://grafana.localhost  (admin/admin)
echo   Explore -^> Loki -^> {job="esq-k8s"} ^| json ^| correlationId = "..."
endlocal
