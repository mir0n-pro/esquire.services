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
rem The compact fleet is declared ONCE for every environment -- see the shared file.
call ..\test\o11y\fleet-compact-k8s.bat
python ..\test\o11y\o11y-verify.py
endlocal
