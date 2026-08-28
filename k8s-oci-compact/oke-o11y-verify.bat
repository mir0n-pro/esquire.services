@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem oke-o11y-verify.bat -- verify the OKE SUPER-COMPACT observability stack end to
rem end, LIVE.
rem
rem The OKE launcher for test\o11y\o11y-verify.py -- same checks as the docker and
rem local-k8s launchers; only the addresses and the fleet differ. The stack itself is
rem identical everywhere, so the script is shared and only the environment changes.
rem
rem The fleet is declared ONCE in test\o11y\fleet-supercompact-k8s.bat and used by
rem every super-compact launcher, so a service can never be named in one launcher and
rem forgotten in another.
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
rem The 1xxxx band is the DEFAULT, not a rule: a port already forwarded to ANOTHER cluster would otherwise
rem make this report on that cluster while still saying "oke". Pre-set any of these to use your own forward.
if not defined PROM_URL    set PROM_URL=http://localhost:19090
if not defined LOKI_URL    set LOKI_URL=http://localhost:13100
if not defined TEMPO_URL   set TEMPO_URL=http://localhost:13200
if not defined GRAFANA_URL set GRAFANA_URL=http://localhost:13009
set LOKI_JOB=esq-k8s
rem The super-compact fleet is declared ONCE for every environment -- see the shared file.
call ..\test\o11y\fleet-supercompact-k8s.bat
rem BOARDS = the dashboards THIS environment provisions. The dependency and band checks read the
rem PANELS as the declaration of what must exist, so they have to read the boards that are actually
rem deployed here -- not another topology's. Path is relative to services/.
set BOARDS=k8s-oci-compact\grafana
python ..\test\o11y\o11y-verify.py
endlocal
