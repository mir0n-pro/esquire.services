@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-verify.bat -- verify the COMPACT docker observability stack end to end:
rem   metrics + tracing + logging, at every service and every gauge.
rem
rem Run AFTER o11y-on.bat, and after some activity (e.g. an e2e run) so the
rem meters have live series to check. Docker PUBLISHES the backends on the host
rem (prometheus :9090, loki :3100, tempo :3200), so no port-forward is needed.
rem
rem Mirror of compose\o11y-verify.bat -- the SAME checks; what differs is the
rem fleet. SERVICES is the `application` label, which names PROCESSES, and this
rem profile runs FOUR of them (three Java + the BFF). enyMan, keySmith,
rem kcMaster, the gateway and bizTree are all still reported -- under the
rem `service` label, inside mesnie and gateward.
rem ===========================================================================
set ENVNAME=docker-compact
set PROM_URL=http://localhost:9090
set LOKI_URL=http://localhost:3100
set TEMPO_URL=http://localhost:3200
set GRAFANA_URL=http://localhost:3009
set LOKI_JOB=esq-docker
rem The compact fleet is declared ONCE for every environment -- see the shared file.
call ..\test\o11y\fleet-compact.bat
python ..\test\o11y\o11y-verify.py
endlocal
