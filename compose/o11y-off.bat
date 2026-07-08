@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-off.bat -- disable observability on the local docker compose stack:
rem   1. recreate the app services with tracing OFF (the default), and
rem   2. tear down the o11y viewing-stack containers (Loki + Alloy + Tempo +
rem      OTel Collector + Grafana).
rem The base stack keeps running. Mirror of k8s\o11y-off.bat.
rem ===========================================================================
set ESQ_TRACING_ENABLED=false

echo --- recreating the app services with tracing OFF...
docker compose up -d --no-deps --force-recreate gateway biztree enyman pacman keysmith kcmaster aukeep backend || exit /b 1

echo --- restarting the gateway so it rediscovers the recreated services...
docker compose restart gateway

echo --- tearing down the o11y viewing stack...
docker compose --profile o11y rm -sf loki alloy tempo otel-collector grafana

echo.
echo o11y OFF (docker). Tracing disabled; viewing stack removed.
endlocal
