@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-verify.bat -- verify the LOCAL K8S observability stack end to end:
rem   metrics + tracing + logging, at every service and every gauge.
rem
rem Run AFTER o11y-on.bat and o11y-forward.bat. The forwards put the k8s backends
rem on the 1xxxx band (prometheus :19090, loki :13100, tempo :13200) so they do
rem NOT collide with the docker o11y stack -- see o11y-forward.bat for why.
rem Run some activity first (an e2e run) so the meters have series to check.
rem
rem Mirror of compose\o11y-verify.bat -- the SAME checks; only the addresses
rem differ, because the o11y stack itself is identical on every environment.
rem ===========================================================================
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop". Refusing to run.
  exit /b 1
)
set ENVNAME=k8s
set PROM_URL=http://localhost:19090
set LOKI_URL=http://localhost:13100
set TEMPO_URL=http://localhost:13200
rem Grafana on the same 1xxxx band as the others (o11y-forward.bat): *.localhost resolves for curl but not for
rem python's getaddrinfo, so the live datasource check needs a real localhost port.
set GRAFANA_URL=http://localhost:13009
set LOKI_JOB=esq-k8s
set SERVICES=gateway,biztree,enyman,pacman,keysmith,kcmaster,aukeep
rem LOG_SERVICES = the Loki `service_name` label, which on k8s is the FULL workload name
rem (esquire-<svc>-<svc>), NOT the short meter name. Without this the log-stream sweep looks for
rem a stream that does not exist and FAILs every service (the logs ARE shipped). Mirrors the OKE
rem launcher (oke-o11y-verify.bat); k8s keeps aukeep (OKE has none).
set LOG_SERVICES=esquire-gateway-gateway,esquire-biztree-biztree,esquire-enyman-enyman,esquire-pacman-pacman,esquire-keysmith-keysmith,esquire-kcmaster-kcmaster,esquire-aukeep-aukeep,esquire-backend-backend
rem BOARDS = the dashboards THIS environment provisions. The dependency and band checks read the
rem PANELS as the declaration of what must exist, so they have to read the boards that are actually
rem deployed here -- not another topology's. Path is relative to services/.
set BOARDS=k8s\charts\infra\grafana\dashboards
python ..\test\o11y\o11y-verify.py
endlocal
