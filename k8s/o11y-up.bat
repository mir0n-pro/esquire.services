@echo off
rem ===========================================================================
rem o11y-up.bat -- deploy the OPT-IN observability stack (T1 logging visualize:
rem Loki + Alloy + Grafana) to local Docker Desktop k8s. Run AFTER k8s-up.bat.
rem Mirrors the docker `--profile o11y` -- it does NOT burden the base stack.
rem
rem   Open:   http://grafana.localhost   (admin/admin)  ->  Explore  ->  Loki
rem   Query:  {job="esq-k8s"} | json | correlationId = "<id>"
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
echo --- Installing grafana...
call helm upgrade --install esquire-infra-grafana charts\infra\grafana || exit /b 1

echo Waiting for loki...
kubectl rollout status deployment/esquire-infra-loki    -n default --timeout=150s
echo Waiting for grafana...
kubectl rollout status deployment/esquire-infra-grafana -n default --timeout=150s

echo.
echo o11y stack up. Grafana: http://grafana.localhost  (admin/admin)
echo   Explore -^> Loki -^> {job="esq-k8s"} ^| json ^| correlationId = "..."
endlocal
