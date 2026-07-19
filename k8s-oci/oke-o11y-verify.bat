@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem oke-o11y-verify.bat -- verify the OKE observability stack end to end, LIVE.
rem
rem The OKE launcher for test\o11y\o11y-verify.py -- same checks as the docker and
rem local-k8s launchers; only the addresses and the service list differ. The stack
rem itself is identical everywhere, so the script is shared and only the env changes.
rem
rem OKE delta: the SERVICES list EXCLUDES aukeep -- OKE has no auKeep (audit = DB
rem triggers), so there is no auKeep log stream, metric label, or trace node to
rem assert (asserting one would be a permanent false FAIL).
rem
rem This is the FULL-model verify: it checks metrics + tracing + logging, so it needs
rem Prometheus and Tempo up (oke-o11y-on FULL). For a LOG-only smoke there is no
rem Prometheus, so run a focused Loki check instead (query Loki for each service's
rem log stream + a correlationId); this launcher would FAIL the metrics/tracing checks
rem by design.
rem
rem PRE: port-forward the OKE o11y backends to the 1xxxx band first (so they do not
rem collide with a docker o11y stack on the same host), then run some activity (an
rem e2e run) so the meters/streams have series:
rem   kubectl port-forward svc/esquire-infra-prometheus 19090:9090 -n default
rem   kubectl port-forward svc/esquire-infra-loki       13100:3100 -n default
rem   kubectl port-forward svc/esquire-infra-tempo      13200:3200 -n default
rem   kubectl port-forward svc/esquire-infra-grafana    13009:3000 -n default
rem ===========================================================================
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this is the OKE o11y verify. Refusing.
  exit /b 1
)
set ENVNAME=oke
set PROM_URL=http://localhost:19090
set LOKI_URL=http://localhost:13100
set TEMPO_URL=http://localhost:13200
set GRAFANA_URL=http://localhost:13009
set LOKI_JOB=esq-k8s
rem SERVICES = the metrics `application` label (SHORT names, as the app tags its meters).
set SERVICES=gateway,biztree,enyman,pacman,keysmith,kcmaster
rem LOG_SERVICES = the Loki `service_name` label, which on k8s Loki auto-derives from the pod `app`
rem label = the FULL workload name (esquire-<svc>-<svc>), NOT the short meter name. The log-stream
rem sweep needs the full form or it looks for a stream that does not exist. (verify.py exposes
rem LOG_SERVICES precisely so an environment whose log label differs can override it wholesale.)
set LOG_SERVICES=esquire-gateway-gateway,esquire-biztree-biztree,esquire-enyman-enyman,esquire-pacman-pacman,esquire-keysmith-keysmith,esquire-kcmaster-kcmaster,esquire-backend-backend
rem OKE has NO auKeep (audit = DB triggers): drop its keep-write meters (EXPECTED elsewhere) and its trace node,
rem or they FAIL forever on a fleet that legitimately does not have them.
set EXCLUDE_METERS=esq_biz_keep_write_total,esq_biz_keep_write_duration_seconds,esq_keep_apply_seconds
set EXCLUDE_TRACE_NODES=aukeep
python ..\test\o11y\o11y-verify.py
endlocal
