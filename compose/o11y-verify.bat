@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-verify.bat -- verify the DOCKER observability stack end to end:
rem   metrics + tracing + logging, at every service and every gauge.
rem
rem Run AFTER o11y-on.bat, and after some activity (e.g. an e2e run) so the
rem meters have live series to check. Docker PUBLISHES the backends on the host
rem (prometheus :9090, loki :3100, tempo :3200), so no port-forward is needed.
rem
rem Mirror of k8s\o11y-verify.bat -- the SAME checks; only the addresses differ,
rem because the o11y stack itself is identical on every environment.
rem ===========================================================================
set ENVNAME=docker
set PROM_URL=http://localhost:9090
set LOKI_URL=http://localhost:3100
set TEMPO_URL=http://localhost:3200
set GRAFANA_URL=http://localhost:3009
set LOKI_JOB=esq-docker
set SERVICES=gateway,biztree,enyman,pacman,keysmith,kcmaster,aukeep
rem BOARDS = the dashboards THIS environment provisions. The dependency and band checks read the
rem PANELS as the declaration of what must exist, so they have to read the boards that are actually
rem deployed here -- not another topology's. Path is relative to services/.
set BOARDS=compose\o11y\grafana\provisioning\dashboards
python ..\test\o11y\o11y-verify.py
endlocal
